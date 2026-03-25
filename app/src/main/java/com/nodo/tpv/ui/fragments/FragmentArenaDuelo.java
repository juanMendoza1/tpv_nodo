package com.nodo.tpv.ui.fragments;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.airbnb.lottie.LottieAnimationView;
import com.google.android.flexbox.FlexboxLayout;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.imageview.ShapeableImageView;
import com.nodo.tpv.R;
import com.nodo.tpv.adapters.LogBatallaAdapter;
import com.nodo.tpv.data.database.AppDatabase;
import com.nodo.tpv.data.entities.BolaAnotada;
import com.nodo.tpv.data.entities.Cliente;
import com.nodo.tpv.data.entities.Producto;
import com.nodo.tpv.ui.main.MainActivity;
import com.nodo.tpv.viewmodel.ArenaViewModel;
import com.nodo.tpv.viewmodel.PedidoViewModel;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

// --- NUEVAS IMPORTACIONES PARA WEBVIEW (PLAN B - MJPEG) ---
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

// LIBRERÍAS DE ANIMACIÓN DE LAYOUT
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.transition.TransitionManager;

public class FragmentArenaDuelo extends Fragment {

    // --- ESTADOS PARA MODO VAR (VIDEO STREAMING) ---
    private boolean isVarActive = false;

    // Componentes de Video (WEBVIEW para MJPEG)
    private WebView webViewCamara;

    // URL DE LA CÁMARA (NODO CAM - Plan B)
    private String cameraUrl = "http://192.168.1.2:8080/";

    // Vistas de Paneles Deslizables
    private View panelHistorial, panelConfig, panelDespacho;
    private View layoutContenidoArena, panelAcciones;
    private RecyclerView rvHistorialLateral, rvDespachoLateral;

    // Estados de Interfaz
    private boolean historialVisible = false;
    private boolean configVisible = false;
    private boolean despachoVisible = false;
    private boolean hayPendientesBloqueantes = false;
    private boolean pantallaExpandida = true;

    // --- LOS NUEVOS VIEWMODELS ---
    private ArenaViewModel arenaViewModel;
    private PedidoViewModel pedidoViewModel;

    private String tipoJuegoMesa;
    private int idMesaActual;
    private final String PIN_MAESTRO = "1234";

    // UI Dinámica Arena
    private TextView tvBadgePendientes, tvReglaActiva, tvInfoMunicion;
    private LottieAnimationView lottieCelebration;
    private LinearLayout containerMarcadoresDinamicos;
    private FlexboxLayout containerGuerrerosDinamicos;

    private com.google.android.material.switchmaterial.SwitchMaterial switchPin;
    private MaterialButton btnReglaGanador, btnReglaTodos, btnReglaUltimo;

    private List<Integer> equiposSalvadosEnRonda = new ArrayList<>();
    private String reglaActualSync = "GANADOR_SALVA";
    private List<Producto> municionActual = new ArrayList<>();

    private final java.util.Random random = new java.util.Random();

    private View scrollMarcadores;

    public static FragmentArenaDuelo newInstance(List<Cliente> azul, List<Cliente> rojo, String tipoJuego, int idMesa) {
        FragmentArenaDuelo f = new FragmentArenaDuelo();
        Bundle args = new Bundle();
        args.putString("tipo", tipoJuego);
        args.putInt("id_mesa", idMesa);
        f.setArguments(args);
        return f;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            tipoJuegoMesa = getArguments().getString("tipo");
            idMesaActual = getArguments().getInt("id_mesa");
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_arena_duelo_dinamico, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if (requireActivity() instanceof MainActivity) {
            ((MainActivity) requireActivity()).setExpandirContenedor(true);
        }

        // INSTANCIAR LOS DOS VIEWMODELS
        arenaViewModel = new ViewModelProvider(requireActivity()).get(ArenaViewModel.class);
        pedidoViewModel = new ViewModelProvider(requireActivity()).get(PedidoViewModel.class);

        scrollMarcadores = view.findViewById(R.id.scrollMarcadores);

        // 1. VINCULACIÓN DE ESTRUCTURA Y VIDEO
        layoutContenidoArena = view.findViewById(R.id.layoutContenidoArena);
        panelAcciones = view.findViewById(R.id.panelAccionesLateral);
        webViewCamara = view.findViewById(R.id.webViewCamara);
        configurarWebView();

        // 2. VINCULACIÓN DE PANELES
        panelHistorial = view.findViewById(R.id.panelHistorialDeslizable);
        panelConfig = view.findViewById(R.id.panelConfigDeslizable);
        panelDespacho = view.findViewById(R.id.panelDespachoDeslizable);
        rvHistorialLateral = view.findViewById(R.id.rvHistorialLateral);
        rvDespachoLateral = view.findViewById(R.id.rvDespachoLateral);

        // 3. VINCULACIÓN DE INFORMACIÓN
        tvBadgePendientes = view.findViewById(R.id.tvBadgePendientes);
        tvReglaActiva = view.findViewById(R.id.tvReglaActiva);
        tvInfoMunicion = view.findViewById(R.id.tvProductoEnJuego);
        lottieCelebration = view.findViewById(R.id.lottieCelebration);
        containerMarcadoresDinamicos = view.findViewById(R.id.containerMarcadoresDinamicos);
        containerGuerrerosDinamicos = view.findViewById(R.id.containerGuerrerosDinamicos);

        // 4. CONFIGURACIÓN
        switchPin = view.findViewById(R.id.switchRequierePin);
        btnReglaGanador = view.findViewById(R.id.btnReglaGanador);
        btnReglaTodos = view.findViewById(R.id.btnReglaTodos);
        btnReglaUltimo = view.findViewById(R.id.btnReglaUltimo);

        // 5. EVENTOS
        view.findViewById(R.id.btnVerPendientes).setOnClickListener(v -> toggleDespacho(true));
        view.findViewById(R.id.btnCerrarDespacho).setOnClickListener(v -> toggleDespacho(false));
        view.findViewById(R.id.btnEntregarTodoLateral).setOnClickListener(v -> {
            com.nodo.tpv.util.SessionManager session = new com.nodo.tpv.util.SessionManager(requireContext());
            String loginOperativo = session.obtenerUsuario().login;
            int idOperativo = session.obtenerUsuario().idUsuario;

            pedidoViewModel.despacharTodoLaMesa(idMesaActual, idOperativo, loginOperativo);
            toggleDespacho(false);
            Toast.makeText(getContext(), "Despachando productos...", Toast.LENGTH_SHORT).show();
        });

        view.findViewById(R.id.btnHistorialDuelo).setOnClickListener(v -> toggleHistorial(true));
        view.findViewById(R.id.btnCerrarHistorial).setOnClickListener(v -> toggleHistorial(false));
        view.findViewById(R.id.btnConfigReglas).setOnClickListener(v -> toggleConfig(true));
        view.findViewById(R.id.btnCerrarConfig).setOnClickListener(v -> toggleConfig(false));

        view.findViewById(R.id.fabSeleccionarMunicion).setOnClickListener(v -> abrirCatalogo());
        view.findViewById(R.id.btnFinalizarDuelo).setOnClickListener(v -> mostrarResumenFinalBatalla());

        // --- BOTÓN VAR (ACTIVACIÓN DE STREAMING) ---
        view.findViewById(R.id.btnVAR).setOnClickListener(this::toggleModoVAR);

        btnReglaGanador.setOnClickListener(v -> arenaViewModel.actualizarReglaDuelo("GANADOR_SALVA"));
        btnReglaTodos.setOnClickListener(v -> arenaViewModel.actualizarReglaDuelo("TODOS_PAGAN"));
        btnReglaUltimo.setOnClickListener(v -> arenaViewModel.actualizarReglaDuelo("ULTIMO_PAGA"));

        if (switchPin != null) {
            switchPin.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (buttonView.isPressed()) arenaViewModel.actualizarSeguridadPinDuelo(isChecked);
            });
        }

        MaterialButton btnExpandir = view.findViewById(R.id.btnExpandirArena);
        btnExpandir.setOnClickListener(v -> {
            pantallaExpandida = !pantallaExpandida;
            if (requireActivity() instanceof MainActivity) {
                ((MainActivity) requireActivity()).setExpandirContenedor(pantallaExpandida);
                btnExpandir.setIconResource(pantallaExpandida ? R.drawable.ic_fullscreen_exit : R.drawable.ic_fullscreen);
            }
        });

        // 6. OBSERVADORES
        arenaViewModel.recuperarDueloActivo(idMesaActual);

        arenaViewModel.getReglaCobroDuelo().observe(getViewLifecycleOwner(), regla -> {
            if (regla != null) {
                this.reglaActualSync = regla;
                actualizarBotonesReglaUI(regla);
                tvReglaActiva.setText("REGLA: " + regla.replace("_", " "));
            }
        });

        pedidoViewModel.observarConteoPendientesMesa(idMesaActual).observe(getViewLifecycleOwner(), count -> {
            hayPendientesBloqueantes = (count != null && count > 0);
            tvBadgePendientes.setVisibility(hayPendientesBloqueantes ? View.VISIBLE : View.GONE);
            tvBadgePendientes.setText(String.valueOf(count));
            actualizarEstadoVisualMarcadores();
        });

        arenaViewModel.getListaApuestaEntregada().observe(getViewLifecycleOwner(), this::actualizarTextoBolsa);

        arenaViewModel.getMapaColoresDuelo().observe(getViewLifecycleOwner(), mapa -> {
            if (mapa != null && !mapa.isEmpty()) generarInterfazDinamica(mapa);
        });

        arenaViewModel.getScoresEquipos().observe(getViewLifecycleOwner(), scores -> vincularScoresExistentes());
        arenaViewModel.getDbTrigger().observe(getViewLifecycleOwner(), t -> vincularScoresExistentes());
    }

    private void configurarWebView() {
        if (webViewCamara != null) {
            WebSettings settings = webViewCamara.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setSupportZoom(true);
            settings.setBuiltInZoomControls(true);
            settings.setDisplayZoomControls(false);
            settings.setLoadWithOverviewMode(true);
            settings.setUseWideViewPort(true);

            webViewCamara.setInitialScale(100);
            webViewCamara.setBackgroundColor(Color.BLACK);
            webViewCamara.setWebViewClient(new WebViewClient());
        }
    }

    private void toggleModoVAR(View view) {
        isVarActive = !isVarActive;

        ConstraintLayout root = (ConstraintLayout) getView();
        if (root == null) return;

        ConstraintSet set = new ConstraintSet();
        set.clone(root);

        if (isVarActive) {
            webViewCamara.setVisibility(View.VISIBLE);
            if (webViewCamara != null) {
                webViewCamara.loadUrl(cameraUrl + "?t=" + System.currentTimeMillis());
            }
            set.setGuidelinePercent(R.id.guidelineVAR, 0.45f);

            if (scrollMarcadores != null) {
                scrollMarcadores.setVisibility(View.GONE);
            }
        } else {
            if (webViewCamara != null) {
                webViewCamara.loadUrl("about:blank");
                webViewCamara.setVisibility(View.GONE);
            }
            set.setGuidelinePercent(R.id.guidelineVAR, 0.0f);

            if (scrollMarcadores != null) {
                scrollMarcadores.setVisibility(View.VISIBLE);
            }
        }

        TransitionManager.beginDelayedTransition(root);
        set.applyTo(root);
    }

    @Override
    public void onStop() {
        super.onStop();
        if (webViewCamara != null) {
            webViewCamara.loadUrl("about:blank");
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (isVarActive && webViewCamara != null) {
            webViewCamara.loadUrl(cameraUrl + "?t=" + System.currentTimeMillis());
        }
    }

    // --- LÓGICA DE JUEGO Y MARCADORES ---

    private void validarYProcesarPunto(int colorEquipo) {
        if (hayPendientesBloqueantes) {
            Toast.makeText(getContext(), "Despacha la munición pendiente ⏳", Toast.LENGTH_SHORT).show();
            toggleDespacho(true);
            return;
        }

        if (municionActual == null || municionActual.isEmpty()) {
            Toast.makeText(getContext(), "No hay munición en juego", Toast.LENGTH_SHORT).show();
            return;
        }

        if (switchPin != null && switchPin.isChecked()) {
            solicitarPinYRegistrar(colorEquipo);
        } else {
            ejecutarImpactoDirecto(colorEquipo);
        }
    }

    private void ejecutarImpactoDirecto(int colorEquipo) {
        requireView().performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
        arenaViewModel.aplicarDanioMultiequipo(colorEquipo);
        dispararCelebracion();
        estallarYLimpiarMesa();
        Toast.makeText(getContext(), "¡Punto registrado!", Toast.LENGTH_SHORT).show();
    }

    private void solicitarPinYRegistrar(int colorEquipo) {
        View vPin = getLayoutInflater().inflate(R.layout.dialog_pin_seguridad, null);
        EditText etPin = vPin.findViewById(R.id.etPin);
        new MaterialAlertDialogBuilder(requireContext(), R.style.MaterialAlertDialog_Garena)
                .setTitle("CONFIRMAR PUNTO")
                .setView(vPin)
                .setPositiveButton("OK", (d, w) -> {
                    if (PIN_MAESTRO.equals(etPin.getText().toString())) {
                        arenaViewModel.aplicarDanioMultiequipo(colorEquipo);
                        dispararCelebracion();
                        estallarYLimpiarMesa();
                    } else {
                        Toast.makeText(getContext(), "PIN Incorrecto", Toast.LENGTH_SHORT).show();
                    }
                }).show();
    }

    private void vincularScoresExistentes() {
        Map<Integer, Integer> scores = arenaViewModel.getScoresEquipos().getValue();
        if (scores == null) return;
        for (int i = 0; i < containerMarcadoresDinamicos.getChildCount(); i++) {
            View v = containerMarcadoresDinamicos.getChildAt(i);
            if (v.getTag() instanceof Integer && scores.containsKey((int)v.getTag())) {
                ((TextView) v.findViewById(R.id.tvScoreDinamico)).setText(String.valueOf(scores.get((int)v.getTag())));
            }
        }
    }

    private void actualizarBotonesReglaUI(String reglaActiva) {
        int colorActivo = Color.parseColor("#FFD600");
        int colorInactivo = Color.parseColor("#4DFFFFFF");
        btnReglaGanador.setStrokeColor(ColorStateList.valueOf(reglaActiva.equals("GANADOR_SALVA") ? colorActivo : colorInactivo));
        btnReglaTodos.setStrokeColor(ColorStateList.valueOf(reglaActiva.equals("TODOS_PAGAN") ? colorActivo : colorInactivo));
        btnReglaUltimo.setStrokeColor(ColorStateList.valueOf(reglaActiva.equals("ULTIMO_PAGA") ? colorActivo : colorInactivo));
    }

    private void toggleDespacho(boolean mostrar) {
        if (despachoVisible == mostrar) return;
        if (mostrar) {
            com.nodo.tpv.util.SessionManager session = new com.nodo.tpv.util.SessionManager(requireContext());
            com.nodo.tpv.data.entities.Usuario user = session.obtenerUsuario();

            final int idOp = (user != null) ? user.idUsuario : 0;
            final String loginOp = (user != null) ? user.login : "desconocido";

            pedidoViewModel.obtenerSoloPendientesMesa(idMesaActual).observe(getViewLifecycleOwner(), lista -> {
                if (lista != null) {
                    rvDespachoLateral.setLayoutManager(new LinearLayoutManager(getContext()));
                    rvDespachoLateral.setAdapter(new LogBatallaAdapter(lista, item -> {
                        pedidoViewModel.marcarComoEntregado(item.idDetalle, idOp, loginOp);
                    }));
                    if (lista.isEmpty() && despachoVisible) toggleDespacho(false);
                }
            });
        }
        animarCapaLateral(panelDespacho, mostrar);
        despachoVisible = mostrar;
    }

    private void toggleHistorial(boolean mostrar) {
        if (historialVisible == mostrar) return;
        if (mostrar) {
            // Utilizamos obtenerDetalleDeudaRegistrada del PedidoViewModel para ver el historial
            pedidoViewModel.obtenerDetalleDeudaRegistrada(idMesaActual).observe(getViewLifecycleOwner(), lista -> {
                if (lista != null) {
                    rvHistorialLateral.setLayoutManager(new LinearLayoutManager(getContext()));
                    rvHistorialLateral.setAdapter(new LogBatallaAdapter(lista, item -> {}));
                }
            });
        }
        animarCapaLateral(panelHistorial, mostrar);
        historialVisible = mostrar;
    }

    private void toggleConfig(boolean mostrar) {
        if (configVisible == mostrar) return;
        animarCapaLateral(panelConfig, mostrar);
        configVisible = mostrar;
    }

    private void animarCapaLateral(View panel, boolean mostrar) {
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        if (mostrar) {
            panel.setVisibility(View.VISIBLE);
            panel.setTranslationX(screenWidth);
            panelAcciones.animate().alpha(0f).scaleX(0f).scaleY(0f).setDuration(200).start();
            layoutContenidoArena.animate().alpha(0.1f).scaleX(0.9f).setDuration(400).start();
            panel.animate().translationX(0f).setDuration(400).setInterpolator(new DecelerateInterpolator()).start();
        } else {
            panel.animate().translationX(screenWidth).setDuration(400).withEndAction(() -> panel.setVisibility(View.GONE)).start();
            layoutContenidoArena.animate().alpha(1f).scaleX(1f).setDuration(400).start();
            panelAcciones.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(300).start();
        }
    }

    private void actualizarTextoBolsa(List<Producto> productos) {
        this.municionActual = productos;

        BigDecimal total = BigDecimal.ZERO;
        if (productos != null) {
            for (Producto p : productos) {
                // CORRECCIÓN: Acceso directo a la variable precioProducto
                total = total.add(p.precioProducto);
            }
        }

        if (tvInfoMunicion != null) {
            tvInfoMunicion.setText(NumberFormat.getCurrencyInstance(new Locale("es", "CO")).format(total));
        }
    }

    private String getNombreColor(int color) {
        if (color == Color.parseColor("#00E5FF")) return "AZUL";
        if (color == Color.parseColor("#FF1744")) return "ROJO";
        if (color == Color.parseColor("#FFD54F")) return "AMAR.";
        if (color == Color.parseColor("#4CAF50")) return "VERDE";
        if (color == Color.parseColor("#AA00FF")) return "MORADO";
        return "EQ";
    }

    private void dispararCelebracion() {
        if (lottieCelebration != null) {
            lottieCelebration.setVisibility(View.VISIBLE);
            lottieCelebration.playAnimation();
            lottieCelebration.addAnimatorListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator animation) { lottieCelebration.setVisibility(View.GONE); }
            });
        }
    }

    private void abrirCatalogo() {
        CatalogoProductosFragment fragment = CatalogoProductosFragment.newInstance(0, idMesaActual);
        getParentFragmentManager().beginTransaction()
                .setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left, android.R.anim.slide_in_left, android.R.anim.slide_out_right)
                .replace(R.id.container_fragments, fragment).addToBackStack(null).commit();
    }

    private void mostrarResumenFinalBatalla() {
        new MaterialAlertDialogBuilder(requireContext(), R.style.MaterialAlertDialog_Garena)
                .setTitle("FINALIZAR BATALLA")
                .setPositiveButton("SÍ", (d, w) -> {
                    arenaViewModel.finalizarDueloCompleto(idMesaActual, "POOL");
                    getParentFragmentManager().popBackStack();
                }).show();
    }

    private void actualizarEstadoVisualMarcadores() {
        if (containerMarcadoresDinamicos == null) return;
        float opacidad = hayPendientesBloqueantes ? 0.4f : 1.0f;
        for (int i = 0; i < containerMarcadoresDinamicos.getChildCount(); i++) {
            View v = containerMarcadoresDinamicos.getChildAt(i);
            v.setAlpha(opacidad);
            v.setEnabled(!hayPendientesBloqueantes);
        }
    }

    private void gestionarReglaUltimoPaga(int colorEquipoTocado) {
        if (equiposSalvadosEnRonda.contains(colorEquipoTocado)) return;

        Map<Integer, Integer> mapa = arenaViewModel.getMapaColoresDuelo().getValue();
        if (mapa == null) return;

        java.util.Set<Integer> coloresUnicos = new java.util.HashSet<>(mapa.values());
        int totalEquipos = coloresUnicos.size();

        equiposSalvadosEnRonda.add(colorEquipoTocado);

        for (int i = 0; i < containerMarcadoresDinamicos.getChildCount(); i++) {
            View v = containerMarcadoresDinamicos.getChildAt(i);
            if (v.getTag() instanceof Integer && (int) v.getTag() == colorEquipoTocado) {
                animarEquipoSalvado(v);
                v.setEnabled(false);
                break;
            }
        }

        if (equiposSalvadosEnRonda.size() == 1) {
            Toast.makeText(getContext(), "GANADOR: " + getNombreColor(colorEquipoTocado) + " 🏆", Toast.LENGTH_SHORT).show();
        }

        if (equiposSalvadosEnRonda.size() == totalEquipos - 1) {
            int colorPerdedorFinal = -1;
            for (Integer c : coloresUnicos) {
                if (!equiposSalvadosEnRonda.contains(c)) {
                    colorPerdedorFinal = c;
                    break;
                }
            }

            if (colorPerdedorFinal != -1) {
                final int perdedor = colorPerdedorFinal;
                final int ganador = equiposSalvadosEnRonda.get(0);

                animarPerdedorFinal(perdedor);

                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    Toast.makeText(getContext(), "¡Ronda finalizada! Paga: " + getNombreColor(perdedor), Toast.LENGTH_LONG).show();
                    aplicarCierreRondaUltimoPaga(ganador, perdedor);
                }, 1000);
            }
        } else {
            int faltan = (totalEquipos - 1) - equiposSalvadosEnRonda.size();
            Toast.makeText(getContext(), getNombreColor(colorEquipoTocado) + " a salvo. Faltan " + faltan, Toast.LENGTH_SHORT).show();
        }
    }

    private void aplicarCierreRondaUltimoPaga(int colorGanador, int colorPerdedor) {
        arenaViewModel.aplicarDanioUltimoPaga(colorGanador, colorPerdedor);
        dispararCelebracion();
        estallarYLimpiarMesa();
        equiposSalvadosEnRonda.clear();
        animarRestauracionUI();
    }

    private void animarEquipoSalvado(View view) {
        view.animate()
                .scaleX(1.1f)
                .scaleY(1.1f)
                .alpha(0.3f)
                .setDuration(400)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    view.setScaleX(0.95f);
                    view.setScaleY(0.95f);
                })
                .start();
    }

    private void animarPerdedorFinal(int colorPerdedor) {
        if (containerMarcadoresDinamicos == null) return;

        for (int i = 0; i < containerMarcadoresDinamicos.getChildCount(); i++) {
            View v = containerMarcadoresDinamicos.getChildAt(i);
            if (v.getTag() instanceof Integer && (int) v.getTag() == colorPerdedor) {
                ObjectAnimator scaleX = ObjectAnimator.ofFloat(v, "scaleX", 1f, 1.15f, 1f);
                ObjectAnimator scaleY = ObjectAnimator.ofFloat(v, "scaleY", 1f, 1.15f, 1f);
                scaleX.setRepeatCount(3);
                scaleY.setRepeatCount(3);
                scaleX.setDuration(200);
                scaleY.setDuration(200);

                if (v instanceof MaterialCardView) {
                    ((MaterialCardView) v).setStrokeColor(ColorStateList.valueOf(Color.RED));
                    ((MaterialCardView) v).setStrokeWidth(12);
                }

                scaleX.start();
                scaleY.start();
                break;
            }
        }
    }

    private void animarRestauracionUI() {
        if (containerMarcadoresDinamicos == null) return;

        for (int i = 0; i < containerMarcadoresDinamicos.getChildCount(); i++) {
            View v = containerMarcadoresDinamicos.getChildAt(i);

            v.animate()
                    .alpha(1.0f)
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setStartDelay(i * 100L)
                    .setDuration(400)
                    .start();

            if (v instanceof MaterialCardView && v.getTag() instanceof Integer) {
                ((MaterialCardView) v).setStrokeColor(ColorStateList.valueOf((int)v.getTag()));
                ((MaterialCardView) v).setStrokeWidth(6);
            }
            v.setEnabled(true);
        }
    }

    private void generarInterfazDinamica(Map<Integer, Integer> mapa) {
        containerGuerrerosDinamicos.removeAllViews();
        containerMarcadoresDinamicos.removeAllViews();
        Map<Integer, List<Integer>> equipos = new HashMap<>();
        for (Map.Entry<Integer, Integer> entry : mapa.entrySet()) {
            if (!equipos.containsKey(entry.getValue())) equipos.put(entry.getValue(), new ArrayList<>());
            equipos.get(entry.getValue()).add(entry.getKey());
        }

        for (Integer color : equipos.keySet()) {
            View vMarcador = getLayoutInflater().inflate(R.layout.item_marcador_arena_equipo, containerMarcadoresDinamicos, false);
            vMarcador.setTag(color);
            ((MaterialCardView) vMarcador).setStrokeColor(ColorStateList.valueOf(color));
            ((TextView) vMarcador.findViewById(R.id.tvLabelEquipoDinamico)).setText(getNombreColor(color));

            LinearLayout containerMalas = vMarcador.findViewById(R.id.containerMalasVisual);
            FlexboxLayout containerBolas = vMarcador.findViewById(R.id.containerBolasIngresadas);
            View layoutInventarioBolas = vMarcador.findViewById(R.id.layoutInventarioBolas);

            if (layoutInventarioBolas != null) {
                layoutInventarioBolas.setVisibility(View.VISIBLE);
            }

            View btnFalta = vMarcador.findViewById(R.id.btnFalta);
            if (btnFalta != null) {
                btnFalta.setOnClickListener(v -> {
                    if (hayPendientesBloqueantes) {
                        Toast.makeText(getContext(), "Despacha la munición primero ⏳", Toast.LENGTH_SHORT).show();
                        toggleDespacho(true);
                        return;
                    }
                    requireView().performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                    abrirPanelSeleccionMalas(color, containerMalas);
                });
            }

            View btnAnotarBola = vMarcador.findViewById(R.id.btnAnotarBola);
            if (btnAnotarBola != null) {
                btnAnotarBola.setOnClickListener(v -> {
                    if (hayPendientesBloqueantes) {
                        Toast.makeText(getContext(), "Despacha la munición primero ⏳", Toast.LENGTH_SHORT).show();
                        toggleDespacho(true);
                        return;
                    }
                    abrirPanelSeleccionBolas(color);
                });
            }

            String uuidActual = arenaViewModel.getUuidDueloActual();
            if (uuidActual != null && containerBolas != null) {
                AppDatabase.getInstance(requireContext()).bolaDueloDao().observarBolasDuelo(uuidActual)
                        .observe(getViewLifecycleOwner(), bolasAnotadas -> {
                            if (bolasAnotadas != null) {
                                containerBolas.removeAllViews();

                                for (BolaAnotada bola : bolasAnotadas) {
                                    // CORRECCIÓN: Acceso directo a colorEquipo
                                    if (bola.colorEquipo == color) {
                                        View bolaView = getLayoutInflater().inflate(R.layout.item_bola_visual, containerBolas, false);
                                        TextView tvNum = bolaView.findViewById(R.id.tvBolaNumero);

                                        // CORRECCIÓN: Acceso directo a numeroBola
                                        tvNum.setText(String.valueOf(bola.numeroBola));
                                        tvNum.getBackground().setTint(color);
                                        tvNum.setTextColor(Color.WHITE);

                                        bolaView.setOnLongClickListener(v -> {
                                            requireView().performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                                            new Thread(() -> {
                                                // CORRECCIÓN: Acceso directo a numeroBola
                                                AppDatabase.getInstance(requireContext()).bolaDueloDao().eliminarBola(uuidActual, bola.numeroBola);
                                            }).start();

                                            // CORRECCIÓN: Acceso directo a numeroBola
                                            Toast.makeText(getContext(), "Bola " + bola.numeroBola + " anulada.", Toast.LENGTH_SHORT).show();
                                            return true;
                                        });

                                        containerBolas.addView(bolaView);
                                    }
                                }
                            }
                        });
            }

            vMarcador.setOnLongClickListener(v -> {
                if (hayPendientesBloqueantes) {
                    Toast.makeText(getContext(), "Despacha la munición primero ⏳", Toast.LENGTH_SHORT).show();
                    toggleDespacho(true);
                    return true;
                }

                requireView().performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);

                if ("ULTIMO_PAGA".equals(reglaActualSync)) {
                    gestionarReglaUltimoPaga(color);
                } else {
                    validarYProcesarPunto(color);
                }

                return true;
            });

            containerMarcadoresDinamicos.addView(vMarcador);

            View vPeloton = getLayoutInflater().inflate(R.layout.item_peloton_arena, containerGuerrerosDinamicos, false);
            ((ShapeableImageView) vPeloton.findViewById(R.id.imgAvatarMando)).setStrokeColor(ColorStateList.valueOf(color));
            FlexboxLayout followers = vPeloton.findViewById(R.id.containerSeguidores);

            for (Integer id : equipos.get(color)) {
                View burbuja = getLayoutInflater().inflate(R.layout.item_cliente_burbuja, followers, false);
                ((TextView) burbuja.findViewById(R.id.tvNombreBurbuja)).setText(arenaViewModel.obtenerAliasCliente(id));
                arenaViewModel.obtenerSaldoIndividualDuelo(id).observe(getViewLifecycleOwner(), saldo -> {
                    if (saldo != null) ((TextView) burbuja.findViewById(R.id.tvSaldoClienteBurbuja)).setText(NumberFormat.getCurrencyInstance(new Locale("es", "CO")).format(saldo));
                });
                followers.addView(burbuja);
            }
            containerGuerrerosDinamicos.addView(vPeloton);
        }

        vincularScoresExistentes();
        actualizarEstadoVisualMarcadores();
    }

    private void abrirPanelSeleccionMalas(int colorEquipo, LinearLayout containerMalasVisual) {
        android.app.Dialog dialog = new android.app.Dialog(requireContext(), android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
        View view = getLayoutInflater().inflate(R.layout.dialog_seleccion_malas_pro, null);
        dialog.setContentView(view);

        view.setAlpha(0f);
        view.setScaleX(0.90f); view.setScaleY(0.90f);
        view.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(250).start();

        TextView tvCantidad = view.findViewById(R.id.tvCantidadMalas);
        android.widget.SeekBar slider = view.findViewById(R.id.sliderMalas);

        int malasActuales = 0;
        if (containerMalasVisual != null && containerMalasVisual.getChildCount() > 0) {
            View malaView = containerMalasVisual.getChildAt(0);
            TextView tvNum = malaView.findViewById(R.id.tvBolaNumero);
            if (tvNum != null) {
                try { malasActuales = Integer.parseInt(tvNum.getText().toString()); }
                catch (NumberFormatException ignored) {}
            }
        }

        slider.setProgress(malasActuales);
        tvCantidad.setText(String.valueOf(malasActuales));

        slider.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                tvCantidad.setText(String.valueOf(progress));
            }
            @Override public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(android.widget.SeekBar seekBar) {}
        });

        view.findViewById(R.id.btnCerrarDialog).setOnClickListener(v -> dialog.dismiss());

        view.findViewById(R.id.btnConfirmarMalas).setOnClickListener(v -> {
            int nuevasMalas = slider.getProgress();
            if (containerMalasVisual != null) {
                containerMalasVisual.removeAllViews();
                if (nuevasMalas > 0) {
                    View bolaMala = getLayoutInflater().inflate(R.layout.item_bola_visual, containerMalasVisual, false);
                    TextView tvNum = bolaMala.findViewById(R.id.tvBolaNumero);
                    tvNum.setText(String.valueOf(nuevasMalas));
                    tvNum.getBackground().setTint(Color.parseColor("#FF1744"));
                    tvNum.setTextColor(Color.WHITE);
                    containerMalasVisual.addView(bolaMala);

                    bolaMala.setScaleX(0); bolaMala.setScaleY(0);
                    bolaMala.animate().scaleX(1.1f).scaleY(1.1f).setDuration(200).start();
                }
            }
            dialog.dismiss();
        });

        dialog.show();
    }

    private void abrirPanelSeleccionBolas(int colorEquipo) {
        android.app.Dialog dialog = new android.app.Dialog(requireContext(), android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
        View view = getLayoutInflater().inflate(R.layout.dialog_seleccion_bolas, null);
        dialog.setContentView(view);

        view.setAlpha(0f);
        view.setScaleX(0.95f); view.setScaleY(0.95f);
        view.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(250).start();

        LinearLayout containerTriangulo = view.findViewById(R.id.containerTriangulo);
        view.findViewById(R.id.btnCerrarDialog).setOnClickListener(v -> dialog.dismiss());
        view.findViewById(R.id.btnConfirmarBolas).setOnClickListener(v -> dialog.dismiss());

        new Thread(() -> {
            String uuidActual = arenaViewModel.getUuidDueloActual();
            List<Integer> bolasBloqueadas = AppDatabase.getInstance(requireContext()).bolaDueloDao().obtenerBolasYaAnotadasSincrono(uuidActual);

            requireActivity().runOnUiThread(() -> {
                int numeroBola = 1;
                int sizePx = (int) (40 * getResources().getDisplayMetrics().density);
                int marginPx = (int) (3 * getResources().getDisplayMetrics().density);

                for (int fila = 1; fila <= 5; fila++) {
                    LinearLayout rowLayout = new LinearLayout(requireContext());
                    rowLayout.setOrientation(LinearLayout.HORIZONTAL);
                    rowLayout.setGravity(android.view.Gravity.CENTER);

                    for (int col = 0; col < fila; col++) {
                        final int bolaActual = numeroBola;

                        MaterialButton btnBola = new MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
                        btnBola.setText(String.valueOf(bolaActual));
                        btnBola.setCornerRadius(100);
                        btnBola.setTextSize(14f);

                        btnBola.setPadding(0, 0, 0, 0);
                        btnBola.setInsetBottom(0);
                        btnBola.setInsetTop(0);
                        btnBola.setMinWidth(0);
                        btnBola.setMinHeight(0);

                        btnBola.setTextColor(Color.WHITE);
                        btnBola.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#BDBDBD")));
                        btnBola.setStrokeWidth(3);

                        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(sizePx, sizePx);
                        params.setMargins(marginPx, marginPx, marginPx, marginPx);
                        btnBola.setLayoutParams(params);

                        if (bolasBloqueadas != null && bolasBloqueadas.contains(bolaActual)) {
                            btnBola.setEnabled(false);
                            btnBola.setAlpha(0.25f);
                            btnBola.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#424242")));
                            btnBola.setTextColor(Color.WHITE);
                        } else {
                            btnBola.setOnClickListener(v -> {
                                btnBola.setEnabled(false);
                                btnBola.setBackgroundTintList(ColorStateList.valueOf(colorEquipo));
                                btnBola.setStrokeColor(ColorStateList.valueOf(colorEquipo));
                                btnBola.setTextColor(Color.WHITE);

                                btnBola.animate().scaleX(1.15f).scaleY(1.15f).setDuration(150)
                                        .withEndAction(() -> btnBola.animate().scaleX(1f).scaleY(1f).start()).start();

                                new Thread(() -> {
                                    AppDatabase.getInstance(requireContext()).bolaDueloDao().insertarBola(
                                            new BolaAnotada(uuidActual, colorEquipo, bolaActual)
                                    );
                                }).start();
                            });
                        }
                        rowLayout.addView(btnBola);
                        numeroBola++;
                    }
                    containerTriangulo.addView(rowLayout);
                }
            });
        }).start();

        dialog.show();
    }

    private void estallarYLimpiarMesa() {
        String uuidActual = arenaViewModel.getUuidDueloActual();
        if (uuidActual == null) return;

        boolean hayAnimacion = false;
        long duracionTotalAnimacion = 1200;

        if (containerMarcadoresDinamicos == null) return;

        for (int i = 0; i < containerMarcadoresDinamicos.getChildCount(); i++) {
            View marcadorTeam = containerMarcadoresDinamicos.getChildAt(i);
            if (!(marcadorTeam instanceof MaterialCardView)) continue;

            int colorEquipo = ((MaterialCardView) marcadorTeam).getStrokeColor();

            LinearLayout contenedorMalas = marcadorTeam.findViewById(R.id.containerMalasVisual);
            if (contenedorMalas != null) {
                List<View> vistasMalas = new ArrayList<>();
                for (int j = 0; j < contenedorMalas.getChildCount(); j++) vistasMalas.add(contenedorMalas.getChildAt(j));

                for (View v : vistasMalas) {
                    estallarUnaVistaConParticulas(v, colorEquipo);
                    hayAnimacion = true;
                }
            }

            FlexboxLayout contenedorBolas = marcadorTeam.findViewById(R.id.containerBolasIngresadas);
            if (contenedorBolas != null) {
                List<View> vistasBolas = new ArrayList<>();
                for (int j = 0; j < contenedorBolas.getChildCount(); j++) vistasBolas.add(contenedorBolas.getChildAt(j));

                for (View v : vistasBolas) {
                    estallarUnaVistaConParticulas(v, colorEquipo);
                    hayAnimacion = true;
                }
            }
        }

        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            new Thread(() -> {
                AppDatabase.getInstance(requireContext()).bolaDueloDao().limpiarMesa(uuidActual);
            }).start();
        }, hayAnimacion ? duracionTotalAnimacion + 100 : 0);
    }

    private void estallarUnaVistaConParticulas(View vistaABorrar, int colorExplosion) {
        if (vistaABorrar == null) return;
        ViewGroup root = (ViewGroup) vistaABorrar.getRootView();
        if (root == null) return;

        int[] location = new int[2];
        vistaABorrar.getLocationOnScreen(location);
        int centerX = location[0] + (vistaABorrar.getWidth() / 2);
        int centerY = location[1] + (vistaABorrar.getHeight() / 2);

        long duracionInflado = 400;
        vistaABorrar.animate()
                .scaleX(1.8f)
                .scaleY(1.8f)
                .alpha(0.5f)
                .setDuration(duracionInflado)
                .setInterpolator(new android.view.animation.OvershootInterpolator())
                .withEndAction(() -> {
                    vistaABorrar.setVisibility(View.INVISIBLE);

                    int totalParticulas = 30;
                    long duracionVueloParticulas = 800;

                    for (int i = 0; i < totalParticulas; i++) {
                        View particula = new View(requireContext());
                        particula.setBackgroundResource(R.drawable.item_particula_visual);
                        particula.getBackground().setTint(colorExplosion);
                        particula.setAlpha(1.0f);

                        int size = 10 + random.nextInt(15);
                        int sizePx = (int) (size * getResources().getDisplayMetrics().density);

                        ConstraintLayout.LayoutParams params = new ConstraintLayout.LayoutParams(sizePx, sizePx);
                        params.leftToLeft = ConstraintLayout.LayoutParams.PARENT_ID;
                        params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
                        particula.setLayoutParams(params);
                        particula.setX(centerX - (sizePx / 2));
                        particula.setY(centerY - (sizePx / 2));

                        root.addView(particula);

                        float angulo = random.nextFloat() * 360f;
                        float distancia = 150f + random.nextFloat() * 300f;

                        float tX = (float) (distancia * Math.cos(Math.toRadians(angulo)));
                        float tY = (float) (distancia * Math.sin(Math.toRadians(angulo)));

                        particula.animate()
                                .translationXBy(tX)
                                .translationYBy(tY)
                                .scaleX(0.2f)
                                .scaleY(0.2f)
                                .alpha(0f)
                                .setDuration(duracionVueloParticulas)
                                .setInterpolator(new DecelerateInterpolator())
                                .withEndAction(() -> {
                                    root.removeView(particula);
                                })
                                .start();
                    }
                })
                .start();
    }
}