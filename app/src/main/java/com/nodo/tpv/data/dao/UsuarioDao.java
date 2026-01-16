package com.nodo.tpv.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.nodo.tpv.data.entities.Usuario;

@Dao
public interface UsuarioDao {

    @Insert
    long insertar(Usuario usuario);

    @Update
    void actualizar(Usuario usuario);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertarOActualizar(Usuario usuario);

    @Query("SELECT * FROM usuario WHERE idUsuario = :id LIMIT 1")
    Usuario obtenerUsuarioPorId(int id);

    @Query("SELECT * FROM usuario LIMIT 1")
    Usuario obtenerUsuarioPersistente();

    @Query("DELETE FROM usuario")
    void eliminarTodos();  // por si deseas reiniciar la app

}
