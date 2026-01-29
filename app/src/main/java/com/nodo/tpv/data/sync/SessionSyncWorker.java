package com.nodo.tpv.data.sync;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.nodo.tpv.data.api.RetrofitClient;
import com.nodo.tpv.data.database.AppDatabase;
import com.nodo.tpv.data.entities.LogSesion;
import java.util.List;
import okhttp3.ResponseBody;
import retrofit2.Response;

public class SessionSyncWorker extends Worker {

    public SessionSyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        AppDatabase db = AppDatabase.getInstance(getApplicationContext());
        List<LogSesion> logsPendientes = db.usuarioSlotDao().obtenerLogsPendientes();

        if (logsPendientes.isEmpty()) return Result.success();

        boolean huboError = false;

        for (LogSesion log : logsPendientes) {
            try {
                // Debes crear este endpoint en tu ApiService del Backend
                Response<ResponseBody> response = RetrofitClient.getInterface(getApplicationContext())
                        .reportarEventoSesion(
                                log.idUsuario,
                                log.slot,
                                log.tipoEvento,
                                log.timestamp
                        ).execute();

                if (response.isSuccessful()) {
                    // Marcamos como sincronizado en Room
                    db.usuarioSlotDao().marcarLogSincronizado(log.idLog);
                } else {
                    huboError = true;
                }
            } catch (Exception e) {
                huboError = true;
            }
        }

        return huboError ? Result.retry() : Result.success();
    }
}