package com.nodo.tpv.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.nodo.tpv.data.dto.DetalleConNombre;
import com.nodo.tpv.data.dto.DetalleHistorialDuelo;
import com.nodo.tpv.data.entities.DetallePedido;
import com.nodo.tpv.data.entities.VentaDetalleHistorial;
import com.nodo.tpv.data.entities.VentaHistorial;
import java.math.BigDecimal;
import java.util.List;

@Dao
public interface DetallePedidoDao {

    @Insert
    void insertarDetalle(DetallePedido detalle);

    // --- NUEVAS CONSULTAS PARA LOGÍSTICA ---

    /**
     * Actualiza el estado de un pedido y registra quién lo entregó.
     */
    @Query("UPDATE detalle_pedido SET estado = :nuevoEstado, idUsuarioEntrega = :idUsuario " +
            "WHERE idDetalle = :idDetalle")
    void despacharPedido(int idDetalle, String nuevoEstado, int idUsuario);

    /**
     * Cuenta cuántos pedidos hay pendientes para una mesa específica (para el Badge).
     */
    @Query("SELECT COUNT(*) FROM detalle_pedido WHERE estado = 'PENDIENTE' AND idMesa = :mesaId")
    LiveData<Integer> observarConteoPendientesMesa(int mesaId);

    /**
     * Obtiene la lista de todos los pedidos pendientes de una mesa (para el mini-log).
     */
    @Query("SELECT * FROM detalle_pedido WHERE estado = 'PENDIENTE' AND idMesa = :mesaId ORDER BY fechaLong ASC")
    LiveData<List<DetallePedido>> obtenerPendientesPorMesa(int mesaId);

    // --- CONSULTAS MODIFICADAS (FILTRANDO POR 'ENTREGADO') ---

    /**
     * 🔥 MODIFICADO: Solo suma al marcador de la Arena lo que ya fue ENTREGADO.
     */
    @Query("SELECT SUM(precioEnVenta * cantidad) FROM detalle_pedido " +
            "WHERE idCliente IN (:ids) AND idDueloOrigen = :dueloId AND estado = 'ENTREGADO'")
    BigDecimal obtenerSumaSaldosDuelo(List<Integer> ids, String dueloId);

    /**
     * 🔥 MODIFICADO: Suma el total de la cuenta del cliente (solo lo entregado).
     */
    @Query("SELECT SUM(cantidad * precioEnVenta) FROM detalle_pedido " +
            "WHERE idCliente = :clienteId AND estado = 'ENTREGADO'")
    LiveData<BigDecimal> obtenerTotalCuenta(int clienteId);

    /**
     * 🔥 MODIFICADO: Historial de la batalla. Traemos todo (incluyendo pendientes)
     * para que en el log se vea qué está en camino.
     */
    /*@Query("SELECT det.marcadorAlMomento, prod.nombreProducto, dt.idEquipo, det.precioEnVenta, det.fechaLong, det.estado " +
            "FROM detalle_pedido det " +
            "INNER JOIN producto prod ON det.idProducto = prod.idProducto " +
            "INNER JOIN duelos_temporales dt ON det.idCliente = dt.idCliente " +
            "WHERE det.idDueloOrigen = :uuidDuelo AND dt.idDuelo = :uuidDuelo " +
            "ORDER BY det.idDetalle DESC")
    LiveData<List<DetalleHistorialDuelo>> obtenerHistorialItemsDuelo(String uuidDuelo);*/

    // --- RESTO DE MÉTODOS EXISTENTES ---

    @Query("SELECT * FROM detalle_pedido WHERE idCliente = :clienteId")
    List<DetallePedido> obtenerConsumoPorCliente(int clienteId);

    @Query("DELETE FROM detalle_pedido WHERE idCliente = :clienteId")
    void borrarCuentaCliente(int clienteId);

    @Query("SELECT detalle.*, prod.nombreProducto FROM detalle_pedido AS detalle " +
            "INNER JOIN producto AS prod ON detalle.idProducto = prod.idProducto " +
            "WHERE detalle.idCliente = :clienteId")
    LiveData<List<DetalleConNombre>> obtenerDetalleConNombres(int clienteId);

    @Insert
    long insertarVentaHistorial(VentaHistorial venta);

    @Query("SELECT SUM(cantidad * precioEnVenta) FROM detalle_pedido WHERE idCliente = :clienteId")
    BigDecimal obtenerTotalDirecto(int clienteId);

    @Query("SELECT * FROM venta_historial ORDER BY fechaLong DESC")
    LiveData<List<VentaHistorial>> obtenerTodoElHistorial();

    @Insert
    void insertarDetallesHistorial(List<VentaDetalleHistorial> detalles);

    @Query("SELECT * FROM venta_detalle_historial WHERE idVentaPadre = :idVenta")
    List<VentaDetalleHistorial> obtenerDetallesDeVentaSincrono(int idVenta);

    @Insert
    void insertarVariosDetalles(List<DetallePedido> detalles);

    @Insert
    long insertarDetalleDuelo(DetallePedido detalle);

    @Query("SELECT SUM(dp.precioEnVenta * dp.cantidad) FROM detalle_pedido dp " +
            "INNER JOIN cliente c ON dp.idCliente = c.idCliente WHERE c.idMesa = :idMesa")
    BigDecimal obtenerTotalDeudaMesa(int idMesa);

    @Query("SELECT det.*, prod.nombreProducto " +
            "FROM detalle_pedido AS det " +
            "INNER JOIN producto AS prod ON det.idProducto = prod.idProducto " +
            "WHERE det.idCliente = :clienteId")
    List<DetalleConNombre> obtenerDetalleConNombresSincrono(int clienteId);

    // En DetallePedidoDao.java
    /*@Query("SELECT SUM(precioEnVenta) FROM detalle_pedido " +
            "WHERE idCliente = :idC AND idDueloOrigen = :uuid AND estado = 'ENTREGADO'")
    BigDecimal obtenerTotalClienteEnDuelo(int idC, String uuid);*/

    @Query("UPDATE detalle_pedido SET estado = 'ENTREGADO', idUsuarioEntrega = :idAdmin " +
            "WHERE idMesa = :idMesa AND estado = 'PENDIENTE'")
    void marcarTodoComoEntregadoMesa(int idMesa, int idAdmin);

    @Query("SELECT det.idDetalle, det.marcadorAlMomento, prod.nombreProducto, dt.idEquipo, " +
            "det.precioEnVenta, det.fechaLong, det.estado " +
            "FROM detalle_pedido det " +
            "INNER JOIN producto prod ON det.idProducto = prod.idProducto " +
            "INNER JOIN duelos_temporales dt ON det.idCliente = dt.idCliente " +
            "WHERE det.idDueloOrigen = :uuidDuelo AND dt.idDuelo = :uuidDuelo " +
            "ORDER BY det.idDetalle DESC")
    LiveData<List<DetalleHistorialDuelo>> obtenerHistorialItemsDuelo(String uuidDuelo);

    @Query("SELECT det.idDetalle, det.marcadorAlMomento, prod.nombreProducto, 0 as idEquipo, " +
            "det.precioEnVenta, det.fechaLong, det.estado " +
            "FROM detalle_pedido det " +
            "INNER JOIN producto prod ON det.idProducto = prod.idProducto " +
            "WHERE det.idMesa = :idMesa AND det.estado = 'PENDIENTE' " +
            "ORDER BY det.fechaLong ASC")
    LiveData<List<DetalleHistorialDuelo>> obtenerSoloPendientesMesa(int idMesa);

    @Query("UPDATE detalle_pedido SET estado = 'REGISTRADO' " +
            "WHERE idDueloOrigen = :uuid AND estado = 'ENTREGADO' AND idCliente = 0")
    void limpiarBolsaTrasPunto(String uuid);

    // Cambiar un producto específico de PENDIENTE a CANCELADO (cuando tocan la 'X' en el catálogo)
    @Query("UPDATE detalle_pedido SET estado = 'CANCELADO' WHERE idDetalle = :idDetalle")
    void cancelarDetallePorId(int idDetalle);

    // Cambiar todos los pendientes de una mesa a CANCELADO (Botón Limpiar Bolsa)
    @Query("UPDATE detalle_pedido SET estado = 'CANCELADO' WHERE idMesa = :idMesa AND estado = 'PENDIENTE'")
    void cancelarTodosLosPendientesMesa(int idMesa);

    @Query("SELECT SUM(precioEnVenta) FROM detalle_pedido " +
            "WHERE idCliente = :idC AND idDueloOrigen = :uuid AND estado = 'REGISTRADO'")
    BigDecimal obtenerTotalClienteEnDuelo(int idC, String uuid);

    // 2. Obtener lo que está en la bolsa (idCliente = 0 y ENTREGADO)
    @Query("SELECT * FROM detalle_pedido WHERE idDueloOrigen = :uuid AND estado = 'ENTREGADO' AND idCliente = 0")
    List<DetallePedido> obtenerDetallesMunicionSincrono(String uuid);

    @Update
    void actualizarDetalle(DetallePedido detalle);

    @Query("DELETE FROM detalle_pedido WHERE idDetalle = :id")
    void borrarDetallePorId(int id);

    @Query("SELECT * FROM detalle_pedido WHERE idMesa = :idMesa AND estado = 'ENTREGADO' AND idCliente = 0 AND idDueloOrigen = :uuid")
    List<DetallePedido> obtenerMunicionListaParaReparto(int idMesa, String uuid);

    @Query("SELECT * FROM detalle_pedido " +
            "WHERE idDueloOrigen = :uuid " +
            "AND estado = 'ENTREGADO' " +
            "AND idCliente = 0")
    List<DetallePedido> obtenerMunicionBolsaParaReparto(String uuid);

    /**
     * 🔥 NUEVO: Obtiene el historial de productos ya cargados a las cuentas individuales
     * (Estado 'REGISTRADO') para mostrar en el LOG de la mesa.
     */
    @Query("SELECT det.idDetalle, det.marcadorAlMomento, prod.nombreProducto, 0 as idEquipo, " +
            "det.precioEnVenta, det.fechaLong, det.estado " +
            "FROM detalle_pedido det " +
            "INNER JOIN producto prod ON det.idProducto = prod.idProducto " +
            "WHERE det.idMesa = :idMesa AND det.estado = 'REGISTRADO' " +
            "ORDER BY det.fechaLong DESC")
    LiveData<List<DetalleHistorialDuelo>> obtenerDeudaPorMesa(int idMesa);

    @Query("SELECT det.idDetalle, det.marcadorAlMomento, prod.nombreProducto, 0 as idEquipo, " +
            "det.precioEnVenta, det.fechaLong, det.estado " +
            "FROM detalle_pedido det " +
            "INNER JOIN producto prod ON det.idProducto = prod.idProducto " +
            "WHERE det.idMesa = :idMesa AND det.estado = 'REGISTRADO' " +
            "ORDER BY det.fechaLong DESC")
    List<DetalleHistorialDuelo> obtenerDeudaPorMesaSincrona(int idMesa);

    @Query("SELECT det.idDetalle, det.marcadorAlMomento, prod.nombreProducto, det.idCliente as idEquipo, " +
            "det.precioEnVenta, det.fechaLong, det.estado " +
            "FROM detalle_pedido det INNER JOIN producto prod ON det.idProducto = prod.idProducto " +
            "WHERE det.idMesa = :idMesa AND det.estado = 'REGISTRADO' ORDER BY det.fechaLong DESC")
    LiveData<List<DetalleHistorialDuelo>> obtenerDeudaPorMesaInd(int idMesa);

    @Query("SELECT det.idDetalle, det.marcadorAlMomento, prod.nombreProducto, det.idCliente as idEquipo, " +
            "det.precioEnVenta, det.fechaLong, det.estado " +
            "FROM detalle_pedido det INNER JOIN producto prod ON det.idProducto = prod.idProducto " +
            "WHERE det.idMesa = :idMesa AND det.estado = 'REGISTRADO' ORDER BY det.fechaLong DESC")
    List<DetalleHistorialDuelo> obtenerDeudaPorMesaIndSincrona(int idMesa);

    @Query("SELECT * FROM detalle_pedido WHERE idDetalle = :id")
    DetallePedido obtenerDetallePorId(int id);

    // También uno para obtener todos los pendientes de una mesa si vas a "Despachar Todo"
    @Query("SELECT * FROM detalle_pedido WHERE idMesa = :idMesa AND estado = 'PENDIENTE'")
    List<DetallePedido> obtenerPendientesMesaSincrono(int idMesa);

    @Query("UPDATE detalle_pedido SET estado = :nuevoEstado, idUsuarioEntrega = :idUsuario " +
            "WHERE idDetalle = :idDetalle")
    void despacharPedidoLocal(int idDetalle, String nuevoEstado, int idUsuario);


    @Query("SELECT * FROM detalle_pedido WHERE (estado = 'ENTREGADO' OR estado = 'REGISTRADO') AND sincronizado = 0")
    List<DetallePedido> obtenerDespachosPendientesSincronizar();

    @Query("UPDATE detalle_pedido SET sincronizado = 1 WHERE idDetalle = :id")
    void marcarComoSincronizado(int id);


}