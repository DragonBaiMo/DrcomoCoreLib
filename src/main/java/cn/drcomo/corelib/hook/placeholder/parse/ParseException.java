package cn.drcomo.corelib.hook.placeholder.parse;

/**
 * 解析条件表达式时抛出的异常。
 */
public class ParseException extends Exception {

    /**
     * 使用错误信息构造异常实例。
     *
     * @param message 错误信息
     */
    public ParseException(String message) {
        super(message);
    }
} 