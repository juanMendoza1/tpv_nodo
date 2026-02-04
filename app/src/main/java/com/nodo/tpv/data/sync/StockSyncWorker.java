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
        List<DetallePedido> pendientes = db.detallePedidoDao().obtenerDespachosPendientesSincronizar();

        if (pendientes.isEmpty()) return Result.success();

        boolean huboErrorDeRed = false;

        for (DetallePedido dp : pendientes) {
            try {
                Response<okhttp3.ResponseBody> response = RetrofitClient.getInterface(getApplicationContext())
                        .reportarDespacho(
                                (long) dp.idProducto,
                                dp.cantidad,
                                dp.idDueloOrigen,
                                "SYNC_AUTO"
                        ).execute();

                if (response.isSuccessful()) {
                    db.detallePedidoDao().marcarComoSincronizado(dp.idDetalle);
                    Log.d("SYNC", "✅ Sincronizado ID: " + dp.idDetalle);
                } else {
                    int code = response.code();
                    if (code >= 400 && code < 500) {
                        // ERROR CRÍTICO: El servidor rechaza los datos (ej: producto inexistente)
                        db.detallePedidoDao().marcarComoErrorCritico(dp.idDetalle);
                        Log.e("SYNC", "❌ Error Crítico (4xx) en ID " + dp.idDetalle + ": " + code);
                    } else {
                        // ERROR DE SERVIDOR: El backend falló (5xx), reintentamos luego
                        huboErrorDeRed = true;
                        Log.w("SYNC", "⚠️ Error Servidor (5xx) en ID " + dp.idDetalle);
                    }
                }
            } catch (Exception e) {
                // FALLO DE RED: No hay internet o timeout
                huboErrorDeRed = true;
                Log.e("SYNC", "🌐 Fallo de conexión en ID: " + dp.idDetalle, e);
            }
        }

        // Solo reintentamos si hubo fallos de red o servidor (5xx)
        return huboErrorDeRed ? Result.retry() : Result.success();
    }
}