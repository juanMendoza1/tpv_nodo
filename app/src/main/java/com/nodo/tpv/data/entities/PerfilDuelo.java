package com.nodo.tpv.data.entities;


import androidx.room.Entity;
import androidx.room.PrimaryKey;

import lombok.Data;

@Data
@Entity(tableName = "perfiles_duelo")
public class PerfilDuelo {
    @PrimaryKey(autoGenerate = true)
    public int idPerfil;
    public String nombrePerfil; // Ej: "Estándar", "Competitivo"
    public String reglaPagoDefault; // "GANADOR_SALVA", "TODOS_PAGAN", etc.
    public boolean requierePinDefault;

    public PerfilDuelo(String nombrePerfil, String reglaPagoDefault, boolean requierePinDefault) {
        this.nombrePerfil = nombrePerfil;
        this.reglaPagoDefault = reglaPagoDefault;
        this.requierePinDefault = requierePinDefault;
    }
}
