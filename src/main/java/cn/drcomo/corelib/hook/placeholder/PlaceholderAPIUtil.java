package cn.drcomo.corelib.hook.placeholder;

import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * ======================================================================
 *  PlaceholderAPIUtil —— 集中式占位符管理器（前置工具）
 * ----------------------------------------------------------------------
 *  ◎ 职责
 *    1. 注册 PAPI 扩展；
 *    2. 暴露 {@link #register(String, BiFunction)} 及 {@link #registerSpecial(String, Function)} 供子插件注册自定义占位符；
 *    3. 提供 {@link #parse(Player, String)} 递归解析工具；
 *    4. 提供 {@link #parse(Player, String, Map)} 带自定义占位符预替换的解析工具；
 *    5. 提供 {@link #splitArgs(String)} 多参数拆分工具；
 *    6. 提供 {@link #convertOuterCharsToPercent(String, char, char)} 外层符号转百分号工具。<br><br>
 *
 *  ◎ 使用方式<br>
 *    1. onEnable() 中调用 {@link #initialize(Plugin, String)};<br>
 *    2. 调用 {@link #register(String, BiFunction)} 或 {@link #registerSpecial(String, Function)} 注册占位符;<br>
 *       在 resolver 内部可通过 {@link #splitArgs(String)} 拆分多参数;<br>
 *    3. 在文本中使用 `%<identifier>_<key>_<arg1>_<arg2>…%` 或先用 `{key}` 自定义占位符;<br>
 *    4. 解析时调用 {@link #parse(Player, String)} 或 {@link #parse(Player, String, Map)}。<br>
 * <br>
 *  ⚠️ 占位符注册可在异步线程调用，但解析仍需在主线程执行。<br>
 * ======================================================================
 */
public class PlaceholderAPIUtil {

    /** 最多递归解析次数 */
    private static final int MAX_PARSE_ITERATIONS = 10;

    private final Plugin plugin;
    private final Map<String, BiFunction<Player, String, String>> handlers = new ConcurrentHashMap<>();
    private final String identifier;
    private final PlaceholderExpansion expansion;
    private final String authors;
    private final String version;
    private final boolean papiEnabled;

    /**
     * 创建一个 PlaceholderAPIUtil 并立即注册到 PlaceholderAPI。
     *
     * @param pluginInstance Bukkit 插件实例，用于注册扩展
     * @param identifier     插件自定义标识符（建议使用插件名）
     */
    public PlaceholderAPIUtil(Plugin pluginInstance, String identifier) {
        this.plugin = pluginInstance;
        this.identifier = identifier.toLowerCase();
        this.authors = String.join("| ", plugin.getDescription().getAuthors());
        this.version = plugin.getDescription().getVersion();

        this.papiEnabled = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");

        if (papiEnabled) {
            this.expansion = new PlaceholderExpansion() {
                @Override public boolean canRegister()       { return true; }
                @Override public String getIdentifier()      { return PlaceholderAPIUtil.this.identifier; }
                @Override public String getAuthor()          { return authors; }
                @Override public String getVersion()         { return version; }

                @Override
                public String onPlaceholderRequest(Player player, String params) {
                    if (params == null) return "";
                    String[] pair = splitParams(params);
                    String key   = pair[0].toLowerCase();
                    String raw   = pair[1];
                    BiFunction<Player, String, String> fn = handlers.get(key);
                    String result = fn != null ? fn.apply(player, raw) : "";
                    return parseRecursive(player, result);
                }
            };

            // 注册扩展
            this.expansion.register();
        } else {
            this.expansion = null;
        }
    }

    /**
     * 注册一个占位符处理器。<br>
     *
     * @param key      占位符主键（不含 % 与参数）
     * @param resolver (player, rawArgs) -> 返回结果；rawArgs 为 key 之后的所有内容
     */
    public void register(String key, BiFunction<Player, String, String> resolver) {
        handlers.put(key.toLowerCase(), resolver);
    }

    /**
     * 注册无参数占位符的快捷方法。<br>
     * 相当于 register(key, (player, rawArgs) -> resolver.apply(player))。
     *
     * @param key      占位符主键（不含 % 与参数）
     * @param resolver player -> 返回结果
     */
    public void registerSpecial(String key, Function<Player, String> resolver) {
        register(key, (player, rawArgs) -> resolver.apply(player));
    }

    /**
     * 解析文本中的 %identifier_key_args% 占位符（支持多重用 `{···}` 嵌套）。<br>
     *
     * @param player 上下文玩家，可为 null
     * @param text   待解析文本
     * @return 解析后文本
     */
    public String parse(Player player, String text) {
        if (!papiEnabled) {
            return text == null ? "" : text;
        }
        return parseRecursive(player, text);
    }

    /**
     * 带自定义 "{key}" 占位符预替换的解析。<br>
     * 先将 text 中所有 `{k}` 按 customPlaceholders 替换，再调用 {@link #parse(Player, String)} 做 PAPI 解析。
     *
     * @param player               上下文玩家，可为 null
     * @param text                 待解析文本
     * @param customPlaceholders   自定义占位符映射，key 对应 `{key}`
     * @return 解析后文本
     */
    public String parse(Player player, String text, Map<String, String> customPlaceholders) {
        if (text == null) return "";
        // 先替换所有 {key}
        for (Map.Entry<String, String> e : customPlaceholders.entrySet()) {
            text = text.replace("{" + e.getKey() + "}", e.getValue());
        }
        // 再调用原有解析
        return parse(player, text);
    }

    // === 私有工具 ===

    /** 递归解析占位符直到稳定，若超过最大次数则返回当前结果并警告 */
    private String parseRecursive(Player player, String text) {
        if (text == null) return "";
        if (!papiEnabled) {
            return text;
        }
        String last = text;
        String cur = text;
        for (int i = 0; i < MAX_PARSE_ITERATIONS; i++) {
            cur = PlaceholderAPI.setPlaceholders(player, last);
            if (cur.equals(last) || (!cur.contains("%") && !cur.contains("{"))) {
                return cur;
            }
            last = cur;
        }
        plugin.getLogger().warning("占位符递归解析超过 " + MAX_PARSE_ITERATIONS + " 次仍未收敛，返回当前结果：" + cur);
        return cur;
    }

    /** 拆分 "key_args1_args2_..." → [ key, "args1_args2_..." ] */
    private String[] splitParams(String params) {
        int idx = params.indexOf('_');
        return idx < 0
                ? new String[]{ params, "" }
                : new String[]{ params.substring(0, idx), params.substring(idx + 1) };
    }

    /**
     * 将 rawArgs 按下划线拆分为数组，方便多参数使用。<br>
     * rawArgs 为空时返回长度为 0 的数组。
     *
     * @param rawArgs key 之外的所有参数，如 "a_b_c"
     * @return ["a","b","c"]
     */
    public String[] splitArgs(String rawArgs) {
        if (rawArgs == null || rawArgs.isEmpty()) {
            return new String[0];
        }
        return rawArgs.split("_", -1);
    }

    /**
     * 将最外层指定符号对替换为 "%"，内部层级的同符号保持不变。
     *
     * @param input     待处理的原始字符串
     * @param openChar  外层左符号，如 '{'
     * @param closeChar 外层右符号，如 '}'
     * @return 处理后的字符串；当输入为 null 或空串时返回空串；若首尾不存在成对符号或结构不匹配，则直接返回原字符串
     */
    public static String convertOuterCharsToPercent(String input, char openChar, char closeChar) {
        if (input == null || input.isEmpty()) {
            return "";
        }
        if (input.charAt(0) != openChar || input.charAt(input.length() - 1) != closeChar) {
            return input;
        }
        int depth = 0;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == openChar) {
                depth++;
            } else if (c == closeChar) {
                depth--;
                if (depth == 0 && i != input.length() - 1) {
                    return input;
                }
                if (depth < 0) {
                    return input;
                }
            }
        }
        if (depth != 0) {
            return input;
        }
        return '%' + input.substring(1, input.length() - 1) + '%';
    }

    /**
     * 将最外层的 "{" 与 "}" 替换为 "%"，内部的花括号保持不变。
     *
     * @param input 待处理的原始字符串
     * @return 处理后的字符串；当输入为 null 或空串时返回空串；若首尾不存在成对花括号或结构不匹配，则直接返回原字符串
     */
    public static String convertOuterBracesToPercent(String input) {
        return convertOuterCharsToPercent(input, '{', '}');
    }

    // === 示例模板 Javadoc Markdown 操作文档里必须有示例 ===
    /*
    public void registerModulePlaceholders() {
        // 多参数示例：%identifier_example_arg1_arg2%
        register("example", (player, rawArgs) -> {
            String[] args = splitArgs(rawArgs);
            // args[0], args[1]...
            return args.length > 1
                ? args[0] + "-" + args[1]
                : "不足参数";
        });

        // 无参数示例：%identifier_prefix%
        registerSpecial("prefix", player -> player.getName());
    }
    */
}
