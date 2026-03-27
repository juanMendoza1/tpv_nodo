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
import android.widget.ImageButton;
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

    private ImageButton btnMainAction;
    private com.google.android.material.button.MaterialButton btnRegistrar, btnPagarTodo, btnHistorial, btnModoDuelo;



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
            if (btnMainAction != null) {
                btnMainAction.setVisibility(View.VISIBLE);
            }
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
                tvMesa.setTextColor(Color.WHITE); // <--- AHORA SIEMPRE SERÁ BLANCO
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
        // 1. Cierra el menú de cristal si está abierto
        if (isMenuOpen) {
            toggleMenu();
        }

        // 2. Oculta el botón principal para que no estorbe el panel de cobro
        if (btnMainAction != null) {
            btnMainAction.setVisibility(View.GONE);
        }

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
                    cardResumenPago.setVisibility(View.GONE);

                    // 3. Vuelve a mostrar el botón principal al ir a la cámara
                    if (btnMainAction != null) {
                        btnMainAction.setVisibility(View.VISIBLE);
                    }

                    FragmentCamaraSeguridad fCam = FragmentCamaraSeguridad.newInstance(cliente.idCliente, cliente.alias, montoParaEnviar);
                    abrirFragmento(fCam);
                });
            } else {
                cardResumenPago.setVisibility(View.GONE);

                // 4. Vuelve a mostrar el botón principal si no hay consumos
                if (btnMainAction != null) {
                    btnMainAction.setVisibility(View.VISIBLE);
                }

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
        // 1. Enlazamos las nuevas vistas del Panel HUD (ImageButtons)
        btnMainAction = view.findViewById(R.id.btnMainAction);
        btnRegistrar  = view.findViewById(R.id.btnRegistrar);
        btnPagarTodo  = view.findViewById(R.id.btnPagarTodo);
        btnHistorial  = view.findViewById(R.id.btnHistorial);
        btnModoDuelo  = view.findViewById(R.id.btnModoDuelo);

        // 2. Evento del botón principal (Abre/Cierra el menú)
        btnMainAction.setOnClickListener(v -> toggleMenu());

        // 3. Eventos de los botones secundarios
        btnRegistrar.setOnClickListener(v -> {
            toggleMenu(); // Cierra el menú al hacer clic
            mostrarDialogoRegistro();
        });

        btnHistorial.setOnClickListener(v -> {
            toggleMenu();
            abrirHistorial();
        });

        btnPagarTodo.setOnClickListener(v -> {
            toggleMenu();
            confirmarPagoMasivo();
        });

        btnModoDuelo.setOnClickListener(v -> {
            toggleMenu();
            if (Boolean.TRUE.equals(arenaViewModel.getEnModoDuelo().getValue())) {
                reanudarDueloPausado();
            } else if (!modoSeleccionVersus) {
                iniciarSeleccionDuelo();
            } else {
                cancelarSeleccionDuelo();
            }
        });
    }

    public void mostrarDialogoRegistro() {
        // 1. Cerramos el menú con animación si está abierto
        if (isMenuOpen) {
            toggleMenu();
        }

        // 2. Ocultamos el botón principal (Usando View.GONE en lugar de .hide())
        if (btnMainAction != null) {
            btnMainAction.setVisibility(View.GONE);
        }

        // Preparamos la vista del diálogo
        View dv = getLayoutInflater().inflate(R.layout.dialog_registrar_cliente, null);
        TextInputEditText etA = dv.findViewById(R.id.etAlias);
        MaterialAutoCompleteTextView act = dv.findViewById(R.id.actTipoCliente);

        act.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, new String[]{"INDIVIDUAL", "GRUPO"}));

        // 3. Creamos el diálogo y le decimos que vuelva a mostrar el botón al cerrarse
        AlertDialog d = new MaterialAlertDialogBuilder(requireContext())
                .setView(dv)
                .setOnDismissListener(dialog -> {
                    // Volvemos a mostrar el botón principal (Usando View.VISIBLE en lugar de .show())
                    if (btnMainAction != null) {
                        btnMainAction.setVisibility(View.VISIBLE);
                    }
                })
                .create();

        // 4. Lógica del botón de guardar
        dv.findViewById(R.id.btnGuardar).setOnClickListener(v -> {
            String a = etA.getText().toString().trim();
            if (!a.isEmpty()) {
                clienteViewModel.guardarCliente(a, act.getText().toString(), idMesaActual);
                d.dismiss();
            } else {
                // Un pequeño extra: Mostrar error si el cajón de texto está vacío
                etA.setError("El alias es obligatorio");
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
        isMenuOpen = !isMenuOpen;

        if (isMenuOpen) {
            // Gira el botón principal (el símbolo '+' se convierte en una 'x')
            btnMainAction.animate().rotation(45f).setDuration(200).start();
            btnMainAction.setColorFilter(Color.parseColor("#FF1744")); // Opcional: se pone rojo al abrir

            // Muestra los botones en cascada (con un pequeño retraso entre cada uno)
            mostrarBotonAnimado(btnRegistrar, 50);
            mostrarBotonAnimado(btnModoDuelo, 100);
            mostrarBotonAnimado(btnPagarTodo, 150);
            mostrarBotonAnimado(btnHistorial, 200);
        } else {
            // Regresa el botón principal a la normalidad
            btnMainAction.animate().rotation(0f).setDuration(200).start();
            btnMainAction.setColorFilter(Color.parseColor("#00E676")); // Vuelve a verde neón

            // Oculta los botones en cascada invertida
            ocultarBotonAnimado(btnHistorial, 0);
            ocultarBotonAnimado(btnPagarTodo, 50);
            ocultarBotonAnimado(btnModoDuelo, 100);
            ocultarBotonAnimado(btnRegistrar, 150);
        }
    }

    private void mostrarBotonAnimado(View btn, int delay) {
        btn.setVisibility(View.VISIBLE);
        btn.setAlpha(0f);
        btn.setTranslationY(20f); // Comienza un poco más abajo
        btn.animate()
                .alpha(1f)
                .translationY(0f) // Sube a su posición original
                .setStartDelay(delay)
                .setDuration(200)
                .start();
    }

    // Función auxiliar para ocultar con animación "Fade Out"
    private void ocultarBotonAnimado(View btn, int delay) {
        btn.animate()
                .alpha(0f)
                .translationY(20f) // Baja ligeramente mientras desaparece
                .setStartDelay(delay)
                .setDuration(200)
                .withEndAction(() -> btn.setVisibility(View.GONE)) // Al terminar, se quita de la pantalla
                .start();
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