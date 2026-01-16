package com.nodo.tpv.data.entities;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.math.BigDecimal;

import lombok.Data;

@Data
@Entity(tableName = "venta_historial")
public class VentaHistorial {

    @PrimaryKey(autoGenerate = true)
    public int idVenta;

    public int idCliente;
    public String nombreCliente;
    public BigDecimal montoTotal;
    public String metodoPago; // EFECTIVO, QR, TRANSFERENCIA
    public long fechaLong; // System.currentTimeMillis()
    // 🔥 Nueva columna: "PENDIENTE" o "EXITOSO"
    public String estado;
    @ColumnInfo(name = "foto_comprobante")
    public String fotoComprobante; // Aquí guardaremos el String Base64

}
