package com.nodo.tpv.adapters;

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
    private NumberFormat nf = NumberFormat.getCurrencyInstance(new Locale("es", "CO"));

    public LogBatallaAdapter(List<DetalleHistorialDuelo> items) { this.items = items; }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup p, int t) {
        View v = LayoutInflater.from(p.getContext()).inflate(R.layout.item_log_batalla, p, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder h, int p) {
        DetalleHistorialDuelo item = items.get(p);
        h.tvMarcador.setText(item.marcadorAlMomento != null ? item.marcadorAlMomento : "0-0");
        h.tvEquipo.setText(item.idEquipo == 1 ? "🔵" : "🔴");
        h.tvProducto.setText(item.nombreProducto);
        h.tvValor.setText(nf.format(item.precioEnVenta));
    }

    @Override public int getItemCount() { return items.size(); }

    class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvMarcador, tvEquipo, tvProducto, tvValor;
        ViewHolder(View v) {
            super(v);
            tvMarcador = v.findViewById(R.id.txtMarcador);
            tvEquipo = v.findViewById(R.id.txtEquipo);
            tvProducto = v.findViewById(R.id.txtProducto);
            tvValor = v.findViewById(R.id.txtValor);
        }
    }
}
