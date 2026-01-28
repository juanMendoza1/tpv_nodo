package com.nodo.tpv.ui.fragments;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.airbnb.lottie.LottieAnimationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.nodo.tpv.R;
import com.nodo.tpv.adapters.BolsaDetalleAdapter;
import com.nodo.tpv.adapters.ClienteAdapter;
import com.nodo.tpv.adapters.LogDeudaAdapter;
import com.nodo.tpv.adapters.LogResumenSalidaAdapter;
import com.nodo.tpv.data.entities.Cliente;
import com.nodo.tpv.data.entities.DueloTemporalInd;
import com.nodo.tpv.data.entities.Producto;
import com.nodo.tpv.ui.main.MainActivity;
import com.nodo.tpv.viewmodel.ClienteViewModel;
import com.nodo.tpv.viewmodel.ProductoViewModel;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import android.util.TypedValue;

public class FragmentArenaDueloInd extends Fragment {

    private ProductoViewModel productoViewModel;
    private ClienteViewModel clienteViewModel;
    private List<Cliente> participantes = new ArrayList<>();

    private boolean bolsaEsVisible = false;
    private BolsaDetalleAdapter bolsaDetalleAdapter;

    private final int[] COLORES_NEON = {
            Color.parseColor("#00E5FF"), // Cyan
            Color.parseColor("#FF1744"), // Rojo
            Color.parseColor("#FFD54F"), // Amarillo
            Color.parseColor("#4CAF50"), // Verde
            Color.parseColor("#AA00FF"), // Morado
            Color.parseColor("#FF6D00")  // Naranja
    };

    private boolean marcadorCargadoPorPrimeraVez = false;
    private int idMesaActual;
    private int metaPuntosActual = 20;

    private LottieAnimationView lottieCelebration;
    private TextView tvValorEnJuego, tvItemsBolsa, tvHeaderBillar;
    private MaterialCardView cardBolsaExpandible;
    private ImageView ivFlechaBolsa;
    private final String PIN_MAESTRO = "1234";

    private View panelConfigInd;
    private boolean configVisible = false;

    private View panelRecluta;
    private RecyclerView rvRecluta;
    private MaterialButton btnConfirmarRecluta;
    private Cliente clienteSeleccionadoPro;

    private Handler timerHandler = new Handler(Looper.getMainLooper());
    private boolean isTimerRunning = false;
    private Map<Integer, Long> tiempoAcumuladoPorJugador = new HashMap<>();
    private Map<Integer, Boolean> estadoPausaPorJugador = new HashMap<>();
    private Map<Integer, Integer> miniMarcadoresPorJugador = new HashMap<>();
    private Map<Integer, Long> startTimesMap = new HashMap<>();

    private LogDeudaAdapter logDeudaAdapter;

    // 1. Declarar la vista en la clase
    private View panelLiquidacion;
    private Cliente clienteEnLiquidacion;

    private LogResumenSalidaAdapter liquidacionAdapter;
    private RecyclerView rvResumenSalida;

    public static FragmentArenaDueloInd newInstance(List<Cliente> seleccionados, int idMesa) {
        FragmentArenaDueloInd f = new FragmentArenaDueloInd();
        Bundle args = new Bundle();
        args.putInt("id_mesa", idMesa);
        f.setArguments(args);
        f.participantes = new ArrayList<>(seleccionados);
        return f;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            idMesaActual = getArguments().getInt("id_mesa");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_arena_duelo_ind, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        productoViewModel = new ViewModelProvider(requireActivity()).get(ProductoViewModel.class);
        clienteViewModel = new ViewModelProvider(requireActivity()).get(ClienteViewModel.class);

        // 1. Recuperar participantes
        if (participantes == null || participantes.isEmpty()) {
            participantes = productoViewModel.getIntegrantesAzulCacheados();
        }

        // 2. Vincular Vistas principales
        lottieCelebration = view.findViewById(R.id.lottieCelebration);
        tvValorEnJuego = view.findViewById(R.id.tvProductoEnJuego);
        tvItemsBolsa = view.findViewById(R.id.tvItemsBolsa);
        tvHeaderBillar = view.findViewById(R.id.tvHeaderBillar);
        cardBolsaExpandible = view.findViewById(R.id.cardBolsaExpandible);
        ivFlechaBolsa = view.findViewById(R.id.ivFlechaBolsa);

        panelConfigInd = view.findViewById(R.id.panelConfigDeslizableInd);
        panelRecluta = view.findViewById(R.id.panelReclutamientoPro);
        rvRecluta = view.findViewById(R.id.rvReclutamiento);
        btnConfirmarRecluta = view.findViewById(R.id.btnConfirmarRecluta);

        // 3. Vincular Panel de Liquidación Individual (Nuevo)
        panelLiquidacion = view.findViewById(R.id.panelLiquidacionIndividual);
        rvResumenSalida = view.findViewById(R.id.rvDuelosIndividualLiq);

        // Inicializar el nuevo adaptador de liquidación
        liquidacionAdapter = new LogResumenSalidaAdapter();
        rvResumenSalida.setLayoutManager(new LinearLayoutManager(getContext()));
        rvResumenSalida.setAdapter(liquidacionAdapter);

        if (requireActivity() instanceof MainActivity) {
            ((MainActivity) requireActivity()).setExpandirContenedor(true);
        }

        // --- SECCIÓN RECYCLERVIEWS INTERCAMBIABLES (Arena General) ---
        RecyclerView rvIntercambiable = view.findViewById(R.id.rvDetalleBolsaExpandida);
        bolsaDetalleAdapter = new BolsaDetalleAdapter();
        logDeudaAdapter = new LogDeudaAdapter();
        rvIntercambiable.setLayoutManager(new LinearLayoutManager(getContext()));

        // --- LÓGICA DE TARJETA BOLSA (MUNICIÓN PENDIENTE) ---
        if (cardBolsaExpandible != null) {
            cardBolsaExpandible.setOnClickListener(v -> {
                bolsaEsVisible = !bolsaEsVisible;
                v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);

                LinearLayout containerJugadores = view.findViewById(R.id.containerJugadores);
                ivFlechaBolsa.animate().rotation(bolsaEsVisible ? 180 : 0).setDuration(300).start();

                if (bolsaEsVisible) {
                    containerJugadores.animate().alpha(0f).scaleX(0.9f).scaleY(0.9f).setDuration(300)
                            .withEndAction(() -> {
                                containerJugadores.setVisibility(View.GONE);
                                rvIntercambiable.setVisibility(View.VISIBLE);
                                rvIntercambiable.setAdapter(bolsaDetalleAdapter);
                                rvIntercambiable.setAlpha(0f);
                                rvIntercambiable.animate().alpha(1f).setDuration(300).start();
                            }).start();
                    productoViewModel.getBolsaIndEntregada().observe(getViewLifecycleOwner(), bolsaDetalleAdapter::setLista);
                } else {
                    rvIntercambiable.animate().alpha(0f).setDuration(300).withEndAction(() -> {
                        rvIntercambiable.setVisibility(View.GONE);
                        containerJugadores.setVisibility(View.VISIBLE);
                        containerJugadores.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(300).start();
                    }).start();
                }
            });
        }

        // --- LÓGICA DEL PANEL DE LIQUIDACIÓN (Botones) ---
        view.findViewById(R.id.btnVolverDeLiq).setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            togglePanelLiquidacion(null, false);
        });

        view.findViewById(R.id.btnConfirmarSalidaLiq).setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
            if (clienteEnLiquidacion != null) {
                // 1. Persistencia: Marcar estado FINALIZADO en DB
                productoViewModel.retirarJugadorEspecificoInd(idMesaActual, clienteEnLiquidacion.idCliente);

                // 2. Limpiar timer local
                startTimesMap.remove(clienteEnLiquidacion.idCliente);

                // 3. Animación de salida
                togglePanelLiquidacion(null, false);

                // 4. Refrescar la Arena (esperamos un poco a la DB)
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    participantes = productoViewModel.getIntegrantesAzulCacheados();
                    setupMiniMarcadores(getView());
                }, 350);

                Toast.makeText(getContext(), clienteEnLiquidacion.alias.toUpperCase() + " ha salido de la arena", Toast.LENGTH_SHORT).show();
                clienteEnLiquidacion = null;
            }
        });

        // --- LÓGICA DEL BOTÓN LOG (HISTORIAL AGRUPADO ARENA) ---
        view.findViewById(R.id.btnVerLog).setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            Map<Integer, Integer> mapaColoresInd = new HashMap<>();
            for (int i = 0; i < participantes.size(); i++) {
                mapaColoresInd.put(participantes.get(i).idCliente, COLORES_NEON[i % COLORES_NEON.length]);
            }
            logDeudaAdapter.setMapaColores(mapaColoresInd);

            LinearLayout containerJugadores = view.findViewById(R.id.containerJugadores);
            if (containerJugadores.getVisibility() == View.VISIBLE) {
                containerJugadores.animate().alpha(0f).scaleX(0.95f).scaleY(0.95f).setDuration(300).withEndAction(() -> {
                    containerJugadores.setVisibility(View.GONE);
                    rvIntercambiable.setVisibility(View.VISIBLE);
                    rvIntercambiable.setAlpha(0f);
                    rvIntercambiable.setAdapter(logDeudaAdapter);
                    productoViewModel.obtenerLogAgrupado(idMesaActual).observe(getViewLifecycleOwner(), listaAgrupada -> {
                        if (listaAgrupada != null) logDeudaAdapter.setListaAgrupada(listaAgrupada);
                    });
                    rvIntercambiable.animate().alpha(1f).setDuration(300).start();
                }).start();
                ((MaterialButton)v).setText("VOLVER");
                ((MaterialButton)v).setIconResource(R.drawable.ic_arrow_back);
            } else {
                rvIntercambiable.animate().alpha(0f).setDuration(300).withEndAction(() -> {
                    rvIntercambiable.setVisibility(View.GONE);
                    containerJugadores.setVisibility(View.VISIBLE);
                    containerJugadores.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(300).start();
                }).start();
                ((MaterialButton)v).setText("LOG");
                ((MaterialButton)v).setIconResource(R.drawable.ic_history);
            }
        });

        // --- SECCIÓN CONFIGURACIÓN Y REGLAS ---
        MaterialButton btnNivAficionado = view.findViewById(R.id.btnNivelAficionado);
        MaterialButton btnNivIntermedio = view.findViewById(R.id.btnNivelIntermedio);
        MaterialButton btnNivAvanzado = view.findViewById(R.id.btnNivelAvanzado);
        MaterialButton btnNivPro = view.findViewById(R.id.btnNivelPro);
        List<MaterialButton> listaBotonesNivel = Arrays.asList(btnNivAficionado, btnNivIntermedio, btnNivAvanzado, btnNivPro);

        btnNivAficionado.setOnClickListener(v -> { guardarPerfilDuelo(20, "Aficionado"); resaltarBoton(btnNivAficionado, listaBotonesNivel, "#00E5FF"); });
        btnNivIntermedio.setOnClickListener(v -> { guardarPerfilDuelo(25, "Intermedio"); resaltarBoton(btnNivIntermedio, listaBotonesNivel, "#FFD54F"); });
        btnNivAvanzado.setOnClickListener(v -> { guardarPerfilDuelo(30, "Avanzado"); resaltarBoton(btnNivAvanzado, listaBotonesNivel, "#4CAF50"); });
        btnNivPro.setOnClickListener(v -> { guardarPerfilDuelo(40, "Profesional"); resaltarBoton(btnNivPro, listaBotonesNivel, "#FF1744"); });

        MaterialButton btnReglaPerdedores = view.findViewById(R.id.btnReglaPerdedores);
        MaterialButton btnReglaTodos = view.findViewById(R.id.btnReglaTodos);
        MaterialButton btnReglaUltimo = view.findViewById(R.id.btnReglaUltimo);
        List<MaterialButton> listaBotonesReglas = Arrays.asList(btnReglaPerdedores, btnReglaTodos, btnReglaUltimo);

        btnReglaPerdedores.setOnClickListener(v -> { productoViewModel.setReglaPagoInd("PERDEDORES"); resaltarBoton(btnReglaPerdedores, listaBotonesReglas, "#FFFFFF"); });
        btnReglaTodos.setOnClickListener(v -> { productoViewModel.setReglaPagoInd("TODOS"); resaltarBoton(btnReglaTodos, listaBotonesReglas, "#FFFFFF"); });
        btnReglaUltimo.setOnClickListener(v -> { productoViewModel.setReglaPagoInd("ULTIMO"); resaltarBoton(btnReglaUltimo, listaBotonesReglas, "#FFFFFF"); });

        com.google.android.material.switchmaterial.SwitchMaterial swPin = view.findViewById(R.id.switchRequierePin);
        swPin.setOnClickListener(v -> productoViewModel.actualizarSeguridadPinDuelo(swPin.isChecked()));

        // --- OBSERVADORES ---
        productoViewModel.getPerfilDueloInd(idMesaActual).observe(getViewLifecycleOwner(), perfil -> {
            if (perfil != null) {
                this.metaPuntosActual = perfil.metaPuntos;
                if (perfil.metaPuntos == 20) resaltarBoton(btnNivAficionado, listaBotonesNivel, "#00E5FF");
                else if (perfil.metaPuntos == 25) resaltarBoton(btnNivIntermedio, listaBotonesNivel, "#FFD54F");
                else if (perfil.metaPuntos == 30) resaltarBoton(btnNivAvanzado, listaBotonesNivel, "#4CAF50");
                else if (perfil.metaPuntos == 40) resaltarBoton(btnNivPro, listaBotonesNivel, "#FF1744");
            }
        });

        productoViewModel.getReglaPagoInd().observe(getViewLifecycleOwner(), regla -> {
            if (regla != null) {
                if ("PERDEDORES".equals(regla)) resaltarBoton(btnReglaPerdedores, listaBotonesReglas, "#FFFFFF");
                else if ("TODOS".equals(regla)) resaltarBoton(btnReglaTodos, listaBotonesReglas, "#FFFFFF");
                else if ("ULTIMO".equals(regla)) resaltarBoton(btnReglaUltimo, listaBotonesReglas, "#FFFFFF");
            }
        });

        productoViewModel.getRequierePinDuelo().observe(getViewLifecycleOwner(), swPin::setChecked);

        // --- BOTONES DE NAVEGACIÓN ---
        view.findViewById(R.id.btnVerPendientes).setOnClickListener(v -> togglePanelDespacho(true));
        view.findViewById(R.id.btnCerrarDespacho).setOnClickListener(v -> togglePanelDespacho(false));
        view.findViewById(R.id.fabSeleccionarMunicion).setOnClickListener(v -> abrirCatalogo());
        view.findViewById(R.id.btnFinalizarDuelo).setOnClickListener(v -> mostrarDialogoFinal());
        view.findViewById(R.id.btnConfigDueloInd).setOnClickListener(v -> toggleConfigPanel(true));
        view.findViewById(R.id.btnCerrarConfigInd).setOnClickListener(v -> toggleConfigPanel(false));
        view.findViewById(R.id.btnAgregarJugadorInd).setOnClickListener(v -> abrirPanelReclutamientoPro());

        btnConfirmarRecluta.setOnClickListener(v -> ejecutarCargaPro());
        view.findViewById(R.id.btnCancelarRecluta).setOnClickListener(v -> togglePanelRecluta(false));

        setupMiniMarcadores(view);
        setupObservadores();
        startGlobalTimer();
        productoViewModel.iniciarDueloIndPersistente(participantes, idMesaActual);
    }

    private void abrirPanelReclutamientoPro() {
        clienteViewModel.getClientesActivos().observe(getViewLifecycleOwner(), todos -> {
            List<Integer> idsOcupados = productoViewModel.obtenerIdsParticipantesArena();
            List<com.nodo.tpv.data.dto.ClienteConSaldo> filtrados = new ArrayList<>();

            if (todos != null) {
                for (com.nodo.tpv.data.dto.ClienteConSaldo c : todos) {
                    if (!idsOcupados.contains(c.cliente.idCliente)) filtrados.add(c);
                }
            }

            int colorDisponible = COLORES_NEON[participantes.size() % COLORES_NEON.length];

            // 🔥 Creamos el adaptador
            ClienteAdapter reclutaAdapter = new ClienteAdapter() {
                private int idSeleccionado = -1; // Local para el brillo

                @Override
                public void onBindViewHolder(@NonNull ClienteViewHolder holder, int position) {
                    super.onBindViewHolder(holder, position);
                    Cliente cliente = filtrados.get(position).cliente;

                    // Diseño Ghost
                    holder.itemView.setBackgroundColor(Color.TRANSPARENT);
                    if (holder.itemView instanceof MaterialCardView) {
                        MaterialCardView card = (MaterialCardView) holder.itemView;
                        card.setCardBackgroundColor(Color.TRANSPARENT);
                        card.setStrokeWidth(0);
                        card.setCardElevation(0);
                    }

                    holder.itemView.findViewById(R.id.tvAmount).setVisibility(View.GONE);
                    holder.itemView.findViewById(R.id.btnView).setVisibility(View.GONE);
                    holder.itemView.findViewById(R.id.btnPay).setVisibility(View.GONE);
                    holder.itemView.findViewById(R.id.btnAdd).setVisibility(View.GONE);

                    holder.tvAlias.setTextColor(Color.WHITE);
                    holder.tvAlias.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);

                    View bgIcono = holder.itemView.findViewById(R.id.bgIcono);
                    ImageView ivIcono = holder.itemView.findViewById(R.id.ivRolIcon);

                    // 🔥 Lógica de resaltado corregida
                    if (cliente.idCliente == idSeleccionado) {
                        bgIcono.setBackgroundTintList(ColorStateList.valueOf(colorDisponible));
                        if (ivIcono != null) ivIcono.setColorFilter(Color.WHITE);
                        holder.tvAlias.setShadowLayer(15, 0, 0, colorDisponible);
                        holder.tvAlias.setScaleX(1.15f);
                        holder.tvAlias.setScaleY(1.15f);
                    } else {
                        bgIcono.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#25FFFFFF")));
                        if (ivIcono != null) ivIcono.setColorFilter(Color.parseColor("#80FFFFFF"));
                        holder.tvAlias.setShadowLayer(0, 0, 0, Color.TRANSPARENT);
                        holder.tvAlias.setScaleX(1.0f);
                        holder.tvAlias.setScaleY(1.0f);
                    }

                    // 🔥 ASIGNACIÓN DIRECTA DEL CLICK (Solución al problema de selección)
                    holder.itemView.setOnClickListener(v -> {
                        idSeleccionado = cliente.idCliente;
                        clienteSeleccionadoPro = cliente;

                        v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);

                        // Configurar el botón de confirmar
                        btnConfirmarRecluta.setEnabled(true);
                        btnConfirmarRecluta.setText("RECLUTAR A " + cliente.alias.toUpperCase());
                        btnConfirmarRecluta.setStrokeColor(ColorStateList.valueOf(colorDisponible));
                        btnConfirmarRecluta.setTextColor(colorDisponible);

                        notifyDataSetChanged(); // Refresca para aplicar el color neón
                    });
                }
            };

            rvRecluta.setLayoutManager(new GridLayoutManager(getContext(), 4));
            rvRecluta.setAdapter(reclutaAdapter);
            reclutaAdapter.setClientes(filtrados);
            reclutaAdapter.setModoSeleccionVersus(true);

            togglePanelRecluta(true);
        });
    }

    private void togglePanelRecluta(boolean mostrar) {
        int screenHeight = getResources().getDisplayMetrics().heightPixels;
        if (mostrar) {
            panelRecluta.setVisibility(View.VISIBLE);
            panelRecluta.setAlpha(0f);
            panelRecluta.setTranslationY(screenHeight * 0.1f);
            panelRecluta.animate().translationY(0).alpha(1f).setDuration(500).setInterpolator(new DecelerateInterpolator()).start();
        } else {
            panelRecluta.animate().translationY(screenHeight * 0.2f).alpha(0f).setDuration(400).withEndAction(() -> panelRecluta.setVisibility(View.GONE)).start();
            clienteSeleccionadoPro = null;
            btnConfirmarRecluta.setEnabled(false);
            btnConfirmarRecluta.setText("TOCA UN CLIENTE");
        }
    }

    private void ejecutarCargaPro() {
        if (clienteSeleccionadoPro != null) {
            // Ejecutar la adición en el ViewModel
            productoViewModel.agregarJugadorADueloIndActivo(idMesaActual, clienteSeleccionadoPro);

            Toast.makeText(getContext(), clienteSeleccionadoPro.alias.toUpperCase() + " sumado a la mesa", Toast.LENGTH_SHORT).show();

            // Cerrar el panel
            togglePanelRecluta(false);

            // Limpiar el objeto para la siguiente vez
            clienteSeleccionadoPro = null;
        } else {
            Toast.makeText(getContext(), "Seleccione un cliente primero", Toast.LENGTH_SHORT).show();
        }
    }

    private void toggleConfigPanel(boolean mostrar) {
        if (configVisible == mostrar) return;
        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        if (mostrar) {
            panelConfigInd.setVisibility(View.VISIBLE);
            panelConfigInd.setTranslationX(screenWidth);
            panelConfigInd.animate().translationX(0f).setDuration(400).setInterpolator(new DecelerateInterpolator()).start();
        } else {
            panelConfigInd.animate().translationX(screenWidth).setDuration(400).withEndAction(() -> panelConfigInd.setVisibility(View.GONE)).start();
        }
        configVisible = mostrar;
    }

    private void guardarPerfilDuelo(int puntos, String nivel) {
        this.metaPuntosActual = puntos;
        productoViewModel.actualizarPerfilDueloInd(idMesaActual, puntos, nivel);
        tvHeaderBillar.setText("MESA #" + idMesaActual + " | META: " + puntos);
        actualizarMetasEnCards();
        toggleConfigPanel(false);
    }

    private void setupMiniMarcadores(View root) {
        LinearLayout container = root.findViewById(R.id.containerJugadores);
        if (container == null) return;

        // 1. Limpiamos el contenedor
        container.removeAllViews();

        for (int i = 0; i < participantes.size(); i++) {
            Cliente cliente = participantes.get(i);
            int color = COLORES_NEON[i % COLORES_NEON.length];
            final int indexFinal = i;

            // Inicializar estados si el jugador es nuevo
            if (!tiempoAcumuladoPorJugador.containsKey(cliente.idCliente)) {
                tiempoAcumuladoPorJugador.put(cliente.idCliente, 0L);
                estadoPausaPorJugador.put(cliente.idCliente, false);
                miniMarcadoresPorJugador.put(cliente.idCliente, 0);
            }

            // 2. Inflar Card
            View cardView = getLayoutInflater().inflate(R.layout.item_marcador_individual, container, false);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f);
            params.setMargins(10, 10, 10, 10);
            cardView.setLayoutParams(params);

            MaterialCardView mCard = (MaterialCardView) cardView;
            mCard.setStrokeColor(ColorStateList.valueOf(color));

            // Vinculamos Vistas
            TextView tvNombre = cardView.findViewById(R.id.tvNombreInd);
            TextView tvScore = cardView.findViewById(R.id.tvScoreInd);
            TextView tvMiniCounter = cardView.findViewById(R.id.tvMiniCounterInd);
            TextView tvMetaMini = cardView.findViewById(R.id.tvMetaMiniInd);
            TextView tvDeuda = cardView.findViewById(R.id.tvDeudaIndividual); // Burbuja de deuda
            TextView tvTiempo = cardView.findViewById(R.id.tvTiempoInd); // Reloj
            ImageButton btnAdd = cardView.findViewById(R.id.btnSumarPunto);
            ImageButton btnSub = cardView.findViewById(R.id.btnRestarPunto);
            ImageButton btnPausa = cardView.findViewById(R.id.btnPausaReanudarInd);
            ImageButton btnQuitarJugadorInd = cardView.findViewById(R.id.btnQuitarJugadorInd);


            // Configuración inicial de textos y colores
            tvNombre.setText(cliente.alias.toUpperCase());
            tvNombre.setTextColor(color);
            tvScore.setTag(cliente.idCliente); // Tag para el re-hidratador global
            btnAdd.setImageTintList(ColorStateList.valueOf(color));
            btnSub.setImageTintList(ColorStateList.valueOf(color));

            final int idClienteFinal = cliente.idCliente;
            final TextView tvMiniFinal = tvMiniCounter;
            final View cardFinal = cardView;

            tvMiniFinal.setText(String.valueOf(miniMarcadoresPorJugador.get(idClienteFinal)));
            if (tvMetaMini != null) tvMetaMini.setText(String.valueOf(metaPuntosActual));

            // --- 🔥 OBSERVADOR DE DEUDA EN TIEMPO REAL ---
            // Esto soluciona que el valor de la deuda aparezca y se refresque
            productoViewModel.obtenerSaldoIndividualDuelo(idClienteFinal).observe(getViewLifecycleOwner(), saldo -> {
                if (tvDeuda != null) {
                    NumberFormat format = NumberFormat.getCurrencyInstance(new Locale("es", "CO"));
                    format.setMaximumFractionDigits(0);
                    tvDeuda.setText("DEUDA: " + format.format(saldo != null ? saldo : BigDecimal.ZERO));
                    tvDeuda.setTextColor(color); // Mantener el estilo neón
                }
            });

            btnQuitarJugadorInd.setOnClickListener(v -> {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                togglePanelLiquidacion(cliente, true);
            });

            // 3. Listener Pausa
            if (btnPausa != null) {
                btnPausa.setOnClickListener(v -> {
                    boolean estaPausado = estadoPausaPorJugador.getOrDefault(idClienteFinal, false);
                    estadoPausaPorJugador.put(idClienteFinal, !estaPausado);
                    btnPausa.setImageResource(!estaPausado ? R.drawable.ic_play_circle : R.drawable.ic_pause_circle);
                    btnPausa.setImageTintList(ColorStateList.valueOf(!estaPausado ? Color.GREEN : Color.parseColor("#FFD600")));
                });
            }

            // 4. Listener Sumar (Con reseteo masivo y snapshot)
            btnAdd.setOnClickListener(v -> {
                int actual = miniMarcadoresPorJugador.get(idClienteFinal);
                if (actual >= metaPuntosActual) return;

                actual++;
                miniMarcadoresPorJugador.put(idClienteFinal, actual);
                tvMiniFinal.setText(String.valueOf(actual));
                v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);

                if (actual >= metaPuntosActual) {
                    com.google.android.material.switchmaterial.SwitchMaterial swPin = root.findViewById(R.id.switchRequierePin);
                    boolean requierePin = (swPin != null) ? swPin.isChecked() :
                            (productoViewModel.getRequierePinDuelo().getValue() != null &&
                                    productoViewModel.getRequierePinDuelo().getValue());

                    if (requierePin) {
                        confirmarImpactoOficial(indexFinal, tvMiniFinal);
                    } else {
                        dispararCelebracion();
                        aplicarEfectoImpacto(cardFinal, color);

                        // A. Foto antes de borrar
                        Map<Integer, Integer> snapshotActual = new HashMap<>(miniMarcadoresPorJugador);

                        // B. Registro en ViewModel (Dispara deuda y Log)
                        productoViewModel.aplicarDanioInd(idClienteFinal, participantes, snapshotActual);

                        // C. Reseteo Lógico
                        for (Cliente c : participantes) {
                            miniMarcadoresPorJugador.put(c.idCliente, 0);
                        }

                        // D. Reseteo Visual tras un momento de gloria
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            for (int j = 0; j < container.getChildCount(); j++) {
                                View child = container.getChildAt(j);
                                TextView miniText = child.findViewById(R.id.tvMiniCounterInd);
                                if (miniText != null) miniText.setText("0");
                            }
                        }, 1500);

                        Toast.makeText(getContext(), "¡RONDA FINALIZADA!", Toast.LENGTH_SHORT).show();
                    }
                }
            });

            // 5. Listener Restar
            btnSub.setOnClickListener(v -> {
                int val = miniMarcadoresPorJugador.get(idClienteFinal);
                if (val > 0) {
                    val--;
                    miniMarcadoresPorJugador.put(idClienteFinal, val);
                    tvMiniFinal.setText(String.valueOf(val));
                }
            });

            container.addView(cardView);
        }

        // 6. REHIDRATACIÓN DE SCORES GLOBALES (Marcador grande)
        Map<Integer, Integer> scoresActuales = productoViewModel.getScoresIndividualesInd().getValue();
        if (scoresActuales != null) {
            for (int j = 0; j < container.getChildCount(); j++) {
                View card = container.getChildAt(j);
                TextView tvScoreCard = card.findViewById(R.id.tvScoreInd);
                if (tvScoreCard != null && tvScoreCard.getTag() != null) {
                    int id = (int) tvScoreCard.getTag();
                    if (scoresActuales.containsKey(id)) {
                        tvScoreCard.setText(String.valueOf(scoresActuales.get(id)));
                    }
                }
            }
        }
    }

    private void confirmarImpactoOficial(int index, TextView tvMini) {
        final int idClienteDuelo = participantes.get(index).idCliente;
        final String alias = participantes.get(index).alias;

        View vPin = getLayoutInflater().inflate(R.layout.dialog_pin_seguridad, null);
        EditText etPin = vPin.findViewById(R.id.etPin);

        new MaterialAlertDialogBuilder(requireContext(), R.style.MaterialAlertDialog_Garena)
                .setTitle("META ALCANZADA")
                .setMessage("¿Registrar impacto oficial para " + alias.toUpperCase() + "?")
                .setView(vPin)
                .setCancelable(false)
                .setPositiveButton("REGISTRAR", (d, w) -> {
                    String pinIngresado = etPin.getText().toString();

                    if (PIN_MAESTRO.equals(pinIngresado)) {
                        dispararCelebracion();

                        // 1. Snapshot antes de limpiar
                        Map<Integer, Integer> snapshotActual = new HashMap<>(miniMarcadoresPorJugador);

                        // 2. ViewModel registra daño e hito
                        productoViewModel.aplicarDanioInd(idClienteDuelo, participantes, snapshotActual);

                        // 3. 🔥 RESETEO MASIVO: Todos vuelven a cero
                        for (Cliente c : participantes) {
                            miniMarcadoresPorJugador.put(c.idCliente, 0);
                        }

                        // 4. Refrescar UI (Tarjetas a cero)
                        new Handler(Looper.getMainLooper()).postDelayed(() -> {
                            setupMiniMarcadores(getView());
                        }, 1000);

                        Toast.makeText(getContext(), "¡PUNTO REGISTRADO!", Toast.LENGTH_SHORT).show();

                    } else {
                        vPin.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                        Toast.makeText(getContext(), "PIN INCORRECTO", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("SEGUIR SUMANDO", null)
                .show();
    }

    private void setupObservadores() {
        // Dentro de setupObservadores() en FragmentArenaDueloInd.java
        productoViewModel.obtenerScoresDesdePersistencia(idMesaActual).observe(getViewLifecycleOwner(), listaDuelos -> {
            if (listaDuelos != null) {
                Map<Integer, Integer> mapScores = new HashMap<>();
                for (DueloTemporalInd d : listaDuelos) {
                    // d.score es el valor persistido en la DB
                    mapScores.put(d.idCliente, d.score);
                }

                // Sincronizamos el LiveData del ViewModel para que las CardViews se refresquen
                productoViewModel.setScoresIndividualesManual(mapScores);
            }
        });
        // 1. BOLSA CONFIRMADA (Productos ENTREGADOS con idCliente = 0)
        productoViewModel.getBolsaIndEntregada().observe(getViewLifecycleOwner(), productos -> {
            if (productos != null) {
                actualizarUIBolsa(productos);
                if (bolsaDetalleAdapter != null) {
                    bolsaDetalleAdapter.setLista(productos);
                }
            }
        });

        // 2. BADGE DE NOTIFICACIÓN (Pedidos PENDIENTES)
        productoViewModel.observarConteoPendientesMesa(idMesaActual).observe(getViewLifecycleOwner(), conteo -> {
            TextView tvBadge = getView().findViewById(R.id.tvBadgePendientes);
            if (conteo != null && conteo > 0) {
                if (tvBadge != null) {
                    tvBadge.setVisibility(View.VISIBLE);
                    tvBadge.setText(String.valueOf(conteo));
                    tvBadge.animate().scaleX(1.3f).scaleY(1.3f).setDuration(250)
                            .withEndAction(() -> tvBadge.animate().scaleX(1.0f).scaleY(1.0f).setDuration(250).start())
                            .start();
                }
            } else {
                if (tvBadge != null) tvBadge.setVisibility(View.GONE);
            }
        });

        // Busca este bloque dentro de setupObservadores
        productoViewModel.getDbTrigger().observe(getViewLifecycleOwner(), trigger -> {
            participantes = productoViewModel.getIntegrantesAzulCacheados();
            setupMiniMarcadores(getView());

            LinearLayout container = getView() != null ? getView().findViewById(R.id.containerJugadores) : null;
            if (container == null) return;

            for (int i = 0; i < participantes.size(); i++) {
                final int idCliente = participantes.get(i).idCliente;
                final int index = i;

                // --- CORRECCIÓN CRONÓMETRO ---
                // Traemos el tiempo de inicio de la DB y lo ponemos en el mapa que usa el Runnable
                new Thread(() -> {
                    long startTime = productoViewModel.obtenerTimestampInicioPorCliente(idMesaActual, idCliente);
                    if (startTime > 0) {
                        startTimesMap.put(idCliente, startTime);
                    }
                }).start();

                // --- CORRECCIÓN REFRESCO DEUDA ---
                // Buscamos la tarjeta ya dibujada para actualizar solo su texto de deuda
                View card = container.getChildAt(index);
                if (card != null) {
                    TextView tvDeuda = card.findViewById(R.id.tvDeudaIndividual);
                    // Observamos el saldo y actualizamos directamente el TextView
                    productoViewModel.obtenerSaldoIndividualDuelo(idCliente).observe(getViewLifecycleOwner(), saldo -> {
                        if (tvDeuda != null) {
                            NumberFormat format = NumberFormat.getCurrencyInstance(new Locale("es", "CO"));
                            format.setMaximumFractionDigits(0);
                            tvDeuda.setText("DEUDA: " + format.format(saldo != null ? saldo : BigDecimal.ZERO));
                        }
                    });
                }
            }
        });

        // 4. CONFIGURACIÓN DEL DUELO (Meta de puntos)
        productoViewModel.getPerfilDueloInd(idMesaActual).observe(getViewLifecycleOwner(), perfil -> {
            if (perfil != null) {
                this.metaPuntosActual = perfil.metaPuntos;
                if (tvHeaderBillar != null) {
                    tvHeaderBillar.setText("MESA #" + idMesaActual + " | META: " + metaPuntosActual);
                }
                actualizarMetasEnCards();
            }
        });

        // Dentro de setupObservadores() en FragmentArenaDueloInd.java

        productoViewModel.getScoresIndividualesInd().observe(getViewLifecycleOwner(), scoresMap -> {
            if (scoresMap == null) return;

            LinearLayout container = getView().findViewById(R.id.containerJugadores);
            if (container == null) return;

            // Recorremos las tarjetas que ya existen en el contenedor
            for (int i = 0; i < container.getChildCount(); i++) {
                View card = container.getChildAt(i);
                TextView tvScore = card.findViewById(R.id.tvScoreInd);

                if (tvScore != null && tvScore.getTag() != null) {
                    int clienteId = (int) tvScore.getTag();
                    if (scoresMap.containsKey(clienteId)) {
                        // Actualizamos el número grande (Global)
                        tvScore.setText(String.valueOf(scoresMap.get(clienteId)));
                    }
                }
            }
        });
    }

    private void actualizarMetasEnCards() {
        LinearLayout container = getView().findViewById(R.id.containerJugadores);
        if (container == null) return;
        for (int i = 0; i < container.getChildCount(); i++) {
            TextView tvMeta = container.getChildAt(i).findViewById(R.id.tvMetaInd);
            TextView tvMetaMini = container.getChildAt(i).findViewById(R.id.tvMetaMiniInd);
            if (tvMeta != null) tvMeta.setText("MODO: " + metaPuntosActual + " PTS");
            if (tvMetaMini != null) tvMetaMini.setText(String.valueOf(metaPuntosActual));
        }
    }

    private void aplicarEfectoImpacto(View card, int colorNeon) {
        card.animate().scaleX(1.15f).scaleY(1.15f).setDuration(150).withEndAction(() -> {
            card.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start();
        }).start();
        if (card instanceof MaterialCardView) {
            MaterialCardView mCard = (MaterialCardView) card;
            mCard.setStrokeWidth(15);
            new Handler(Looper.getMainLooper()).postDelayed(() -> mCard.setStrokeWidth(6), 400);
        }
    }

    // En FragmentArenaDueloInd.java

    private void actualizarUIBolsa(List<Producto> productos) {
        if (productos == null || productos.isEmpty()) {
            tvItemsBolsa.setText("Cargue munición...");
            tvValorEnJuego.setText("BOLSA: $0");
        } else {
            StringBuilder sb = new StringBuilder();
            BigDecimal total = BigDecimal.ZERO;

            for (Producto p : productos) {
                // Concatenamos cada producto en una nueva línea para tvItemsBolsa
                sb.append("• ").append(p.getNombreProducto().toUpperCase()).append("\n");
                total = total.add(p.getPrecioProducto());
            }

            tvItemsBolsa.setText(sb.toString().trim());

            NumberFormat format = NumberFormat.getCurrencyInstance(new Locale("es", "CO"));
            tvValorEnJuego.setText("BOLSA: " + format.format(total));
        }
    }

    private void startGlobalTimer() {
        isTimerRunning = true;
        timerHandler.post(timerRunnable);
    }

    // En FragmentArenaDueloInd.java

    private Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isTimerRunning) return;

            LinearLayout container = getView() != null ? getView().findViewById(R.id.containerJugadores) : null;
            if (container != null) {
                long ahora = System.currentTimeMillis();
                for (int i = 0; i < container.getChildCount(); i++) {
                    View card = container.getChildAt(i);
                    TextView tvTimer = card.findViewById(R.id.tvTiempoInd);
                    // Usamos el tag que pusimos en setupMiniMarcadores
                    TextView tvScore = card.findViewById(R.id.tvScoreInd);

                    if (tvScore != null && tvScore.getTag() != null) {
                        int idCliente = (int) tvScore.getTag();

                        if (estadoPausaPorJugador.getOrDefault(idCliente, false)) continue;

                        Long startTime = startTimesMap.get(idCliente);
                        if (startTime != null && startTime > 0) {
                            long diff = ahora - startTime;
                            int seconds = (int) (diff / 1000);
                            int minutes = seconds / 60;
                            int hours = minutes / 60;
                            tvTimer.setText(String.format(Locale.getDefault(),
                                    "%02d:%02d:%02d", hours, minutes % 60, seconds % 60));
                        }
                    }
                }
            }
            timerHandler.postDelayed(this, 1000); // Re-ejecutar cada segundo
        }
    };

    private void mostrarDialogoReglas() {
        String[] opciones = {"PAGAN PERDEDORES", "REPARTO EQUITATIVO", "PAGA EL ÚLTIMO"};
        new MaterialAlertDialogBuilder(requireContext(), R.style.MaterialAlertDialog_Garena)
                .setTitle("REGLA DE REPARTO")
                .setItems(opciones, (dialog, which) -> {
                    String regla = which == 0 ? "PERDEDORES" : which == 1 ? "TODOS" : "ULTIMO";
                    productoViewModel.setReglaPagoInd(regla);
                }).show();
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
                .replace(R.id.container_fragments, fragment)
                .addToBackStack(null).commit();
    }

    private void mostrarDialogoFinal() {
        new MaterialAlertDialogBuilder(requireContext(), R.style.MaterialAlertDialog_Garena)
                .setTitle("FINALIZAR DUELO")
                .setPositiveButton("SÍ", (d, w) -> {
                    // Pasamos el ID de la mesa y el tag de 3 Bandas
                    productoViewModel.finalizarDueloCompleto(idMesaActual, "3BANDAS");
                    getParentFragmentManager().popBackStack();
                }).show();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (requireActivity() instanceof MainActivity) {
            ((MainActivity) requireActivity()).setExpandirContenedor(true);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        isTimerRunning = false;
        timerHandler.removeCallbacks(timerRunnable);
    }

    private void togglePanelDespacho(boolean mostrar) {
        View panel = getView().findViewById(R.id.panelDespachoBolsa);
        int screenWidth = getResources().getDisplayMetrics().widthPixels;

        if (mostrar) {
            panel.setVisibility(View.VISIBLE);
            panel.setTranslationX(screenWidth); // Asegurar que inicie en la derecha
            panel.animate()
                    .translationX(0)
                    .setDuration(400)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();

            // Cargar los datos al abrir
            cargarPendientesParaDespacho();
        } else {
            panel.animate()
                    .translationX(screenWidth)
                    .setDuration(400)
                    .withEndAction(() -> panel.setVisibility(View.GONE))
                    .start();
        }
    }

    private void cargarPendientesParaDespacho() {
        RecyclerView rv = getView().findViewById(R.id.rvDespachoBolsa);
        rv.setLayoutManager(new LinearLayoutManager(getContext()));

        productoViewModel.obtenerSoloPendientesMesa(idMesaActual).observe(getViewLifecycleOwner(), lista -> {
            if (lista == null || lista.isEmpty()) {
                // Si ya no hay nada, mostramos un mensaje o cerramos
                togglePanelDespacho(false);
                return;
            }

            RecyclerView.Adapter adapter = new RecyclerView.Adapter<DespachoViewHolder>() {
                @NonNull
                @Override
                public DespachoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                    View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_despacho_bolsa, parent, false);
                    return new DespachoViewHolder(v);
                }

                @Override
                public void onBindViewHolder(@NonNull DespachoViewHolder holder, int position) {
                    com.nodo.tpv.data.dto.DetalleHistorialDuelo item = lista.get(position);
                    holder.tvNombre.setText(item.nombreProducto.toUpperCase());

                    holder.btnConfirmar.setOnClickListener(v -> {
                        v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);

                        // 1. Obtener la sesión actual para saber quién entrega
                        com.nodo.tpv.util.SessionManager session = new com.nodo.tpv.util.SessionManager(holder.itemView.getContext());
                        com.nodo.tpv.data.entities.Usuario user = session.obtenerUsuario();

                        // Datos reales (seguridad contra nulos)
                        final int idOp = (user != null) ? user.idUsuario : 0;
                        final String loginOp = (user != null) ? user.login : "anonimo";

                        // 2. Animación de salida
                        holder.itemView.animate().alpha(0f).translationX(100f).setDuration(300).withEndAction(() -> {
                            // 3. Llamada corregida con (int idDetalle, int idUsuario, String loginOperativo)
                            productoViewModel.marcarComoEntregado(item.idDetalle, idOp, loginOp);
                        }).start();
                    });
                }

                @Override
                public int getItemCount() { return lista.size(); }
            };

            rv.setAdapter(adapter);
        });
    }

    // Clase interna para el ViewHolder del Despacho
    static class DespachoViewHolder extends RecyclerView.ViewHolder {
        TextView tvNombre;
        MaterialButton btnConfirmar;

        public DespachoViewHolder(@NonNull View itemView) {
            super(itemView);
            tvNombre = itemView.findViewById(R.id.tvNombreProdDespacho);
            btnConfirmar = itemView.findViewById(R.id.btnConfirmarItem);
        }
    }

    private void resaltarBoton(MaterialButton seleccionado, List<MaterialButton> grupo, String colorHex) {
        int colorActivo = Color.parseColor(colorHex);

        for (MaterialButton btn : grupo) {
            if (btn == seleccionado) {
                // Estilo Activo: Fondo sólido con color neón y texto negro
                btn.setBackgroundTintList(ColorStateList.valueOf(colorActivo));
                btn.setTextColor(Color.BLACK);
                btn.setIconTint(ColorStateList.valueOf(Color.BLACK));
                btn.setStrokeWidth(0); // Quitar borde en activo
            } else {
                // Estilo Inactivo: Transparente con borde blanco sutil
                btn.setBackgroundTintList(ColorStateList.valueOf(Color.TRANSPARENT));
                btn.setTextColor(Color.WHITE);
                btn.setIconTint(ColorStateList.valueOf(Color.WHITE));
                btn.setStrokeColor(ColorStateList.valueOf(Color.parseColor("#33FFFFFF")));
                btn.setStrokeWidth(2);
            }
        }
    }

    // Dentro de FragmentArenaDueloInd.java

    private void togglePanelLiquidacion(Cliente cliente, boolean mostrar) {
        // Vistas a ocultar/mostrar
        View topBar = getView().findViewById(R.id.layoutTopBar);
        LinearLayout containerJugadores = getView().findViewById(R.id.containerJugadores);
        View bottomActions = getView().findViewById(R.id.layoutAccionesInferiores);

        if (mostrar) {
            this.clienteEnLiquidacion = cliente;

            // A. Identificar color del jugador para el diseño
            int index = participantes.indexOf(cliente);
            int colorJugador = COLORES_NEON[index % COLORES_NEON.length];

            // B. Setear textos básicos
            ((TextView)panelLiquidacion.findViewById(R.id.tvNombreJugadorLiq)).setText(cliente.alias.toUpperCase());
            ((TextView)panelLiquidacion.findViewById(R.id.tvNombreJugadorLiq)).setTextColor(colorJugador);

            // C. Obtener tiempo actual del cronómetro visual
            TextView tvRelojCard = containerJugadores.getChildAt(index).findViewById(R.id.tvTiempoInd);
            if (tvRelojCard != null) {
                ((TextView)panelLiquidacion.findViewById(R.id.tvTiempoTotalLiq)).setText(tvRelojCard.getText());
            }

            // D. Cargar Saldo y Log Agrupado
            productoViewModel.obtenerSaldoIndividualDuelo(cliente.idCliente).observe(getViewLifecycleOwner(), saldo -> {
                NumberFormat format = NumberFormat.getCurrencyInstance(new Locale("es", "CO"));
                format.setMaximumFractionDigits(0);
                ((TextView)panelLiquidacion.findViewById(R.id.tvTotalPagarLiq)).setText(format.format(saldo));
            });

            // E. Configurar Adaptador y observar datos
            liquidacionAdapter.configurarProtagonista(cliente.idCliente, colorJugador);
            productoViewModel.obtenerLogAgrupado(idMesaActual).observe(getViewLifecycleOwner(), grupos -> {
                if (grupos != null) {
                    liquidacionAdapter.setListaAgrupada(grupos);
                }
            });

            // F. Animaciones de transición (Ocultar fondo / Mostrar panel)
            containerJugadores.animate().alpha(0f).scaleX(0.9f).scaleY(0.9f).setDuration(300).start();
            topBar.animate().alpha(0f).setDuration(300).start();
            bottomActions.animate().alpha(0f).translationY(100).setDuration(300).start();

            panelLiquidacion.setVisibility(View.VISIBLE);
            panelLiquidacion.setAlpha(0f);
            panelLiquidacion.setTranslationY(getResources().getDisplayMetrics().heightPixels * 0.1f);
            panelLiquidacion.animate()
                    .alpha(1f)
                    .translationY(0)
                    .setDuration(500)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();

        } else {
            // Regresar a la normalidad
            panelLiquidacion.animate()
                    .alpha(0f)
                    .translationY(getResources().getDisplayMetrics().heightPixels * 0.1f)
                    .setDuration(400)
                    .withEndAction(() -> panelLiquidacion.setVisibility(View.GONE))
                    .start();

            containerJugadores.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(400).start();
            topBar.animate().alpha(1f).setDuration(400).start();
            bottomActions.animate().alpha(1f).translationY(0).setDuration(400).start();
        }
    }

}