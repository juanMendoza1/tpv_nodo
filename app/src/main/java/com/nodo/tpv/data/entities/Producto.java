package com.nodo.tpv.data.entities;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.math.BigDecimal;

import lombok.Data;

@Data
@Entity(tableName = "producto")
public class Producto {

    @PrimaryKey
    public int idProducto;
    public String nombreProducto;
    public BigDecimal precioProducto;
    public BigDecimal precioCosto;
    public int stockActual;
    public String categoria;
    public String imagenUrl;

}
