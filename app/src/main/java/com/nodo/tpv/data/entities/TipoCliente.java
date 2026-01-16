package com.nodo.tpv.data.entities;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import lombok.Data;

@Data
@Entity(tableName = "tipo_cliente")
public class TipoCliente {

    @PrimaryKey(autoGenerate = false)
    @ColumnInfo(name = "idTipoCliente")
    public int idTipoCliente;

    public String nombreTipoCliente;

    public TipoCliente(int idTipoCliente, String nombreTipoCliente) {
        this.idTipoCliente = idTipoCliente;
        this.nombreTipoCliente = nombreTipoCliente;
    }
}
