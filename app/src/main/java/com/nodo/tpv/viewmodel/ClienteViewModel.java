package com.nodo.tpv.viewmodel;

import android.app.Application;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import com.nodo.tpv.data.database.AppDatabase;
import com.nodo.tpv.data.dto.ClienteConSaldo;
import com.nodo.tpv.data.entities.Cliente;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ClienteViewModel extends AndroidViewModel {

    private final AppDatabase db;
    private final LiveData<List<ClienteConSaldo>> clientesActivos;
    // Executor para no bloquear el hilo principal (UI)
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    public ClienteViewModel(Application application) {
        super(application);
        db = AppDatabase.getInstance(application);
        clientesActivos = db.clienteDao().obtenerTodosLosClientesLive();
    }

    public LiveData<List<ClienteConSaldo>> getClientesActivos() {
        return clientesActivos;
    }

    public void guardarCliente(String alias, String tipoStr) {
        executorService.execute(() -> {
            int tipoId = tipoStr.equals("INDIVIDUAL") ? 1 : 2;
            Cliente nuevo = new Cliente();
            nuevo.alias = alias;
            nuevo.idTipoCliente = tipoId;
            nuevo.idMesa = 1; // Por ahora por defecto, luego lo pediremos

            db.clienteDao().insertar(nuevo);
        });
    }

}
