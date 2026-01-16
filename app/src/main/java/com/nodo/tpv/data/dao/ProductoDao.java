package com.nodo.tpv.data.dao;

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
}
