package cn.drcomo.corelib.config;

/**
 * 配置结构声明接口。
 * <p>实现此接口以在 {@link ConfigValidator} 上定义校验规则。</p>
 */
@FunctionalInterface
public interface ConfigSchema {
    /**
     * 使用给定的校验器声明配置结构。
     *
     * @param validator 配置校验器
     */
    void configure(ConfigValidator validator);
}

