package com.nodo.tpv.data.repository;

import android.app.Application;
import android.content.Context;

import androidx.lifecycle.LiveData;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.nodo.tpv.data.dao.ActividadOperativaLocalDao;
import com.nodo.tpv.data.dao.DetalleDueloTemporalIndDao;
import com.nodo.tpv.data.dao.DueloDao;
import com.nodo.tpv.data.dao.DueloTemporalIndDao;
import com.nodo.tpv.data.dao.PerfilDueloIndDao;
import com.nodo.tpv.data.database.AppDatabase;
import com.nodo.tpv.data.entities.ActividadOperativaLocal;
import com.nodo.tpv.data.entities.Cliente;
import com.nodo.tpv.data.entities.DetalleDueloTemporalInd;
import com.nodo.tpv.data.entities.DueloTemporal;
import com.nodo.tpv.data.entities.DueloTemporalInd;
import com.nodo.tpv.data.entities.PerfilDueloInd;
import com.nodo.tpv.data.sync.OperatividadSyncWorker;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DueloRepository {

    private final DueloDao dueloDao;
    private final DueloTemporalIndDao dueloIndDao;
    private final DetalleDueloTemporalIndDao detalleDueloIndDao;
    private final PerfilDueloIndDao perfilDueloIndDao;
    private final ActividadOperativaLocalDao actividadOperativaLocalDao;
    private final ExecutorService executorService;
    private final Context context;

    public DueloRepository(Application application) {
        AppDatabase db = AppDatabase.getInstance(application);
        this.dueloDao = db.dueloDao();
        this.dueloIndDao = db.dueloTemporalIndDao();
        this.detalleDueloIndDao = db.detalleDueloTemporalIndDao();
        this.perfilDueloIndDao = db.perfilDueloIndDao();
        this.actividadOperativaLocalDao = db.actividadOperativaLocalDao();
        this.context = application.getApplicationContext();

        this.executorService = Executors.newFixedThreadPool(4);
    }

    // ==========================================
    // MÉTODOS PARA DUELO POOL (EQUIPOS)
    // ==========================================

    public String obtenerUuidDueloActivoPorMesaSincrono(int idMesa) {
        return dueloDao.obtenerUuidDueloActivoPorMesa(idMesa);
    }

    public void prepararDueloPoolMultiequipo(String uuidDuelo, Map<Integer, Integer> seleccion, int idMesa, Runnable onComplete) {
        executorService.execute(() -> {
            dueloDao.borrarDueloFallidoPorMesa(idMesa);

            for (Map.Entry<Integer, Integer> entry : seleccion.entrySet()) {
                int idCliente = entry.getKey();
                int colorAsignado = entry.getValue();

                DueloTemporal dt = new DueloTemporal(
                        uuidDuelo, colorAsignado, idCliente, "ACTIVO", idMesa
                );
                dueloDao.insertarParticipante(dt);
            }

            // 🔥 GATILLO: Inicio de Duelo Pool
            ActividadOperativaLocal pendiente = new ActividadOperativaLocal();
            pendiente.eventoId = java.util.UUID.randomUUID().toString();
            pendiente.tipoEvento = "DUELO_POOL_INICIADO";
            pendiente.fechaDispositivo = System.currentTimeMillis();
            pendiente.estadoSync = "PENDIENTE";
            pendiente.detallesJson = "{ \"idMesa\": " + idMesa + ", \"uuidDuelo\": \"" + uuidDuelo + "\", \"participantes\": " + seleccion.size() + " }";
            actividadOperativaLocalDao.insertar(pendiente);
            dispararSincronizacion();

            if (onComplete != null) onComplete.run();
        });
    }

    public void finalizarDueloPool(int idMesa, Runnable onComplete) {
        executorService.execute(() -> {
            dueloDao.finalizarDueloPorMesa(idMesa);

            // 🔥 GATILLO: Fin de Duelo Pool
            ActividadOperativaLocal pendiente = new ActividadOperativaLocal();
            pendiente.eventoId = java.util.UUID.randomUUID().toString();
            pendiente.tipoEvento = "DUELO_POOL_FINALIZADO";
            pendiente.fechaDispositivo = System.currentTimeMillis();
            pendiente.estadoSync = "PENDIENTE";
            pendiente.detallesJson = "{ \"idMesa\": " + idMesa + " }";
            actividadOperativaLocalDao.insertar(pendiente);
            dispararSincronizacion();

            if (onComplete != null) onComplete.run();
        });
    }

    // ==========================================
    // MÉTODOS PARA DUELO INDIVIDUAL (3 BANDAS)
    // ==========================================

    public String obtenerUuidDueloActivoIndPorMesaSincrono(int idMesa) {
        return dueloDao.obtenerUuidDueloActivoIndPorMesa(idMesa);
    }

    public LiveData<PerfilDueloInd> obtenerPerfilPorMesa(int idMesa) {
        return perfilDueloIndDao.obtenerPerfilPorMesa(idMesa);
    }

    public void actualizarPerfilDueloInd(int idMesa, int puntos, String nivel, Runnable onComplete) {
        executorService.execute(() -> {
            PerfilDueloInd nuevoPerfil = new PerfilDueloInd(idMesa, puntos, nivel, "PERDEDORES");
            perfilDueloIndDao.insertarOActualizar(nuevoPerfil);
            if (onComplete != null) onComplete.run();
        });
    }

    public void iniciarDueloIndPersistente(String uuidDuelo, int idMesa, List<Cliente> clientes, Runnable onComplete) {
        executorService.execute(() -> {
            for (Cliente c : clientes) {
                DueloTemporalInd existente = dueloIndDao.obtenerEstadoCliente(idMesa, c.idCliente);
                if (existente == null) {
                    DueloTemporalInd nuevo = new DueloTemporalInd(
                            uuidDuelo, idMesa, c.idCliente, 20, "PERDEDORES"
                    );
                    nuevo.timestampInicio = System.currentTimeMillis();
                    dueloIndDao.insertarOActualizar(nuevo);
                }
            }

            // 🔥 GATILLO: Inicio Duelo Individual (3 Bandas)
            ActividadOperativaLocal pendiente = new ActividadOperativaLocal();
            pendiente.eventoId = java.util.UUID.randomUUID().toString();
            pendiente.tipoEvento = "DUELO_IND_INICIADO";
            pendiente.fechaDispositivo = System.currentTimeMillis();
            pendiente.estadoSync = "PENDIENTE";
            pendiente.detallesJson = "{ \"idMesa\": " + idMesa + ", \"uuidDuelo\": \"" + uuidDuelo + "\", \"cantidadJugadores\": " + clientes.size() + " }";
            actividadOperativaLocalDao.insertar(pendiente);
            dispararSincronizacion();

            if (onComplete != null) onComplete.run();
        });
    }

    public void finalizarDueloIndividual(int idMesa, Runnable onComplete) {
        executorService.execute(() -> {
            dueloIndDao.finalizarDueloMesa(idMesa);

            // 🔥 GATILLO: Fin de Duelo Individual
            ActividadOperativaLocal pendiente = new ActividadOperativaLocal();
            pendiente.eventoId = java.util.UUID.randomUUID().toString();
            pendiente.tipoEvento = "DUELO_IND_FINALIZADO";
            pendiente.fechaDispositivo = System.currentTimeMillis();
            pendiente.estadoSync = "PENDIENTE";
            pendiente.detallesJson = "{ \"idMesa\": " + idMesa + " }";
            actividadOperativaLocalDao.insertar(pendiente);
            dispararSincronizacion();

            if (onComplete != null) onComplete.run();
        });
    }

    // ==========================================
    // CONFIGURACIÓN Y REGLAS DE DUELO
    // ==========================================

    public void actualizarReglaDuelo(String uuidDueloActual, String nuevaRegla, Runnable onComplete) {
        executorService.execute(() -> {
            if (uuidDueloActual != null) {
                dueloDao.actualizarReglaCobroDuelo(uuidDueloActual, nuevaRegla);

                // 🔥 GATILLO: Cambio de regla
                ActividadOperativaLocal pendiente = new ActividadOperativaLocal();
                pendiente.eventoId = java.util.UUID.randomUUID().toString();
                pendiente.tipoEvento = "REGLA_DUELO_ACTUALIZADA";
                pendiente.fechaDispositivo = System.currentTimeMillis();
                pendiente.estadoSync = "PENDIENTE";
                pendiente.detallesJson = "{ \"uuidDuelo\": \"" + uuidDueloActual + "\", \"nuevaRegla\": \"" + nuevaRegla + "\" }";
                actividadOperativaLocalDao.insertar(pendiente);
                dispararSincronizacion();

                if (onComplete != null) onComplete.run();
            }
        });
    }

    private void dispararSincronizacion() {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        OneTimeWorkRequest syncRequest = new OneTimeWorkRequest.Builder(OperatividadSyncWorker.class)
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(this.context).enqueueUniqueWork(
                "SyncOperatividadInmediata",
                ExistingWorkPolicy.KEEP,
                syncRequest
        );
    }
}