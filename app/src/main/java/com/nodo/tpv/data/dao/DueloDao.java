package com.nodo.tpv.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.nodo.tpv.data.entities.Cliente;
import com.nodo.tpv.data.entities.DueloTemporal;
import com.nodo.tpv.data.entities.PerfilDuelo;
import com.nodo.tpv.data.entities.PerfilDueloInd;

import java.util.List;

@Dao
public interface DueloDao {

    @Insert
    void insertarParticipante(DueloTemporal participante);

    // 🔥 CAMBIO MULTIMESA: Ahora busca el duelo activo específicamente en ESA mesa
    @Query("SELECT idDuelo FROM duelos_temporales WHERE estado = 'ACTIVO' AND idMesa = :idMesa LIMIT 1")
    String obtenerUuidDueloActivoPorMesa(int idMesa);

    // 🔥 CAMBIO MULTIMESA: Igual para 3 Bandas
    @Query("SELECT idDuelo FROM duelos_temporales_ind WHERE estado = 'ACTIVO' AND idMesa = :idMesa LIMIT 1")
    String obtenerUuidDueloActivoIndPorMesa(int idMesa);

    // Obtiene los clientes de un equipo específico para una mesa en particular
    @Query("SELECT c.* FROM cliente c " +
            "INNER JOIN duelos_temporales d ON c.idCliente = d.idCliente " +
            "WHERE d.estado = 'ACTIVO' AND d.idMesa = :idMesa AND d.idEquipo = :idEquipo")
    LiveData<List<Cliente>> obtenerEquipoActivoPorMesa(int idMesa, int idEquipo);

    // Verifica si hay un duelo en curso en una mesa específica
    @Query("SELECT COUNT(*) FROM duelos_temporales WHERE estado = 'ACTIVO' AND idMesa = :idMesa")
    int hayDueloActivoEnMesa(int idMesa);

    // 🔥 CAMBIO MULTIMESA: Finaliza solo el duelo de la mesa que le pasamos
    @Query("UPDATE duelos_temporales SET estado = 'FINALIZADO' WHERE estado = 'ACTIVO' AND idMesa = :idMesa")
    void finalizarDueloPorMesa(int idMesa);

    // 🔥 CAMBIO MULTIMESA: Borra los datos fallidos solo de esa mesa
    @Query("DELETE FROM duelos_temporales WHERE estado = 'ACTIVO' AND idMesa = :idMesa")
    void borrarDueloFallidoPorMesa(int idMesa);

    @Query("SELECT * FROM duelos_temporales WHERE estado = 'ACTIVO' AND idMesa = :idMesa")
    List<DueloTemporal> obtenerParticipantesSincronoPorMesa(int idMesa);

    @Query("SELECT c.* FROM cliente c " +
            "INNER JOIN duelos_temporales d ON c.idCliente = d.idCliente " +
            "WHERE d.estado = 'ACTIVO' AND d.idMesa = :idMesa")
    List<Cliente> obtenerTodosLosParticipantesDueloPorMesa(int idMesa);

    @Query("SELECT COUNT(*) > 0 FROM duelos_temporales WHERE idDuelo = :uuid AND idCliente = :idCliente")
    boolean verificarClienteEnDuelo(String uuid, int idCliente);

    // Actualizar configuración del duelo actual (Estos se mantienen por UUID, lo cual es correcto)
    @Query("UPDATE duelos_temporales SET requierePin = :requiere WHERE idDuelo = :uuid")
    void actualizarSeguridadPinDuelo(String uuid, boolean requiere);

    @Query("SELECT requierePin FROM duelos_temporales WHERE idDuelo = :uuid LIMIT 1")
    boolean obtenerRequierePinDuelo(String uuid);

    @Query("SELECT reglaCobro FROM duelos_temporales WHERE idDuelo = :uuid LIMIT 1")
    String obtenerReglaCobroDuelo(String uuid);

    @Query("UPDATE duelos_temporales SET reglaCobro = :nuevaRegla WHERE idDuelo = :uuid")
    void actualizarReglaCobroDuelo(String uuid, String nuevaRegla);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertarPerfilesIniciales(List<PerfilDuelo> perfiles);

    @Query("SELECT * FROM perfiles_duelo")
    LiveData<List<PerfilDuelo>> obtenerTodosLosPerfiles();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertarPerfilInd(PerfilDueloInd perfil);

    @Query("SELECT * FROM perfil_duelo_ind WHERE idMesa = :mesaId")
    LiveData<PerfilDueloInd> obtenerPerfilMesaInd(int mesaId);
}