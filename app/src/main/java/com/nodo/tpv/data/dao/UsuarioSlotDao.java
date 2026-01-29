package com.nodo.tpv.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import com.nodo.tpv.data.entities.UsuarioSlot;
import com.nodo.tpv.data.entities.LogSesion;
import java.util.List;

@Dao
public interface UsuarioSlotDao {

    // --- GESTIÓN DE SLOTS ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void actualizarSlot(UsuarioSlot slot);

    @Query("SELECT * FROM usuario_slot WHERE idSlot = :slotId")
    UsuarioSlot obtenerSlot(int slotId);

    @Query("UPDATE usuario_slot SET estado = 'DISPONIBLE', idUsuario = 0 WHERE idSlot = :slotId")
    void liberarSlot(int slotId);

    // --- GESTIÓN DE LOGS (AUDITORÍA) ---
    @Insert
    void insertarLogSesion(LogSesion log);

    @Query("SELECT * FROM log_sesion WHERE sincronizado = 0")
    List<LogSesion> obtenerLogsPendientes();

    @Query("UPDATE log_sesion SET sincronizado = 1 WHERE idLog = :id")
    void marcarLogSincronizado(int id);
}