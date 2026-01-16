package com.nodo.tpv.ui.fragments;

import android.content.Context;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.nodo.tpv.R;
import com.nodo.tpv.data.database.AppDatabase;
import com.nodo.tpv.data.entities.Usuario;
import com.nodo.tpv.util.SessionManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FragmentSesion extends Fragment {

    private LinearLayout layoutLogin, layoutComandos;
    private TextInputEditText etUsuario, etPin;
    private TextView tvUsuarioActivo;
    private Button btnLogin, btnSalir;
    private SessionManager sessionManager;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    public interface OnSesionListener {
        void onLoginExitoso(Usuario usuario);
        void onLogout();

        void onComandoAbrirMesa(int idMesa, String tipoJuego);

        // Compatibilidad con llamadas sin parámetros
        void onComandoAbrirMesa();
    }

    private OnSesionListener listener;

    public FragmentSesion() {
        // Required empty public constructor
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof OnSesionListener) {
            listener = (OnSesionListener) context;
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_sesion, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        sessionManager = new SessionManager(requireContext());

        // Referencias UI
        layoutLogin = view.findViewById(R.id.layoutLogin);
        layoutComandos = view.findViewById(R.id.layoutComandos);
        etUsuario = view.findViewById(R.id.etUsuarioLogin);
        etPin = view.findViewById(R.id.etPinLogin);
        tvUsuarioActivo = view.findViewById(R.id.tvUsuarioActivo);

        btnLogin = view.findViewById(R.id.btnLogin);
        btnSalir = view.findViewById(R.id.btnCerrarSesion);

        // 1. Verificar sesión al cargar
        Usuario sesionGuardada = sessionManager.obtenerUsuario();
        if (sesionGuardada != null) {
            activarModoComandos(sesionGuardada);
        } else {
            activarModoLogin();
        }

        // 2. Click Login
        btnLogin.setOnClickListener(v -> procesarLogin());

        // 3. Click Salir (Logout)
        btnSalir.setOnClickListener(v -> {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Cerrar Sesión")
                    .setMessage("¿Deseas finalizar tu turno actual?")
                    .setPositiveButton("Cerrar", (dialog, which) -> {
                        sessionManager.borrarSesion();
                        activarModoLogin();
                        if (listener != null) listener.onLogout();
                    })
                    .setNegativeButton("Cancelar", null).show();
        });
    }

    private void procesarLogin() {
        String u = etUsuario.getText().toString().trim();
        String p = etPin.getText().toString().trim();
        if (!u.isEmpty() && !p.isEmpty()) {
            Usuario user = new Usuario();
            user.idUsuario = 101;
            user.nombreUsuario = u.toUpperCase();
            user.idMesa = 1; // ID de referencia para esta terminal

            executorService.execute(() -> {
                AppDatabase.getInstance(requireContext()).usuarioDao().insertarOActualizar(user);
                sessionManager.guardarUsuario(user);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        activarModoComandos(user);
                        if (listener != null) listener.onLoginExitoso(user);
                    });
                }
            });
        }
    }

    private void activarModoComandos(Usuario usuario) {
        layoutLogin.setVisibility(View.GONE);
        layoutComandos.setVisibility(View.VISIBLE);
        tvUsuarioActivo.setText(usuario.nombreUsuario);
    }

    private void activarModoLogin() {
        layoutLogin.setVisibility(View.VISIBLE);
        layoutComandos.setVisibility(View.GONE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executorService.shutdown();
    }
}