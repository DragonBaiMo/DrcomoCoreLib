package cn.drcomo.corelib.math;

/**
 * 数值相关工具方法。
 * <p>提供常见的数值判断与运算功能。</p>
 */
public final class NumberUtil {

    private NumberUtil() {
        throw new UnsupportedOperationException("This utility class cannot be instantiated");
    }

    /**
     * 判断字符串是否为合法数字。
     * <p>允许前后空白以及正负号。</p>
     *
     * @param input 待检测的字符串
     * @return 若可解析为 {@code double} 则返回 {@code true}，否则返回 {@code false}
     */
    public static boolean isNumeric(String input) {
        if (input == null) {
            return false;
        }
        try {
            Double.parseDouble(input.trim());
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /**
     * 计算两个双精度数的和。
     *
     * @param a 加数1
     * @param b 加数2
     * @return 两数之和
     */
    public static double add(double a, double b) {
        return a + b;
    }
    /**
     * 解析整数。
     *
     * @param input        待解析的字符串
     * @param defaultValue 解析失败时返回的默认值
     * @return 解析后的整数；若输入为空或解析失败则返回 {@code defaultValue}
     */
    public static int parseInt(String input, int defaultValue) {
        if (input == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(input.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 解析双精度浮点数。
     *
     * @param input        待解析的字符串
     * @param defaultValue 解析失败时返回的默认值
     * @return 解析后的双精度浮点数；若输入为空或解析失败则返回 {@code defaultValue}
     */
    public static double parseDouble(String input, double defaultValue) {
        if (input == null) {
            return defaultValue;
        }
        try {
            return Double.parseDouble(input.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * 解析整数，如果解析失败则返回 null。
     *
     * @param input 待解析的字符串
     * @return 解析后的整数；若输入为空或解析失败则返回 {@code null}
     */
    public static Integer parseInt(String input) {
        if (input == null) {
            return null;
        }
        try {
            return Integer.parseInt(input.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 解析双精度浮点数，如果解析失败则返回 null。
     *
     * @param input 待解析的字符串
     * @return 解析后的双精度浮点数；若输入为空或解析失败则返回 {@code null}
     */
    public static Double parseDouble(String input) {
        if (input == null) {
            return null;
        }
        try {
            return Double.parseDouble(input.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

