package com.nodo.tpv.adapters;

import android.graphics.Color;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.nodo.tpv.R;
import com.nodo.tpv.data.entities.VentaDetalleHistorial;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class TicketProductosAdapter extends RecyclerView.Adapter<TicketProductosAdapter.VH>{

    private List<VentaDetalleHistorial> lista = new ArrayList<>();
    private final NumberFormat nf = NumberFormat.getCurrencyInstance(new Locale("es", "CO"));

    public void setLista(List<VentaDetalleHistorial> lista) {
        this.lista = lista;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Inflamos el layout de la fila del ticket
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_ticket_fila, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH holder, int position) {
        VentaDetalleHistorial d = lista.get(position);

        // 1. Configuración de Cantidad y Subtotal
        holder.tvCant.setText(d.cantidad + "x");
        BigDecimal subtotal = d.precioUnitario.multiply(new BigDecimal(d.cantidad));
        holder.tvSub.setText(nf.format(subtotal));

        // 2. Lógica de Distinción: ¿Es un consumo normal o una pérdida de duelo?
        if (d.esApuesta) {
            // Si es apuesta, le damos un estilo de "Daño de Combate"
            holder.tvProd.setText("⚔️ " + d.nombreProducto + " (DUELO)");
            holder.tvProd.setTextColor(Color.parseColor("#E64A19")); // Un naranja/rojo oscuro
            holder.tvProd.setTypeface(null, Typeface.BOLD_ITALIC);

            // Opcional: El subtotal de duelo también puede resaltar
            holder.tvSub.setTextColor(Color.parseColor("#D32F2F"));
        } else {
            // Consumo normal
            holder.tvProd.setText(d.nombreProducto);
            holder.tvProd.setTextColor(Color.BLACK);
            holder.tvProd.setTypeface(null, Typeface.NORMAL);
            holder.tvSub.setTextColor(Color.DKGRAY);
        }
    }

    @Override
    public int getItemCount() { return lista.size(); }

    class VH extends RecyclerView.ViewHolder {
        TextView tvCant, tvProd, tvSub;
        VH(View v) {
            super(v);
            tvCant = v.findViewById(R.id.tvCantFila);
            tvProd = v.findViewById(R.id.tvProdFila);
            tvSub = v.findViewById(R.id.tvSubtotalFila);
        }
    }
}