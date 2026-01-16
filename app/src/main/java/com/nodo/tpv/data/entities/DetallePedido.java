package com.nodo.tpv.data.entities;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import java.math.BigDecimal;
import lombok.Data;

@Data
@Entity(tableName = "detalle_pedido")
public class DetallePedido {
    @PrimaryKey(autoGenerate = true)
    public int idDetalle;
    public int idCliente;
    public int idProducto;
    public int cantidad;
    public BigDecimal precioEnVenta;
    public boolean esApuesta;
    public String idDueloOrigen;

    // 🔥 NUEVA COLUMNA: Guarda por ejemplo "1 - 0"
    public String marcadorAlMomento;
    // 🔥 NUEVA COLUMNA: Para ordenar por tiempo
    //public long fechaRegistro = System.currentTimeMillis();
    public long fechaLong = System.currentTimeMillis();
}