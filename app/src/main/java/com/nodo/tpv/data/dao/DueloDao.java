package com.nodo.tpv.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.nodo.tpv.data.entities.Cliente;
import com.nodo.tpv.data.entities.DueloTemporal;

import java.util.List;

@Dao
public interface DueloDao {

    @Insert
    void insertarParticipante(DueloTemporal participante);

    /**
     * 🔥 CLAVE PARA LA PERSISTENCIA:
     * Recupera el UUID del duelo que está marcado como ACTIVO.
     * Esto permite que el ViewModel "recuerde" en qué duelo estaba al volver de otra pantalla.
     */
    @Query("SELECT idDuelo FROM duelos_temporales WHERE estado = 'ACTIVO' LIMIT 1")
    String obtenerUuidDueloActivo();

    // Obtiene los clientes de un equipo específico para el duelo actual
    @Query("SELECT c.* FROM cliente c " +
            "INNER JOIN duelos_temporales d ON c.idCliente = d.idCliente " +
            "WHERE d.estado = 'ACTIVO' AND d.idEquipo = :idEquipo")
    LiveData<List<Cliente>> obtenerEquipoActivo(int idEquipo);

    // Verifica si hay un duelo en curso al abrir la app
    @Query("SELECT COUNT(*) FROM duelos_temporales WHERE estado = 'ACTIVO'")
    int hayDueloActivo();

    // Finaliza el duelo (puedes borrar los datos o cambiar el estado)
    @Query("UPDATE duelos_temporales SET estado = 'FINALIZADO' WHERE estado = 'ACTIVO'")
    void finalizarDueloActual();

    @Query("DELETE FROM duelos_temporales WHERE estado = 'ACTIVO'")
    void borrarDueloFallido();

    @Query("SELECT * FROM duelos_temporales WHERE estado = 'ACTIVO'")
    List<DueloTemporal> obtenerParticipantesSincrono();

    @Query("SELECT c.* FROM cliente c " +
            "INNER JOIN duelos_temporales d ON c.idCliente = d.idCliente " +
            "WHERE d.estado = 'ACTIVO'")
    List<Cliente> obtenerTodosLosParticipantesDuelo();

}