package com.nodo.tpv.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.nodo.tpv.data.entities.TipoCliente;

import java.util.List;

@Dao
public interface TipoClienteDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    long insertar(TipoCliente tipoCliente);

    @Update
    void actualizar(TipoCliente tipoCliente);

    @Query("SELECT * FROM tipo_cliente ORDER BY idTipoCliente ASC")
    List<TipoCliente> obtenerTodosTipoCliente();

    @Query("SELECT * FROM tipo_cliente WHERE idTipoCliente = :id LIMIT 1")
    TipoCliente obtenerTipoClienteById(int id);

}
