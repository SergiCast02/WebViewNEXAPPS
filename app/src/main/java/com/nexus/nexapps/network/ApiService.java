package com.nexus.nexapps.network;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.POST;

public interface ApiService {
    @POST("api/register_token/")
    Call<Void> registerToken(
            @Header("Authorization") String bearerToken,
            @Body TokenRequest body
    );
}
