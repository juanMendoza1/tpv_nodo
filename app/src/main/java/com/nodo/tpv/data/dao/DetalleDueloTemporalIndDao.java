package com.nodo.tpv.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import com.nodo.tpv.data.entities.DetalleDueloTemporalInd;
import java.util.List;

@Dao
public interface DetalleDueloTemporalIndDao {

    @Insert
    long insertarHito(DetalleDueloTemporalInd hito);
    // Recupera los hitos de la mesa actual para el encabezado del Log
    @Query("SELECT * FROM detalle_duelo_temporal_ind WHERE idMesa = :idMesa ORDER BY fechaHito DESC")
    LiveData<List<DetalleDueloTemporalInd>> obtenerHitosPorMesa(int idMesa);

    // Para auditoría: permite ver hitos de un duelo específico aunque la mesa ya esté cerrada
    @Query("SELECT * FROM detalle_duelo_temporal_ind WHERE idDuelo = :uuid ORDER BY scoreGlobalAnotador ASC")
    List<DetalleDueloTemporalInd> obtenerHistorialHitosSincrono(String uuid);
}
