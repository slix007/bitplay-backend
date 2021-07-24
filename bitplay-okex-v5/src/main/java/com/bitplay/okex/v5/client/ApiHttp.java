package com.bitplay.okex.v5.client;

import com.bitplay.okex.v5.ApiConfiguration;
import com.bitplay.okex.v5.constant.ApiConstants;
import com.bitplay.okex.v5.dto.result.HttpResult;
import com.bitplay.okex.v5.exception.ApiException;
import com.bitplay.okex.v5.helper.OkexObjectMapper;
import com.bitplay.okex.v5.utils.DateUtils;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@SuppressWarnings("ALL")
@Slf4j
public class ApiHttp {

    private OkHttpClient client;
    private ApiConfiguration config;

    static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    public ApiHttp(ApiConfiguration config, OkHttpClient client) {
        this.config = config;
        this.client = client;
    }

    public String get(String url) {
        Request request = new Request.Builder()
                .url(url(url))
                .build();
        return execute(request);
    }

//    public String post(String url, JSONObject params) {
//        String body = params.toJSONString();
//        RequestBody requestBody = RequestBody.create(JSON, body);
//        Request request = new Request.Builder()
//                .url(url(url))
//                .post(requestBody)
//                .build();
//        return execute(request);
//    }
//
//    public String delete(String url, JSONObject params) {
//        String body = params.toJSONString();
//        RequestBody requestBody = RequestBody.create(JSON, body);
//        Request request = new Request.Builder()
//                .url(url(url))
//                .delete(requestBody)
//                .build();
//        return execute(request);
//    }

    public String execute(Request request) {
        try {
            Response response = this.client.newCall(request).execute();
            int status = response.code();
            String bodyString = response.body().string();
            boolean responseIsNotNull = response != null;
            if (this.config.isPrint()) {
                printResponse(status, response.message(), bodyString, responseIsNotNull);
            }
            String message = new StringBuilder().append(response.code()).append(" / ").append(response.message()).toString();
            if (response.isSuccessful()) {
                return bodyString;
            } else if (ApiConstants.resultStatusArray.contains(status)) {
                HttpResult result = OkexObjectMapper.get().readValue(bodyString, HttpResult.class);
                throw new ApiException(result.getCode(), result.getMessage());
            } else {
                throw new ApiException(message);
            }
        } catch (IOException e) {
            throw new ApiException("ApiClient executeSync exception.", e);
        }
    }

    private void printResponse(int status, String message, String body, boolean responseIsNotNull) {
        StringBuilder responseInfo = new StringBuilder();
        responseInfo.append("\n\tResponse").append("(").append(DateUtils.timeToString(null, 4)).append("):");
        if (responseIsNotNull) {
            responseInfo.append("\n\t\t").append("Status: ").append(status);
            responseInfo.append("\n\t\t").append("Message: ").append(message);
            responseInfo.append("\n\t\t").append("Body: ").append(body);
        } else {
            responseInfo.append("\n\t\t").append("\n\tRequest Error: response is null");
        }
        log.info(responseInfo.toString());
    }

    public String url(String url) {
        return new StringBuilder(this.config.getEndpoint()).append(url).toString();
    }
}
