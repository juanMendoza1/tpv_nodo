package com.nodo.tpv.adapters;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.imageview.ShapeableImageView;
import com.google.android.material.shape.CornerFamily;
import com.google.android.material.shape.RelativeCornerSize;
import com.nodo.tpv.R;
import com.nodo.tpv.data.dto.ClienteConSaldo;

import java.util.ArrayList;
import java.util.List;

public class DueloBurbujaAdapter extends RecyclerView.Adapter<DueloBurbujaAdapter.BurbujaViewHolder> {

    private List<ClienteConSaldo> integrantes = new ArrayList<>();
    private final int equipoTipo; // 1: Azul, 2: Rojo
    private final OnBurbujaClickListener listener;

    public interface OnBurbujaClickListener {
        void onAgregarConsumo(ClienteConSaldo item);
        void onPagoIndividual(ClienteConSaldo item);
        void onAsignarAIntegrante(ClienteConSaldo grupo, String nombreIntegrante);
    }

    public DueloBurbujaAdapter(int equipoTipo, OnBurbujaClickListener listener) {
        this.equipoTipo = equipoTipo;
        this.listener = listener;
    }

    public void setIntegrantes(List<ClienteConSaldo> lista) {
        this.integrantes = lista;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public BurbujaViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_duelo_mini, parent, false);
        return new BurbujaViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BurbujaViewHolder holder, int position) {
        ClienteConSaldo item = integrantes.get(position);
        holder.bind(item, equipoTipo, listener);
    }

    @Override
    public int getItemCount() {
        return integrantes.size();
    }

    class BurbujaViewHolder extends RecyclerView.ViewHolder {
        ShapeableImageView ivFotoGrupal;
        GridLayout gridMiniAvatares;
        TextView tvAlias, tvMonto;
        View containerBurbuja;

        public BurbujaViewHolder(@NonNull View itemView) {
            super(itemView);
            ivFotoGrupal = itemView.findViewById(R.id.ivFotoGrupal);
            gridMiniAvatares = itemView.findViewById(R.id.gridMiniAvatares);
            tvAlias = itemView.findViewById(R.id.tvAliasBurbuja);
            tvMonto = itemView.findViewById(R.id.tvMontoBurbuja);
            containerBurbuja = itemView.findViewById(R.id.containerBurbuja);
        }

        public void bind(ClienteConSaldo item, int tipo, OnBurbujaClickListener listener) {
            tvAlias.setText(item.cliente.alias);

            // 1. Efecto Neón Garena
            int colorNeon = (tipo == 1) ? Color.parseColor("#00E5FF") : Color.parseColor("#FF1744");
            containerBurbuja.setBackgroundTintList(ColorStateList.valueOf(colorNeon));

            // 2. Lógica de Identidad Dinámica
            if (item.getFotoTemporalDuelo() != null && !item.getFotoTemporalDuelo().isEmpty()) {
                ivFotoGrupal.setVisibility(View.VISIBLE);
                gridMiniAvatares.setVisibility(View.GONE);
                ivFotoGrupal.setImageResource(R.drawable.ic_group);
            } else {
                ivFotoGrupal.setVisibility(View.GONE);
                gridMiniAvatares.setVisibility(View.VISIBLE);
                // Aquí pasamos la cantidad real de integrantes del grupo si está disponible
                generarMiniFormacion(gridMiniAvatares, 4);
            }

            itemView.setOnClickListener(v -> {
                v.animate().scaleX(0.85f).scaleY(0.85f).setDuration(80).withEndAction(() -> {
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(80).start();
                    mostrarMenuAsignacion(v.getContext(), item);
                }).start();
            });

            itemView.setOnLongClickListener(v -> {
                mostrarMenuGestion(v.getContext(), item);
                return true;
            });
        }

        private void generarMiniFormacion(GridLayout grid, int cantidad) {
            grid.removeAllViews();
            int limite = Math.min(cantidad, 4);
            for (int i = 0; i < limite; i++) {
                ShapeableImageView mini = new ShapeableImageView(grid.getContext());
                GridLayout.LayoutParams params = new GridLayout.LayoutParams();
                params.width = 65; params.height = 65;
                params.setMargins(4, 4, 4, 4);
                mini.setLayoutParams(params);

                mini.setShapeAppearanceModel(mini.getShapeAppearanceModel().toBuilder()
                        .setAllCorners(CornerFamily.ROUNDED, new RelativeCornerSize(0.5f).getRelativePercent())
                        .build());

                mini.setImageResource(R.drawable.ic_person);
                mini.setStrokeColor(ColorStateList.valueOf(Color.WHITE));
                mini.setStrokeWidth(2f);
                grid.addView(mini);
            }
        }

        private void mostrarMenuAsignacion(Context context, ClienteConSaldo item) {
            String[] opciones = {"💥 Cargar a todo el Grupo (Dividir)", "🎯 Seleccionar Miembro"};
            new MaterialAlertDialogBuilder(context)
                    .setTitle("ESTRATEGIA DE CARGO")
                    .setItems(opciones, (dialog, which) -> {
                        if (which == 0) listener.onAgregarConsumo(item);
                        else listener.onAsignarAIntegrante(item, "Guerrero");
                    }).show();
        }

        private void mostrarMenuGestion(Context context, ClienteConSaldo item) {
            String[] opciones = {"💳 Pagar individual", "❌ Retirar del duelo"};
            new MaterialAlertDialogBuilder(context)
                    .setTitle(item.cliente.alias)
                    .setItems(opciones, (dialog, which) -> {
                        if (which == 0) listener.onPagoIndividual(item);
                        else if (which == 1) {
                            int pos = getAdapterPosition();
                            if (pos != RecyclerView.NO_POSITION) {
                                integrantes.remove(pos);
                                notifyItemRemoved(pos);
                            }
                        }
                    }).show();
        }
    }
}