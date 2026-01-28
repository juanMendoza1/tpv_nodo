package com.nodo.tpv.data.entities;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import com.google.gson.annotations.SerializedName;

@Entity(tableName = "usuario") // Aquí confirmamos que la tabla se llama usuario
public class Usuario {
    @PrimaryKey
    @SerializedName("id")
    public int idUsuario;

    @SerializedName("nombreCompleto")
    public String nombreUsuario;
    public String rolUsuario; // Podemos usar el login como identificador de rol o alias

    public int idMesa; // Este lo usaremos luego para la operativa

    public String login;
}