package com.nodo.tpv.adapters;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.nodo.tpv.R;
import com.nodo.tpv.data.dto.DetalleHistorialDuelo;

import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

public class LogBatallaAdapter extends RecyclerView.Adapter<LogBatallaAdapter.ViewHolder> {

    private List<DetalleHistorialDuelo> items;
    private final NumberFormat nf = NumberFormat.getCurrencyInstance(new Locale("es", "CO"));
    private final OnItemClickListener listener;

    // Interfaz para capturar el click en el Fragment
    public interface OnItemClickListener {
        void onItemClick(DetalleHistorialDuelo item);
    }

    // Constructor actualizado con el listener
    public LogBatallaAdapter(List<DetalleHistorialDuelo> items, OnItemClickListener listener) {
        this.items = items;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_log_batalla, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DetalleHistorialDuelo item = items.get(position);

        // 1. Lógica de Estados (PENDIENTE vs ENTREGADO)
        if ("PENDIENTE".equals(item.estado)) {
            holder.tvProducto.setText("⏳ " + item.nombreProducto);
            holder.itemView.setAlpha(0.5f); // Opaco mientras está pendiente
        } else {
            holder.tvProducto.setText("✅ " + item.nombreProducto);
            holder.itemView.setAlpha(1.0f); // Sólido cuando se entrega
        }

        // 2. Valores monetarios
        holder.tvValor.setText(nf.format(item.precioEnVenta));

        // 3. Lógica de colores y nombre de equipo
        int color = item.idEquipo;
        holder.viewColorEquipo.setBackgroundTintList(ColorStateList.valueOf(color));
        holder.tvEquipo.setText(obtenerNombreSimpleColor(color));
        holder.tvEquipo.setTextColor(color);

        // 4. Corrección de Marcador
        String marcador = item.marcadorAlMomento;
        if (marcador == null || marcador.equals("0-0") || marcador.isEmpty()) {
            holder.tvMarcador.setText("Punto de apertura");
        } else {
            holder.tvMarcador.setText("Score: " + marcador);
        }

        // 5. Evento de Click para Despachar
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onItemClick(item);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items != null ? items.size() : 0;
    }

    public static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvMarcador, tvEquipo, tvProducto, tvValor;
        View viewColorEquipo;

        public ViewHolder(View v) {
            super(v);
            tvMarcador = v.findViewById(R.id.txtMarcador);
            tvEquipo = v.findViewById(R.id.txtEquipo);
            tvProducto = v.findViewById(R.id.txtProducto);
            tvValor = v.findViewById(R.id.txtValor);
            viewColorEquipo = v.findViewById(R.id.viewColorEquipo);
        }
    }

    private String obtenerNombreSimpleColor(int color) {
        if (color == Color.parseColor("#00E5FF")) return "AZUL";
        if (color == Color.parseColor("#FF1744")) return "ROJO";
        if (color == Color.parseColor("#FFD54F")) return "AMAR.";
        if (color == Color.parseColor("#4CAF50")) return "VERDE";
        if (color == Color.parseColor("#AA00FF")) return "MORADO";
        return "E.Q.";
    }

    public void updateList(List<DetalleHistorialDuelo> nuevaLista) {
        this.items = nuevaLista;
        // Notificamos al adaptador que los datos cambiaron para que refresque la vista
        notifyDataSetChanged();
    }
}