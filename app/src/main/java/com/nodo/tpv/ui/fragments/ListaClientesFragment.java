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
    private boolean esAdicionInd = false;

    private ImageButton btnMainAction;
    private com.google.android.material.button.MaterialButton btnRegistrar, btnPagarTodo, btnHistorial, btnModoDuelo;

    // 🔥 COLOR AZUL MÁS PRO Y OSCURO (Deep Blue A400) EN LUGAR DEL AGUAMARINA
    private final int[] COLORES_BILLAR = {
            Color.parseColor("#2962FF"), // Azul Pro Premium
            Color.parseColor("#FF1744"), // Rojo neón
            Color.parseColor("#FFD54F"), // Amarillo neón
            Color.parseColor("#4CAF50")  // Verde neón
    };

    private boolean modoSeleccionVersus = false;
    private int equipoActual = 1;
    private List<Cliente> equipoAzul = new ArrayList<>();
    private List<Cliente> equipoRojo = new ArrayList<>();
    //private Snackbar snackbarGuia;

    // VARIABLES PARA EL MODO SELECCIÓN INTEGRADO
    private View containerSeleccionVersus;
    private TextView tvInstruccionSeleccion;
    private View btnCancelarVersus;
    private View btnIniciarVersus;

    private RecyclerView recyclerView;
    private ClienteAdapter adapter;

    private ClienteViewModel clienteViewModel;
    private PedidoViewModel pedidoViewModel;
    private ArenaViewModel arenaViewModel;

    private boolean isMenuOpen = false;
    private MaterialCardView cardDueloEnEspera;
    private TextView tvResumenDuelo;
    private Button btnReanudarDuelo;

    // VARIABLES PANEL PAGO
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
        setupFloatingButtons(view);

        cardDueloEnEspera = view.findViewById(R.id.cardDueloEnEspera);
        tvResumenDuelo = view.findViewById(R.id.tvResumenDuelo);
        btnReanudarDuelo = view.findViewById(R.id.btnReanudarDuelo);

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

        clienteViewModel = new ViewModelProvider(requireActivity()).get(ClienteViewModel.class);
        pedidoViewModel = new ViewModelProvider(requireActivity()).get(PedidoViewModel.class);
        arenaViewModel = new ViewModelProvider(requireActivity()).get(ArenaViewModel.class);

        recyclerView.setLayoutManager(new GridLayoutManager(getContext(), 3));
        adapter = new ClienteAdapter();
        recyclerView.setAdapter(adapter);

        btnMinimizarPago.setOnClickListener(v -> toggleResumenVisual((ViewGroup) view));

        // CERRAR PANEL DE PAGO
        view.findViewById(R.id.btnCerrarResumen).setOnClickListener(v -> {
            cardResumenPago.animate().translationY(cardResumenPago.getHeight()).alpha(0f).setDuration(300)
                    .withEndAction(() -> cardResumenPago.setVisibility(View.GONE)).start();
            if (btnMainAction != null) {
                btnMainAction.setVisibility(View.VISIBLE);
            }
            mostrarGrillaClientesAnimada(); // 🔥 Vuelve a mostrar los clientes
        });

        containerSeleccionVersus = view.findViewById(R.id.containerSeleccionVersus);
        tvInstruccionSeleccion = view.findViewById(R.id.tvInstruccionSeleccion);
        btnCancelarVersus = view.findViewById(R.id.btnCancelarVersus);
        btnIniciarVersus = view.findViewById(R.id.btnIniciarVersus);

        // Lógica de los nuevos botones del encabezado
        btnCancelarVersus.setOnClickListener(v -> {
            if (esAdicionInd) {
                esAdicionInd = false;
                getParentFragmentManager().popBackStack(); // Si estaba reclutando, que regrese a la arena
            } else {
                cancelarSeleccionDuelo();
            }
        });

        btnIniciarVersus.setOnClickListener(v -> irAlFragmentArena());

        arenaViewModel.getEnModoDuelo().observe(getViewLifecycleOwner(), activo -> {
            View bgDuelo = view.findViewById(R.id.bgDueloIncrustado);
            View marcadorCentral = view.findViewById(R.id.containerInfoDueloIncrustado);
            TextView tvMesa = view.findViewById(R.id.tvTituloMesa);
            TextView tvTipo = view.findViewById(R.id.tvTituloMesaActual);

            if (activo || esAdicionInd) {
                bgDuelo.setVisibility(View.VISIBLE);
                marcadorCentral.setVisibility(View.VISIBLE);
                tvMesa.setTextColor(Color.WHITE);
                tvTipo.setTextColor(Color.parseColor("#B3FFFFFF"));
                indicadorEstado.setBackgroundColor(Color.parseColor("#FFD600"));

                if (esAdicionInd) {
                    tvResumenIncrustado.setText("VS");
                    btnReanudarIncrustado.setText("RECLUTANDO...");
                } else {
                    tvResumenIncrustado.setText(arenaViewModel.obtenerMarcadorActualString().replaceAll("[^0-9\\- ]", ""));
                    btnReanudarIncrustado.setText("REANUDAR DUELO");
                }
            } else {
                bgDuelo.setVisibility(View.GONE);
                marcadorCentral.setVisibility(View.GONE);
                tvMesa.setTextColor(Color.WHITE);
                tvTipo.setTextColor(Color.parseColor("#BDBDBD"));
                indicadorEstado.setBackgroundColor(Color.parseColor("#1B5E20"));
            }
        });

        btnReanudarIncrustado.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            esAdicionInd = false;
            reanudarDueloPausado();
        });

        arenaViewModel.getScoreAzul().observe(getViewLifecycleOwner(), pts -> { if (!esAdicionInd) actualizarTextoBanner(); });
        arenaViewModel.getScoreRojo().observe(getViewLifecycleOwner(), pts -> { if (!esAdicionInd) actualizarTextoBanner(); });

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
                        Toast.makeText(getContext(), "El cliente ya está en el duelo", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    arenaViewModel.agregarJugadorADueloIndActivo(idMesaActual, cliente);
                    esAdicionInd = false;
                    modoSeleccionVersus = false;
                    if (adapter != null) adapter.setModoSeleccionVersus(false);

                    Toast.makeText(getContext(), cliente.alias.toUpperCase() + " sumado", Toast.LENGTH_SHORT).show();
                    getParentFragmentManager().popBackStack();

                } else if (modoSeleccionVersus) {
                    if ("POOL".equals(tipoJuegoMesa)) adapter.excluirCliente(cliente.idCliente);
                    else gestionarSeleccionDuelo(cliente);
                }
            }

            @Override
            public void onShortClickVersus(Cliente cliente) {
                if (modoSeleccionVersus && "POOL".equals(tipoJuegoMesa)) {
                    adapter.rotarColorCliente(cliente.idCliente);

                    // 🔥 Reemplazamos mostrarBarraGuia por nuestro nuevo panel
                    if (tvInstruccionSeleccion != null) {
                        tvInstruccionSeleccion.setText("Color actualizado...");
                        // Le damos un pequeño destello verde neón para confirmar la acción
                        tvInstruccionSeleccion.setTextColor(Color.parseColor("#00E676"));
                    }
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

                // Bloquea visualmente a los clientes que ya están jugando un duelo
                if (Boolean.TRUE.equals(arenaViewModel.getEnModoDuelo().getValue()) || esAdicionInd) {
                    adapter.actualizarBloqueoDuelo(arenaViewModel.obtenerIdsParticipantesArena());
                }

                // 🔥 Si estamos reclutando a un nuevo jugador (Adición Individual)
                if (esAdicionInd) {
                    // Simplemente llamamos a nuestro nuevo método que activa el panel Ghost
                    iniciarSeleccionDuelo();
                }

                adapter.setClientes(clientes);
            }
        });
    }

    // 🔥 MÉTODOS MÁGICOS PARA OCULTAR/MOSTRAR CON ESTILO PRO
    private void ocultarGrillaClientesAnimada() {
        if (recyclerView.getVisibility() == View.VISIBLE) {
            recyclerView.animate()
                    .alpha(0f)
                    .scaleX(0.92f) // Efecto de zoom hacia atrás
                    .scaleY(0.92f)
                    .setDuration(250)
                    .withEndAction(() -> recyclerView.setVisibility(View.INVISIBLE))
                    .start();
        }
    }

    private void mostrarGrillaClientesAnimada() {
        if (recyclerView.getVisibility() == View.INVISIBLE || recyclerView.getVisibility() == View.GONE) {
            recyclerView.setVisibility(View.VISIBLE);
            recyclerView.animate()
                    .alpha(1f)
                    .scaleX(1f) // Vuelve a su tamaño original
                    .scaleY(1f)
                    .setDuration(250)
                    .start();
        }
    }

    private void activarInterfazCobroVisual(Cliente cliente, boolean permitirPago) {
        if (isMenuOpen) toggleMenu();
        if (btnMainAction != null) btnMainAction.setVisibility(View.GONE);

        tvNombreClientePago.setText(cliente.alias.toUpperCase());
        btnEjecutarPagoVisual.setVisibility(permitirPago ? View.VISIBLE : View.GONE);
        rvItemsCuenta.setLayoutManager(new LinearLayoutManager(getContext()));
        DetalleTicketAdapter tAdapter = new DetalleTicketAdapter();
        rvItemsCuenta.setAdapter(tAdapter);

        pedidoViewModel.obtenerDetalleCliente(cliente.idCliente).observe(getViewLifecycleOwner(), dt -> {
            if (dt != null && !dt.isEmpty()) {
                // 🔥 Ocultamos los clientes del fondo para que no se traslapen con el cristal
                ocultarGrillaClientesAnimada();

                BigDecimal total = BigDecimal.ZERO;
                for (DetalleConNombre d : dt) total = total.add(d.getSubtotal());
                tAdapter.setLista(dt);
                tvMontoTotalResumen.setText(NumberFormat.getCurrencyInstance(new Locale("es", "CO")).format(total));

                cardResumenPago.setVisibility(View.VISIBLE);
                cardResumenPago.setAlpha(0f);
                cardResumenPago.setTranslationY(600);
                cardResumenPago.animate().translationY(0).alpha(1f).setDuration(400).start();

                final BigDecimal montoParaEnviar = total;
                btnEjecutarPagoVisual.setOnClickListener(v -> {
                    cardResumenPago.setVisibility(View.GONE);
                    if (btnMainAction != null) btnMainAction.setVisibility(View.VISIBLE);
                    FragmentCamaraSeguridad fCam = FragmentCamaraSeguridad.newInstance(cliente.idCliente, cliente.alias, montoParaEnviar);
                    abrirFragmento(fCam);
                });
            } else {
                cardResumenPago.setVisibility(View.GONE);
                if (btnMainAction != null) btnMainAction.setVisibility(View.VISIBLE);
                mostrarGrillaClientesAnimada();
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
        btnMainAction = view.findViewById(R.id.btnMainAction);
        btnRegistrar  = view.findViewById(R.id.btnRegistrar);
        btnPagarTodo  = view.findViewById(R.id.btnPagarTodo);
        btnHistorial  = view.findViewById(R.id.btnHistorial);
        btnModoDuelo  = view.findViewById(R.id.btnModoDuelo);

        btnMainAction.setOnClickListener(v -> toggleMenu());

        btnRegistrar.setOnClickListener(v -> {
            toggleMenu();
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
        if (isMenuOpen) toggleMenu();
        if (btnMainAction != null) btnMainAction.setVisibility(View.GONE);

        // Ocultamos el fondo temporalmente al abrir el diálogo
        ocultarGrillaClientesAnimada();

        View dv = getLayoutInflater().inflate(R.layout.dialog_registrar_cliente, null);
        TextInputEditText etA = dv.findViewById(R.id.etAlias);
        MaterialAutoCompleteTextView act = dv.findViewById(R.id.actTipoCliente);

        act.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, new String[]{"INDIVIDUAL", "GRUPO"}));

        AlertDialog d = new MaterialAlertDialogBuilder(requireContext())
                .setView(dv)
                .setOnDismissListener(dialog -> {
                    if (btnMainAction != null) btnMainAction.setVisibility(View.VISIBLE);
                    // Recuperamos el fondo al cerrar el diálogo
                    mostrarGrillaClientesAnimada();
                })
                .create();

        dv.findViewById(R.id.btnGuardar).setOnClickListener(v -> {
            String a = etA.getText().toString().trim();
            if (!a.isEmpty()) {
                // 1. Guarda el cliente localmente
                clienteViewModel.guardarCliente(a, act.getText().toString(), idMesaActual);

                // 🔥 2. AVISAMOS A LA CAJA NEGRA QUE LLEGÓ ALGUIEN A LA MESA (Para la vista Universal)
                new Thread(() -> {
                    try {
                        java.util.Map<String, Object> payload = new java.util.HashMap<>();
                        payload.put("idMesa", idMesaActual);
                        payload.put("nombreCliente", a); // El alias que acaban de escribir

                        com.nodo.tpv.data.entities.ActividadOperativaLocal evento = new com.nodo.tpv.data.entities.ActividadOperativaLocal();
                        evento.eventoId = java.util.UUID.randomUUID().toString();
                        evento.tipoEvento = "CLIENTE_NUEVO"; // El panel React ahora escucha este evento
                        evento.fechaDispositivo = System.currentTimeMillis();
                        evento.estadoSync = "PENDIENTE";
                        evento.detallesJson = new com.google.gson.Gson().toJson(payload);

                        com.nodo.tpv.data.database.AppDatabase.getInstance(requireContext())
                                .actividadOperativaLocalDao().insertar(evento);

                        androidx.work.Constraints constraints = new androidx.work.Constraints.Builder()
                                .setRequiredNetworkType(androidx.work.NetworkType.CONNECTED).build();
                        androidx.work.OneTimeWorkRequest syncRequest = new androidx.work.OneTimeWorkRequest.Builder(com.nodo.tpv.data.sync.OperatividadSyncWorker.class)
                                .setConstraints(constraints).build();
                        androidx.work.WorkManager.getInstance(requireContext())
                                .enqueueUniqueWork("SyncCliente", androidx.work.ExistingWorkPolicy.KEEP, syncRequest);
                    } catch (Exception e) {}
                }).start();

                d.dismiss();
            } else {
                etA.setError("El alias es obligatorio");
            }
        });

        d.show();
    }

    private void toggleMenu() {
        isMenuOpen = !isMenuOpen;

        if (isMenuOpen) {
            btnMainAction.animate().rotation(45f).setDuration(200).start();
            btnMainAction.setColorFilter(Color.parseColor("#FF1744")); // Rojo al abrir para indicar "Cancelar"

            mostrarBotonAnimado(btnRegistrar, 50);
            mostrarBotonAnimado(btnModoDuelo, 100);
            mostrarBotonAnimado(btnPagarTodo, 150);
            mostrarBotonAnimado(btnHistorial, 200);
        } else {
            btnMainAction.animate().rotation(0f).setDuration(200).start();
            // 🔥 Vuelve al Azul Pro Premium cuando está cerrado
            btnMainAction.setColorFilter(Color.parseColor("#2962FF"));

            ocultarBotonAnimado(btnHistorial, 0);
            ocultarBotonAnimado(btnPagarTodo, 50);
            ocultarBotonAnimado(btnModoDuelo, 100);
            ocultarBotonAnimado(btnRegistrar, 150);
        }
    }

    private void mostrarBotonAnimado(View btn, int delay) {
        btn.setVisibility(View.VISIBLE);
        btn.setAlpha(0f);
        btn.setTranslationY(20f);
        btn.animate().alpha(1f).translationY(0f).setStartDelay(delay).setDuration(200).start();
    }

    private void ocultarBotonAnimado(View btn, int delay) {
        btn.animate().alpha(0f).translationY(20f).setStartDelay(delay).setDuration(200)
                .withEndAction(() -> btn.setVisibility(View.GONE)).start();
    }

    private void irAlFragmentArena() {
        Fragment fragmentArena;
        if ("3BANDAS".equals(tipoJuegoMesa)) {
            Boolean dueloActivo = arenaViewModel.getEnModoDuelo().getValue();
            List<Cliente> participantesParaArena = Boolean.TRUE.equals(dueloActivo) ? arenaViewModel.getIntegrantesAzulCacheados() : equipoAzul;
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

    private void iniciarSeleccionDuelo() {
        modoSeleccionVersus = true;
        if (adapter != null) adapter.setModoSeleccionVersus(true);

        // Ocultar botón de menú principal
        if (btnMainAction != null) btnMainAction.setVisibility(View.GONE);

        // Mostrar la capa superior con efecto "Fade In"
        containerSeleccionVersus.setVisibility(View.VISIBLE);
        containerSeleccionVersus.setAlpha(0f);
        containerSeleccionVersus.animate().alpha(1f).setDuration(250).start();

        // Ocultamos el botón "INICIAR" si solo estamos reclutando a 1 persona desde la Arena
        btnIniciarVersus.setVisibility(esAdicionInd ? View.GONE : View.VISIBLE);

        actualizarTextoSeleccion();
    }

    private void actualizarTextoSeleccion() {
        if (esAdicionInd) {
            tvInstruccionSeleccion.setText("Reclutando jugador...");
            tvInstruccionSeleccion.setTextColor(Color.parseColor("#FFD54F")); // Amarillo
        } else if ("POOL".equals(tipoJuegoMesa)) {
            tvInstruccionSeleccion.setText("Toca para asignar color...");
            tvInstruccionSeleccion.setTextColor(Color.parseColor("#00E5FF")); // Cyan
        } else {
            // Para 3 Bandas muestra cuántos van
            int asignados = equipoAzul.size();
            tvInstruccionSeleccion.setText("Seleccionados: " + asignados);
            tvInstruccionSeleccion.setTextColor(Color.parseColor("#00E5FF"));
        }
    }

    private void gestionarSeleccionDuelo(Cliente cliente) {
        if ("3BANDAS".equals(tipoJuegoMesa)) {
            if (equipoAzul.contains(cliente)) equipoAzul.remove(cliente);
            else { if (equipoAzul.size() < COLORES_BILLAR.length) equipoAzul.add(cliente); }
            List<Integer> ids = new ArrayList<>(); List<Integer> colores = new ArrayList<>();
            for (int i = 0; i < equipoAzul.size(); i++) { ids.add(equipoAzul.get(i).idCliente); colores.add(COLORES_BILLAR[i]); }
            if (adapter != null) adapter.setModoMulticolor(ids, colores);

            actualizarTextoSeleccion(); // Actualiza el número de seleccionados
        }
    }

    private void cancelarSeleccionDuelo() {
        modoSeleccionVersus = false;
        if (adapter != null) adapter.limpiarSelecciones();

        // Restaurar botón principal
        if (btnMainAction != null) btnMainAction.setVisibility(View.VISIBLE);

        // Ocultar el panel de selección con "Fade Out"
        containerSeleccionVersus.animate().alpha(0f).setDuration(250)
                .withEndAction(() -> containerSeleccionVersus.setVisibility(View.GONE)).start();
    }






    private void actualizarTextoBanner() { tvResumenDuelo.setText("Mesa en Duelo: " + arenaViewModel.obtenerMarcadorActualString()); }
    private void abrirHistorial() { requireActivity().getSupportFragmentManager().beginTransaction().replace(R.id.container_fragments, new HistorialVentasFragment()).addToBackStack(null).commit(); }
    private void abrirCatalogo(int idCliente) { requireActivity().getSupportFragmentManager().beginTransaction().replace(R.id.container_fragments, CatalogoProductosFragment.newInstance(idCliente, idMesaActual)).addToBackStack(null).commit(); }
    private void confirmarPagoMasivo() { new MaterialAlertDialogBuilder(requireContext()).setTitle("Cierre Masivo").setMessage("¿Finalizar todas las cuentas?").setPositiveButton("SÍ", (d, w) -> {}).show(); }
}