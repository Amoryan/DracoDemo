package com.fxyan.draco.net;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;
import retrofit2.http.Streaming;

/**
 * @author fxYan
 */
public interface Api {
    @Streaming
    @GET("{id}")
    Call<ResponseBody> download(@Path("id") String id);
}

