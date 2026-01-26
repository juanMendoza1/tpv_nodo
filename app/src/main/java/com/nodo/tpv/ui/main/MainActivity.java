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
        // 1. Iniciamos la expansión primero (con la animación de 600ms que definimos)
        setExpandirContenedor(true);

        // 2. Usamos un pequeño delay para que el fragmento aparezca
        // mientras la pantalla ya se está estirando. ¡Se ve mucho más pro!
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {

            if (isFinishing()) return;

            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();

            // Usamos animaciones que den sensación de expansión lateral
            transaction.setCustomAnimations(
                    R.anim.slide_in_right, // El nuevo fragment entra desde la derecha
                    android.R.anim.fade_out
            );

            // Limpiamos historial
            getSupportFragmentManager().popBackStack(null,
                    androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE);

            transaction.replace(R.id.container_fragments, new FragmentEsperaVenta());
            transaction.commitAllowingStateLoss();

            Toast.makeText(this, "Bienvenido, " + usuario.nombreUsuario, Toast.LENGTH_SHORT).show();

        }, 150); // Delay corto para sincronizar con setExpandirContenedor
    }

    @Override
    public void onLogout() {
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.setCustomAnimations(android.R.anim.fade_in, android.R.anim.slide_out_right);

        getSupportFragmentManager().popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE);

        transaction.replace(R.id.container_fragments, new FragmentBloqueo());
        transaction.commit();
    }

    /**
     * 🔥 Función Principal de Apertura de Mesa
     * Recibe la modalidad elegida en el diálogo del FragmentSesion
     */
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

            // Preparamos la transición suave
            androidx.transition.ChangeBounds transition = new androidx.transition.ChangeBounds();
            transition.setDuration(600); // 600ms es el "punto dulce" para tablets
            transition.setInterpolator(new android.view.animation.AnticipateOvershootInterpolator(1.0f));

            if (expandir) {
                // --- ANIMACIÓN HACIA 100% ---

                // 1. Animamos la opacidad del panel antes de ocultarlo
                panelDerecho.animate().alpha(0f).setDuration(300).withEndAction(() -> {
                    panelDerecho.setVisibility(View.GONE);
                }).start();

                // 2. Rompemos la guía y pegamos al borde derecho
                set.clear(R.id.container_fragments, ConstraintSet.END);
                set.connect(R.id.container_fragments, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, 0);

            } else {
                // --- ANIMACIÓN HACIA 70/30 ---

                panelDerecho.setVisibility(View.VISIBLE);
                panelDerecho.setAlpha(0f);
                panelDerecho.animate().alpha(1f).setDuration(600).start();

                // Restauramos la conexión al Guideline
                set.clear(R.id.container_fragments, ConstraintSet.END);
                set.connect(R.id.container_fragments, ConstraintSet.END, R.id.guideline, ConstraintSet.START, 0);
            }

            // Ejecutamos la animación de los layouts
            androidx.transition.TransitionManager.beginDelayedTransition(layoutRaiz, transition);
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