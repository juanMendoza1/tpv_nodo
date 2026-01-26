package com.nodo.tpv.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.nodo.tpv.data.entities.DetalleDueloTemporalInd;
import com.nodo.tpv.data.entities.DueloTemporalInd;

import java.util.List;

@Dao
public interface DueloTemporalIndDao {

    // 🔥 Guardar o actualizar el estado de un cliente en el duelo
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertarOActualizar(DueloTemporalInd duelo);

    // 🔥 Obtener todos los clientes activos en una mesa específica
    @Query("SELECT * FROM duelos_temporales_ind WHERE idMesa = :idMesa AND estado = 'ACTIVO'")
    LiveData<List<DueloTemporalInd>> obtenerDueloActivoPorMesa(int idMesa);

    // 🔥 Consultar un cliente específico en un duelo activo
    @Query("SELECT * FROM duelos_temporales_ind WHERE idMesa = :idMesa AND idCliente = :idCliente AND estado = 'ACTIVO' LIMIT 1")
    DueloTemporalInd obtenerEstadoCliente(int idMesa, int idCliente);

    // 🔥 Actualizar solo el puntaje (carambolas) de un cliente
    @Query("UPDATE duelos_temporales_ind SET score = :nuevoScore WHERE idMesa = :idMesa AND idCliente = :idCliente AND estado = 'ACTIVO'")
    void actualizarScore(int idMesa, int idCliente, int nuevoScore);

    // 🔥 Cambiar la regla de pago para todos los integrantes de la mesa
    @Query("UPDATE duelos_temporales_ind SET reglaPago = :nuevaRegla WHERE idMesa = :idMesa AND estado = 'ACTIVO'")
    void actualizarReglaPagoMesa(int idMesa, String nuevaRegla);

    // 🔥 Finalizar el duelo para todos los clientes de la mesa (Cierre de mesa)
    @Query("UPDATE duelos_temporales_ind SET estado = 'FINALIZADO' WHERE idMesa = :idMesa AND estado = 'ACTIVO'")
    void finalizarDueloMesa(int idMesa);

    // 🔥 Eliminar registros huérfanos o antiguos si es necesario
    @Query("DELETE FROM duelos_temporales_ind WHERE idMesa = :idMesa")
    void eliminarDueloPorMesa(int idMesa);

    // 🔥 Obtener el timestamp de inicio para sincronizar cronómetros
    @Query("SELECT timestampInicio FROM duelos_temporales_ind WHERE idMesa = :idMesa AND estado = 'ACTIVO' LIMIT 1")
    long obtenerTimestampInicio(int idMesa);

    @Query("SELECT timestampInicio FROM duelos_temporales_ind WHERE idMesa = :idMesa AND idCliente = :idCliente AND estado = 'ACTIVO' LIMIT 1")
    long obtenerTimestampInicioPorCliente(int idMesa, int idCliente);

    @Query("SELECT idDuelo FROM duelos_temporales_ind WHERE idMesa = :idMesa AND estado = 'ACTIVO' LIMIT 1")
    String obtenerIdDueloPorMesaSincrono(int idMesa);

    // 🔥 NOVEDAD 1: Obtener el objeto completo para manipularlo (Síncrono para usar en hilos)
    @Query("SELECT * FROM duelos_temporales_ind WHERE idMesa = :idMesa AND idCliente = :idCliente AND estado = 'ACTIVO' LIMIT 1")
    DueloTemporalInd obtenerDueloPorMesaYCliente(int idMesa, int idCliente);

    // 🔥 NOVEDAD 2: Actualizar el objeto completo en la base de datos
    @Update
    void actualizar(DueloTemporalInd duelo);

    // 🔥 Obtener lista para cargar marcadores iniciales al abrir el fragment
    @Query("SELECT * FROM duelos_temporales_ind WHERE idMesa = :idMesa AND estado = 'ACTIVO'")
    LiveData<List<DueloTemporalInd>> obtenerScoresDesdePersistencia(int idMesa);

    // Finalizar a un solo jugador (Para la "X")
    @Query("UPDATE duelos_temporales_ind SET estado = 'FINALIZADO' " +
            "WHERE idMesa = :idMesa AND idCliente = :idCliente AND estado = 'ACTIVO'")
    void finalizarJugadorIndividual(int idMesa, int idCliente);

    // 🔥 FILTRADO POR UUID: Obtenemos solo los hitos de la partida en curso
    @Query("SELECT * FROM detalle_duelo_temporal_ind " +
            "WHERE idDuelo = :uuidDuelo " +
            "ORDER BY fechaHito DESC")
    LiveData<List<DetalleDueloTemporalInd>> obtenerHitosDePartidaActual(String uuidDuelo);

    // Para el proceso síncrono del Log Agrupado
    @Query("SELECT * FROM detalle_duelo_temporal_ind " +
            "WHERE idDuelo = :uuid " +
            "ORDER BY scoreGlobalAnotador ASC")
    List<DetalleDueloTemporalInd> obtenerHistorialHitosSincrono(String uuid);

}
