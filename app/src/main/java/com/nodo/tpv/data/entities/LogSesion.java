package com.nodo.tpv.data.entities;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import lombok.Data;

@Data
@Entity(tableName = "log_sesion")
public class LogSesion {
    @PrimaryKey(autoGenerate = true)
    public int idLog;

    public int idUsuario;
    public int slot;
    public String tipoEvento; // "LOGIN" o "LOGOUT"
    public long timestamp;

    /**
     * 0 = Pendiente de subir al servidor
     * 1 = Sincronizado con éxito
     */
    public int sincronizado = 0;
}