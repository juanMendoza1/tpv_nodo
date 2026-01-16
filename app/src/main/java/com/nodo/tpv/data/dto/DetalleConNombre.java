package com.nodo.tpv.data.dto;

import androidx.room.Embedded;

import com.nodo.tpv.data.entities.Cliente;
import com.nodo.tpv.data.entities.DetallePedido;

import java.math.BigDecimal;

public class DetalleConNombre {
    @Embedded
    public DetallePedido detallePedido;
    public String nombreProducto;

    // Método de conveniencia para calcular el subtotal
    public BigDecimal getSubtotal() {
        return detallePedido.precioEnVenta.multiply(new BigDecimal(detallePedido.cantidad));
    }
}
