package com.nodo.tpv.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.google.gson.Gson;
import com.nodo.tpv.data.database.AppDatabase;
import com.nodo.tpv.data.dto.ClienteConSaldo;
import com.nodo.tpv.data.entities.ActividadOperativaLocal;
import com.nodo.tpv.data.entities.Cliente;
import com.nodo.tpv.data.sync.OperatividadSyncWorker;
import com.nodo.tpv.util.SessionManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ClienteViewModel extends AndroidViewModel {

    private final AppDatabase db;
    private final ExecutorService executorService = Executors.newFixedThreadPool(4);

    public ClienteViewModel(@NonNull Application application) {
        super(application);
        db = AppDatabase.getInstance(application);
    }

    public LiveData<List<ClienteConSaldo>> getClientesLiveData() {
        return db.clienteDao().obtenerTodosLosClientesLive();
    }

    public LiveData<List<ClienteConSaldo>> getClientesPorMesa(int idMesa) {
        return db.clienteDao().obtenerClientesPorMesaLive(idMesa);
    }

    public void guardarCliente(String alias, String tipo, int idMesa) {
        executorService.execute(() -> {
            // 1. Convertimos el String "INDIVIDUAL"/"GRUPO" al ID correspondiente para la llave foránea
            // Asumimos que 1 es INDIVIDUAL y 2 es GRUPO.
            int idTipo = (tipo != null && tipo.equalsIgnoreCase("GRUPO")) ? 2 : 1;

            // 2. Guardar localmente en Room usando los nuevos campos
            Cliente c = new Cliente();
            c.alias = alias;
            c.idTipoCliente = idTipo;
            c.idMesa = idMesa;
            c.fechaCreacionLong = System.currentTimeMillis();
            c.identificadorDevice = UUID.randomUUID().toString();
            db.clienteDao().insertar(c);

            // 🔥 3. GATILLO UNIVERSAL: Registramos "CLIENTE_NUEVO" en la Caja Negra
            try {
                ActividadOperativaLocal evento = new ActividadOperativaLocal();
                evento.eventoId = c.identificadorDevice; // Reusamos el UUID del cliente como ID del evento
                evento.tipoEvento = "CLIENTE_NUEVO"; // Estandarizado para que React lo atrape
                evento.fechaDispositivo = c.fechaCreacionLong;
                evento.estadoSync = "PENDIENTE";

                Map<String, Object> payload = new HashMap<>();
                payload.put("idMesa", idMesa);
                payload.put("nombreCliente", alias);
                // Opcional: Mandamos el texto al backend por si quiere saber el tipo, aunque acá usemos ID
                payload.put("tipoCliente", tipo);

                SessionManager session = new SessionManager(getApplication().getApplicationContext());
                if (session.obtenerUsuario() != null) {
                    payload.put("idUsuarioSlot", session.obtenerUsuario().idUsuario);
                }

                // Generamos el JSON de forma segura
                evento.detallesJson = new Gson().toJson(payload);

                // Insertamos en la Caja Negra local
                db.actividadOperativaLocalDao().insertar(evento);

                // 4. Despertamos al Worker de sincronización Inmediata
                Constraints constraints = new Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build();

                OneTimeWorkRequest syncRequest = new OneTimeWorkRequest.Builder(OperatividadSyncWorker.class)
                        .setConstraints(constraints)
                        .build();

                WorkManager.getInstance(getApplication().getApplicationContext())
                        .enqueueUniqueWork("SyncOperatividadInmediata", ExistingWorkPolicy.KEEP, syncRequest);

            } catch (Exception e) {
                android.util.Log.e("SYNC_CLIENTE", "Error al registrar cliente en caja negra", e);
            }
        });
    }

    public void eliminarCliente(Cliente cliente) {
        executorService.execute(() -> {
            db.clienteDao().eliminar(cliente);
        });
    }
}