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
import com.nodo.tpv.data.dto.DetalleHistorialDuelo;
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

import com.nodo.tpv.adapters.ClienteDestinoAdapter;

public class FragmentArenaDuelo extends Fragment {

    // --- ESTADOS PARA MODO VAR (VIDEO STREAMING) ---
    private boolean isVarActive = false;

    // Componentes de Video (WEBVIEW para MJPEG)
    private WebView webViewCamara;

    // URL DE LA CÁMARA (NODO CAM - Plan B)
    private String cameraUrl = "http://192.168.1.2:8080/";

    private List<DetalleHistorialDuelo> listaPendientesActual = new ArrayList<>();

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

            // 🔥 SOLUCIÓN CAMBIO 2: Sincronizar inmediatamente la bolsa tras despachar todo
            arenaViewModel.recuperarDueloActivo(idMesaActual);

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

            // 🔥 SOLUCIÓN CAMBIO 2: Refrescar la bolsa cada vez que cambia el estado de los pendientes
            // (Asegura que el valor se actualice en tiempo real sin importar quién o cómo despache)
            arenaViewModel.recuperarDueloActivo(idMesaActual);

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
        // 🔥 SOLUCIÓN CAMBIO 1: Se eliminaron las validaciones que bloqueaban la puntuación
        // Ahora se puede puntuar aunque haya pendientes o no haya munición.

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

        // 🔥 Referencias a los textos que van a cambiar dinámicamente
        TextView tvTituloDespacho = requireView().findViewById(R.id.tvTituloDespacho);
        TextView tvSubtituloDespacho = requireView().findViewById(R.id.tvSubtituloDespacho);

        if (mostrar) {
            com.nodo.tpv.util.SessionManager session = new com.nodo.tpv.util.SessionManager(requireContext());
            com.nodo.tpv.data.entities.Usuario user = session.obtenerUsuario();
            final int idOp = (user != null) ? user.idUsuario : 0;
            final String loginOp = (user != null) ? user.login : "desconocido";

            View panel = requireView();
            androidx.constraintlayout.widget.Group grupoVistaCarrito = panel.findViewById(R.id.grupoVistaCarrito);
            View layoutDestinoCobro = panel.findViewById(R.id.layoutDestinoCobro);

            View btnEntregarTodo = panel.findViewById(R.id.btnEntregarTodoLateral);
            View btnEditarPedido = panel.findViewById(R.id.btnEditarPedido);
            View btnDestinoBolsa = panel.findViewById(R.id.btnDestinoBolsa);
            View btnCancelarDestino = panel.findViewById(R.id.btnCancelarDestino);
            RecyclerView rvJugadoresDestino = panel.findViewById(R.id.rvJugadoresDestino);

            // 1. RESETEAMOS LA VISTA AL ESTADO ORIGINAL
            if (grupoVistaCarrito != null && layoutDestinoCobro != null) {
                grupoVistaCarrito.setVisibility(View.VISIBLE);
                layoutDestinoCobro.setVisibility(View.GONE);
                tvTituloDespacho.setText("DESPACHO DE PEDIDO");
                tvSubtituloDespacho.setVisibility(View.VISIBLE);
            }

            // 2. CARGAMOS LOS PRODUCTOS PENDIENTES
            pedidoViewModel.obtenerSoloPendientesMesa(idMesaActual).observe(getViewLifecycleOwner(), lista -> {
                if (lista != null) {
                    listaPendientesActual = lista;
                    rvDespachoLateral.setLayoutManager(new LinearLayoutManager(getContext()));
                    rvDespachoLateral.setAdapter(new LogBatallaAdapter(lista, item -> {}));
                    if (lista.isEmpty() && despachoVisible) toggleDespacho(false);
                }
            });

            // 3. CARGAMOS LOS CLIENTES EN EL CARRUSEL
            List<Cliente> clientesMesa = arenaViewModel.getTodosLosParticipantesDuelo();
            if (clientesMesa != null && !clientesMesa.isEmpty() && rvJugadoresDestino != null) {
                rvJugadoresDestino.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));

                rvJugadoresDestino.setAdapter(new ClienteDestinoAdapter(clientesMesa, clienteSeleccionado -> {
                    // 🔥 ¡MAGIA! COBRAMOS DIRECTO AL CLIENTE
                    if (listaPendientesActual != null && !listaPendientesActual.isEmpty()) {
                        for (DetalleHistorialDuelo item : listaPendientesActual) {
                            // Le decimos al ViewModel que saque este producto de la apuesta
                            pedidoViewModel.marcarComoEntregadoACliente(item.idDetalle, clienteSeleccionado.idCliente, idOp, loginOp);
                        }

                        Toast.makeText(requireContext(), "Cargado a " + clienteSeleccionado.alias + " ✅", Toast.LENGTH_SHORT).show();
                        // Refrescamos toda la arena (esto actualizará el saldo de la burbuja del jugador)
                        arenaViewModel.recuperarDueloActivo(idMesaActual);
                        toggleDespacho(false);
                    }
                }));
            }

            // 4. AL PRESIONAR ENTREGAR: CAMBIAMOS EL TÍTULO Y LA VISTA
            if (btnEntregarTodo != null) {
                btnEntregarTodo.setOnClickListener(v -> {
                    if (listaPendientesActual != null && !listaPendientesActual.isEmpty()) {
                        grupoVistaCarrito.setVisibility(View.GONE);
                        layoutDestinoCobro.setVisibility(View.VISIBLE);

                        // Mutación del título para ahorrar espacio
                        tvTituloDespacho.setText("SELECCIONA EL DESTINO");
                        tvSubtituloDespacho.setVisibility(View.GONE);
                    } else {
                        Toast.makeText(requireContext(), "No hay productos", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            // 5. AL CANCELAR: VOLVEMOS EL TÍTULO A LA NORMALIDAD
            if (btnCancelarDestino != null) {
                btnCancelarDestino.setOnClickListener(v -> {
                    layoutDestinoCobro.setVisibility(View.GONE);
                    grupoVistaCarrito.setVisibility(View.VISIBLE);
                    tvTituloDespacho.setText("DESPACHO DE PEDIDO");
                    tvSubtituloDespacho.setVisibility(View.VISIBLE);
                });
            }

            // 6. AL COBRAR A LA BOLSA CENTRAL (Todo normal)
            if (btnDestinoBolsa != null) {
                btnDestinoBolsa.setOnClickListener(v -> {
                    if (listaPendientesActual != null && !listaPendientesActual.isEmpty()) {
                        for (DetalleHistorialDuelo item : listaPendientesActual) {
                            pedidoViewModel.marcarComoEntregado(item.idDetalle, idOp, loginOp);
                        }
                        Toast.makeText(requireContext(), "Cargado a la Apuesta ✅", Toast.LENGTH_SHORT).show();
                        arenaViewModel.recuperarDueloActivo(idMesaActual);
                        toggleDespacho(false);
                    }
                });
            }

            // 7. BOTÓN EDITAR
            if (btnEditarPedido != null) {
                btnEditarPedido.setOnClickListener(v -> {
                    toggleDespacho(false);
                    abrirCatalogo();
                });
            }
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
        // 🔥 SOLUCIÓN CAMBIO 1: Ya no opacamos ni deshabilitamos los marcadores si hay pendientes
        for (int i = 0; i < containerMarcadoresDinamicos.getChildCount(); i++) {
            View v = containerMarcadoresDinamicos.getChildAt(i);
            v.setAlpha(1.0f);
            v.setEnabled(true);
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

            // 🔥 Ahora es un LinearLayout para que funcione el Scroll Horizontal
            LinearLayout containerBolas = vMarcador.findViewById(R.id.containerBolasIngresadas);
            View layoutInventarioBolas = vMarcador.findViewById(R.id.layoutInventarioBolas);

            if (layoutInventarioBolas != null) {
                layoutInventarioBolas.setVisibility(View.VISIBLE);
            }

            // --- VINCULACIÓN ZONA DE SALDOS ---
            TextView tvSaldoApuesta = vMarcador.findViewById(R.id.tvSaldoEquipo);
            TextView tvSaldoExtra = vMarcador.findViewById(R.id.tvSaldoExtra);

            arenaViewModel.getListaApuestaEntregada().observe(getViewLifecycleOwner(), productos -> {
                BigDecimal totalApuesta = BigDecimal.ZERO;
                if (productos != null) {
                    for (Producto p : productos) {
                        totalApuesta = totalApuesta.add(p.precioProducto);
                    }
                }
                if (tvSaldoApuesta != null) {
                    tvSaldoApuesta.setText(NumberFormat.getCurrencyInstance(new Locale("es", "CO")).format(totalApuesta));
                }
            });

            if (equipos.containsKey(color)) {
                List<Integer> integrantesEquipo = equipos.get(color);
                final Map<Integer, BigDecimal> saldosIndividuales = new HashMap<>();

                for (Integer idJugador : integrantesEquipo) {
                    arenaViewModel.obtenerSaldoIndividualDuelo(idJugador).observe(getViewLifecycleOwner(), saldo -> {
                        saldosIndividuales.put(idJugador, saldo != null ? saldo : BigDecimal.ZERO);

                        BigDecimal totalExtraEquipo = BigDecimal.ZERO;
                        for (BigDecimal s : saldosIndividuales.values()) {
                            totalExtraEquipo = totalExtraEquipo.add(s);
                        }

                        if (tvSaldoExtra != null) {
                            tvSaldoExtra.setText(NumberFormat.getCurrencyInstance(new Locale("es", "CO")).format(totalExtraEquipo));
                        }
                    });
                }
            }

            View btnFalta = vMarcador.findViewById(R.id.btnFalta);
            if (btnFalta != null) {
                btnFalta.setOnClickListener(v -> {
                    requireView().performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                    abrirPanelSeleccionMalas(color, containerMalas);
                });
            }

            View btnAnotarBola = vMarcador.findViewById(R.id.btnAnotarBola);
            if (btnAnotarBola != null) {
                btnAnotarBola.setOnClickListener(v -> {
                    abrirPanelSeleccionBolas(color);
                });
            }

            String uuidActual = arenaViewModel.getUuidDueloActual();
            if (uuidActual != null && containerBolas != null) {
                AppDatabase.getInstance(requireContext()).bolaDueloDao().observarBolasDuelo(uuidActual)
                        .observe(getViewLifecycleOwner(), bolasAnotadas -> {
                            if (bolasAnotadas != null) {
                                containerBolas.removeAllViews();

                                List<Integer> listaMalasGuardadas = new ArrayList<>();
                                int sumaPuntosPositivos = 0;

                                for (BolaAnotada bola : bolasAnotadas) {
                                    if (bola.colorEquipo == color) {

                                        if (bola.numeroBola < 0) {
                                            // Solo guardamos en la lista, YA NO DIBUJAMOS LA BOLA ROJA
                                            listaMalasGuardadas.add(bola.numeroBola);
                                        } else {
                                            sumaPuntosPositivos += bola.numeroBola;

                                            View bolaView = getLayoutInflater().inflate(R.layout.item_bola_visual, containerBolas, false);
                                            TextView tvNum = bolaView.findViewById(R.id.tvBolaNumero);
                                            View bolaFondoColor = bolaView.findViewById(R.id.bolaFondoColor);
                                            View bolaBandaColor = bolaView.findViewById(R.id.bolaBandaColor);

                                            int numeroBola = bola.numeroBola;
                                            tvNum.setText(String.valueOf(numeroBola));
                                            tvNum.setTextColor(Color.BLACK);

                                            String colorHex;
                                            switch (numeroBola) {
                                                case 1: case 9:  colorHex = "#FDD835"; break;
                                                case 2: case 10: colorHex = "#1E88E5"; break;
                                                case 3: case 11: colorHex = "#E53935"; break;
                                                case 4: case 12: colorHex = "#5E35B1"; break;
                                                case 5: case 13: colorHex = "#FB8C00"; break;
                                                case 6: case 14: colorHex = "#43A047"; break;
                                                case 7: case 15: colorHex = "#6D4C41"; break;
                                                case 8:          colorHex = "#212121"; break;
                                                default:         colorHex = "#757575"; break;
                                            }

                                            int colorRealBola = Color.parseColor(colorHex);

                                            if (numeroBola >= 9 && numeroBola <= 15) {
                                                if (bolaFondoColor != null) bolaFondoColor.setBackgroundColor(Color.WHITE);
                                                if (bolaBandaColor != null) {
                                                    bolaBandaColor.setVisibility(View.VISIBLE);
                                                    bolaBandaColor.setBackgroundColor(colorRealBola);
                                                }
                                            } else {
                                                if (bolaFondoColor != null) bolaFondoColor.setBackgroundColor(colorRealBola);
                                                if (bolaBandaColor != null) bolaBandaColor.setVisibility(View.GONE);
                                            }

                                            bolaView.setOnLongClickListener(v -> {
                                                requireView().performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                                                new Thread(() -> AppDatabase.getInstance(requireContext()).bolaDueloDao().eliminarBola(uuidActual, numeroBola)).start();
                                                Toast.makeText(getContext(), "Bola " + numeroBola + " anulada.", Toast.LENGTH_SHORT).show();
                                                return true;
                                            });

                                            containerBolas.addView(bolaView);
                                        }
                                    }
                                }

                                TextView tvMalasValor = vMarcador.findViewById(R.id.tvMalasValor);
                                TextView tvScoreDinamico = vMarcador.findViewById(R.id.tvScoreDinamico);

                                // Actualizamos solo el texto de malas (ya quitamos el método "actualizarVistaMalas" que dibujaba bolas)
                                if (tvMalasValor != null) {
                                    tvMalasValor.setText(String.valueOf(listaMalasGuardadas.size()));
                                    tvMalasValor.setTag(listaMalasGuardadas); // Esto es clave para que no falle al restar malas
                                }

                                if (tvScoreDinamico != null) {
                                    int totalPuntos = sumaPuntosPositivos - listaMalasGuardadas.size();
                                    tvScoreDinamico.setText(String.valueOf(totalPuntos));

                                    if (totalPuntos < 0) {
                                        tvScoreDinamico.setTextColor(Color.parseColor("#FF1744"));
                                    } else if (totalPuntos > 0) {
                                        tvScoreDinamico.setTextColor(Color.parseColor("#00E5FF"));
                                    } else {
                                        tvScoreDinamico.setTextColor(Color.WHITE);
                                    }
                                }
                            }
                        });
            }

            vMarcador.setOnLongClickListener(v -> {
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

        slider.setProgress(1);
        tvCantidad.setText("1");

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
            int faltasCometidas = slider.getProgress();
            if (faltasCometidas > 0) {
                String uuidActual = arenaViewModel.getUuidDueloActual();
                if(uuidActual != null) {
                    procesarFaltaEnBaseDeDatos(uuidActual, colorEquipo, faltasCometidas);
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

            // 🔥 CORRECCIÓN: Ahora es LinearLayout para poder hacer scroll horizontal
            LinearLayout contenedorBolas = marcadorTeam.findViewById(R.id.containerBolasIngresadas);
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

    private void procesarFalta(int colorInfractor, int cantidadFaltasCometidas) {
        int faltasRestantes = cantidadFaltasCometidas;

        for (int i = 0; i < containerMarcadoresDinamicos.getChildCount(); i++) {
            View vMarcador = containerMarcadoresDinamicos.getChildAt(i);
            if (vMarcador.getTag() instanceof Integer) {
                int colorOtro = (int) vMarcador.getTag();

                if (colorOtro != colorInfractor) {
                    LinearLayout containerMalas = vMarcador.findViewById(R.id.containerMalasVisual);
                    int malasOtro = obtenerMalasDeVista(containerMalas);

                    if (malasOtro > 0) {
                        if (faltasRestantes >= malasOtro) {
                            faltasRestantes -= malasOtro;
                            actualizarVistaMalas(containerMalas, 0);
                        } else {
                            actualizarVistaMalas(containerMalas, malasOtro - faltasRestantes);
                            faltasRestantes = 0;
                            break;
                        }
                    }
                }
            }
        }

        if (faltasRestantes > 0) {
            for (int i = 0; i < containerMarcadoresDinamicos.getChildCount(); i++) {
                View vMarcador = containerMarcadoresDinamicos.getChildAt(i);
                if (vMarcador.getTag() instanceof Integer && (int) vMarcador.getTag() == colorInfractor) {
                    LinearLayout containerMalas = vMarcador.findViewById(R.id.containerMalasVisual);
                    int malasPropias = obtenerMalasDeVista(containerMalas);
                    actualizarVistaMalas(containerMalas, malasPropias + faltasRestantes);
                    break;
                }
            }
        }
    }

    private int obtenerMalasDeVista(LinearLayout container) {
        if (container != null && container.getChildCount() > 0) {
            View malaView = container.getChildAt(0);
            TextView tvNum = malaView.findViewById(R.id.tvBolaNumero);
            try { return Integer.parseInt(tvNum.getText().toString()); }
            catch (Exception e) { return 0; }
        }
        return 0;
    }

    private void actualizarVistaMalas(LinearLayout container, int cantidad) {
        container.removeAllViews();
        if (cantidad > 0) {
            View bolaMala = getLayoutInflater().inflate(R.layout.item_bola_visual, container, false);
            TextView tvNum = bolaMala.findViewById(R.id.tvBolaNumero);
            View bolaFondoColor = bolaMala.findViewById(R.id.bolaFondoColor);
            View bolaBandaColor = bolaMala.findViewById(R.id.bolaBandaColor);

            tvNum.setText(String.valueOf(cantidad));
            tvNum.setTextColor(Color.WHITE);

            if (bolaFondoColor != null) bolaFondoColor.setBackgroundColor(Color.parseColor("#FF1744"));
            if (bolaBandaColor != null) bolaBandaColor.setVisibility(View.GONE);

            container.addView(bolaMala);

            bolaMala.setScaleX(0.5f); bolaMala.setScaleY(0.5f);
            bolaMala.animate().scaleX(1f).scaleY(1f).setDuration(300)
                    .setInterpolator(new android.view.animation.OvershootInterpolator()).start();
        }
    }

    private void procesarFaltaEnBaseDeDatos(String uuidActual, int colorInfractor, int cantidadFaltasCometidas) {
        int faltasRestantes = cantidadFaltasCometidas;
        List<Runnable> dbOperations = new ArrayList<>();

        Map<Integer, List<Integer>> malasPorJugador = new HashMap<>();
        int minNumeroNegativo = 0;

        for (int i = 0; i < containerMarcadoresDinamicos.getChildCount(); i++) {
            View vMarcador = containerMarcadoresDinamicos.getChildAt(i);
            if (vMarcador.getTag() instanceof Integer) {
                int color = (int) vMarcador.getTag();
                TextView tvMalas = vMarcador.findViewById(R.id.tvMalasValor);

                // 🔥 SOLUCIÓN AL CRASH: Blindaje contra nulos
                List<Integer> malas = new ArrayList<>();
                if (tvMalas != null && tvMalas.getTag() != null) {
                    malas = (List<Integer>) tvMalas.getTag();
                }

                malasPorJugador.put(color, new ArrayList<>(malas));

                for (int m : malas) {
                    if (m < minNumeroNegativo) {
                        minNumeroNegativo = m;
                    }
                }
            }
        }

        while (faltasRestantes > 0) {
            boolean todosLosOtrosTienenMalas = true;

            for (Map.Entry<Integer, List<Integer>> entry : malasPorJugador.entrySet()) {
                if (entry.getKey() != colorInfractor) {
                    if (entry.getValue().isEmpty()) {
                        todosLosOtrosTienenMalas = false;
                        break;
                    }
                }
            }

            if (todosLosOtrosTienenMalas) {
                for (Map.Entry<Integer, List<Integer>> entry : malasPorJugador.entrySet()) {
                    if (entry.getKey() != colorInfractor) {
                        List<Integer> malasOtro = entry.getValue();

                        int idParaBorrar = malasOtro.remove(malasOtro.size() - 1);
                        dbOperations.add(() -> {
                            AppDatabase.getInstance(requireContext()).bolaDueloDao().eliminarBola(uuidActual, idParaBorrar);
                        });
                    }
                }
                faltasRestantes--;
            } else {
                break;
            }
        }

        if (faltasRestantes > 0) {
            int siguienteNumeroNegativo = minNumeroNegativo - 1;

            for (int k = 0; k < faltasRestantes; k++) {
                final int malaToInsert = siguienteNumeroNegativo;
                dbOperations.add(() -> {
                    AppDatabase.getInstance(requireContext()).bolaDueloDao().insertarBola(
                            new BolaAnotada(uuidActual, colorInfractor, malaToInsert)
                    );
                });
                siguienteNumeroNegativo--;
            }
        }

        new Thread(() -> {
            for (Runnable op : dbOperations) {
                op.run();
            }
        }).start();
    }
}