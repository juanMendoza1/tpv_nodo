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

    private final MutableLiveData<Long> dbTrigger = new MutableLiveData<>(System.currentTimeMillis());
    private final MutableLiveData<Boolean> _eventoVentaExitosa = new MutableLiveData<>();

    public interface OnDetallesCargadosListener { void onCargados(List<VentaDetalleHistorial> detalles); }

    public PedidoViewModel(@NonNull Application application) {
        super(application);
        db = AppDatabase.getInstance(application);
        pedidoRepository = new PedidoRepository(application);
    }

    public LiveData<Long> getDbTrigger() { return dbTrigger; }
    public LiveData<Boolean> getEventoVentaExitosa() { return _eventoVentaExitosa; }
    public void resetEventoVenta() { _eventoVentaExitosa.setValue(false); }

    // --- GESTIÓN DE PEDIDOS DESDE LISTA CLIENTES ---

    /**
     * 🔥 ACTUALIZADO: Registra un pedido ya entregado y lo manda al Backend.
     * Se usa cuando desde Lista Clientes se abre el catálogo y se confirma el pedido.
     */
    public void insertarConsumoDirectoEntregado(int idCliente, int idMesa, Producto producto, int cantidad) {
        // Delegamos al repositorio para que inserte en Room y cree el evento de Sincronización
        pedidoRepository.insertarConsumoDirectoEntregado(idCliente, idMesa, producto, cantidad, () -> {
            mainThreadHandler.post(() -> dbTrigger.setValue(System.currentTimeMillis()));
        });
    }

    /**
     * Inserta un consumo que queda PENDIENTE (esperando ser despachado).
     */
    public void insertarConsumoDirecto(int idCliente, Producto producto, int cantidad) {
        executorService.execute(() -> {
            int idMesa = db.clienteDao().obtenerMesaDelCliente(idCliente);
            String uuidDuelo = db.dueloDao().obtenerUuidDueloActivoPorMesa(idMesa);

            DetallePedido dp = new DetallePedido();
            dp.idCliente = idCliente;
            dp.idProducto = producto.idProducto;
            dp.idMesa = idMesa;
            dp.cantidad = cantidad;
            dp.precioEnVenta = producto.precioProducto;
            dp.fechaLong = System.currentTimeMillis();
            dp.estado = "PENDIENTE";

            boolean clienteEnDuelo = db.dueloDao().verificarClienteEnDuelo(uuidDuelo, idCliente);

            if (clienteEnDuelo) {
                dp.esApuesta = true;
                dp.idDueloOrigen = uuidDuelo;
                dp.marcadorAlMomento = "Consumo Directo";
            } else {
                dp.esApuesta = false;
            }

            db.detallePedidoDao().insertarDetalle(dp);

            // Aquí podrías agregar un dispararSincronizaciónOperatividad si quieres que
            // el backend sepa de pedidos pendientes.

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
            dispararSincronizacionStock();
            mainThreadHandler.post(() -> dbTrigger.setValue(System.currentTimeMillis()));
        });
    }

    public void despacharTodoLaMesa(int idMesa, int idUsuario, String loginOperativo) {
        executorService.execute(() -> {
            List<DetallePedido> pendientes = db.detallePedidoDao().obtenerPendientesMesaSincrono(idMesa);
            if (pendientes == null || pendientes.isEmpty()) return;

            // 🔥 Usamos el repositorio para generar un evento web por CADA producto
            for (DetallePedido dp : pendientes) {
                pedidoRepository.marcarComoEntregadoLocal(dp.idDetalle, idUsuario, null);
            }

            dispararSincronizacionStock();
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

    /**
     * Sincroniza el Stock real con el servidor central.
     */
    private void dispararSincronizacionStock() {
        Constraints restricciones = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        OneTimeWorkRequest syncRequest = new OneTimeWorkRequest.Builder(StockSyncWorker.class)
                .setConstraints(restricciones)
                .build();

        WorkManager.getInstance(getApplication()).enqueue(syncRequest);
    }

    // --- LÓGICA DE ARENA / DUELOS ---

    public void insertarMunicionDueloPendiente(int idMesa, Producto producto, int cantidad) {
        executorService.execute(() -> {
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

    public void insertarMunicionBolsaIndPendiente(int idMesa, Producto producto) {
        executorService.execute(() -> {
            String uuid = db.dueloDao().obtenerUuidDueloActivoIndPorMesa(idMesa);

            DetallePedido dp = new DetallePedido();
            dp.idProducto = producto.idProducto;
            dp.idMesa = idMesa;
            dp.idCliente = 0;
            dp.idDueloOrigen = uuid;
            dp.precioEnVenta = producto.precioProducto;
            dp.cantidad = 1;
            dp.estado = "PENDIENTE";
            dp.esApuesta = true;
            dp.fechaLong = System.currentTimeMillis();

            db.detallePedidoDao().insertarDetalle(dp);
            mainThreadHandler.post(() -> dbTrigger.setValue(System.currentTimeMillis()));
        });
    }

    public LiveData<List<DetalleHistorialDuelo>> obtenerDetalleDeudaRegistrada(int idMesa) {
        return db.detallePedidoDao().obtenerDeudaPorMesa(idMesa);
    }

    public LiveData<List<DetalleHistorialDuelo>> obtenerDeudaPorMesaInd(int idMesa) {
        return db.detallePedidoDao().obtenerDeudaPorMesaInd(idMesa);
    }

    public void marcarComoEntregadoACliente(int idDetalle, int idCliente, int idUsuario, String loginOp) {
        // 🔥 Ahora llamamos a nuestro Repositorio Inteligente en vez del DAO directo
        pedidoRepository.marcarComoEntregadoAClienteLocal(idDetalle, idCliente, idUsuario, () -> {
            dispararSincronizacionStock();
            mainThreadHandler.post(() -> dbTrigger.setValue(System.currentTimeMillis()));
        });
    }
}