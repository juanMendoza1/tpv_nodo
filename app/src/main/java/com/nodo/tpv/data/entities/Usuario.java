package com.nodo.tpv.data.entities;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

import lombok.Data;

@Data
@Entity(tableName = "usuario")
public class Usuario {

    @PrimaryKey
    public int idUsuario;
    public String nombreUsuario;
    public String rolUsuario;
    public int idMesa;      // viene de la API (numero de mesa asignado al usuario)

}
