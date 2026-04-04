package com.nodo.tpv.data.sync;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.nodo.tpv.data.api.RetrofitClient;
import com.nodo.tpv.data.dao.ActividadOperativaLocalDao;
import com.nodo.tpv.data.database.AppDatabase;
import com.nodo.tpv.data.dto.EventoOperativoDTO;
import com.nodo.tpv.data.dto.SincronizacionPaqueteDTO;
import com.nodo.tpv.data.entities.ActividadOperativaLocal;
import com.nodo.tpv.util.SessionManager;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import okhttp3.ResponseBody;
import retrofit2.Response;

public class OperatividadSyncWorker extends Worker {

    private static final String TAG = "OperatividadSyncWorker";
    private final ActividadOperativaLocalDao dao;
    private final SessionManager sessionManager;
    private final Gson gson;

    public OperatividadSyncWorker(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
        this.dao = AppDatabase.getInstance(context).actividadOperativaLocalDao();
        this.sessionManager = new SessionManager(context);
        this.gson = new Gson();
    }

    @NonNull
    @Override
    public Result doWork() {
        Log.d(TAG, "Iniciando sincronización de operatividad...");

        // 1. Obtener eventos pendientes
        List<ActividadOperativaLocal> pendientes = dao.obtenerPendientesSincrono();

        if (pendientes == null || pendientes.isEmpty()) {
            Log.d(TAG, "No hay eventos pendientes por sincronizar.");
            return Result.success();
        }

        // 2. Obtener datos de sesión (UUID de la tablet y Empresa)
        String terminalUuid = sessionManager.getTerminalUuid(); // Asegúrate de tener este método en SessionManager
        long empresaId = sessionManager.getEmpresaId(); // Asegúrate de tener este método en SessionManager

        Log.d(TAG, "Revisando sesión -> UUID: " + terminalUuid + " | EmpresaID: " + empresaId);

        // FIX: Cambiamos "empresaId == 0" por "empresaId <= 0" porque tu SessionManager devuelve -1
        if (terminalUuid == null || terminalUuid.isEmpty() || empresaId <= 0) {
            Log.e(TAG, "Faltan datos de sesión. No se puede sincronizar.");

            // Si no hay sesión, no deberíamos tener basura pendiente de enviar, así que podemos borrarla
            // (Opcional, pero ayuda a que el Worker no se quede atascado fallando por siempre)
            return Result.failure();
        }

        // 3. Empacar los eventos en el DTO
        SincronizacionPaqueteDTO paquete = new SincronizacionPaqueteDTO();
        paquete.terminalUuid = terminalUuid;
        paquete.empresaId = empresaId;
        paquete.eventos = new ArrayList<>();

        Type mapType = new TypeToken<Map<String, Object>>(){}.getType();

        for (ActividadOperativaLocal local : pendientes) {
            EventoOperativoDTO evento = new EventoOperativoDTO();
            evento.eventoId = local.eventoId;
            evento.tipoEvento = local.tipoEvento;
            evento.fechaDispositivo = local.fechaDispositivo;

            // Convertimos el JSON String guardado en Room de vuelta a un Map para Retrofit
            if (local.detallesJson != null && !local.detallesJson.isEmpty()) {
                evento.data = gson.fromJson(local.detallesJson, mapType);
            }

            paquete.eventos.add(evento);
        }

        // 4. Enviar al Backend (Llamada Síncrona porque estamos en un Worker en segundo plano)
        try {
            Response<ResponseBody> response = RetrofitClient.getInterface(getApplicationContext())
                    .sincronizarOperatividad(paquete)
                    .execute();

            if (response.isSuccessful()) {
                // 5. ¡Éxito! El backend nos confirmó. Borramos los eventos de la tablet.
                Log.d(TAG, "Sincronización exitosa. Borrando " + pendientes.size() + " registros locales.");
                for (ActividadOperativaLocal local : pendientes) {
                    dao.eliminarPorId(local.eventoId);
                }
                return Result.success();
            } else {
                Log.e(TAG, "Error del servidor: " + response.code());
                return Result.retry(); // Le decimos a WorkManager que lo intente más tarde
            }

        } catch (Exception e) {
            Log.e(TAG, "Error de red al sincronizar: " + e.getMessage());
            return Result.retry(); // Si no hay internet, falla y WorkManager lo reintentará
        }
    }
}