package com.nodo.tpv.util;

import android.content.Context;
import android.content.SharedPreferences;

import com.nodo.tpv.data.entities.Usuario;

public class SessionManager {
    private SharedPreferences prefs;
    private SharedPreferences.Editor editor;
    private static final String PREF_NAME = "OperadorSesion";

    public SessionManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        editor = prefs.edit();
    }

    public void guardarUsuario(Usuario usuario) {
        editor.putInt("id", usuario.idUsuario);
        editor.putString("nombre", usuario.nombreUsuario);
        editor.putString("rol", usuario.rolUsuario);
        editor.putInt("mesa", usuario.idMesa);
        editor.putBoolean("activo", true);
        editor.apply();
    }

    public Usuario obtenerUsuario() {
        if (!prefs.getBoolean("activo", false)) return null;
        Usuario u = new Usuario();
        u.idUsuario = prefs.getInt("id", 0);
        u.nombreUsuario = prefs.getString("nombre", "");
        u.rolUsuario = prefs.getString("rol", "");
        u.idMesa = prefs.getInt("mesa", 0);
        return u;
    }

    public void borrarSesion() {
        editor.clear().apply();
    }
}
