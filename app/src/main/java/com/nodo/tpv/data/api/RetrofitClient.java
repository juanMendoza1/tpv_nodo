package com.nodo.tpv.data.api;

import com.nodo.tpv.util.SessionManager;
import android.content.Context;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RetrofitClient {

    private static Retrofit retrofit = null;
    // IP estándar para acceder al localhost del PC desde el emulador de Android
    private static final String BASE_URL = //"http://192.168.1.8:8080/";
    "http://10.44.11.68:8080/";
    //"http://192.168.1.56:8080/";

    public static ApiService getInterface(Context context) {
        if (retrofit == null) {
            SessionManager sessionManager = new SessionManager(context);

            // 1. Interceptor de Seguridad: Pega el UUID en todas las peticiones
            Interceptor headerInterceptor = chain -> {
                Request original = chain.request();
                String uuid = sessionManager.getTerminalUuid();

                Request.Builder requestBuilder = original.newBuilder()
                        .header("Content-Type", "application/json")
                        .method(original.method(), original.body());

                // Si ya tenemos el UUID guardado, lo enviamos en el header
                if (uuid != null) {
                    requestBuilder.header("X-Terminal-UUID", uuid);
                }

                return chain.proceed(requestBuilder.build());
            };

            // 2. Interceptor de Logs: Para ver el tráfico en Logcat
            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BODY);

            OkHttpClient client = new OkHttpClient.Builder()
                    .addInterceptor(headerInterceptor)
                    .addInterceptor(logging)
                    .build();

            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create())
                    .client(client)
                    .build();
        }
        return retrofit.create(ApiService.class);
    }
}