package cn.drcomo.corelib.config;

import cn.drcomo.corelib.math.NumberUtil;
import org.bukkit.configuration.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * 单个配置项的校验构建器。
 * <p>由 {@link ConfigValidator} 创建并使用。</p>
 */
public class ValidatorBuilder {

    /** 可验证的值类型 */
    enum ValueType { STRING, NUMBER, ENUM }

    private final String path;
    private final ValueType type;
    private final Class<? extends Enum<?>> enumClass;
    private boolean required;
    private final List<CustomRule> customs = new ArrayList<>();

    ValidatorBuilder(String path, ValueType type) {
        this(path, type, null);
    }

    ValidatorBuilder(String path, ValueType type, Class<? extends Enum<?>> enumClass) {
        this.path = path;
        this.type = type;
        this.enumClass = enumClass;
    }

    /** 标记该配置项为必须存在 */
    public ValidatorBuilder required() {
        this.required = true;
        return this;
    }

    /**
     * 添加自定义校验规则。
     *
     * @param rule    断言函数，返回 true 表示通过
     * @param message 失败时的提示
     * @return 构建器本身
     */
    public ValidatorBuilder custom(Predicate<Object> rule, String message) {
        customs.add(new CustomRule(rule, message));
        return this;
    }

    /** 执行校验，将错误信息写入给定列表 */
    void apply(Configuration cfg, List<String> errors) {
        if (!cfg.contains(path)) {
            if (required) {
                errors.add("缺少配置项: " + path);
            }
            return;
        }
        Object value = cfg.get(path);
        switch (type) {
            case STRING:
                if (!(value instanceof String)) {
                    errors.add("配置 '" + path + "' 应为字符串");
                    return;
                }
                break;
            case NUMBER:
                if (!(value instanceof Number)) {
                    if (value instanceof String) {
                        if (!NumberUtil.isNumeric((String) value)) {
                            errors.add("配置 '" + path + "' 不是有效数字");
                            return;
                        }
                    } else {
                        errors.add("配置 '" + path + "' 类型错误，期望数字");
                        return;
                    }
                }
                break;
            case ENUM:
                if (!(value instanceof String)) {
                    errors.add("配置 '" + path + "' 应为字符串");
                    return;
                }
                String enumVal = ((String) value).toUpperCase();
                try {
                    Enum.valueOf((Class) enumClass, enumVal);
                } catch (IllegalArgumentException e) {
                    errors.add("配置 '" + path + "' 非法枚举值: " + value);
                    return;
                }
                break;
            default:
                break;
        }
        for (CustomRule cr : customs) {
            if (!cr.rule.test(value)) {
                errors.add(cr.message + " -> " + path);
            }
        }
    }

    /** 自定义校验规则 */
    private static class CustomRule {
        final Predicate<Object> rule;
        final String message;
        CustomRule(Predicate<Object> rule, String message) {
            this.rule = rule;
            this.message = message;
        }
    }
}
