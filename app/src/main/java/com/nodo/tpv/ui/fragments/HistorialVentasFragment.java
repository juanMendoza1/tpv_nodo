package com.nodo.tpv.ui.fragments;

import android.app.AlertDialog;
import android.os.Bundle;

import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

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

    private RecyclerView rv;
    private HistorialAdapter adapter;
    private ProductoViewModel productoViewModel; // Unificado el nombre
    private TextView tvTotalDia, tvTotalEfectivo, tvTotalDigital;

    public HistorialVentasFragment() {
        // Required empty public constructor
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_historial_ventas, container, false);

        tvTotalDia = view.findViewById(R.id.tvTotalDia);
        tvTotalEfectivo = view.findViewById(R.id.tvTotalEfectivo);
        tvTotalDigital = view.findViewById(R.id.tvTotalDigital);
        rv = view.findViewById(R.id.rvHistorial);

        // 1. Configurar Adapter Principal
        rv.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new HistorialAdapter();

        // Listener para abrir el ticket al tocar una venta
        adapter.setOnVentaClickListener(this::mostrarDialogoTicket);

        rv.setAdapter(adapter);

        // 2. Inicializar ViewModel
        productoViewModel = new ViewModelProvider(requireActivity()).get(ProductoViewModel.class);

        // 3. Observar cambios en el historial (Lista General)
        productoViewModel.obtenerTodoElHistorial().observe(getViewLifecycleOwner(), lista -> {
            if (lista != null) {
                adapter.setLista(lista);
                actualizarDashboard(lista);
            }
        });

        return view;
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

        NumberFormat nf = NumberFormat.getCurrencyInstance();
        tvTotalDia.setText(nf.format(total));
        tvTotalEfectivo.setText(nf.format(efectivo));
        tvTotalDigital.setText(nf.format(digital));
    }

    private void mostrarDialogoTicket(VentaHistorial venta) {
        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext());
        View view = getLayoutInflater().inflate(R.layout.layout_ticket_detalle, null);

        TextView tvFecha = view.findViewById(R.id.tvFechaTicket);
        TextView tvCliente = view.findViewById(R.id.tvClienteTicket);
        TextView tvTotal = view.findViewById(R.id.tvTotalTicket);
        TextView tvMetodo = view.findViewById(R.id.tvMetodoTicket);
        RecyclerView rvDetalle = view.findViewById(R.id.rvDetalleTicket);
        Button btnCerrar = view.findViewById(R.id.btnCerrarTicket);

        // OPCIONAL: Botón de confirmar (si decides agregarlo al XML)
        // Button btnConfirmar = view.findViewById(R.id.btnConfirmarPagoAdmin);

        // Llenar datos básicos
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault());
        tvFecha.setText(sdf.format(new Date(venta.fechaLong)));
        tvCliente.setText("CLIENTE: " + venta.nombreCliente.toUpperCase());
        tvMetodo.setText("MÉTODO DE PAGO: " + venta.metodoPago);

        NumberFormat nf = NumberFormat.getCurrencyInstance();
        tvTotal.setText(nf.format(venta.montoTotal));

        // Configurar el RecyclerView interno del ticket
        rvDetalle.setLayoutManager(new LinearLayoutManager(getContext()));
        TicketProductosAdapter ticketAdapter = new TicketProductosAdapter();
        rvDetalle.setAdapter(ticketAdapter);

        productoViewModel.obtenerDetallesTicket(venta.idVenta, detalles -> {
            if (detalles != null) {
                // Aquí podrías incluso separar la lista en dos grupos: Normal y Duelo
                ticketAdapter.setLista(detalles);
            }
        });

        AlertDialog dialog = builder.setView(view).create();

        // Hacer el fondo transparente para que se vea el estilo del ticket
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        // Lógica para confirmar pago si está PENDIENTE
        /* if (venta.estado.equals("PENDIENTE")) {
            btnConfirmar.setVisibility(View.VISIBLE);
            btnConfirmar.setOnClickListener(v -> {
                productoViewModel.confirmarPagoAdmin(venta.idVenta);
                dialog.dismiss();
                Toast.makeText(getContext(), "Pago Confirmado por Administrador", Toast.LENGTH_SHORT).show();
            });
        }
        */

        btnCerrar.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }
}