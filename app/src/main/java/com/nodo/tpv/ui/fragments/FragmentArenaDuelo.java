package com.nodo.tpv.ui.fragments;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import com.nodo.tpv.data.entities.Cliente;
import com.nodo.tpv.data.entities.Producto;
import com.nodo.tpv.ui.main.MainActivity;
import com.nodo.tpv.viewmodel.ProductoViewModel;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class FragmentArenaDuelo extends Fragment {

    private ProductoViewModel productoViewModel;
    private String tipoJuegoMesa;
    private int idMesaActual;

    private TextView tvInfoMunicion;
    private LottieAnimationView lottieCelebration;
    private LinearLayout containerMarcadoresDinamicos;
    private FlexboxLayout containerGuerrerosDinamicos;

    private final String PIN_MAESTRO = "1234";
    private boolean pantallaExpandida = true;

    /**
     * Instancia base
     */
    public static FragmentArenaDuelo newInstance(List<Cliente> azul, List<Cliente> rojo, String tipoJuego) {
        FragmentArenaDuelo f = new FragmentArenaDuelo();
        f.tipoJuegoMesa = tipoJuego;
        return f;
    }

    /**
     * Instancia completa con ID de Mesa para persistencia
     */
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

        productoViewModel = new ViewModelProvider(requireActivity()).get(ProductoViewModel.class);

        // Vinculación de Vistas
        tvInfoMunicion = view.findViewById(R.id.tvProductoEnJuego);
        lottieCelebration = view.findViewById(R.id.lottieCelebration);
        containerMarcadoresDinamicos = view.findViewById(R.id.containerMarcadoresDinamicos);
        containerGuerrerosDinamicos = view.findViewById(R.id.containerGuerrerosDinamicos);

        MaterialButton btnExpandir = view.findViewById(R.id.btnExpandirArena);
        MaterialButton btnCargarMunicion = view.findViewById(R.id.fabSeleccionarMunicion);



        // --- INICIALIZACIÓN ---
        // Recuperamos el duelo y los nombres de la base de datos
        productoViewModel.recuperarDueloActivo();

        // --- OBSERVADORES ---

        // Redibujar la arena cuando cambien participantes o equipos
        productoViewModel.getMapaColoresDuelo().observe(getViewLifecycleOwner(), mapa -> {
            if (mapa != null && !mapa.isEmpty()) {
                generarInterfazDinamica(mapa);
            }
        });

        // Actualizar el valor de la bolsa (Bolsa Lateral)
        productoViewModel.getListaApuesta().observe(getViewLifecycleOwner(), this::actualizarTextoBolsa);

        // Escuchar disparadores de DB para refrescar saldos y scores
        productoViewModel.getDbTrigger().observe(getViewLifecycleOwner(), t -> {
            vincularScoresExistentes();
        });

        // Observador dedicado para scores (permite actualizar el número sin redibujar todo)
        productoViewModel.getScoresEquipos().observe(getViewLifecycleOwner(), scores -> {
            vincularScoresExistentes();
        });

        // --- PANEL DE CONTROL LATERAL ---

        btnCargarMunicion.setOnClickListener(v -> abrirCatalogo());
        view.findViewById(R.id.btnFinalizarDuelo).setOnClickListener(v -> mostrarResumenFinalBatalla());
        view.findViewById(R.id.btnHistorialDuelo).setOnClickListener(v -> mostrarLogBatalla());
        view.findViewById(R.id.btnConfigReglas).setOnClickListener(v -> mostrarDialogoReglas());

        btnExpandir.setOnClickListener(v -> {
            pantallaExpandida = !pantallaExpandida;
            if (requireActivity() instanceof MainActivity) {
                ((MainActivity) requireActivity()).setExpandirContenedor(pantallaExpandida);
                btnExpandir.setIconResource(pantallaExpandida ? R.drawable.ic_fullscreen_exit : R.drawable.ic_fullscreen);
            }
        });
    }

    private void generarInterfazDinamica(Map<Integer, Integer> mapa) {
        containerGuerrerosDinamicos.removeAllViews();
        containerMarcadoresDinamicos.removeAllViews();

        // 1. Agrupar por colores (Equipos Dinámicos)
        Map<Integer, List<Integer>> equiposPorColor = new HashMap<>();
        for (Map.Entry<Integer, Integer> entry : mapa.entrySet()) {
            int color = entry.getValue();
            if (!equiposPorColor.containsKey(color)) {
                equiposPorColor.put(color, new ArrayList<>());
            }
            equiposPorColor.get(color).add(entry.getKey());
        }

        // 2. Construir Marcadores y Pelotones
        for (Integer color : equiposPorColor.keySet()) {

            // A. Crear Marcador HUD Superior
            View vMarcador = getLayoutInflater().inflate(R.layout.item_marcador_arena_equipo, containerMarcadoresDinamicos, false);
            vMarcador.setTag(color);
            ((MaterialCardView) vMarcador).setStrokeColor(ColorStateList.valueOf(color));

            TextView tvLabel = vMarcador.findViewById(R.id.tvLabelEquipoDinamico);
            tvLabel.setText(getNombreColor(color));

            vMarcador.setOnClickListener(v -> validarYProcesarPunto(color));
            containerMarcadoresDinamicos.addView(vMarcador);

            // B. Crear Pelotón de Combate
            List<Integer> integrantes = equiposPorColor.get(color);
            View viewPeloton = getLayoutInflater().inflate(R.layout.item_peloton_arena, containerGuerrerosDinamicos, false);

            ShapeableImageView imgMando = viewPeloton.findViewById(R.id.imgAvatarMando);
            FlexboxLayout containerSeguidores = viewPeloton.findViewById(R.id.containerSeguidores);

            imgMando.setImageResource(integrantes.size() > 1 ? R.drawable.ic_group : R.drawable.ic_person);
            imgMando.setStrokeColor(ColorStateList.valueOf(color));

            // C. Inyectar Guerreros (Integrantes)
            for (Integer idCliente : integrantes) {
                View burbujaSmall = getLayoutInflater().inflate(R.layout.item_cliente_burbuja, containerSeguidores, false);
                ShapeableImageView imgSmall = burbujaSmall.findViewById(R.id.imgBurbuja);
                TextView tvNombre = burbujaSmall.findViewById(R.id.tvNombreBurbuja);
                TextView tvSaldo = burbujaSmall.findViewById(R.id.tvSaldoClienteBurbuja);

                imgSmall.setStrokeColor(ColorStateList.valueOf(color));

                // Nombre persistente desde la base de datos
                tvNombre.setText(productoViewModel.obtenerAliasCliente(idCliente));

                // Saldo individual reactivo (Refresca al insertar productos)
                productoViewModel.obtenerSaldoIndividualDuelo(idCliente).observe(getViewLifecycleOwner(), saldo -> {
                    if (saldo != null) {
                        tvSaldo.setText(NumberFormat.getCurrencyInstance(new Locale("es", "CO")).format(saldo));
                        tvSaldo.setTextColor(saldo.compareTo(BigDecimal.ZERO) > 0 ?
                                Color.parseColor("#FFD600") : Color.parseColor("#80FFFFFF"));
                    }
                });

                containerSeguidores.addView(burbujaSmall);
            }

            containerGuerrerosDinamicos.addView(viewPeloton);
        }
        vincularScoresExistentes();
    }

    private void vincularScoresExistentes() {
        Map<Integer, Integer> scoresActuales = productoViewModel.getScoresEquipos().getValue();
        if (scoresActuales == null) return;

        for (int i = 0; i < containerMarcadoresDinamicos.getChildCount(); i++) {
            View v = containerMarcadoresDinamicos.getChildAt(i);
            if (v.getTag() instanceof Integer) {
                int color = (int) v.getTag();
                if (scoresActuales.containsKey(color)) {
                    TextView tvScore = v.findViewById(R.id.tvScoreDinamico);
                    if (tvScore != null) tvScore.setText(String.valueOf(scoresActuales.get(color)));
                }
            }
        }
    }

    private String getNombreColor(int color) {
        if (color == Color.parseColor("#00E5FF")) return "AZUL";
        if (color == Color.parseColor("#FF1744")) return "ROJO";
        if (color == Color.parseColor("#FFD54F")) return "AMAR...";
        if (color == Color.parseColor("#4CAF50")) return "VERDE";
        if (color == Color.parseColor("#AA00FF")) return "MORADO";
        return "EQ";
    }

    private void validarYProcesarPunto(int colorEquipoGanador) {
        List<Producto> apuesta = productoViewModel.getListaApuesta().getValue();
        if (apuesta == null || apuesta.isEmpty()) {
            Toast.makeText(getContext(), "Bolsa vacía 👜", Toast.LENGTH_SHORT).show();
            return;
        }

        View vPin = getLayoutInflater().inflate(R.layout.dialog_pin_seguridad, null);
        EditText etPin = vPin.findViewById(R.id.etPin);

        new MaterialAlertDialogBuilder(requireContext(), R.style.MaterialAlertDialog_Garena)
                .setTitle("PUNTO PARA EQUIPO " + getNombreColor(colorEquipoGanador))
                .setView(vPin)
                .setPositiveButton("REGISTRAR", (d, w) -> {
                    if (PIN_MAESTRO.equals(etPin.getText().toString())) {
                        productoViewModel.aplicarDanioMultiequipo(colorEquipoGanador);
                        dispararCelebracion();
                    } else {
                        Toast.makeText(getContext(), "PIN Incorrecto", Toast.LENGTH_SHORT).show();
                    }
                }).show();
    }

    private void mostrarDialogoReglas() {
        String[] reglas = {"Gana bando (Reparto resto)", "Reparto todos", "Paga el último"};
        new MaterialAlertDialogBuilder(requireContext(), R.style.MaterialAlertDialog_Garena)
                .setTitle("REGLA DE COBRO")
                .setItems(reglas, (d, which) -> {
                    String seleccion = (which == 0) ? "GANADOR_SALVA" : (which == 1) ? "TODOS_PAGAN" : "ULTIMO_PAGA";
                    productoViewModel.setReglaCobroBD(idMesaActual, seleccion);
                }).show();
    }

    private void mostrarLogBatalla() {
        productoViewModel.obtenerHistorialItemsActivo().observe(getViewLifecycleOwner(), lista -> {
            if (lista != null && !lista.isEmpty()) {
                RecyclerView rv = new RecyclerView(requireContext());
                rv.setLayoutManager(new LinearLayoutManager(getContext()));
                rv.setAdapter(new LogBatallaAdapter(lista));
                new MaterialAlertDialogBuilder(requireContext(), R.style.MaterialAlertDialog_Garena)
                        .setTitle("LOG DE BATALLA")
                        .setView(rv).setPositiveButton("CERRAR", null).show();
            }
        });
    }

    private void dispararCelebracion() {
        if (lottieCelebration != null) {
            lottieCelebration.setVisibility(View.VISIBLE);
            lottieCelebration.playAnimation();
            lottieCelebration.addAnimatorListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator animation) {
                    lottieCelebration.setVisibility(View.GONE);
                }
            });
        }
    }

    private void mostrarResumenFinalBatalla() {
        new MaterialAlertDialogBuilder(requireContext(), R.style.MaterialAlertDialog_Garena)
                .setTitle("FINALIZAR BATALLA")
                .setMessage("¿Desea cerrar la arena? Los saldos se guardarán y la próxima batalla iniciará en $0.")
                .setPositiveButton("SÍ, FINALIZAR", (dialog, which) -> {
                    productoViewModel.finalizarDueloCompleto();
                    getParentFragmentManager().popBackStack();
                }).setNegativeButton("CANCELAR", null).show();
    }

    private void actualizarTextoBolsa(List<Producto> productos) {
        if (productos != null && !productos.isEmpty()) {
            BigDecimal total = BigDecimal.ZERO;
            for (Producto p : productos) total = total.add(p.getPrecioProducto());
            tvInfoMunicion.setText(NumberFormat.getCurrencyInstance(new Locale("es", "CO")).format(total));
        } else {
            tvInfoMunicion.setText("$0");
        }
    }

    private void abrirCatalogo() {
        requireActivity().getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left, android.R.anim.slide_in_left, android.R.anim.slide_out_right)
                .replace(R.id.container_fragments, CatalogoProductosFragment.newInstance(0))
                .addToBackStack(null).commit();
    }
}