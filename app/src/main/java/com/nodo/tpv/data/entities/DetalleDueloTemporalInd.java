package com.nodo.tpv.data.entities;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import lombok.Data;

@Data
@Entity(tableName = "detalle_duelo_temporal_ind")
public class DetalleDueloTemporalInd {
    @PrimaryKey(autoGenerate = true)
    public int idDetalleDuelo;

    public String idDuelo;      // UUID para agrupar toda la partida
    public int idMesa;
    public long fechaHito = System.currentTimeMillis();

    // --- DATOS DEL GANADOR DE LA RONDA ---
    public int idClienteAnotador;
    public String aliasAnotador;
    public int scoreGlobalAnotador; // En qué carambola iba (ej: su carambola #3)

    // --- EL SNAPSHOT DE LA MESA (Lo que me pediste) ---
    // Guardaremos un String formateado con los minimarcadores de todos
    // Ejemplo: "Azul: 10 | Rojo: 5 | Amarillo: 20"
    public String fotoMiniMarcadores;

    // El marcador global de todos en ese momento
    // Ejemplo: "Azul: 2 | Rojo: 1 | Amarillo: 4"
    public String marcadorGlobalSnapshot;

    public DetalleDueloTemporalInd(String idDuelo, int idMesa, int idClienteAnotador,
                                   String aliasAnotador, int scoreGlobalAnotador,
                                   String fotoMiniMarcadores, String marcadorGlobalSnapshot) {
        this.idDuelo = idDuelo;
        this.idMesa = idMesa;
        this.idClienteAnotador = idClienteAnotador;
        this.aliasAnotador = aliasAnotador;
        this.scoreGlobalAnotador = scoreGlobalAnotador;
        this.fotoMiniMarcadores = fotoMiniMarcadores;
        this.marcadorGlobalSnapshot = marcadorGlobalSnapshot;
    }
}