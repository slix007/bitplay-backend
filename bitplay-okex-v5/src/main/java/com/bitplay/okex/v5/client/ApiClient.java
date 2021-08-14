package com.bitplay.okex.v5.client;

import com.bitplay.okex.v5.ApiConfigurationV5;
import com.bitplay.okex.v5.constant.ApiConstants;
import com.bitplay.okex.v5.dto.result.HttpResult;
import com.bitplay.okex.v5.enums.HttpHeadersEnum;
import com.bitplay.okex.v5.exception.ApiException;
import com.bitplay.okex.v5.helper.OkexObjectMapper;
import com.bitplay.okex.v5.utils.DateUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.SocketTimeoutException;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;


public class ApiClient {

    private final Logger log;

    private final ApiConfigurationV5 config;
    private final ApiCredentialsV5 credentials;
    private final OkHttpClient client;
    private final Retrofit retrofit;
    private final ApiHttp apiHttp;

    /**
     * Initialize the apis client
     */
    public ApiClient(final ApiConfigurationV5 config, String arbTypeUpperCase) {
        if (config == null || StringUtils.isEmpty(config.getEndpoint())) {
            throw new RuntimeException("The ApiClient params can't be empty.");
        }
        log = LoggerFactory.getLogger("OKEX_V5_" + arbTypeUpperCase);
        this.config = config;
        this.credentials = new ApiCredentialsV5(config);
        this.client = new ApiHttpClient(config, this.credentials, log).client();
        this.retrofit = new ApiRetrofit(config, this.client).retrofit();
        this.apiHttp = new ApiHttp(config, this.client);
    }

    /**
     * Initialize the retrofit operation service
     */
    public <T> T createService(final Class<T> service) {
        return this.retrofit.create(service);
    }

    public ApiHttp getApiHttp() {
        return this.apiHttp;
    }

    /**
     * Synchronous send request
     */
    public <T> T executeSync(final Call<T> call) {
        try {
            final Response<T> response = call.execute();
            if (this.config.isPrint()) {
                this.printResponse(response, call.request().url().toString());
            }
            final int status = response.code();
            final String message = new StringBuilder().append(response.code()).append(" / ").append(response.message()).toString();
            if (response.isSuccessful()) {
                return response.body();
            } else if (ApiConstants.resultStatusArray.contains(status)) {
                final String content = new String(response.errorBody().bytes());
                log.error("ApiClient error: " + content);
                final HttpResult result = OkexObjectMapper.get().readValue(content, HttpResult.class);
                if (result == null) {
                    throw new ApiException("ApiClient executeSync exception=" + content);
                }
                if (result.getCode() == 0 && result.getMessage() == null) {
                    throw new ApiException(result.getError_code(), content);
                } else {
                    throw new ApiException(result.getCode(), result.getMessage() + "raw: " + content);
                }
            } else {
                throw new ApiException(message);
            }
        } catch (final SocketTimeoutException e) {
            printResponse(e, call.request());
            throw new ApiException("ApiClient executeSync exception." + e.getMessage());
        } catch (final IOException e) {
            printResponse(e, call.request());
            throw new ApiException("ApiClient executeSync exception.", e);
        }
    }

//    /**
//     * Synchronous send request
//     */
//    public <T> CursorPager<T> executeSyncCursorPager(final Call<List<T>> call) {
//        try {
//            final Response<List<T>> response = call.execute();
//            if (this.config.isPrint()) {
//                this.printResponse(response);
//            }
//            final int status = response.code();
//            final String message = response.code() + " / " + response.message();
//            if (response.isSuccessful()) {
//                final Headers headers = response.headers();
//                final CursorPager<T> cursorPager = new CursorPager<>();
//                cursorPager.setData(response.body());
//                cursorPager.setBefore(headers.get("OK-BEFORE"));
//                cursorPager.setAfter(headers.get("OK-AFTER"));
//                cursorPager.setLimit(Optional.ofNullable(headers.get("OK-LIMIT")).map(Integer::valueOf).orElse(100));
//                return cursorPager;
//            }
//            if (ApiConstants.resultStatusArray.contains(status)) {
//                final HttpResult result = JSON.parseObject(new String(response.errorBody().bytes()), HttpResult.class);
//                throw new ApiException(result.getCode(), result.getMessage());
//            }
//            throw new ApiException(message);
//        } catch (final IOException e) {
//            throw new ApiException("ApiClient executeSync exception.", e);
//        }
//    }

    private void printResponse(final Exception e, final Request request) {
        final StringBuilder responseInfo = new StringBuilder();
        responseInfo.append("\n\tOkex-v5 Request error").append("(").append(DateUtils.timeToString(null, 4)).append("):");
        responseInfo.append("\n\t\t")
                .append(request.method())
                .append("Url: ").append(request.url());
        responseInfo.append("\n\t\t")
                .append("Exception: ").append(e.getClass())
                .append(": ").append(e.getMessage());
        log.trace(responseInfo.toString());
    }

    private void printResponse(final Response response, final String requestUrl) {
        final StringBuilder responseInfo = new StringBuilder();
        responseInfo.append("\n\tOkex-v5 Response").append("(").append(DateUtils.timeToString(null, 4)).append("):");
        responseInfo.append("\n\t\t").append("Url: ").append(requestUrl);
        if (response != null) {
            final String limit = response.headers().get(HttpHeadersEnum.OK_LIMIT.header());
            if (StringUtils.isNotEmpty(limit)) {
                responseInfo.append("\n\t\t").append("Headers: ");
//                responseInfo.append("\n\t\t\t").append(HttpHeadersEnum.OK_BEFORE.header()).append(": ").append(response.headers().get(HttpHeadersEnum.OK_BEFORE.header()));
//                responseInfo.append("\n\t\t\t").append(HttpHeadersEnum.OK_AFTER.header()).append(": ").append(response.headers().get(HttpHeadersEnum.OK_AFTER.header()));
                responseInfo.append("\n\t\t\t").append(HttpHeadersEnum.OK_FROM.header()).append(": ")
                        .append(response.headers().get(HttpHeadersEnum.OK_FROM.header()));
                responseInfo.append("\n\t\t\t").append(HttpHeadersEnum.OK_TO.header()).append(": ")
                        .append(response.headers().get(HttpHeadersEnum.OK_TO.header()));
                responseInfo.append("\n\t\t\t").append(HttpHeadersEnum.OK_LIMIT.header()).append(": ").append(limit);
            }
            responseInfo.append("\n\t\t").append("Status: ").append(response.code());
            responseInfo.append("\n\t\t").append("Message: ").append(response.message());
            final ObjectMapper objectMapper = OkexObjectMapper.get();
            responseInfo.append("\n\t\t").append("Body: ").append(response.body());
//            try {
//                final ResponseBody rawBody = response.raw().body();
//                if (rawBody != null && rawBody.contentLength() > 0) {
//                    final String str = rawBody
//                            .source()
//                            .readUtf8();
//                    responseInfo.append("\n\t\t").append("RawBody: ").append(str);
//
//                }
//
//            } catch (Exception e) {
//                responseInfo.append("\n\t\t").append("RawBody exception ").append(e.getMessage());
//            }
        } else {
            responseInfo.append("\n\t\t").append("\n\tRequest Error: response is null");
        }
        log.trace(responseInfo.toString());
    }
}
