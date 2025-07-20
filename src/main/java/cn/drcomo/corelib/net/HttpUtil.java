package cn.drcomo.corelib.net;

import cn.drcomo.corelib.util.DebugUtil;

import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * HTTP 请求工具类，使用 Java 11 {@link HttpClient} 实现异步网络访问。
 * <p>通过构建器配置代理、超时和重试次数。</p>
 */
public class HttpUtil {

    private final DebugUtil logger;
    private final HttpClient client;
    private final Duration timeout;
    private final int maxRetries;

    private HttpUtil(DebugUtil logger, HttpClient client, Duration timeout, int maxRetries) {
        this.logger = logger;
        this.client = client;
        this.timeout = timeout;
        this.maxRetries = maxRetries;
    }

    /**
     * 创建 Builder 用于配置 HttpUtil。
     *
     * @return Builder 实例
     */
    public static Builder newBuilder() {
        return new Builder();
    }

    /**
     * 发送 GET 请求。
     *
     * @param url     请求地址
     * @param headers 请求头，可为空
     * @return 异步响应字符串
     */
    public CompletableFuture<String> get(String url, Map<String, String> headers) {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(url)).GET();
        applyHeaders(req, headers);
        return sendAsync(req.build(), 0);
    }

    /**
     * 发送 POST 请求，Body 为字符串。
     *
     * @param url     请求地址
     * @param body    请求体
     * @param headers 请求头，可为空
     * @return 异步响应字符串
     */
    public CompletableFuture<String> post(String url, String body, Map<String, String> headers) {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(url))
                .POST(HttpRequest.BodyPublishers.ofString(body));
        applyHeaders(req, headers);
        return sendAsync(req.build(), 0);
    }

    /**
     * 上传文件。
     *
     * @param url     请求地址
     * @param path    文件路径
     * @param headers 请求头，可为空
     * @return 异步响应字符串
     */
    public CompletableFuture<String> upload(String url, java.nio.file.Path path, Map<String, String> headers) {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(url))
                .POST(HttpRequest.BodyPublishers.ofFile(path));
        applyHeaders(req, headers);
        return sendAsync(req.build(), 0);
    }

    private void applyHeaders(HttpRequest.Builder builder, Map<String, String> headers) {
        if (headers != null) {
            headers.forEach(builder::header);
        }
        builder.timeout(timeout);
    }

    private CompletableFuture<String> sendAsync(HttpRequest request, int attempt) {
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenApply(HttpResponse::body)
                .exceptionallyCompose(ex -> {
                    logger.error("网络请求失败: " + ex.getMessage());
                    if (attempt < maxRetries) {
                        logger.warn("重试中... (" + (attempt + 1) + "/" + maxRetries + ")");
                        return sendAsync(request, attempt + 1);
                    }
                    CompletableFuture<String> fail = new CompletableFuture<>();
                    fail.completeExceptionally(ex);
                    return fail;
                });
    }

    /**
     * Builder 用于构建 HttpUtil。
     */
    public static class Builder {
        private DebugUtil logger;
        private ProxySelector proxy;
        private Duration timeout = Duration.ofSeconds(10);
        private int retries = 0;

        /**
         * 设置日志工具。
         */
        public Builder logger(DebugUtil logger) {
            this.logger = logger;
            return this;
        }

        /**
         * 设置 HTTP 代理。
         *
         * @param host 主机名
         * @param port 端口
         */
        public Builder proxy(String host, int port) {
            this.proxy = ProxySelector.of(new InetSocketAddress(host, port));
            return this;
        }

        /**
         * 设置超时时间。
         *
         * @param timeout 超时
         */
        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        /**
         * 设置最大重试次数。
         *
         * @param retries 次数
         */
        public Builder retries(int retries) {
            this.retries = Math.max(0, retries);
            return this;
        }

        /**
         * 构建 HttpUtil 实例。
         *
         * @return HttpUtil
         */
        public HttpUtil build() {
            HttpClient.Builder cb = HttpClient.newBuilder();
            if (proxy != null) {
                cb.proxy(proxy);
            }
            HttpClient client = cb.build();
            return new HttpUtil(logger, client, timeout, retries);
        }
    }
}
