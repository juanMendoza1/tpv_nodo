package com.nodo.tpv.data.dto;

import androidx.room.Embedded;

import com.nodo.tpv.data.entities.Cliente;

import java.math.BigDecimal;

public class ClienteConSaldo {
    @Embedded
    public Cliente cliente;

    public BigDecimal saldoTotal; // Aquí llegará el resultado del SUM(...)

    // CAMBIO CLAVE: Foto temporal solo para la sesión del duelo
    private String fotoTemporalDuelo;

    public String getFotoTemporalDuelo() { return fotoTemporalDuelo; }
    public void setFotoTemporalDuelo(String foto) { this.fotoTemporalDuelo = foto; }
}
