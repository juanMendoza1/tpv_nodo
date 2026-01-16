package com.nodo.tpv.ui.fragments;

import android.app.AlertDialog;
import android.os.Bundle;
import android.transition.TransitionManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.nodo.tpv.R;
import com.nodo.tpv.adapters.ProductoAdapter;
import com.nodo.tpv.data.entities.Producto;
import com.nodo.tpv.viewmodel.ProductoViewModel;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CatalogoProductosFragment extends Fragment {
    private ProductoViewModel productoViewModel;
    private int idClienteSeleccionado;

    private Button btnFinalizarSeleccion;
    private View cardResumenContable;
    private TextView tvTotalResumen;
    private ResumenContableAdapter resumenAdapter;

    private LinearLayout layoutDetalleColapsable;
    private ImageView btnMinimizarResumen;
    private boolean estaMinimizado = true;

    /**
     * @param idCliente Usar 0 para indicar "Modo Apuesta / Arena"
     */
    public static CatalogoProductosFragment newInstance(int idCliente) {
        CatalogoProductosFragment fragment = new CatalogoProductosFragment();
        Bundle args = new Bundle();
        args.putInt("id_cliente", idCliente);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_catalogo_productos, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        productoViewModel = new ViewModelProvider(requireActivity()).get(ProductoViewModel.class);

        if (getArguments() != null) {
            idClienteSeleccionado = getArguments().getInt("id_cliente");
        }

        // 1. Vincular Vistas
        btnFinalizarSeleccion = view.findViewById(R.id.btnFinalizarSeleccion);
        cardResumenContable = view.findViewById(R.id.cardResumenContable);
        tvTotalResumen = view.findViewById(R.id.tvTotalResumen);
        layoutDetalleColapsable = view.findViewById(R.id.layoutDetalleColapsable);
        btnMinimizarResumen = view.findViewById(R.id.btnMinimizarResumen);
        RecyclerView rvResumen = view.findViewById(R.id.rvResumenApuesta);

        // 2. Configurar Resumen (Bolsa de Apuesta)
        rvResumen.setLayoutManager(new LinearLayoutManager(getContext()));
        resumenAdapter = new ResumenContableAdapter(productoEliminar -> {
            List<Producto> actual = new ArrayList<>(productoViewModel.getListaApuesta().getValue());
            actual.remove(productoEliminar);
            productoViewModel.actualizarListaApuesta(actual);
        });
        rvResumen.setAdapter(resumenAdapter);

        // 3. Lógica Colapsable
        btnMinimizarResumen.setOnClickListener(v -> toggleResumen((ViewGroup) view));

        // 4. Observar la Bolsa (SOLO SI idClienteSeleccionado == 0)
        // Esto evita que el resumen de apuesta aparezca cuando vendes a un cliente individual
        productoViewModel.getListaApuesta().observe(getViewLifecycleOwner(), productos -> {
            if (idClienteSeleccionado == 0 && productos != null && !productos.isEmpty()) {
                cardResumenContable.setVisibility(View.VISIBLE);
                resumenAdapter.updateList(productos);

                BigDecimal total = BigDecimal.ZERO;
                for (Producto p : productos) {
                    total = total.add(p.getPrecioProducto());
                }
                tvTotalResumen.setText(NumberFormat.getCurrencyInstance(new Locale("es", "CO")).format(total));
            } else {
                cardResumenContable.setVisibility(View.GONE);
            }
        });

        // 5. Botón Limpiar Bolsa
        view.findViewById(R.id.btnLimpiarApuesta).setOnClickListener(v -> {
            productoViewModel.limpiarApuesta();
            Toast.makeText(getContext(), "Bolsa vaciada", Toast.LENGTH_SHORT).show();
        });

        // 6. Botón Finalizar
        btnFinalizarSeleccion.setOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());

        // 7. Catálogo Principal
        RecyclerView rvProductos = view.findViewById(R.id.rvProductos);
        rvProductos.setLayoutManager(new GridLayoutManager(getContext(), 3));
        ProductoAdapter adapter = new ProductoAdapter();
        rvProductos.setAdapter(adapter);

        // Listener modificado para pasar por el diálogo de cantidad
        adapter.setOnProductoClickListener(this::mostrarDialogoCantidad);

        productoViewModel.getProductosResultados().observe(getViewLifecycleOwner(), adapter::setProductos);
        productoViewModel.cargarTodosLosProductos();
    }

    private void toggleResumen(ViewGroup root) {
        estaMinimizado = !estaMinimizado;
        TransitionManager.beginDelayedTransition(root);
        if (estaMinimizado) {
            layoutDetalleColapsable.setVisibility(View.GONE);
            btnMinimizarResumen.setRotation(0);
        } else {
            layoutDetalleColapsable.setVisibility(View.VISIBLE);
            btnMinimizarResumen.setRotation(180);
        }
    }

    private void mostrarDialogoCantidad(Producto producto) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_cantidad_producto, null);

        TextView tvCant = dialogView.findViewById(R.id.tvCantidad);
        Button btnMas = dialogView.findViewById(R.id.btnMas);
        Button btnMenos = dialogView.findViewById(R.id.btnMenos);
        Button btnConfirmar = dialogView.findViewById(R.id.btnConfirmarAgregar);

        ((TextView)dialogView.findViewById(R.id.tvNombreConfirmar)).setText(producto.getNombreProducto());
        ((TextView)dialogView.findViewById(R.id.tvPrecioConfirmar)).setText(
                NumberFormat.getCurrencyInstance(new Locale("es", "CO")).format(producto.getPrecioProducto())
        );

        final int[] contadorLocal = {1};

        btnMas.setOnClickListener(v -> {
            contadorLocal[0]++;
            tvCant.setText(String.valueOf(contadorLocal[0]));
        });

        btnMenos.setOnClickListener(v -> {
            if (contadorLocal[0] > 1) {
                contadorLocal[0]--;
                tvCant.setText(String.valueOf(contadorLocal[0]));
            }
        });

        AlertDialog dialog = builder.setView(dialogView).create();

        btnConfirmar.setOnClickListener(v -> {
            // USAMOS EL ID RECIBIDO PARA DECIDIR
            if (idClienteSeleccionado == 0) {
                // CAMINO ARENA: Agrega a la bolsa temporal (APUESTA)
                for (int i = 0; i < contadorLocal[0]; i++) {
                    productoViewModel.agregarProductoAApuesta(producto);
                }
                Toast.makeText(getContext(), "Añadido a la apuesta", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                // En modo duelo no cerramos el catálogo para poder agregar más "munición"
            } else {
                // CAMINO CLIENTE INDIVIDUAL: Inserta directo a la DB
                productoViewModel.insertarConsumoDirecto(idClienteSeleccionado, producto, contadorLocal[0]);
                Toast.makeText(getContext(), "Cargado a la cuenta", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                // Cerramos el catálogo porque es una venta personal directa
                requireActivity().getSupportFragmentManager().popBackStack();
            }
        });
        dialog.show();
    }

    // --- ADAPTADOR INTERNO (Sin cambios, funcional) ---
    private static class ResumenContableAdapter extends RecyclerView.Adapter<ResumenContableAdapter.ViewHolder> {
        private List<Producto> items = new ArrayList<>();
        private final OnItemDeleteListener listener;

        public interface OnItemDeleteListener { void onDelete(Producto p); }

        public ResumenContableAdapter(OnItemDeleteListener listener) { this.listener = listener; }

        public void updateList(List<Producto> newList) {
            this.items = newList;
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_resumen_contable, parent, false);
            return new ViewHolder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Producto p = items.get(position);
            holder.tvNombre.setText(p.getNombreProducto());
            holder.tvPrecio.setText(NumberFormat.getCurrencyInstance(new Locale("es", "CO")).format(p.getPrecioProducto()));
            holder.btnDelete.setOnClickListener(v -> listener.onDelete(p));
        }

        @Override
        public int getItemCount() { return items.size(); }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvNombre, tvPrecio;
            ImageView btnDelete;
            ViewHolder(View v) {
                super(v);
                tvNombre = v.findViewById(R.id.tvNombreResumen);
                tvPrecio = v.findViewById(R.id.tvPrecioResumen);
                btnDelete = v.findViewById(R.id.btnBorrarItem);
            }
        }
    }
}