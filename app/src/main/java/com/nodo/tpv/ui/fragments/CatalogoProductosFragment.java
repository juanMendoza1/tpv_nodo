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
import com.nodo.tpv.data.dto.DetalleHistorialDuelo;
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
    private int idMesaActual;

    public static CatalogoProductosFragment newInstance(int idCliente, int idMesa) {
        CatalogoProductosFragment fragment = new CatalogoProductosFragment();
        Bundle args = new Bundle();
        args.putInt("id_cliente", idCliente);
        args.putInt("id_mesa", idMesa);
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
            idMesaActual = getArguments().getInt("id_mesa");
        }

        // --- 1. VINCULACIÓN DE VISTAS ---
        btnFinalizarSeleccion = view.findViewById(R.id.btnFinalizarSeleccion);
        cardResumenContable = view.findViewById(R.id.cardResumenContable);
        tvTotalResumen = view.findViewById(R.id.tvTotalResumen);
        layoutDetalleColapsable = view.findViewById(R.id.layoutDetalleColapsable);
        btnMinimizarResumen = view.findViewById(R.id.btnMinimizarResumen);
        RecyclerView rvResumen = view.findViewById(R.id.rvResumenApuesta);

        // --- 2. CONFIGURAR RESUMEN (Lógica reactiva a la DB) ---
        rvResumen.setLayoutManager(new LinearLayoutManager(getContext()));
        resumenAdapter = new ResumenContableAdapter(productoItem -> {
            // El idProducto aquí contiene el idDetalle de la DB. Cambiamos estado a CANCELADO.
            productoViewModel.eliminarDetallePendiente(productoItem.idProducto);
            Toast.makeText(getContext(), "Producto quitado", Toast.LENGTH_SHORT).show();
        });
        rvResumen.setAdapter(resumenAdapter);

        // --- 3. LÓGICA COLAPSABLE ---
        btnMinimizarResumen.setOnClickListener(v -> toggleResumen((ViewGroup) view));

        // --- 4. OBSERVADOR DE PENDIENTES (BADGE DE BOLSA) ---
        if (idClienteSeleccionado == 0) {
            productoViewModel.obtenerSoloPendientesMesa(idMesaActual).observe(getViewLifecycleOwner(), listaPendientes -> {
                if (listaPendientes != null && !listaPendientes.isEmpty()) {
                    cardResumenContable.setVisibility(View.VISIBLE);
                    List<Producto> visuales = new ArrayList<>();
                    BigDecimal totalSuma = BigDecimal.ZERO;

                    for (DetalleHistorialDuelo d : listaPendientes) {
                        Producto p = new Producto();
                        p.idProducto = d.idDetalle;
                        p.setNombreProducto(d.nombreProducto);
                        p.setPrecioProducto(d.precioEnVenta);
                        visuales.add(p);
                        totalSuma = totalSuma.add(d.precioEnVenta);
                    }
                    resumenAdapter.updateList(visuales);
                    tvTotalResumen.setText(NumberFormat.getCurrencyInstance(new Locale("es", "CO")).format(totalSuma));
                } else {
                    cardResumenContable.setVisibility(View.GONE);
                }
            });
        }

        // --- 5. BOTÓN LIMPIAR BOLSA ---
        view.findViewById(R.id.btnLimpiarApuesta).setOnClickListener(v -> {
            productoViewModel.cancelarMunicionPendienteMesa(idMesaActual);
            Toast.makeText(getContext(), "Bolsa vaciada", Toast.LENGTH_SHORT).show();
        });

        // --- 6. BOTÓN FINALIZAR ---
        btnFinalizarSeleccion.setOnClickListener(v -> {
            requireActivity().getSupportFragmentManager().popBackStack();
        });

        // --- 7. CONFIGURACIÓN DEL CATÁLOGO PRINCIPAL (FLUJO REACTIVO) ---
        RecyclerView rvProductos = view.findViewById(R.id.rvProductos);
        rvProductos.setLayoutManager(new GridLayoutManager(getContext(), 3));
        ProductoAdapter adapter = new ProductoAdapter();
        rvProductos.setAdapter(adapter);
        adapter.setOnProductoClickListener(this::mostrarDialogoCantidad);

        // CAMBIO CLAVE: Observamos directamente el LiveData de Room.
        // Cada vez que 'refrescarStockSilencioso' actualice la DB, esto se dispara solo.
        productoViewModel.getProductosLiveData().observe(getViewLifecycleOwner(), lista -> {
            if (lista != null) {
                adapter.setProductos(lista);
                // El adapter (actualizado previamente) se encarga de bloquear las cards con stock 0
            }
        });

        // --- 8. SINCRONIZACIÓN HÍBRIDA ---
        com.nodo.tpv.util.SessionManager sessionManager = new com.nodo.tpv.util.SessionManager(requireContext());
        long empresaId = sessionManager.getEmpresaId();

        if (empresaId > 0) {
            // Disparamos la actualización desde el servidor en segundo plano
            productoViewModel.refrescarStockSilencioso(empresaId);
        }
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
            if (idClienteSeleccionado == 0) {
                // Modo Arena: Se inserta directo a DB como PENDIENTE
                productoViewModel.insertarMunicionDueloPendiente(idMesaActual, producto, contadorLocal[0]);
                Toast.makeText(getContext(), "Pedido enviado al Badge ⏳", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                // No cerramos el fragmento para que pueda seguir pidiendo
            } else {
                // Modo Cliente Individual
                productoViewModel.insertarConsumoDirectoEntregado(idClienteSeleccionado, producto, contadorLocal[0]);
                Toast.makeText(getContext(), "Producto cargado ✅", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
                requireActivity().getSupportFragmentManager().popBackStack();
            }
        });
        dialog.show();
    }

    // Adaptador Interno
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