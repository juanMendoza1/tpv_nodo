package com.nodo.tpv.data.dto;

import java.util.Map;

public class EventoOperativoDTO {
    public String eventoId;
    public String tipoEvento;
    public long fechaDispositivo;
    public Map<String, Object> data; // Retrofit convertirá este Map en un JSON flexible
}