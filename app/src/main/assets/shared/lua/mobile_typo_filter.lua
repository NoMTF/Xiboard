-- Mobile typo reranker for Xiboard.
-- Keeps correction fully offline and bounded: it only reorders candidates that
-- Rime has already produced, and it stops scanning after a small candidate window.

local M = {}

local typo_cache = {}
local typo_cache_order = {}

local function read_int(config, key, default)
    local value = config:get_int(key)
    if value == nil then return default end
    return value
end

local function read_bool(config, key, default)
    local value = config:get_bool(key)
    if value == nil then return default end
    return value
end

local function normalize_code(text)
    if text == nil then return "" end
    return text:gsub("[^A-Za-z]", ""):lower()
end

local function now_ms()
    return math.floor(os.clock() * 1000)
end

local function add_variant(list, seen, value)
    if value == nil or value == "" or seen[value] then return end
    seen[value] = true
    table.insert(list, value)
end

local function add_replace_suffix(list, seen, code, from, to)
    if code:sub(-#from) == from then
        add_variant(list, seen, code:sub(1, #code - #from) .. to)
    end
end

local function add_single_replacements(list, seen, code, pairs, max_count)
    for _, pair in ipairs(pairs) do
        local from = pair[1]
        local to = pair[2]
        local start = 1
        while #list < max_count do
            local s, e = code:find(from, start, true)
            if s == nil then break end
            add_variant(list, seen, code:sub(1, s - 1) .. to .. code:sub(e + 1))
            start = s + 1
        end
    end
end

local function variants_for(code, max_count)
    local cache_key = code .. ":" .. max_count
    if typo_cache[cache_key] then return typo_cache[cache_key] end

    local variants = {}
    local seen = {}

    if #code < 3 then
        typo_cache[cache_key] = variants
        return variants
    end

    -- Typing-short forms commonly seen on mobile full pinyin.
    add_replace_suffix(variants, seen, code, "uen", "un")
    add_replace_suffix(variants, seen, code, "uei", "ui")
    add_replace_suffix(variants, seen, code, "iou", "iu")
    add_replace_suffix(variants, seen, code, "ve", "ue")
    add_replace_suffix(variants, seen, code, "gn", "ng")
    add_replace_suffix(variants, seen, code, "mg", "ng")
    add_replace_suffix(variants, seen, code, "ign", "ing")
    add_replace_suffix(variants, seen, code, "img", "ing")

    -- Limited fuzzy finals. These are intentionally fewer than desktop fuzzy
    -- pinyin options, because every extra rule affects mobile candidate latency.
    add_replace_suffix(variants, seen, code, "eng", "en")
    add_replace_suffix(variants, seen, code, "en", "eng")
    add_replace_suffix(variants, seen, code, "ing", "in")
    add_replace_suffix(variants, seen, code, "in", "ing")
    add_replace_suffix(variants, seen, code, "ang", "an")
    add_replace_suffix(variants, seen, code, "an", "ang")

    -- Adjacent key/near miss pairs. Generate one edit only; scoring below keeps
    -- original candidates ahead unless the corrected path is much more plausible.
    add_single_replacements(
        variants,
        seen,
        code,
        {
            { "ai", "ao" }, { "ao", "ai" },
            { "uo", "io" }, { "io", "uo" },
            { "ua", "ia" }, { "ia", "ua" },
            { "ei", "ie" }, { "ie", "ei" },
        },
        max_count
    )

    while #variants > max_count do
        table.remove(variants)
    end

    typo_cache[cache_key] = variants
    table.insert(typo_cache_order, cache_key)
    if #typo_cache_order > 64 then
        local old_key = table.remove(typo_cache_order, 1)
        typo_cache[old_key] = nil
    end
    return variants
end

local function typo_score(input_code, cand_code, variants)
    if cand_code == "" then return 0 end
    if cand_code == input_code then return 0 end

    for i, variant in ipairs(variants) do
        if cand_code == variant or cand_code:find("^" .. variant) then
            return 100 - i
        end
    end

    -- Rime candidate preedit may contain only the segment currently being
    -- composed after partial selection. Prefix match is a weak signal.
    if #cand_code >= 3 and input_code:find("^" .. cand_code) == nil then
        for i, variant in ipairs(variants) do
            if #variant >= 3 and variant:find("^" .. cand_code) then
                return 60 - i
            end
        end
    end
    return 0
end

local function is_strong_correction(cand)
    return cand.comment ~= nil and cand.comment:find("^@typo!%s") ~= nil
end

function M.init(env)
    local config = env.engine.schema.config
    env.name_space = env.name_space:gsub("^*", "")
    env.max_scan = read_int(config, env.name_space .. "/max_scan", 32)
    env.max_promote = read_int(config, env.name_space .. "/max_promote", 3)
    env.max_variants = read_int(config, env.name_space .. "/max_variants", 8)
    env.min_input_len = read_int(config, env.name_space .. "/min_input_len", 3)
    env.max_input_len = read_int(config, env.name_space .. "/max_input_len", 18)
    env.deadline_ms = read_int(config, env.name_space .. "/deadline_ms", 8)
    env.show_marker = read_bool(config, env.name_space .. "/show_marker", false)
end

function M.func(input, env)
    local started = now_ms()
    local code = normalize_code(env.engine.context.input)
    if #code < env.min_input_len or #code > env.max_input_len then
        for cand in input:iter() do yield(cand) end
        return
    end

    local variants = variants_for(code, env.max_variants)
    if #variants == 0 then
        for cand in input:iter() do yield(cand) end
        return
    end

    local promoted = {}
    local normal = {}
    local emitted_text = {}
    local scanned = 0

    local iter, state, key = input:iter()
    while true do
        local cand = iter(state, key)
        key = cand
        if cand == nil then break end

        scanned = scanned + 1
        local cand_code = normalize_code(cand.preedit)
        local score = typo_score(code, cand_code, variants)

        if score > 0 and not emitted_text[cand.text] and #promoted < env.max_promote then
            emitted_text[cand.text] = true
            if env.show_marker then
                cand:get_genuine().comment = cand.comment == "" and "@typo" or (cand.comment .. " @typo")
            end
            table.insert(promoted, { cand = cand, score = score, order = scanned })
        else
            table.insert(normal, cand)
        end

        if scanned >= env.max_scan or now_ms() - started > env.deadline_ms then break end
    end

    table.sort(promoted, function(a, b)
        if a.score == b.score then return a.order < b.order end
        return a.score > b.score
    end)

    -- Exact-map corrections are allowed to win. Generic near-key corrections
    -- stay behind the first original hit to keep daily typing predictable.
    local emitted_promoted = {}
    for i, item in ipairs(promoted) do
        if is_strong_correction(item.cand) then
            emitted_promoted[i] = true
            yield(item.cand)
        end
    end

    if #normal > 0 then
        yield(normal[1])
        for i, item in ipairs(promoted) do
            if not emitted_promoted[i] then yield(item.cand) end
        end
        for i = 2, #normal do yield(normal[i]) end
    else
        for i, item in ipairs(promoted) do
            if not emitted_promoted[i] then yield(item.cand) end
        end
    end

    while true do
        local cand = iter(state, key)
        key = cand
        if cand == nil then break end

        if not emitted_text[cand.text] then yield(cand) end
    end
end

return M
