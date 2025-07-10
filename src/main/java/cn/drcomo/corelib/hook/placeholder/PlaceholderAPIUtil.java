package cn.drcomo.corelib.hook.placeholder;

import me.clip.placeholderapi.PlaceholderAPI;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * ======================================================================
 *  PlaceholderAPIUtil —— 集中式占位符管理器（前置工具）
 * ----------------------------------------------------------------------
 *  ◎ 职责
 *    1. 注册 PAPI 扩展；
 *    2. 暴露 {@link #register(String, BiFunction)} 供子插件注册自定义占位符；
 *    3. 提供 {@link #parse(Player, String)} 递归解析工具；
 *    4. 提供 {@link #splitArgs(String)} 多参数拆分工具。<br><br>
 *
 *  ◎ 使用方式<br>
 *    1. onEnable() 中调用 {@link #initialize(Plugin, String)};<br>
 *    2. 调用 {@link #register(String, BiFunction)} 注册占位符;<br>
 *       在 resolver 内部可通过 {@link #splitArgs(String)} 拆分多参数;<br>
 *    3. 在文本中使用 `%<identifier>_<key>_<arg1>_<arg2>…%`;<br>
 *    4. 解析时调用 {@link #parse(Player, String)}。<br>
 * ======================================================================
 */
public class PlaceholderAPIUtil {

    private final Plugin plugin;
    private final Map<String, BiFunction<Player, String, String>> handlers = new HashMap<>();
    private final String identifier;
    private final PlaceholderExpansion expansion;

    /**
     * 创建一个 PlaceholderAPIUtil 并立即注册到 PlaceholderAPI。
     *
     * @param pluginInstance Bukkit 插件实例，用于注册扩展
     * @param identifier     插件自定义标识符（建议使用插件名）
     */
    public PlaceholderAPIUtil(Plugin pluginInstance, String identifier) {
        this.plugin = pluginInstance;
        this.identifier = identifier.toLowerCase();

        this.expansion = new PlaceholderExpansion() {
            @Override public boolean canRegister()       { return true; }
            @Override public String getIdentifier()      { return PlaceholderAPIUtil.this.identifier; }
            @Override public String getAuthor()          { return String.join(" | ", plugin.getDescription().getAuthors()); }
            @Override public String getVersion()         { return plugin.getDescription().getVersion(); }

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
    }

    /**
     * 注册一个占位符处理器。<br>
     * 示例：
     * <pre>{@code
     * // 在子插件 onEnable() 中：
     * PlaceholderAPIUtil.initialize(this, "myid");
     * PlaceholderAPIUtil.register("coords", (player, rawArgs) -> {
     *     // rawArgs 可能是 "100_64_-200"
     *     String[] parts = PlaceholderAPIUtil.splitArgs(rawArgs);
     *     // parts = ["100","64","-200"]
     *     return String.format("X=%s Y=%s Z=%s", parts[0], parts[1], parts[2]);
     * });
     * // 然后在文本里用 %myid_coords_100_64_-200%
     * }</pre>
     *
     * @param key      占位符主键（不含 % 与参数）
     * @param resolver (player, rawArgs) -> 返回结果；rawArgs 为 key 之后的所有内容
     */
    public void register(String key, BiFunction<Player, String, String> resolver) {
        handlers.put(key.toLowerCase(), resolver);
    }

    /**
     * 解析文本中的 %identifier_key_args% 占位符（支持多重用 `{···}` 嵌套）。<br>
     * @param player 上下文玩家，可为 null
     * @param text   待解析文本
     * @return 解析后文本
     */
    public String parse(Player player, String text) {
        return parseRecursive(player, text);
    }

    // === 私有工具 ===

    /** 递归解析占位符直到稳定 */
    private String parseRecursive(Player player, String text) {
        if (text == null) return "";
        String last, cur = text;
        do {
            last = cur;
            cur  = PlaceholderAPI.setPlaceholders(player, last);
        } while (!cur.equals(last) && (cur.contains("%") || cur.contains("{")));
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
    }
    */
}
