package com.nodo.tpv.adapters;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.card.MaterialCardView;
import com.nodo.tpv.R;
import com.nodo.tpv.data.entities.Usuario;
import java.util.ArrayList;
import java.util.List;

public class UsuarioSlotAdapter extends RecyclerView.Adapter<UsuarioSlotAdapter.ViewHolder> {

    private List<Usuario> usuarios = new ArrayList<>();
    private OnUsuarioClickListener listener;
    private int selectedPosition = -1;

    public interface OnUsuarioClickListener {
        void onUsuarioClick(Usuario usuario);
    }

    public UsuarioSlotAdapter(OnUsuarioClickListener listener) {
        this.listener = listener;
    }

    public void setUsuarios(List<Usuario> usuarios) {
        this.usuarios = usuarios;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_usuario_slot, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Usuario u = usuarios.get(position);

        // MOSTRAR NOMBRE
        holder.tvNombre.setText(u.nombreUsuario);

        // EFECTO DE SELECCIÓN VISUAL
        if (selectedPosition == position) {
            holder.card.setStrokeColor(Color.parseColor("#2E7D32")); // Verde Nodo
            holder.card.setStrokeWidth(4);
            holder.card.setCardBackgroundColor(Color.parseColor("#E8F5E9"));
        } else {
            holder.card.setStrokeWidth(0);
            holder.card.setCardBackgroundColor(Color.WHITE);
        }

        holder.itemView.setOnClickListener(v -> {
            int previous = selectedPosition;
            selectedPosition = holder.getAdapterPosition();
            notifyItemChanged(previous);
            notifyItemChanged(selectedPosition);
            listener.onUsuarioClick(u);
        });
    }

    @Override
    public int getItemCount() {
        return usuarios != null ? usuarios.size() : 0;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvNombre;
        MaterialCardView card;

        ViewHolder(View itemView) {
            super(itemView);
            tvNombre = itemView.findViewById(R.id.tvNombreSlot);
            card = itemView.findViewById(R.id.cardSlot);
        }
    }
}