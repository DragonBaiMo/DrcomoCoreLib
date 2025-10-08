package cn.drcomo.corelib.math;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NumberUtilTest {

    @Test
    @DisplayName("clampInt 应在区间内返回原值")
    void clampIntShouldReturnValueInsideRange() {
        assertEquals(5, NumberUtil.clampInt(5, 0, 10));
    }

    @Test
    @DisplayName("clampInt 小于最小值时返回最小值")
    void clampIntShouldReturnMinWhenValueTooSmall() {
        assertEquals(0, NumberUtil.clampInt(-3, 0, 10));
    }

    @Test
    @DisplayName("clampInt 大于最大值时返回最大值")
    void clampIntShouldReturnMaxWhenValueTooLarge() {
        assertEquals(10, NumberUtil.clampInt(23, 0, 10));
    }

    @Test
    @DisplayName("clampInt 参数非法时抛出异常")
    void clampIntShouldThrowWhenMinGreaterThanMax() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> NumberUtil.clampInt(1, 5, 1));
        assertTrue(exception.getMessage().contains("最小值"));
    }

    @Test
    @DisplayName("ensureNonNegative 对负数返回 0")
    void ensureNonNegativeShouldReturnZeroForNegative() {
        assertEquals(0, NumberUtil.ensureNonNegative(-10));
    }

    @Test
    @DisplayName("ensureNonNegative 对非负数保持原值")
    void ensureNonNegativeShouldKeepValueForNonNegative() {
        assertEquals(7, NumberUtil.ensureNonNegative(7));
        assertEquals(0, NumberUtil.ensureNonNegative(0));
    }
}
