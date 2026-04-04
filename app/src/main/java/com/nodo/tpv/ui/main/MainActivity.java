package com.nodo.tpv.ui.main;

import android.content.Context;
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
import androidx.work.BackoffPolicy;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;

import com.nodo.tpv.R;
import com.nodo.tpv.data.database.AppDatabase;
import com.nodo.tpv.data.entities.Usuario;
import com.nodo.tpv.data.sync.SessionSyncWorker;
import com.nodo.tpv.data.sync.StockSyncWorker;
import com.nodo.tpv.ui.fragments.FragmentBloqueo;
import com.nodo.tpv.ui.fragments.FragmentEsperaVenta;
import com.nodo.tpv.ui.fragments.FragmentSesion;
import com.nodo.tpv.ui.fragments.ListaClientesFragment;
import com.nodo.tpv.util.SessionManager;
import com.nodo.tpv.viewmodel.ArenaViewModel;
import com.nodo.tpv.viewmodel.PedidoViewModel;
import com.nodo.tpv.viewmodel.ProductoViewModel;
import com.nodo.tpv.viewmodel.UsuarioSlotViewModel;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity implements FragmentSesion.OnSesionListener {

    // --- VIEWMODELS ---
    private ProductoViewModel productoViewModel;
    private PedidoViewModel pedidoViewModel;
    private ArenaViewModel arenaViewModel;
    private UsuarioSlotViewModel usuarioSlotViewModel;

    private SessionManager sessionManager;
    private View splashContainer;
    private View contentLayout;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        sessionManager = new SessionManager(this);
        splashContainer = findViewById(R.id.splash_container);
        contentLayout = findViewById(R.id.content_layout);

        // Ajuste de paddings para barras de sistema
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main_root), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // 1. Inicialización de TODOS los ViewModels
        productoViewModel = new ViewModelProvider(this).get(ProductoViewModel.class);
        pedidoViewModel = new ViewModelProvider(this).get(PedidoViewModel.class);
        arenaViewModel = new ViewModelProvider(this).get(ArenaViewModel.class);
        usuarioSlotViewModel = new ViewModelProvider(this).get(UsuarioSlotViewModel.class);

        // 2. 🔥 OBSERVADORES DE SINCRONIZACIÓN INTELIGENTE

        // A1. Sincronización cuando se genera una venta en el Cajero (Pedidos manuales)
        pedidoViewModel.getEventoVentaExitosa().observe(this, huboVenta -> {
            if (huboVenta != null && huboVenta) {
                programarSincronizacionStock(this);
                pedidoViewModel.resetEventoVenta();
            }
        });

        // A2. Sincronización cuando se genera una deuda en la Arena (Bolas, Puntos)
        arenaViewModel.getEventoVentaExitosa().observe(this, huboVenta -> {
            if (huboVenta != null && huboVenta) {
                programarSincronizacionStock(this);
                arenaViewModel.resetEventoVenta();
            }
        });

        // Manejo del botón atrás
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
        transaction.replace(R.id.container_right, new FragmentSesion());

        if (sessionManager.obtenerUsuario() != null) {
            transaction.replace(R.id.container_fragments, new FragmentEsperaVenta());
        } else {
            transaction.replace(R.id.container_fragments, new FragmentBloqueo());
        }
        transaction.commit();

        programarSincronizacionStock(this);
        programarSincronizacionSesion(this);
    }

    // --- CALLBACKS DE FragmentSesion.OnSesionListener ---

    @Override
    public void onLoginExitoso(Usuario usuario) {
        setExpandirContenedor(false);
        new Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (isFinishing()) return;
            FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
            transaction.setCustomAnimations(R.anim.slide_in_right, android.R.anim.fade_out);
            getSupportFragmentManager().popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
            transaction.replace(R.id.container_right, new FragmentSesion());
            transaction.replace(R.id.container_fragments, new FragmentEsperaVenta());
            transaction.commitAllowingStateLoss();
        }, 150);
    }

    @Override
    public void onLogout() {
        if (sessionManager != null) sessionManager.borrarSesion();

        setExpandirContenedor(false);

        java.util.concurrent.Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase.getInstance(this).mesaDao().eliminarTodasLasMesas();
        });

        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.setCustomAnimations(android.R.anim.fade_in, android.R.anim.slide_out_right);
        getSupportFragmentManager().popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        transaction.replace(R.id.container_fragments, new FragmentBloqueo());
        transaction.replace(R.id.container_right, new FragmentSesion());
        transaction.commit();

        Toast.makeText(this, "Turno finalizado y panel reiniciado", Toast.LENGTH_SHORT).show();
    }

    @Override
    public void onComandoAbrirMesa(int idMesa, String tipoJuego) {
        executorService.execute(() -> {
            AppDatabase db = AppDatabase.getInstance(this);
            com.nodo.tpv.data.entities.ActividadOperativaLocal p = new com.nodo.tpv.data.entities.ActividadOperativaLocal();
            p.eventoId = java.util.UUID.randomUUID().toString();
            p.tipoEvento = "MESA_ABIERTA";
            p.fechaDispositivo = System.currentTimeMillis();
            p.estadoSync = "PENDIENTE";
            p.detallesJson = "{ \"idMesa\": " + idMesa + ", \"tipoJuego\": \"" + tipoJuego + "\" }";

            db.actividadOperativaLocalDao().insertar(p);
            programarSincronizacionOperatividad(this); // Asegúrate de tener este método similar a programarSincronizacionStock
        });

        ListaClientesFragment fragment = new ListaClientesFragment();
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

    @Override
    public void onComandoAbrirMesa() {
        onComandoAbrirMesa(1, "POOL");
    }

    // --- GESTIÓN DE INTERFAZ ---

    public void setExpandirContenedor(boolean expandir) {
        ConstraintLayout layoutRaiz = findViewById(R.id.content_layout);
        View panelDerecho = findViewById(R.id.container_right);
        if (layoutRaiz == null || panelDerecho == null || isFinishing()) return;

        layoutRaiz.post(() -> {
            ConstraintSet set = new ConstraintSet();
            set.clone(layoutRaiz);
            androidx.transition.ChangeBounds transition = new androidx.transition.ChangeBounds();
            transition.setDuration(600);

            if (expandir) {
                panelDerecho.setVisibility(View.GONE);
                set.connect(R.id.container_fragments, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END, 0);
            } else {
                panelDerecho.setVisibility(View.VISIBLE);
                float porcentaje = (sessionManager.obtenerUsuario() != null) ? 0.7f : 0.4f;
                set.setGuidelinePercent(R.id.guideline, porcentaje);
                set.connect(R.id.container_fragments, ConstraintSet.END, R.id.guideline, ConstraintSet.START, 0);
                set.connect(R.id.container_right, ConstraintSet.START, R.id.guideline, ConstraintSet.END, 0);
            }
            TransitionManager.beginDelayedTransition(layoutRaiz, transition);
            set.applyTo(layoutRaiz);
        });
    }

    // --- MÓDULOS DE SINCRONIZACIÓN (WorkManager) ---

    public void programarSincronizacionStock(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        OneTimeWorkRequest syncRequest = new OneTimeWorkRequest.Builder(StockSyncWorker.class)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
                .build();

        WorkManager.getInstance(context).enqueueUniqueWork(
                "sync_stock_unique",
                ExistingWorkPolicy.KEEP,
                syncRequest
        );
    }

    public void programarSincronizacionSesion(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        OneTimeWorkRequest syncRequest = new OneTimeWorkRequest.Builder(SessionSyncWorker.class)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
                .build();

        WorkManager.getInstance(context).enqueueUniqueWork(
                "sync_session_unique",
                ExistingWorkPolicy.KEEP,
                syncRequest
        );
    }

    public void programarSincronizacionOperatividad(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        OneTimeWorkRequest syncRequest = new OneTimeWorkRequest.Builder(com.nodo.tpv.data.sync.OperatividadSyncWorker.class)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, WorkRequest.MIN_BACKOFF_MILLIS, TimeUnit.MILLISECONDS)
                .build();

        WorkManager.getInstance(context).enqueueUniqueWork(
                "sync_operatividad_unique",
                ExistingWorkPolicy.KEEP,
                syncRequest
        );
    }
}