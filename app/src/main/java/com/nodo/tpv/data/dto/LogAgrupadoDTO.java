package com.nodo.tpv.data.dto;

import com.nodo.tpv.data.entities.DetalleDueloTemporalInd;
import java.math.BigDecimal;
import java.util.List;

public class LogAgrupadoDTO {
    public DetalleDueloTemporalInd hito;
    public List<DetalleHistorialDuelo> productos;
    public BigDecimal subtotalHito;

    public LogAgrupadoDTO(DetalleDueloTemporalInd hito, List<DetalleHistorialDuelo> productos) {
        this.hito = hito;
        this.productos = productos;

        BigDecimal suma = BigDecimal.ZERO;
        if (productos != null) {
            for (DetalleHistorialDuelo p : productos) {
                suma = suma.add(p.precioEnVenta);
            }
        }
        this.subtotalHito = suma;
    }
}