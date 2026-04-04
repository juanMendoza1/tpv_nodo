package com.nodo.tpv.data.dto;

import java.util.List;

public class SincronizacionPaqueteDTO {
    public String terminalUuid;
    public long empresaId;
    public List<EventoOperativoDTO> eventos;
}