package com.nodo.tpv.data.entities;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import lombok.Data;

@Data
@Entity(tableName = "bolas_anotadas")
public class BolaAnotada {
    @PrimaryKey(autoGenerate = true)
    public int id;

    public String idDuelo;   // El UUID del duelo actual
    public int colorEquipo;  // Color del equipo que la anotó
    public int numeroBola;   // Del 1 al 15
    public long timestamp;   // Hora en que se anotó

    public BolaAnotada(String idDuelo, int colorEquipo, int numeroBola) {
        this.idDuelo = idDuelo;
        this.colorEquipo = colorEquipo;
        this.numeroBola = numeroBola;
        this.timestamp = System.currentTimeMillis();
    }
}