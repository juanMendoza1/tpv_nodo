package com.nodo.tpv.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.nodo.tpv.data.entities.Producto;

import java.util.List;

@Dao
public interface ProductoDao {
    @Query("SELECT * FROM producto ORDER BY nombreProducto ASC")
    List<Producto> obtenerTodosProductos();

    @Query("SELECT * FROM producto WHERE categoria = :cat")
    List<Producto> obtenerPorCategoriaProductos(String cat);

    // Para tu buscador en tiempo real
    @Query("SELECT * FROM producto WHERE nombreProducto LIKE :busqueda")
    List<Producto> buscarProducto(String busqueda);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertarOActualizar(List<Producto> productos);

    @Query("SELECT COUNT(*) FROM producto")
    int getProductoCount();

    @Query("SELECT p.* FROM producto p " +
            "INNER JOIN detalle_pedido dp ON p.idProducto = dp.idProducto " +
            "WHERE dp.idDueloOrigen = :uuid " +
            "AND dp.estado = 'ENTREGADO' " +
            "AND dp.idCliente = 0")
    List<Producto> obtenerProductosBolsaEntregados(String uuid);

    @Query("SELECT p.* FROM producto p " +
            "INNER JOIN detalle_pedido dp ON p.idProducto = dp.idProducto " +
            "WHERE dp.idDueloOrigen = :uuid " +
            "AND dp.estado = 'ENTREGADO' " +
            "AND dp.idCliente = 0") // <--- ESTO ES LO QUE EVITA LA DUPLICACIÓN
    List<Producto> obtenerProductosEntregadosDuelo(String uuid);

    @Query("UPDATE producto SET stockActual = :stock, precioProducto = :precio WHERE idProducto = :id")
    void actualizarStockYPrecio(int id, int stock, java.math.BigDecimal precio);

    @Query("SELECT * FROM producto ORDER BY nombreProducto ASC")
    LiveData<List<Producto>> obtenerTodosProductosLiveData();

}
