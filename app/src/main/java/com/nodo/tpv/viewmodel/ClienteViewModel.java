package com.nodo.tpv.viewmodel;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.nodo.tpv.data.database.AppDatabase;
import com.nodo.tpv.data.dto.ClienteConSaldo;
import com.nodo.tpv.data.entities.ActividadOperativaLocal;
import com.nodo.tpv.data.entities.Cliente;
import com.nodo.tpv.data.sync.OperatividadSyncWorker;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ClienteViewModel extends AndroidViewModel {

    private final AppDatabase db;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    public ClienteViewModel(Application application) {
        super(application);
        db = AppDatabase.getInstance(application);
    }

    // 🔥 NUEVO: Método que pide los clientes de una mesa específica
    public LiveData<List<ClienteConSaldo>> getClientesPorMesa(int idMesa) {
        return db.clienteDao().obtenerClientesPorMesaLive(idMesa);
    }

    // 🔥 MODIFICADO: Ahora exige saber en qué mesa guardar al cliente y gatilla sincronización
    public void guardarCliente(String alias, String tipoStr, int idMesa) {
        executorService.execute(() -> {
            int tipoId = tipoStr.equals("INDIVIDUAL") ? 1 : 2;
            Cliente nuevo = new Cliente();
            nuevo.alias = alias;
            nuevo.idTipoCliente = tipoId;
            nuevo.idMesa = idMesa;

            // Guardamos el cliente en la base local
            long idGenerado = db.clienteDao().insertar(nuevo);

            // 🔥 GATILLO: Cliente Creado
            ActividadOperativaLocal pendiente = new ActividadOperativaLocal();
            pendiente.eventoId = java.util.UUID.randomUUID().toString();
            pendiente.tipoEvento = "CLIENTE_CREADO";
            pendiente.fechaDispositivo = System.currentTimeMillis();
            pendiente.estadoSync = "PENDIENTE";
            pendiente.detallesJson = "{ \"idClienteLocal\": " + idGenerado + ", \"alias\": \"" + alias + "\", \"idMesa\": " + idMesa + " }";

            db.actividadOperativaLocalDao().insertar(pendiente);
            dispararSincronizacion();
        });
    }

    private void dispararSincronizacion() {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        OneTimeWorkRequest syncRequest = new OneTimeWorkRequest.Builder(OperatividadSyncWorker.class)
                .setConstraints(constraints)
                .build();

        // Usamos getApplication() porque estamos en un AndroidViewModel
        WorkManager.getInstance(getApplication()).enqueueUniqueWork(
                "SyncOperatividadInmediata",
                ExistingWorkPolicy.KEEP,
                syncRequest
        );
    }
}