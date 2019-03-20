package com.fxyan.draco.net;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * @author fxYan
 */
public final class ApiCreator {

    private Retrofit retrofit;

    private ApiCreator() {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(20, TimeUnit.SECONDS)
                .build();
        retrofit = new Retrofit.Builder()
                .baseUrl("https://api.app.jpark.vip/api/file/get/")
                .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
                .addConverterFactory(GsonConverterFactory.create())
                .client(client)
                .build();
    }

    public static Api api() {
        return Holder.instance.retrofit.create(Api.class);
    }

    private static class Holder {
        private static final ApiCreator instance = new ApiCreator();
    }

}
