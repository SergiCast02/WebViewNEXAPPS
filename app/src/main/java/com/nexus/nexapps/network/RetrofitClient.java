package com.nexus.nexapps.network;

import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RetrofitClient {
    // Opción con IP (HTTP)
    private static final String BASE_URL = "http://192.168.0.3:1000/";

    // Opción con dominio (HTTPS, si tu servidor tiene certificado válido)
    //private static final String BASE_URL = "https://pedidos.nexushn.com/";

    private static Retrofit retrofit;

    public static ApiService getApi() {
        if (retrofit == null) {
            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit.create(ApiService.class);
    }
}
