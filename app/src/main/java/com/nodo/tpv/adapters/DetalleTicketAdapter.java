package com.nodo.tpv.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.nodo.tpv.R;
import com.nodo.tpv.data.dto.DetalleConNombre;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class DetalleTicketAdapter extends RecyclerView.Adapter<DetalleTicketAdapter.ViewHolder> {

    private List<DetalleConNombre> lista = new ArrayList<>();

    public void setLista(List<DetalleConNombre> lista) {
        this.lista = lista;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_detalle_ticket, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        DetalleConNombre item = lista.get(position);
        holder.tvCant.setText(item.detallePedido.cantidad + "x");
        holder.tvNombre.setText(item.nombreProducto);

        NumberFormat format = NumberFormat.getCurrencyInstance(Locale.getDefault());
        holder.tvSubtotal.setText(format.format(item.getSubtotal()));
    }

    @Override
    public int getItemCount() { return lista.size(); }

    class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvCant, tvNombre, tvSubtotal;
        ViewHolder(View v) {
            super(v);
            tvCant = v.findViewById(R.id.tvCantTick);
            tvNombre = v.findViewById(R.id.tvNombreProdTick);
            tvSubtotal = v.findViewById(R.id.tvSubtotalTick);
        }
    }

}
