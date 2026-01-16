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
import java.util.ArrayList;
import java.util.List;

public class ResumenApuestaAdapter extends RecyclerView.Adapter<ResumenApuestaAdapter.ViewHolder>{
    private List<Producto> lista = new ArrayList<>();
    private final OnRemoveListener listener;

    public interface OnRemoveListener { void onRemove(Producto p); }
    public ResumenApuestaAdapter(OnRemoveListener l) { this.listener = l; }

    public void setProductos(List<Producto> p) { this.lista = p; notifyDataSetChanged(); }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Usa un layout simple: un texto para nombre, uno para precio y un botón de borrar
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_resumen_contable, parent, false);
        return new ViewHolder(v);
    }

    @Override public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Producto p = lista.get(position);
        holder.tvNombre.setText(p.getNombreProducto());
        holder.tvPrecio.setText(NumberFormat.getCurrencyInstance().format(p.getPrecioProducto()));
        holder.btnBorrar.setOnClickListener(v -> listener.onRemove(p));
    }

    @Override public int getItemCount() { return lista.size(); }

    class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvNombre, tvPrecio;
        View btnBorrar;
        ViewHolder(View v) {
            super(v);
            tvNombre = v.findViewById(R.id.tvNombreResumen);
            tvPrecio = v.findViewById(R.id.tvPrecioResumen);
            btnBorrar = v.findViewById(R.id.btnBorrarItem);
        }
    }
}
