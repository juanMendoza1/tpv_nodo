package com.nodo.tpv.data.entities;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import lombok.Data;

@Data
@Entity(tableName = "duelos_temporales",
        foreignKeys = @ForeignKey(
                entity = Cliente.class,
                parentColumns = "idCliente",
                childColumns = "idCliente",
                onDelete = ForeignKey.CASCADE
        ),
        indices = {@Index("idCliente")})
public class DueloTemporal {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public String idDuelo; // 🔥 Cambiado de dueloUuid a idDuelo para coincidir con el DAO
    public int idEquipo;   // 1: Azul, 2: Rojo
    public int idCliente;
    public boolean esGanador;
    public String estado;  // "ACTIVO" o "FINALIZADO"

    public DueloTemporal(String idDuelo, int idEquipo, int idCliente, String estado) {
        this.idDuelo = idDuelo;
        this.idEquipo = idEquipo;
        this.idCliente = idCliente;
        this.estado = estado;
        this.esGanador = false;
    }
}