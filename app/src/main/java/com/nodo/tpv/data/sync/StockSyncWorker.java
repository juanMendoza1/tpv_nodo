package com.nodo.tpv.data.sync;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.nodo.tpv.data.api.RetrofitClient;
import com.nodo.tpv.data.database.AppDatabase;
import com.nodo.tpv.data.entities.DetallePedido;
import java.util.List;
import retrofit2.Response;

public class StockSyncWorker extends Worker {

    public StockSyncWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        AppDatabase db = AppDatabase.getInstance(getApplicationContext());

        // 1. Obtener registros locales no sincronizados
        List<DetallePedido> pendientes = db.detallePedidoDao().obtenerDespachosPendientesSincronizar();

        if (pendientes.isEmpty()) return Result.success();

        boolean huboError = false;

        for (DetallePedido dp : pendientes) {
            try {
                // 2. Ejecutar petición
                // Nota: Asegúrate de que los nombres de los parámetros coincidan con tu backend
                Response<okhttp3.ResponseBody> response = RetrofitClient.getInterface(getApplicationContext())
                        .reportarDespacho(
                                (long) dp.idProducto,
                                dp.cantidad,
                                dp.idDueloOrigen,
                                "SYNC_AUTO"
                        ).execute();

                if (response.isSuccessful()) {
                    // 3. Si el servidor responde 200 OK, marcamos como sincronizado
                    db.detallePedidoDao().marcarComoSincronizado(dp.idDetalle);
                    Log.d("SYNC", "ID " + dp.idDetalle + " sincronizado. Respuesta: " + response.code());
                } else {
                    // Si hay un error 400 (ej: no hay stock), marcamos huboError para reintentar luego
                    Log.e("SYNC", "Error servidor ID " + dp.idDetalle + ": " + response.code());
                    huboError = true;
                }
            } catch (Exception e) {
                Log.e("SYNC", "Fallo de conexión para ID: " + dp.idDetalle, e);
                huboError = true;
            }
        }

        // Si hubo errores (ej. se cayó el internet a mitad), WorkManager reintenta después
        return huboError ? Result.retry() : Result.success();
    }
}