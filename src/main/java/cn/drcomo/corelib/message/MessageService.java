package cn.drcomo.corelib.message;

// 显示导入所有必要类
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;

import cn.drcomo.corelib.color.ColorUtil;
import cn.drcomo.corelib.util.DebugUtil;
import cn.drcomo.corelib.config.YamlUtil;
import cn.drcomo.corelib.hook.placeholder.PlaceholderAPIUtil;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IllegalFormatException;
import java.util.LinkedHashMap;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 管理和发送 Bukkit/Spigot 插件消息的工具类。
 * 支持多语言、内部占位符({key} 或 {key:args})、%PAPI% 占位符解析，
 * 以及基于上下文的消息管理。
 */
public class MessageService {

    // === 私有字段 ===
    // 插件实例，用于访问插件数据和资源（当前未使用，已注释）
//    private final Plugin plugin;
    // 日志工具，用于调试、信息、警告和错误日志
    private final DebugUtil logger;
    // YAML 工具，用于加载和管理语言文件
    private final YamlUtil yamlUtil;
    // PlaceholderAPI 工具实例
    private final PlaceholderAPIUtil placeholderUtil;
    // 缓存语言文件中的原始消息
    private final Map<String, String> messages = new HashMap<>();
    // 基于上下文的消息存储
    private final Map<Object, List<String>> contextMessages = new HashMap<>();
    // 内部占位符处理器：{key[:args]} → resolver
    private final Map<String, BiFunction<Player, String, String>> internalHandlers = new HashMap<>();
    // === 可配置字段 ===
    /** 语言文件路径（不含 .yml） */
    private String langConfigPath;
    /** 消息键统一前缀，可为空 */
    private String keyPrefix;
    /** 默认内部占位符匹配正则：{key} 或 {key:args} */
    private static final Pattern DEFAULT_INTERNAL_PLACEHOLDER_PATTERN =
            Pattern.compile("\\{([a-zA-Z0-9_]+)(?::([^}]*))?\\}");
    /** 当前内部占位符匹配正则 */
    private Pattern internalPlaceholderPattern = DEFAULT_INTERNAL_PLACEHOLDER_PATTERN;
    /** 额外占位符解析规则 */
    private final Map<Pattern, BiFunction<Player, Matcher, String>> extraPlaceholderRules = new LinkedHashMap<>();

    // === 构造函数 ===
    /**
     * 构造函数，允许调用方自定义语言文件及键前缀。
     * @param plugin          插件实例
     * @param logger          已创建的 DebugUtil 实例
     * @param yamlUtil        已创建的 YamlUtil 实例
     * @param placeholderUtil PlaceholderAPIUtil 实例，用于占位符解析
     * @param langConfigPath  语言文件路径（不含 .yml，可含相对目录）
     * @param keyPrefix       统一追加到所有键前的前缀，可为空
     */
    public MessageService(Plugin plugin, DebugUtil logger, YamlUtil yamlUtil,
                          PlaceholderAPIUtil placeholderUtil,
                          String langConfigPath, String keyPrefix) {
//        this.plugin = plugin;  // 未调用，已注释
        this.logger = logger;
        this.yamlUtil = yamlUtil;
        this.placeholderUtil = placeholderUtil;
        this.langConfigPath = langConfigPath;
        this.keyPrefix = keyPrefix == null ? "" : keyPrefix;
        yamlUtil.loadConfig(this.langConfigPath);
        loadMessages(this.langConfigPath);
    }

    // === 初始化和语言切换 ===
    /** 重新加载语言文件并刷新消息缓存。 */
    public void reloadLanguages() {
        logger.info("重新加载语言文件...");
        yamlUtil.reloadConfig(langConfigPath);
        messages.clear();
        loadMessages(langConfigPath);
        logger.info("语言文件重载完成，共加载消息: " + messages.size() + " 条");
    }

    /**
     * 切换语言文件路径并立即重新加载。
     * @param newPath 新的语言文件路径（不含 .yml）
     */
    public void switchLanguage(String newPath) {
        this.langConfigPath = newPath;
        yamlUtil.loadConfig(this.langConfigPath);
        reloadLanguages();
    }

    /**
     * 动态修改键前缀。
     * @param newPrefix 新前缀，可为 null
     */
    public void setKeyPrefix(String newPrefix) {
        this.keyPrefix = newPrefix == null ? "" : newPrefix;
    }

    /**
     * 设置内部占位符的匹配正则。
     * @param pattern 新正则
     */
    public void setInternalPlaceholderPattern(Pattern pattern) {
        if (pattern != null) {
            this.internalPlaceholderPattern = pattern;
        }
    }

    /**
     * 新增一个自定义占位符解析规则。
     * @param pattern  匹配的正则
     * @param resolver 解析函数，入参为玩家和匹配器
     */
    public void addPlaceholderRule(Pattern pattern, BiFunction<Player, Matcher, String> resolver) {
        if (pattern != null && resolver != null) {
            extraPlaceholderRules.put(pattern, resolver);
        }
    }

    // === 内部占位符注册 ===
    /**
     * 注册一个内部占位符，形如 {key} 或 {key:args}。
     * 子插件 onEnable() 中调用即可。
     */
    public void registerInternalPlaceholder(String key, BiFunction<Player, String, String> resolver) {
        internalHandlers.put(key.toLowerCase(), resolver);
    }

    // === 消息获取与解析 ===
    /**
     * 解析完整键名，自动补全配置的 keyPrefix 前缀。
     */
    private String resolveKey(String key) {
        return keyPrefix.isEmpty() || key.startsWith(keyPrefix)
                ? key
                : keyPrefix + key;
    }

    /** 获取原始消息并记录日志。 */
    public String getRaw(String key) {
        String actual = resolveKey(key);
        String raw = messages.get(actual);
        if (raw == null) {
            logger.warn("未找到原始消息，键: " + actual);
        } else {
            logger.debug("获取原始消息，键: " + actual + " -> " + raw);
        }
        return raw;
    }

    /** 格式化并获取消息，使用 String.format。 */
    public String get(String key, Object... args) {
        String raw = getRaw(key);
        if (raw == null) {
            logger.warn("获取消息失败，键: " + key);
            return "Message not found: " + key;
        }
        try {
            String fmt = String.format(raw, args);
            logger.debug("格式化消息，键: " + key + " -> " + fmt);
            return fmt;
        } catch (IllegalFormatException e) {
            logger.error("消息格式化失败，键: " + key + "，错误: " + e.getMessage());
            return raw;
        }
    }

    /**
     * 完整解析消息并替换占位符。
     * 1. String.format
     * 2. 自定义 %placeholder%
     * 3. 内部 {key[:args]}
     * 4. PlaceholderAPI %plugin_key%
     * 5. 颜色代码转换
     * 
     * @deprecated 请使用 {@link #parseWithDelimiter(String, Player, Map, String, String)} 方法，支持自定义占位符分隔符
     */
    @Deprecated
    public String parse(String key, Player player, Map<String, String> custom) {
        return parseWithDelimiter(key, player, custom, "%", "%");
    }
    
    /**
     * 使用自定义分隔符解析消息并替换占位符。
     * 1. String.format
     * 2. 自定义占位符（支持自定义前后缀）
     * 3. 内部 {key[:args]}
     * 4. PlaceholderAPI %plugin_key%
     * 5. 颜色代码转换
     * 
     * @param key 消息键
     * @param player 玩家，可为 null
     * @param custom 自定义占位符映射
     * @param prefix 占位符前缀，如 "{" 或 "%"
     * @param suffix 占位符后缀，如 "}" 或 "%"
     * @return 解析后的消息，如果键不存在则返回 null
     */
    public String parseWithDelimiter(String key, Player player, Map<String, String> custom, String prefix, String suffix) {
        String msg = get(key);
        if (msg == null) {
            logger.warn("解析失败，消息为 null，键: " + key);
            return null;
        }
        String result = processPlaceholdersWithDelimiter(player, msg, custom, prefix, suffix);
        // 应用颜色转换
        return ColorUtil.translateColors(result);
    }

    /** 获取原始字符串列表。 */
    public List<String> getList(String key) {
        String actual = resolveKey(key);
        YamlConfiguration cfg = yamlUtil.getConfig(langConfigPath);
        List<String> list = cfg.getStringList(actual);
        if (list.isEmpty()) {
            logger.warn("未找到消息列表，键: " + actual);
        } else {
            logger.debug("获取消息列表，键: " + actual + "，条目数: " + list.size());
        }
        return list;
    }

    /** 解析列表并替换占位符。 */
    public List<String> parseList(String key, Player player, Map<String, String> custom) {
        List<String> raw = getList(key);
        List<String> out = new ArrayList<>(raw.size());
        for (String line : raw) {
            String msg = processPlaceholders(player, line, custom);
            out.add(msg);
            logger.debug("解析列表项，键: " + key + " -> " + msg);
        }
        return out;
    }

    // === 消息发送方法 ===
    public void send(Player player, String key) {
        String msg = parse(key, player, Collections.emptyMap());
        if (msg != null) {
            sendColorizedRaw(player, msg);
        } else {
            logger.warn("发送消息失败，键: " + key);
        }
    }

    public void send(CommandSender target, String key, Map<String, String> custom) {
        Player p = (target instanceof Player) ? (Player) target : null;
        String msg = parse(key, p, custom);
        if (msg != null) {
            sendColorizedRaw(target, msg);
        } else {
            logger.warn("发送消息失败，键: " + key);
        }
    }

    public void sendList(CommandSender target, String key) {
        Player p = (target instanceof Player) ? (Player) target : null;
        sendColorizedList(target, parseList(key, p, Collections.emptyMap()));
    }

    public void sendList(CommandSender target, String key, Map<String, String> custom) {
        Player p = (target instanceof Player) ? (Player) target : null;
        sendColorizedList(target, parseList(key, p, custom));
    }

    public void sendRaw(CommandSender target, String rawMessage) {
        sendColorizedRaw(target, rawMessage);
    }

    public void sendRawList(CommandSender target, List<String> rawMessages) {
        sendColorizedList(target, rawMessages);
    }

    public void sendOptimizedChat(Player player, List<String> messages) {
        logger.debug("优化发送聊天消息给: " + player.getName());
        sendColorizedList(player, messages);
    }

    public void sendActionBar(Player player, String key, Map<String, String> custom) {
        String msg = parse(key, player, custom);
        if (msg != null) {
            sendActionBarRaw(player, msg);
        } else {
            logger.warn("发送 ActionBar 失败，键: " + key);
        }
    }

    public void sendStagedActionBar(Player player, List<String> messages) {
        for (String msg : messages) {
            sendActionBarRaw(player, msg);
        }
    }

    public void sendTitle(Player player, String titleKey, String subKey, Map<String, String> custom) {
        String title = parse(titleKey, player, custom);
        String sub = parse(subKey, player, custom);
        if (title != null && sub != null) {
            sendTitleRaw(player, title, sub);
        } else {
            logger.warn("发送 Title 失败，键: " + titleKey + " / " + subKey);
        }
    }

    public void sendStagedTitle(Player player, List<String> titles, List<String> subtitles) {
        int n = Math.min(titles.size(), subtitles.size());
        for (int i = 0; i < n; i++) {
            sendTitleRaw(player, titles.get(i), subtitles.get(i));
        }
    }

    // === 广播方法 ===
    public void broadcast(String key) {
        String msg = parse(key, null, Collections.emptyMap());
        if (msg != null) {
            String col = ColorUtil.translateColors(msg);
            Bukkit.broadcastMessage(col);
            logger.debug("广播消息，键: " + key + " -> " + col);
        } else {
            logger.warn("广播失败，键: " + key);
        }
    }

    public void broadcast(String key, Map<String, String> custom, String permission) {
        broadcastToPermission(key, custom, permission);
    }

    public void broadcastList(String key, Map<String, String> custom, String permission) {
        broadcastListToPermission(key, custom, permission);
    }

    // === 上下文消息管理 ===
    public void storeMessage(Object context, String key, Map<String, String> custom) {
        String msg = parse(key, null, custom);
        if (msg != null) {
            contextMessages.computeIfAbsent(context, k -> new ArrayList<>()).add(msg);
        }
    }

    public void storeMessageList(Object context, String key, Map<String, String> custom) {
        contextMessages.computeIfAbsent(context, k -> new ArrayList<>())
                .addAll(parseList(key, null, custom));
    }

    public boolean hasMessages(Object context) {
        List<String> list = contextMessages.get(context);
        return list != null && !list.isEmpty();
    }

    public int countMessages(Object context) {
        List<String> list = contextMessages.get(context);
        return list == null ? 0 : list.size();
    }

    public void sendContext(Object context, Player player, String channel) {
        List<String> list = contextMessages.getOrDefault(context, Collections.emptyList());
        if (list.isEmpty()) {
            return;
        }
        switch (channel.toLowerCase()) {
            case "chat":
                sendOptimizedChat(player, list);
                break;
            case "actionbar":
                sendStagedActionBar(player, list);
                break;
            case "title":
                sendStagedTitle(player, list, Collections.emptyList());
                break;
            default:
                logger.warn("未知渠道: " + channel);
        }
        contextMessages.remove(context);
    }

    public void sendContextFailures(Object context, Player player) {
        sendContextMessages(context, player, this::sendColorizedRaw);
    }

    public void sendContextSuccesses(Object context, Player player) {
        sendContextMessages(context, player, this::sendColorizedRaw);
    }

    // === 私有工具方法 ===

    /** 加载语言文件到缓存 */
    private void loadMessages(String path) {
        YamlConfiguration cfg = yamlUtil.getConfig(path);
        for (String key : cfg.getKeys(true)) {
            if (cfg.isString(key)) {
                messages.put(key, cfg.getString(key));
            }
        }
        logger.info("加载消息: " + messages.size() + " 条，来源: " + path);
    }

    /**
     * 统一处理占位符替换逻辑：
     *  自定义占位符 -> 内部占位符 -> PlaceholderAPI 占位符
     */
    private String processPlaceholders(Player player, String msg, Map<String, String> custom) {
        return processPlaceholdersWithDelimiter(player, msg, custom, "%", "%");
    }
    
    private String processPlaceholdersWithDelimiter(Player player, String msg, Map<String, String> custom, String prefix, String suffix) {
        String result = msg;
        // 自定义占位符
        if (custom != null && !custom.isEmpty()) {
            for (Map.Entry<String, String> e : custom.entrySet()) {
                String placeholder = prefix + e.getKey() + suffix;
                result = result.replace(placeholder, e.getValue());
            }
        }
        // 内部 {key:args}
        if (!internalHandlers.isEmpty()) {
            Matcher m = internalPlaceholderPattern.matcher(result);
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                String k = m.group(1).toLowerCase();
                String args = m.group(2) == null ? "" : m.group(2);
                BiFunction<Player, String, String> fn = internalHandlers.get(k);
                String rep = fn != null ? fn.apply(player, args) : m.group(0);
                m.appendReplacement(sb, Matcher.quoteReplacement(rep));
            }
            m.appendTail(sb);
            result = sb.toString();
        }
        // 额外规则
        for (Map.Entry<Pattern, BiFunction<Player, Matcher, String>> e : extraPlaceholderRules.entrySet()) {
            Matcher m = e.getKey().matcher(result);
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                String rep = e.getValue().apply(player, m);
                m.appendReplacement(sb, Matcher.quoteReplacement(rep));
            }
            m.appendTail(sb);
            result = sb.toString();
        }
        // PlaceholderAPI
        result = placeholderUtil.parse(player, result);
        logger.debug("完整解析完成 -> " + result);
        return result;
    }

    /** 发送带颜色的单行消息 */
    private void sendColorizedRaw(CommandSender target, String msg) {
        if (msg == null || msg.trim().isEmpty()) {
            return;
        }
        // 消息已经在 parseWithDelimiter 中处理过颜色代码
        target.sendMessage(msg);
    }

    /** 发送带颜色的消息列表 */
    private void sendColorizedList(CommandSender target, List<String> list) {
        for (String msg : list) {
            sendColorizedRaw(target, msg);
        }
    }

    /** 发送原始 ActionBar 文本 */
    private void sendActionBarRaw(Player player, String msg) {
        player.spigot().sendMessage(
                ChatMessageType.ACTION_BAR,
                new TextComponent(ColorUtil.translateColors(msg))
        );
    }

    /** 发送原始 Title/SubTitle 文本 */
    private void sendTitleRaw(Player player, String title, String sub) {
        player.sendTitle(
                ColorUtil.translateColors(title),
                ColorUtil.translateColors(sub),
                10, 70, 20
        );
    }

    /** 带权限的单条广播 */
    private void broadcastToPermission(String key, Map<String, String> custom, String permission) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission(permission)) {
                String msg = parse(key, p, custom);
                if (msg != null) {
                    sendColorizedRaw(p, msg);
                    logger.debug("广播给 " + p.getName() + "，键: " + key);
                }
            }
        }
    }

    /** 带权限的列表广播 */
    private void broadcastListToPermission(String key, Map<String, String> custom, String permission) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission(permission)) {
                for (String msg : parseList(key, p, custom)) {
                    sendColorizedRaw(p, msg);
                    logger.debug("广播列表给 " + p.getName() + "，键: " + key);
                }
            }
        }
    }

    /** 通用上下文发送实现 */
    private void sendContextMessages(Object context, Player player, BiConsumer<CommandSender, String> fn) {
        List<String> list = contextMessages.getOrDefault(context, Collections.emptyList());
        for (String msg : list) {
            fn.accept(player, msg);
        }
        contextMessages.remove(context);
    }
}
