package com.nodo.tpv.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.nodo.tpv.data.entities.BolaAnotada;
import java.util.List;

@Dao
public interface BolaDueloDao {
    @Insert
    void insertarBola(BolaAnotada bola);

    @Insert
    void insertarMultiplesBolas(List<BolaAnotada> bolas);

    // Obtener todas las bolas de un duelo en tiempo real
    @Query("SELECT * FROM bolas_anotadas WHERE idDuelo = :idDuelo")
    LiveData<List<BolaAnotada>> observarBolasDuelo(String idDuelo);

    // Obtener las bolas ya anotadas (para bloquearlas en la lista y que nadie más las elija)
    @Query("SELECT numeroBola FROM bolas_anotadas WHERE idDuelo = :idDuelo")
    List<Integer> obtenerBolasYaAnotadasSincrono(String idDuelo);

    @Query("DELETE FROM bolas_anotadas WHERE idDuelo = :idDuelo AND numeroBola = :numeroBola")
    void eliminarBola(String idDuelo, int numeroBola);

    @Query("DELETE FROM bolas_anotadas WHERE idDuelo = :idDuelo")
    void limpiarMesa(String idDuelo);

    @Query("SELECT * FROM bolas_anotadas WHERE idDuelo = :uuid ORDER BY timestamp ASC")
    List<BolaAnotada> obtenerBolasPorDueloSincrono(String uuid);
}