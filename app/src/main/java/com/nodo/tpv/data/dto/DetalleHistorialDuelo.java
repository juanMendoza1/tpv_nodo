package com.nodo.tpv.data.dto;

public class DetalleHistorialDuelo {
    public String marcadorAlMomento; // Ej: "2 - 1"
    public String nombreProducto;
    public int idEquipo;
    public java.math.BigDecimal precioEnVenta;
    public long fechaLong; // Para ordenar y mostrar hora

    public String estado;
    public int idDetalle; // 🔥 AGREGAR ESTO: Clave para la actualización
}
