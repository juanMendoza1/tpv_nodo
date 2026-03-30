package com.nodo.tpv.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.nodo.tpv.R;
import com.nodo.tpv.data.entities.Cliente; // 🔥 Importación actualizada

import java.util.List;

public class ClienteDestinoAdapter extends RecyclerView.Adapter<ClienteDestinoAdapter.ClienteViewHolder> {

    private List<Cliente> listaClientes;
    private final OnClienteClickListener listener;

    // Interfaz actualizada para Clientes
    public interface OnClienteClickListener {
        void onClienteClick(Cliente cliente);
    }

    public ClienteDestinoAdapter(List<Cliente> listaClientes, OnClienteClickListener listener) {
        this.listaClientes = listaClientes;
        this.listener = listener;
    }

    // Método para actualizar la lista (por si se une un cliente nuevo a la mesa)
    public void setClientes(List<Cliente> nuevosClientes) {
        this.listaClientes = nuevosClientes;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ClienteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Usamos el mismo layout de los círculos que diseñamos
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_jugador_destino, parent, false);
        return new ClienteViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ClienteViewHolder holder, int position) {
        Cliente cliente = listaClientes.get(position);

        // 1. Nombre del cliente (usamos el atributo .nombre de la entidad Cliente)
        String nombreCompleto = (cliente.alias != null) ? cliente.alias : "Sin Nombre";
        holder.tvNombre.setText(nombreCompleto);

        // 2. Inicial para el círculo holográfico
        if (!nombreCompleto.isEmpty()) {
            String inicial = nombreCompleto.substring(0, 1).toUpperCase();
            holder.tvInicial.setText(inicial);
        } else {
            holder.tvInicial.setText("?");
        }

        // 3. Efecto "Feedback" al tocar el cristal
        holder.itemView.setOnClickListener(v -> {
            v.animate()
                    .scaleX(0.9f).scaleY(0.9f)
                    .setDuration(100)
                    .withEndAction(() -> {
                        v.animate().scaleX(1f).scaleY(1f).setDuration(100).start();
                        // Ejecutamos la acción de cobro
                        listener.onClienteClick(cliente);
                    }).start();
        });
    }

    @Override
    public int getItemCount() {
        return listaClientes == null ? 0 : listaClientes.size();
    }

    // --- VIEWHOLDER ---
    static class ClienteViewHolder extends RecyclerView.ViewHolder {
        TextView tvInicial;
        TextView tvNombre;

        public ClienteViewHolder(@NonNull View itemView) {
            super(itemView);
            tvInicial = itemView.findViewById(R.id.tvInicialJugador);
            tvNombre = itemView.findViewById(R.id.tvNombreJugadorDestino);
        }
    }
}