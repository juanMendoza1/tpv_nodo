package com.nodo.tpv.adapters;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.nodo.tpv.R;
import com.nodo.tpv.data.dto.DetalleHistorialDuelo;
import com.nodo.tpv.data.dto.LogAgrupadoDTO;
import com.nodo.tpv.data.entities.DetalleDueloTemporalInd;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Adaptador actualizado para mostrar el LOG agrupado por puntos ganados (Hitos).
 * Diseño Ghost (transparente) y distinción de responsables por color.
 */
public class LogDeudaAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_PRODUCT = 1;

    private List<Object> itemsPlanos = new ArrayList<>();
    private Map<Integer, Integer> mapaColores = new HashMap<>(); // idCliente -> Color (Neon)
    private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("es", "CO"));

    public LogDeudaAdapter() {
        currencyFormat.setMaximumFractionDigits(0);
    }

    /**
     * Recibe los colores actuales de la arena para pintar los indicadores de los productos.
     */
    public void setMapaColores(Map<Integer, Integer> colores) {
        this.mapaColores = (colores != null) ? colores : new HashMap<>();
        notifyDataSetChanged();
    }

    /**
     * Aplana los grupos (Hito + sus Productos) para el RecyclerView.
     */
    public void setListaAgrupada(List<LogAgrupadoDTO> grupos) {
        this.itemsPlanos.clear();
        if (grupos != null) {
            for (LogAgrupadoDTO grupo : grupos) {
                itemsPlanos.add(grupo); // Agrega la cabecera (Hito)
                if (grupo.productos != null) {
                    itemsPlanos.addAll(grupo.productos); // Agrega los productos de ese punto
                }
            }
        }
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        return (itemsPlanos.get(position) instanceof LogAgrupadoDTO) ? TYPE_HEADER : TYPE_PRODUCT;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == TYPE_HEADER) {
            // Layout Ghost con fondo transparente
            View v = inflater.inflate(R.layout.item_log_header, parent, false);
            return new HeaderViewHolder(v);
        } else {
            // Layout limpio con bolita de color
            View v = inflater.inflate(R.layout.item_log_producto, parent, false);
            return new ProductViewHolder(v);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Object item = itemsPlanos.get(position);

        if (holder instanceof HeaderViewHolder) {
            LogAgrupadoDTO grupo = (LogAgrupadoDTO) item;
            DetalleDueloTemporalInd hito = grupo.hito;
            HeaderViewHolder h = (HeaderViewHolder) holder;

            h.tvTitulo.setText("PUNTO #" + hito.scoreGlobalAnotador + " - " + hito.aliasAnotador.toUpperCase());
            h.tvSubtotal.setText(currencyFormat.format(grupo.subtotalHito != null ? grupo.subtotalHito : 0));
            h.tvSnapshotMini.setText("MINIS: " + hito.fotoMiniMarcadores);

            // Opcional: El borde de la cabecera brilla con el color de quien hizo el punto
            int colorAnotador = obtenerColorSeguro(hito.idClienteAnotador);
            ((MaterialCardView)h.itemView).setStrokeColor(ColorStateList.valueOf(colorAnotador));

        } else if (holder instanceof ProductViewHolder) {
            DetalleHistorialDuelo prod = (DetalleHistorialDuelo) item;
            ProductViewHolder p = (ProductViewHolder) holder;

            p.tvNombre.setText(prod.nombreProducto != null ? prod.nombreProducto.toUpperCase() : "PRODUCTO");
            p.tvPrecio.setText(currencyFormat.format(prod.precioEnVenta));

            // 🔥 ASIGNACIÓN DE COLOR:
            // Buscamos en el mapa el color que le corresponde a la víctima (idEquipo guarda el ID del cliente)
            int colorVictima = obtenerColorSeguro(prod.idEquipo);
            p.viewColor.setBackgroundTintList(ColorStateList.valueOf(colorVictima));
        }
    }

    private int obtenerColorSeguro(int idCliente) {
        if (mapaColores != null && mapaColores.containsKey(idCliente)) {
            return mapaColores.get(idCliente);
        }
        return Color.parseColor("#4DFFFFFF"); // Gris transparente por defecto
    }

    @Override
    public int getItemCount() {
        return itemsPlanos.size();
    }

    // --- VIEW HOLDERS ---

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitulo, tvSubtotal, tvSnapshotMini;

        HeaderViewHolder(@NonNull View v) {
            super(v);
            tvTitulo = v.findViewById(R.id.tvTituloHito);
            tvSubtotal = v.findViewById(R.id.tvSubtotalHito);
            tvSnapshotMini = v.findViewById(R.id.tvSnapshotMini);
        }
    }

    static class ProductViewHolder extends RecyclerView.ViewHolder {
        TextView tvNombre, tvPrecio;
        View viewColor; // La bolita indicadora

        ProductViewHolder(@NonNull View v) {
            super(v);
            tvNombre = v.findViewById(R.id.tvNombreProductoLog);
            tvPrecio = v.findViewById(R.id.tvPrecioProductoLog);
            viewColor = v.findViewById(R.id.viewColorResponsable);
        }
    }
}