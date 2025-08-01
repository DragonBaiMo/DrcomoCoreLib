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
}

