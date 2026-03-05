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
    private boolean isExpanded = false; // Control de pantalla completa

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
                ((MainActivity) requireActivity()).setExpandirContenedor(isExpanded); // Ajustado al método que definimos antes
                btnExpandir.setText(isExpanded ? "CONTRAER" : "FULL HUD");
            }
        });

        // 2. 🔥 NUEVA LÓGICA: Agregar mesas dinámicamente al local
        btnHabilitarDirecto.setOnClickListener(v -> {
            agregarNuevaMesaAlLocal();
        });

        // 3. Disparamos la carga inicial
        cargarMesas();
    }

    // --- LÓGICA DE CARGA Y CREACIÓN AUTOMÁTICA ---

    private void cargarMesas() {
        executorService.execute(() -> {
            List<Mesa> mesas = AppDatabase.getInstance(requireContext()).mesaDao().obtenerTodasLasMesas();

            // 🔥 ELIMINAMOS LA AUTO-CREACIÓN AQUÍ.
            // Si la lista está vacía, simplemente mostrará el RecyclerView vacío
            // hasta que el usuario presione "NUEVA MESA".

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    MesaAdapter adapter = new MesaAdapter(mesas, mesa -> {
                        if (!"ABIERTO".equalsIgnoreCase(mesa.estado)) {
                            mostrarDialogoHabilitarMesa(mesa);
                        } else {
                            abrirGestionMesa(mesa);
                        }
                    });

                    adapter.setOnMesaLongClickListener(this::validarCierreMesa);
                    rvMesas.setAdapter(adapter);

                    // Carga de saldos en tiempo real para mesas abiertas
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

    // --- NUEVO: MÉTODO PARA CREAR UNA MESA EXTRA (Ej: Mesa 7, Mesa 8...) ---
    private void agregarNuevaMesaAlLocal() {
        new MaterialAlertDialogBuilder(requireContext(), R.style.MaterialAlertDialog_Rounded)
                .setTitle("🛠 Instalar Nueva Mesa")
                .setMessage("¿Deseas agregar una nueva mesa física al panel de control?")
                .setPositiveButton("AGREGAR", (dialog, which) -> {
                    executorService.execute(() -> {
                        List<Mesa> mesasActuales = AppDatabase.getInstance(requireContext()).mesaDao().obtenerTodasLasMesas();

                        // Calculamos cuál es el ID más alto actualmente para sumar 1
                        int maxId = 0;
                        for (Mesa m : mesasActuales) {
                            if (m.idMesa > maxId) maxId = m.idMesa;
                        }

                        int nuevoId = maxId + 1;

                        Mesa nuevaMesa = new Mesa();
                        nuevaMesa.idMesa = nuevoId;
                        nuevaMesa.estado = "CERRADA";
                        nuevaMesa.tipoJuego = "POOL";

                        AppDatabase.getInstance(requireContext()).mesaDao().insertarMesa(nuevaMesa);

                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                Toast.makeText(getContext(), "Mesa #" + nuevoId + " instalada con éxito", Toast.LENGTH_SHORT).show();
                                cargarMesas(); // Refresca la grilla al instante
                            });
                        }
                    });
                })
                .setNegativeButton("CANCELAR", null)
                .show();
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

    // --- LÓGICA DE HABILITACIÓN ---

    private void mostrarDialogoHabilitarMesa(Mesa mesa) {
        String[] opciones = {"🎱 POOL (Bola 8/9)", "⏱ 3 BANDAS (Tiempo)"};
        new MaterialAlertDialogBuilder(requireContext(), R.style.MaterialAlertDialog_Rounded)
                .setTitle("DESPLEGAR MESA " + mesa.idMesa)
                .setItems(opciones, (dialog, which) -> {
                    procederAAbrirMesaManual(mesa.idMesa, (which == 0) ? "POOL" : "3BANDAS");
                }).show();
    }

    private void procederAAbrirMesaManual(int idMesa, String modo) {
        executorService.execute(() -> {
            Mesa mesaExistente = AppDatabase.getInstance(requireContext()).mesaDao().obtenerMesaPorId(idMesa);

            Mesa mesaParaProcesar;
            if (mesaExistente == null) {
                mesaParaProcesar = new Mesa();
                mesaParaProcesar.idMesa = idMesa;
            } else {
                mesaParaProcesar = mesaExistente;
            }

            mesaParaProcesar.estado = "ABIERTO";
            mesaParaProcesar.tipoJuego = modo;
            mesaParaProcesar.fechaApertura = System.currentTimeMillis();

            AppDatabase.getInstance(requireContext()).mesaDao().insertarMesa(mesaParaProcesar);

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), "Mesa #" + idMesa + " en línea (" + modo + ")", Toast.LENGTH_SHORT).show();
                    cargarMesas();
                    abrirGestionMesa(mesaParaProcesar);
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
        if (requireActivity() instanceof MainActivity) {
            ((MainActivity) requireActivity()).setExpandirContenedor(isExpanded);
        }
    }
}