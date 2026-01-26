package com.nodo.tpv.data.entities;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

import lombok.Data;

@Data
@Entity(tableName = "perfil_duelo_ind")
public class PerfilDueloInd {
    @PrimaryKey
    public int idMesa;
    public int metaPuntos; // 20, 25, 30, 40, 50
    public String nivelNombre; // "Aficionado", "Intermedio", etc.
    public String reglaPago; // "PERDEDORES", "TODOS", "ULTIMO"

    public PerfilDueloInd(int idMesa, int metaPuntos, String nivelNombre, String reglaPago) {
        this.idMesa = idMesa;
        this.metaPuntos = metaPuntos;
        this.nivelNombre = nivelNombre;
        this.reglaPago = reglaPago;
    }
}