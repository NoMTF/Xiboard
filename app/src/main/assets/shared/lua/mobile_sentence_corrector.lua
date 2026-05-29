-- Bounded sentence-level correction for mobile pinyin.
-- This layer is deliberately small: it only combines high-frequency local
-- phrase fragments and exact typo maps, then stops within a short deadline.

local M = {}

local cache = {}
local cache_order = {}

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

local function add_item(map, item, code, cost)
    if code == nil or code == "" then return end
    local list = map[code]
    if not list then
        list = {}
        map[code] = list
    end
    list[#list + 1] = {
        code = code,
        canon = item.canon,
        lookup = item.lookup,
        text = item.text,
        cost = cost or item.cost or 0,
        changed = code ~= item.canon,
    }
end

local function build_phrase_map()
    local phrases = {
        { canon = "wo", lookup = "wo", text = "我" },
        { canon = "ni", lookup = "ni", text = "你", variants = { "li" }, cost = 2 },
        { canon = "ta", lookup = "ta", text = "他" },
        { canon = "a", lookup = "a", text = "啊" },
        { canon = "ba", lookup = "ba", text = "吧" },
        { canon = "ma", lookup = "ma", text = "吗" },
        { canon = "le", lookup = "le", text = "了" },
        { canon = "de", lookup = "de", text = "的" },
        { canon = "zai", lookup = "zai", text = "在" },
        { canon = "shi", lookup = "shi", text = "是" },
        { canon = "yao", lookup = "yao", text = "要" },
        { canon = "xiang", lookup = "xiang", text = "想" },
        { canon = "qu", lookup = "qu", text = "去" },
        { canon = "kan", lookup = "kan", text = "看" },
        { canon = "chi", lookup = "chi", text = "吃" },
        { canon = "fa", lookup = "fa", text = "发" },
        { canon = "bang", lookup = "bang", text = "帮" },
        { canon = "wen", lookup = "wen", text = "问" },
        { canon = "deng", lookup = "deng", text = "等" },
        { canon = "xian", lookup = "xian", text = "先" },
        { canon = "zai", lookup = "zai", text = "再" },
        { canon = "women", lookup = "wo men", text = "我们" },
        { canon = "nimen", lookup = "ni men", text = "你们", variants = { "limen" }, cost = 2 },
        { canon = "nihao", lookup = "ni hao", text = "你好", variants = { "lihao" }, cost = 2 },
        { canon = "nihaoma", lookup = "ni hao ma", text = "你好吗", variants = { "lihaoma" }, cost = 2 },
        { canon = "haoduo", lookup = "hao duo", text = "好多", variants = { "haiduo", "haidui", "haodui" }, cost = 1 },
        { canon = "haode", lookup = "hao de", text = "好的" },
        { canon = "haoba", lookup = "hao ba", text = "好吧" },
        { canon = "meishi", lookup = "mei shi", text = "没事", variants = { "meish" }, cost = 1 },
        { canon = "meiyou", lookup = "mei you", text = "没有", variants = { "meiyo" }, cost = 1 },
        { canon = "meiwenti", lookup = "mei wen ti", text = "没问题", variants = { "meiwenyi" }, cost = 1 },
        { canon = "buzhidao", lookup = "bu zhi dao", text = "不知道", variants = { "buzidao", "buzhdao", "buzhido", "bushidao", "bushizhidao" }, cost = 1 },
        { canon = "buyong", lookup = "bu yong", text = "不用", variants = { "buyon" }, cost = 1 },
        { canon = "buyao", lookup = "bu yao", text = "不要" },
        { canon = "keyi", lookup = "ke yi", text = "可以", variants = { "keyo" }, cost = 1 },
        { canon = "bukeyi", lookup = "bu ke yi", text = "不可以" },
        { canon = "duibuqi", lookup = "dui bu qi", text = "对不起", variants = { "duibuq" }, cost = 1 },
        { canon = "xiexie", lookup = "xie xie", text = "谢谢", variants = { "xieixie" }, cost = 1 },
        { canon = "weishenme", lookup = "wei shen me", text = "为什么", variants = { "weisheme", "weishenm", "weishenmw" }, cost = 1 },
        { canon = "zenmele", lookup = "zen me le", text = "怎么了", variants = { "zhenmele", "zengmele", "zemmele", "zenmle" }, cost = 1 },
        { canon = "zenmeban", lookup = "zen me ban", text = "怎么办" },
        { canon = "shenme", lookup = "shen me", text = "什么" },
        { canon = "shibushi", lookup = "shi bu shi", text = "是不是" },
        { canon = "yaobuyao", lookup = "yao bu yao", text = "要不要" },
        { canon = "nengbuneng", lookup = "neng bu neng", text = "能不能" },
        { canon = "youmeiyou", lookup = "you mei you", text = "有没有" },
        { canon = "youdian", lookup = "you dian", text = "有点", variants = { "youdina" }, cost = 1 },
        { canon = "chabuduo", lookup = "cha bu duo", text = "差不多" },
        { canon = "wojuede", lookup = "wo jue de", text = "我觉得", variants = { "wojude" }, cost = 1 },
        { canon = "juede", lookup = "jue de", text = "觉得" },
        { canon = "woxiang", lookup = "wo xiang", text = "我想" },
        { canon = "woxiangyao", lookup = "wo xiang yao", text = "我想要" },
        { canon = "woxiangwenyixia", lookup = "wo xiang wen yi xia", text = "我想问一下", variants = { "woxiangwenixia", "wojiangwenyixia" }, cost = 2 },
        { canon = "kanxia", lookup = "kan xia", text = "看下" },
        { canon = "kanyixia", lookup = "kan yi xia", text = "看一下" },
        { canon = "dengyixia", lookup = "deng yi xia", text = "等一下", variants = { "dengixia" }, cost = 1 },
        { canon = "mafan", lookup = "ma fan", text = "麻烦" },
        { canon = "bangwo", lookup = "bang wo", text = "帮我" },
        { canon = "bangmang", lookup = "bang mang", text = "帮忙" },
        { canon = "faxia", lookup = "fa xia", text = "发下" },
        { canon = "fayixia", lookup = "fa yi xia", text = "发一下" },
        { canon = "fageiwo", lookup = "fa gei wo", text = "发给我" },
        { canon = "geiwo", lookup = "gei wo", text = "给我" },
        { canon = "xianzai", lookup = "xian zai", text = "现在" },
        { canon = "jintian", lookup = "jin tian", text = "今天", variants = { "jinrian", "jintain" }, cost = 1 },
        { canon = "mingtian", lookup = "ming tian", text = "明天", variants = { "migngtian", "migtian" }, cost = 1 },
        { canon = "zuotian", lookup = "zuo tian", text = "昨天", variants = { "zuotain" }, cost = 1 },
        { canon = "tianqi", lookup = "tian qi", text = "天气", variants = { "tianq" }, cost = 1 },
        { canon = "bucuo", lookup = "bu cuo", text = "不错", variants = { "bucou", "buco" }, cost = 1 },
        { canon = "feichang", lookup = "fei chang", text = "非常", variants = { "feichan" }, cost = 1 },
        { canon = "zhongguo", lookup = "zhong guo", text = "中国", variants = { "zongguo" }, cost = 2 },
        { canon = "yiqiqu", lookup = "yi qi qu", text = "一起去" },
        { canon = "yiqichi", lookup = "yi qi chi", text = "一起吃" },
        { canon = "chuqu", lookup = "chu qu", text = "出去" },
        { canon = "chifan", lookup = "chi fan", text = "吃饭" },
        { canon = "chiwanfan", lookup = "chi wan fan", text = "吃完饭" },
        { canon = "wanfan", lookup = "wan fan", text = "晚饭" },
        { canon = "zaofan", lookup = "zao fan", text = "早饭" },
        { canon = "wufan", lookup = "wu fan", text = "午饭" },
        { canon = "huijia", lookup = "hui jia", text = "回家" },
        { canon = "shangban", lookup = "shang ban", text = "上班" },
        { canon = "xiaban", lookup = "xia ban", text = "下班" },
        { canon = "huishuo", lookup = "hui shuo", text = "会说" },
        { canon = "zaishuo", lookup = "zai shuo", text = "再说" },
        { canon = "zaikan", lookup = "zai kan", text = "再看" },
        { canon = "zhijie", lookup = "zhi jie", text = "直接" },
        { canon = "kaishi", lookup = "kai shi", text = "开始" },
        { canon = "yijing", lookup = "yi jing", text = "已经" },
        { canon = "wenti", lookup = "wen ti", text = "问题" },
        { canon = "wendang", lookup = "wen dang", text = "文档" },
        { canon = "tupian", lookup = "tu pian", text = "图片" },
        { canon = "xiaoxi", lookup = "xiao xi", text = "消息" },
        { canon = "weizhi", lookup = "wei zhi", text = "位置" },
        { canon = "shijian", lookup = "shi jian", text = "时间" },
        { canon = "budaba", lookup = "bu da ba", text = "不大吧" },
        { canon = "buda", lookup = "bu da", text = "不大" },
        { canon = "yinggai", lookup = "ying gai", text = "应该" },
        { canon = "bijiao", lookup = "bi jiao", text = "比较" },
        { canon = "zhende", lookup = "zhen de", text = "真的" },
    }

    local map = {}
    local max_len = 0
    local min_len = 99
    for _, item in ipairs(phrases) do
        add_item(map, item, item.canon, 0)
        if item.variants then
            for _, variant in ipairs(item.variants) do
                add_item(map, item, variant, item.cost or 1)
            end
        end
        max_len = math.max(max_len, #item.canon)
        min_len = math.min(min_len, #item.canon)
    end
    return map, min_len, max_len
end

local function build_exact_map()
    return {
        ["jinriantianqibucuo"] = { text = "今天天气不错", canon = "jintiantianqibucuo", lookup = "jin tian tian qi bu cuo", cost = 1 },
        ["jintaintianqibucuo"] = { text = "今天天气不错", canon = "jintiantianqibucuo", lookup = "jin tian tian qi bu cuo", cost = 1 },
        ["jintiantianqibucou"] = { text = "今天天气不错", canon = "jintiantianqibucuo", lookup = "jin tian tian qi bu cuo", cost = 1 },
        ["jintiantianqibucuowomenchuquchifan"] = { text = "今天天气不错我们出去吃饭", canon = "jintiantianqibucuowomenchuquchifan", lookup = "jin tian tian qi bu cuo wo men chu qu chi fan", cost = 0 },
        ["jinriantianqibucuowomenchuquchifan"] = { text = "今天天气不错我们出去吃饭", canon = "jintiantianqibucuowomenchuquchifan", lookup = "jin tian tian qi bu cuo wo men chu qu chi fan", cost = 2 },
        ["jintiantianqibucouwomenchuquchifan"] = { text = "今天天气不错我们出去吃饭", canon = "jintiantianqibucuowomenchuquchifan", lookup = "jin tian tian qi bu cuo wo men chu qu chi fan", cost = 1 },
        ["lihaowoxiangwenyixia"] = { text = "你好我想问一下", canon = "nihaowoxiangwenyixia", lookup = "ni hao wo xiang wen yi xia", cost = 2 },
        ["woxiangwenixia"] = { text = "我想问一下", canon = "woxiangwenyixia", lookup = "wo xiang wen yi xia", cost = 1 },
        ["woxiangyaochuquchifan"] = { text = "我想要出去吃饭", canon = "woxiangyaochuquchifan", lookup = "wo xiang yao chu qu chi fan", cost = 0 },
        ["womenchuquchifanba"] = { text = "我们出去吃饭吧", canon = "womenchuquchifanba", lookup = "wo men chu qu chi fan ba", cost = 0 },
        ["dengyixiafageiwo"] = { text = "等一下发给我", canon = "dengyixiafageiwo", lookup = "deng yi xia fa gei wo", cost = 0 },
        ["dengixiafageiwo"] = { text = "等一下发给我", canon = "dengyixiafageiwo", lookup = "deng yi xia fa gei wo", cost = 1 },
        ["nibangwofaxia"] = { text = "你帮我发下", canon = "nibangwofaxia", lookup = "ni bang wo fa xia", cost = 0 },
        ["libangwofaxia"] = { text = "你帮我发下", canon = "nibangwofaxia", lookup = "ni bang wo fa xia", cost = 2 },
        ["nibangwofayixia"] = { text = "你帮我发一下", canon = "nibangwofayixia", lookup = "ni bang wo fa yi xia", cost = 0 },
        ["libangwofayixia"] = { text = "你帮我发一下", canon = "nibangwofayixia", lookup = "ni bang wo fa yi xia", cost = 2 },
        ["xianzaiyoumeiyoushijian"] = { text = "现在有没有时间", canon = "xianzaiyoumeiyoushijian", lookup = "xian zai you mei you shi jian", cost = 0 },
        ["wojudeyinggaikeyi"] = { text = "我觉得应该可以", canon = "wojuedeyinggaikeyi", lookup = "wo jue de ying gai ke yi", cost = 1 },
        ["wojuedeyinggaikeyi"] = { text = "我觉得应该可以", canon = "wojuedeyinggaikeyi", lookup = "wo jue de ying gai ke yi", cost = 0 },
        ["weishenmebuzhidao"] = { text = "为什么不知道", canon = "weishenmebuzhidao", lookup = "wei shen me bu zhi dao", cost = 0 },
        ["weishemebuzhidao"] = { text = "为什么不知道", canon = "weishenmebuzhidao", lookup = "wei shen me bu zhi dao", cost = 1 },
        ["zongguorenhaoduo"] = { text = "中国人好多", canon = "zhongguorenhaoduo", lookup = "zhong guo ren hao duo", cost = 2 },
        ["zongguohaoduo"] = { text = "中国好多", canon = "zhongguohaoduo", lookup = "zhong guo hao duo", cost = 2 },
    }
end

local function remember(key, value)
    cache[key] = value
    cache_order[#cache_order + 1] = key
    if #cache_order > 96 then
        local old_key = table.remove(cache_order, 1)
        cache[old_key] = nil
    end
end

local function make_candidate_tuple(text, canon, lookup, cost, changed, segments)
    return {
        text = text,
        canon = canon,
        lookup = lookup,
        cost = cost or 0,
        changed = changed,
        segments = segments or 1,
    }
end

local function sentence_candidates(code, env, started)
    local cache_key = code .. ":" .. env.max_candidates
    if cache[cache_key] then return cache[cache_key] end

    local results = {}
    local exact = env.exact_map[code]
    if exact then
        results[#results + 1] = make_candidate_tuple(exact.text, exact.canon, exact.lookup, exact.cost, exact.canon ~= code, 1)
    end

    local function dfs(pos, parts, canon_parts, lookup_parts, cost, changed)
        if now_ms() - started > env.deadline_ms then return end
        if #results >= env.max_candidates then return end
        if #parts > env.max_segments then return end
        if pos > #code then
            if #parts >= env.min_segments and (changed or #code >= env.min_sentence_input_len) then
                results[#results + 1] = make_candidate_tuple(
                    table.concat(parts, ""),
                    table.concat(canon_parts, ""),
                    table.concat(lookup_parts, " "),
                    cost,
                    changed,
                    #parts
                )
            end
            return
        end

        local remaining = #code - pos + 1
        if remaining < env.min_phrase_len then return end

        for len = math.min(env.max_phrase_len, remaining), env.min_phrase_len, -1 do
            local key = code:sub(pos, pos + len - 1)
            local items = env.phrase_map[key]
            if items then
                for _, item in ipairs(items) do
                    if #parts >= env.max_segments then return end
                    parts[#parts + 1] = item.text
                    canon_parts[#canon_parts + 1] = item.canon
                    lookup_parts[#lookup_parts + 1] = item.lookup
                    dfs(pos + len, parts, canon_parts, lookup_parts, cost + item.cost, changed or item.changed)
                    parts[#parts] = nil
                    canon_parts[#canon_parts] = nil
                    lookup_parts[#lookup_parts] = nil
                    if #results >= env.max_candidates then return end
                end
            end
        end
    end

    dfs(1, {}, {}, {}, 0, false)

    table.sort(results, function(a, b)
        if a.changed ~= b.changed then return a.changed end
        if a.cost == b.cost then return a.segments < b.segments end
        return a.cost < b.cost
    end)

    while #results > env.max_candidates do
        table.remove(results)
    end
    remember(cache_key, results)
    return results
end

function M.init(env)
    local config = env.engine.schema.config
    env.name_space = env.name_space:gsub("^*", "")
    env.min_input_len = read_int(config, env.name_space .. "/min_input_len", 7)
    env.max_input_len = read_int(config, env.name_space .. "/max_input_len", 42)
    env.min_sentence_input_len = read_int(config, env.name_space .. "/min_sentence_input_len", 12)
    env.min_segments = read_int(config, env.name_space .. "/min_segments", 2)
    env.max_segments = read_int(config, env.name_space .. "/max_segments", 8)
    env.max_candidates = read_int(config, env.name_space .. "/max_candidates", 2)
    env.deadline_ms = read_int(config, env.name_space .. "/deadline_ms", 12)
    env.quality = read_int(config, env.name_space .. "/quality", 3)
    env.show_marker = read_bool(config, env.name_space .. "/show_marker", true)
    env.phrase_map, env.min_phrase_len, env.max_phrase_len = build_phrase_map()
    env.min_phrase_len = read_int(config, env.name_space .. "/min_phrase_len", math.max(2, env.min_phrase_len))
    env.exact_map = build_exact_map()
end

function M.func(input, seg, env)
    local started = now_ms()
    local code = normalize_code(input)
    if #code < env.min_input_len or #code > env.max_input_len then return end

    local candidates = sentence_candidates(code, env, started)
    local emitted = {}
    for order, item in ipairs(candidates) do
        if now_ms() - started > env.deadline_ms then return end
        if item.text ~= "" and not emitted[item.text] then
            emitted[item.text] = true
            local comment = ""
            if env.show_marker then
                local marker = item.changed and "@sent! " or "@sent "
                comment = marker .. code .. ">" .. item.canon
            end
            local cand = Candidate("mobile_sentence", seg.start, seg._end, item.text, comment)
            cand.preedit = item.canon
            cand.quality = env.quality + (item.changed and 3.5 or 1.0) - item.cost * 0.18 - order * 0.03
            yield(cand)
        end
    end
end

return M
