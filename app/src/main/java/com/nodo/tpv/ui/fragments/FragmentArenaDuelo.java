package com.nodo.tpv.ui.fragments;

import android.animation.ObjectAnimator;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.airbnb.lottie.LottieAnimationView;
import com.google.android.flexbox.FlexboxLayout;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.nodo.tpv.R;
import com.nodo.tpv.adapters.LogBatallaAdapter;
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

import android.webkit.WebView;
import com.nodo.tpv.ui.arena.managers.ArenaVARManager;
import com.nodo.tpv.ui.arena.managers.ArenaAnimationHelper;
import com.nodo.tpv.ui.arena.dialogs.ArenaDialogFactory;

import com.nodo.tpv.adapters.ClienteDestinoAdapter;

public class FragmentArenaDuelo extends Fragment {

    // --- MANAGERS Y FACTORIES ---
    private ArenaVARManager varManager;
    private ArenaAnimationHelper animationHelper;
    private ArenaDialogFactory dialogFactory;

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

    // --- VIEWMODELS ---
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

    // VARIABLES PARA EL MARCADOR ESPN DINÁMICO
    private LinearLayout containerScoreESPN;
    private View scrollMarcadores;

    private boolean candadoDeslizamiento = false; // Bloquea el swipe por 3 segundos

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

        arenaViewModel = new ViewModelProvider(requireActivity()).get(ArenaViewModel.class);
        pedidoViewModel = new ViewModelProvider(requireActivity()).get(PedidoViewModel.class);

        scrollMarcadores = view.findViewById(R.id.scrollMarcadores);

        layoutContenidoArena = view.findViewById(R.id.layoutContenidoArena);
        panelAcciones = view.findViewById(R.id.panelAccionesLateral);
        WebView webViewCamaraLocal = view.findViewById(R.id.webViewCamara);

        tvBadgePendientes = view.findViewById(R.id.tvBadgePendientes);
        tvReglaActiva = view.findViewById(R.id.tvReglaActiva);
        tvInfoMunicion = view.findViewById(R.id.tvProductoEnJuego);
        lottieCelebration = view.findViewById(R.id.lottieCelebration);
        containerMarcadoresDinamicos = view.findViewById(R.id.containerMarcadoresDinamicos);
        containerGuerrerosDinamicos = view.findViewById(R.id.containerGuerrerosDinamicos);

        varManager = new ArenaVARManager((ConstraintLayout) view, webViewCamaraLocal, scrollMarcadores);
        varManager.configurarWebView();

        animationHelper = new ArenaAnimationHelper(requireContext(), lottieCelebration, containerMarcadoresDinamicos);
        dialogFactory = new ArenaDialogFactory(requireContext(), getLayoutInflater(), PIN_MAESTRO);

        panelHistorial = view.findViewById(R.id.panelHistorialDeslizable);
        panelConfig = view.findViewById(R.id.panelConfigDeslizable);
        panelDespacho = view.findViewById(R.id.panelDespachoDeslizable);
        rvHistorialLateral = view.findViewById(R.id.rvHistorialLateral);
        rvDespachoLateral = view.findViewById(R.id.rvDespachoLateral);
        containerScoreESPN = view.findViewById(R.id.containerScoreESPN);

        switchPin = view.findViewById(R.id.switchRequierePin);
        btnReglaGanador = view.findViewById(R.id.btnReglaGanador);
        btnReglaTodos = view.findViewById(R.id.btnReglaTodos);
        btnReglaUltimo = view.findViewById(R.id.btnReglaUltimo);

        view.findViewById(R.id.btnVerPendientes).setOnClickListener(v -> toggleDespacho(true));
        view.findViewById(R.id.btnCerrarDespacho).setOnClickListener(v -> toggleDespacho(false));
        view.findViewById(R.id.btnEntregarTodoLateral).setOnClickListener(v -> {
            com.nodo.tpv.util.SessionManager session = new com.nodo.tpv.util.SessionManager(requireContext());
            String loginOperativo = session.obtenerUsuario().login;
            int idOperativo = session.obtenerUsuario().idUsuario;

            pedidoViewModel.despacharTodoLaMesa(idMesaActual, idOperativo, loginOperativo);
            toggleDespacho(false);
            arenaViewModel.recuperarDueloActivo(idMesaActual);
            Toast.makeText(getContext(), "Despachando productos...", Toast.LENGTH_SHORT).show();
        });

        view.findViewById(R.id.btnHistorialDuelo).setOnClickListener(v -> toggleHistorial(true));
        view.findViewById(R.id.btnCerrarHistorial).setOnClickListener(v -> toggleHistorial(false));
        view.findViewById(R.id.btnConfigReglas).setOnClickListener(v -> toggleConfig(true));
        view.findViewById(R.id.btnCerrarConfig).setOnClickListener(v -> toggleConfig(false));

        view.findViewById(R.id.fabSeleccionarMunicion).setOnClickListener(v -> abrirCatalogo());
        view.findViewById(R.id.btnFinalizarDuelo).setOnClickListener(v -> mostrarResumenFinalBatalla());
        view.findViewById(R.id.btnVAR).setOnClickListener(v -> varManager.toggleModoVAR());

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

        arenaViewModel.recuperarDueloActivo(idMesaActual);

        arenaViewModel.getProcesandoPunto().observe(getViewLifecycleOwner(), this::bloquearMarcadores);

        arenaViewModel.getReglaCobroDuelo().observe(getViewLifecycleOwner(), regla -> {
            if (regla != null) {
                this.reglaActualSync = regla;
                actualizarBotonesReglaUI(regla);
                tvReglaActiva.setText("REGLA: " + regla.replace("_", " "));
                Map<Integer, Integer> scoresActuales = arenaViewModel.getScoresEquipos().getValue();
                actualizarTextosSwipeYMarcador(scoresActuales);
            }
        });

        pedidoViewModel.observarConteoPendientesMesa(idMesaActual).observe(getViewLifecycleOwner(), count -> {
            hayPendientesBloqueantes = (count != null && count > 0);
            tvBadgePendientes.setVisibility(hayPendientesBloqueantes ? View.VISIBLE : View.GONE);
            tvBadgePendientes.setText(String.valueOf(count));
            arenaViewModel.recuperarDueloActivo(idMesaActual);
            actualizarEstadoVisualMarcadores();
        });

        arenaViewModel.getListaApuestaEntregada().observe(getViewLifecycleOwner(), this::actualizarTextoBolsa);

        arenaViewModel.getMapaColoresDuelo().observe(getViewLifecycleOwner(), mapa -> {
            if (mapa != null && !mapa.isEmpty()) generarInterfazDinamica(mapa);
        });

        arenaViewModel.getScoresEquipos().observe(getViewLifecycleOwner(), scores -> {
            if (scores != null) actualizarTextosSwipeYMarcador(scores);
        });
    }

    @Override
    public void onStop() {
        super.onStop();
        if (varManager != null) varManager.onStop();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (varManager != null) varManager.onResume();
    }

    private void validarYProcesarPunto(int colorEquipo) {
        if (switchPin != null && switchPin.isChecked()) {
            solicitarPinYRegistrar(colorEquipo);
        } else {
            ejecutarImpactoDirecto(colorEquipo);
        }
    }

    private void ejecutarImpactoDirecto(int colorEquipo) {
        requireView().performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);

        // 1. Sumamos el punto global
        arenaViewModel.aplicarDanioMultiequipo(colorEquipo);

        // 2. Limpiamos las bolas de la mesa silenciosamente (sin explosiones viejas)
        String uuidActual = arenaViewModel.getUuidDueloActual();
        if (uuidActual != null) {
            arenaViewModel.limpiarMesaDuelo(uuidActual);
        }
    }

    private void solicitarPinYRegistrar(int colorEquipo) {
        dialogFactory.mostrarDialogoPin(colorEquipo, color -> {
            // 1. Sumamos el punto global
            arenaViewModel.aplicarDanioMultiequipo(color);

            // 2. Limpiamos las bolas de la mesa silenciosamente (sin explosiones viejas)
            String uuidActual = arenaViewModel.getUuidDueloActual();
            if (uuidActual != null) {
                arenaViewModel.limpiarMesaDuelo(uuidActual);
            }
        });
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

            if (grupoVistaCarrito != null && layoutDestinoCobro != null) {
                grupoVistaCarrito.setVisibility(View.VISIBLE);
                layoutDestinoCobro.setVisibility(View.GONE);
                tvTituloDespacho.setText("DESPACHO DE PEDIDO");
                tvSubtituloDespacho.setVisibility(View.VISIBLE);
            }

            pedidoViewModel.obtenerSoloPendientesMesa(idMesaActual).observe(getViewLifecycleOwner(), lista -> {
                if (lista != null) {
                    listaPendientesActual = lista;
                    rvDespachoLateral.setLayoutManager(new LinearLayoutManager(getContext()));
                    rvDespachoLateral.setAdapter(new LogBatallaAdapter(lista, item -> {}));
                    if (lista.isEmpty() && despachoVisible) toggleDespacho(false);
                }
            });

            List<Cliente> clientesMesa = arenaViewModel.getTodosLosParticipantesDuelo();
            if (clientesMesa != null && !clientesMesa.isEmpty() && rvJugadoresDestino != null) {
                rvJugadoresDestino.setLayoutManager(new LinearLayoutManager(getContext(), LinearLayoutManager.HORIZONTAL, false));

                rvJugadoresDestino.setAdapter(new ClienteDestinoAdapter(clientesMesa, clienteSeleccionado -> {
                    if (listaPendientesActual != null && !listaPendientesActual.isEmpty()) {
                        for (DetalleHistorialDuelo item : listaPendientesActual) {
                            pedidoViewModel.marcarComoEntregadoACliente(item.idDetalle, clienteSeleccionado.idCliente, idOp, loginOp);
                        }
                        Toast.makeText(requireContext(), "Cargado a " + clienteSeleccionado.alias + " ✅", Toast.LENGTH_SHORT).show();
                        arenaViewModel.recuperarDueloActivo(idMesaActual);
                        toggleDespacho(false);
                    }
                }));
            }

            if (btnEntregarTodo != null) {
                btnEntregarTodo.setOnClickListener(v -> {
                    if (listaPendientesActual != null && !listaPendientesActual.isEmpty()) {
                        grupoVistaCarrito.setVisibility(View.GONE);
                        layoutDestinoCobro.setVisibility(View.VISIBLE);
                        tvTituloDespacho.setText("SELECCIONA EL DESTINO");
                        tvSubtituloDespacho.setVisibility(View.GONE);
                    } else {
                        Toast.makeText(requireContext(), "No hay productos", Toast.LENGTH_SHORT).show();
                    }
                });
            }

            if (btnCancelarDestino != null) {
                btnCancelarDestino.setOnClickListener(v -> {
                    layoutDestinoCobro.setVisibility(View.GONE);
                    grupoVistaCarrito.setVisibility(View.VISIBLE);
                    tvTituloDespacho.setText("DESPACHO DE PEDIDO");
                    tvSubtituloDespacho.setVisibility(View.VISIBLE);
                });
            }

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
            // ¡NUEVO!: Llamamos a nuestro Timeline Supremo en lugar del historial viejo
            String uuidActual = arenaViewModel.getUuidDueloActual();
            if (uuidActual != null) {
                arenaViewModel.obtenerLogBatallaEnVivo(uuidActual).observe(getViewLifecycleOwner(), lista -> {
                    if (lista != null) {
                        rvHistorialLateral.setLayoutManager(new LinearLayoutManager(getContext()));
                        // Usamos nuestro nuevo adaptador
                        rvHistorialLateral.setAdapter(new com.nodo.tpv.adapters.TimelineBatallaAdapter(lista));
                    }
                });
            }
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

    private void abrirCatalogo() {
        CatalogoProductosFragment fragment = CatalogoProductosFragment.newInstance(0, idMesaActual);
        getParentFragmentManager().beginTransaction()
                .setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left, android.R.anim.slide_in_left, android.R.anim.slide_out_right)
                .replace(R.id.container_fragments, fragment).addToBackStack(null).commit();
    }

    private void mostrarResumenFinalBatalla() {
        dialogFactory.mostrarResumenFinalBatalla(() -> {
            arenaViewModel.finalizarDueloCompleto(idMesaActual, "POOL");
            getParentFragmentManager().popBackStack();
        });
    }

    private void actualizarEstadoVisualMarcadores() {
        if (containerMarcadoresDinamicos == null) return;
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
                animationHelper.animarEquipoSalvado(v);
                v.setEnabled(false);
                break;
            }
        }

        if (equiposSalvadosEnRonda.size() == 1) {
            Toast.makeText(getContext(), "GANADOR: " + getNombreColor(colorEquipoTocado) + " 🏆", Toast.LENGTH_SHORT).show();
        }

        cruzarMalasSobrevivientes();

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

                animationHelper.animarPerdedorFinal(perdedor);

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
        // 1. Buscar las vistas del GANADOR ABSOLUTO (el primero que se salvó) en la pantalla
        View vistaMarcadorGanador = null;
        LinearLayout contenedorBolasGanador = null;

        if (containerMarcadoresDinamicos != null) {
            for (int i = 0; i < containerMarcadoresDinamicos.getChildCount(); i++) {
                View v = containerMarcadoresDinamicos.getChildAt(i);
                if (v.getTag() instanceof Integer && (int) v.getTag() == colorGanador) {
                    vistaMarcadorGanador = v;
                    contenedorBolasGanador = v.findViewById(R.id.containerBolasIngresadas);
                    break;
                }
            }
        }

        // Si por alguna razón de sistema no lo encuentra, hacemos el cierre directo
        if (vistaMarcadorGanador == null) {
            ejecutarCierreSilenciosoUltimoPaga(colorGanador, colorPerdedor);
            return;
        }

        // 2. ¡Lanzar la magia! Ráfaga de espirales del ganador hacia el marcador ESPN
        animationHelper.animarRafagaBolasYChoque(vistaMarcadorGanador, contenedorBolasGanador, containerScoreESPN, colorGanador, () -> {
            // 3. Este bloque se ejecuta SOLAMENTE cuando la última bola choca con el marcador
            ejecutarCierreSilenciosoUltimoPaga(colorGanador, colorPerdedor);
        });
    }

    private void ejecutarCierreSilenciosoUltimoPaga(int colorGanador, int colorPerdedor) {
        // A. Aplicar el daño (cobrar el dinero al perdedor) y sumar el punto al ganador en BD
        arenaViewModel.aplicarDanioUltimoPaga(colorGanador, colorPerdedor);

        // B. Limpiar TODAS las bolas de la mesa silenciosamente (sin la explosión vieja)
        String uuidActual = arenaViewModel.getUuidDueloActual();
        if (uuidActual != null) {
            arenaViewModel.limpiarMesaDuelo(uuidActual);
        }

        // C. Limpiar la lista de supervivientes y DESCONGELAR la UI para la nueva ronda
        equiposSalvadosEnRonda.clear();
        animationHelper.animarRestauracionUI();

        // D. Actualizar el marcador ESPN y revivir los textos/deslizadores
        Map<Integer, Integer> scoresActuales = arenaViewModel.getScoresEquipos().getValue();
        actualizarTextosSwipeYMarcador(scoresActuales);
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

            LinearLayout containerBolas = vMarcador.findViewById(R.id.containerBolasIngresadas);
            View layoutInventarioBolas = vMarcador.findViewById(R.id.layoutInventarioBolas);

            if (layoutInventarioBolas != null) {
                layoutInventarioBolas.setVisibility(View.VISIBLE);
            }

            android.widget.HorizontalScrollView scrollBolas = vMarcador.findViewById(R.id.scrollBolas);
            if (scrollBolas != null) {
                scrollBolas.setOnTouchListener((viewTouch, event) -> {
                    viewTouch.getParent().requestDisallowInterceptTouchEvent(true);
                    return false;
                });
            }

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
                    arenaViewModel.obtenerSaldoExtraIndividual(idJugador).observe(getViewLifecycleOwner(), saldo -> {
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
                    dialogFactory.mostrarPanelSeleccionMalas(faltasCometidas -> {
                        String uuidActual = arenaViewModel.getUuidDueloActual();
                        if(uuidActual != null) {
                            procesarFaltaEnBaseDeDatos(uuidActual, color, faltasCometidas);
                        }
                    });
                });
            }

            View btnAnotarBola = vMarcador.findViewById(R.id.btnAnotarBola);
            if (btnAnotarBola != null) {
                btnAnotarBola.setOnClickListener(v -> {
                    String uuidActual = arenaViewModel.getUuidDueloActual();
                    if (uuidActual != null) {
                        arenaViewModel.obtenerBolasBloqueadas(uuidActual, bloqueadas -> {
                            dialogFactory.mostrarPanelSeleccionBolas(color, bloqueadas, bolaSeleccionada -> {
                                arenaViewModel.anotarBolaDuelo(uuidActual, color, bolaSeleccionada);
                            });
                        });
                    }
                });
            }

            String uuidActual = arenaViewModel.getUuidDueloActual();
            if (uuidActual != null && containerBolas != null) {
                arenaViewModel.observarBolasDuelo(uuidActual)
                        .observe(getViewLifecycleOwner(), bolasAnotadas -> {
                            if (bolasAnotadas != null) {
                                containerBolas.removeAllViews();

                                List<Integer> listaMalasGuardadas = new ArrayList<>();
                                int sumaPuntosPositivos = 0;

                                for (BolaAnotada bola : bolasAnotadas) {
                                    if (bola.colorEquipo == color) {

                                        if (bola.numeroBola < 0) {
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
                                                dialogFactory.mostrarDialogoRevertirJugada(numeroBola, () -> {
                                                    arenaViewModel.revertirBolaDuelo(uuidActual, numeroBola);
                                                    Toast.makeText(getContext(), "Bola " + numeroBola + " retirada correctamente.", Toast.LENGTH_SHORT).show();
                                                });
                                                return true;
                                            });

                                            containerBolas.addView(bolaView);
                                        }
                                    }
                                }

                                if (scrollBolas != null) {
                                    scrollBolas.post(() -> scrollBolas.fullScroll(View.FOCUS_RIGHT));
                                }

                                TextView tvMalasValor = vMarcador.findViewById(R.id.tvMalasValor);
                                TextView tvScoreDinamico = vMarcador.findViewById(R.id.tvScoreDinamico);

                                if (tvMalasValor != null) {
                                    tvMalasValor.setText(String.valueOf(listaMalasGuardadas.size()));
                                    tvMalasValor.setTag(listaMalasGuardadas);
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

            android.widget.SeekBar swipeToWin = vMarcador.findViewById(R.id.swipeToWin);
            TextView tvTextoSwipe = vMarcador.findViewById(R.id.tvTextoSwipe);
            android.widget.FrameLayout layoutFondoSwipe = vMarcador.findViewById(R.id.layoutFondoSwipe);

            if (swipeToWin != null && tvTextoSwipe != null && layoutFondoSwipe != null) {

                // Solo restaurar texto inicial si NO estamos en los 3 segundos de candado
                if (!candadoDeslizamiento) {
                    if ("ULTIMO_PAGA".equals(reglaActualSync)) {
                        tvTextoSwipe.setText("👉 DESLIZA PARA SALVARSE");
                        tvTextoSwipe.setTextColor(Color.parseColor("#00E676"));
                        swipeToWin.setThumbTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#00E676")));
                    } else {
                        tvTextoSwipe.setText("👉 DESLIZA PARA GANAR");
                        tvTextoSwipe.setTextColor(Color.parseColor("#FFD600"));
                        swipeToWin.setThumbTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#FFD600")));
                    }
                }

                swipeToWin.setOnSeekBarChangeListener(new android.widget.SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(android.widget.SeekBar seekBar, int progress, boolean fromUser) {
                        if (fromUser && !candadoDeslizamiento) {
                            tvTextoSwipe.setAlpha(1f - (progress / 100f));
                        }
                    }

                    @Override public void onStartTrackingTouch(android.widget.SeekBar seekBar) {}

                    @Override
                    public void onStopTrackingTouch(android.widget.SeekBar seekBar) {
                        if (seekBar.getProgress() > 85) {
                            seekBar.setProgress(100);

                            // 1. REVISAR EL CANDADO DE 3 SEGUNDOS
                            if (candadoDeslizamiento) {
                                resetearSwipe(seekBar, tvTextoSwipe);
                                return;
                            }

                            // 2. REVISAR PROCESAMIENTO DEL VIEWMODEL
                            if (Boolean.TRUE.equals(arenaViewModel.getProcesandoPunto().getValue())) {
                                Toast.makeText(getContext(), "Procesando cobro, espera...", Toast.LENGTH_SHORT).show();
                                resetearSwipe(seekBar, tvTextoSwipe);
                                return;
                            }

                            // --- ¡PUNTO VÁLIDO! CERRAMOS EL CANDADO ---
                            candadoDeslizamiento = true;

                            // BLOQUEO FÍSICO Y VISUAL
                            seekBar.setEnabled(false);
                            tvTextoSwipe.setText("ESPERA ⏳");
                            tvTextoSwipe.setAlpha(1f);

                            // EL CRONÓMETRO DE 3 SEGUNDOS
                            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                                candadoDeslizamiento = false;
                                seekBar.setEnabled(true); // Desbloqueo físico

                                // Restaurar texto
                                if ("ULTIMO_PAGA".equals(reglaActualSync)) {
                                    tvTextoSwipe.setText("👉 DESLIZA PARA SALVARSE");
                                } else {
                                    tvTextoSwipe.setText("👉 DESLIZA PARA GANAR");
                                }
                            }, 3000);

                            requireView().performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);

                            // 3. EJECUTAR LÓGICA DE JUEGO
                            if ("ULTIMO_PAGA".equals(reglaActualSync)) {
                                layoutFondoSwipe.setVisibility(View.GONE);
                                gestionarReglaUltimoPaga(color);
                            } else {
                                // --- ¡AQUÍ ESTÁ EL CAMBIO A RÁFAGA DE BOLAS! ---
                                animationHelper.animarRafagaBolasYChoque(vMarcador, containerBolas, containerScoreESPN, color, () -> {
                                    // Cuando todas las bolas chocaron, validamos y sonamos
                                    validarYProcesarPunto(color);
                                });

                                resetearSwipe(seekBar, tvTextoSwipe);
                            }

                        } else {
                            resetearSwipe(seekBar, tvTextoSwipe);
                        }
                    }
                });
            }

            containerMarcadoresDinamicos.addView(vMarcador);

            View vPeloton = getLayoutInflater().inflate(R.layout.item_peloton_arena, containerGuerrerosDinamicos, false);
            ((com.google.android.material.imageview.ShapeableImageView) vPeloton.findViewById(R.id.imgAvatarMando)).setStrokeColor(ColorStateList.valueOf(color));
            com.google.android.flexbox.FlexboxLayout followers = vPeloton.findViewById(R.id.containerSeguidores);

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
        actualizarEstadoVisualMarcadores();
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

            LinearLayout contenedorBolas = marcadorTeam.findViewById(R.id.containerBolasIngresadas);
            if (contenedorBolas != null) {
                List<View> vistasBolas = new ArrayList<>();
                for (int j = 0; j < contenedorBolas.getChildCount(); j++) vistasBolas.add(contenedorBolas.getChildAt(j));

                for (View v : vistasBolas) {
                    animationHelper.estallarUnaVistaConParticulas(v, colorEquipo);
                    hayAnimacion = true;
                }
            }
        }

        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            // Delegamos el borrado al ViewModel
            arenaViewModel.limpiarMesaDuelo(uuidActual);
        }, hayAnimacion ? duracionTotalAnimacion + 100 : 0);
    }

    private void procesarFaltaEnBaseDeDatos(String uuidActual, int colorInfractor, int cantidadFaltasCometidas) {
        int faltasRestantes = cantidadFaltasCometidas;

        Map<Integer, List<Integer>> malasPorJugador = new HashMap<>();
        int minNumeroNegativo = 0;

        for (int i = 0; i < containerMarcadoresDinamicos.getChildCount(); i++) {
            View vMarcador = containerMarcadoresDinamicos.getChildAt(i);
            if (vMarcador.getTag() instanceof Integer) {
                int color = (int) vMarcador.getTag();
                TextView tvMalas = vMarcador.findViewById(R.id.tvMalasValor);

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

        List<Integer> bolasAEliminar = new ArrayList<>();
        List<BolaAnotada> bolasAInsertar = new ArrayList<>();

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
                        bolasAEliminar.add(idParaBorrar);
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
                bolasAInsertar.add(new BolaAnotada(uuidActual, colorInfractor, siguienteNumeroNegativo));
                siguienteNumeroNegativo--;
            }
        }

        // Delegamos las operaciones de DB al ViewModel
        arenaViewModel.procesarCruceFaltas(uuidActual, bolasAEliminar, bolasAInsertar);
    }

    private void bloquearMarcadores(boolean bloquear) {
        if (containerMarcadoresDinamicos == null) return;

        for (int i = 0; i < containerMarcadoresDinamicos.getChildCount(); i++) {
            View v = containerMarcadoresDinamicos.getChildAt(i);
            android.widget.SeekBar swipeToWin = v.findViewById(R.id.swipeToWin);

            if (bloquear) {
                v.setClickable(false);
                v.setLongClickable(false);
                v.setAlpha(0.7f);
                if (swipeToWin != null) swipeToWin.setEnabled(false);
            } else {
                if (v.getTag() instanceof Integer) {
                    int color = (int) v.getTag();
                    if (!equiposSalvadosEnRonda.contains(color)) {
                        v.setClickable(true);
                        v.setLongClickable(true);
                        v.setAlpha(1.0f);
                        if (swipeToWin != null) swipeToWin.setEnabled(true);
                    }
                }
            }
        }
    }

    private void resetearSwipe(android.widget.SeekBar seekBar, TextView tvTextoSwipe) {
        ObjectAnimator anim = ObjectAnimator.ofInt(seekBar, "progress", seekBar.getProgress(), 0);
        anim.setDuration(250);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.start();
        tvTextoSwipe.animate().alpha(1f).setDuration(250).start();
    }

    private void actualizarTextosSwipeYMarcador(Map<Integer, Integer> scores) {
        if (containerScoreESPN != null) {
            containerScoreESPN.setVisibility(View.VISIBLE);
        }

        if (containerMarcadoresDinamicos != null) {
            for (int i = 0; i < containerMarcadoresDinamicos.getChildCount(); i++) {
                View v = containerMarcadoresDinamicos.getChildAt(i);
                TextView tvTextoSwipe = v.findViewById(R.id.tvTextoSwipe);
                android.widget.SeekBar swipeToWin = v.findViewById(R.id.swipeToWin);
                android.widget.FrameLayout layoutFondoSwipe = v.findViewById(R.id.layoutFondoSwipe);

                if (tvTextoSwipe == null || swipeToWin == null) continue;

                if ("ULTIMO_PAGA".equals(reglaActualSync)) {
                    // CÓDIGO NUEVO AQUÍ: Proteger el texto si el candado está cerrado
                    if (!candadoDeslizamiento) {
                        tvTextoSwipe.setText("👉 DESLIZA PARA SALVARSE");
                    }
                    tvTextoSwipe.setTextColor(Color.parseColor("#00E676"));
                    swipeToWin.setThumbTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#00E676")));

                    if (v.getTag() instanceof Integer && equiposSalvadosEnRonda.contains((Integer) v.getTag())) {
                        layoutFondoSwipe.setVisibility(View.GONE);
                    } else {
                        layoutFondoSwipe.setVisibility(View.VISIBLE);
                    }
                } else {
                    // CÓDIGO NUEVO AQUÍ: Proteger el texto si el candado está cerrado
                    if (!candadoDeslizamiento) {
                        tvTextoSwipe.setText("👉 DESLIZA PARA GANAR");
                    }
                    tvTextoSwipe.setTextColor(Color.parseColor("#FFD600"));
                    swipeToWin.setThumbTintList(android.content.res.ColorStateList.valueOf(Color.parseColor("#FFD600")));
                    layoutFondoSwipe.setVisibility(View.VISIBLE);
                }
            }
        }

        if (containerScoreESPN != null) {
            containerScoreESPN.removeAllViews();

            Map<Integer, Integer> mapaColores = arenaViewModel.getMapaColoresDuelo().getValue();
            if (mapaColores != null && scores != null) {

                List<Integer> coloresUnicos = new ArrayList<>();
                for (Integer c : mapaColores.values()) {
                    if (!coloresUnicos.contains(c)) coloresUnicos.add(c);
                }

                for (int i = 0; i < coloresUnicos.size(); i++) {
                    int colorEquipo = coloresUnicos.get(i);
                    int pts = scores.containsKey(colorEquipo) ? scores.get(colorEquipo) : 0;

                    LinearLayout layoutEquipo = new LinearLayout(requireContext());
                    layoutEquipo.setOrientation(LinearLayout.HORIZONTAL);
                    layoutEquipo.setGravity(android.view.Gravity.CENTER);

                    TextView tvLabel = new TextView(requireContext());
                    tvLabel.setText(getNombreColor(colorEquipo));
                    tvLabel.setTextColor(colorEquipo);
                    tvLabel.setTextSize(12f);
                    tvLabel.setTypeface(null, android.graphics.Typeface.BOLD);
                    tvLabel.setPadding(0, 0, (int) (8 * getResources().getDisplayMetrics().density), 0);

                    TextView tvScore = new TextView(requireContext());
                    tvScore.setTextColor(colorEquipo);
                    tvScore.setTextSize(20f);
                    tvScore.setTypeface(null, android.graphics.Typeface.BOLD);

                    if ("ULTIMO_PAGA".equals(reglaActualSync)) {
                        tvScore.setText(equiposSalvadosEnRonda.contains(colorEquipo) ? "🛡️" : String.valueOf(pts));
                    } else {
                        tvScore.setText(String.valueOf(pts));
                    }

                    layoutEquipo.addView(tvLabel);
                    layoutEquipo.addView(tvScore);

                    containerScoreESPN.addView(layoutEquipo);

                    if (i < coloresUnicos.size() - 1) {
                        View separador = new View(requireContext());
                        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                                (int) (1 * getResources().getDisplayMetrics().density),
                                (int) (20 * getResources().getDisplayMetrics().density)
                        );
                        int margenHorizontal = (int) (16 * getResources().getDisplayMetrics().density);
                        params.setMargins(margenHorizontal, 0, margenHorizontal, 0);
                        separador.setLayoutParams(params);
                        separador.setBackgroundColor(Color.parseColor("#4DFFFFFF"));

                        containerScoreESPN.addView(separador);
                    }
                }
            }
        }
    }

    private void cruzarMalasSobrevivientes() {
        String uuidActual = arenaViewModel.getUuidDueloActual();
        if (uuidActual == null) return;

        Map<Integer, Integer> mapa = arenaViewModel.getMapaColoresDuelo().getValue();
        if (mapa == null) return;

        java.util.Set<Integer> coloresSobrevivientes = new java.util.HashSet<>();
        for (Integer c : mapa.values()) {
            if (!equiposSalvadosEnRonda.contains(c)) {
                coloresSobrevivientes.add(c);
            }
        }

        if (coloresSobrevivientes.size() < 2) return;

        Map<Integer, List<Integer>> malasPorSobreviviente = new HashMap<>();
        int minMalas = Integer.MAX_VALUE;

        if (containerMarcadoresDinamicos == null) return;

        for (int i = 0; i < containerMarcadoresDinamicos.getChildCount(); i++) {
            View vMarcador = containerMarcadoresDinamicos.getChildAt(i);
            if (vMarcador.getTag() instanceof Integer) {
                int color = (int) vMarcador.getTag();
                if (coloresSobrevivientes.contains(color)) {
                    TextView tvMalas = vMarcador.findViewById(R.id.tvMalasValor);
                    List<Integer> malas = new ArrayList<>();

                    if (tvMalas != null && tvMalas.getTag() != null) {
                        malas = (List<Integer>) tvMalas.getTag();
                    }
                    malasPorSobreviviente.put(color, new ArrayList<>(malas));

                    if (malas.size() < minMalas) {
                        minMalas = malas.size();
                    }
                }
            }
        }

        if (minMalas == 0 || minMalas == Integer.MAX_VALUE) return;

        final int malasABorrar = minMalas;
        List<Integer> bolasAEliminar = new ArrayList<>();

        for (Map.Entry<Integer, List<Integer>> entry : malasPorSobreviviente.entrySet()) {
            List<Integer> malasEquipo = entry.getValue();
            for (int k = 0; k < malasABorrar; k++) {
                bolasAEliminar.add(malasEquipo.remove(malasEquipo.size() - 1));
            }
        }

        // Delegamos las operaciones de DB al ViewModel
        arenaViewModel.procesarCruceFaltas(uuidActual, bolasAEliminar, null);

        Toast.makeText(getContext(), "⚔️ Cruce automático: -" + malasABorrar + " malas", Toast.LENGTH_SHORT).show();
    }
}