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
import com.nodo.tpv.data.dao.ClienteDao;
import com.nodo.tpv.data.dao.DetallePedidoDao;
import com.nodo.tpv.data.dao.DueloDao;
import com.nodo.tpv.data.dao.DueloTemporalIndDao;
import com.nodo.tpv.data.database.AppDatabase;
import com.nodo.tpv.data.dto.DetalleConNombre;
import com.nodo.tpv.data.dto.DetalleHistorialDuelo;
import com.nodo.tpv.data.entities.ActividadOperativaLocal;
import com.nodo.tpv.data.entities.DetallePedido;
import com.nodo.tpv.data.entities.Producto;
import com.nodo.tpv.data.entities.VentaDetalleHistorial;
import com.nodo.tpv.data.entities.VentaHistorial;
import com.nodo.tpv.data.sync.OperatividadSyncWorker;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PedidoRepository {

    private final DetallePedidoDao detallePedidoDao;
    private final DueloDao dueloDao;
    private final ClienteDao clienteDao;
    private final DueloTemporalIndDao dueloTemporalIndDao;
    private final ActividadOperativaLocalDao actividadOperativaLocalDao;
    private final ExecutorService executorService;

    // Guardamos el contexto a nivel global de la clase para el WorkManager
    private final Context context;

    public PedidoRepository(Application application) {
        AppDatabase db = AppDatabase.getInstance(application);
        this.detallePedidoDao = db.detallePedidoDao();
        this.dueloDao = db.dueloDao();
        this.clienteDao = db.clienteDao();
        this.dueloTemporalIndDao = db.dueloTemporalIndDao();
        this.actividadOperativaLocalDao = db.actividadOperativaLocalDao();

        this.context = application.getApplicationContext();

        // Hilo dedicado a las transacciones de ventas y pedidos
        this.executorService = Executors.newFixedThreadPool(4);
    }

    // ==========================================
    // LÓGICA DE BADGE Y PENDIENTES (UI EN TIEMPO REAL)
    // ==========================================

    public LiveData<Integer> observarConteoPendientesMesa(int idMesa) {
        return detallePedidoDao.observarConteoPendientesMesa(idMesa);
    }

    public LiveData<List<DetalleHistorialDuelo>> obtenerSoloPendientesMesa(int idMesa) {
        return detallePedidoDao.obtenerSoloPendientesMesa(idMesa);
    }

    // ==========================================
    // LÓGICA DE LA "BOLSA" DE MUNICIÓN (DUELOS)
    // ==========================================

    public void insertarMunicionDueloPendiente(int idMesa, Producto producto, int cantidad, String uuidEnMemoria, Runnable onComplete) {
        executorService.execute(() -> {
            String uuid = uuidEnMemoria;

            if (uuid == null) uuid = dueloTemporalIndDao.obtenerIdDueloPorMesaSincrono(idMesa);
            if (uuid == null) uuid = dueloDao.obtenerUuidDueloActivoPorMesa(idMesa);

            if (uuid != null) {
                for (int i = 0; i < cantidad; i++) {
                    DetallePedido dp = new DetallePedido();
                    dp.idProducto = producto.idProducto;
                    dp.idMesa = idMesa;
                    dp.idDueloOrigen = uuid;
                    dp.precioEnVenta = producto.getPrecioProducto();
                    dp.cantidad = 1;
                    dp.idCliente = 0; // 0 = La Bolsa
                    dp.estado = "PENDIENTE";
                    dp.esApuesta = true;
                    dp.fechaLong = System.currentTimeMillis();
                    detallePedidoDao.insertarDetalle(dp);
                }

                // 🔥 GATILLO: Registrar MUNICIÓN_AGREGADA
                ActividadOperativaLocal pendiente = new ActividadOperativaLocal();
                pendiente.eventoId = java.util.UUID.randomUUID().toString();
                pendiente.tipoEvento = "MUNICION_AGREGADA";
                pendiente.fechaDispositivo = System.currentTimeMillis();
                pendiente.estadoSync = "PENDIENTE";
                pendiente.detallesJson = "{ \"idMesa\": " + idMesa + ", \"idProducto\": " + producto.idProducto + ", \"cantidad\": " + cantidad + " }";
                actividadOperativaLocalDao.insertar(pendiente);
                dispararSincronizacion();
            }
            if (onComplete != null) onComplete.run();
        });
    }

    public void marcarComoEntregadoLocal(int idDetalle, int idUsuario, Runnable onComplete) {
        executorService.execute(() -> {
            detallePedidoDao.despacharPedidoLocal(idDetalle, "ENTREGADO", idUsuario);

            // 🔥 GATILLO: Registrar DESPACHO INDIVIDUAL
            ActividadOperativaLocal pendiente = new ActividadOperativaLocal();
            pendiente.eventoId = java.util.UUID.randomUUID().toString();
            pendiente.tipoEvento = "DESPACHO";
            pendiente.fechaDispositivo = System.currentTimeMillis();
            pendiente.estadoSync = "PENDIENTE";
            pendiente.detallesJson = "{ \"idDetalle\": " + idDetalle + ", \"idUsuario\": " + idUsuario + " }";
            actividadOperativaLocalDao.insertar(pendiente);
            dispararSincronizacion();

            if (onComplete != null) onComplete.run();
        });
    }

    public void despacharTodoLaMesa(int idMesa, int idUsuario, Runnable onComplete) {
        executorService.execute(() -> {
            List<DetallePedido> pendientes = detallePedidoDao.obtenerPendientesMesaSincrono(idMesa);
            if (pendientes != null && !pendientes.isEmpty()) {
                for (DetallePedido dp : pendientes) {
                    detallePedidoDao.despacharPedidoLocal(dp.idDetalle, "ENTREGADO", idUsuario);
                }

                // 🔥 GATILLO: Registrar DESPACHO MASIVO
                ActividadOperativaLocal pendiente = new ActividadOperativaLocal();
                pendiente.eventoId = java.util.UUID.randomUUID().toString();
                pendiente.tipoEvento = "DESPACHO_MESA";
                pendiente.fechaDispositivo = System.currentTimeMillis();
                pendiente.estadoSync = "PENDIENTE";
                pendiente.detallesJson = "{ \"idMesa\": " + idMesa + ", \"idUsuario\": " + idUsuario + ", \"cantidadDespachada\": " + pendientes.size() + " }";
                actividadOperativaLocalDao.insertar(pendiente);
                dispararSincronizacion();
            }
            if (onComplete != null) onComplete.run();
        });
    }

    public void cancelarDetallePendiente(int idDetalle, Runnable onComplete) {
        executorService.execute(() -> {
            detallePedidoDao.cancelarDetallePorId(idDetalle);

            // 🔥 GATILLO: Registrar CANCELACIÓN INDIVIDUAL
            ActividadOperativaLocal pendiente = new ActividadOperativaLocal();
            pendiente.eventoId = java.util.UUID.randomUUID().toString();
            pendiente.tipoEvento = "CANCELACION_DETALLE";
            pendiente.fechaDispositivo = System.currentTimeMillis();
            pendiente.estadoSync = "PENDIENTE";
            pendiente.detallesJson = "{ \"idDetalle\": " + idDetalle + " }";
            actividadOperativaLocalDao.insertar(pendiente);
            dispararSincronizacion();

            if (onComplete != null) onComplete.run();
        });
    }

    public void cancelarMunicionPendienteMesa(int idMesa, Runnable onComplete) {
        executorService.execute(() -> {
            detallePedidoDao.cancelarTodosLosPendientesMesa(idMesa);

            // 🔥 GATILLO: Registrar CANCELACIÓN DE MESA COMPLETA
            ActividadOperativaLocal pendiente = new ActividadOperativaLocal();
            pendiente.eventoId = java.util.UUID.randomUUID().toString();
            pendiente.tipoEvento = "CANCELACION_MESA";
            pendiente.fechaDispositivo = System.currentTimeMillis();
            pendiente.estadoSync = "PENDIENTE";
            pendiente.detallesJson = "{ \"idMesa\": " + idMesa + " }";
            actividadOperativaLocalDao.insertar(pendiente);
            dispararSincronizacion();

            if (onComplete != null) onComplete.run();
        });
    }

    // ==========================================
    // CONSUMO DIRECTO Y CIERRE DE CUENTA
    // ==========================================

    public void insertarConsumoDirectoEntregado(int idCliente, int idMesa, Producto producto, int cantidad, Runnable onComplete) {
        executorService.execute(() -> {
            DetallePedido dp = new DetallePedido();
            dp.idCliente = idCliente;
            dp.idMesa = idMesa; // 🔥 Agregamos la mesa
            dp.idProducto = producto.idProducto;
            dp.cantidad = cantidad;
            dp.precioEnVenta = producto.getPrecioProducto();
            dp.estado = "ENTREGADO";
            dp.esApuesta = false;
            dp.fechaLong = System.currentTimeMillis();
            detallePedidoDao.insertarDetalle(dp);

            // 🔥 GATILLO UNIVERSAL: Registro de Pedido Directo
            ActividadOperativaLocal pendiente = new ActividadOperativaLocal();
            pendiente.eventoId = java.util.UUID.randomUUID().toString();
            pendiente.tipoEvento = "PEDIDO_DIRECTO";
            pendiente.fechaDispositivo = System.currentTimeMillis();
            pendiente.estadoSync = "PENDIENTE";

            // JSON súper detallado para el backend
            pendiente.detallesJson = String.format(Locale.US,
                    "{ \"idCliente\": %d, \"idMesa\": %d, \"idProducto\": %d, \"nombre\": \"%s\", \"cantidad\": %d, \"precio\": %s }",
                    idCliente, idMesa, producto.idProducto, producto.nombreProducto, cantidad, producto.precioProducto.toString());

            actividadOperativaLocalDao.insertar(pendiente);
            dispararSincronizacion();

            if (onComplete != null) onComplete.run();
        });
    }

    public void finalizarCuenta(int idCliente, String alias, String metodo, String fotoBase64, Runnable onComplete) {
        executorService.execute(() -> {
            BigDecimal total = detallePedidoDao.obtenerTotalDirecto(idCliente);
            List<DetalleConNombre> consumos = detallePedidoDao.obtenerDetalleConNombresSincrono(idCliente);

            if (total != null && total.compareTo(BigDecimal.ZERO) > 0 && consumos != null) {
                // A. Venta Padre
                VentaHistorial vH = new VentaHistorial();
                vH.idCliente = idCliente;
                vH.nombreCliente = alias;
                vH.montoTotal = total;
                vH.metodoPago = metodo;
                vH.fechaLong = System.currentTimeMillis();
                vH.estado = "PENDIENTE";
                vH.fotoComprobante = fotoBase64;
                long idVentaPadre = detallePedidoDao.insertarVentaHistorial(vH);

                // B. Migrar detalles
                List<VentaDetalleHistorial> listaDetallesHistorial = new ArrayList<>();
                for (DetalleConNombre item : consumos) {
                    VentaDetalleHistorial vDet = new VentaDetalleHistorial();
                    vDet.idVentaPadre = (int) idVentaPadre;
                    vDet.nombreProducto = item.nombreProducto;
                    vDet.cantidad = item.detallePedido.cantidad;
                    vDet.precioUnitario = item.detallePedido.precioEnVenta;
                    vDet.esApuesta = item.detallePedido.esApuesta;
                    listaDetallesHistorial.add(vDet);
                }
                detallePedidoDao.insertarDetallesHistorial(listaDetallesHistorial);

                // C. Limpieza
                detallePedidoDao.borrarCuentaCliente(idCliente);
                clienteDao.eliminarPorId(idCliente);

                // 🔥 GATILLO: CIERRE_CUENTA
                ActividadOperativaLocal pendiente = new ActividadOperativaLocal();
                pendiente.eventoId = java.util.UUID.randomUUID().toString();
                pendiente.tipoEvento = "CIERRE_CUENTA";
                pendiente.fechaDispositivo = System.currentTimeMillis();
                pendiente.estadoSync = "PENDIENTE";
                pendiente.detallesJson = "{ \"idCliente\": " + idCliente + ", \"metodoPago\": \"" + metodo + "\", \"total\": " + total + " }";
                actividadOperativaLocalDao.insertar(pendiente);
                dispararSincronizacion();

                if (onComplete != null) onComplete.run();
            }
        });
    }

    // ==========================================
    // GETTERS PARA UI
    // ==========================================

    public LiveData<List<VentaHistorial>> obtenerTodoElHistorial() {
        return detallePedidoDao.obtenerTodoElHistorial();
    }

    private void dispararSincronizacion() {
        // 1. Configuramos que SÓLO se ejecute si hay conexión a internet
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        // 2. Creamos la petición de trabajo de una sola vez
        OneTimeWorkRequest syncRequest = new OneTimeWorkRequest.Builder(OperatividadSyncWorker.class)
                .setConstraints(constraints)
                .build();

        // 3. Lo encolamos de forma ÚNICA.
        WorkManager.getInstance(this.context).enqueueUniqueWork(
                "SyncOperatividadInmediata",
                ExistingWorkPolicy.KEEP,
                syncRequest
        );
    }
}