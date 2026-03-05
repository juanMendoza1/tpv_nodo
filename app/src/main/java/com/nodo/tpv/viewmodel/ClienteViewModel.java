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
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    public ClienteViewModel(Application application) {
        super(application);
        db = AppDatabase.getInstance(application);
    }

    // 🔥 NUEVO: Método que pide los clientes de una mesa específica
    public LiveData<List<ClienteConSaldo>> getClientesPorMesa(int idMesa) {
        return db.clienteDao().obtenerClientesPorMesaLive(idMesa);
    }

    // 🔥 MODIFICADO: Ahora exige saber en qué mesa guardar al cliente
    public void guardarCliente(String alias, String tipoStr, int idMesa) {
        executorService.execute(() -> {
            int tipoId = tipoStr.equals("INDIVIDUAL") ? 1 : 2;
            Cliente nuevo = new Cliente();
            nuevo.alias = alias;
            nuevo.idTipoCliente = tipoId;
            nuevo.idMesa = idMesa; // Se asigna a la mesa que pasamos por parámetro

            db.clienteDao().insertar(nuevo);
        });
    }
}