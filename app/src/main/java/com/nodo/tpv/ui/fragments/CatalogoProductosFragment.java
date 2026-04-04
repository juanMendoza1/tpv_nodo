package com.nodo.tpv.ui.fragments;

import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
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

import com.google.android.material.textfield.TextInputEditText;
import com.nodo.tpv.R;
import com.nodo.tpv.adapters.ProductoAdapter;
import com.nodo.tpv.data.dto.DetalleHistorialDuelo;
import com.nodo.tpv.data.entities.Producto;
import com.nodo.tpv.viewmodel.PedidoViewModel;
import com.nodo.tpv.viewmodel.ProductoViewModel;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CatalogoProductosFragment extends Fragment {
    private ProductoViewModel productoViewModel;
    private PedidoViewModel pedidoViewModel;
    private int idClienteSeleccionado;
    private int idMesaActual;

    // Vistas principales
    private TextInputEditText etBuscarProducto;
    private ProductoAdapter productoAdapter;
    private List<Producto> listaProductosCompleta = new ArrayList<>();

    // Vistas del Carrito / Resumen
    private View cardResumenContable;
    private TextView tvTotalResumen;
    private ResumenContableAdapter resumenAdapter;
    private View layoutDetalleColapsable;
    private ImageView btnMinimizarResumen;
    private Button btnFinalizarSeleccion;
    private boolean estaMinimizado = true;

    private final android.os.Handler searchHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable searchRunnable;

    private RecyclerView rvProductos;

    // Carrito temporal (Para cuando le cobramos a un cliente individual)
    private List<ItemCarritoLocal> carritoClienteLocal = new ArrayList<>();

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
        pedidoViewModel = new ViewModelProvider(requireActivity()).get(PedidoViewModel.class);

        if (getArguments() != null) {
            idClienteSeleccionado = getArguments().getInt("id_cliente");
            idMesaActual = getArguments().getInt("id_mesa");
        }

        // --- 1. VINCULACIÓN DE VISTAS ---
        etBuscarProducto = view.findViewById(R.id.etBuscarProducto);
        btnFinalizarSeleccion = view.findViewById(R.id.btnFinalizarSeleccion);
        cardResumenContable = view.findViewById(R.id.cardResumenContable);
        tvTotalResumen = view.findViewById(R.id.tvTotalResumen);
        layoutDetalleColapsable = view.findViewById(R.id.layoutDetalleColapsable);
        btnMinimizarResumen = view.findViewById(R.id.btnMinimizarResumen);
        RecyclerView rvResumen = view.findViewById(R.id.rvResumenApuesta);

        // Lógica de colapsar
        btnMinimizarResumen.setOnClickListener(v -> {
            estaMinimizado = !estaMinimizado;
            TransitionManager.beginDelayedTransition((ViewGroup) view);

            if (estaMinimizado) {
                layoutDetalleColapsable.setVisibility(View.GONE);
                btnFinalizarSeleccion.setVisibility(View.GONE);
                btnMinimizarResumen.setRotation(0);

                // 🔥 El panel se cerró, mostramos los productos de nuevo
                mostrarGrillaProductosAnimada();
            } else {
                layoutDetalleColapsable.setVisibility(View.VISIBLE);
                btnFinalizarSeleccion.setVisibility(View.VISIBLE);
                btnMinimizarResumen.setRotation(180);

                // 🔥 El panel se abrió, ocultamos los productos para no hacer ruido visual
                ocultarGrillaProductosAnimada();
            }
        });

        // --- 2. CONFIGURAR BUSCADOR EN TIEMPO REAL ---
        etBuscarProducto.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // Si el usuario sigue escribiendo, cancelamos la búsqueda anterior
                if (searchRunnable != null) {
                    searchHandler.removeCallbacks(searchRunnable);
                }
            }

            @Override
            public void afterTextChanged(Editable s) {
                // Creamos una nueva tarea de búsqueda
                searchRunnable = () -> filtrarProductosSeguro(s.toString());

                // Esperamos 300ms. Si el usuario no escribe nada más, ¡buscamos!
                searchHandler.postDelayed(searchRunnable, 300);
            }
        });


        // --- 3. CONFIGURAR CATÁLOGO PRINCIPAL ---
        rvProductos = view.findViewById(R.id.rvProductos);
        rvProductos.setLayoutManager(new GridLayoutManager(getContext(), 3));
        productoAdapter = new ProductoAdapter();
        rvProductos.setAdapter(productoAdapter);
        productoAdapter.setOnProductoClickListener(this::mostrarDialogoCantidad);

        productoViewModel.getProductosLiveData().observe(getViewLifecycleOwner(), lista -> {
            if (lista != null) {
                listaProductosCompleta = lista;
                productoAdapter.setProductos(lista);
            }
        });

        // --- 4. CONFIGURAR CARRITO / RESUMEN ---
        rvResumen.setLayoutManager(new LinearLayoutManager(getContext()));
        resumenAdapter = new ResumenContableAdapter(itemVisual -> {
            if (idClienteSeleccionado == 0) {
                DetalleHistorialDuelo d = (DetalleHistorialDuelo) itemVisual.rawObject;
                pedidoViewModel.eliminarDetallePendiente(d.idDetalle);
            } else {
                ItemCarritoLocal i = (ItemCarritoLocal) itemVisual.rawObject;
                carritoClienteLocal.remove(i);
                actualizarCarritoLocalUI();
            }
            Toast.makeText(getContext(), "Ítem retirado", Toast.LENGTH_SHORT).show();
        });
        rvResumen.setAdapter(resumenAdapter);

        // --- 5. OBSERVADORES DEL CARRITO ---
        if (idClienteSeleccionado == 0) {
            pedidoViewModel.obtenerSoloPendientesMesa(idMesaActual).observe(getViewLifecycleOwner(), listaPendientes -> {
                if (listaPendientes != null && !listaPendientes.isEmpty()) {
                    mostrarPanelCarrito();
                    List<ItemCarritoVisual> visuales = new ArrayList<>();
                    BigDecimal totalSuma = BigDecimal.ZERO;

                    for (DetalleHistorialDuelo d : listaPendientes) {
                        ItemCarritoVisual v = new ItemCarritoVisual();
                        v.textoAmostrar = d.nombreProducto;
                        v.precioAmostrar = d.precioEnVenta;
                        v.rawObject = d;
                        visuales.add(v);
                        totalSuma = totalSuma.add(d.precioEnVenta);
                    }
                    resumenAdapter.updateList(visuales);
                    tvTotalResumen.setText(NumberFormat.getCurrencyInstance(new Locale("es", "CO")).format(totalSuma));
                } else {
                    ocultarPanelCarrito();
                }
            });
        } else {
            actualizarCarritoLocalUI();
        }

        // --- 6. BOTONES DE ACCIÓN DEL CARRITO ---
        view.findViewById(R.id.btnLimpiarApuesta).setOnClickListener(v -> {
            if (idClienteSeleccionado == 0) {
                pedidoViewModel.cancelarMunicionPendienteMesa(idMesaActual);
            } else {
                carritoClienteLocal.clear();
                actualizarCarritoLocalUI();
            }
            Toast.makeText(getContext(), "Carrito vaciado", Toast.LENGTH_SHORT).show();
        });

        btnFinalizarSeleccion.setOnClickListener(v -> {
            if (idClienteSeleccionado == 0) {
                requireActivity().getSupportFragmentManager().popBackStack();
            } else {
                if (!carritoClienteLocal.isEmpty()) {
                    for (ItemCarritoLocal item : carritoClienteLocal) {
                        pedidoViewModel.insertarConsumoDirectoEntregado(idClienteSeleccionado, item.producto, item.cantidad);
                    }
                    Toast.makeText(getContext(), "Productos cargados al cliente \u2705", Toast.LENGTH_SHORT).show();
                    requireActivity().getSupportFragmentManager().popBackStack();
                } else {
                    requireActivity().getSupportFragmentManager().popBackStack();
                }
            }
        });

        // --- 7. SINCRONIZACIÓN ---
        com.nodo.tpv.util.SessionManager sessionManager = new com.nodo.tpv.util.SessionManager(requireContext());
        long empresaId = sessionManager.getEmpresaId();
        if (empresaId > 0) {
            productoViewModel.refrescarStockSilencioso(empresaId);
        }
    }

    // --- MÉTODOS DE BÚSQUEDA Y UI ---
    private void filtrarProductos(String query) {
        if (query.isEmpty()) {
            productoAdapter.setProductos(listaProductosCompleta);
        } else {
            List<Producto> filtrada = new ArrayList<>();
            for (Producto p : listaProductosCompleta) {
                if (p.nombreProducto.toLowerCase().contains(query.toLowerCase())) {
                    filtrada.add(p);
                }
            }
            productoAdapter.setProductos(filtrada);
        }
    }

    private void mostrarPanelCarrito() {
        if (cardResumenContable.getVisibility() == View.GONE) {
            cardResumenContable.setVisibility(View.VISIBLE);
            cardResumenContable.setAlpha(0f);
            cardResumenContable.animate().alpha(1f).setDuration(300).start();
        }
    }

    private void ocultarPanelCarrito() {
        if (cardResumenContable.getVisibility() == View.VISIBLE) {
            cardResumenContable.animate().alpha(0f).setDuration(300)
                    .withEndAction(() -> {
                        cardResumenContable.setVisibility(View.GONE);
                        // Reseteamos el estado del panel por si lo vuelven a abrir
                        estaMinimizado = true;
                        layoutDetalleColapsable.setVisibility(View.GONE);
                        btnFinalizarSeleccion.setVisibility(View.GONE);
                        btnMinimizarResumen.setRotation(0);
                    }).start();
        }
        // 🔥 Si el carrito se oculta (ej. al limpiarlo), los productos SIEMPRE deben volver a verse
        mostrarGrillaProductosAnimada();
    }

    // --- MAGIA VISUAL: OCULTAR/MOSTRAR PRODUCTOS ---
    private void ocultarGrillaProductosAnimada() {
        if (rvProductos != null && rvProductos.getVisibility() == View.VISIBLE) {
            rvProductos.animate()
                    .alpha(0f)
                    .scaleX(0.92f) // Efecto zoom hacia atrás
                    .scaleY(0.92f)
                    .setDuration(250)
                    .withEndAction(() -> rvProductos.setVisibility(View.INVISIBLE))
                    .start();
        }
    }

    private void mostrarGrillaProductosAnimada() {
        if (rvProductos != null && (rvProductos.getVisibility() == View.INVISIBLE || rvProductos.getVisibility() == View.GONE)) {
            rvProductos.setVisibility(View.VISIBLE);
            rvProductos.animate()
                    .alpha(1f)
                    .scaleX(1f) // Vuelve a su tamaño normal
                    .scaleY(1f)
                    .setDuration(250)
                    .start();
        }
    }

    // --- LÓGICA DE AGREGAR PRODUCTO ---
    private void mostrarDialogoCantidad(Producto producto) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_cantidad_producto, null);

        TextView tvCant = dialogView.findViewById(R.id.tvCantidad);
        Button btnMas = dialogView.findViewById(R.id.btnMas);
        Button btnMenos = dialogView.findViewById(R.id.btnMenos);
        Button btnConfirmar = dialogView.findViewById(R.id.btnConfirmarAgregar);

        ((TextView)dialogView.findViewById(R.id.tvNombreConfirmar)).setText(producto.nombreProducto);
        ((TextView)dialogView.findViewById(R.id.tvPrecioConfirmar)).setText(
                NumberFormat.getCurrencyInstance(new Locale("es", "CO")).format(producto.precioProducto)
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
                pedidoViewModel.insertarMunicionDueloPendiente(idMesaActual, producto, contadorLocal[0]);
                Toast.makeText(getContext(), "Enviado al carrito (Bolsa)", Toast.LENGTH_SHORT).show();
            } else {
                carritoClienteLocal.add(new ItemCarritoLocal(producto, contadorLocal[0]));
                actualizarCarritoLocalUI();
                Toast.makeText(getContext(), "Agregado al carrito", Toast.LENGTH_SHORT).show();
            }
            dialog.dismiss();
        });
        dialog.show();
    }

    // --- LÓGICA CARRITO LOCAL ---
    private void actualizarCarritoLocalUI() {
        if (carritoClienteLocal.isEmpty()) {
            ocultarPanelCarrito();
        } else {
            mostrarPanelCarrito();
            List<ItemCarritoVisual> visuales = new ArrayList<>();
            BigDecimal totalSuma = BigDecimal.ZERO;

            for (ItemCarritoLocal item : carritoClienteLocal) {
                ItemCarritoVisual v = new ItemCarritoVisual();
                v.textoAmostrar = item.cantidad + "× " + item.producto.nombreProducto;
                v.precioAmostrar = item.producto.precioProducto.multiply(new BigDecimal(item.cantidad));
                v.rawObject = item;
                visuales.add(v);
                totalSuma = totalSuma.add(v.precioAmostrar);
            }
            resumenAdapter.updateList(visuales);
            tvTotalResumen.setText(NumberFormat.getCurrencyInstance(new Locale("es", "CO")).format(totalSuma));
        }
    }

    // --- CLASES INTERNAS Y ADAPTADORES ---
    private static class ItemCarritoLocal {
        Producto producto;
        int cantidad;
        ItemCarritoLocal(Producto p, int c) { this.producto = p; this.cantidad = c; }
    }

    private static class ItemCarritoVisual {
        String textoAmostrar;
        BigDecimal precioAmostrar;
        Object rawObject;
    }

    private static class ResumenContableAdapter extends RecyclerView.Adapter<ResumenContableAdapter.ViewHolder> {
        private List<ItemCarritoVisual> items = new ArrayList<>();
        private final OnItemDeleteListener listener;

        public interface OnItemDeleteListener { void onDelete(ItemCarritoVisual item); }

        public ResumenContableAdapter(OnItemDeleteListener listener) { this.listener = listener; }

        public void updateList(List<ItemCarritoVisual> newList) {
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
            ItemCarritoVisual item = items.get(position);

            holder.tvNombre.setText(item.textoAmostrar);
            holder.tvPrecio.setText(NumberFormat.getCurrencyInstance(new Locale("es", "CO")).format(item.precioAmostrar));

            holder.tvNombre.setTextColor(Color.parseColor("#E0E0E0"));
            holder.tvPrecio.setTextColor(Color.parseColor("#00E676"));
            holder.btnDelete.setColorFilter(Color.parseColor("#FF5252"));

            holder.btnDelete.setOnClickListener(v -> listener.onDelete(item));
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

    private void filtrarProductosSeguro(String query) {
        if (productoAdapter == null || listaProductosCompleta == null) return;

        if (query.trim().isEmpty()) {
            // Enviamos una COPIA de la lista completa para proteger la memoria
            productoAdapter.setProductos(new ArrayList<>(listaProductosCompleta));
        } else {
            List<Producto> filtrada = new ArrayList<>();
            String queryLower = query.toLowerCase().trim();

            for (Producto p : listaProductosCompleta) {
                if (p.getNombreProducto() != null && p.getNombreProducto().toLowerCase().contains(queryLower)) {
                    filtrada.add(p);
                }
            }
            // Enviamos la lista filtrada al Adapter
            productoAdapter.setProductos(filtrada);
        }
    }
}