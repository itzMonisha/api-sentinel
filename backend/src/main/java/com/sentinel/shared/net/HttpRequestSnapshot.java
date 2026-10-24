package com.sentinel.shared.net;

import java.util.Collections;
import java.util.Map;

/**
 * Immutable snapshot of an outbound HTTP request.
 */
public class HttpRequestSnapshot {

    private final String url;
    private final String httpMethod;
    private final Map<String, String> headers;
    private final Map<String, String> queryParams;
    private final String body;
    private final Integer connectTimeoutMs;
    private final Integer readTimeoutMs;
    private final boolean followRedirects;
    private final int maxRedirectHops;

    public HttpRequestSnapshot(
            String url,
            String httpMethod,
            Map<String, String> headers,
            Map<String, String> queryParams,
            String body,
            Integer connectTimeoutMs,
            Integer readTimeoutMs,
            boolean followRedirects,
            int maxRedirectHops) {
        this.url = url;
        this.httpMethod = httpMethod != null ? httpMethod.toUpperCase() : "GET";
        this.headers = headers != null ? headers : Collections.emptyMap();
        this.queryParams = queryParams != null ? queryParams : Collections.emptyMap();
        this.body = body;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
        this.followRedirects = followRedirects;
        this.maxRedirectHops = maxRedirectHops > 0 ? maxRedirectHops : 3;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getUrl() {
        return url;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public Map<String, String> getQueryParams() {
        return queryParams;
    }

    public String getBody() {
        return body;
    }

    public Integer getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public Integer getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public boolean isFollowRedirects() {
        return followRedirects;
    }

    public int getMaxRedirectHops() {
        return maxRedirectHops;
    }

    public static class Builder {
        private String url;
        private String httpMethod = "GET";
        private Map<String, String> headers = Collections.emptyMap();
        private Map<String, String> queryParams = Collections.emptyMap();
        private String body;
        private Integer connectTimeoutMs;
        private Integer readTimeoutMs;
        private boolean followRedirects = false;
        private int maxRedirectHops = 3;

        public Builder url(String url) {
            this.url = url;
            return this;
        }

        public Builder httpMethod(String httpMethod) {
            this.httpMethod = httpMethod;
            return this;
        }

        public Builder headers(Map<String, String> headers) {
            this.headers = headers;
            return this;
        }

        public Builder queryParams(Map<String, String> queryParams) {
            this.queryParams = queryParams;
            return this;
        }

        public Builder body(String body) {
            this.body = body;
            return this;
        }

        public Builder connectTimeoutMs(Integer connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
            return this;
        }

        public Builder readTimeoutMs(Integer readTimeoutMs) {
            this.readTimeoutMs = readTimeoutMs;
            return this;
        }

        public Builder followRedirects(boolean followRedirects) {
            this.followRedirects = followRedirects;
            return this;
        }

        public Builder maxRedirectHops(int maxRedirectHops) {
            this.maxRedirectHops = maxRedirectHops;
            return this;
        }

        public HttpRequestSnapshot build() {
            return new HttpRequestSnapshot(
                    url,
                    httpMethod,
                    headers,
                    queryParams,
                    body,
                    connectTimeoutMs,
                    readTimeoutMs,
                    followRedirects,
                    maxRedirectHops
            );
        }
    }
}
