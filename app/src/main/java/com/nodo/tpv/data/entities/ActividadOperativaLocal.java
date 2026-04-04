package com.nodo.tpv.data.entities;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "cola_actividad_operativa")
public class ActividadOperativaLocal {

    @PrimaryKey
    @NonNull
    public String eventoId; // Usaremos UUID.randomUUID().toString()

    public String tipoEvento; // "PEDIDO_NUEVO", "DESPACHO", "CIERRE_CUENTA"

    public long fechaDispositivo; // System.currentTimeMillis()

    public String detallesJson; // Guardamos el JSON crudo en local (usaremos Gson para convertirlo)

    // "PENDIENTE" o "EN_PROCESO"
    public String estadoSync;
}