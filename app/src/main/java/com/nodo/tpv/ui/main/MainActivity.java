package com.nodo.tpv.ui.main;

import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;
import androidx.transition.TransitionManager;

import com.nodo.tpv.R;
import com.nodo.tpv.data.entities.Usuario;
import com.nodo.tpv.ui.fragments.FragmentBloqueo;
import com.nodo.tpv.ui.fragments.FragmentCamaraSeguridad;
import com.nodo.tpv.ui.fragments.FragmentEsperaVenta;
import com.nodo.tpv.ui.fragments.FragmentSesion;
import com.nodo.tpv.ui.fragments.ListaClientesFragment;
import com.nodo.tpv.util.SessionManager;
import com.nodo.tpv.viewmodel.ProductoViewModel;

public class MainActivity extends AppCompatActivity implements FragmentSesion.OnSesionListener {

    private ProductoViewModel productoViewModel;
    private SessionManager sessionManager;
    private View splashContainer;
    private View contentLayout;

    private boolean isArenaExpanded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        sessionManager = new SessionManager(this);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_root), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        productoViewModel = new ViewModelProvider(this).get(ProductoViewModel.class);
        productoViewModel.insertarProductosPrueba();

        splashContainer = findViewById(R.id.splash_container);
        contentLayout = findViewById(R.id.content_layout);

        // --- MANEJO DEL BOTÓN ATRÁS (Moderno y sin advertencias) ---
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (getSupportFragmentManager().getBackStackEntryCount() > 0) {
                    getSupportFragmentManager().popBackStack();
                } else {
                    Toast.makeText(MainActivity.this,
                            "Sesión activa. Use el panel lateral para cerrar turno.",
                            Toast.LENGTH_SHORT).show();
                }
            }
        });

        // Splash screen delay
        new Handler().postDelayed(this::revelarAppPrincipal, 2500);
    }

    private void revelarAppPrincipal() {
        AlphaAnimation fadeOut = new AlphaAnimation(1.0f, 0.0f);
        fadeOut.setDuration(500);
        splashContainer.startAnimation(fadeOut);

        fadeOut.setAnimationListener(new android.view.animation.Animation.AnimationListener() {
            @Override
            public void onAnimationEnd(android.view.animation.Animation animation) {
                splashContainer.setVisibility(View.GONE);
                contentLayout.setVisibility(View.VISIBLE);
                inicializarFragmentos();
            }
            @Override public void onAnimationStart(android.view.animation.Animation animation) {}
            @Override public void onAnimationRepeat(android.view.animation.Animation animation) {}
        });
    }

    private void inicializarFragmentos() {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();

        // Panel de comandos lateral (fijo al 30%)
        transaction.replace(R.id.container_right, new FragmentSesion());

        // Panel principal (70%) - Decidimos si mostramos dashboard o pantalla de bloqueo
        if (sessionManager.obtenerUsuario() != null) {
            transaction.replace(R.id.container_fragments, new FragmentEsperaVenta());
        } else {
            transaction.replace(R.id.container_fragments, new FragmentBloqueo());
        }
        transaction.commit();
    }

    // --- CALLBACKS DE FragmentSesion.OnSesionListener ---

    @Override
    public void onLoginExitoso(Usuario usuario) {
        // 1. Forzamos la vista 70/30 (false = no expandir al 100%)
        setExpandirContenedor(false);

        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (isFinishing()) return;

            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            transaction.setCustomAnimations(R.anim.slide_in_right, android.R.anim.fade_out);

            // Limpiar pantallas de bloqueo
            getSupportFragmentManager().popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);

            // 2. Asegurar que el fragmento de sesión esté en su lugar (Derecha)
            transaction.replace(R.id.container_right, new FragmentSesion());

            // 3. Cargar la espera de venta (Izquierda)
            transaction.replace(R.id.container_fragments, new FragmentEsperaVenta());

            transaction.commitAllowingStateLoss();
        }, 150);
    }

    @Override
    public void onLogout() {
        // 1. Limpiar la sesión en el storage
        if (sessionManager != null) {
            sessionManager.borrarSesion();
        }

        // 2. Resetear la pantalla al 50/50 dinámicamente
        setExpandirContenedor(false);

        // 3. Transacción de fragmentos
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.setCustomAnimations(android.R.anim.fade_in, android.R.anim.slide_out_right);

        // Limpiar el historial para que no puedan volver atrás
        getSupportFragmentManager().popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);

        transaction.replace(R.id.container_fragments, new FragmentBloqueo());
        // Aseguramos que el panel de sesión también se resetee si es necesario
        transaction.replace(R.id.container_right, new FragmentSesion());

        transaction.commit();

        Toast.makeText(this, "Turno finalizado", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onComandoAbrirMesa(int idMesa, String tipoJuego) {
        ListaClientesFragment fragment = new ListaClientesFragment();

        // Inyectamos los parámetros de la mesa al fragmento
        Bundle args = new Bundle();
        args.putInt("id_mesa", idMesa);
        args.putString("tipo_juego", tipoJuego);
        fragment.setArguments(args);

        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.setCustomAnimations(
                R.anim.slide_in_right, R.anim.slide_out_left,
                android.R.anim.slide_in_left, android.R.anim.slide_out_right
        );
        transaction.replace(R.id.container_fragments, fragment);
        transaction.addToBackStack(null);
        transaction.commit();
    }

    // Compatibilidad con llamadas sin parámetros
    @Override
    public void onComandoAbrirMesa() {
        onComandoAbrirMesa(1, "POOL"); // Por defecto mesa 1 modo Pool
    }

    // --- GESTIÓN DE INTERFAZ Y EXPANSIÓN (ARENA) ---


    public void setExpandirContenedor(boolean expandir) {
        ConstraintLayout layoutRaiz = findViewById(R.id.content_layout);
        View panelDerecho = findViewById(R.id.container_right);

        if (layoutRaiz == null || panelDerecho == null) return;

        layoutRaiz.post(() -> {
            ConstraintSet set = new ConstraintSet();
            set.clone(layoutRaiz);

            androidx.transition.ChangeBounds transition = new androidx.transition.ChangeBounds();
            transition.setDuration(600);
            transition.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());

            if (expandir) {
                // --- MODO 100% (Pantalla Completa) ---
                panelDerecho.setVisibility(View.GONE);
                set.connect(R.id.container_fragments, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, 0);
            } else {
                // --- MODO DINÁMICO (50/50 o 70/30) ---
                panelDerecho.setVisibility(View.VISIBLE);
                panelDerecho.setAlpha(1.0f);

                // Decidimos el porcentaje según si hay un usuario logueado
                float porcentaje = (sessionManager.obtenerUsuario() != null) ? 0.7f : 0.4f;

                // MOVER EL GUIDELINE DINÁMICAMENTE
                set.setGuidelinePercent(R.id.guideline, porcentaje);

                // Re-conectar todo al guideline
                set.connect(R.id.container_fragments, ConstraintSet.END, R.id.guideline, ConstraintSet.START, 0);
                set.connect(R.id.container_right, ConstraintSet.START, R.id.guideline, ConstraintSet.END, 0);
                set.connect(R.id.container_right, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, 0);
            }

            TransitionManager.beginDelayedTransition(layoutRaiz, transition);
            set.applyTo(layoutRaiz);
        });
    }

    // --- NAVEGACIÓN Y UTILIDADES ---

    public void onComandoCerrarMesa() {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out);
        transaction.replace(R.id.container_fragments, new FragmentEsperaVenta());
        transaction.commit();
    }

    public void cambiarFragmentoPrincipal(Fragment fragment) {
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.container_fragments, fragment)
                .addToBackStack(null)
                .commit();
    }

    /*public void abrirCamaraIntegrada(int idCliente, String alias, String metodo) {
        FragmentCamaraSeguridad fragment = FragmentCamaraSeguridad.newInstance(idCliente, alias, metodo);
        getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left,
                        android.R.anim.slide_in_left, android.R.anim.slide_out_right)
                .replace(R.id.container_fragments, fragment)
                .addToBackStack(null)
                .commit();
    }*/
}