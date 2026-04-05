package com.nodo.tpv.data.repository;

import android.app.Application;
import android.content.Context;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.google.gson.Gson;
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
import com.nodo.tpv.util.SessionManager;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PedidoRepository {

    private final DetallePedidoDao detallePedidoDao;
    private final DueloDao dueloDao;
    private final ClienteDao clienteDao;
    private final DueloTemporalIndDao dueloTemporalIndDao;
    private final ActividadOperativaLocalDao actividadOperativaLocalDao;
    private final ExecutorService executorService;
    private final Context context;
    private final Gson gson;

    public PedidoRepository(Application application) {
        AppDatabase db = AppDatabase.getInstance(application);
        this.detallePedidoDao = db.detallePedidoDao();
        this.dueloDao = db.dueloDao();
        this.clienteDao = db.clienteDao();
        this.dueloTemporalIndDao = db.dueloTemporalIndDao();
        this.actividadOperativaLocalDao = db.actividadOperativaLocalDao();
        this.context = application.getApplicationContext();
        this.executorService = Executors.newFixedThreadPool(4);
        this.gson = new Gson();
    }

    // ==========================================
    // LÓGICA DE BADGE Y PENDIENTES
    // ==========================================

    public LiveData<Integer> observarConteoPendientesMesa(int idMesa) {
        return detallePedidoDao.observarConteoPendientesMesa(idMesa);
    }

    public LiveData<List<DetalleHistorialDuelo>> obtenerSoloPendientesMesa(int idMesa) {
        return detallePedidoDao.obtenerSoloPendientesMesa(idMesa);
    }

    // ==========================================
    // LÓGICA DE LA "BOLSA" (ARENA / DUELOS)
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
                    dp.idCliente = 0; // Bolsa
                    dp.estado = "PENDIENTE";
                    dp.esApuesta = true;
                    dp.fechaLong = System.currentTimeMillis();
                    detallePedidoDao.insertarDetalle(dp);
                }

                // 🔥 REGISTRO ESTÁNDAR: "DESPACHO_MESA" para que React lo vea al instante
                ActividadOperativaLocal evento = new ActividadOperativaLocal();
                evento.eventoId = UUID.randomUUID().toString();
                evento.tipoEvento = "DESPACHO_MESA";
                evento.fechaDispositivo = System.currentTimeMillis();
                evento.estadoSync = "PENDIENTE";

                Map<String, Object> payload = new HashMap<>();
                payload.put("idMesa", idMesa);
                payload.put("uuidDuelo", uuid);

                List<Map<String, Object>> prodList = new ArrayList<>();
                Map<String, Object> pMap = new HashMap<>();
                pMap.put("nombre", producto.nombreProducto);
                pMap.put("precio", producto.getPrecioProducto());
                pMap.put("cantidad", cantidad);
                prodList.add(pMap);
                payload.put("productos", prodList);

                evento.detallesJson = gson.toJson(payload);
                actividadOperativaLocalDao.insertar(evento);
                dispararSincronizacion();
            }
            if (onComplete != null) onComplete.run();
        });
    }

    public void marcarComoEntregadoLocal(int idDetalle, int idUsuario, Runnable onComplete) {
        executorService.execute(() -> {
            detallePedidoDao.despacharPedidoLocal(idDetalle, "ENTREGADO", idUsuario);

            ActividadOperativaLocal evento = new ActividadOperativaLocal();
            evento.eventoId = UUID.randomUUID().toString();
            evento.tipoEvento = "DESPACHO";
            evento.fechaDispositivo = System.currentTimeMillis();
            evento.estadoSync = "PENDIENTE";

            Map<String, Object> payload = new HashMap<>();
            payload.put("idDetalle", idDetalle);
            payload.put("idUsuarioSlot", idUsuario);
            evento.detallesJson = gson.toJson(payload);

            actividadOperativaLocalDao.insertar(evento);
            dispararSincronizacion();

            if (onComplete != null) onComplete.run();
        });
    }

    // ==========================================
    // CONSUMO DIRECTO (LISTA CLIENTES)
    // ==========================================

    public void insertarConsumoDirectoEntregado(int idCliente, int idMesa, Producto producto, int cantidad, Runnable onComplete) {
        executorService.execute(() -> {
            DetallePedido dp = new DetallePedido();
            dp.idCliente = idCliente;
            dp.idMesa = idMesa;
            dp.idProducto = producto.idProducto;
            dp.cantidad = cantidad;
            dp.precioEnVenta = producto.getPrecioProducto();
            dp.estado = "ENTREGADO";
            dp.esApuesta = false;
            dp.fechaLong = System.currentTimeMillis();
            detallePedidoDao.insertarDetalle(dp);

            // 🔥 REGISTRO ESTÁNDAR: "DESPACHO_MESA" para evitar duplicidad y asegurar lectura en React
            ActividadOperativaLocal evento = new ActividadOperativaLocal();
            evento.eventoId = "VENTA-" + System.currentTimeMillis() + "-" + idCliente;
            evento.tipoEvento = "DESPACHO_MESA";
            evento.fechaDispositivo = System.currentTimeMillis();
            evento.estadoSync = "PENDIENTE";

            Map<String, Object> payload = new HashMap<>();
            payload.put("idMesa", idMesa);
            payload.put("idCliente", idCliente);

            SessionManager session = new SessionManager(context);
            if (session.obtenerUsuario() != null) {
                payload.put("idUsuarioSlot", session.obtenerUsuario().idUsuario);
            }

            List<Map<String, Object>> prodList = new ArrayList<>();
            Map<String, Object> pMap = new HashMap<>();
            pMap.put("nombre", producto.nombreProducto);
            pMap.put("precio", producto.getPrecioProducto());
            pMap.put("cantidad", cantidad);
            prodList.add(pMap);
            payload.put("productos", prodList);

            evento.detallesJson = gson.toJson(payload);
            actividadOperativaLocalDao.insertar(evento);
            dispararSincronizacion();

            if (onComplete != null) onComplete.run();
        });
    }

    public void finalizarCuenta(int idCliente, String alias, String metodo, String fotoBase64, Runnable onComplete) {
        executorService.execute(() -> {
            BigDecimal total = detallePedidoDao.obtenerTotalDirecto(idCliente);
            List<DetalleConNombre> consumos = detallePedidoDao.obtenerDetalleConNombresSincrono(idCliente);

            if (total != null && total.compareTo(BigDecimal.ZERO) > 0 && consumos != null) {
                VentaHistorial vH = new VentaHistorial();
                vH.idCliente = idCliente;
                vH.nombreCliente = alias;
                vH.montoTotal = total;
                vH.metodoPago = metodo;
                vH.fechaLong = System.currentTimeMillis();
                vH.estado = "PENDIENTE";
                vH.fotoComprobante = fotoBase64;
                long idVentaPadre = detallePedidoDao.insertarVentaHistorial(vH);

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

                detallePedidoDao.borrarCuentaCliente(idCliente);
                clienteDao.eliminarPorId(idCliente);

                ActividadOperativaLocal evento = new ActividadOperativaLocal();
                evento.eventoId = UUID.randomUUID().toString();
                evento.tipoEvento = "CIERRE_CUENTA";
                evento.fechaDispositivo = System.currentTimeMillis();
                evento.estadoSync = "PENDIENTE";

                Map<String, Object> payload = new HashMap<>();
                payload.put("idCliente", idCliente);
                payload.put("total", total);
                payload.put("metodoPago", metodo);
                evento.detallesJson = gson.toJson(payload);

                actividadOperativaLocalDao.insertar(evento);
                dispararSincronizacion();

                if (onComplete != null) onComplete.run();
            }
        });
    }

    public void cancelarDetallePendiente(int idDetalle, Runnable onComplete) {
        executorService.execute(() -> {
            detallePedidoDao.cancelarDetallePorId(idDetalle);
            ActividadOperativaLocal evento = new ActividadOperativaLocal();
            evento.eventoId = UUID.randomUUID().toString();
            evento.tipoEvento = "CANCELACION_DETALLE";
            evento.fechaDispositivo = System.currentTimeMillis();
            evento.estadoSync = "PENDIENTE";
            evento.detallesJson = "{ \"idDetalle\": " + idDetalle + " }";
            actividadOperativaLocalDao.insertar(evento);
            dispararSincronizacion();
            if (onComplete != null) onComplete.run();
        });
    }

    public void cancelarMunicionPendienteMesa(int idMesa, Runnable onComplete) {
        executorService.execute(() -> {
            detallePedidoDao.cancelarTodosLosPendientesMesa(idMesa);
            ActividadOperativaLocal evento = new ActividadOperativaLocal();
            evento.eventoId = UUID.randomUUID().toString();
            evento.tipoEvento = "CANCELACION_MESA";
            evento.fechaDispositivo = System.currentTimeMillis();
            evento.estadoSync = "PENDIENTE";
            evento.detallesJson = "{ \"idMesa\": " + idMesa + " }";
            actividadOperativaLocalDao.insertar(evento);
            dispararSincronizacion();
            if (onComplete != null) onComplete.run();
        });
    }

    public LiveData<List<VentaHistorial>> obtenerTodoElHistorial() {
        return detallePedidoDao.obtenerTodoElHistorial();
    }

    private void dispararSincronizacion() {
        Constraints constraints = new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
        OneTimeWorkRequest syncRequest = new OneTimeWorkRequest.Builder(OperatividadSyncWorker.class).setConstraints(constraints).build();
        WorkManager.getInstance(this.context).enqueueUniqueWork("SyncOperatividadInmediata", ExistingWorkPolicy.KEEP, syncRequest);
    }
}