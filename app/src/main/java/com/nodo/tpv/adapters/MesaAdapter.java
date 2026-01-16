package com.nodo.tpv.adapters;

import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.card.MaterialCardView;
import com.nodo.tpv.R;
import com.nodo.tpv.data.entities.Mesa;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MesaAdapter extends RecyclerView.Adapter<MesaAdapter.MesaViewHolder> {

    private final List<Mesa> listaMesas;
    private final OnMesaClickListener listener;
    private OnMesaLongClickListener longClickListener;

    // 🔥 Mapa para guardar los saldos de cada mesa de forma dinámica
    private Map<Integer, BigDecimal> saldosMesas = new HashMap<>();

    public interface OnMesaClickListener {
        void onMesaClick(Mesa mesa);
    }

    public interface OnMesaLongClickListener {
        void onMesaLongClick(Mesa mesa);
    }

    public MesaAdapter(List<Mesa> listaMesas, OnMesaClickListener listener) {
        this.listaMesas = listaMesas;
        this.listener = listener;
    }

    public void setOnMesaLongClickListener(OnMesaLongClickListener longClickListener) {
        this.longClickListener = longClickListener;
    }

    // 🔥 Método para actualizar el saldo de una mesa desde el Fragment
    public void actualizarSaldoMesa(int idMesa, BigDecimal saldo) {
        saldosMesas.put(idMesa, saldo);
        notifyDataSetChanged(); // Refresca para mostrar el saldo Matrix
    }

    @NonNull
    @Override
    public MesaViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_mesa_dashboard, parent, false);
        return new MesaViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MesaViewHolder holder, int position) {
        Mesa mesa = listaMesas.get(position);
        BigDecimal saldo = saldosMesas.getOrDefault(mesa.idMesa, BigDecimal.ZERO);
        holder.bind(mesa, saldo, listener, longClickListener);
    }

    @Override
    public int getItemCount() {
        return listaMesas.size();
    }

    static class MesaViewHolder extends RecyclerView.ViewHolder {
        MaterialCardView cardMesa;
        TextView tvNumero, tvEstado, tvTipoJuego, tvDeudaMesa;
        ImageView imgStatus;

        public MesaViewHolder(@NonNull View itemView) {
            super(itemView);
            cardMesa = itemView.findViewById(R.id.cardMesa);
            tvNumero = itemView.findViewById(R.id.tvNumeroMesa);
            tvEstado = itemView.findViewById(R.id.tvEstadoMesa);
            tvTipoJuego = itemView.findViewById(R.id.tvTipoJuego);
            tvDeudaMesa = itemView.findViewById(R.id.tvDeudaMesa); // El nuevo campo Matrix
            imgStatus = itemView.findViewById(R.id.imgStatusMesa);
        }

        public void bind(Mesa mesa, BigDecimal saldo, OnMesaClickListener listener, OnMesaLongClickListener longListener) {
            tvNumero.setText("MESA " + String.format("%02d", mesa.idMesa));

            if ("ABIERTO".equalsIgnoreCase(mesa.estado)) {
                // Estilo MATRIX Green
                tvEstado.setText("SISTEMA ACTIVO");
                tvEstado.setTextColor(Color.parseColor("#00C853"));
                imgStatus.setColorFilter(Color.parseColor("#00E676"));
                cardMesa.setStrokeColor(Color.parseColor("#00E676"));
                cardMesa.setStrokeWidth(5);
                cardMesa.setCardElevation(12f);

                // Configuración de Saldo Matrix
                if (saldo.compareTo(BigDecimal.ZERO) > 0) {
                    tvDeudaMesa.setVisibility(View.VISIBLE);
                    tvDeudaMesa.setText(NumberFormat.getCurrencyInstance(new Locale("es", "CO")).format(saldo));
                } else {
                    tvDeudaMesa.setText("$ 0");
                    tvDeudaMesa.setVisibility(View.VISIBLE);
                }

                if ("3BANDAS".equalsIgnoreCase(mesa.tipoJuego)) {
                    tvTipoJuego.setText("MODO: CRONÓMETRO");
                    tvTipoJuego.setTextColor(Color.parseColor("#FFD600"));
                    imgStatus.setImageResource(R.drawable.ic_timer);
                } else {
                    tvTipoJuego.setText("MODO: POOL");
                    tvTipoJuego.setTextColor(Color.parseColor("#00C853"));
                    imgStatus.setImageResource(R.drawable.ic_pool_table);
                }
                tvTipoJuego.setVisibility(View.VISIBLE);

            } else {
                // Estilo DISPONIBLE (Gris)
                tvEstado.setText("DISPONIBLE");
                tvEstado.setTextColor(Color.parseColor("#9E9E9E"));
                imgStatus.setColorFilter(Color.parseColor("#BDBDBD"));
                imgStatus.setImageResource(R.drawable.ic_table_bar);
                cardMesa.setStrokeColor(Color.parseColor("#E0E0E0"));
                cardMesa.setStrokeWidth(2);
                cardMesa.setCardElevation(2f);
                tvTipoJuego.setVisibility(View.GONE);
                tvDeudaMesa.setVisibility(View.GONE);
            }

            itemView.setOnClickListener(v -> listener.onMesaClick(mesa));

            itemView.setOnLongClickListener(v -> {
                if (longListener != null) {
                    longListener.onMesaLongClick(mesa);
                    return true;
                }
                return false;
            });
        }
    }
}