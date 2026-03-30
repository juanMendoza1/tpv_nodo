package com.nodo.tpv.viewmodel;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.nodo.tpv.data.database.AppDatabase;
import com.nodo.tpv.data.dto.DetalleConNombre;
import com.nodo.tpv.data.dto.DetalleHistorialDuelo;
import com.nodo.tpv.data.entities.DetallePedido;
import com.nodo.tpv.data.entities.Producto;
import com.nodo.tpv.data.entities.VentaDetalleHistorial;
import com.nodo.tpv.data.entities.VentaHistorial;
import com.nodo.tpv.data.repository.PedidoRepository;
import com.nodo.tpv.data.sync.StockSyncWorker;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PedidoViewModel extends AndroidViewModel {

    private final AppDatabase db;
    private final PedidoRepository pedidoRepository;
    private final ExecutorService executorService = Executors.newFixedThreadPool(4);
    private final Handler mainThreadHandler = new Handler(Looper.getMainLooper());

    // Trigger para refrescar la UI
    private final MutableLiveData<Long> dbTrigger = new MutableLiveData<>(System.currentTimeMillis());

    // Evento para notificar a la UI cuando una venta o registro fue exitoso
    private final MutableLiveData<Boolean> _eventoVentaExitosa = new MutableLiveData<>();

    public interface OnDetallesCargadosListener { void onCargados(List<VentaDetalleHistorial> detalles); }

    public PedidoViewModel(@NonNull Application application) {
        super(application);
        db = AppDatabase.getInstance(application);
        pedidoRepository = new PedidoRepository(application);
    }

    // --- GETTERS DE ESTADO ---
    public LiveData<Long> getDbTrigger() { return dbTrigger; }
    public LiveData<Boolean> getEventoVentaExitosa() { return _eventoVentaExitosa; }
    public void resetEventoVenta() { _eventoVentaExitosa.setValue(false); }

    // --- GESTIÓN DE PEDIDOS E INSERCIONES ---

    public void insertarConsumoDirecto(int idCliente, Producto producto, int cantidad) {
        executorService.execute(() -> {
            int idMesa = db.clienteDao().obtenerMesaDelCliente(idCliente);
            String uuidDuelo = db.dueloDao().obtenerUuidDueloActivoPorMesa(idMesa);

            DetallePedido dp = new DetallePedido();
            dp.idCliente = idCliente;
            dp.idProducto = producto.idProducto;
            dp.idMesa = idMesa;
            dp.cantidad = cantidad;

            // CORRECCIÓN: Acceso directo a precioProducto
            dp.precioEnVenta = producto.precioProducto;

            dp.fechaLong = System.currentTimeMillis();
            dp.estado = "PENDIENTE";

            boolean clienteEnDuelo = db.dueloDao().verificarClienteEnDuelo(uuidDuelo, idCliente);

            if (clienteEnDuelo) {
                dp.esApuesta = true;
                dp.idDueloOrigen = uuidDuelo;
                dp.marcadorAlMomento = "Consumo Directo"; // Placeholder hasta que ArenaViewModel tome el control
            } else {
                dp.esApuesta = false;
            }

            db.detallePedidoDao().insertarDetalle(dp);
            mainThreadHandler.post(() -> dbTrigger.setValue(System.currentTimeMillis()));
        });
    }

    public void insertarConsumoDirectoEntregado(int idCliente, Producto producto, int cantidad) {
        executorService.execute(() -> {
            DetallePedido dp = new DetallePedido();
            dp.idCliente = idCliente;
            dp.idProducto = producto.idProducto;
            dp.cantidad = cantidad;

            // CORRECCIÓN: Acceso directo a precioProducto
            dp.precioEnVenta = producto.precioProducto;

            dp.estado = "ENTREGADO";
            dp.esApuesta = false;
            dp.fechaLong = System.currentTimeMillis();

            db.detallePedidoDao().insertarDetalle(dp);
            mainThreadHandler.post(() -> dbTrigger.setValue(System.currentTimeMillis()));
        });
    }

    // --- CIERRE DE CUENTA E HISTORIAL ---
    public void finalizarCuenta(int id, String alias, String metodo, String fotoBase64) {
        pedidoRepository.finalizarCuenta(id, alias, metodo, fotoBase64, () -> {
            mainThreadHandler.post(() -> dbTrigger.setValue(System.currentTimeMillis()));
        });
    }

    public void obtenerDetallesTicket(int idVenta, OnDetallesCargadosListener listener) {
        executorService.execute(() -> {
            List<VentaDetalleHistorial> detalles = db.detallePedidoDao().obtenerDetallesDeVentaSincrono(idVenta);
            mainThreadHandler.post(() -> { if (listener != null) listener.onCargados(detalles); });
        });
    }

    public LiveData<List<VentaHistorial>> obtenerTodoElHistorial() {
        return db.detallePedidoDao().obtenerTodoElHistorial();
    }

    public LiveData<List<DetalleConNombre>> obtenerDetalleCliente(int idCliente) {
        return db.detallePedidoDao().obtenerDetalleConNombres(idCliente);
    }

    // --- LOGÍSTICA Y DESPACHOS (BADGES) ---

    public LiveData<Integer> observarConteoPendientesMesa(int idMesa) {
        return pedidoRepository.observarConteoPendientesMesa(idMesa);
    }

    public LiveData<List<DetalleHistorialDuelo>> obtenerSoloPendientesMesa(int idMesa) {
        return pedidoRepository.obtenerSoloPendientesMesa(idMesa);
    }

    public void marcarComoEntregado(int idDetalle, int idUsuario, String loginOperativo) {
        pedidoRepository.marcarComoEntregadoLocal(idDetalle, idUsuario, () -> {
            dispararSincronizacion();
            mainThreadHandler.post(() -> dbTrigger.setValue(System.currentTimeMillis()));
        });
    }

    public void despacharTodoLaMesa(int idMesa, int idUsuario, String loginOperativo) {
        executorService.execute(() -> {
            List<DetallePedido> pendientes = db.detallePedidoDao().obtenerPendientesMesaSincrono(idMesa);
            if (pendientes == null || pendientes.isEmpty()) return;

            for (DetallePedido dp : pendientes) {
                db.detallePedidoDao().despacharPedidoLocal(dp.idDetalle, "ENTREGADO", idUsuario);
            }

            dispararSincronizacion();
            mainThreadHandler.post(() -> dbTrigger.setValue(System.currentTimeMillis()));
        });
    }

    public void eliminarDetallePendiente(int idDetalle) {
        pedidoRepository.cancelarDetallePendiente(idDetalle, () -> {
            mainThreadHandler.post(() -> dbTrigger.setValue(System.currentTimeMillis()));
        });
    }

    public void cancelarMunicionPendienteMesa(int idMesa) {
        pedidoRepository.cancelarMunicionPendienteMesa(idMesa, () -> {
            mainThreadHandler.post(() -> dbTrigger.setValue(System.currentTimeMillis()));
        });
    }

    private void dispararSincronizacion() {
        Constraints restricciones = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        OneTimeWorkRequest syncRequest = new OneTimeWorkRequest.Builder(StockSyncWorker.class)
                .setConstraints(restricciones)
                .build();

        WorkManager.getInstance(getApplication()).enqueue(syncRequest);
    }

    public void insertarMunicionDueloPendiente(int idMesa, Producto producto, int cantidad) {
        executorService.execute(() -> {
            // Buscamos el UUID del duelo activo en esta mesa (Pool o 3 Bandas)
            String uuidDuelo = db.dueloDao().obtenerUuidDueloActivoPorMesa(idMesa);
            if (uuidDuelo == null) {
                uuidDuelo = db.dueloDao().obtenerUuidDueloActivoIndPorMesa(idMesa);
            }

            final String finalUuid = uuidDuelo;

            pedidoRepository.insertarMunicionDueloPendiente(idMesa, producto, cantidad, finalUuid, () -> {
                mainThreadHandler.post(() -> dbTrigger.setValue(System.currentTimeMillis()));
            });
        });
    }

    /**
     * Inserta productos directos a la bolsa en la Arena Individual (3 Bandas).
     */
    public void insertarMunicionBolsaIndPendiente(int idMesa, Producto producto) {
        executorService.execute(() -> {
            String uuid = db.dueloDao().obtenerUuidDueloActivoIndPorMesa(idMesa);

            DetallePedido dp = new DetallePedido();
            dp.idProducto = producto.idProducto;
            dp.idMesa = idMesa;
            dp.idCliente = 0; // 0 = Identificador universal de "La Bolsa"
            dp.idDueloOrigen = uuid;

            // CORRECCIÓN: Acceso directo a precioProducto
            dp.precioEnVenta = producto.precioProducto;

            dp.cantidad = 1;
            dp.estado = "PENDIENTE";
            dp.esApuesta = true;
            dp.fechaLong = System.currentTimeMillis();

            db.detallePedidoDao().insertarDetalle(dp);
            mainThreadHandler.post(() -> dbTrigger.setValue(System.currentTimeMillis()));
        });
    }

    // --- CONSULTAS DE DEUDAS PARA LA UI ---

    public LiveData<List<DetalleHistorialDuelo>> obtenerDetalleDeudaRegistrada(int idMesa) {
        return db.detallePedidoDao().obtenerDeudaPorMesa(idMesa);
    }

    public LiveData<List<DetalleHistorialDuelo>> obtenerDeudaPorMesaInd(int idMesa) {
        return db.detallePedidoDao().obtenerDeudaPorMesaInd(idMesa);
    }


    public void marcarComoEntregadoACliente(int idDetalle, int idCliente, int idUsuario, String loginOp) {
        executorService.execute(() -> {
            // Aquí le decimos a la BD:
            // 1. Estado = ENTREGADO
            // 2. idCliente = el cliente que seleccionamos en el círculo holográfico
            // 3. esApuesta = 0 (false) -> ¡Esto saca el producto de la bolsa central!
            // 4. idUsuarioEntrega = el mesero que hizo la acción
            db.detallePedidoDao().despacharPedidoAClienteEspecifico(idDetalle, idCliente, idUsuario);
        });
    }

}