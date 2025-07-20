package cn.drcomo.corelib.config;

import cn.drcomo.corelib.util.DebugUtil;
import org.bukkit.configuration.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * 配置校验器，结合 {@link YamlUtil} 提供的配置对象进行字段验证。
 * <p>使用链式 API 声明各字段的类型和约束，最后调用 {@link #validate(Configuration)} 获取结果。</p>
 */
public class ConfigValidator {

    private final YamlUtil yamlUtil;
    private final DebugUtil logger;
    private final List<ValidatorBuilder> builders = new ArrayList<>();

    /**
     * 构造配置校验器
     *
     * @param yamlUtil YamlUtil 实例
     * @param logger   DebugUtil 实例
     */
    public ConfigValidator(YamlUtil yamlUtil, DebugUtil logger) {
        this.yamlUtil = yamlUtil;
        this.logger = logger;
    }

    /**
     * 声明一个字符串配置项
     *
     * @param path 配置路径
     * @return 构建器，可继续设置约束
     */
    public ValidatorBuilder validateString(String path) {
        ValidatorBuilder b = new ValidatorBuilder(path, ValidatorBuilder.ValueType.STRING);
        builders.add(b);
        return b;
    }

    /**
     * 声明一个数值配置项
     *
     * @param path 配置路径
     * @return 构建器，可继续设置约束
     */
    public ValidatorBuilder validateNumber(String path) {
        ValidatorBuilder b = new ValidatorBuilder(path, ValidatorBuilder.ValueType.NUMBER);
        builders.add(b);
        return b;
    }

    /**
     * 声明一个枚举配置项
     *
     * @param path  配置路径
     * @param clazz 枚举类型
     * @param <E>   枚举泛型
     * @return 构建器，可继续设置约束
     */
    public <E extends Enum<E>> ValidatorBuilder validateEnum(String path, Class<E> clazz) {
        ValidatorBuilder b = new ValidatorBuilder(path, ValidatorBuilder.ValueType.ENUM, clazz);
        builders.add(b);
        return b;
    }

    /**
     * 对给定配置对象执行校验
     *
     * @param cfg 配置对象
     * @return 校验结果
     */
    public ValidationResult validate(Configuration cfg) {
        List<String> errors = new ArrayList<>();
        for (ValidatorBuilder b : builders) {
            b.apply(cfg, errors);
        }
        boolean ok = errors.isEmpty();
        if (ok) {
            logger.debug("配置校验通过");
        } else {
            logger.warn("配置校验失败，共 " + errors.size() + " 处错误");
            for (String msg : errors) {
                logger.warn(msg);
            }
        }
        return new ValidationResult(ok, errors);
    }
}
