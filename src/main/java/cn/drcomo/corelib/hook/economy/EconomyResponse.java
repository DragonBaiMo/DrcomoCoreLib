package cn.drcomo.corelib.hook.economy;

/**
 * 经济操作响应对象，提供操作是否成功以及可选错误信息。
 */
public class EconomyResponse {

    /** 是否操作成功 */
    public final boolean success;
    /** 失败时的错误信息，成功时可为 null */
    public final String errorMessage;

    /**
     * 构造响应对象。
     *
     * @param success       是否成功
     * @param errorMessage  错误信息（成功时可为 null）
     */
    public EconomyResponse(boolean success, String errorMessage) {
        this.success = success;
        this.errorMessage = errorMessage;
    }

    /**
     * 快捷方法：成功响应。
     */
    public static EconomyResponse ok() {
        return new EconomyResponse(true, null);
    }

    /**
     * 快捷方法：失败响应。
     *
     * @param msg 错误信息
     */
    public static EconomyResponse fail(String msg) {
        return new EconomyResponse(false, msg);
    }
} 