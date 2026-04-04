package com.nodo.tpv.data.dto;

import java.util.List;
import java.util.Map;

public class ReporteEstadisticoDueloDTO {
    public int idMesa;
    public String uuidDuelo;
    public String tipoJuego;
    public long timestampFin;
    public Map<String, Integer> resumenGeneral; // <NombreEquipo, PuntosTotales>
    public Map<String, DetalleEquipo> detalleEstadistico; // <NombreEquipo, ObjetoDetalle>

    public static class DetalleEquipo {
        public int puntosTotales;
        public int puntosPositivos;
        public int cantidadMalas;
        public List<Integer> bolasAnotadas; // Aquí llegarán los números de las bolas (1, 2, 9, etc.)
    }
}