package com.nodo.tpv.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.nodo.tpv.data.dto.ClienteConSaldo;
import com.nodo.tpv.data.entities.Cliente;

import java.util.List;

@Dao
public interface ClienteDao {

    @Insert
    long insertar(Cliente cliente);

    @Update
    void actualizar(Cliente cliente);

    @Delete
    void eliminar(Cliente cliente);

    @Query("SELECT * FROM cliente ORDER BY idCliente ASC")
    List<Cliente> obtenerTodosClientes();

    @Query("SELECT * FROM cliente WHERE idCliente = :id LIMIT 1")
    Cliente obtenerClienteById(int id);

    @Query("DELETE FROM cliente")
    void eliminarTodosCliente();

    @Query("SELECT cliente.*, " +
            "COALESCE((SELECT SUM(cantidad * precioEnVenta) FROM detalle_pedido WHERE idCliente = cliente.idCliente), 0) as saldoTotal " +
            "FROM cliente")
    LiveData<List<ClienteConSaldo>> obtenerTodosLosClientesLive();

    @Query("DELETE FROM cliente WHERE idCliente = :id")
    void eliminarPorId(int id);

}
