package cn.drcomo.corelib.message;

// ========== 显式导入（按包名排序）==========
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IllegalFormatException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
 * </pre>
 */
public class MessageService {

    /* -------------------- 私有字段 -------------------- */

    // 插件实例（目前未用到，如有需要可打开）
    // private final Plugin plugin;

    private final DebugUtil logger;
    private final YamlUtil yamlUtil;
    private final PlaceholderAPIUtil placeholderUtil;

    /** 语言文件中的原始键值缓存 */
    private final Map<String, String> messages = new HashMap<>();
    /** 上下文 -> 消息缓存 */
    private final Map<Object, List<String>> contextMessages = new HashMap<>();
    /** 内部占位符处理器 {key[:args]} */
    private final Map<String, BiFunction<Player, String, String>> internalHandlers = new HashMap<>();

    /* ---------- 可配置字段 ---------- */
    private String langConfigPath;
    private String keyPrefix;

    /** 默认内部占位符正则：{key} / {key:args} */
    private static final Pattern DEFAULT_INTERNAL_PLACEHOLDER_PATTERN =
            Pattern.compile("\\{([a-zA-Z0-9_]+)(?::([^}]*))?\\}");

    private Pattern internalPlaceholderPattern = DEFAULT_INTERNAL_PLACEHOLDER_PATTERN;
    /** 额外占位符扩展规则 */
    private final Map<Pattern, BiFunction<Player, Matcher, String>> extraPlaceholderRules = new LinkedHashMap<>();
    /** prefix+suffix 对应的正则缓存 */
    private final Map<String, Pattern> delimiterPatternCache = new HashMap<>();

    /* -------------------- 构造函数 -------------------- */

    public MessageService(Plugin plugin,
                          DebugUtil logger,
                          YamlUtil yamlUtil,
                          PlaceholderAPIUtil placeholderUtil,
                          String langConfigPath,
                          String keyPrefix) {

        // this.plugin = plugin;
        this.logger = logger;
        this.yamlUtil = yamlUtil;
        this.placeholderUtil = placeholderUtil;
        this.langConfigPath = langConfigPath;
        this.keyPrefix = keyPrefix == null ? "" : keyPrefix;

        yamlUtil.loadConfig(langConfigPath);
        loadMessages(langConfigPath);
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

    /** 注册自定义占位符解析规则（正则级别） */
    public void addPlaceholderRule(Pattern pattern, BiFunction<Player, Matcher, String> resolver) {
        if (pattern != null && resolver != null) {
            extraPlaceholderRules.put(pattern, resolver);
        }
    }

    /* ==================================================
     *               占位符注册 & 解析核心
     * ================================================== */

    /** 子插件调用：注册 {key[:args]} 占位符 */
    public void registerInternalPlaceholder(String key, BiFunction<Player, String, String> resolver) {
        internalHandlers.put(key.toLowerCase(), resolver);
    }

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
            out.add(processPlaceholders(player, line, custom));
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
        int idx = 0;
        int len = template.length();
        // 使用索引遍历模板，遇到"{}"按顺序替换
        for (int i = 0; i < len; i++) {
            char c = template.charAt(i);
            if (c == '{' && i + 1 < len && template.charAt(i + 1) == '}') {
                if (idx < args.length) {
                    sb.append(String.valueOf(args[idx++]));
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
     * @param player   目标玩家
     * @param template 消息模板，使用 "{}" 表示占位符
     * @param args     需要替换的参数
     */
    /**
     * 通过聊天渠道发送，自定义占位符解析。
     */
    public void send(Player player,
                     String template,
                     Map<String, String> custom,
                     String prefix,
                     String suffix) {
        if (player == null || template == null) return;
        String parsed = processPlaceholdersWithDelimiter(player, template, custom, prefix, suffix);
        sendColorizedRaw(player, ColorUtil.translateColors(parsed));
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
     * @param player   目标玩家
     * @param template 消息模板
     * @param args     参数列表
     */
    /**
     * 通过 ActionBar 发送，自定义占位符解析。
     */
    public void sendActionBar(Player player,
                              String template,
                              Map<String, String> custom,
                              String prefix,
                              String suffix) {
        if (player == null || template == null) return;
        String parsed = processPlaceholdersWithDelimiter(player, template, custom, prefix, suffix);
        sendActionBarRaw(player, ColorUtil.translateColors(parsed));
    }

    public void sendActionBar(Player player, String template, Object... args) {
        if (player == null || template == null) return;
        sendActionBarRaw(player, replaceBraces(template, args));
    }

    /**
     * Title/SubTitle 发送，支持 "{}" 占位符顺序替换。
     * @param player         目标玩家
     * @param titleTemplate  主标题模板
     * @param subTemplate    副标题模板
     * @param args           参数列表（同时作用于主/副标题）
     */
    /**
     * 通过 Title/SubTitle 发送，自定义占位符解析。
     */
    public void sendTitle(Player player,
                          String titleTemplate,
                          String subTemplate,
                          Map<String, String> custom,
                          String prefix,
                          String suffix) {
        if (player == null) return;
        String title = titleTemplate == null ? "" : processPlaceholdersWithDelimiter(player, titleTemplate, custom, prefix, suffix);
        String sub = subTemplate == null ? "" : processPlaceholdersWithDelimiter(player, subTemplate, custom, prefix, suffix);
        sendTitleRaw(player, ColorUtil.translateColors(title), ColorUtil.translateColors(sub));
    }

    public void sendTitle(Player player, String titleTemplate, String subTemplate, Object... args) {
        if (player == null) return;
        String title = titleTemplate == null ? "" : replaceBraces(titleTemplate, args);
        String sub = subTemplate == null ? "" : replaceBraces(subTemplate, args);
        sendTitleRaw(player, title, sub);
    }

    /* -------- 广播 -------- */
    public void broadcast(String key) {
        String msg = parse(key, null, Collections.emptyMap());
        if (msg != null) Bukkit.broadcastMessage(msg);
    }

    /**
     * 全服广播，自定义占位符解析（不依赖语言文件）。
     * @param template  原始模板，可包含自定义占位符
     * @param custom    自定义占位符 map
     * @param prefix    占位符前缀
     * @param suffix    占位符后缀
     */
    public void broadcast(String template,
                          Map<String, String> custom,
                          String prefix,
                          String suffix) {
        String parsed = processPlaceholdersWithDelimiter(null, template, custom, prefix, suffix);
        Bukkit.broadcastMessage(ColorUtil.translateColors(parsed));
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
            String m = parse(key, p, custom);
            if (m != null) sendColorizedRaw(p, m);
        });
    }

    public void broadcastList(String key, Map<String, String> custom, String permission) {
        broadcastToPlayersWithPerm(permission, p -> {
            parseList(key, p, custom).forEach(m -> sendColorizedRaw(p, m));
        });
    }

    /* ==================================================
     *            上下文消息（失败 / 成功 / 通用）
     * ================================================== */

    public void storeMessage(Object context, String key, Map<String, String> custom) {
        String msg = parse(key, null, custom);
        if (msg != null) contextMessages.computeIfAbsent(context, k -> new ArrayList<>()).add(msg);
    }

    public void storeMessageList(Object context, String key, Map<String, String> custom) {
        contextMessages.computeIfAbsent(context, k -> new ArrayList<>())
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
            case "title" -> sendStagedTitle(player, list, Collections.emptyList());
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

    /** 占位符完整处理（自定义 → 内部 → 额外正则 → PlaceholderAPI） */
    private String processPlaceholders(Player player, String msg, Map<String, String> custom) {
        return processPlaceholdersWithDelimiter(player, msg, custom, "%", "%");
    }

    private String processPlaceholdersWithDelimiter(Player player,
                                                    String msg,
                                                    Map<String, String> custom,
                                                    String prefix,
                                                    String suffix) {

        String result = msg;

        // —— 1. 自定义占位符 ——
        if (custom != null && !custom.isEmpty()) {
            // 使用 prefix+suffix 缓存编译后的正则，避免重复构建
            String cacheKey = prefix + suffix;
            Pattern pattern = delimiterPatternCache.get(cacheKey);
            if (pattern == null) {
                pattern = Pattern.compile(prefix + "(?<key>[^" + suffix + "]+)" + suffix);
                delimiterPatternCache.put(cacheKey, pattern);
            }
            Matcher matcher = pattern.matcher(result);
            StringBuffer sb = new StringBuffer();
            while (matcher.find()) {
                String key = matcher.group("key");
                String value = custom.getOrDefault(key, matcher.group(0));
                matcher.appendReplacement(sb, Matcher.quoteReplacement(value));
            }
            matcher.appendTail(sb);
            result = sb.toString();
        }

        // —— 2. 内部 {key[:args]} ——
        if (!internalHandlers.isEmpty()) {
            Matcher m = internalPlaceholderPattern.matcher(result);
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                String k = m.group(1).toLowerCase();
                String args = m.group(2) == null ? "" : m.group(2);
                BiFunction<Player, String, String> fn = internalHandlers.get(k);
                m.appendReplacement(sb, Matcher.quoteReplacement(
                        fn != null ? fn.apply(player, args) : m.group(0)));
            }
            m.appendTail(sb);
            result = sb.toString();
        }

        // —— 3. 额外正则规则 ——
        for (var e : extraPlaceholderRules.entrySet()) {
            Matcher m = e.getKey().matcher(result);
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                m.appendReplacement(sb, Matcher.quoteReplacement(
                        e.getValue().apply(player, m)));
            }
            m.appendTail(sb);
            result = sb.toString();
        }

        // —— 4. PlaceholderAPI ——
        return placeholderUtil.parse(player, result);
    }

    /** 始终确保通过 ColorUtil 转换颜色再发送 */
    private void sendColorizedRaw(CommandSender target, String msg) {
        if (msg == null || msg.isBlank()) return;
        target.sendMessage(ColorUtil.translateColors(msg));
    }

    private void sendColorizedList(CommandSender target, List<String> list) {
        if (list == null) return;
        list.forEach(m -> sendColorizedRaw(target, m));
    }

    private void sendActionBarRaw(Player player, String msg) {
        if (player == null || msg == null) return;
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                new TextComponent(ColorUtil.translateColors(msg)));
    }

    private void sendTitleRaw(Player player, String title, String sub) {
        if (player == null) return;
        player.sendTitle(ColorUtil.translateColors(title),
                         ColorUtil.translateColors(sub),
                         10, 70, 20);
    }

    /** 根据权限批量广播 */
    private void broadcastToPlayersWithPerm(String permission, Consumer<Player> action) {
        if (permission == null || action == null) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission(permission)) action.accept(p);
        }
    }

    /** 统一处理上下文消息发送并清理缓存 */
    private void sendContextMessages(Object context, Player player,
                                     BiConsumer<CommandSender, String> fn) {
        List<String> list = contextMessages.getOrDefault(context, Collections.emptyList());
        list.forEach(msg -> fn.accept(player, msg));
        contextMessages.remove(context);
    }

    /** 将 CommandSender 转换为 Player（控制台返回 null） */
    private Player asPlayer(CommandSender sender) {
        return (sender instanceof Player p) ? p : null;
    }

    /* ============ 未使用字段 / 方法放置到最末尾隐藏 ============ */

    // private final Plugin plugin; // 保留声明但默认注释，防止误删
}
