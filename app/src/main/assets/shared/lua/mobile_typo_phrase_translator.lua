-- Bounded mobile typo recall for Xiboard.
-- It generates a few one-edit pinyin paths, looks them up in Rime's local
-- dictionary, and yields only a tiny number of marked candidates.

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

local function now_ms()
    return math.floor(os.clock() * 1000)
end

local function normalize_code(text)
    if text == nil then return "" end
    return text:gsub("[^A-Za-z]", ""):lower()
end

local function add_variant(list, seen, value, cost, source)
    if value == nil or value == "" or seen[value] then return end
    seen[value] = true
    table.insert(list, { code = value, lookup = value, cost = cost or 1, source = source or "rule" })
end

local function add_exact_variant(list, seen, value, lookup, cost, text)
    if value == nil or value == "" or seen[value] then return end
    seen[value] = true
    table.insert(list, {
        code = value,
        lookup = lookup or value,
        cost = cost or 1,
        source = "map",
        text = text,
        bonus = 4.0,
    })
end

local function add_replace_suffix(list, seen, code, from, to, cost)
    if code:sub(-#from) == from then
        add_variant(list, seen, code:sub(1, #code - #from) .. to, cost, from .. ">" .. to)
    end
end

local function add_single_replacements(list, seen, code, pairs, max_count)
    for _, pair in ipairs(pairs) do
        local from = pair[1]
        local to = pair[2]
        local cost = pair[3] or 2
        local start = 1
        while #list < max_count do
            local s, e = code:find(from, start, true)
            if s == nil then break end
            add_variant(list, seen, code:sub(1, s - 1) .. to .. code:sub(e + 1), cost, from .. ">" .. to)
            start = s + 1
        end
    end
end

local function add_exact_map(list, seen, code, map)
    local items = map[code]
    if not items then return end
    for _, item in ipairs(items) do
        add_exact_variant(list, seen, item[1], item[2], item[3] or 1, item[4])
    end
end

local function variants_for(code, max_count, exact_map)
    local cache_key = code .. ":" .. max_count
    if typo_cache[cache_key] then return typo_cache[cache_key] end

    local variants = {}
    local seen = {}
    if #code < 3 then
        typo_cache[cache_key] = variants
        return variants
    end

    add_exact_map(variants, seen, code, exact_map)

    -- Pinyin shorthand / typo endings.
    add_replace_suffix(variants, seen, code, "ign", "ing", 1)
    add_replace_suffix(variants, seen, code, "img", "ing", 1)
    add_replace_suffix(variants, seen, code, "gn", "ng", 1)
    add_replace_suffix(variants, seen, code, "mg", "ng", 1)
    add_replace_suffix(variants, seen, code, "uen", "un", 1)
    add_replace_suffix(variants, seen, code, "uei", "ui", 1)
    add_replace_suffix(variants, seen, code, "iou", "iu", 1)
    add_replace_suffix(variants, seen, code, "ve", "ue", 1)

    -- Limited fuzzy finals required by the mobile profile.
    add_replace_suffix(variants, seen, code, "eng", "en", 2)
    add_replace_suffix(variants, seen, code, "en", "eng", 2)
    add_replace_suffix(variants, seen, code, "ing", "in", 2)
    add_replace_suffix(variants, seen, code, "in", "ing", 2)
    add_replace_suffix(variants, seen, code, "ang", "an", 2)
    add_replace_suffix(variants, seen, code, "an", "ang", 2)

    -- One low-cost adjacent/near-key edit. No recursive expansion.
    add_single_replacements(
        variants,
        seen,
        code,
        {
            { "ai", "ao", 1 }, { "ao", "ai", 1 },
            { "uo", "io", 2 }, { "io", "uo", 2 },
            { "ua", "ia", 2 }, { "ia", "ua", 2 },
            { "ei", "ie", 2 }, { "ie", "ei", 2 },
            { "zong", "zhong", 2 }, { "zhong", "zong", 2 },
            { "ni", "li", 3 }, { "li", "ni", 3 },
        },
        max_count
    )

    while #variants > max_count do
        table.remove(variants)
    end

    typo_cache[cache_key] = variants
    table.insert(typo_cache_order, cache_key)
    if #typo_cache_order > 96 then
        local old_key = table.remove(typo_cache_order, 1)
        typo_cache[old_key] = nil
    end
    return variants
end

local function build_exact_map()
    return {
        ["haiduo"] = { { "haoduo", "hao duo", 1, "好多" } },
        ["haidui"] = { { "haoduo", "hao duo", 1, "好多" } },
        ["haodui"] = { { "haoduo", "hao duo", 1, "好多" } },
        ["lihao"] = { { "nihao", "ni hao", 2, "你好" } },
        ["lihaoma"] = { { "nihaoma", "ni hao ma", 2, "你好吗" } },
        ["weisheme"] = { { "weishenme", "wei shen me", 1, "为什么" } },
        ["weishenm"] = { { "weishenme", "wei shen me", 1, "为什么" } },
        ["weishenmw"] = { { "weishenme", "wei shen me", 1, "为什么" } },
        ["zhenmele"] = { { "zenmele", "zen me le", 1, "怎么了" } },
        ["zengmele"] = { { "zenmele", "zen me le", 1, "怎么了" } },
        ["zemmele"] = { { "zenmele", "zen me le", 1, "怎么了" } },
        ["zenmle"] = { { "zenmele", "zen me le", 1, "怎么了" } },
        ["buzidao"] = { { "buzhidao", "bu zhi dao", 1, "不知道" } },
        ["buzhdao"] = { { "buzhidao", "bu zhi dao", 1, "不知道" } },
        ["buzhido"] = { { "buzhidao", "bu zhi dao", 1, "不知道" } },
        ["xieixie"] = { { "xiexie", "xie xie", 1, "谢谢" } },
        ["meish"] = { { "meishi", "mei shi", 1, "没事" } },
        ["meiyo"] = { { "meiyou", "mei you", 1, "没有" } },
        ["keyo"] = { { "keyi", "ke yi", 1, "可以" } },
        ["buyon"] = { { "buyong", "bu yong", 1, "不用" } },
        ["duibuq"] = { { "duibuqi", "dui bu qi", 1, "对不起" } },
        ["meiwenyi"] = { { "meiwenti", "mei wen ti", 1, "没问题" } },
        ["youdina"] = { { "youdian", "you dian", 1, "有点" } },
        ["dengixia"] = { { "dengyixia", "deng yi xia", 1, "等一下" } },
        ["jinrian"] = { { "jintian", "jin tian", 1, "今天" } },
        ["jintain"] = { { "jintian", "jin tian", 1, "今天" } },
        ["migngtian"] = { { "mingtian", "ming tian", 1, "明天" } },
        ["migtian"] = { { "mingtian", "ming tian", 1, "明天" } },
        ["zuotain"] = { { "zuotian", "zuo tian", 1, "昨天" } },
        ["wojude"] = { { "wojuede", "wo jue de", 1, "我觉得" } },
        ["zongguo"] = { { "zhongguo", "zhong guo", 2, "中国" } },
        ["feichan"] = { { "feichang", "fei chang", 1, "非常" } },
        ["bushidao"] = { { "buzhidao", "bu zhi dao", 2, "不知道" } },
        ["bushizhidao"] = { { "buzhidao", "bu zhi dao", 2, "不知道" } },
    }
end

local function read_stats(path)
    local stats = {}
    local file = io.open(path, "r")
    if not file then return stats end
    for line in file:lines() do
        local from, to, score, hits = line:match("^([a-z]+)\t([a-z]+)\t(-?%d+)\t?(%d*)$")
        if from and to and score then
            stats[from .. ">" .. to] = {
                score = tonumber(score) or 0,
                hits = tonumber(hits) or 0,
            }
        end
    end
    file:close()
    return stats
end

local function lookup_variant(mem, variant, max_candidates)
    local result = {}
    if variant.text then
        result[#result + 1] = variant.text
    end
    if not mem or not mem:dict_lookup(variant.lookup or variant.code, true, max_candidates) then return result end
    local count = 0
    for entry in mem:iter_dict() do
        local duplicate = false
        for _, text in ipairs(result) do
            if text == entry.text then
                duplicate = true
                break
            end
        end
        if not duplicate then
            count = count + 1
            result[#result + 1] = entry.text
        end
        if count >= max_candidates then break end
    end
    return result
end

local function refresh_stats_if_needed(env)
    env.stats_reload_tick = (env.stats_reload_tick or 0) + 1
    if env.stats_reload_tick < env.stats_reload_interval then return end
    env.stats_reload_tick = 0
    env.stats = read_stats(env.stats_path)
end

function M.init(env)
    local config = env.engine.schema.config
    env.name_space = env.name_space:gsub("^*", "")
    env.min_input_len = read_int(config, env.name_space .. "/min_input_len", 3)
    env.max_input_len = read_int(config, env.name_space .. "/max_input_len", 18)
    env.max_paths = read_int(config, env.name_space .. "/max_paths", 4)
    env.max_candidates = read_int(config, env.name_space .. "/max_candidates", 2)
    env.deadline_ms = read_int(config, env.name_space .. "/deadline_ms", 10)
    env.quality = read_int(config, env.name_space .. "/quality", 1)
    env.show_marker = read_bool(config, env.name_space .. "/show_marker", true)
    env.exact_map = build_exact_map()
    env.mem = Memory(env.engine, env.engine.schema)
    env.stats_path = rime_api:get_user_data_dir() .. "/mobile_typo_stats.tsv"
    env.stats = read_stats(env.stats_path)
    env.stats_reload_interval = 32
    env.stats_reload_tick = 0
end

function M.func(input, seg, env)
    local started = now_ms()
    local code = normalize_code(input)
    if #code < env.min_input_len or #code > env.max_input_len then return end
    refresh_stats_if_needed(env)

    local variants = variants_for(code, env.max_paths, env.exact_map)
    if #variants == 0 then return end

    local emitted = {}
    local yielded = 0
    for order, variant in ipairs(variants) do
        if now_ms() - started > env.deadline_ms then return end
        local texts = lookup_variant(env.mem, variant, env.max_candidates)
        local stat = env.stats[code .. ">" .. variant.code]
        local learn = stat and stat.score or 0
        local hits = stat and stat.hits or 0
        for _, text in ipairs(texts) do
            if not emitted[text] then
                emitted[text] = true
                yielded = yielded + 1
                local comment = ""
                if env.show_marker then
                    local payload = code .. ">" .. variant.code
                    comment = (variant.bonus and variant.bonus >= 1) and ("@typo! " .. payload) or ("@typo " .. payload)
                end
                local cand = Candidate("mobile_typo", seg.start, seg._end, text, comment)
                cand.preedit = variant.code
                cand.quality = env.quality + (variant.bonus or 0) + learn * 0.08 + hits * 0.01 - variant.cost * 0.15 - order * 0.01
                yield(cand)
                if yielded >= env.max_candidates then return end
            end
        end
    end
end

function M.fini(env)
    if env.mem and env.mem.disconnect then env.mem:disconnect() end
end

return M
