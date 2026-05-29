-- Lightweight local context reranker.
-- This is a tiny table-based n-gram reranker: it loads a local phrase-score
-- asset and only reorders the first few Rime candidates. It never generates
-- text on its own and never calls network services.

local M = {}

local function read_int(config, key, default)
    local value = config:get_int(key)
    if value == nil then return default end
    return value
end

local function now_ms()
    return math.floor(os.clock() * 1000)
end

local function last_chars(text, count)
    if text == nil or text == "" then return "" end
    local len = utf8.len(text)
    if not len or len <= count then return text end
    local start = utf8.offset(text, len - count + 1)
    if not start then return text end
    return text:sub(start)
end

local function has_cjk(text)
    return text ~= nil and text:find("[\228-\233]") ~= nil
end

local function starts_with(text, prefix)
    return text ~= nil and prefix ~= nil and text:sub(1, #prefix) == prefix
end

local function add_hint(map, context, text, score)
    if context == nil or context == "" or text == nil or text == "" then return end
    local numeric_score = tonumber(score) or 1
    local list = map[context]
    if not list then
        list = {}
        map[context] = list
    end
    for _, hint in ipairs(list) do
        if hint.text == text then
            if numeric_score > hint.score then hint.score = numeric_score end
            return
        end
    end
    list[#list + 1] = { text = text, score = numeric_score }
end

local function sort_context_map(map)
    for _, list in pairs(map) do
        table.sort(list, function(a, b)
            if a.score == b.score then return #a.text > #b.text end
            return a.score > b.score
        end)
    end
end

local function compact_map(raw)
    local compact = {}
    for context, texts in pairs(raw) do
        for score, text in ipairs(texts) do
            add_hint(compact, context, text, #texts - score + 1)
        end
    end
    return compact
end

local function build_fallback_context_map()
    return compact_map({
        ["我"] = { "是", "在", "也", "就", "想", "觉得", "可以", "没有", "现在", "今天", "们" },
        ["你"] = { "好", "在", "是", "要", "可以", "觉得", "现在", "有没有", "是不是", "们" },
        ["他"] = { "是", "在", "也", "就", "没有", "可以", "们" },
        ["她"] = { "是", "在", "也", "就", "没有", "可以", "们" },
        ["我们"] = { "可以", "现在", "今天", "明天", "一起", "出去", "先", "再", "要不要", "能不能" },
        ["你们"] = { "可以", "现在", "今天", "明天", "先", "再", "要不要", "能不能" },
        ["这个"] = { "可以", "不用", "就是", "应该", "比较", "有点", "没有", "问题" },
        ["那个"] = { "可以", "不用", "就是", "应该", "比较", "有点", "没有", "问题" },
        ["现在"] = { "可以", "不用", "开始", "已经", "还", "没有", "是不是", "就", "要" },
        ["今天"] = { "可以", "不用", "晚上", "下午", "上午", "有点", "天气", "先", "就" },
        ["明天"] = { "可以", "上午", "下午", "晚上", "再", "去", "不用", "一起" },
        ["昨天"] = { "晚上", "下午", "上午", "已经", "没有", "就" },
        ["为什么"] = { "会", "不", "没有", "这么", "还是", "不能", "要" },
        ["怎么"] = { "了", "办", "样", "回事", "可能", "这么", "还" },
        ["是不是"] = { "可以", "不用", "已经", "还有", "没有", "要" },
        ["能不能"] = { "帮", "先", "再", "直接", "不要", "给" },
        ["要不要"] = { "先", "再", "一起", "看看", "试试", "去" },
        ["有没有"] = { "什么", "可能", "办法", "时间", "问题" },
        ["没"] = { "有", "事", "关系", "问题", "必要", "办法" },
        ["没有"] = { "问题", "关系", "办法", "必要", "时间", "看到" },
        ["不"] = { "用", "要", "是", "知道", "可以", "行", "好", "对" },
        ["有"] = { "点", "没有", "什么", "问题", "可能", "时间", "办法" },
        ["好"] = { "的", "多", "像", "吧", "了", "一点", "不好" },
        ["好的"] = { "我", "那", "你", "没问题", "谢谢" },
        ["谢谢"] = { "你", "啦", "了", "老板" },
        ["麻烦"] = { "你", "帮", "发", "看", "问" },
        ["一起"] = { "去", "吃", "看", "玩", "出来", "回去" },
        ["出来"] = { "一下", "吃饭", "玩吗", "看看" },
        ["出去"] = { "吃饭", "玩", "一下", "看看" },
        ["吃"] = { "饭", "什么", "了吗", "完", "点" },
        ["看"] = { "一下", "看", "到了", "起来", "不懂" },
        ["等"] = { "一下", "会儿", "我", "你", "下" },
        ["先"] = { "这样", "不用", "等", "看", "去", "把" },
        ["再"] = { "说", "看", "等", "去", "发", "试试" },
        ["会"] = { "不会", "不会是", "有", "比较", "更" },
        ["可以"] = { "的", "吗", "了", "啊", "先", "不用" },
        ["觉得"] = { "可以", "不行", "还是", "有点", "应该", "没必要" },
        ["应该"] = { "可以", "不用", "是", "没有", "不会", "还能" },
        ["真的"] = { "可以", "不用", "很好", "有点", "没事" },
    })
end

local function merge_model_file(map, path)
    local file = io.open(path, "r")
    if not file then return false end
    for line in file:lines() do
        if line:sub(1, 1) ~= "#" and line:find("%S") then
            local context, text, score = line:match("^([^\t]+)\t([^\t]+)\t(-?%d+)")
            add_hint(map, context, text, score)
        end
    end
    file:close()
    return true
end

local function load_context_map(model_file)
    local map = build_fallback_context_map()
    local loaded = false
    local shared_dir = nil
    if rime_api and rime_api.get_shared_data_dir then
        shared_dir = rime_api:get_shared_data_dir()
    end
    if shared_dir then
        loaded = merge_model_file(map, shared_dir .. "/lua/" .. model_file)
    end
    if not loaded and rime_api and rime_api.get_user_data_dir then
        loaded = merge_model_file(map, rime_api:get_user_data_dir() .. "/lua/" .. model_file)
    end
    if not loaded then
        merge_model_file(map, "lua/" .. model_file)
    end
    sort_context_map(map)
    return map
end

function M.init(env)
    local config = env.engine.schema.config
    env.name_space = env.name_space:gsub("^*", "")
    env.max_scan = read_int(config, env.name_space .. "/max_scan", 20)
    env.max_promote = read_int(config, env.name_space .. "/max_promote", 2)
    env.deadline_ms = read_int(config, env.name_space .. "/deadline_ms", 8)
    env.model_file = config:get_string(env.name_space .. "/model_file") or "mobile_lm_reranker.tsv"
    env.context_map = load_context_map(env.model_file)
end

function M.func(input, env)
    local started = now_ms()
    local latest = env.engine.context.commit_history:latest_text()
    local context = last_chars(latest, 4)
    if not has_cjk(context) then
        for cand in input:iter() do yield(cand) end
        return
    end

    local hints = nil
    for i = utf8.len(context) or 0, 1, -1 do
        local start = utf8.offset(context, i)
        local key = start and context:sub(start) or context
        hints = env.context_map[key]
        if hints then break end
    end

    if not hints then
        for cand in input:iter() do yield(cand) end
        return
    end

    local promoted = {}
    local normal = {}
    local emitted_text = {}
    local scanned = 0
    local promoted_count = 0

    local iter, state, key = input:iter()
    while true do
        local cand = iter(state, key)
        key = cand
        if cand == nil then break end

        scanned = scanned + 1
        local matched = false
        if promoted_count < env.max_promote and has_cjk(cand.text) then
            for score, hint in ipairs(hints) do
                if cand.text == hint.text or starts_with(cand.text, hint.text) then
                    table.insert(promoted, { cand = cand, score = hint.score, order = scanned })
                    emitted_text[cand.text] = true
                    promoted_count = promoted_count + 1
                    matched = true
                    break
                end
            end
        end
        if not matched then table.insert(normal, cand) end
        if scanned >= env.max_scan or now_ms() - started > env.deadline_ms then break end
    end

    table.sort(promoted, function(a, b)
        if a.score == b.score then return a.order < b.order end
        return a.score > b.score
    end)

    if #normal > 0 then
        yield(normal[1])
        for _, item in ipairs(promoted) do yield(item.cand) end
        for i = 2, #normal do yield(normal[i]) end
    else
        for _, item in ipairs(promoted) do yield(item.cand) end
    end

    while true do
        local cand = iter(state, key)
        key = cand
        if cand == nil then break end

        if not emitted_text[cand.text] then yield(cand) end
    end
end

return M
