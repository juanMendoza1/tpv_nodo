package com.nodo.tpv.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.nodo.tpv.R;
import com.nodo.tpv.data.entities.Producto;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

// Adaptador para el detalle contable de la bolsa
public class BolsaDetalleAdapter extends RecyclerView.Adapter<BolsaDetalleAdapter.ViewHolder> {
    private List<Producto> items = new ArrayList<>();
    private SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm", Locale.getDefault());

    public void setLista(List<Producto> nuevaLista) {
        this.items = nuevaLista;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_detalle_bolsa_pro, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Producto p = items.get(position);
        holder.tvCant.setText("1x"); // O p.getCantidad() si lo tienes
        holder.tvNombre.setText(p.getNombreProducto().toUpperCase());
        holder.tvPrecio.setText(NumberFormat.getCurrencyInstance(new Locale("es", "CO")).format(p.getPrecioProducto()));

        // Estado y Fecha
        holder.tvEstado.setText("ENTREGADO");
        holder.tvFecha.setText(dateFormat.format(new Date())); // Puedes usar p.getFechaLong()
    }

    @Override
    public int getItemCount() { return items.size(); }

    class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvCant, tvNombre, tvPrecio, tvEstado, tvFecha;
        ViewHolder(View v) {
            super(v);
            tvCant = v.findViewById(R.id.tvCantBolsa);
            tvNombre = v.findViewById(R.id.tvNombreBolsa);
            tvPrecio = v.findViewById(R.id.tvPrecioBolsa);
            tvEstado = v.findViewById(R.id.tvEstadoBolsa);
            tvFecha = v.findViewById(R.id.tvFechaBolsa);
        }
    }
}
