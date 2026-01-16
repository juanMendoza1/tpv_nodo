package com.nodo.tpv.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.nodo.tpv.R;
import com.nodo.tpv.adapters.MesaAdapter;
import com.nodo.tpv.data.database.AppDatabase;
import com.nodo.tpv.data.entities.Mesa;
import com.nodo.tpv.data.entities.Usuario;
import com.nodo.tpv.ui.main.MainActivity;
import com.nodo.tpv.util.SessionManager;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FragmentEsperaVenta extends Fragment {

    private RecyclerView rvMesas;
    private final ExecutorService executorService = Executors.newFixedThreadPool(4);
    private boolean isExpanded = true; // Control de pantalla completa

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_espera_venta, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Vinculación de UI
        rvMesas = view.findViewById(R.id.rvMesasDashboard);
        MaterialButton btnExpandir = view.findViewById(R.id.btnExpandirDashboard);
        MaterialButton btnHabilitarDirecto = view.findViewById(R.id.btnHabilitarMesaDirecto);

        // Configuración de Grilla (4 columnas al estar expandido)
        rvMesas.setLayoutManager(new GridLayoutManager(getContext(), 4));

        // 1. Lógica de Expansión (70% <-> 100%)
        btnExpandir.setOnClickListener(v -> {
            isExpanded = !isExpanded;
            if (requireActivity() instanceof MainActivity) {
                ((MainActivity) requireActivity()).setExpandirContenedor(isExpanded);
                btnExpandir.setIconResource(isExpanded ? R.drawable.ic_fullscreen_exit : R.drawable.ic_fullscreen);
                btnExpandir.setText(isExpanded ? "CONTRAER" : "EXPANDIR");
            }
        });

        // 2. Lógica de Habilitar Mesa (Trasladada de FragmentSesion)
        btnHabilitarDirecto.setOnClickListener(v -> {
            SessionManager sessionManager = new SessionManager(requireContext());
            Usuario user = sessionManager.obtenerUsuario();
            if (user != null) {
                mostrarDialogoEpicHabilitar(user);
            } else {
                Toast.makeText(getContext(), "Inicie sesión primero", Toast.LENGTH_SHORT).show();
            }
        });

        cargarMesas();
    }

    // --- LÓGICA DE CARGA Y DATOS ---

    private void cargarMesas() {
        executorService.execute(() -> {
            List<Mesa> mesas = AppDatabase.getInstance(requireContext()).mesaDao().obtenerTodasLasMesas();

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    MesaAdapter adapter = new MesaAdapter(mesas, mesa -> {
                        if (!"ABIERTO".equalsIgnoreCase(mesa.estado)) {
                            // En lugar de habilitar por clic, podrías usar el botón superior
                            // o permitirlo también por aquí:
                            mostrarDialogoHabilitarMesa(mesa);
                        } else {
                            abrirGestionMesa(mesa);
                        }
                    });

                    adapter.setOnMesaLongClickListener(this::validarCierreMesa);
                    rvMesas.setAdapter(adapter);

                    // Carga de saldos Matrix en tiempo real
                    for (Mesa m : mesas) {
                        if ("ABIERTO".equalsIgnoreCase(m.estado)) {
                            executorService.execute(() -> {
                                BigDecimal deuda = AppDatabase.getInstance(requireContext())
                                        .detallePedidoDao().obtenerTotalDeudaMesa(m.idMesa);
                                getActivity().runOnUiThread(() ->
                                        adapter.actualizarSaldoMesa(m.idMesa, deuda != null ? deuda : BigDecimal.ZERO)
                                );
                            });
                        }
                    }
                });
            }
        });
    }

    // --- LÓGICA DE CIERRE (CANDADO DE SEGURIDAD) ---

    private void validarCierreMesa(Mesa mesa) {
        if (!"ABIERTO".equalsIgnoreCase(mesa.estado)) return;

        executorService.execute(() -> {
            BigDecimal deudaTotal = AppDatabase.getInstance(requireContext())
                    .detallePedidoDao().obtenerTotalDeudaMesa(mesa.idMesa);

            if (deudaTotal == null) deudaTotal = BigDecimal.ZERO;
            final BigDecimal finalDeuda = deudaTotal;

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    if (finalDeuda.compareTo(BigDecimal.ZERO) > 0) {
                        String total = NumberFormat.getCurrencyInstance(new Locale("es", "CO")).format(finalDeuda);
                        new MaterialAlertDialogBuilder(requireContext())
                                .setTitle("⚠️ Mesa con Deuda")
                                .setMessage("No se puede cerrar la mesa #" + mesa.idMesa + ".\nSaldo pendiente: " + total)
                                .setPositiveButton("IR A COBRAR", (d, w) -> abrirGestionMesa(mesa))
                                .show();
                    } else {
                        confirmarCierreFinal(mesa);
                    }
                });
            }
        });
    }

    private void confirmarCierreFinal(Mesa mesa) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Liberar Mesa #" + mesa.idMesa)
                .setMessage("¿Confirmas que la mesa está libre y lista para el siguiente turno?")
                .setPositiveButton("SÍ, LIBERAR", (dialog, which) -> ejecutarCierreMesa(mesa))
                .setNegativeButton("CANCELAR", null).show();
    }

    private void ejecutarCierreMesa(Mesa mesa) {
        executorService.execute(() -> {
            mesa.estado = "CERRADA";
            mesa.fechaCierre = System.currentTimeMillis();
            AppDatabase.getInstance(requireContext()).mesaDao().actualizarMesa(mesa);
            getActivity().runOnUiThread(() -> {
                Toast.makeText(getContext(), "Mesa liberada", Toast.LENGTH_SHORT).show();
                cargarMesas();
            });
        });
    }

    // --- LÓGICA DE HABILITACIÓN (MODO EPIC) ---

    private void mostrarDialogoEpicHabilitar(Usuario user) {
        View v = getLayoutInflater().inflate(R.layout.dialog_seleccion_modalidad, null);
        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext(), R.style.MaterialAlertDialog_Rounded)
                .setView(v).create();

        // Animación de entrada
        v.setAlpha(0f);
        v.setScaleX(0.8f);
        v.animate().alpha(1f).scaleX(1f).setDuration(300).start();

        v.findViewById(R.id.cardModoPool).setOnClickListener(view -> {
            procederAAbrirMesaManual(user.idMesa, "POOL");
            dialog.dismiss();
        });

        v.findViewById(R.id.cardModoBillar).setOnClickListener(view -> {
            procederAAbrirMesaManual(user.idMesa, "3BANDAS");
            dialog.dismiss();
        });

        dialog.show();
    }

    // Para habilitar una mesa específica al hacerle clic
    private void mostrarDialogoHabilitarMesa(Mesa mesa) {
        String[] opciones = {"🎱 POOL", "⏱ 3 BANDAS"};
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("HABILITAR MESA " + mesa.idMesa)
                .setItems(opciones, (dialog, which) -> {
                    procederAAbrirMesaManual(mesa.idMesa, (which == 0) ? "POOL" : "3BANDAS");
                }).show();
    }

    private void procederAAbrirMesaManual(int idMesa, String modo) {
        executorService.execute(() -> {
            // 1. Buscamos la mesa
            Mesa mesaExistente = AppDatabase.getInstance(requireContext()).mesaDao().obtenerMesaPorId(idMesa);

            // 2. Creamos una instancia final o que no se reasigne
            Mesa mesaParaProcesar;
            if (mesaExistente == null) {
                mesaParaProcesar = new Mesa();
                mesaParaProcesar.idMesa = idMesa;
            } else {
                mesaParaProcesar = mesaExistente;
            }

            // 3. Seteamos los valores
            mesaParaProcesar.estado = "ABIERTO";
            mesaParaProcesar.tipoJuego = modo;
            mesaParaProcesar.fechaApertura = System.currentTimeMillis();

            // 4. Guardamos en BD
            AppDatabase.getInstance(requireContext()).mesaDao().insertarMesa(mesaParaProcesar);

            // 5. Para el hilo de UI, la variable mesaParaProcesar ahora es "efectivamente final"
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), "Mesa #" + idMesa + " iniciada (" + modo + ")", Toast.LENGTH_SHORT).show();
                    cargarMesas();
                    abrirGestionMesa(mesaParaProcesar); // Ahora funciona sin errores
                });
            }
        });
    }

    // --- NAVEGACIÓN ---

    private void abrirGestionMesa(Mesa mesa) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).onComandoAbrirMesa(mesa.idMesa, mesa.tipoJuego);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        executorService.shutdown();
    }

    @Override
    public void onResume() {
        super.onResume();
        // 🔥 FORZAR ESTADO AL REGRESAR:
        // Si isExpanded es true, ocultamos el 30% inmediatamente al volver.
        if (requireActivity() instanceof MainActivity) {
            ((MainActivity) requireActivity()).setExpandirContenedor(isExpanded);
        }
    }
}