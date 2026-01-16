package com.nodo.tpv.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.nodo.tpv.data.entities.Mesa;

import java.util.List;

@Dao
public interface MesaDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertarMesa(Mesa mesa);

    @Update
    void actualizarMesa(Mesa mesa);

    @Query("SELECT * FROM mesa WHERE idMesa = :idMesa LIMIT 1")
    Mesa obtenerMesa(int idMesa);

    // 🔥 NUEVA CONSULTA: Busca si la mesa específica está abierta
    @Query("SELECT * FROM mesa WHERE idMesa = :idMesa AND estado = 'ABIERTO' LIMIT 1")
    Mesa obtenerMesaAbierta(int idMesa);

    @Query("DELETE FROM mesa WHERE idMesa = :idMesa")
    void eliminarMesa(int idMesa);

    @Query("SELECT * FROM mesa ORDER BY idMesa ASC")
    List<Mesa> obtenerTodasLasMesas();

    @Query("SELECT * FROM mesa WHERE idMesa = :id LIMIT 1")
    Mesa obtenerMesaPorId(int id);

    @Query("UPDATE mesa SET reglaDuelo = :regla WHERE idMesa = :idMesa")
    void actualizarReglaDuelo(int idMesa, String regla);

}
