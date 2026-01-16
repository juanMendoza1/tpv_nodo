package com.nodo.tpv.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
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

    @Query("SELECT * FROM detalle_pedido WHERE idCliente = :clienteId")
    List<DetallePedido> obtenerConsumoPorCliente(int clienteId);

    @Query("SELECT SUM(cantidad * precioEnVenta) FROM detalle_pedido WHERE idCliente = :clienteId")
    LiveData<BigDecimal> obtenerTotalCuenta(int clienteId);

    @Query("DELETE FROM detalle_pedido WHERE idCliente = :clienteId")
    void borrarCuentaCliente(int clienteId);

    @Query("SELECT detalle.*, prod.nombreProducto " +
            "FROM detalle_pedido AS detalle " +
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

    @Query("SELECT detalle.*, prod.nombreProducto " +
            "FROM detalle_pedido AS detalle " +
            "INNER JOIN producto AS prod ON detalle.idProducto = prod.idProducto " +
            "WHERE detalle.idCliente = :clienteId")
    List<DetalleConNombre> obtenerDetalleConNombresSincrono(int clienteId);

    // 🔥 MODIFICADO: Suma solo lo que pertenezca a un duelo específico para el marcador de la Arena
    @Query("SELECT SUM(precioEnVenta * cantidad) FROM detalle_pedido " +
            "WHERE idCliente IN (:ids) AND idDueloOrigen = :dueloId")
    BigDecimal obtenerSumaSaldosDuelo(List<Integer> ids, String dueloId);

    @Query("SELECT SUM(precioEnVenta * cantidad) FROM detalle_pedido WHERE idCliente IN (:ids)")
    BigDecimal obtenerSumaSaldosClientes(List<Integer> ids);

    @Query("SELECT det.marcadorAlMomento, prod.nombreProducto, dt.idEquipo, det.precioEnVenta, det.fechaLong " +
            "FROM detalle_pedido det " +
            "INNER JOIN producto prod ON det.idProducto = prod.idProducto " +
            "INNER JOIN duelos_temporales dt ON det.idCliente = dt.idCliente " +
            "WHERE det.idDueloOrigen = :uuidDuelo AND dt.idDuelo = :uuidDuelo " +
            "ORDER BY det.idDetalle DESC")
    LiveData<List<DetalleHistorialDuelo>> obtenerHistorialItemsDuelo(String uuidDuelo);

    /**
     * Opcional: Si quieres ver cuánto ha gastado cada equipo específicamente en la arena.
     */
    @Query("SELECT SUM(det.precioEnVenta * det.cantidad) " +
            "FROM detalle_pedido det " +
            "INNER JOIN duelos_temporales dt ON det.idCliente = dt.idCliente " +
            "WHERE det.idDueloOrigen = :uuidDuelo AND dt.idEquipo = :idEquipo")
    BigDecimal obtenerTotalPorEquipoEnDuelo(String uuidDuelo, int idEquipo);

    @Insert
    void insertarVariosDetalles(List<DetallePedido> detalles);

    // Método para insertar un consumo de duelo con toda su metadata
    @Insert
    long insertarDetalleDuelo(DetallePedido detalle);

    @Query("SELECT SUM(precioEnVenta) FROM detalle_pedido WHERE idCliente = :idC AND idDueloOrigen = :uuid")
    BigDecimal obtenerTotalClienteEnDuelo(int idC, String uuid);

    @Query("SELECT SUM(dp.precioEnVenta * dp.cantidad) " +
            "FROM detalle_pedido dp " +
            "INNER JOIN cliente c ON dp.idCliente = c.idCliente " +
            "WHERE c.idMesa = :idMesa")
    BigDecimal obtenerTotalDeudaMesa(int idMesa);


}