package com.nodo.tpv.data.entities;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.PrimaryKey;

import lombok.Data;

@Data
@Entity(
        tableName = "cliente",
        foreignKeys = @ForeignKey(
                entity = TipoCliente.class,
                parentColumns = "idTipoCliente",
                childColumns = "idTipoCliente",
                onDelete = ForeignKey.NO_ACTION
        )
)
public class Cliente {
    @PrimaryKey(autoGenerate = true)
    public int idCliente;

    public String alias;

    // referencia a tipoCliente
    public int idTipoCliente;

    // relación con la mesa
    public int idMesa;

    // 🔥 NUEVOS CAMPOS AÑADIDOS PARA LA SINCRONIZACIÓN Y AUDITORÍA
    public long fechaCreacionLong;
    public String identificadorDevice;
}