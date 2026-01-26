package com.nodo.tpv.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.nodo.tpv.data.entities.PerfilDueloInd;

@Dao
public interface PerfilDueloIndDao {

    /**
     * Guarda o actualiza la configuración de nivel de una mesa.
     * Si la mesa ya tiene un perfil, lo reemplaza con los nuevos puntos/nivel.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertarOActualizar(PerfilDueloInd perfil);

    /**
     * Obtiene la configuración de nivel (meta de puntos) para una mesa específica.
     * Se usa LiveData para que la UI se actualice sola al cambiar el nivel en el panel.
     */
    @Query("SELECT * FROM perfil_duelo_ind WHERE idMesa = :idMesa LIMIT 1")
    LiveData<PerfilDueloInd> obtenerPerfilPorMesa(int idMesa);

    /**
     * Borra la configuración de una mesa (opcional, por si necesitas resetear el nivel).
     */
    @Query("DELETE FROM perfil_duelo_ind WHERE idMesa = :idMesa")
    void borrarPerfilMesa(int idMesa);
}