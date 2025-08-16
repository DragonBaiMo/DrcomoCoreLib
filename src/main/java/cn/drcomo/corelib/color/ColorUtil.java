package cn.drcomo.corelib.color;

import cn.drcomo.corelib.math.NumberUtil;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Server;

/**
 * 颜色转换工具类
 * <p>支持：</p>
 * <ul>
 *   <li>传统的 &amp;0–&amp;f 颜色码（以及 &amp;k–&amp;o 样式，&amp;r 重置）</li>
 *   <li>形如 &#RRGGBB 的 RGB 颜色码（1.16+ 输出为 §x……，低版本自动降级为最接近的传统色）</li>
 *   <li>渐变标签：<gradient:c1,c2,...>文本</gradient>（自动对每个字符插入颜色）</li>
 *   <li>单色标签：<color:c>文本</color></li>
 *   <li>CSS 颜色格式：#RGB/#RRGGBB/#RGBA/#RRGGBBAA、rgb()/rgba()、hsl()/hsla()、常见命名色（如 red、gold、rebeccapurple）</li>
 * </ul>
 */
public class ColorUtil {
    // 匹配 &#RRGGBB 格式
    private static final Pattern HEX_PATTERN = Pattern.compile("&#([A-Fa-f0-9]{6})");
    // 优化：合并为一个正则表达式，一次性移除所有颜色代码
    private static final Pattern STRIP_COLOR_PATTERN = Pattern.compile("&#[a-fA-F0-9]{6}|(?i)[§&][0-9a-fk-or]");
    // 缓存 Bukkit 版本号解析正则
    private static final Pattern VERSION_PATTERN = Pattern.compile("1\\.(\\d+)");

    // 渐变与颜色标签：<gradient:c1,c2,...>text</gradient> 以及 <color:c>text</color>
    private static final Pattern GRADIENT_TAG_PATTERN = Pattern.compile("<gradient:([^>]+)>([\\s\\S]*?)</gradient>", Pattern.CASE_INSENSITIVE);
    private static final Pattern COLOR_TAG_PATTERN = Pattern.compile("<color:([^>]+)>([\\s\\S]*?)</color>", Pattern.CASE_INSENSITIVE);
    // strip 时清理标签
    private static final Pattern TAG_CLEAN_PATTERN = Pattern.compile("(?is)</?(?:gradient|color)(?:[^>]*)>");

    // 常见 CSS 命名色（小写）
    private static final Map<String, Integer> CSS_NAMED_COLORS = new HashMap<>();
    static {
        // 16 基础 + 扩展常用命名色（覆盖绝大多数使用场景）
        String[] entries = new String[] {
            "black:000000","silver:c0c0c0","gray:808080","white:ffffff","maroon:800000","red:ff0000","purple:800080","fuchsia:ff00ff",
            "green:008000","lime:00ff00","olive:808000","yellow:ffff00","navy:000080","blue:0000ff","teal:008080","aqua:00ffff",
            // 扩展
            "orange:ffa500","aliceblue:f0f8ff","antiquewhite:faebd7","aquamarine:7fffd4","azure:f0ffff","beige:f5f5dc","bisque:ffe4c4",
            "blanchedalmond:ffebcd","blueviolet:8a2be2","brown:a52a2a","burlywood:deb887","cadetblue:5f9ea0","chartreuse:7fff00",
            "chocolate:d2691e","coral:ff7f50","cornflowerblue:6495ed","cornsilk:fff8dc","crimson:dc143c","cyan:00ffff",
            "darkblue:00008b","darkcyan:008b8b","darkgoldenrod:b8860b","darkgray:a9a9a9","darkgreen:006400","darkgrey:a9a9a9",
            "darkkhaki:bdb76b","darkmagenta:8b008b","darkolivegreen:556b2f","darkorange:ff8c00","darkorchid:9932cc","darkred:8b0000",
            "darksalmon:e9967a","darkseagreen:8fbc8f","darkslateblue:483d8b","darkslategray:2f4f4f","darkslategrey:2f4f4f",
            "darkturquoise:00ced1","darkviolet:9400d3","deeppink:ff1493","deepskyblue:00bfff","dimgray:696969","dimgrey:696969",
            "dodgerblue:1e90ff","firebrick:b22222","floralwhite:fffaf0","forestgreen:228b22","gainsboro:dcdcdc","ghostwhite:f8f8ff",
            "gold:ffd700","goldenrod:daa520","greenyellow:adff2f","honeydew:f0fff0","hotpink:ff69b4","indianred:cd5c5c",
            "indigo:4b0082","ivory:fffff0","khaki:f0e68c","lavender:e6e6fa","lavenderblush:fff0f5","lawngreen:7cfc00",
            "lemonchiffon:fffacd","lightblue:add8e6","lightcoral:f08080","lightcyan:e0ffff","lightgoldenrodyellow:fafad2",
            "lightgray:d3d3d3","lightgreen:90ee90","lightgrey:d3d3d3","lightpink:ffb6c1","lightsalmon:ffa07a",
            "lightseagreen:20b2aa","lightskyblue:87cefa","lightslategray:778899","lightslategrey:778899","lightsteelblue:b0c4de",
            "lightyellow:ffffe0","limegreen:32cd32","linen:faf0e6","magenta:ff00ff","mediumaquamarine:66cdaa","mediumblue:0000cd",
            "mediumorchid:ba55d3","mediumpurple:9370db","mediumseagreen:3cb371","mediumslateblue:7b68ee","mediumspringgreen:00fa9a",
            "mediumturquoise:48d1cc","mediumvioletred:c71585","midnightblue:191970","mintcream:f5fffa","mistyrose:ffe4e1",
            "moccasin:ffe4b5","navajowhite:ffdead","oldlace:fdf5e6","olivedrab:6b8e23","orangered:ff4500","orchid:da70d6",
            "palegoldenrod:eee8aa","palegreen:98fb98","paleturquoise:afeeee","palevioletred:db7093","papayawhip:ffefd5",
            "peachpuff:ffdab9","peru:cd853f","pink:ffc0cb","plum:dda0dd","powderblue:b0e0e6","rebeccapurple:663399",
            "rosybrown:bc8f8f","royalblue:4169e1","saddlebrown:8b4513","salmon:fa8072","sandybrown:f4a460","seagreen:2e8b57",
            "seashell:fff5ee","sienna:a0522d","skyblue:87ceeb","slateblue:6a5acd","slategray:708090","slategrey:708090",
            "snow:fffafa","springgreen:00ff7f","steelblue:4682b4","tan:d2b48c","thistle:d8bfd8","tomato:ff6347",
            "turquoise:40e0d0","violet:ee82ee","wheat:f5deb3","whitesmoke:f5f5f5","yellowgreen:9acd32"
        };
        for (String e : entries) {
            int i = e.indexOf(':');
            if (i > 0) {
                CSS_NAMED_COLORS.put(e.substring(0, i), Integer.parseInt(e.substring(i + 1), 16));
            }
        }
    }

    // 缓存的服务端主版本号，-1 表示尚未初始化
    private static volatile int MAJOR_VERSION = -1;

    private ColorUtil() {
        // 私有构造，禁止实例化
    }

    /**
     * 把文本中的颜色代码翻译成 Minecraft 格式。
     * @param text 带颜色码的原始文本
     * @return 转换后可直接发送给玩家的文本
     */
    public static String translateColors(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        // 先处理自定义标签：<gradient:...> 与 <color:...>
        text = applyGradientTags(text);
        text = applyColorTags(text);

        // 优化：单次遍历处理HEX颜色，避免在循环中重复创建StringBuilder
        Matcher matcher = HEX_PATTERN.matcher(text);
        if (!matcher.find()) {
            // 如果没有HEX颜色，直接使用原生方法，这是最快的
            return ChatColor.translateAlternateColorCodes('&', text);
        }

        StringBuilder sb = new StringBuilder(text.length());
        int lastEnd = 0;

        // 重置匹配器以从头开始
        matcher.reset();

        while (matcher.find()) {
            // 追加上一次匹配到本次匹配之间的文本
            sb.append(text, lastEnd, matcher.start());

            // 获取HEX并进行转换
            String hex = matcher.group(1);
            if (getMajorVersion() >= 16) {
                // 1.16+ 直接构造 §x§R§R§G§G§B§B 格式
                sb.append(ChatColor.COLOR_CHAR).append('x');
                for (char c : hex.toCharArray()) {
                    sb.append(ChatColor.COLOR_CHAR).append(c);
                }
            } else {
                // 低版本降级为最接近的传统色
                sb.append(nearestLegacy(hex));
            }
            lastEnd = matcher.end();
        }

        // 追加最后一次匹配到字符串末尾的文本
        if (lastEnd < text.length()) {
            sb.append(text, lastEnd, text.length());
        }

        // 最后统一处理 & 颜色码
        return ChatColor.translateAlternateColorCodes('&', sb.toString());
    }
    /**
     * 去除文本中的所有颜色代码
     * @param text 带颜色码的原始文本
     * @return 去除颜色码后的文本
     * 
     * <p>注意：用的上才用,用不上请忽略该方法</p>
     */
    public static String stripColorCodes(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        // 先移除自定义标签本体，保留内部文字
        text = TAG_CLEAN_PATTERN.matcher(text).replaceAll("");
        // 优化：使用单一正则表达式一次性替换所有颜色代码
        return STRIP_COLOR_PATTERN.matcher(text).replaceAll("");
    }

    /**
     * 在插件 <code>onEnable()</code> 阶段调用以缓存服务器主版本号，避免运行时频繁解析字符串。
     *
     * @param server Bukkit 服务器实例
     */
    public static void initMajorVersion(Server server) {
        if (MAJOR_VERSION == -1) {
            synchronized (ColorUtil.class) {
                if (MAJOR_VERSION == -1) {
                    MAJOR_VERSION = parseMajorVersion(server);
                }
            }
        }
    }

    /** 获取已缓存的主版本号，如未初始化则立即解析并缓存 */
    private static int getMajorVersion() {
        if (MAJOR_VERSION == -1) {
            initMajorVersion(Bukkit.getServer());
        }
        return MAJOR_VERSION;
    }

    /**
     * 解析服务器 Bukkit 主版本号，比如 "1.18.2" → 18
     */
    private static int parseMajorVersion(Server server) {
        String v = server.getBukkitVersion();
        Matcher m = VERSION_PATTERN.matcher(v);
        if (m.find()) {
            String major = m.group(1);
            if (NumberUtil.isNumeric(major)) {
                return Integer.parseInt(major);
            }
        }
        return 8; // 默认当作低版本
    }

    // 将 <gradient:...> 标签转换为由 &#RRGGBB + 样式码 构成的渐变文本
    private static String applyGradientTags(String input) {
        if (input == null || input.isEmpty()) return input;
        String text = input;
        boolean changed = true;
        while (changed) {
            changed = false;
            Matcher m = GRADIENT_TAG_PATTERN.matcher(text);
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                String colorList = m.group(1);
                String inner = m.group(2);
                List<Integer> stops = parseColorStops(colorList);
                String replacement;
                if (stops.size() >= 2) {
                    replacement = applyGradientToText(inner, stops);
                } else if (stops.size() == 1) {
                    replacement = applySolidColorToText(inner, stops.get(0));
                } else {
                    // 解析失败：去除标签但保留内容
                    replacement = inner;
                }
                m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
                changed = true;
            }
            m.appendTail(sb);
            text = sb.toString();
        }
        return text;
    }

    // 将 <color:...> 标签转换为由 &#RRGGBB + 样式码 构成的单色文本
    private static String applyColorTags(String input) {
        if (input == null || input.isEmpty()) return input;
        String text = input;
        boolean changed = true;
        while (changed) {
            changed = false;
            Matcher m = COLOR_TAG_PATTERN.matcher(text);
            StringBuffer sb = new StringBuffer();
            while (m.find()) {
                String colorExpr = m.group(1);
                String inner = m.group(2);
                Integer rgb = parseCssColor(colorExpr);
                String replacement = (rgb != null) ? applySolidColorToText(inner, rgb) : inner;
                m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
                changed = true;
            }
            m.appendTail(sb);
            text = sb.toString();
        }
        return text;
    }

    // 解析逗号/空格/冒号分隔的颜色停靠点
    private static List<Integer> parseColorStops(String colorList) {
        List<Integer> result = new ArrayList<>();
        if (colorList == null || colorList.trim().isEmpty()) return result;
        String[] parts = colorList.split("[\\s,:]+");
        for (String p : parts) {
            Integer rgb = parseCssColor(p);
            if (rgb != null) result.add(rgb);
        }
        return result;
    }

    /**
     * 解析 CSS 颜色字符串为 24 位 RGB（忽略 Alpha）。
     * 支持：#RGB/#RRGGBB/#RGBA/#RRGGBBAA、rgb()/rgba()、hsl()/hsla()、常见命名色。
     */
    private static Integer parseCssColor(String css) {
        if (css == null) return null;
        String s = css.trim().toLowerCase(Locale.ROOT);
        if (s.isEmpty()) return null;

        // 十六进制：#RGB/#RRGGBB/#RGBA/#RRGGBBAA
        if (s.charAt(0) == '#') {
            return parseHexColor(s.substring(1));
        }

        // rgb()/rgba()
        if (s.startsWith("rgb(")) {
            return parseRgbOrRgba(s.substring(4, s.length() - (s.endsWith(")") ? 1 : 0)));
        }
        if (s.startsWith("rgba(")) {
            return parseRgbOrRgba(s.substring(5, s.length() - (s.endsWith(")") ? 1 : 0)));
        }

        // hsl()/hsla()
        if (s.startsWith("hsl(")) {
            return parseHslOrHsla(s.substring(4, s.length() - (s.endsWith(")") ? 1 : 0)));
        }
        if (s.startsWith("hsla(")) {
            return parseHslOrHsla(s.substring(5, s.length() - (s.endsWith(")") ? 1 : 0)));
        }

        // 命名色
        Integer rgb = CSS_NAMED_COLORS.get(s);
        return rgb;
    }

    private static Integer parseHexColor(String hex) {
        int len = hex.length();
        try {
            if (len == 3) {
                int r = Integer.parseInt(hex.substring(0, 1) + hex.substring(0, 1), 16);
                int g = Integer.parseInt(hex.substring(1, 2) + hex.substring(1, 2), 16);
                int b = Integer.parseInt(hex.substring(2, 3) + hex.substring(2, 3), 16);
                return (r << 16) | (g << 8) | b;
            } else if (len == 4) {
                int r = Integer.parseInt(hex.substring(0, 1) + hex.substring(0, 1), 16);
                int g = Integer.parseInt(hex.substring(1, 2) + hex.substring(1, 2), 16);
                int b = Integer.parseInt(hex.substring(2, 3) + hex.substring(2, 3), 16);
                // 忽略 alpha: hex[3]
                return (r << 16) | (g << 8) | b;
            } else if (len == 6) {
                return Integer.parseInt(hex, 16);
            } else if (len == 8) {
                // #RRGGBBAA -> 忽略 AA
                return Integer.parseInt(hex.substring(0, 6), 16);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static Integer parseRgbOrRgba(String inside) {
        // 兼容逗号或空格、以及可选的 / 分隔 alpha
        String norm = inside.replace('/', ',').replace(" ", ",");
        String[] parts = norm.split(",[ ]*");
        if (parts.length < 3) return null;
        Integer r = parseRgbComponent(parts[0]);
        Integer g = parseRgbComponent(parts[1]);
        Integer b = parseRgbComponent(parts[2]);
        if (r == null || g == null || b == null) return null;
        return (r << 16) | (g << 8) | b;
    }

    private static Integer parseRgbComponent(String s) {
        s = s.trim();
        try {
            if (s.endsWith("%")) {
                double pct = Double.parseDouble(s.substring(0, s.length() - 1));
                int v = (int)Math.round(clamp01(pct / 100.0) * 255.0);
                return clamp8(v);
            }
            if (s.contains(".")) {
                // 小数按 0..255 解释
                double v = Double.parseDouble(s);
                return clamp8((int)Math.round(v));
            }
            int v = Integer.parseInt(s);
            return clamp8(v);
        } catch (Exception ignored) {}
        return null;
    }

    private static Integer parseHslOrHsla(String inside) {
        String norm = inside.replace('/', ',').replace(" ", ",");
        String[] parts = norm.split(",[ ]*");
        if (parts.length < 3) return null;
        try {
            double h = Double.parseDouble(parts[0].trim());
            double s = parsePercent(parts[1].trim());
            double l = parsePercent(parts[2].trim());
            return hslToRgb(h, s, l);
        } catch (Exception ignored) {}
        return null;
    }

    private static double parsePercent(String s) {
        if (s.endsWith("%")) {
            return clamp01(Double.parseDouble(s.substring(0, s.length() - 1)) / 100.0);
        }
        // 允许 0..1 小数
        return clamp01(Double.parseDouble(s));
    }

    private static int hslToRgb(double h, double s, double l) {
        h = ((h % 360) + 360) % 360; // wrap
        double c = (1 - Math.abs(2 * l - 1)) * s;
        double x = c * (1 - Math.abs((h / 60.0) % 2 - 1));
        double m = l - c / 2.0;
        double r1, g1, b1;
        if (h < 60) { r1 = c; g1 = x; b1 = 0; }
        else if (h < 120) { r1 = x; g1 = c; b1 = 0; }
        else if (h < 180) { r1 = 0; g1 = c; b1 = x; }
        else if (h < 240) { r1 = 0; g1 = x; b1 = c; }
        else if (h < 300) { r1 = x; g1 = 0; b1 = c; }
        else { r1 = c; g1 = 0; b1 = x; }
        int r = clamp8((int)Math.round((r1 + m) * 255.0));
        int g = clamp8((int)Math.round((g1 + m) * 255.0));
        int b = clamp8((int)Math.round((b1 + m) * 255.0));
        return (r << 16) | (g << 8) | b;
    }

    private static int clamp8(int v) { return Math.max(0, Math.min(255, v)); }
    private static double clamp01(double v) { return Math.max(0.0, Math.min(1.0, v)); }

    private static String toHex6(int rgb) {
        String s = Integer.toHexString(rgb & 0xFFFFFF);
        if (s.length() < 6) {
            StringBuilder sb = new StringBuilder(6);
            for (int i = 0; i < 6 - s.length(); i++) sb.append('0');
            sb.append(s);
            return sb.toString();
        }
        return s;
    }

    // 将样式标志追加为 &k&l&m&n&o 的序列
    private static void appendActiveStyles(StringBuilder out, boolean obf, boolean bold, boolean strike, boolean under, boolean italic) {
        if (obf) out.append('&').append('k');
        if (bold) out.append('&').append('l');
        if (strike) out.append('&').append('m');
        if (under) out.append('&').append('n');
        if (italic) out.append('&').append('o');
    }

    // 对文本每个可见字符应用同一颜色
    private static String applySolidColorToText(String inner, int rgb) {
        if (inner == null || inner.isEmpty()) return inner;
        // 先统计可见字符并逐个输出，保持 &k-o 样式
        StringBuilder out = new StringBuilder(inner.length() * 10);
        boolean obf=false,bold=false,strike=false,under=false,italic=false;

        int i = 0;
        while (i < inner.length()) {
            char ch = inner.charAt(i);
            if ((ch == '&' || ch == ChatColor.COLOR_CHAR) && i + 1 < inner.length()) {
                char code = Character.toLowerCase(inner.charAt(i + 1));
                if (isColorCode(code) || code == 'r') { obf=bold=strike=under=italic=false; i += 2; continue; }
                switch (code) {
                    case 'k': obf = true; break;
                    case 'l': bold = true; break;
                    case 'm': strike = true; break;
                    case 'n': under = true; break;
                    case 'o': italic = true; break;
                    default: break;
                }
                i += 2; continue;
            }
            // 输出颜色 + 样式 + 字符
            out.append("&#").append(toHex6(rgb));
            appendActiveStyles(out, obf, bold, strike, under, italic);
            out.append(ch);
            i++;
        }
        return out.toString();
    }

    // 生成线性渐变（多段）
    private static String applyGradientToText(String inner, List<Integer> stops) {
        if (inner == null || inner.isEmpty()) return inner;
        // 计算可见字符数量
        int visible = countVisible(inner);
        if (visible <= 0) return inner;

        StringBuilder out = new StringBuilder(inner.length() * 10);
        boolean obf=false,bold=false,strike=false,under=false,italic=false;
        
        int visIndex = 0;
        int i = 0;
        while (i < inner.length()) {
            char ch = inner.charAt(i);
            if ((ch == '&' || ch == ChatColor.COLOR_CHAR) && i + 1 < inner.length()) {
                char code = Character.toLowerCase(inner.charAt(i + 1));
                if (isColorCode(code) || code == 'r') { obf=bold=strike=under=italic=false; i += 2; continue; }
                switch (code) {
                    case 'k': obf = true; break;
                    case 'l': bold = true; break;
                    case 'm': strike = true; break;
                    case 'n': under = true; break;
                    case 'o': italic = true; break;
                    default: break;
                }
                i += 2; continue;
            }

            // 计算当前字符在 [0,1] 上的位置
            double t = (visible == 1) ? 0.0 : (visIndex / (double)(visible - 1));
            int rgb = interpolateStops(stops, t);

            out.append("&#").append(toHex6(rgb));
            appendActiveStyles(out, obf, bold, strike, under, italic);
            out.append(ch);

            visIndex++;
            i++;
        }
        return out.toString();
    }

    private static int countVisible(String s) {
        int visible = 0;
        int i = 0;
        while (i < s.length()) {
            char ch = s.charAt(i);
            if ((ch == '&' || ch == ChatColor.COLOR_CHAR) && i + 1 < s.length()) {
                i += 2; // 跳过格式码
                continue;
            }
            visible++;
            i++;
        }
        return visible;
    }

    private static boolean isColorCode(char code) {
        return (code >= '0' && code <= '9') || (code >= 'a' && code <= 'f');
    }

    private static int interpolateStops(List<Integer> stops, double t) {
        int n = stops.size();
        if (n == 1) return stops.get(0);
        double pos = t * (n - 1);
        int idx = (int)Math.floor(pos);
        if (idx >= n - 1) return stops.get(n - 1);
        double f = pos - idx;
        int c1 = stops.get(idx);
        int c2 = stops.get(idx + 1);
        int r = (int)Math.round(((c1 >> 16) & 0xFF) * (1 - f) + ((c2 >> 16) & 0xFF) * f);
        int g = (int)Math.round(((c1 >> 8) & 0xFF) * (1 - f) + ((c2 >> 8) & 0xFF) * f);
        int b = (int)Math.round((c1 & 0xFF) * (1 - f) + (c2 & 0xFF) * f);
        return (clamp8(r) << 16) | (clamp8(g) << 8) | clamp8(b);
    }

    /** 简单亮度法选取最接近的传统颜色 */
    private static String nearestLegacy(String hex) {
        int rgb = Integer.parseInt(hex, 16);
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        double bright = 0.299 * r + 0.587 * g + 0.114 * b;
        if (r > g * 2 && r > b * 2) return bright > 160 ? "§c" : "§4";
        if (g > r * 2 && g > b * 2) return bright > 160 ? "§a" : "§2";
        if (b > r * 2 && b > g * 2) return bright > 160 ? "§9" : "§1";
        if (bright > 180) return "§f";
        if (bright > 120) return "§7";
        if (bright > 60)  return "§8";
        return "§0";
    }
}
