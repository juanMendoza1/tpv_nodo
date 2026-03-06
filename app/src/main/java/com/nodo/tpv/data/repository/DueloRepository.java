package com.nodo.tpv.data.repository;

import android.app.Application;

import androidx.lifecycle.LiveData;

import com.nodo.tpv.data.dao.DetalleDueloTemporalIndDao;
import com.nodo.tpv.data.dao.DueloDao;
import com.nodo.tpv.data.dao.DueloTemporalIndDao;
import com.nodo.tpv.data.dao.PerfilDueloIndDao;
import com.nodo.tpv.data.database.AppDatabase;
import com.nodo.tpv.data.entities.Cliente;
import com.nodo.tpv.data.entities.DetalleDueloTemporalInd;
import com.nodo.tpv.data.entities.DueloTemporal;
import com.nodo.tpv.data.entities.DueloTemporalInd;
import com.nodo.tpv.data.entities.PerfilDueloInd;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DueloRepository {

    private final DueloDao dueloDao;
    private final DueloTemporalIndDao dueloIndDao;
    private final DetalleDueloTemporalIndDao detalleDueloIndDao;
    private final PerfilDueloIndDao perfilDueloIndDao;
    private final ExecutorService executorService;

    public DueloRepository(Application application) {
        AppDatabase db = AppDatabase.getInstance(application);
        this.dueloDao = db.dueloDao();
        this.dueloIndDao = db.dueloTemporalIndDao();
        this.detalleDueloIndDao = db.detalleDueloTemporalIndDao();
        this.perfilDueloIndDao = db.perfilDueloIndDao();

        // Un thread pool dedicado para operaciones de duelos
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
            if (onComplete != null) onComplete.run();
        });
    }

    public void finalizarDueloPool(int idMesa, Runnable onComplete) {
        executorService.execute(() -> {
            dueloDao.finalizarDueloPorMesa(idMesa);
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
            if (onComplete != null) onComplete.run();
        });
    }

    public void finalizarDueloIndividual(int idMesa, Runnable onComplete) {
        executorService.execute(() -> {
            dueloIndDao.finalizarDueloMesa(idMesa);
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
                if (onComplete != null) onComplete.run();
            }
        });
    }
}