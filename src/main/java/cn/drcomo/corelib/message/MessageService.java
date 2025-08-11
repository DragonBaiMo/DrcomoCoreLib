package cn.drcomo.corelib.message;

// ========== 显式导入（按包名排序）==========
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IllegalFormatException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import cn.drcomo.corelib.color.ColorUtil;
import cn.drcomo.corelib.config.YamlUtil;
import cn.drcomo.corelib.hook.placeholder.PlaceholderAPIUtil;
import cn.drcomo.corelib.util.DebugUtil;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

/**
 * <pre>
 * 消息统一调度中心（1.18 / Java 17）
 * --------------------------------------------------
 * · 支持多语言 / 内部占位符 / PlaceholderAPI
 * · 支持上下文消息缓存与多渠道发送
 * · 所有公共 API 保持不变，仅对内部实现做重构
 * · 保证所有对玩家输出均经过 {@link ColorUtil} 进行颜色转换
 *
 * 线程安全保证：
 * · 通过 {@link java.util.concurrent.ConcurrentHashMap} 与
 *   {@link java.util.concurrent.CopyOnWriteArrayList} 管理上下文消息，
 *   允许在异步线程安全地写入，在主线程统一发送。
 * 推荐调用方式：
 * · 异步线程使用 {@link #storeMessage(Object, String, Map)}、
 *   {@link #storeMessageList(Object, String, Map)} 收集消息；
 * · 主线程调用 {@link #sendContext(Object, Player, String)}、
 *   {@link #sendContextFailures(Object, Player)} 等方法发送并清理。
 * </pre>
 */
public class MessageService {

    /* -------------------- 私有字段 -------------------- */

    // 插件实例（用于确保在主线程与调度任务中安全调用 Bukkit API）
    private final Plugin plugin;

    private final DebugUtil logger;
    private final YamlUtil yamlUtil;
    private final PlaceholderAPIUtil placeholderUtil;

    /** 语言文件中的原始键值缓存 */
    private final Map<String, String> messages = new HashMap<>();
    /** 上下文 -> 消息缓存 */
    private final Map<Object, List<String>> contextMessages = new ConcurrentHashMap<>();
    /** 内部占位符处理器 {key[:args]} */
    private final Map<String, PlaceholderResolver> internalHandlers = new HashMap<>();

    /* ---------- 可配置字段 ---------- */
    private String langConfigPath;
    private String keyPrefix;

    /** 默认内部占位符正则：{key} / {key:args} */
    private static final Pattern DEFAULT_INTERNAL_PLACEHOLDER_PATTERN =
            Pattern.compile("\\{([a-zA-Z0-9_]+)(?::([^}]*))?}");
    private Pattern internalPlaceholderPattern = DEFAULT_INTERNAL_PLACEHOLDER_PATTERN;

    /** 额外占位符扩展规则 */
    private final Map<Pattern, BiFunction<Player, Matcher, String>> extraPlaceholderRules = new LinkedHashMap<>();
    /** prefix+suffix 对应的正则缓存 */
    private final Map<String, Pattern> delimiterPatternCache = new ConcurrentHashMap<>();

    /** 自定义占位符默认分隔符（用于 key 路径下 custom map 的解析），默认使用 %...% 以保持兼容 */
    private String defaultCustomPrefix = "%";
    private String defaultCustomSuffix = "%";

    /* -------------------- 构造函数 -------------------- */

    public MessageService(Plugin plugin,
                          DebugUtil logger,
                          YamlUtil yamlUtil,
                          PlaceholderAPIUtil placeholderUtil,
                          String langConfigPath,
                          String keyPrefix) {
        this.plugin = plugin;
        this.logger = logger;
        this.yamlUtil = yamlUtil;
        this.placeholderUtil = placeholderUtil;
        this.langConfigPath = langConfigPath;
        this.keyPrefix = keyPrefix == null ? "" : keyPrefix;

        yamlUtil.loadConfig(langConfigPath);
        loadMessages(langConfigPath);
        if (this.plugin == null) {
            logger.warn("MessageService 警告：Plugin 为空，将无法进行主线程切换。");
        }
    }

    /* ==================================================
     *                 语言文件相关方法
     * ================================================== */

    /** 重新加载当前语言文件 */
    public void reloadLanguages() {
        logger.info("重新加载语言文件…");
        yamlUtil.reloadConfig(langConfigPath);
        messages.clear();
        loadMessages(langConfigPath);
        logger.info("语言文件重载完成，共 " + messages.size() + " 条");
    }

    /** 切换语言文件并立即重载 */
    public void switchLanguage(String newPath) {
        this.langConfigPath = newPath;
        yamlUtil.loadConfig(newPath);
        reloadLanguages();
    }

    /** 动态修改统一键前缀 */
    public void setKeyPrefix(String newPrefix) {
        this.keyPrefix = newPrefix == null ? "" : newPrefix;
    }

    /** 指定新的内部占位符匹配正则 */
    public void setInternalPlaceholderPattern(Pattern pattern) {
        if (pattern != null) {
            this.internalPlaceholderPattern = pattern;
        }
    }

    /**
     * 设置自定义占位符默认分隔符（仅影响需要 custom map 的解析路径，如按 key 发送/广播时的 custom 替换）。
     * 默认值为 "%" 和 "%"，调用方可根据语言模板改为 "{" 与 "}"。
     */
    public void setDefaultCustomDelimiters(String prefix, String suffix) {
        this.defaultCustomPrefix = (prefix == null || prefix.isEmpty()) ? "%" : prefix;
        this.defaultCustomSuffix = (suffix == null || suffix.isEmpty()) ? "%" : suffix;
        logger.info("已设置默认自定义占位符分隔符: prefix='" + this.defaultCustomPrefix + "' suffix='" + this.defaultCustomSuffix + "'");
    }

    /** 注册自定义占位符解析规则（正则级别） */
    public void addPlaceholderRule(Pattern pattern, BiFunction<Player, Matcher, String> resolver) {
        if (pattern != null && resolver != null) {
            extraPlaceholderRules.put(pattern, resolver);
        }
    }

    /* ==================================================
     *               占位符注册 & 解析核心
     * ================================================== */

    /**
     * 子插件调用：注册 {key[:args]} 占位符。
     *
     * @param key      占位符标识（不含大括号）
     * @param resolver 占位符解析器
     */
    public void registerInternalPlaceholder(String key, PlaceholderResolver resolver) {
        if (key == null || resolver == null) {
            return;
        }
        internalHandlers.put(key.toLowerCase(), resolver);
    }

    /**
     * 兼容旧接口：接受单字符串参数的注册方法。
     *
     * @param key      占位符标识（不含大括号）
     * @param resolver 旧版解析器（参数串按冒号划分的首段）
     * @deprecated 请改用 {@link #registerInternalPlaceholder(String, PlaceholderResolver)}，以获取完整参数数组
     */
    @Deprecated
    public void registerInternalPlaceholder(String key, BiFunction<Player, String, String> resolver) {
        if (key == null || resolver == null) {
            return;
        }
        registerInternalPlaceholder(key,
                (PlaceholderResolver) (player, args) -> resolver.apply(player, args.length > 0 ? args[0] : ""));
    }

    /**
     * 取消注册内部占位符。
     *
     * @param key 占位符标识（不含大括号）
     */
    public void unregisterInternalPlaceholder(String key) {
        if (key == null) {
            return;
        }
        internalHandlers.remove(key.toLowerCase());
    }

    /* ==================================================
     *               消息获取与格式化
     * ================================================== */

    /**
     * 解析完整键（自动拼接统一前缀）
     */
    private String resolveKey(String key) {
        return keyPrefix.isEmpty() || key.startsWith(keyPrefix) ? key : keyPrefix + key;
    }

    /** 取得原始字符串（无占位符处理） */
    public String getRaw(String key) {
        String actual = resolveKey(key);
        String raw = messages.get(actual);
        if (raw == null) {
            logger.warn("未找到原始消息，键: " + actual);
        }
        return raw;
    }

    /** 使用 {@link String#format} 格式化后返回 */
    public String get(String key, Object... args) {
        String raw = getRaw(key);
        if (raw == null) {
            return "Message not found: " + key;
        }
        try {
            return String.format(raw, args);
        } catch (IllegalFormatException ex) {
            logger.error("格式化失败，键: " + key + " | " + ex.getMessage());
            return raw;
        }
    }

    /* -------------------- 占位符完整解析 -------------------- */

    /**
     * 兼容旧接口，默认使用 "%" 前后缀。
     *
     * @param key    消息键
     * @param player 当前玩家，可为 null
     * @param custom 自定义占位符
     * @return 解析结果
     * @deprecated 请使用 {@link #parseWithDelimiter(String, Player, Map, String, String)}
     */
    @Deprecated
    public String parse(String key, Player player, Map<String, String> custom) {
        return parseWithDelimiter(key, player, custom, "%", "%");
    }

    public String parseWithDelimiter(String key,
                                     Player player,
                                     Map<String, String> custom,
                                     String prefix,
                                     String suffix) {
        String msg = get(key);
        if (msg == null) return null;
        String result = processPlaceholdersWithDelimiter(player, msg, custom, prefix, suffix);
        return ColorUtil.translateColors(result);
    }

    /* -------------------- 列表读取与解析 -------------------- */

    public List<String> getList(String key) {
        String actual = resolveKey(key);
        List<String> list = yamlUtil.getConfig(langConfigPath).getStringList(actual);
        if (list.isEmpty()) logger.warn("未找到消息列表，键: " + actual);
        return list;
    }

    public List<String> parseList(String key, Player player, Map<String, String> custom) {
        List<String> raw = getList(key);
        List<String> out = new ArrayList<>(raw.size());
        for (String line : raw) {
            out.add(processPlaceholdersWithDelimiter(player, line, custom, "%", "%"));
        }
        return out;
    }

    /* ==================================================
     *                 对外发送接口（公共）
     * ================================================== */

    /* -------- 单行 -------- */

    public void send(Player player, String key) {
        String msg = parse(key, player, Collections.emptyMap());
        if (msg != null) sendColorizedRaw(player, msg);
    }

    public void send(CommandSender target, String key, Map<String, String> custom) {
        Player p = asPlayer(target);
        String msg = parse(key, p, custom);
        if (msg != null) sendColorizedRaw(target, msg);
    }

    /* -------- 列表 -------- */

    /**
     * 聊天多行发送，自定义占位符解析。
     */
    public void sendList(CommandSender target,
                         List<String> templates,
                         Map<String, String> custom,
                         String prefix,
                         String suffix) {
        if (templates == null || templates.isEmpty()) return;
        Player p = asPlayer(target);
        List<String> parsed = new ArrayList<>(templates.size());
        for (String t : templates) {
            parsed.add(processPlaceholdersWithDelimiter(p, t, custom, prefix, suffix));
        }
        sendColorizedList(target, parsed);
    }

    public void sendList(CommandSender target, String key) {
        Player p = asPlayer(target);
        sendColorizedList(target, parseList(key, p, Collections.emptyMap()));
    }

    public void sendList(CommandSender target, String key, Map<String, String> custom) {
        Player p = asPlayer(target);
        sendColorizedList(target, parseList(key, p, custom));
    }

    /* -------- 直接原文 -------- */

    public void sendRaw(CommandSender target, String rawMessage) {
        sendColorizedRaw(target, rawMessage);
    }

    public void sendRawList(CommandSender target, List<String> rawMessages) {
        sendColorizedList(target, rawMessages);
    }

    /* -------- ActionBar / Title -------- */

    public void sendOptimizedChat(Player player, List<String> messages) {
        sendColorizedList(player, messages);
    }

    public void sendActionBar(Player player, String key, Map<String, String> custom) {
        String msg = parse(key, player, custom);
        if (msg != null) sendActionBarRaw(player, msg);
    }

    public void sendStagedActionBar(Player player, List<String> messages) {
        if (messages == null) return;
        messages.forEach(m -> sendActionBarRaw(player, m));
    }

    public void sendTitle(Player player, String titleKey, String subKey, Map<String, String> custom) {
        String title = parse(titleKey, player, custom);
        String sub = parse(subKey, player, custom);
        if (title != null && sub != null) sendTitleRaw(player, title, sub);
    }

    public void sendStagedTitle(Player player, List<String> titles, List<String> subtitles) {
        if (titles == null || subtitles == null) return;
        int n = Math.min(titles.size(), subtitles.size());
        for (int i = 0; i < n; i++) {
            sendTitleRaw(player, titles.get(i), subtitles.get(i));
        }
    }

    /* -------- 直接模板发送 (Chat / ActionBar / Title) -------- */

    /**
     * 使用 "{}" 顺序占位符替换。
     * 若占位符数量 < args.length，多余参数将被忽略；若占位符数量不足，则保持原样。
     */
    private String replaceBraces(String template, Object... args) {
        if (template == null) return null;
        StringBuilder sb = new StringBuilder();
        int idx = 0, len = template.length();
        for (int i = 0; i < len; i++) {
            char c = template.charAt(i);
            if (c == '{' && i + 1 < len && template.charAt(i + 1) == '}') {
                if (idx < args.length) {
                    sb.append(args[idx++]);
                } else {
                    sb.append("{}");
                }
                i++;
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * 聊天发送，支持 "{}" 占位符顺序替换。
     * 通过聊天渠道发送，自定义占位符解析。
     */
    public void send(Player player,
                     String template,
                     Map<String, String> custom,
                     String prefix,
                     String suffix) {
        if (player == null || template == null) return;
        sendColorizedRaw(player, parseTranslate(player, template, custom, prefix, suffix));
    }

    /**
     * 直接格式化聊天（保留旧 {} 替换方式）。
     */
    public void sendChat(Player player, String template, Object... args) {
        if (player == null || template == null) return;
        sendColorizedRaw(player, replaceBraces(template, args));
    }

    /**
     * ActionBar 发送，支持 "{}" 占位符顺序替换。
     * 通过 ActionBar 发送，自定义占位符解析。
     */
    public void sendActionBar(Player player,
                              String template,
                              Map<String, String> custom,
                              String prefix,
                              String suffix) {
        if (player == null || template == null) return;
        sendActionBarRaw(player, parseTranslate(player, template, custom, prefix, suffix));
    }

    public void sendActionBar(Player player, String template, Object... args) {
        if (player == null || template == null) return;
        sendActionBarRaw(player, replaceBraces(template, args));
    }

    /**
     * Title/SubTitle 发送，支持 "{}" 占位符顺序替换。
     * 通过 Title/SubTitle 发送，自定义占位符解析。
     */
    public void sendTitle(Player player,
                          String titleTemplate,
                          String subTemplate,
                          Map<String, String> custom,
                          String prefix,
                          String suffix) {
        if (player == null) return;
        String title = titleTemplate == null ? "" : parseTranslate(player, titleTemplate, custom, prefix, suffix);
        String sub = subTemplate == null ? "" : parseTranslate(player, subTemplate, custom, prefix, suffix);
        sendTitleRaw(player, title, sub);
    }

    public void sendTitle(Player player, String titleTemplate, String subTemplate, Object... args) {
        if (player == null) return;
        String title = titleTemplate == null ? "" : replaceBraces(titleTemplate, args);
        String sub = subTemplate == null ? "" : replaceBraces(subTemplate, args);
        sendTitleRaw(player, title, sub);
    }

    /* -------- 广播 -------- */

    public void broadcast(String key) {
        broadcastToPlayersWithPerm(null, p -> {
            String msg = parse(key, p, Collections.emptyMap());
            if (msg != null && !msg.isBlank()) {
                sendColorizedRaw(p, msg);
            }
        });
    }

    /**
     * 全服广播，自定义占位符解析（不依赖语言文件）。
     *
     * @param template 原始模板，可包含自定义占位符
     * @param custom   自定义占位符 map
     * @param prefix   占位符前缀
     * @param suffix   占位符后缀
     */
    public void broadcast(String template,
                          Map<String, String> custom,
                          String prefix,
                          String suffix) {
        broadcastToPlayersWithPerm(null, p -> {
            String m = processPlaceholdersWithDelimiter(p, template, custom, prefix, suffix);
            sendColorizedRaw(p, ColorUtil.translateColors(m));
        });
    }

    /**
     * 权限过滤广播，自定义占位符解析。
     */
    public void broadcast(String template,
                          Map<String, String> custom,
                          String prefix,
                          String suffix,
                          String permission) {
        broadcastToPlayersWithPerm(permission, p -> {
            String m = processPlaceholdersWithDelimiter(p, template, custom, prefix, suffix);
            sendColorizedRaw(p, ColorUtil.translateColors(m));
        });
    }

    public void broadcast(String key, Map<String, String> custom, String permission) {
        broadcastToPlayersWithPerm(permission, p -> {
            String m = parseWithDelimiter(key, p, custom, defaultCustomPrefix, defaultCustomSuffix);
            if (m != null) sendColorizedRaw(p, m);
        });
    }

    public void broadcastList(String key, Map<String, String> custom, String permission) {
        broadcastToPlayersWithPerm(permission, p -> {
            // 列表解析仍按默认 %，如需自定义分隔符建议先通过 getList + sendList 并指定分隔符
            parseList(key, p, custom).forEach(m -> sendColorizedRaw(p, m));
        });
    }

    /**
     * 通过语言键广播（支持自定义分隔符）。
     */
    public void broadcastByKey(String key,
                               Map<String, String> custom,
                               String prefix,
                               String suffix) {
        broadcastToPlayersWithPerm(null, p -> {
            String m = parseWithDelimiter(key, p, custom, prefix, suffix);
            if (m != null) sendColorizedRaw(p, m);
        });
    }

    /**
     * 通过语言键广播到具备权限的玩家（支持自定义分隔符）。
     */
    public void broadcastByKey(String key,
                               Map<String, String> custom,
                               String prefix,
                               String suffix,
                               String permission) {
        broadcastToPlayersWithPerm(permission, p -> {
            String m = parseWithDelimiter(key, p, custom, prefix, suffix);
            if (m != null) sendColorizedRaw(p, m);
        });
    }

    /* ==================================================
     *            上下文消息（失败 / 成功 / 通用）
     * ================================================== */

    public void storeMessage(Object context, String key, Map<String, String> custom) {
        String msg = parse(key, null, custom);
        if (msg != null) contextMessages.computeIfAbsent(context, k -> new CopyOnWriteArrayList<>()).add(msg);
    }

    public void storeMessageList(Object context, String key, Map<String, String> custom) {
        contextMessages.computeIfAbsent(context, k -> new CopyOnWriteArrayList<>())
                .addAll(parseList(key, null, custom));
    }

    public boolean hasMessages(Object context) {
        return countMessages(context) > 0;
    }

    public int countMessages(Object context) {
        List<String> list = contextMessages.get(context);
        return list == null ? 0 : list.size();
    }

    public void sendContext(Object context, Player player, String channel) {
        List<String> list = contextMessages.getOrDefault(context, Collections.emptyList());
        if (list.isEmpty()) return;

        switch (channel.toLowerCase()) {
            case "chat" -> sendOptimizedChat(player, list);
            case "actionbar" -> sendStagedActionBar(player, list);
            case "title" -> sendStagedTitle(player, list, Collections.nCopies(list.size(), ""));
            default -> logger.warn("未知渠道: " + channel);
        }
        contextMessages.remove(context);
    }

    public void sendContextFailures(Object context, Player player) {
        sendContextMessages(context, player, this::sendColorizedRaw);
    }

    public void sendContextSuccesses(Object context, Player player) {
        sendContextMessages(context, player, this::sendColorizedRaw);
    }

    /* ==================================================
     *                  —— 私有工具方法 ——
     * ================================================== */

    /** 加载指定语言文件到缓存 */
    private void loadMessages(String path) {
        YamlConfiguration cfg = yamlUtil.getConfig(path);
        for (String key : cfg.getKeys(true)) {
            if (cfg.isString(key)) messages.put(key, cfg.getString(key));
        }
        logger.info("加载语言文件: " + path + " | 共 " + messages.size() + " 条");
    }

    /**
     * 核心处理：按顺序应用自定义、内部、额外正则、PlaceholderAPI 解析
     */
    private String processPlaceholdersWithDelimiter(Player player,
                                                    String msg,
                                                    Map<String, String> custom,
                                                    String prefix,
                                                    String suffix) {
        String result = msg;
        result = applyCustomPlaceholders(player, result, custom, prefix, suffix);
        result = applyInternalPlaceholders(player, result);
        result = applyExtraPlaceholderRules(player, result);
        return applyPlaceholderAPI(player, result);
    }

    /** 应用自定义占位符解析 */
    private String applyCustomPlaceholders(Player player,
                                           String msg,
                                           Map<String, String> custom,
                                           String prefix,
                                           String suffix) {
        if (custom == null || custom.isEmpty()) return msg;
        Pattern pattern = getDelimiterPattern(prefix, suffix);
        Matcher matcher = pattern.matcher(msg);
        // 预估容量，减少扩容
        StringBuilder sb = new StringBuilder(Math.max(16, msg.length() + custom.size() * 8));
        while (matcher.find()) {
            String key = matcher.group("key");
            String value = custom.getOrDefault(key, matcher.group(0));
            matcher.appendReplacement(sb, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /** 获取或缓存 prefix+suffix 对应的正则 */
    private Pattern getDelimiterPattern(String prefix, String suffix) {
        String pf = (prefix == null || prefix.isEmpty()) ? "%" : prefix;
        String sf = (suffix == null || suffix.isEmpty()) ? "%" : suffix;
        String cacheKey = pf + sf;
        return delimiterPatternCache.computeIfAbsent(cacheKey, k -> {
            String regex = Pattern.quote(pf) + "(?<key>.+?)" + Pattern.quote(sf);
            return Pattern.compile(regex);
        });
    }

    /** 应用内部 {key[:args]} 占位符解析 */
    private String applyInternalPlaceholders(Player player, String msg) {
        if (internalHandlers.isEmpty()) return msg;
        Matcher m = internalPlaceholderPattern.matcher(msg);
        StringBuilder sb = new StringBuilder(msg.length() + 16);
        while (m.find()) {
            String key = m.group(1).toLowerCase();
            String argStr = m.group(2);
            String[] args = (argStr == null || argStr.isEmpty()) ? new String[0] : argStr.split(":");
            PlaceholderResolver resolver = internalHandlers.get(key);
            String replacement = resolver != null ? resolver.resolve(player, args) : m.group(0);
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** 应用额外正则占位符规则 */
    private String applyExtraPlaceholderRules(Player player, String msg) {
        String result = msg;
        for (var entry : extraPlaceholderRules.entrySet()) {
            Matcher m = entry.getKey().matcher(result);
            StringBuilder sb = new StringBuilder();
            while (m.find()) {
                String replacement = entry.getValue().apply(player, m);
                if (replacement == null) {
                    replacement = m.group(0);
                }
                m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
            }
            m.appendTail(sb);
            result = sb.toString();
        }
        return result;
    }

    /** 应用 PlaceholderAPI 解析 */
    private String applyPlaceholderAPI(Player player, String msg) {
        return placeholderUtil.parse(player, msg);
    }

    /** 快捷解析并翻译颜色 */
    private String parseTranslate(Player player,
                                  String template,
                                  Map<String, String> custom,
                                  String prefix,
                                  String suffix) {
        String processed = processPlaceholdersWithDelimiter(player, template, custom, prefix, suffix);
        return ColorUtil.translateColors(processed);
    }

    /** 始终确保通过 ColorUtil 转换颜色再发送 */
    private void sendColorizedRaw(CommandSender target, String msg) {
        if (msg == null || msg.isBlank()) return;
        runSync(() -> target.sendMessage(ColorUtil.translateColors(msg)));
    }

    private void sendColorizedList(CommandSender target, List<String> list) {
        if (list == null) return;
        list.forEach(m -> sendColorizedRaw(target, m));
    }

    

    @SuppressWarnings("deprecation")
    private void sendActionBarRaw(Player player, String msg) {
        if (player == null || msg == null) return;
        // 兼容方式：部分环境 ActionBar 旧 API 标记为过时，保留调用；如需更高级控制请在子插件使用 Adventure API
        runSync(() -> player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                new TextComponent(ColorUtil.translateColors(msg))));
    }

    @SuppressWarnings("deprecation")
    private void sendTitleRaw(Player player, String title, String sub) {
        if (player == null) return;
        // 兼容方式：旧 API 标记为过时，行为保持不变
        runSync(() -> player.sendTitle(ColorUtil.translateColors(title),
                ColorUtil.translateColors(sub),
                10, 70, 20));
    }

    /** 根据权限批量广播 */
    private void broadcastToPlayersWithPerm(String permission, Consumer<Player> action) {
        if (action == null) return;
        runSync(() -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (permission == null || permission.isBlank() || p.hasPermission(permission)) {
                    action.accept(p);
                }
            }
        });
    }

    /** 统一处理上下文消息发送并清理缓存 */
    private void sendContextMessages(Object context, Player player,
                                     BiConsumer<CommandSender, String> fn) {
        List<String> list = contextMessages.getOrDefault(context, Collections.emptyList());
        list.forEach(msg -> fn.accept(player, msg));
        // 发送完毕后移除上下文，避免并发修改异常
        contextMessages.remove(context);
    }

    /** 将 CommandSender 转换为 Player（控制台返回 null） */
    private Player asPlayer(CommandSender sender) {
        return (sender instanceof Player p) ? p : null;
    }

    /**
     * 确保在主线程执行 Bukkit API 调用。
     */
    private void runSync(Runnable task) {
        if (task == null) return;
        if (Bukkit.isPrimaryThread() || plugin == null) {
            if (plugin == null) {
                logger.warn("消息在异步上下文且 Plugin 为空，已直接在当前线程执行。请修正构造参数传入有效 Plugin。");
            }
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    /* ============ 未使用字段 / 方法放置到最末尾隐藏 ============ */

    // private final Plugin plugin; // 保留声明但默认注释，防止误删
}
