package com.nodo.tpv.data.api;

import android.content.Context;

import com.nodo.tpv.BuildConfig;
import com.nodo.tpv.util.SessionManager;

import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RetrofitClient {

    private static Retrofit retrofit = null;

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
            // Recomendación: En producción podrías cambiar esto a Level.NONE para que la app sea más rápida,
            // pero Level.BODY está perfecto para desarrollo.
            logging.setLevel(HttpLoggingInterceptor.Level.BODY);

            // 3. Configuración del Cliente HTTP (Se agregan Timeouts para evitar cuelgues por mala conexión)
            OkHttpClient client = new OkHttpClient.Builder()
                    .addInterceptor(headerInterceptor)
                    .addInterceptor(logging)
                    .connectTimeout(30, TimeUnit.SECONDS) // Tiempo máximo para conectar al servidor
                    .readTimeout(30, TimeUnit.SECONDS)    // Tiempo máximo esperando respuesta
                    .writeTimeout(30, TimeUnit.SECONDS)   // Tiempo máximo para enviar datos
                    .build();

            // 4. Construcción de Retrofit con la URL dinámica de Gradle
            retrofit = new Retrofit.Builder()
                    .baseUrl(BuildConfig.BASE_URL)
                    .addConverterFactory(GsonConverterFactory.create())
                    .client(client)
                    .build();
        }
        return retrofit.create(ApiService.class);
    }
}