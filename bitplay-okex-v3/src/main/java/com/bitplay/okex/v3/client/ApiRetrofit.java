package com.bitplay.okex.v3.client;

import com.bitplay.okex.v3.ApiConfiguration;
import com.bitplay.okex.v3.helper.OkexObjectMapper;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;


public class ApiRetrofit {

    private ApiConfiguration config;
    private OkHttpClient client;

    public ApiRetrofit(ApiConfiguration config, OkHttpClient client) {
        this.config = config;
        this.client = client;
    }

    public Retrofit retrofit() {
        Retrofit.Builder builder = new Retrofit.Builder();
        builder.client(this.client);
//        builder.addConverterFactory(ScalarsConverterFactory.create());
//        builder.addConverterFactory(GsonConverterFactory.create());
        final JacksonConverterFactory factory = JacksonConverterFactory.create(OkexObjectMapper.get());
        builder.addConverterFactory(factory);
//        builder.addCallAdapterFactory(RxJavaCallAdapterFactory.create());
        builder.baseUrl(this.config.getEndpoint());
        return builder.build();
    }
}
