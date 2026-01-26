package com.nodo.tpv.ui.fragments;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.transition.TransitionManager;

import com.nodo.tpv.R;
import com.nodo.tpv.adapters.HistorialAdapter;
import com.nodo.tpv.adapters.TicketProductosAdapter;
import com.nodo.tpv.data.entities.VentaHistorial;
import com.nodo.tpv.viewmodel.ProductoViewModel;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class HistorialVentasFragment extends Fragment {

    private RecyclerView rvHistorial, rvDetalleIntegrado;
    private HistorialAdapter adapter;
    private TicketProductosAdapter ticketAdapter;
    private ProductoViewModel productoViewModel;

    private View layoutListaHistorial, layoutDetalleVentaIntegrado,
            layoutEvidenciaIntegrada, layoutNoFotoMsg, layoutDashboardExtra;
    private TextView tvTotalDia, tvTotalEfectivo, tvTotalDigital, tvInfoVentaIntegrada;
    private ImageView ivFotoEvidenciaIntegrada;
    private ImageButton btnVolverALista;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_historial_ventas, container, false);

        // Dashboard
        tvTotalDia = view.findViewById(R.id.tvTotalDia);
        tvTotalEfectivo = view.findViewById(R.id.tvTotalEfectivo);
        tvTotalDigital = view.findViewById(R.id.tvTotalDigital);
        layoutDashboardExtra = view.findViewById(R.id.layoutDashboardExtra);

        // Paneles Principales
        layoutListaHistorial = view.findViewById(R.id.layoutListaHistorial);
        layoutDetalleVentaIntegrado = view.findViewById(R.id.layoutDetalleVentaIntegrado);

        // Elementos Auditoría
        tvInfoVentaIntegrada = view.findViewById(R.id.tvInfoVentaIntegrada);
        ivFotoEvidenciaIntegrada = view.findViewById(R.id.ivFotoEvidenciaIntegrada);
        layoutEvidenciaIntegrada = view.findViewById(R.id.layoutEvidenciaIntegrada);
        layoutNoFotoMsg = view.findViewById(R.id.layoutNoFotoMsg);
        btnVolverALista = view.findViewById(R.id.btnVolverALista);

        rvHistorial = view.findViewById(R.id.rvHistorial);
        rvDetalleIntegrado = view.findViewById(R.id.rvDetalleVentaUnica);

        configurarUI();

        productoViewModel = new ViewModelProvider(requireActivity()).get(ProductoViewModel.class);

        // Observar historial general
        productoViewModel.obtenerTodoElHistorial().observe(getViewLifecycleOwner(), lista -> {
            if (lista != null) {
                adapter.setLista(lista);
                actualizarDashboard(lista);
            }
        });

        btnVolverALista.setOnClickListener(v -> cerrarDetalle());

        return view;
    }

    private void configurarUI() {
        // CAMBIO CLAVE: Usamos LinearLayoutManager para que el item ocupe TODA la fila
        rvHistorial.setLayoutManager(new LinearLayoutManager(getContext()));

        adapter = new HistorialAdapter();
        adapter.setOnVentaClickListener(this::abrirDetalle);
        rvHistorial.setAdapter(adapter);

        // Recycler del Detalle interno
        rvDetalleIntegrado.setLayoutManager(new LinearLayoutManager(getContext()));
        ticketAdapter = new TicketProductosAdapter();
        rvDetalleIntegrado.setAdapter(ticketAdapter);
    }

    private void abrirDetalle(VentaHistorial venta) {
        // Animación suave de transición
        TransitionManager.beginDelayedTransition((ViewGroup) getView());

        // Colapsar dashboard y cambiar vistas
        layoutDashboardExtra.setVisibility(View.GONE);
        layoutListaHistorial.setVisibility(View.GONE);
        layoutDetalleVentaIntegrado.setVisibility(View.VISIBLE);

        // Info de cabecera
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy - hh:mm a", Locale.getDefault());
        tvInfoVentaIntegrada.setText("Cliente: " + venta.nombreCliente.toUpperCase() + " | " + sdf.format(new Date(venta.fechaLong)));

        // Gestión de la foto
        if (venta.fotoComprobante != null && !venta.fotoComprobante.isEmpty()) {
            try {
                byte[] decoded = Base64.decode(venta.fotoComprobante, Base64.NO_WRAP);
                Bitmap bitmap = BitmapFactory.decodeByteArray(decoded, 0, decoded.length);
                ivFotoEvidenciaIntegrada.setImageBitmap(bitmap);
                layoutEvidenciaIntegrada.setVisibility(View.VISIBLE);
                layoutNoFotoMsg.setVisibility(View.GONE);
            } catch (Exception e) {
                mostrarSinEvidencia();
            }
        } else {
            mostrarSinEvidencia();
        }

        // 🔥 CARGA DE PRODUCTOS: Aquí es donde traemos el detalle de la venta
        // Usamos el ID de la venta padre para filtrar en 'venta_detalle_historial'
        productoViewModel.obtenerDetallesTicket(venta.idVenta, detalles -> {
            if (detalles != null && !detalles.isEmpty()) {
                ticketAdapter.setLista(detalles);
            } else {
                Log.d("TPV", "No se encontraron productos para la venta: " + venta.idVenta);
            }
        });
    }

    private void mostrarSinEvidencia() {
        layoutEvidenciaIntegrada.setVisibility(View.GONE);
        layoutNoFotoMsg.setVisibility(View.VISIBLE);
    }

    private void cerrarDetalle() {
        TransitionManager.beginDelayedTransition((ViewGroup) getView());
        layoutDashboardExtra.setVisibility(View.VISIBLE);
        layoutDetalleVentaIntegrado.setVisibility(View.GONE);
        layoutListaHistorial.setVisibility(View.VISIBLE);
    }

    private void actualizarDashboard(List<VentaHistorial> lista) {
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal efectivo = BigDecimal.ZERO;
        BigDecimal digital = BigDecimal.ZERO;

        for (VentaHistorial v : lista) {
            total = total.add(v.montoTotal);
            if ("EFECTIVO".equals(v.metodoPago)) {
                efectivo = efectivo.add(v.montoTotal);
            } else {
                digital = digital.add(v.montoTotal);
            }
        }

        NumberFormat nf = NumberFormat.getCurrencyInstance(new Locale("es", "CO"));
        tvTotalDia.setText(nf.format(total));
        tvTotalEfectivo.setText(nf.format(efectivo));
        tvTotalDigital.setText(nf.format(digital));
    }
}