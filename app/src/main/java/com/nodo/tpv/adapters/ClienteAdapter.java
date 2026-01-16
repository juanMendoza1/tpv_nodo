package com.nodo.tpv.adapters;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.nodo.tpv.R;
import com.nodo.tpv.data.dto.ClienteConSaldo;
import com.nodo.tpv.data.entities.Cliente;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ClienteAdapter extends RecyclerView.Adapter<ClienteAdapter.ClienteViewHolder> {
    private List<ClienteConSaldo> listaClientes = new ArrayList<>();
    private OnClienteClickListener listener;

    private boolean modoSeleccionVersus = false;

    // 🔥 NUEVA LÓGICA MODO POOL DINÁMICO
    private Map<Integer, Integer> mapaColoresPool = new HashMap<>(); // idCliente -> Color
    private final int[] PALETA_EQUIPOS = {
            Color.parseColor("#00E5FF"), // Cyan
            Color.parseColor("#FF1744"), // Rojo
            Color.parseColor("#FFD54F"), // Amarillo
            Color.parseColor("#4CAF50"), // Verde
            Color.parseColor("#AA00FF")  // Morado
    };

    // Mantenemos compatibilidad con 3 BANDAS (Sin modificar su lógica)
    private List<Integer> idsMulticolor = new ArrayList<>();
    private List<Integer> coloresAsignados = new ArrayList<>();
    private List<Integer> idsBloqueadosPorDuelo = new ArrayList<>();

    // Listas antiguas (mantener para no romper llamadas existentes si las hay, aunque se usarán menos)
    private List<Integer> seleccionadosEquipoA = new ArrayList<>();
    private List<Integer> seleccionadosEquipoB = new ArrayList<>();

    public interface OnClienteClickListener {
        void onVerClick(Cliente cliente);
        void onAgregarClick(Cliente cliente);
        void onPagarClick(Cliente cliente);
        void onLongClickVersus(Cliente cliente);
        void onShortClickVersus(Cliente cliente); // Nuevo para rotar colores
    }

    public void setOnClienteClickListener(OnClienteClickListener listener) {
        this.listener = listener;
    }

    public void setClientes(List<ClienteConSaldo> clientes) {
        this.listaClientes = clientes;
        notifyDataSetChanged();
    }

    // 🔥 NUEVOS MÉTODOS PARA EL PINTADO DINÁMICO
    public void rotarColorCliente(int idCliente) {
        int colorActual = mapaColoresPool.containsKey(idCliente) ? mapaColoresPool.get(idCliente) : -1;
        int siguienteColorIndex = 0;

        if (colorActual != -1) {
            for (int i = 0; i < PALETA_EQUIPOS.length; i++) {
                if (PALETA_EQUIPOS[i] == colorActual) {
                    siguienteColorIndex = (i + 1) % PALETA_EQUIPOS.length;
                    break;
                }
            }
        }
        mapaColoresPool.put(idCliente, PALETA_EQUIPOS[siguienteColorIndex]);
        notifyDataSetChanged();
    }

    public void excluirCliente(int idCliente) {
        mapaColoresPool.remove(idCliente);
        notifyDataSetChanged();
    }

    public Map<Integer, Integer> getMapaColoresPool() {
        return mapaColoresPool;
    }

    // Mantenemos compatibilidad con lógica de 3 BANDAS
    public void setModoMulticolor(List<Integer> ids, List<Integer> colores) {
        this.idsMulticolor = ids;
        this.coloresAsignados = colores;
        this.mapaColoresPool.clear();
        notifyDataSetChanged();
    }

    public void actualizarBloqueoDuelo(List<Integer> idsParticipantes) {
        this.idsBloqueadosPorDuelo = (idsParticipantes != null) ? idsParticipantes : new ArrayList<>();
        notifyDataSetChanged();
    }

    public void setModoSeleccionVersus(boolean activo) {
        this.modoSeleccionVersus = activo;
        notifyDataSetChanged();
    }

    public void limpiarSelecciones() {
        mapaColoresPool.clear();
        idsMulticolor.clear();
        coloresAsignados.clear();
        seleccionadosEquipoA.clear();
        seleccionadosEquipoB.clear();
        this.modoSeleccionVersus = false;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ClienteViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_cliente, parent, false);
        return new ClienteViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ClienteViewHolder holder, int position) {
        ClienteConSaldo item = listaClientes.get(position);
        boolean estaEnDuelo = idsBloqueadosPorDuelo.contains(item.cliente.idCliente);

        int colorBorde = 0;

        // Prioridad 1: Modo Multicolor (3 BANDAS)
        if (!idsMulticolor.isEmpty() && idsMulticolor.contains(item.cliente.idCliente)) {
            int index = idsMulticolor.indexOf(item.cliente.idCliente);
            colorBorde = coloresAsignados.get(index);
        }
        // Prioridad 2: Modo Pintado Dinámico (POOL)
        else if (mapaColoresPool.containsKey(item.cliente.idCliente)) {
            colorBorde = mapaColoresPool.get(item.cliente.idCliente);
        }
        // Prioridad 3: Listas antiguas (Azul/Rojo)
        else if (seleccionadosEquipoA.contains(item.cliente.idCliente)) {
            colorBorde = Color.parseColor("#00E5FF");
        } else if (seleccionadosEquipoB.contains(item.cliente.idCliente)) {
            colorBorde = Color.parseColor("#FF1744");
        }

        holder.bind(item, listener, modoSeleccionVersus, colorBorde, estaEnDuelo);
    }

    @Override
    public int getItemCount() {
        return listaClientes.size();
    }

    class ClienteViewHolder extends RecyclerView.ViewHolder {
        TextView tvAlias, tvTipo, tvAmount;
        ImageView ivIcono;
        ImageButton btnVer, btnAdd, btnPay;
        View bgIcono;
        MaterialCardView cardPadre;

        public ClienteViewHolder(@NonNull View itemView) {
            super(itemView);
            cardPadre = (MaterialCardView) itemView;
            bgIcono = itemView.findViewById(R.id.bgIcono);
            tvAlias = itemView.findViewById(R.id.tvAlias);
            tvTipo = itemView.findViewById(R.id.tvClientType);
            tvAmount = itemView.findViewById(R.id.tvAmount);
            ivIcono = itemView.findViewById(R.id.ivRolIcon);
            btnVer = itemView.findViewById(R.id.btnView);
            btnAdd = itemView.findViewById(R.id.btnAdd);
            btnPay = itemView.findViewById(R.id.btnPay);
        }

        public void bind(ClienteConSaldo item, OnClienteClickListener listener, boolean modoSeleccion, int colorBorde, boolean bloqueado) {
            Cliente cliente = item.cliente;
            tvAlias.setText(cliente.alias);

            // 1. Reset visual a Blanco/Neutro
            cardPadre.setStrokeWidth(0);
            bgIcono.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#F5F5F5")));
            ivIcono.setColorFilter(Color.parseColor("#757575"));

            // 2. Aplicar Color Neón si tiene equipo asignado
            if (colorBorde != 0) {
                cardPadre.setStrokeColor(colorBorde);
                cardPadre.setStrokeWidth(8);
                bgIcono.setBackgroundTintList(ColorStateList.valueOf(colorBorde));
                ivIcono.setColorFilter(Color.WHITE);
            }

            // 3. Bloqueos
            if (bloqueado) {
                btnPay.setEnabled(false);
                btnPay.setAlpha(0.3f); // Visualmente desactivado
                btnPay.setOnClickListener(null); // Quitar el listener
                // Opcional: mostrar un Toast al intentar clickear la card
                cardPadre.setOnClickListener(v -> Toast.makeText(itemView.getContext(), "Cliente en Duelo", Toast.LENGTH_SHORT).show());
            } else {
                btnPay.setEnabled(true);
                btnPay.setAlpha(1.0f);
                btnPay.setOnClickListener(v -> { if(listener != null) listener.onPagarClick(cliente); });
                cardPadre.setOnClickListener(null); // Resetear si era bloqueado
            }

            // 4. Saldo
            NumberFormat format = NumberFormat.getCurrencyInstance(new Locale("es", "CO"));
            tvAmount.setText(format.format(item.saldoTotal != null ? item.saldoTotal : BigDecimal.ZERO));
            tvAmount.setTextColor(item.saldoTotal != null && item.saldoTotal.compareTo(BigDecimal.ZERO) > 0 ? Color.parseColor("#2E7D32") : Color.GRAY);

            tvTipo.setText(cliente.idTipoCliente == 1 ? "INDIVIDUAL" : "GRUPO");
            ivIcono.setImageResource(cliente.idTipoCliente == 1 ? R.drawable.ic_person : R.drawable.ic_group);

            btnVer.setOnClickListener(v -> { if(listener != null) listener.onVerClick(cliente); });
            btnAdd.setOnClickListener(v -> { if(listener != null) listener.onAgregarClick(cliente); });

            // 🔥 GESTIÓN DE SELECCIÓN DINÁMICA
            itemView.setOnClickListener(v -> {
                if (modoSeleccion && listener != null) {
                    listener.onShortClickVersus(cliente);
                }
            });

            itemView.setOnLongClickListener(v -> {
                if (modoSeleccion && listener != null) {
                    listener.onLongClickVersus(cliente);
                    return true;
                }
                return false;
            });
        }
    }
}