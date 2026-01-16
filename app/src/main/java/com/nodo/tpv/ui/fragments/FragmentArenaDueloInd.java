package com.nodo.tpv.ui.fragments;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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

import com.airbnb.lottie.LottieAnimationView;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.nodo.tpv.R;
import com.nodo.tpv.data.entities.Cliente;
import com.nodo.tpv.data.entities.Producto;
import com.nodo.tpv.ui.main.MainActivity;
import com.nodo.tpv.viewmodel.ProductoViewModel;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class FragmentArenaDueloInd extends Fragment {

    private ProductoViewModel productoViewModel;
    private List<Cliente> participantes = new ArrayList<>();

    // Colores Neón sincronizados
    private final int[] COLORES_NEON = {
            Color.parseColor("#00E5FF"), // Cyan
            Color.parseColor("#FF1744"), // Rojo
            Color.parseColor("#FFD54F"), // Amarillo
            Color.parseColor("#4CAF50")  // Verde
    };

    private LottieAnimationView lottieCelebration;
    private TextView tvValorEnJuego, tvItemsBolsa;
    private MaterialCardView cardBolsaExpandible;
    private ImageView ivFlechaBolsa;
    private final String PIN_MAESTRO = "1234";

    // Gestión de Cronómetro
    private Handler timerHandler = new Handler(Looper.getMainLooper());
    private long startTime = 0L;
    private boolean isTimerRunning = false;

    public static FragmentArenaDueloInd newInstance(List<Cliente> seleccionados) {
        FragmentArenaDueloInd f = new FragmentArenaDueloInd();
        f.participantes = new ArrayList<>(seleccionados);
        return f;
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

        // 🔥 RECUPERACIÓN CACHÉ: Si participantes está vacío (al reanudar), traer del ViewModel
        if (participantes == null || participantes.isEmpty()) {
            participantes = productoViewModel.getIntegrantesAzulCacheados();
        }

        lottieCelebration = view.findViewById(R.id.lottieCelebration);
        tvValorEnJuego = view.findViewById(R.id.tvProductoEnJuego);
        tvItemsBolsa = view.findViewById(R.id.tvItemsBolsa);
        cardBolsaExpandible = view.findViewById(R.id.cardBolsaExpandible);
        ivFlechaBolsa = view.findViewById(R.id.ivFlechaBolsa);

        // Expandir pantalla completa en MainActivity
        if (requireActivity() instanceof MainActivity) {
            ((MainActivity) requireActivity()).setExpandirContenedor(true);
        }

        // Lógica de colapsar/expandir bolsa
        if (cardBolsaExpandible != null) {
            cardBolsaExpandible.setOnClickListener(v -> {
                boolean estaOculto = tvItemsBolsa.getVisibility() == View.GONE;
                tvItemsBolsa.setVisibility(estaOculto ? View.VISIBLE : View.GONE);
                ivFlechaBolsa.setRotation(estaOculto ? 180 : 0);
            });
        }

        // 🔥 Configurar Marcadores Dinámicos
        setupMiniMarcadores(view);
        setupObservadores();
        startGlobalTimer();

        // Botones de acción
        view.findViewById(R.id.fabSeleccionarMunicion).setOnClickListener(v -> abrirCatalogo());
        view.findViewById(R.id.btnFinalizarDuelo).setOnClickListener(v -> mostrarDialogoFinal());
        view.findViewById(R.id.btnReglasPago).setOnClickListener(v -> mostrarDialogoReglas());
        view.findViewById(R.id.btnVerLog).setOnClickListener(v ->
                Toast.makeText(getContext(), "Historial de impactos disponible en el log", Toast.LENGTH_SHORT).show());

        // Iniciar persistencia en DB
        productoViewModel.iniciarDueloIndPersistente(participantes, 4);
    }

    private void setupMiniMarcadores(View root) {
        LinearLayout container = root.findViewById(R.id.containerJugadores);
        if (container == null) return;

        container.removeAllViews(); // Limpiar para evitar duplicados

        for (int i = 0; i < participantes.size(); i++) {
            Cliente cliente = participantes.get(i);
            int color = COLORES_NEON[i % COLORES_NEON.length];

            // 🔥 INFLACIÓN DINÁMICA: Crear la tarjeta del jugador
            View cardView = getLayoutInflater().inflate(R.layout.item_marcador_individual, container, false);

            // Configurar pesos para que se expandan equitativamente (Ej: 2 jugadores = 50% cada uno)
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.0f);
            params.setMargins(10, 10, 10, 10);
            cardView.setLayoutParams(params);

            // Personalización Neón
            MaterialCardView mCard = (MaterialCardView) cardView;
            mCard.setStrokeColor(ColorStateList.valueOf(color));
            mCard.setStrokeWidth(6);

            TextView tvNombre = cardView.findViewById(R.id.tvNombreInd);
            tvNombre.setText(cliente.alias.toUpperCase());
            tvNombre.setTextColor(color);

            // Guardamos el ID del cliente en el Tag del Score para identificarlo en el Observer
            TextView tvScore = cardView.findViewById(R.id.tvScoreInd);
            tvScore.setTag(cliente.idCliente);
            tvScore.setText("0");

            ImageButton btnAdd = cardView.findViewById(R.id.btnSumarPunto);
            ImageButton btnSub = cardView.findViewById(R.id.btnRestarPunto);

            btnAdd.setImageTintList(ColorStateList.valueOf(color));
            btnSub.setImageTintList(ColorStateList.valueOf(color));

            final int index = i;
            btnAdd.setOnClickListener(v -> {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                confirmarAccionPunto(index, true);
            });

            btnSub.setOnClickListener(v -> confirmarAccionPunto(index, false));

            container.addView(cardView);
        }
    }

    private void setupObservadores() {
        productoViewModel.getListaApuesta().observe(getViewLifecycleOwner(), this::actualizarUIBolsa);

        productoViewModel.getScoresIndividualesInd().observe(getViewLifecycleOwner(), scoresMap -> {
            if (scoresMap == null) return;
            LinearLayout container = getView().findViewById(R.id.containerJugadores);
            if (container == null) return;

            for (int i = 0; i < container.getChildCount(); i++) {
                View card = container.getChildAt(i);
                TextView tvScore = card.findViewById(R.id.tvScoreInd);
                int clienteId = (int) tvScore.getTag();

                if (scoresMap.containsKey(clienteId)) {
                    int puntos = scoresMap.get(clienteId);
                    if (!tvScore.getText().toString().equals(String.valueOf(puntos))) {
                        tvScore.setText(String.valueOf(puntos));
                        aplicarEfectoImpacto(card, COLORES_NEON[i % COLORES_NEON.length]);
                    }
                }
            }
        });
    }

    private void confirmarAccionPunto(int indexJugador, boolean esSuma) {
        if (!esSuma) {
            Toast.makeText(getContext(), "Corrección manual: El punto se restará del log", Toast.LENGTH_SHORT).show();
            return;
        }

        if (productoViewModel.getListaApuesta().getValue() == null || productoViewModel.getListaApuesta().getValue().isEmpty()) {
            Toast.makeText(getContext(), "Cargue productos en la bolsa primero 👜", Toast.LENGTH_SHORT).show();
            return;
        }

        View vPin = getLayoutInflater().inflate(R.layout.dialog_pin_seguridad, null);
        EditText etPin = vPin.findViewById(R.id.etPin);

        new MaterialAlertDialogBuilder(requireContext(), R.style.MaterialAlertDialog_Garena)
                .setTitle("CONFIRMAR CARAMBOLA")
                .setMessage("¿Registrar para " + participantes.get(indexJugador).alias + "?")
                .setView(vPin)
                .setPositiveButton("REGISTRAR", (d, w) -> {
                    if (PIN_MAESTRO.equals(etPin.getText().toString())) {
                        productoViewModel.aplicarDanioInd(participantes.get(indexJugador).idCliente, participantes);
                        dispararCelebracion();
                    } else {
                        Toast.makeText(getContext(), "PIN Incorrecto", Toast.LENGTH_SHORT).show();
                    }
                }).show();
    }

    private void aplicarEfectoImpacto(View card, int colorNeon) {
        card.animate().scaleX(1.05f).scaleY(1.05f).setDuration(100).withEndAction(() -> {
            card.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start();
        }).start();

        if (card instanceof MaterialCardView) {
            MaterialCardView mCard = (MaterialCardView) card;
            mCard.setStrokeWidth(12);
            new Handler(Looper.getMainLooper()).postDelayed(() -> mCard.setStrokeWidth(6), 300);
        }
    }

    private void actualizarUIBolsa(List<Producto> productos) {
        if (productos == null || productos.isEmpty()) {
            tvItemsBolsa.setText("Sin productos cargados");
            tvValorEnJuego.setText("BOLSA: $0");
        } else {
            StringBuilder sb = new StringBuilder();
            BigDecimal total = BigDecimal.ZERO;
            for (Producto p : productos) {
                sb.append("• ").append(p.getNombreProducto().toUpperCase()).append("\n");
                total = total.add(p.getPrecioProducto());
            }
            tvItemsBolsa.setText(sb.toString().trim());
            tvValorEnJuego.setText("BOLSA: " + NumberFormat.getCurrencyInstance(new Locale("es", "CO")).format(total));
        }
    }

    private void startGlobalTimer() {
        if (startTime == 0L) startTime = System.currentTimeMillis();
        isTimerRunning = true;
        timerHandler.post(timerRunnable);
    }

    private Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isTimerRunning) return;
            long millis = System.currentTimeMillis() - startTime;
            int seconds = (int) (millis / 1000);
            int minutes = seconds / 60;
            int hours = minutes / 60;
            seconds %= 60; minutes %= 60;

            String timeText = String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, seconds);

            LinearLayout container = getView() != null ? getView().findViewById(R.id.containerJugadores) : null;
            if (container != null) {
                for (int i = 0; i < container.getChildCount(); i++) {
                    TextView tvTimer = container.getChildAt(i).findViewById(R.id.tvTiempoInd);
                    if (tvTimer != null) tvTimer.setText(timeText);
                }
            }
            timerHandler.postDelayed(this, 1000);
        }
    };

    private void mostrarDialogoReglas() {
        String[] opciones = {"PAGAN PERDEDORES (Clásico)", "REPARTO EQUITATIVO", "PAGA EL ÚLTIMO"};
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
                @Override public void onAnimationEnd(Animator animation) {
                    lottieCelebration.setVisibility(View.GONE);
                }
            });
        }
    }

    private void abrirCatalogo() {
        requireActivity().getSupportFragmentManager().beginTransaction()
                .setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left, android.R.anim.slide_in_left, android.R.anim.slide_out_right)
                .replace(R.id.container_fragments, CatalogoProductosFragment.newInstance(0))
                .addToBackStack(null).commit();
    }

    private void mostrarDialogoFinal() {
        new MaterialAlertDialogBuilder(requireContext(), R.style.MaterialAlertDialog_Garena)
                .setTitle("FINALIZAR DUELO")
                .setMessage("¿Desea cerrar la mesa y liquidar cuentas?")
                .setPositiveButton("SÍ, FINALIZAR", (d, w) -> {
                    isTimerRunning = false;
                    productoViewModel.finalizarDueloCompleto();
                    getParentFragmentManager().popBackStack();
                }).setNegativeButton("CANCELAR", null).show();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (requireActivity() instanceof MainActivity) {
            ((MainActivity) requireActivity()).setExpandirContenedor(true);
        }
    }
}