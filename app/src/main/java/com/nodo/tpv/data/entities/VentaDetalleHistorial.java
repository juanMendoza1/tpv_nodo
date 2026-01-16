package com.nodo.tpv.data.entities;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import java.math.BigDecimal;
import lombok.Data;

@Data
@Entity(tableName = "venta_detalle_historial")
public class VentaDetalleHistorial {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public int idVentaPadre;
    public String nombreProducto;
    public int cantidad;
    public BigDecimal precioUnitario;

    // 🔥 NUEVA COLUMNA
    public boolean esApuesta;
}