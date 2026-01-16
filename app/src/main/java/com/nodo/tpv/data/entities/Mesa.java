package com.nodo.tpv.data.entities;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.math.BigDecimal;

import lombok.Data;

@Data
@Entity(tableName = "mesa")
public class Mesa {
    @PrimaryKey
    public int idMesa;
    public long fechaApertura;
    public long fechaCierre;
    public int idUsuario;
    public String estado; // "ABIERTO", "CERRADO"

    // 🔥 NUEVOS CAMPOS PARA LA REGLA DE NEGOCIO
    public String tipoJuego; // "POOL" (Costo Directo) o "3BANDAS" (Cronómetro)
    public BigDecimal tarifaTiempo; // Por si cada mesa tiene un costo por hora distinto
    public String reglaDuelo = "GANADOR_SALVA"; // Valor por defecto
}
