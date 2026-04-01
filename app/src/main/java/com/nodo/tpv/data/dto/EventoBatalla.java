package com.nodo.tpv.data.dto;

/**
 * DTO para el Historial de Batalla estilo Ghost.
 * Soporta jerarquía de Padre (Estado de Batalla) e Hijos (Detalles).
 */
public class EventoBatalla {

    // --- CONSTANTES DE TIPO ---
    public static final int TIPO_BOLA_ANOTADA = 1;
    public static final int TIPO_FALTA = 2;
    public static final int TIPO_COMPRA_MUNICION = 3;
    public static final int TIPO_CIERRE_RONDA = 4; // Usado para el "Estado de Batalla" (Padre)

    // --- LÓGICA DE EXPANSIÓN ---
    public boolean esHijo = false;      // Si es true, se oculta/muestra bajo un padre
    public boolean expandido = false;   // Estado de visibilidad (solo relevante para el Padre)

    // --- DATOS DEL EVENTO ---
    public int tipoEvento;
    public long timestamp;
    public String horaFormateada;
    public String titulo;
    public String descripcion;
    public String marcadorMomento;      // Foto del marcador global en ese instante
    public int colorFoco;               // Color Neón (Azul, Rojo, Dorado, Blanco)

    /**
     * Constructor principal
     */
    public EventoBatalla(int tipoEvento, long timestamp, String horaFormateada, String titulo, String descripcion, String marcadorMomento, int colorFoco) {
        this.tipoEvento = tipoEvento;
        this.timestamp = timestamp;
        this.horaFormateada = horaFormateada;
        this.titulo = titulo;
        this.descripcion = descripcion;
        this.marcadorMomento = marcadorMomento;
        this.colorFoco = colorFoco;
    }

    // --- GETTERS Y SETTERS PARA FLUJO DINÁMICO ---

    public boolean isEsHijo() {
        return esHijo;
    }

    public void setEsHijo(boolean esHijo) {
        this.esHijo = esHijo;
    }

    public boolean isExpandido() {
        return expandido;
    }

    public void setExpandido(boolean expandido) {
        this.expandido = expandido;
    }

    /**
     * Helper para clonar o crear eventos rápidos de sistema
     */
    public static EventoBatalla crearBannerEstado(long timestamp, String hora, String marcador, String juego) {
        return new EventoBatalla(
                TIPO_CIERRE_RONDA,
                timestamp,
                hora,
                "ESTADO DE BATALLA",
                "Resumen de " + juego + " (Toca para detalles)",
                marcador,
                android.graphics.Color.WHITE
        );
    }
}