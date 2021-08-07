package com.bitplay.okex.v5.client;

import com.bitplay.okex.v5.ApiConfigurationV5;
import com.bitplay.okex.v5.constant.ApiConstants;
import com.bitplay.okex.v5.enums.ContentTypeEnum;
import com.bitplay.okex.v5.enums.HttpHeadersEnum;
import com.bitplay.okex.v5.enums.I18nEnum;
import com.bitplay.okex.v5.exception.ApiException;
import com.bitplay.okex.v5.utils.DateUtils;
import com.bitplay.okex.v5.utils.HmacSHA256Base64Utils;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.util.concurrent.TimeUnit;
import okhttp3.Headers;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okio.Buffer;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

public class ApiHttpClient {

    private final ApiConfigurationV5 config;
    private final ApiCredentialsV5 credentials;
    private final Logger log;

    public ApiHttpClient(final ApiConfigurationV5 config, final ApiCredentialsV5 credentials, Logger log) {
        this.config = config;
        this.credentials = credentials;
        this.log = log;
    }

    /**
     * Get a ok http 3 client object. <br/> Declare:
     * <blockquote><pre>
     *  1. Set default client args:
     *         connectTimeout=30s
     *         readTimeout=30s
     *         writeTimeout=30s
     *         retryOnConnectionFailure=true.
     *  2. Set request headers:
     *      Content-Type: application/json; charset=UTF-8  (default)
     *      Cookie: locale=en_US        (English)
     *      OK-ACCESS-KEY: (Your setting)
     *      OK-ACCESS-SIGN: (Use your setting, auto sign and add)
     *      OK-ACCESS-TIMESTAMP: (Auto add)
     *      OK-ACCESS-PASSPHRASE: Your setting
     *  3. Set default print api info: false.
     * </pre></blockquote>
     */
    public OkHttpClient client() {
        final OkHttpClient.Builder clientBuilder = new OkHttpClient.Builder();
        clientBuilder.connectTimeout(this.config.getConnectTimeout(), TimeUnit.SECONDS);
        clientBuilder.readTimeout(this.config.getReadTimeout(), TimeUnit.SECONDS);
        clientBuilder.writeTimeout(this.config.getWriteTimeout(), TimeUnit.SECONDS);
        clientBuilder.retryOnConnectionFailure(this.config.isRetryOnConnectionFailure());
        clientBuilder.addInterceptor((Interceptor.Chain chain) -> {
            final Request.Builder requestBuilder = chain.request().newBuilder();
            final String timestamp = DateUtils.getUnixTime();
//            System.out.println("timestamp={" + timestamp + "}");
            requestBuilder.headers(this.headers(chain.request(), timestamp));
            final Request request = requestBuilder.build();
            if (this.config.isPrint()) {
                this.printRequest(request, timestamp);
            }
            return chain.proceed(request);
        });
        return clientBuilder.build();
    }

    private Headers headers(final Request request, final String timestamp) {
        final Headers.Builder builder = new Headers.Builder();
        builder.add(ApiConstants.ACCEPT, ContentTypeEnum.APPLICATION_JSON.contentType());
        builder.add(ApiConstants.CONTENT_TYPE, ContentTypeEnum.APPLICATION_JSON_UTF8.contentType());
        builder.add(ApiConstants.COOKIE, this.getCookie());
        if (StringUtils.isNotEmpty(this.credentials.getSecretKey())) {
            builder.add(HttpHeadersEnum.OK_ACCESS_KEY.header(), this.credentials.getApiKey());
            builder.add(HttpHeadersEnum.OK_ACCESS_SIGN.header(), this.sign(request, timestamp));
            builder.add(HttpHeadersEnum.OK_ACCESS_TIMESTAMP.header(), timestamp);
            builder.add(HttpHeadersEnum.OK_ACCESS_PASSPHRASE.header(), this.credentials.getPassphrase());
        }
        return builder.build();
    }

    private String getCookie() {
        final StringBuilder cookie = new StringBuilder();
        cookie.append(ApiConstants.LOCALE).append(I18nEnum.ENGLISH.i18n());
        return cookie.toString();
    }

    private String sign(final Request request, final String timestamp) {
        final String sign;
        try {
            sign = HmacSHA256Base64Utils.sign(timestamp, this.method(request), this.requestPath(request),
                    this.queryString(request), this.body(request), this.credentials.getSecretKey());
        } catch (final IOException e) {
            throw new ApiException("Request get body io exception.", e);
        } catch (final CloneNotSupportedException e) {
            throw new ApiException("Hmac SHA256 Base64 Signature clone not supported exception.", e);
        } catch (final InvalidKeyException e) {
            throw new ApiException("Hmac SHA256 Base64 Signature invalid key exception.", e);
        }
        return sign;
    }

    private String url(final Request request) {
        return request.url().toString();
    }

    private String method(final Request request) {
        return request.method().toUpperCase();
    }

    private String requestPath(final Request request) {
        String url = this.url(request);
        url = url.replace(this.config.getEndpoint(), ApiConstants.EMPTY);
        String requestPath = url;
        if (requestPath.contains(ApiConstants.QUESTION)) {
            requestPath = requestPath.substring(0, url.lastIndexOf(ApiConstants.QUESTION));
        }
        if (this.config.getEndpoint().endsWith(ApiConstants.SLASH)) {
            requestPath = ApiConstants.SLASH + requestPath;
        }
        return requestPath;
    }

    private String queryString(final Request request) {
        final String url = this.url(request);
        String queryString = ApiConstants.EMPTY;
        if (url.contains(ApiConstants.QUESTION)) {
            queryString = url.substring(url.lastIndexOf(ApiConstants.QUESTION) + 1);
        }
        return queryString;
    }

    private String body(final Request request) throws IOException {
        final RequestBody requestBody = request.body();
        String body = ApiConstants.EMPTY;
        if (requestBody != null) {
            final Buffer buffer = new Buffer();
            requestBody.writeTo(buffer);
            body = buffer.readString(ApiConstants.UTF_8);
        }
        return body;
    }

    private void printRequest(final Request request, final String timestamp) {
        final String method = this.method(request);
        final String requestPath = this.requestPath(request);
        final String queryString = this.queryString(request);
        final String body;
        try {
            body = this.body(request);
        } catch (final IOException e) {
            throw new ApiException("Request get body io exception.", e);
        }
        final StringBuilder requestInfo = new StringBuilder();
//        requestInfo.append("\n").append("\tSecret-Key: ").append(this.credentials.getSecretKey());
        requestInfo.append("\n\tOkex-v5 Request").append("(").append(DateUtils.timeToString(null, 4)).append("):");
        requestInfo.append("\n\t\t").append("Url: ").append(this.url(request));
        requestInfo.append("\n\t\t").append("Method: ").append(method);
        requestInfo.append("\n\t\t").append("Headers: ");
        final Headers headers = request.headers();
        if (headers != null && headers.size() > 0) {
            for (final String name : headers.names()) {
                requestInfo.append("\n\t\t\t").append(name).append(": ").append(headers.get(name));
            }
        }
        requestInfo.append("\n\t\t").append("Body: ").append(body);
        final String preHash = HmacSHA256Base64Utils.preHash(timestamp, method, requestPath, queryString, body);
        requestInfo.append("\n\t\t").append("preHash: ").append(preHash);
        log.trace(requestInfo.toString());
    }
}
