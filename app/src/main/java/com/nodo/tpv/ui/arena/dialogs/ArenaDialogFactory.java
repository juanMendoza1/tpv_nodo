package com.nodo.tpv.ui.arena.dialogs;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.nodo.tpv.R;

import java.util.List;

public class ArenaDialogFactory {

    private final Context context;
    private final LayoutInflater inflater;
    private final String pinMaestro;

    public ArenaDialogFactory(Context context, LayoutInflater inflater, String pinMaestro) {
        this.context = context;
        this.inflater = inflater;
        this.pinMaestro = pinMaestro;
    }

    // --- INTERFACES PARA COMUNICARSE CON EL FRAGMENTO ---
    public interface OnPinValidadoListener {
        void onSuccess(int colorEquipo);
    }

    public interface OnMalasConfirmadasListener {
        void onConfirmar(int faltasCometidas);
    }

    public interface OnBolaSeleccionadaListener {
        void onBolaSeleccionada(int numeroBola);
    }

    // --- MÉTODOS CREADORES DE DIÁLOGOS ---

    public void mostrarDialogoPin(int colorEquipo, OnPinValidadoListener listener) {
        View vPin = inflater.inflate(R.layout.dialog_pin_seguridad, null);
        EditText etPin = vPin.findViewById(R.id.etPin);

        new MaterialAlertDialogBuilder(context, R.style.MaterialAlertDialog_Garena)
                .setTitle("CONFIRMAR PUNTO")
                .setView(vPin)
                .setPositiveButton("OK", (d, w) -> {
                    if (pinMaestro.equals(etPin.getText().toString())) {
                        listener.onSuccess(colorEquipo);
                    } else {
                        Toast.makeText(context, "PIN Incorrecto", Toast.LENGTH_SHORT).show();
                    }
                }).show();
    }

    public void mostrarPanelSeleccionMalas(OnMalasConfirmadasListener listener) {
        Dialog dialog = new Dialog(context, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
        View view = inflater.inflate(R.layout.dialog_seleccion_malas_pro, null);
        dialog.setContentView(view);

        view.setAlpha(0f);
        view.setScaleX(0.90f); view.setScaleY(0.90f);
        view.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(250).start();

        TextView tvCantidad = view.findViewById(R.id.tvCantidadMalas);
        SeekBar slider = view.findViewById(R.id.sliderMalas);

        slider.setProgress(1);
        tvCantidad.setText("1");

        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvCantidad.setText(String.valueOf(progress));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        view.findViewById(R.id.btnCerrarDialog).setOnClickListener(v -> dialog.dismiss());

        view.findViewById(R.id.btnConfirmarMalas).setOnClickListener(v -> {
            int faltasCometidas = slider.getProgress();
            if (faltasCometidas > 0) {
                listener.onConfirmar(faltasCometidas);
            }
            dialog.dismiss();
        });

        dialog.show();
    }

    public void mostrarPanelSeleccionBolas(int colorEquipo, List<Integer> bolasBloqueadas, OnBolaSeleccionadaListener listener) {
        Dialog dialog = new Dialog(context, android.R.style.Theme_Translucent_NoTitleBar_Fullscreen);
        View view = inflater.inflate(R.layout.dialog_seleccion_bolas, null);
        dialog.setContentView(view);

        view.setAlpha(0f);
        view.setScaleX(0.95f); view.setScaleY(0.95f);
        view.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(250).start();

        LinearLayout containerTriangulo = view.findViewById(R.id.containerTriangulo);
        view.findViewById(R.id.btnCerrarDialog).setOnClickListener(v -> dialog.dismiss());
        view.findViewById(R.id.btnConfirmarBolas).setOnClickListener(v -> dialog.dismiss());

        int numeroBola = 1;
        int sizePx = (int) (40 * context.getResources().getDisplayMetrics().density);
        int marginPx = (int) (3 * context.getResources().getDisplayMetrics().density);

        for (int fila = 1; fila <= 5; fila++) {
            LinearLayout rowLayout = new LinearLayout(context);
            rowLayout.setOrientation(LinearLayout.HORIZONTAL);
            rowLayout.setGravity(android.view.Gravity.CENTER);

            for (int col = 0; col < fila; col++) {
                final int bolaActual = numeroBola;

                MaterialButton btnBola = new MaterialButton(context, null, com.google.android.material.R.attr.materialButtonOutlinedStyle);
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

                        // Pasamos la acción al Fragmento
                        listener.onBolaSeleccionada(bolaActual);
                    });
                }
                rowLayout.addView(btnBola);
                numeroBola++;
            }
            containerTriangulo.addView(rowLayout);
        }
        dialog.show();
    }

    public void mostrarResumenFinalBatalla(Runnable onConfirmar) {
        new MaterialAlertDialogBuilder(context, R.style.MaterialAlertDialog_Garena)
                .setTitle("FINALIZAR BATALLA")
                .setPositiveButton("SÍ", (d, w) -> onConfirmar.run())
                .show();
    }

    public void mostrarDialogoRevertirJugada(int numeroBola, Runnable onConfirmar) {
        new MaterialAlertDialogBuilder(context, R.style.MaterialAlertDialog_Garena)
                .setTitle("⚠️ REVERTIR JUGADA")
                .setMessage("¿Estás seguro de anular la bola número " + numeroBola + " de este equipo?")
                .setNegativeButton("CANCELAR", (dialog, which) -> dialog.dismiss())
                .setPositiveButton("SÍ, ANULAR", (dialog, which) -> onConfirmar.run())
                .show();
    }
}