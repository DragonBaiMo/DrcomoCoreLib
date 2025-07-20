package cn.drcomo.corelib.config;

import java.util.Collections;
import java.util.List;

/**
 * 封装配置校验结果的简单数据类。
 */
public class ValidationResult {
    private final boolean success;
    private final List<String> errors;

    /**
     * 构造校验结果
     *
     * @param success 是否全部通过
     * @param errors  错误信息列表
     */
    public ValidationResult(boolean success, List<String> errors) {
        this.success = success;
        this.errors = errors == null ? List.of() : List.copyOf(errors);
    }

    /** 判断是否校验通过 */
    public boolean isSuccess() {
        return success;
    }

    /** 获取所有错误信息（只读） */
    public List<String> getErrors() {
        return Collections.unmodifiableList(errors);
    }
}
