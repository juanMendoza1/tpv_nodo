package com.nodo.tpv.adapters;

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

    // Interfaz para capturar el clic en el producto
    public interface OnProductoClickListener {
        void onProductoClick(Producto producto);
    }

    public void setOnProductoClickListener(OnProductoClickListener listener) {
        this.listener = listener;
    }

    public void setProductos(List<Producto> productos) {
        this.productos = productos;
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

            // Formatear el precio (BigDecimal) a moneda local
            NumberFormat format = NumberFormat.getCurrencyInstance(Locale.getDefault());
            tvPrecio.setText(format.format(producto.getPrecioProducto()));

            tvStock.setText("Stock: " + producto.getStockActual());

            // Si tienes una lógica de imágenes, aquí usarías Glide o Picasso
            // Por ahora usamos un icono genérico
            ivProducto.setImageResource(R.drawable.ic_inventory);

            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onProductoClick(producto);
            });
        }
    }
}
