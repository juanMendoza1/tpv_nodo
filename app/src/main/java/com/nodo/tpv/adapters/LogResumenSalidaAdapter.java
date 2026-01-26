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
import java.util.List;
import java.util.Locale;

public class LogResumenSalidaAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_PRODUCT = 1;

    private List<Object> itemsPlanos = new ArrayList<>();
    private int idProtagonista;
    private int colorProtagonista;
    private final NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(new Locale("es", "CO"));

    public LogResumenSalidaAdapter() {
        currencyFormat.setMaximumFractionDigits(0);
    }

    public void configurarProtagonista(int idCliente, int color) {
        this.idProtagonista = idCliente;
        this.colorProtagonista = color;
    }

    public void setListaAgrupada(List<LogAgrupadoDTO> grupos) {
        this.itemsPlanos.clear();
        if (grupos != null) {
            for (LogAgrupadoDTO grupo : grupos) {
                // Siempre añadimos el Hito (Cabecera) para que vea el progreso
                itemsPlanos.add(grupo);

                // Filtramos: Solo productos cargados a ESTE cliente
                if (grupo.productos != null) {
                    for (DetalleHistorialDuelo p : grupo.productos) {
                        if (p.idEquipo == idProtagonista) {
                            itemsPlanos.add(p);
                        }
                    }
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
            return new HeaderViewHolder(inflater.inflate(R.layout.item_log_header_salida, parent, false));
        } else {
            return new ProductViewHolder(inflater.inflate(R.layout.item_log_producto_salida, parent, false));
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Object item = itemsPlanos.get(position);

        if (holder instanceof HeaderViewHolder) {
            LogAgrupadoDTO grupo = (LogAgrupadoDTO) item;
            DetalleDueloTemporalInd hito = grupo.hito;
            HeaderViewHolder h = (HeaderViewHolder) holder;

            boolean ganeYo = hito.idClienteAnotador == idProtagonista;

            h.tvTitulo.setText("RONDA #" + hito.scoreGlobalAnotador);
            h.tvInfo.setText(ganeYo ? "¡PUNTO PARA TI!" : "PUNTO DE " + hito.aliasAnotador.toUpperCase());

            // Si gané, brilla con mi color. Si no, se ve gris sutil.
            int colorStroke = ganeYo ? colorProtagonista : Color.parseColor("#33FFFFFF");
            h.card.setStrokeColor(ColorStateList.valueOf(colorStroke));
            h.tvTitulo.setTextColor(ganeYo ? colorProtagonista : Color.WHITE);

        } else if (holder instanceof ProductViewHolder) {
            DetalleHistorialDuelo prod = (DetalleHistorialDuelo) item;
            ProductViewHolder p = (ProductViewHolder) holder;

            p.tvNombre.setText(prod.nombreProducto.toUpperCase());
            p.tvPrecio.setText(currencyFormat.format(prod.precioEnVenta));
            // La bolita de color siempre brilla con mi color porque son MIS consumos
            p.indicator.setBackgroundTintList(ColorStateList.valueOf(colorProtagonista));
        }
    }

    @Override
    public int getItemCount() { return itemsPlanos.size(); }

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        TextView tvTitulo, tvInfo;
        MaterialCardView card;
        HeaderViewHolder(@NonNull View v) {
            super(v);
            tvTitulo = v.findViewById(R.id.tvTituloHitoSalida);
            tvInfo = v.findViewById(R.id.tvInfoHitoSalida);
            card = (MaterialCardView) v;
        }
    }

    static class ProductViewHolder extends RecyclerView.ViewHolder {
        TextView tvNombre, tvPrecio;
        View indicator;
        ProductViewHolder(@NonNull View v) {
            super(v);
            tvNombre = v.findViewById(R.id.tvNombreProdSalida);
            tvPrecio = v.findViewById(R.id.tvPrecioProdSalida);
            indicator = v.findViewById(R.id.viewIndicatorSalida);
        }
    }
}