package com.nodo.tpv.data.entities;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity(tableName = "usuario_slot")
public class UsuarioSlot {
    @PrimaryKey
    public int idSlot; // 1, 2, 3...

    public int idUsuario;
    public String loginUsuario;
    public long lastAccessTimestamp;

    // "ACTIVO" o "DISPONIBLE"
    public String estado;
}
