package com.nodo.tpv.ui.fragments;

import android.animation.ObjectAnimator;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.view.animation.TranslateAnimation;
import android.widget.ArrayAdapter;
import android.widget.Button;
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

import com.google.android.material.bottomsheet.BottomSheetDialog;
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
import com.nodo.tpv.ui.main.MainActivity;
import com.nodo.tpv.viewmodel.ClienteViewModel;
import com.nodo.tpv.viewmodel.ProductoViewModel;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ListaClientesFragment extends Fragment {

    private int idMesaActual;
    private String tipoJuegoMesa;

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
    private ClienteViewModel clienteViewModel;
    private ProductoViewModel productoViewModel;

    private boolean isMenuOpen = false;
    private FloatingActionButton fabHistorial, fabPagarTodo, fabRegistrar, fabMain, fabModoDuelo;
    private Animation rotateOpen, rotateClose, fromBottom, toBottom;

    private MaterialCardView cardDueloEnEspera;
    private TextView tvResumenDuelo;
    private Button btnReanudarDuelo;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            idMesaActual = getArguments().getInt("id_mesa");
            tipoJuegoMesa = getArguments().getString("tipo_juego", "POOL");
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
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        recyclerView = view.findViewById(R.id.rvClientes);
        TextView tvEmpty = view.findViewById(R.id.tvEmptyMessage);
        TextView tvTituloMesa = view.findViewById(R.id.tvTituloMesa);
        TextView tvTipoJuego = view.findViewById(R.id.tvTituloMesaActual);

        if (tvTituloMesa != null) tvTituloMesa.setText("MESA #" + idMesaActual);
        if (tvTipoJuego != null) tvTipoJuego.setText(tipoJuegoMesa);

        clienteViewModel = new ViewModelProvider(requireActivity()).get(ClienteViewModel.class);
        productoViewModel = new ViewModelProvider(requireActivity()).get(ProductoViewModel.class);

        GridLayoutManager gridLayoutManager = new GridLayoutManager(getContext(), 3);
        recyclerView.setLayoutManager(gridLayoutManager);


        adapter = new ClienteAdapter();
        recyclerView.setAdapter(adapter);

        productoViewModel.getEnModoDuelo().observe(getViewLifecycleOwner(), activo -> {
            if (activo) {
                cardDueloEnEspera.setVisibility(View.VISIBLE);
                animarBannerGlow(cardDueloEnEspera);

                // 🔥 NUEVA LÓGICA DINÁMICA:
                // Obtenemos todos los IDs que están en el mapa de colores del duelo actual
                List<Integer> idsEnArena = productoViewModel.obtenerIdsParticipantesArena();

                // Si la lista está vacía (posiblemente porque es 3BANDAS),
                // podrías mantener la lógica antigua de respaldo:
                if (idsEnArena.isEmpty()) {
                    for (Cliente c : productoViewModel.getIntegrantesAzulCacheados()) idsEnArena.add(c.idCliente);
                    for (Cliente c : productoViewModel.getIntegrantesRojoCacheados()) idsEnArena.add(c.idCliente);
                }

                if (adapter != null) adapter.actualizarBloqueoDuelo(idsEnArena);
                fabModoDuelo.setBackgroundTintList(ColorStateList.valueOf(Color.GRAY));
            } else {
                cardDueloEnEspera.setVisibility(View.GONE);
                if (adapter != null) adapter.actualizarBloqueoDuelo(new ArrayList<>());
                fabModoDuelo.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#FFD600")));
            }
        });

        productoViewModel.getScoreAzul().observe(getViewLifecycleOwner(), pts -> actualizarTextoBanner());
        productoViewModel.getScoreRojo().observe(getViewLifecycleOwner(), pts -> actualizarTextoBanner());
        btnReanudarDuelo.setOnClickListener(v -> reanudarDueloPausado());

        adapter.setOnClienteClickListener(new ClienteAdapter.OnClienteClickListener() {
            @Override public void onVerClick(Cliente cliente) { mostrarDetalleConsumo(cliente); }
            @Override public void onAgregarClick(Cliente cliente) { abrirCatalogo(cliente.idCliente); }
            @Override public void onPagarClick(Cliente cliente) { abrirSeleccionPago(cliente); }

            @Override public void onLongClickVersus(Cliente cliente) {
                if (modoSeleccionVersus) {
                    if ("POOL".equals(tipoJuegoMesa)) {
                        // 🔥 EXCLUIR: Vuelve a blanco
                        adapter.excluirCliente(cliente.idCliente);
                    } else {
                        // Lógica original 3BANDAS (no tocar)
                        gestionarSeleccionDuelo(cliente);
                    }
                }
            }

            @Override public void onShortClickVersus(Cliente cliente) {
                if (modoSeleccionVersus && "POOL".equals(tipoJuegoMesa)) {
                    adapter.rotarColorCliente(cliente.idCliente);
                    // 🔥 Actualizar el mensaje o validación en tiempo real
                    mostrarBarraGuia("Equipos configurados...");
                }
            }
        });

        clienteViewModel.getClientesActivos().observe(getViewLifecycleOwner(), clientes -> {
            if (clientes == null || clientes.isEmpty()) {
                tvEmpty.setVisibility(View.VISIBLE);
                recyclerView.setVisibility(View.GONE);
            } else {
                tvEmpty.setVisibility(View.GONE);
                recyclerView.setVisibility(View.VISIBLE);

                // 🔥 PASO 1: Antes de setear los clientes, asegurarnos de que el adaptador sepa quiénes están bloqueados
                Boolean dueloActivo = productoViewModel.getEnModoDuelo().getValue();
                if (dueloActivo != null && dueloActivo) {
                    adapter.actualizarBloqueoDuelo(productoViewModel.obtenerIdsParticipantesArena());
                }

                adapter.setClientes(clientes);
            }
        });
    }

    private void setupFloatingButtons(View view) {
        fabMain = view.findViewById(R.id.fabMain);
        fabRegistrar = view.findViewById(R.id.fabRegistrar);
        fabPagarTodo = view.findViewById(R.id.fabPagarTodo);
        fabHistorial = view.findViewById(R.id.fabHistorial);
        fabModoDuelo = view.findViewById(R.id.fabModoDuelo);

        fabMain.setOnClickListener(v -> toggleMenu());
        fabRegistrar.setOnClickListener(v -> { toggleMenu(); mostrarDialogoRegistro(); });
        fabHistorial.setOnClickListener(v -> { toggleMenu(); abrirHistorial(); });
        fabPagarTodo.setOnClickListener(v -> { toggleMenu(); confirmarPagoMasivo(); });
        fabModoDuelo.setOnClickListener(v -> {
            toggleMenu();
            Boolean activo = productoViewModel.getEnModoDuelo().getValue();
            if (activo != null && activo) reanudarDueloPausado();
            else if (!modoSeleccionVersus) iniciarSeleccionDuelo();
            else cancelarSeleccionDuelo();
        });
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
        if (!isMenuOpen) {
            fabMain.startAnimation(rotateOpen);
            setFabVisibility(View.VISIBLE, fromBottom);
            isMenuOpen = true;
        } else {
            fabMain.startAnimation(rotateClose);
            setFabVisibility(View.GONE, toBottom);
            isMenuOpen = false;
        }
    }

    private void setFabVisibility(int visibility, Animation anim) {
        boolean isVisible = (visibility == View.VISIBLE);
        fabRegistrar.setVisibility(visibility); fabRegistrar.setClickable(isVisible);
        fabPagarTodo.setVisibility(visibility); fabPagarTodo.setClickable(isVisible);
        fabHistorial.setVisibility(visibility); fabHistorial.setClickable(isVisible);
        fabModoDuelo.setVisibility(visibility); fabModoDuelo.setClickable(isVisible);
        if (isVisible) {
            fabRegistrar.startAnimation(anim); fabPagarTodo.startAnimation(anim);
            fabHistorial.startAnimation(anim); fabModoDuelo.startAnimation(anim);
        }
    }

    private void iniciarSeleccionDuelo() {
        modoSeleccionVersus = true;
        equipoAzul.clear();
        equipoRojo.clear();
        equipoActual = 1;
        adapter.setModoSeleccionVersus(true); // Actualizado para usar nuevo método del adapter
        String msg = "3BANDAS".equals(tipoJuegoMesa) ? "Seleccione Jugadores (Individual)" : "Agrupe por colores para definir equipos";
        mostrarBarraGuia(msg);
    }

    private void gestionarSeleccionDuelo(Cliente cliente) {
        // Esta función solo se mantiene para la lógica de 3BANDAS
        if ("3BANDAS".equals(tipoJuegoMesa)) {
            if (equipoAzul.contains(cliente)) {
                equipoAzul.remove(cliente);
            } else {
                if (equipoAzul.size() < COLORES_BILLAR.length) equipoAzul.add(cliente);
                else Toast.makeText(getContext(), "Máximo 4 jugadores", Toast.LENGTH_SHORT).show();
            }

            if (adapter != null) {
                List<Integer> ids = new ArrayList<>();
                List<Integer> colores = new ArrayList<>();
                for (int i = 0; i < equipoAzul.size(); i++) {
                    ids.add(equipoAzul.get(i).idCliente);
                    colores.add(COLORES_BILLAR[i]);
                }
                adapter.setModoMulticolor(ids, colores);
            }
        }
    }

    private void mostrarBarraGuia(String mensaje) {
        if (snackbarGuia != null) snackbarGuia.dismiss();
        snackbarGuia = Snackbar.make(requireView(), mensaje, Snackbar.LENGTH_INDEFINITE);

        snackbarGuia.setAction("INICIAR ARENA", v -> {
            if ("3BANDAS".equals(tipoJuegoMesa)) {
                // Lógica para 3 BANDAS: Al menos 2 jugadores individuales
                if (equipoAzul.size() < 2) {
                    Toast.makeText(getContext(), "Mínimo 2 jugadores para el duelo", Toast.LENGTH_SHORT).show();
                } else {
                    irAlFragmentArena();
                }
            } else {
                // 🔥 LÓGICA POOL DINÁMICA (EQUIPOS)
                Map<Integer, Integer> mapa = adapter.getMapaColoresPool();

                // Paso 1: Validar cantidad total de participantes
                if (mapa.size() < 2) {
                    Toast.makeText(getContext(), "Seleccione al menos 2 participantes", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Paso 2: Validar que existan al menos 2 equipos (colores) diferentes
                // Usamos un Set para contar colores únicos
                java.util.Set<Integer> equiposUnicos = new java.util.HashSet<>(mapa.values());

                if (equiposUnicos.size() < 2) {
                    Toast.makeText(getContext(), "¡Obligatorio! Debe haber al menos 2 equipos diferentes", Toast.LENGTH_SHORT).show();
                    return;
                }

                // Si pasa ambas validaciones, iniciamos
                irAlFragmentArena();
            }
        });
        snackbarGuia.show();
    }

    private void irAlFragmentArena() {
        Fragment fragmentArena;
        if ("3BANDAS".equals(tipoJuegoMesa)) {
            productoViewModel.guardarIntegrantesDuelo(equipoAzul, new ArrayList<>());
            fragmentArena = FragmentArenaDueloInd.newInstance(equipoAzul);
        } else {
            // 🔥 CORRECCIÓN AQUÍ:
            Boolean dueloActivo = productoViewModel.getEnModoDuelo().getValue();

            if (dueloActivo != null && dueloActivo) {
                // SI YA ESTÁ ACTIVO: No le pedimos nada al adaptador,
                // el ViewModel ya tiene los datos persistentes.
                fragmentArena = FragmentArenaDuelo.newInstance(new ArrayList<>(), new ArrayList<>(), tipoJuegoMesa);
            } else {
                // SI ES NUEVO: Tomamos la selección del adaptador
                Map<Integer, Integer> mapaSeleccion = adapter.getMapaColoresPool();
                if (mapaSeleccion.isEmpty()) {
                    Toast.makeText(getContext(), "No hay jugadores seleccionados", Toast.LENGTH_SHORT).show();
                    return;
                }
                productoViewModel.prepararDueloPoolMultiequipo(mapaSeleccion);
                fragmentArena = FragmentArenaDuelo.newInstance(new ArrayList<>(), new ArrayList<>(), tipoJuegoMesa);
            }
        }
        abrirFragmento(fragmentArena);
        cancelarSeleccionDuelo();
    }

    private void reanudarDueloPausado() { irAlFragmentArena(); }
    private void cancelarSeleccionDuelo() { modoSeleccionVersus = false; if (snackbarGuia != null) snackbarGuia.dismiss(); adapter.limpiarSelecciones(); }

    private void abrirFragmento(Fragment f) {
        requireActivity().getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left, android.R.anim.slide_in_left, android.R.anim.slide_out_right)
                .replace(R.id.container_fragments, f)
                .addToBackStack(null).commit();
    }

    private void abrirHistorial() {
        requireActivity().getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left, android.R.anim.slide_in_left, android.R.anim.slide_out_right)
                .replace(R.id.container_fragments, new HistorialVentasFragment())
                .addToBackStack(null).commit();
    }

    private void abrirCatalogo(int id) {
        requireActivity().getSupportFragmentManager().beginTransaction()
                .replace(R.id.container_fragments, CatalogoProductosFragment.newInstance(id))
                .addToBackStack(null).commit();
    }

    private void abrirSeleccionPago(Cliente c) {
        BottomSheetDialog bd = new BottomSheetDialog(requireContext());
        View v = getLayoutInflater().inflate(R.layout.layout_modal_pago, null);
        productoViewModel.obtenerDetalleCliente(c.idCliente).observe(getViewLifecycleOwner(), dt -> {
            BigDecimal subTotal = BigDecimal.ZERO;
            if (dt != null) for (DetalleConNombre d : dt) subTotal = subTotal.add(d.getSubtotal());
            final BigDecimal totalFinal = subTotal;
            ((TextView)v.findViewById(R.id.tvMontoTotalPago)).setText(NumberFormat.getCurrencyInstance(new Locale("es", "CO")).format(totalFinal));
            v.findViewById(R.id.btnPagoEfectivo).setOnClickListener(view -> { bd.dismiss(); mostrarDetalleConfirmacion(c, "EFECTIVO", totalFinal); });
        });
        bd.setContentView(v); bd.show();
    }

    public void mostrarDetalleConfirmacion(Cliente c, String m, BigDecimal t) {
        BottomSheetDialog b = new BottomSheetDialog(requireContext());
        View v = getLayoutInflater().inflate(R.layout.fragment_confirmacion_pago, null);
        ((TextView)v.findViewById(R.id.tvMontoConfirmar)).setText(NumberFormat.getCurrencyInstance(new Locale("es", "CO")).format(t));
        v.findViewById(R.id.btnFinalizarPago).setOnClickListener(vi -> {
            productoViewModel.finalizarCuenta(c.idCliente, c.alias, m, "");
            b.dismiss(); Toast.makeText(getContext(), "Venta cerrada", Toast.LENGTH_SHORT).show();
        });
        b.setContentView(v); b.show();
    }

    private void mostrarDetalleConsumo(Cliente cliente) {
        BottomSheetDialog bs = new BottomSheetDialog(requireContext());
        View v = getLayoutInflater().inflate(R.layout.layout_detalle_consumo, null);
        ((TextView)v.findViewById(R.id.tvNombreClienteDetalle)).setText(cliente.alias);
        RecyclerView rv = v.findViewById(R.id.rvDetalleProductos);
        rv.setLayoutManager(new LinearLayoutManager(getContext()));
        DetalleTicketAdapter tAdapter = new DetalleTicketAdapter();
        rv.setAdapter(tAdapter);
        productoViewModel.obtenerDetalleCliente(cliente.idCliente).observe(getViewLifecycleOwner(), dt -> {
            BigDecimal tot = BigDecimal.ZERO; if (dt != null) for (DetalleConNombre d : dt) tot = tot.add(d.getSubtotal());
            tAdapter.setLista(dt);
            ((TextView)v.findViewById(R.id.tvTotalMonto)).setText(NumberFormat.getCurrencyInstance(new Locale("es", "CO")).format(tot));
        });
        bs.setContentView(v); bs.show();
    }

    private void confirmarPagoMasivo() {
        new MaterialAlertDialogBuilder(requireContext()).setTitle("Cierre Masivo")
                .setMessage("¿Finalizar todas las cuentas?")
                .setPositiveButton("SÍ", (d, w) -> Toast.makeText(getContext(), "Cerrando...", Toast.LENGTH_SHORT).show()).show();
    }

    private void actualizarTextoBanner() {
        String resumen = productoViewModel.obtenerMarcadorActualString();
        tvResumenDuelo.setText("Mesa en Duelo: " + resumen);
    }

    private void animarBannerGlow(View view) {
        view.setAlpha(0f);
        view.animate().alpha(1f).translationY(0f).setDuration(600).start();
    }

    public void mostrarDialogoRegistro() {
        View dv = getLayoutInflater().inflate(R.layout.dialog_registrar_cliente, null);
        TextInputEditText etA = dv.findViewById(R.id.etAlias);
        MaterialAutoCompleteTextView act = dv.findViewById(R.id.actTipoCliente);
        act.setAdapter(new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, new String[]{"INDIVIDUAL", "GRUPO"}));
        AlertDialog d = new MaterialAlertDialogBuilder(requireContext()).setView(dv).create();
        dv.findViewById(R.id.btnGuardar).setOnClickListener(v -> {
            String a = etA.getText().toString().trim();
            if (!a.isEmpty()) { clienteViewModel.guardarCliente(a, act.getText().toString()); d.dismiss(); }
        });
        d.show();
    }

}