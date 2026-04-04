package com.nodo.tpv.ui.fragments;

import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.gson.Gson;
import com.nodo.tpv.R;
import com.nodo.tpv.adapters.MesaAdapter;
import com.nodo.tpv.data.api.RetrofitClient;
import com.nodo.tpv.data.database.AppDatabase;
import com.nodo.tpv.data.dto.MesaDTO;
import com.nodo.tpv.data.entities.ActividadOperativaLocal;
import com.nodo.tpv.data.entities.Mesa;
import com.nodo.tpv.ui.main.MainActivity;
import com.nodo.tpv.util.SessionManager;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

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

        rvMesas = view.findViewById(R.id.rvMesasDashboard);
        MaterialButton btnExpandir = view.findViewById(R.id.btnExpandirDashboard);
        MaterialButton btnHabilitarDirecto = view.findViewById(R.id.btnHabilitarMesaDirecto);

        rvMesas.setLayoutManager(new GridLayoutManager(getContext(), 4));

        btnExpandir.setOnClickListener(v -> {
            isExpanded = !isExpanded;
            if (requireActivity() instanceof MainActivity) {
                ((MainActivity) requireActivity()).setExpandirContenedor(isExpanded);
                btnExpandir.setText(isExpanded ? "CONTRAER" : "FULL HUD");
            }
        });

        btnHabilitarDirecto.setOnClickListener(v -> {
            agregarNuevaMesaAlLocal();
        });

        // Sincronizar el estado del local con el servidor al abrir el fragmento
        sincronizarMesasDesdeBackend();
        cargarMesas();
    }

    // =====================================================================================
    // OBTENER ESTADO DEL SERVIDOR (RECUPERACIÓN / SINCRONIZACIÓN)
    // =====================================================================================

    private void sincronizarMesasDesdeBackend() {
        // Instanciamos el SessionManager de forma segura
        long empresaId = new SessionManager(requireContext()).getEmpresaId();

        RetrofitClient.getInterface(requireContext()).obtenerEstadoMesas(empresaId).enqueue(new Callback<List<MesaDTO>>() {
            @Override
            public void onResponse(@NonNull Call<List<MesaDTO>> call, @NonNull Response<List<MesaDTO>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    actualizarBaseDatosLocal(response.body());
                }
            }

            @Override
            public void onFailure(@NonNull Call<List<MesaDTO>> call, @NonNull Throwable t) {
                Log.e("API_ERROR", "No se pudo conectar con MesaController: " + t.getMessage());
            }
        });
    }

    private void actualizarBaseDatosLocal(List<MesaDTO> mesasBackend) {
        executorService.execute(() -> {
            for (MesaDTO dto : mesasBackend) {
                Mesa local = AppDatabase.getInstance(requireContext()).mesaDao().obtenerMesaPorId(dto.idMesaLocal);

                if (local == null) {
                    local = new Mesa();
                    local.idMesa = dto.idMesaLocal;
                }

                local.estado = dto.estado;
                // Si el backend nos dice que está DISPONIBLE, la limpiamos localmente
                if ("DISPONIBLE".equals(dto.estado)) {
                    local.tipoJuego = null;
                    local.tarifaTiempo = null;
                } else {
                    local.tipoJuego = dto.tipoJuego;
                    local.tarifaTiempo = dto.tarifaTiempo;
                    local.reglaDuelo = dto.reglaDuelo;
                    local.fechaApertura = (dto.fechaApertura != null) ? dto.fechaApertura : 0L;
                }

                AppDatabase.getInstance(requireContext()).mesaDao().insertarMesa(local);
            }

            if (getActivity() != null) {
                getActivity().runOnUiThread(this::cargarMesas);
            }
        });
    }

    // =====================================================================================
    // LÓGICA DE CARGA Y OPERACIONES LOCALES
    // =====================================================================================

    private void cargarMesas() {
        executorService.execute(() -> {
            List<Mesa> mesas = AppDatabase.getInstance(requireContext()).mesaDao().obtenerTodasLasMesas();

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

    private void agregarNuevaMesaAlLocal() {
        new MaterialAlertDialogBuilder(requireContext(), R.style.MaterialAlertDialog_Rounded)
                .setTitle("🛠 Instalar Nueva Mesa")
                .setMessage("¿Deseas agregar una nueva mesa física al panel de control?")
                .setPositiveButton("AGREGAR", (dialog, which) -> {
                    executorService.execute(() -> {
                        List<Mesa> mesasActuales = AppDatabase.getInstance(requireContext()).mesaDao().obtenerTodasLasMesas();

                        int maxId = 0;
                        for (Mesa m : mesasActuales) {
                            if (m.idMesa > maxId) maxId = m.idMesa;
                        }
                        int nuevoId = maxId + 1;

                        Mesa nuevaMesa = new Mesa();
                        nuevaMesa.idMesa = nuevoId;
                        nuevaMesa.estado = "DISPONIBLE";

                        AppDatabase.getInstance(requireContext()).mesaDao().insertarMesa(nuevaMesa);

                        // 🔥 EVENTO BACKEND: Mesa Creada
                        Map<String, Object> payload = new HashMap<>();
                        payload.put("idMesa", nuevoId);

                        // EXTRAEMOS EL ID DEL MESERO LOGUEADO
                        com.nodo.tpv.data.entities.Usuario usuarioLogueado = new SessionManager(requireContext()).obtenerUsuario();
                        if (usuarioLogueado != null) {
                            payload.put("idUsuarioSlot", usuarioLogueado.idUsuario);
                        }

                        registrarEventoOperativo("MESA_CREADA", new Gson().toJson(payload));

                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                Toast.makeText(getContext(), "Mesa #" + nuevoId + " instalada con éxito", Toast.LENGTH_SHORT).show();
                                cargarMesas();
                            });
                        }
                    });
                })
                .setNegativeButton("CANCELAR", null)
                .show();
    }

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

            Mesa mesaParaProcesar = (mesaExistente == null) ? new Mesa() : mesaExistente;
            mesaParaProcesar.idMesa = idMesa;
            mesaParaProcesar.estado = "ABIERTO";
            mesaParaProcesar.tipoJuego = modo;
            // Aún no le ponemos fechaApertura porque el juego no ha iniciado

            AppDatabase.getInstance(requireContext()).mesaDao().insertarMesa(mesaParaProcesar);

            // 🔥 EVENTO BACKEND: Avisamos que la mesa se preparó y elegimos el tipo de juego
            Map<String, Object> payload = new HashMap<>();
            payload.put("idMesa", idMesa);
            payload.put("tipoJuego", modo);

            // Extraemos al mesero
            com.nodo.tpv.data.entities.Usuario usuarioLogueado = new SessionManager(requireContext()).obtenerUsuario();
            if (usuarioLogueado != null) {
                payload.put("idUsuarioSlot", usuarioLogueado.idUsuario);
            }

            registrarEventoOperativo("MESA_ABIERTA", new Gson().toJson(payload));

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), "Mesa #" + idMesa + " preparada (" + modo + ")", Toast.LENGTH_SHORT).show();
                    cargarMesas();
                    abrirGestionMesa(mesaParaProcesar);
                });
            }
        });
    }

    private void abrirGestionMesa(Mesa mesa) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).onComandoAbrirMesa(mesa.idMesa, mesa.tipoJuego);
        }
    }

    // =====================================================================================
    // LÓGICA DE CIERRE Y LIBERACIÓN DE MESA
    // =====================================================================================

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
            mesa.estado = "DISPONIBLE"; // En paridad con el backend
            mesa.fechaCierre = System.currentTimeMillis();
            mesa.tipoJuego = null; // Limpiamos para el próximo turno

            AppDatabase.getInstance(requireContext()).mesaDao().actualizarMesa(mesa);

            // 🔥 EVENTO BACKEND: Mesa Cerrada (Libera la mesa y cierra el duelo en la BD central)
            Map<String, Object> payload = new HashMap<>();
            payload.put("idMesa", mesa.idMesa);
            registrarEventoOperativo("MESA_CERRADA", new Gson().toJson(payload));

            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    Toast.makeText(getContext(), "Mesa liberada", Toast.LENGTH_SHORT).show();
                    cargarMesas();
                });
            }
        });
    }

    // =====================================================================================
    // MOTOR DE SINCRONIZACIÓN (ACTIVIDADES)
    // =====================================================================================

    private void registrarEventoOperativo(String tipo, String json) {
        try {
            ActividadOperativaLocal evento = new ActividadOperativaLocal();
            evento.eventoId = UUID.randomUUID().toString();
            evento.tipoEvento = tipo;
            evento.fechaDispositivo = System.currentTimeMillis();
            evento.estadoSync = "PENDIENTE";
            evento.detallesJson = json;

            AppDatabase.getInstance(requireContext()).actividadOperativaLocalDao().insertar(evento);
            dispararSincronizacion();
        } catch (Exception e) {
            Log.e("SYNC_MESA", "Error registrando evento " + tipo, e);
        }
    }

    private void dispararSincronizacion() {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        OneTimeWorkRequest syncRequest = new OneTimeWorkRequest.Builder(com.nodo.tpv.data.sync.OperatividadSyncWorker.class)
                .setConstraints(constraints)
                .build();

        WorkManager.getInstance(requireContext())
                .enqueueUniqueWork("SyncOperatividadInmediata", ExistingWorkPolicy.KEEP, syncRequest);
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