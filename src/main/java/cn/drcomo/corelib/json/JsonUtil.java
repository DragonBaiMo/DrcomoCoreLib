package cn.drcomo.corelib.json;

import cn.drcomo.corelib.util.DebugUtil;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * JSON 工具类，封装了常用的序列化与文件读写操作。
 * <p>通过注入 {@link DebugUtil} 输出调试信息，并持有一个 {@link Gson} 实例。</p>
 */
public class JsonUtil {

    private final DebugUtil logger;
    private final Gson gson;
    /**
     * 用于美化输出的 {@link Gson} 实例。
     */
    private final Gson prettyGson;

    /**
     * 构造函数，使用默认的 {@link Gson} 配置。
     *
     * @param logger DebugUtil 实例
     */
    public JsonUtil(DebugUtil logger) {
        this(logger, new GsonBuilder().create(), new GsonBuilder().setPrettyPrinting().create());
    }

    /**
     * 构造函数，允许自定义 {@link Gson} 实例。
     *
     * @param logger DebugUtil 实例
     * @param gson   自定义的 Gson 实例
     */
    public JsonUtil(DebugUtil logger, Gson gson) {
        this(logger, gson, new GsonBuilder().setPrettyPrinting().create());
    }

    /**
     * 构造函数，允许自定义常规 {@link Gson} 与美化输出的 {@code prettyGson} 实例。
     *
     * @param logger     DebugUtil 实例
     * @param gson       自定义的 Gson 实例
     * @param prettyGson 自定义的美化输出 Gson 实例
     */
    public JsonUtil(DebugUtil logger, Gson gson, Gson prettyGson) {
        this.logger = logger;
        this.gson = gson == null ? new GsonBuilder().create() : gson;
        this.prettyGson =
                prettyGson == null ? new GsonBuilder().setPrettyPrinting().create() : prettyGson;
    }

    /**
     * 将对象序列化为 JSON 字符串。
     *
     * @param obj 需要序列化的对象
     * @return JSON 字符串
     */
    public String toJson(Object obj) {
        try {
            return gson.toJson(obj);
        } catch (Exception e) {
            logger.error("序列化对象失败", e);
            return "";
        }
    }

    /**
     * 从 JSON 字符串反序列化对象。
     *
     * @param json  JSON 字符串
     * @param clazz 目标类型
     * @param <T>   泛型类型
     * @return 解析后的对象
     * @throws IllegalArgumentException JSON 解析失败
     */
    public <T> T fromJson(String json, Class<T> clazz) throws IllegalArgumentException {
        try {
            return gson.fromJson(json, clazz);
        } catch (JsonParseException e) {
            logger.error("解析 JSON 失败", e);
            throw new IllegalArgumentException("Invalid JSON", e);
        }
    }

    /**
     * 使用 {@link TypeToken} 解析复杂泛型类型。
     *
     * @param json      JSON 字符串
     * @param typeToken Gson TypeToken
     * @param <T>       泛型类型
     * @return 解析后的对象
     * @throws IllegalArgumentException JSON 解析失败
     */
    public <T> T fromJson(String json, TypeToken<T> typeToken) throws IllegalArgumentException {
        try {
            return gson.fromJson(json, typeToken.getType());
        } catch (JsonParseException e) {
            logger.error("解析 JSON 失败", e);
            throw new IllegalArgumentException("Invalid JSON", e);
        }
    }

    /**
     * 从文件读取并解析 JSON。
     *
     * @param path  文件路径
     * @param clazz 目标类型
     * @param <T>   泛型类型
     * @return 解析后的对象
     * @throws IllegalStateException 读取或解析失败
     */
    public <T> T readJsonFile(Path path, Class<T> clazz) throws IllegalStateException {
        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            return fromJson(json, clazz);
        } catch (IOException e) {
            logger.error("读取 JSON 文件失败: " + path, e);
            throw new IllegalStateException("无法读取文件", e);
        }
    }

    /**
     * 从文件读取并解析复杂泛型类型。
     *
     * @param path      文件路径
     * @param typeToken Gson TypeToken
     * @param <T>       泛型类型
     * @return 解析后的对象
     * @throws IllegalStateException 读取或解析失败
     */
    public <T> T readJsonFile(Path path, TypeToken<T> typeToken) throws IllegalStateException {
        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);
            return fromJson(json, typeToken);
        } catch (IOException e) {
            logger.error("读取 JSON 文件失败: " + path, e);
            throw new IllegalStateException("无法读取文件", e);
        }
    }

    /**
     * 将对象写入 JSON 文件，自动创建缺失的父目录。
     *
     * @param path 文件路径
     * @param obj  待写入对象
     */
    public void writeJsonFile(Path path, Object obj) {
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            Files.writeString(path, gson.toJson(obj), StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.error("写入 JSON 文件失败: " + path, e);
        }
    }

    /**
     * 检查字符串是否为合法 JSON。
     *
     * @param json JSON 字符串
     * @return 如果合法返回 true
     */
    public boolean isValidJson(String json) {
        try {
            gson.fromJson(json, Object.class);
            return true;
        } catch (JsonParseException e) {
            return false;
        }
    }

    /**
     * 美化输出 JSON 字符串。
     *
     * @param json 原始 JSON
     * @return 格式化后的 JSON
     * @implNote 该方法线程安全，可在多线程环境下安全使用。
     */
    public String prettyPrint(String json) {
        try {
            Object obj = gson.fromJson(json, Object.class);
            return prettyGson.toJson(obj);
        } catch (JsonParseException e) {
            logger.error("美化 JSON 失败", e);
            return json;
        }
    }
}
