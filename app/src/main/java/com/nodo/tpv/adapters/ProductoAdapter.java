package com.nodo.tpv.adapters;

import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.nodo.tpv.R;
import com.nodo.tpv.data.entities.Producto;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ProductoAdapter extends RecyclerView.Adapter<ProductoAdapter.ProductoViewHolder> {
    private List<Producto> productos = new ArrayList<>();
    private OnProductoClickListener listener;

    public interface OnProductoClickListener {
        void onProductoClick(Producto producto);
    }

    public void setOnProductoClickListener(OnProductoClickListener listener) {
        this.listener = listener;
    }

    public void setProductos(List<Producto> productos) {
        this.productos = productos;
        // Importante: notifyDataSetChanged permitirá que se refresquen los
        // estados de stock cuando el LiveData detecte cambios en Room.
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ProductoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_producto, parent, false);
        return new ProductoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ProductoViewHolder holder, int position) {
        holder.bind(productos.get(position), listener);
    }

    @Override
    public int getItemCount() {
        return productos.size();
    }

    static class ProductoViewHolder extends RecyclerView.ViewHolder {
        TextView tvNombre, tvPrecio, tvStock;
        ImageView ivProducto;

        public ProductoViewHolder(@NonNull View itemView) {
            super(itemView);
            tvNombre = itemView.findViewById(R.id.tvNombreProducto);
            tvPrecio = itemView.findViewById(R.id.tvPrecioProducto);
            tvStock = itemView.findViewById(R.id.tvStock);
            ivProducto = itemView.findViewById(R.id.ivProducto);
        }

        public void bind(Producto producto, OnProductoClickListener listener) {
            tvNombre.setText(producto.getNombreProducto());

            // 1. Formatear precio
            NumberFormat format = NumberFormat.getCurrencyInstance(new Locale("es", "CO"));
            tvPrecio.setText(format.format(producto.getPrecioProducto()));

            // 2. Mostrar stock
            tvStock.setText("Stock: " + producto.getStockActual());

            // 3. LÓGICA DE BLOQUEO POR STOCK 0
            if (producto.getStockActual() <= 0) {
                // Estado visual desactivado (Gris y traslúcido)
                itemView.setAlpha(0.5f);
                itemView.setEnabled(false); // Bloquea el toque a nivel de sistema

                // Poner la imagen en escala de grises para que sea intuitivo
                ColorMatrix matrix = new ColorMatrix();
                matrix.setSaturation(0);
                ivProducto.setColorFilter(new ColorMatrixColorFilter(matrix));

                tvStock.setTextColor(itemView.getContext().getResources().getColor(android.R.color.holo_red_dark));
                tvStock.setText("AGOTADO");

                // Quitamos el listener para asegurar que no se abra el diálogo
                itemView.setOnClickListener(null);
            } else {
                // Estado visual normal (Activo)
                itemView.setAlpha(1.0f);
                itemView.setEnabled(true);

                // Quitar filtro de grises
                ivProducto.clearColorFilter();

                tvStock.setTextColor(itemView.getContext().getResources().getColor(android.R.color.white));

                // Solo si hay stock, permitimos el clic
                itemView.setOnClickListener(v -> {
                    if (listener != null) listener.onProductoClick(producto);
                });
            }

            // Imagen por defecto
            ivProducto.setImageResource(R.drawable.ic_inventory);
        }
    }
}