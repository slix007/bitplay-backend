package com.bitplay.okex.v3.client;

import com.bitplay.okex.v3.ApiConfiguration;
import com.bitplay.okex.v3.constant.ApiConstants;
import com.bitplay.okex.v3.dto.futures.HttpResult;
import com.bitplay.okex.v3.enums.HttpHeadersEnum;
import com.bitplay.okex.v3.exception.ApiException;
import com.bitplay.okex.v3.helper.OkexObjectMapper;
import com.bitplay.okex.v3.utils.DateUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.apache.commons.lang3.StringUtils;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;

import java.io.IOException;
import java.net.SocketTimeoutException;


@Slf4j
public class ApiClient {

    private final ApiConfiguration config;
    private final ApiCredentials credentials;
    private final OkHttpClient client;
    private final Retrofit retrofit;
    private final ApiHttp apiHttp;

    /**
     * Initialize the apis client
     */
    public ApiClient(final ApiConfiguration config) {
        if (config == null || StringUtils.isEmpty(config.getEndpoint())) {
            throw new RuntimeException("The ApiClient params can't be empty.");
        }
        this.config = config;
        this.credentials = new ApiCredentials(config);
        this.client = new ApiHttpClient(config, this.credentials).client();
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
                this.printResponse(response);
            }
            final int status = response.code();
            final String message = new StringBuilder().append(response.code()).append(" / ").append(response.message()).toString();
            if (response.isSuccessful()) {
                return response.body();
            } else if (ApiConstants.resultStatusArray.contains(status)) {
                final String content = new String(response.errorBody().bytes());
                final HttpResult result = OkexObjectMapper.get().readValue(content, HttpResult.class);
                if (result == null) {
                    log.error(content);
                    throw new ApiException("ApiClient executeSync exception=" + content);
                }
                if (result.getCode() == 0 && result.getMessage() == null) {
                    throw new ApiException(result.getErrorCode(), result.getErrorMessage());
                } else {
                    throw new ApiException(result.getCode(), result.getMessage());
                }
            } else {
                throw new ApiException(message);
            }
        } catch (final SocketTimeoutException e) {
            throw new ApiException("ApiClient executeSync exception." + e.getMessage());
        } catch (final IOException e) {
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

    private void printResponse(final Response response) {
        final StringBuilder responseInfo = new StringBuilder();
        responseInfo.append("\n\tOkex-v3 Response").append("(").append(DateUtils.timeToString(null, 4)).append("):");
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
        } else {
            responseInfo.append("\n\t\t").append("\n\tRequest Error: response is null");
        }
        log.trace(responseInfo.toString());
    }
}
