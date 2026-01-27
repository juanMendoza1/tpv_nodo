package com.nodo.tpv.data.entities;

import androidx.room.Entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * Entidad que representa la identificación y estado de la terminal en el sistema.
 * Basada en la estructura del backend para asegurar compatibilidad total.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TerminalDispositivo {

    private Long id;

    // --- IDENTIFICACIÓN TÉCNICA Y SEGURIDAD ---

    // Identificador interno (Android ID) usado para validar el acceso
    private String uuidHardware;

    // Serial físico del equipo (opcional)
    private String serial;

    // Marca del fabricante (ej: Samsung)
    private String marca;

    // Modelo del dispositivo (ej: SM-X200)
    private String modelo;

    // Nombre descriptivo asignado (ej: "Caja Principal")
    private String alias;

    // --- CONTROL OPERATIVO ---

    // Fecha en que se registró por primera vez en el backend
    private String fechaRegistro;

    // Fecha del último contacto con el servidor
    private String ultimoAcceso;

    // Indica si el administrador ha bloqueado esta tablet remotamente
    private Boolean bloqueado;

    // Estado físico reportado (NUEVO, BUENO, DAÑADO)
    private String estadoFisico;

    private Suscripcion suscripcion;

    private String mensaje;
    private long empresaId;
}