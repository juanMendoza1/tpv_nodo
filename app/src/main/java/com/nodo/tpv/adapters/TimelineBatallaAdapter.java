package com.nodo.tpv.adapters;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.nodo.tpv.R;
import com.nodo.tpv.data.dto.EventoBatalla;

import java.util.List;

public class TimelineBatallaAdapter extends RecyclerView.Adapter<TimelineBatallaAdapter.TimelineViewHolder> {

    private List<EventoBatalla> eventos;
    private Context context;
    // Variable global para controlar si el subgrupo actual está expandido o colapsado
    private boolean isExpanded = false;

    public TimelineBatallaAdapter(List<EventoBatalla> eventos) {
        this.eventos = eventos;
    }

    public void actualizarDatos(List<EventoBatalla> nuevosEventos) {
        this.eventos = nuevosEventos;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public TimelineViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        this.context = parent.getContext();
        View view = LayoutInflater.from(context).inflate(R.layout.item_timeline_evento, parent, false);
        return new TimelineViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TimelineViewHolder holder, int position) {
        EventoBatalla evento = eventos.get(position);

        // --- 1. LÓGICA DE VISIBILIDAD (EXPANDIR/COLAPSAR) ---
        if (evento.esHijo) {
            // Si es un detalle hijo, su visibilidad depende del estado de expansión
            if (isExpanded) {
                holder.itemView.setVisibility(View.VISIBLE);
                holder.itemView.setLayoutParams(new RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            } else {
                holder.itemView.setVisibility(View.GONE);
                holder.itemView.setLayoutParams(new RecyclerView.LayoutParams(0, 0));
            }
        } else {
            // El "Estado de Batalla" (Padre) siempre es visible
            holder.itemView.setVisibility(View.VISIBLE);
            holder.itemView.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

            // Listener para alternar la expansión al hacer clic
            holder.itemView.setOnClickListener(v -> {
                isExpanded = !isExpanded;
                notifyDataSetChanged(); // Refrescamos para ocultar/mostrar hijos
            });
        }

        // --- 2. LÓGICA DE JERARQUÍA VISUAL (SANGRÍA) ---
        ConstraintLayout.LayoutParams cardParams = (ConstraintLayout.LayoutParams) holder.cardEvento.getLayoutParams();
        ConstraintLayout.LayoutParams iconParams = (ConstraintLayout.LayoutParams) holder.contenedorIcono.getLayoutParams();

        if (evento.tipoEvento == EventoBatalla.TIPO_CIERRE_RONDA) {
            // DISEÑO PADRE
            cardParams.setMargins(dpToPx(16), dpToPx(8), dpToPx(16), dpToPx(8));
            iconParams.width = dpToPx(32);
            iconParams.height = dpToPx(32);
            iconParams.setMarginStart(dpToPx(16));

            holder.cardEvento.setStrokeWidth(dpToPx(2));
            holder.cardEvento.setAlpha(1.0f);
            holder.contenedorIcono.setAlpha(1.0f);

            // Indicador visual de expansión en la descripción
            String textoExpansion = isExpanded ? " ▲ Toca para resumir" : " ▼ Toca para ver detalles";
            holder.tvDescripcion.setText(evento.descripcion + textoExpansion);

        } else {
            // DISEÑO HIJO (SUBGRUPO)
            cardParams.setMargins(dpToPx(48), dpToPx(4), dpToPx(16), dpToPx(4));
            iconParams.width = dpToPx(22);
            iconParams.height = dpToPx(22);
            iconParams.setMarginStart(dpToPx(21)); // Centrado con la línea de tiempo

            holder.cardEvento.setStrokeWidth(dpToPx(1));
            holder.cardEvento.setAlpha(0.85f);
            holder.contenedorIcono.setAlpha(0.7f);
            holder.tvDescripcion.setText(evento.descripcion);
        }

        holder.cardEvento.setLayoutParams(cardParams);
        holder.contenedorIcono.setLayoutParams(iconParams);

        // --- 3. LLENADO DE DATOS Y COLORES ---
        holder.tvTitulo.setText(evento.titulo);
        holder.tvHora.setText(evento.horaFormateada);
        holder.tvMarcador.setText(evento.marcadorMomento);

        holder.contenedorIcono.setStrokeColor(evento.colorFoco);
        holder.iconoEvento.setImageTintList(ColorStateList.valueOf(evento.colorFoco));
        holder.tvTitulo.setTextColor(evento.colorFoco);
        holder.cardEvento.setStrokeColor(evento.colorFoco);

        // --- 4. ICONOGRAFÍA ---
        switch (evento.tipoEvento) {
            case EventoBatalla.TIPO_BOLA_ANOTADA:
                holder.iconoEvento.setImageResource(R.drawable.ic_pool_ball);
                break;
            case EventoBatalla.TIPO_FALTA:
                holder.iconoEvento.setImageResource(R.drawable.ic_remove);
                break;
            case EventoBatalla.TIPO_COMPRA_MUNICION:
                holder.iconoEvento.setImageResource(R.drawable.ic_shopping_bag);
                break;
            case EventoBatalla.TIPO_CIERRE_RONDA:
                holder.iconoEvento.setImageResource(R.drawable.ic_flag);
                break;
        }

        // --- 5. LÍNEA DE TIEMPO ---
        if (position == eventos.size() - 1 || (evento.tipoEvento == EventoBatalla.TIPO_CIERRE_RONDA && !isExpanded)) {
            holder.lineaTiempo.setVisibility(View.INVISIBLE);
        } else {
            holder.lineaTiempo.setVisibility(View.VISIBLE);
        }
    }

    @Override
    public int getItemCount() {
        return eventos != null ? eventos.size() : 0;
    }

    private int dpToPx(int dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density);
    }

    static class TimelineViewHolder extends RecyclerView.ViewHolder {
        View lineaTiempo;
        MaterialCardView contenedorIcono, cardEvento;
        ImageView iconoEvento;
        TextView tvTitulo, tvHora, tvDescripcion, tvMarcador;

        public TimelineViewHolder(@NonNull View itemView) {
            super(itemView);
            lineaTiempo = itemView.findViewById(R.id.lineaTiempo);
            contenedorIcono = itemView.findViewById(R.id.contenedorIcono);
            cardEvento = itemView.findViewById(R.id.cardEvento);
            iconoEvento = itemView.findViewById(R.id.iconoEvento);
            tvTitulo = itemView.findViewById(R.id.tvTituloEvento);
            tvHora = itemView.findViewById(R.id.tvHoraEvento);
            tvDescripcion = itemView.findViewById(R.id.tvDescripcionEvento);
            tvMarcador = itemView.findViewById(R.id.tvMarcadorMomento);
        }
    }
}