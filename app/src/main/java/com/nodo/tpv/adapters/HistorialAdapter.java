package com.nodo.tpv.adapters;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.nodo.tpv.R;
import com.nodo.tpv.data.entities.VentaHistorial;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistorialAdapter extends RecyclerView.Adapter<HistorialAdapter.ViewHolder> {

    private List<VentaHistorial> listaVentas = new ArrayList<>();
    private OnVentaClickListener listener; // Declaración del listener

    // Interface para capturar el clic desde el Fragment
    public interface OnVentaClickListener {
        void onVentaClick(VentaHistorial venta);
    }

    public void setOnVentaClickListener(OnVentaClickListener listener) {
        this.listener = listener;
    }

    public void setLista(List<VentaHistorial> lista) {
        this.listaVentas = lista;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_historial_venta, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        VentaHistorial venta = listaVentas.get(position);

        // 1. Nombre del Cliente y Monto
        holder.tvCliente.setText(venta.nombreCliente);
        NumberFormat nf = NumberFormat.getCurrencyInstance(Locale.getDefault());
        holder.tvMonto.setText(nf.format(venta.montoTotal));

        // 2. Formatear Fecha/Hora
        SimpleDateFormat sdf = new SimpleDateFormat("hh:mm a", Locale.getDefault());
        holder.tvHora.setText(sdf.format(new Date(venta.fechaLong)));

        // 3. Estilo dinámico según Método de Pago
        if (venta.metodoPago.equals("EFECTIVO")) {
            holder.ivMetodo.setImageResource(R.drawable.ic_payments);
            holder.ivMetodo.setColorFilter(Color.parseColor("#4CAF50"));
        } else {
            holder.ivMetodo.setImageResource(R.drawable.ic_qr_code);
            holder.ivMetodo.setColorFilter(Color.parseColor("#2196F3"));
        }

        // 4. Estilo dinámico según Estado
        if (venta.estado.equals("PENDIENTE")) {
            holder.tvEstado.setText("PENDIENTE");
            holder.tvEstado.setTextColor(Color.parseColor("#F57F17"));
            holder.tvEstado.setBackgroundResource(R.drawable.bg_badge_pending);
        } else {
            holder.tvEstado.setText("CONFIRMADO");
            holder.tvEstado.setTextColor(Color.parseColor("#2E7D32"));
            holder.tvEstado.setBackgroundResource(R.drawable.bg_badge_success);
        }

        // 5. EVENTO DE CLIC PARA EL TICKET DETALLADO
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onVentaClick(venta);
            }
        });
    }

    @Override
    public int getItemCount() {
        return listaVentas.size();
    }

    class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvCliente, tvHora, tvMonto, tvEstado;
        ImageView ivMetodo;

        ViewHolder(View v) {
            super(v);
            tvCliente = v.findViewById(R.id.tvNombreClienteHistorial);
            tvHora = v.findViewById(R.id.tvHoraHistorial);
            tvMonto = v.findViewById(R.id.tvMontoHistorial);
            tvEstado = v.findViewById(R.id.tvEstadoBadge);
            ivMetodo = v.findViewById(R.id.ivMetodoPago);
        }
    }
}