package com.nodo.tpv.util;

import android.content.Context;
import android.content.SharedPreferences;
import com.nodo.tpv.data.entities.Usuario;

/**
 * Gestiona la persistencia de datos de la terminal (Tablet) y
 * la sesión del usuario operativo (Mesero/Cajero).
 */
public class SessionManager {

    private static final String PREF_NAME = "OperadorSesion";

    // Claves para la Terminal (Vínculo QR - Persistente)
    private static final String KEY_TERMINAL_UUID = "terminal_uuid";
    private static final String KEY_EMPRESA_ID = "empresa_id";
    private static final String KEY_IS_ACTIVA = "terminal_activa";

    // Claves para el Usuario (Sesión de Slot - Volátil)
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_USER_NOMBRE = "user_nombre";
    private static final String KEY_USER_ROL = "user_rol";
    private static final String KEY_USER_MESA = "user_mesa";
    private static final String KEY_USER_ACTIVO = "user_activo";

    private final SharedPreferences prefs;

    public SessionManager(Context context) {
        // Context.MODE_PRIVATE asegura que solo esta app pueda leer los datos
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    // --- GESTIÓN DE LA TERMINAL (VINCULACIÓN QR) ---

    /**
     * Guarda la configuración tras escanear el QR y recibir éxito del servidor.
     */
    public void guardarConfiguracionTerminal(String uuid, long empresaId) {
        prefs.edit()
                .putString(KEY_TERMINAL_UUID, uuid)
                .putLong(KEY_EMPRESA_ID, empresaId)
                .putBoolean(KEY_IS_ACTIVA, true)
                .apply(); // apply() es asíncrono y más eficiente para la UI
    }

    /**
     * Retorna true si la tablet ya tiene un vínculo activo con una empresa.
     */
    public boolean estaTerminalVinculada() {
        return prefs.getBoolean(KEY_IS_ACTIVA, false);
    }

    public String getTerminalUuid() {
        return prefs.getString(KEY_TERMINAL_UUID, null);
    }

    public long getEmpresaId() {
        // Retornamos -1L si no hay ID, para que cargarSlots() detecte que no es válido
        return prefs.getLong(KEY_EMPRESA_ID, -1L);
    }

    /**
     * Elimina todos los datos: terminal y usuario. Útil para resetear la tablet.
     */
    public void desvincularTerminalTotalmente() {
        prefs.edit().clear().apply();
    }

    /**
     * Limpia específicamente los datos de la empresa antes de una nueva vinculación.
     */
    public void prepararNuevaVinculacion() {
        prefs.edit()
                .remove(KEY_TERMINAL_UUID)
                .remove(KEY_EMPRESA_ID)
                .remove(KEY_IS_ACTIVA)
                .commit(); // commit() es síncrono para asegurar limpieza inmediata
    }


    // --- GESTIÓN DEL USUARIO (LOGIN POR SLOTS) ---

    /**
     * Guarda el usuario que inició sesión. Estos datos se mantienen hasta el logout.
     */
    public void guardarUsuario(Usuario usuario) {
        if (usuario == null) return;

        prefs.edit()
                .putInt(KEY_USER_ID, usuario.idUsuario)
                .putString(KEY_USER_NOMBRE, usuario.nombreUsuario)
                .putString(KEY_USER_ROL, usuario.rolUsuario)
                .putInt(KEY_USER_MESA, usuario.idMesa)
                .putBoolean(KEY_USER_ACTIVO, true)
                .apply();
    }

    /**
     * Recupera el usuario actual reconstruyendo el objeto desde SharedPreferences.
     */
    public Usuario obtenerUsuario() {
        if (!prefs.getBoolean(KEY_USER_ACTIVO, false)) return null;

        Usuario u = new Usuario();
        u.idUsuario = prefs.getInt(KEY_USER_ID, 0);
        u.nombreUsuario = prefs.getString(KEY_USER_NOMBRE, "Operador");
        u.rolUsuario = prefs.getString(KEY_USER_ROL, "");
        u.idMesa = prefs.getInt(KEY_USER_MESA, 0);
        return u;
    }

    /**
     * Verifica si hay una sesión de usuario abierta actualmente.
     */
    public boolean haySesionAbierta() {
        return prefs.getBoolean(KEY_USER_ACTIVO, false);
    }

    /**
     * Cierra la sesión del trabajador pero MANTIENE la terminal vinculada a la empresa.
     */
    public void borrarSesion() {
        prefs.edit()
                .remove(KEY_USER_ID)
                .remove(KEY_USER_NOMBRE)
                .remove(KEY_USER_ROL)
                .remove(KEY_USER_MESA)
                .remove(KEY_USER_ACTIVO)
                .apply();
    }
}