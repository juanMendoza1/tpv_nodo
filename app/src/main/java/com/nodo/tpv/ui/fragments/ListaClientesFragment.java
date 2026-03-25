package com.nodo.tpv.ui.fragments;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.transition.TransitionManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.view.animation.TranslateAnimation;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.nodo.tpv.R;
import com.nodo.tpv.adapters.ClienteAdapter;
import com.nodo.tpv.adapters.DetalleTicketAdapter;
import com.nodo.tpv.data.dto.DetalleConNombre;
import com.nodo.tpv.data.entities.Cliente;
import com.nodo.tpv.viewmodel.ArenaViewModel;
import com.nodo.tpv.viewmodel.ClienteViewModel;
import com.nodo.tpv.viewmodel.PedidoViewModel;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ListaClientesFragment extends Fragment {

    private int idMesaActual;
    private String tipoJuegoMesa;
    private boolean esAdicionInd = false; // Flag para reclutamiento individual

    private final int[] COLORES_BILLAR = {
            Color.parseColor("#00E5FF"), // Azul neón
            Color.parseColor("#FF1744"), // Rojo neón
            Color.parseColor("#FFD54F"), // Amarillo neón
            Color.parseColor("#4CAF50")  // Verde neón
    };

    private boolean modoSeleccionVersus = false;
    private int equipoActual = 1;
    private List<Cliente> equipoAzul = new ArrayList<>();
    private List<Cliente> equipoRojo = new ArrayList<>();
    private Snackbar snackbarGuia;

    private RecyclerView recyclerView;
    private ClienteAdapter adapter;

    // --- LOS 3 VIEWMODELS REQUERIDOS ---
    private ClienteViewModel clienteViewModel;
    private PedidoViewModel pedidoViewModel;
    private ArenaViewModel arenaViewModel;

    private boolean isMenuOpen = false;
    private FloatingActionButton fabHistorial, fabPagarTodo, fabRegistrar, fabMain, fabModoDuelo;
    private Animation rotateOpen, rotateClose, fromBottom, toBottom;

    private MaterialCardView cardDueloEnEspera;
    private TextView tvResumenDuelo;
    private Button btnReanudarDuelo;

    // --- VARIABLES PARA EL RESUMEN CHÉVERE ---
    private MaterialCardView cardResumenPago;
    private LinearLayout layoutDetallePagoColapsable;
    private TextView tvMontoTotalResumen, tvNombreClientePago;
    private ImageView btnMinimizarPago;
    private RecyclerView rvItemsCuenta;
    private com.google.android.material.button.MaterialButton btnEjecutarPagoVisual;
    private boolean resumenEstaMinimizado = true;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            idMesaActual = getArguments().getInt("id_mesa");
            tipoJuegoMesa = getArguments().getString("tipo_juego", "POOL");
            esAdicionInd = getArguments().getBoolean("es_adicion_ind", false);
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_lista_clientes, container, false);
        setupAnimations();
        setupFloatingButtons(view);

        cardDueloEnEspera = view.findViewById(R.id.cardDueloEnEspera);
        tvResumenDuelo = view.findViewById(R.id.tvResumenDuelo);
        btnReanudarDuelo = view.findViewById(R.id.btnReanudarDuelo);

        // VINCULAR NUEVO PANEL PAGO
        cardResumenPago = view.findViewById(R.id.cardResumenPago);
        layoutDetallePagoColapsable = view.findViewById(R.id.layoutDetallePagoColapsable);
        tvMontoTotalResumen = view.findViewById(R.id.tvMontoTotalResumen);
        tvNombreClientePago = view.findViewById(R.id.tvNombreClientePago);
        btnMinimizarPago = view.findViewById(R.id.btnMinimizarPago);
        rvItemsCuenta = view.findViewById(R.id.rvItemsCuenta);
        btnEjecutarPagoVisual = view.findViewById(R.id.btnEjecutarPagoVisual);

        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        recyclerView = view.findViewById(R.id.rvClientes);
        TextView tvEmpty = view.findViewById(R.id.tvEmptyMessage);
        TextView tvTituloMesa = view.findViewById(R.id.tvTituloMesa);
        TextView tvTipoJuego = view.findViewById(R.id.tvTituloMesaActual);

        View containerDueloIncrustado = view.findViewById(R.id.containerInfoDueloIncrustado);
        TextView tvResumenIncrustado = view.findViewById(R.id.tvResumenDueloIncrustado);
        View indicadorEstado = view.findViewById(R.id.indicadorEstadoMesa);
        TextView btnReanudarIncrustado = view.findViewById(R.id.btnReanudarIncrustado);

        if (tvTituloMesa != null) tvTituloMesa.setText("MESA #" + idMesaActual);
        if (tvTipoJuego != null) tvTipoJuego.setText(tipoJuegoMesa);

        // INSTANCIACIÓN DE VIEWMODELS
        clienteViewModel = new ViewModelProvider(requireActivity()).get(ClienteViewModel.class);
        pedidoViewModel = new ViewModelProvider(requireActivity()).get(PedidoViewModel.class);
        arenaViewModel = new ViewModelProvider(requireActivity()).get(ArenaViewModel.class);

        recyclerView.setLayoutManager(new GridLayoutManager(getContext(), 3));
        adapter = new ClienteAdapter();
        recyclerView.setAdapter(adapter);

        // Lógica de colapsar resumen
        btnMinimizarPago.setOnClickListener(v -> toggleResumenVisual((ViewGroup) view));
        view.findViewById(R.id.btnCerrarResumen).setOnClickListener(v -> {
            cardResumenPago.setVisibility(View.GONE);
            fabMain.show();
        });

        arenaViewModel.getEnModoDuelo().observe(getViewLifecycleOwner(), activo -> {
            View bgDuelo = view.findViewById(R.id.bgDueloIncrustado);
            View marcadorCentral = view.findViewById(R.id.containerInfoDueloIncrustado);
            TextView tvMesa = view.findViewById(R.id.tvTituloMesa);
            TextView tvTipo = view.findViewById(R.id.tvTituloMesaActual);

            if (activo || esAdicionInd) {
                // MODO DUELO: ACTIVAR "INVASIÓN"
                bgDuelo.setVisibility(View.VISIBLE);
                marcadorCentral.setVisibility(View.VISIBLE);

                // Colores de contraste
                tvMesa.setTextColor(Color.WHITE);
                tvTipo.setTextColor(Color.parseColor("#B3FFFFFF")); // Blanco transparente
                indicadorEstado.setBackgroundColor(Color.parseColor("#FFD600")); // Amarillo

                if (esAdicionInd) {
                    tvResumenIncrustado.setText("VS");
                    btnReanudarIncrustado.setText("RECLUTANDO...");
                } else {
                    // Ponemos solo los números grandes en el centro
                    tvResumenIncrustado.setText(arenaViewModel.obtenerMarcadorActualString().replaceAll("[^0-9\\- ]", ""));
                    btnReanudarIncrustado.setText("REANUDAR DUELO");
                }
            } else {
                // MODO NORMAL: LIMPIO Y BLANCO
                bgDuelo.setVisibility(View.GONE);
                marcadorCentral.setVisibility(View.GONE);
                tvMesa.setTextColor(Color.parseColor("#1A1A1B"));
                tvTipo.setTextColor(Color.parseColor("#BDBDBD"));
                indicadorEstado.setBackgroundColor(Color.parseColor("#1B5E20")); // Verde
            }
        });

// Listener para el nuevo botón incrustado
        btnReanudarIncrustado.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            esAdicionInd = false;
            reanudarDueloPausado();
        });

        arenaViewModel.getScoreAzul().observe(getViewLifecycleOwner(), pts -> {
            if (!esAdicionInd) actualizarTextoBanner();
        });
        arenaViewModel.getScoreRojo().observe(getViewLifecycleOwner(), pts -> {
            if (!esAdicionInd) actualizarTextoBanner();
        });

        btnReanudarDuelo.setOnClickListener(v -> {
            esAdicionInd = false;
            reanudarDueloPausado();
        });

        adapter.setOnClienteClickListener(new ClienteAdapter.OnClienteClickListener() {
            @Override public void onVerClick(Cliente cliente) { activarInterfazCobroVisual(cliente, false); }
            @Override public void onAgregarClick(Cliente cliente) { abrirCatalogo(cliente.idCliente); }
            @Override public void onPagarClick(Cliente cliente) { activarInterfazCobroVisual(cliente, true); }

            @Override
            public void onLongClickVersus(Cliente cliente) {
                if (esAdicionInd) {
                    if (arenaViewModel.obtenerIdsParticipantesArena().contains(cliente.idCliente)) {
                        Toast.makeText(getContext(), "El cliente ya está participando en un duelo", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    arenaViewModel.agregarJugadorADueloIndActivo(idMesaActual, cliente);
                    esAdicionInd = false;
                    modoSeleccionVersus = false;
                    if (adapter != null) adapter.setModoSeleccionVersus(false);

                    Toast.makeText(getContext(), cliente.alias.toUpperCase() + " sumado a Mesa #" + idMesaActual, Toast.LENGTH_SHORT).show();
                    getParentFragmentManager().popBackStack();

                } else if (modoSeleccionVersus) {
                    if ("POOL".equals(tipoJuegoMesa)) adapter.excluirCliente(cliente.idCliente);
                    else gestionarSeleccionDuelo(cliente);
                }
            }

            @Override public void onShortClickVersus(Cliente cliente) {
                if (modoSeleccionVersus && "POOL".equals(tipoJuegoMesa)) {
                    adapter.rotarColorCliente(cliente.idCliente);
                    mostrarBarraGuia("Equipos configurados...");
                }
            }
        });

        clienteViewModel.getClientesPorMesa(idMesaActual).observe(getViewLifecycleOwner(), clientes -> {
            if (clientes == null || clientes.isEmpty()) {
                tvEmpty.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
            } else {
                tvEmpty.setVisibility(View.GONE);
                recyclerView.setVisibility(View.VISIBLE);

                if (Boolean.TRUE.equals(arenaViewModel.getEnModoDuelo().getValue()) || esAdicionInd) {
                    adapter.actualizarBloqueoDuelo(arenaViewModel.obtenerIdsParticipantesArena());
                }

                if (esAdicionInd) {
                    modoSeleccionVersus = true;
                    if (adapter != null) adapter.setModoSeleccionVersus(true);
                    mostrarBarraGuia("Seleccione nuevo jugador para Mesa #" + idMesaActual);
                }
                adapter.setClientes(clientes);
            }
        });
    }

    private void irAlFragmentArena() {
        Fragment fragmentArena;
        if ("3BANDAS".equals(tipoJuegoMesa)) {
            Boolean dueloActivo = arenaViewModel.getEnModoDuelo().getValue();
            List<Cliente> participantesParaArena;

            if (Boolean.TRUE.equals(dueloActivo)) {
                participantesParaArena = arenaViewModel.getIntegrantesAzulCacheados();
            } else {
                participantesParaArena = equipoAzul;
            }

            ArrayList<Integer> idsClientes = new ArrayList<>();
            if (participantesParaArena != null) {
                for (Cliente c : participantesParaArena) idsClientes.add(c.idCliente);
            }

            fragmentArena = FragmentArenaDueloInd.newInstance(idsClientes, idMesaActual);

        } else {
            Boolean dueloActivo = arenaViewModel.getEnModoDuelo().getValue();
            if (Boolean.TRUE.equals(dueloActivo)) {
                fragmentArena = FragmentArenaDuelo.newInstance(new ArrayList<>(), new ArrayList<>(), tipoJuegoMesa, idMesaActual);
            } else {
                Map<Integer, Integer> mapa = adapter.getMapaColoresPool();
                if (mapa.isEmpty()) return;
                arenaViewModel.prepararDueloPoolMultiequipo(mapa, idMesaActual);
                fragmentArena = FragmentArenaDuelo.newInstance(new ArrayList<>(), new ArrayList<>(), tipoJuegoMesa, idMesaActual);
            }
        }
        abrirFragmento(fragmentArena);
        cancelarSeleccionDuelo();
    }

    private void reanudarDueloPausado() { esAdicionInd = false; irAlFragmentArena(); }

    private void abrirFragmento(Fragment f) {
        requireActivity().getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left, android.R.anim.slide_in_left, android.R.anim.slide_out_right)
                .replace(R.id.container_fragments, f).addToBackStack(null).commit();
    }

    private void activarInterfazCobroVisual(Cliente cliente, boolean permitirPago) {
        if (isMenuOpen) toggleMenu();
        fabMain.hide();
        tvNombreClientePago.setText(cliente.alias.toUpperCase());
        btnEjecutarPagoVisual.setVisibility(permitirPago ? View.VISIBLE : View.GONE);
        rvItemsCuenta.setLayoutManager(new LinearLayoutManager(getContext()));
        DetalleTicketAdapter tAdapter = new DetalleTicketAdapter();
        rvItemsCuenta.setAdapter(tAdapter);

        // 🔥 LÓGICA DE COBRO USANDO PedidoViewModel
        pedidoViewModel.obtenerDetalleCliente(cliente.idCliente).observe(getViewLifecycleOwner(), dt -> {
            if (dt != null && !dt.isEmpty()) {
                BigDecimal total = BigDecimal.ZERO;
                for (DetalleConNombre d : dt) total = total.add(d.getSubtotal());
                tAdapter.setLista(dt);
                tvMontoTotalResumen.setText(NumberFormat.getCurrencyInstance(new Locale("es", "CO")).format(total));
                cardResumenPago.setVisibility(View.VISIBLE);
                cardResumenPago.setTranslationY(600);
                cardResumenPago.animate().translationY(0).setDuration(400).start();
                final BigDecimal montoParaEnviar = total;
                btnEjecutarPagoVisual.setOnClickListener(v -> {
                    cardResumenPago.setVisibility(View.GONE); fabMain.show();
                    FragmentCamaraSeguridad fCam = FragmentCamaraSeguridad.newInstance(cliente.idCliente, cliente.alias, montoParaEnviar);
                    abrirFragmento(fCam);
                });
            } else {
                cardResumenPago.setVisibility(View.GONE); fabMain.show();
                Toast.makeText(getContext(), "Este cliente no tiene consumos registrados", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void toggleResumenVisual(ViewGroup root) {
        resumenEstaMinimizado = !resumenEstaMinimizado;
        TransitionManager.beginDelayedTransition(root);
        layoutDetallePagoColapsable.setVisibility(resumenEstaMinimizado ? View.GONE : View.VISIBLE);
        btnMinimizarPago.setRotation(resumenEstaMinimizado ? 0 : 180);
    }

    private void setupFloatingButtons(View view) {
        fabMain = view.findViewById(R.id.fabMain);
        fabRegistrar = view.findViewById(R.id.fabRegistrar);
        fabPagarTodo = view.findViewById(R.id.fabPagarTodo);
        fabHistorial = view.findViewById(R.id.fabHistorial);
        fabModoDuelo = view.findViewById(R.id.fabModoDuelo);
        fabMain.setOnClickListener(v -> toggleMenu());
        fabRegistrar.setOnClickListener(v -> mostrarDialogoRegistro());
        fabHistorial.setOnClickListener(v -> { toggleMenu(); abrirHistorial(); });
        fabPagarTodo.setOnClickListener(v -> { toggleMenu(); confirmarPagoMasivo(); });
        fabModoDuelo.setOnClickListener(v -> {
            toggleMenu();
            if (Boolean.TRUE.equals(arenaViewModel.getEnModoDuelo().getValue())) reanudarDueloPausado();
            else if (!modoSeleccionVersus) iniciarSeleccionDuelo();
            else cancelarSeleccionDuelo();
        });
    }

    public void mostrarDialogoRegistro() {
        if (isMenuOpen) toggleMenu(); fabMain.hide();
        View dv = getLayoutInflater().inflate(R.layout.dialog_registrar_cliente, null);
        TextInputEditText etA = dv.findViewById(R.id.etAlias);
        MaterialAutoCompleteTextView act = dv.findViewById(R.id.actTipoCliente);
        act.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, new String[]{"INDIVIDUAL", "GRUPO"}));
        AlertDialog d = new MaterialAlertDialogBuilder(requireContext()).setView(dv).setOnDismissListener(dialog -> fabMain.show()).create();
        dv.findViewById(R.id.btnGuardar).setOnClickListener(v -> {
            String a = etA.getText().toString().trim();
            if (!a.isEmpty()) {
                clienteViewModel.guardarCliente(a, act.getText().toString(), idMesaActual);
                d.dismiss();
            }
        });
        d.show();
    }

    private void setupAnimations() {
        rotateOpen = new RotateAnimation(0, 45, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        rotateOpen.setDuration(300); rotateOpen.setFillAfter(true);
        rotateClose = new RotateAnimation(45, 0, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        rotateClose.setDuration(300); rotateClose.setFillAfter(true);
        fromBottom = new TranslateAnimation(0, 0, 300, 0); fromBottom.setDuration(300);
        toBottom = new TranslateAnimation(0, 0, 0, 300); toBottom.setDuration(300);
    }

    private void toggleMenu() {
        if (!isMenuOpen) { fabMain.startAnimation(rotateOpen); setFabVisibility(View.VISIBLE, fromBottom); isMenuOpen = true; }
        else { fabMain.startAnimation(rotateClose); setFabVisibility(View.GONE, toBottom); isMenuOpen = false; }
    }

    private void setFabVisibility(int v, Animation a) {
        fabRegistrar.setVisibility(v); fabPagarTodo.setVisibility(v);
        fabHistorial.setVisibility(v); fabModoDuelo.setVisibility(v);
        if (v == View.VISIBLE) {
            fabRegistrar.startAnimation(a); fabPagarTodo.startAnimation(a);
            fabHistorial.startAnimation(a); fabModoDuelo.startAnimation(a);
        }
    }

    private void iniciarSeleccionDuelo() { modoSeleccionVersus = true; if (adapter != null) adapter.setModoSeleccionVersus(true); mostrarBarraGuia("Seleccione jugadores"); }

    private void gestionarSeleccionDuelo(Cliente cliente) {
        if ("3BANDAS".equals(tipoJuegoMesa)) {
            if (equipoAzul.contains(cliente)) equipoAzul.remove(cliente);
            else { if (equipoAzul.size() < COLORES_BILLAR.length) equipoAzul.add(cliente); }
            List<Integer> ids = new ArrayList<>(); List<Integer> colores = new ArrayList<>();
            for (int i = 0; i < equipoAzul.size(); i++) { ids.add(equipoAzul.get(i).idCliente); colores.add(COLORES_BILLAR[i]); }
            if (adapter != null) adapter.setModoMulticolor(ids, colores);
        }
    }

    private void mostrarBarraGuia(String mensaje) {
        if (snackbarGuia != null) snackbarGuia.dismiss();
        snackbarGuia = Snackbar.make(requireView(), mensaje, Snackbar.LENGTH_INDEFINITE);
        if (!esAdicionInd) snackbarGuia.setAction("INICIAR ARENA", v -> irAlFragmentArena());
        snackbarGuia.show();
    }

    private void cancelarSeleccionDuelo() {
        modoSeleccionVersus = false;
        if (snackbarGuia != null) snackbarGuia.dismiss();
        if (adapter != null) adapter.limpiarSelecciones();
    }

    private void actualizarTextoBanner() { tvResumenDuelo.setText("Mesa en Duelo: " + arenaViewModel.obtenerMarcadorActualString()); }
    private void animarBannerGlow(View view) { view.setAlpha(0f); view.animate().alpha(1f).translationY(0f).setDuration(600).start(); }
    private void abrirHistorial() { requireActivity().getSupportFragmentManager().beginTransaction().replace(R.id.container_fragments, new HistorialVentasFragment()).addToBackStack(null).commit(); }
    private void abrirCatalogo(int idCliente) { requireActivity().getSupportFragmentManager().beginTransaction().replace(R.id.container_fragments, CatalogoProductosFragment.newInstance(idCliente, idMesaActual)).addToBackStack(null).commit(); }
    private void confirmarPagoMasivo() { new MaterialAlertDialogBuilder(requireContext()).setTitle("Cierre Masivo").setMessage("¿Finalizar todas las cuentas?").setPositiveButton("SÍ", (d, w) -> {}).show(); }
}