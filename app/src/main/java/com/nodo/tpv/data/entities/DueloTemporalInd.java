package com.nodo.tpv.data.entities;

import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;
import lombok.Data;

@Data
@Entity(tableName = "duelos_temporales_ind",
        foreignKeys = @ForeignKey(
                entity = Cliente.class,
                parentColumns = "idCliente",
                childColumns = "idCliente",
                onDelete = ForeignKey.CASCADE
        ),
        indices = {@Index("idCliente")})
public class DueloTemporalInd {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public String idDuelo;      // ID único de la partida actual
    public int idMesa;          // Mesa donde ocurre el duelo
    public int idCliente;       // Cliente participante
    public int score;           // Carambolas actuales
    public int meta;            // Meta establecida (ej: 15)

    // Configuración de Pago:
    // "TODOS": Pagan todos por igual
    // "PERDEDORES": El ganador no paga, el resto reparte
    // "ULTIMO": Solo paga el que tenga menos puntos
    public String reglaPago;

    public long timestampInicio; // Para persistir el cronómetro
    public String estado;        // "ACTIVO" o "FINALIZADO"

    public DueloTemporalInd(String idDuelo, int idMesa, int idCliente, int meta, String reglaPago) {
        this.idDuelo = idDuelo;
        this.idMesa = idMesa;
        this.idCliente = idCliente;
        this.score = 0;
        this.meta = meta;
        this.reglaPago = reglaPago;
        this.timestampInicio = System.currentTimeMillis();
        this.estado = "ACTIVO";
    }
}