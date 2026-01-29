package com.nodo.tpv.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.nodo.tpv.data.entities.Usuario;
import java.util.List;

@Dao
public interface UsuarioDao {

    @Insert
    long insertar(Usuario usuario);

    @Update
    void actualizar(Usuario usuario);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertarOActualizar(Usuario usuario);

    // NUEVO: Método que te faltaba para obtener la lista de todos los slots
    @Query("SELECT * FROM usuario ORDER BY nombreUsuario ASC")
    List<Usuario> obtenerTodos();

    @Query("SELECT * FROM usuario WHERE idUsuario = :id LIMIT 1")
    Usuario obtenerUsuarioPorId(int id);

    @Query("SELECT * FROM usuario LIMIT 1")
    Usuario obtenerUsuarioPersistente();

    @Query("DELETE FROM usuario")
    void eliminarTodos();

    @Query("SELECT * FROM usuario WHERE login = :login LIMIT 1")
    Usuario obtenerUsuarioPorLoginSincrono(String login);
}