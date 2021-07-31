package com.bitplay.okex.v5.client;

import com.bitplay.okex.v5.ApiConfigurationV5;
import com.bitplay.okex.v5.helper.OkexObjectMapper;
import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.converter.jackson.JacksonConverterFactory;


public class ApiRetrofit {

    private ApiConfigurationV5 config;
    private OkHttpClient client;

    public ApiRetrofit(ApiConfigurationV5 config, OkHttpClient client) {
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
