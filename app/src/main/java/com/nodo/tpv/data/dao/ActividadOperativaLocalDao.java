package com.nodo.tpv.data.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.nodo.tpv.data.entities.ActividadOperativaLocal;

import java.util.List;

@Dao
public interface ActividadOperativaLocalDao {

    // Guarda el evento en la cola. Si por alguna razón ya existe el UUID, lo reemplaza.
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertar(ActividadOperativaLocal actividad);

    // Saca todos los que están esperando viajar, ordenados por el más viejo primero
    @Query("SELECT * FROM cola_actividad_operativa WHERE estadoSync = 'PENDIENTE' ORDER BY fechaDispositivo ASC")
    List<ActividadOperativaLocal> obtenerPendientesSincrono();

    // Por si queremos marcarlos como "EN_PROCESO" para evitar que otro hilo los tome al mismo tiempo
    @Query("UPDATE cola_actividad_operativa SET estadoSync = :nuevoEstado WHERE eventoId = :eventoId")
    void actualizarEstado(String eventoId, String nuevoEstado);

    // Cuando el backend responda "OK", lo borramos de la tablet para liberar espacio
    @Query("DELETE FROM cola_actividad_operativa WHERE eventoId = :eventoId")
    void eliminarPorId(String eventoId);
}