package com.nodo.tpv.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

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
}
