package com.nodo.tpv.data.dto;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class ProductoDTO {
    public Long id;
    public String codigo;
    public String nombre;
    public String categoriaNombre;
    public BigDecimal precioCosto; // El que pediste para la distribución
    public BigDecimal precioVenta;
    public Integer stockActual;
}