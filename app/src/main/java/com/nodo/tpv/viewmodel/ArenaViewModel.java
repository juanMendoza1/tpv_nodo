package com.nodo.tpv.viewmodel;

import android.app.Application;
import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.nodo.tpv.data.database.AppDatabase;
import com.nodo.tpv.data.dto.DetalleHistorialDuelo;
import com.nodo.tpv.data.dto.LogAgrupadoDTO;
import com.nodo.tpv.data.entities.BolaAnotada;
import com.nodo.tpv.data.entities.Cliente;
import com.nodo.tpv.data.entities.DetalleDueloTemporalInd;
import com.nodo.tpv.data.entities.DetallePedido;
import com.nodo.tpv.data.entities.DueloTemporal;
import com.nodo.tpv.data.entities.DueloTemporalInd;
import com.nodo.tpv.data.entities.Mesa;
import com.nodo.tpv.data.entities.PerfilDueloInd;
import com.nodo.tpv.data.entities.Producto;
import com.nodo.tpv.data.repository.DueloRepository;
import com.nodo.tpv.ui.arena.managers.ArenaVARManager;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ArenaViewModel extends AndroidViewModel {

    private final AppDatabase db;
    private final DueloRepository dueloRepository;
    private final ExecutorService executorService = Executors.newFixedThreadPool(4);
    private final Handler mainThreadHandler = new Handler(Looper.getMainLooper());

    private final MutableLiveData<Long> dbTrigger = new MutableLiveData<>(System.currentTimeMillis());
    private final MutableLiveData<Boolean> _eventoVentaExitosa = new MutableLiveData<>(false);

    private ArenaVARManager varManager;
    private final Context context;

    // =========================================================================================
    // 1. ESTADOS GLOBALES DE LA ARENA
    // =========================================================================================
    private final MutableLiveData<String> tipoJuegoActual = new MutableLiveData<>("POOL");
    private final MutableLiveData<Boolean> enModoDuelo = new MutableLiveData<>(false);
    private final MutableLiveData<Long> tiempoInicioDuelo = new MutableLiveData<>(0L);

    private String uuidDueloActual = null;
    private int idMesaActual;
    private List<Cliente> todosLosParticipantesDuelo = new ArrayList<>();

    private final List<com.nodo.tpv.data.entities.BolaAnotada> historialBolasDueloActual = new java.util.ArrayList<>();

    // =========================================================================================
    // 2. ESTADOS ARENA POOL (MULTIEQUIPO)
    // =========================================================================================
    private final MutableLiveData<Integer> scoreAzul = new MutableLiveData<>(0);

    private final MutableLiveData<Boolean> procesandoPunto = new MutableLiveData<>(false);
    public LiveData<Boolean> getProcesandoPunto() { return procesandoPunto; }
    private final MutableLiveData<Integer> scoreRojo = new MutableLiveData<>(0);
    private final MutableLiveData<Map<Integer, Integer>> mapaColoresDuelo = new MutableLiveData<>(new HashMap<>());
    private final MutableLiveData<Map<Integer, Integer>> scoresEquipos = new MutableLiveData<>(new HashMap<>());
    private final MutableLiveData<String> reglaCobroActiva = new MutableLiveData<>("GANADOR_SALVA");

    private List<Cliente> integrantesAzulCacheados = new ArrayList<>();
    private List<Cliente> integrantesRojoCacheados = new ArrayList<>();

    // =========================================================================================
    // 3. ESTADOS ARENA INDIVIDUAL (3 BANDAS)
    // =========================================================================================
    private final MutableLiveData<Map<Integer, Integer>> scoresIndividualesInd = new MutableLiveData<>(new HashMap<>());
    private final MutableLiveData<String> reglaPagoInd = new MutableLiveData<>("PERDEDORES");
    private final MutableLiveData<Integer> metaCarambolasInd = new MutableLiveData<>(15);
    private final MutableLiveData<List<Producto>> listaApuesta = new MutableLiveData<>(new ArrayList<>());

    public LiveData<List<BolaAnotada>> observarBolasDuelo(String uuidActual) {
        return db.bolaDueloDao().observarBolasDuelo(uuidActual);
    }


    public ArenaViewModel(@NonNull Application application) {
        super(application);
        db = AppDatabase.getInstance(application);
        dueloRepository = new DueloRepository(application);
        this.context = application.getApplicationContext();
        varManager = new ArenaVARManager(db, this.context);
    }

    // --- GETTERS GLOBALES ---
    public LiveData<Long> getDbTrigger() { return dbTrigger; }
    public LiveData<Boolean> getEventoVentaExitosa() { return _eventoVentaExitosa; }
    public void resetEventoVenta() { _eventoVentaExitosa.setValue(false); }
    public LiveData<String> getTipoJuegoActual() { return tipoJuegoActual; }
    public LiveData<Boolean> getEnModoDuelo() { return enModoDuelo; }
    public LiveData<Long> getTiempoInicioDuelo() { return tiempoInicioDuelo; }
    public String getUuidDueloActual() { return uuidDueloActual; }

    public List<Cliente> getTodosLosParticipantesDuelo() { return todosLosParticipantesDuelo; }
    public void setTipoJuego(String tipo) { tipoJuegoActual.postValue(tipo); }

    private final List<com.nodo.tpv.data.entities.BolaAnotada> historialBolasDelDueloCompleto = new ArrayList<>();

    // =========================================================================================
    // RECUPERACIÓN Y GESTIÓN DE LA MESA
    // =========================================================================================

    public void recuperarDueloActivo(int idMesa) {
        this.idMesaActual = idMesa;
        executorService.execute(() -> {
            String uuid = db.dueloDao().obtenerUuidDueloActivoPorMesa(idMesa);
            if (uuid != null) {
                this.uuidDueloActual = uuid;
                this.todosLosParticipantesDuelo = db.dueloDao().obtenerTodosLosParticipantesDueloPorMesa(idMesa);
                List<DueloTemporal> participantes = db.dueloDao().obtenerParticipantesSincronoPorMesa(idMesa);
                Map<Integer, Integer> mapaRecuperado = new HashMap<>();

                for (DueloTemporal dt : participantes) {
                    mapaRecuperado.put(dt.idCliente, dt.idEquipo);
                }

                mainThreadHandler.post(() -> {
                    this.mapaColoresDuelo.setValue(mapaRecuperado);
                    this.enModoDuelo.setValue(true);
                    executorService.execute(() -> {
                        String reglaDB = db.dueloDao().obtenerReglaCobroDuelo(uuidDueloActual);
                        if (reglaDB != null) reglaCobroActiva.postValue(reglaDB);
                    });
                    this.dbTrigger.setValue(System.currentTimeMillis());
                });
            } else {
                mainThreadHandler.post(() -> {
                    this.uuidDueloActual = null;
                    this.mapaColoresDuelo.setValue(new HashMap<>());
                    this.enModoDuelo.setValue(false);
                });
            }
        });
    }

    public void finalizarDueloCompleto(int idMesa, String tipoJuego) {
        final String uuidFinal = uuidDueloActual;
        final Map<Integer, Integer> scoreFinal = "POOL".equals(tipoJuego) ?
                new java.util.HashMap<>(scoresEquipos.getValue()) : new java.util.HashMap<>(scoresIndividualesInd.getValue());
        final Map<Integer, Integer> mapaColores = new java.util.HashMap<>(mapaColoresDuelo.getValue());
        final List<com.nodo.tpv.data.entities.BolaAnotada> copiaHistorial = new java.util.ArrayList<>(historialBolasDueloActual);

        if (uuidFinal == null) return;

        executorService.execute(() -> {
            try {
                com.nodo.tpv.data.dto.ReporteEstadisticoDueloDTO reporte = new com.nodo.tpv.data.dto.ReporteEstadisticoDueloDTO();
                reporte.idMesa = idMesa;
                reporte.uuidDuelo = uuidFinal;
                reporte.tipoJuego = tipoJuego;
                reporte.timestampFin = System.currentTimeMillis();

                java.util.Map<String, com.nodo.tpv.data.dto.ReporteEstadisticoDueloDTO.DetalleEquipo> detallesMap = new java.util.HashMap<>();
                java.util.Map<String, Integer> resumenMap = new java.util.HashMap<>();

                for (Integer colorId : new java.util.HashSet<>(mapaColores.values())) {
                    String nombreE = obtenerNombreColor(colorId);

                    com.nodo.tpv.data.dto.ReporteEstadisticoDueloDTO.DetalleEquipo ds = new com.nodo.tpv.data.dto.ReporteEstadisticoDueloDTO.DetalleEquipo();
                    ds.puntosTotales = scoreFinal.getOrDefault(colorId, 0);
                    ds.bolasAnotadas = new java.util.ArrayList<>();
                    ds.puntosPositivos = 0;
                    ds.cantidadMalas = 0;

                    for (com.nodo.tpv.data.entities.BolaAnotada b : copiaHistorial) {
                        if (b.colorEquipo == colorId) {
                            if (b.numeroBola < 0) {
                                ds.cantidadMalas++;
                            } else {
                                ds.puntosPositivos += b.numeroBola;
                                ds.bolasAnotadas.add(b.numeroBola);
                            }
                        }
                    }
                    detallesMap.put(nombreE, ds);
                    resumenMap.put(nombreE, ds.puntosTotales);
                }

                reporte.detalleEstadistico = detallesMap;
                reporte.resumenGeneral = resumenMap;

                String jsonReporte = new com.google.gson.Gson().toJson(reporte);
                registrarEventoOperativo("DUELO_FINALIZADO_ESTADISTICO", jsonReporte);

            } catch (Exception e) {
                android.util.Log.e("ARENA_FIN", "Error al generar el resumen final", e);
            }

            historialBolasDueloActual.clear();
            realizarLimpiezaLocal(idMesa, tipoJuego);
        });
    }

    private String obtenerNombreColor(int color) {
        if (color == -16718337) return "AZUL";
        if (color == -3916) return "ROJO";
        if (color == -10929) return "AMARILLO";
        if (color == -11751600) return "VERDE";
        return "EQUIPO_" + color;
    }

    private void realizarLimpiezaLocal(int idMesa, String tipoJuego) {
        Runnable limpiezaCallback = () -> mainThreadHandler.post(() -> {
            enModoDuelo.setValue(false);
            uuidDueloActual = null;
            tiempoInicioDuelo.setValue(0L);
            mapaColoresDuelo.setValue(new HashMap<>());
            scoresEquipos.setValue(new HashMap<>());
            scoreAzul.setValue(0);
            scoreRojo.setValue(0);
            scoresIndividualesInd.setValue(new HashMap<>());
            limpiarApuesta();
            dbTrigger.setValue(System.currentTimeMillis());
        });

        if ("POOL".equals(tipoJuego)) {
            dueloRepository.finalizarDueloPool(idMesa, limpiezaCallback);
        } else {
            dueloRepository.finalizarDueloIndividual(idMesa, limpiezaCallback);
        }
    }

    // =========================================================================================
    // LÓGICA ARENA POOL (MULTIEQUIPO)
    // =========================================================================================

    public LiveData<Map<Integer, Integer>> getMapaColoresDuelo() { return mapaColoresDuelo; }
    public LiveData<Map<Integer, Integer>> getScoresEquipos() { return scoresEquipos; }
    public LiveData<String> getReglaCobroActiva() { return reglaCobroActiva; }
    public LiveData<Integer> getScoreAzul() { return scoreAzul; }
    public LiveData<Integer> getScoreRojo() { return scoreRojo; }

    public void prepararDueloPoolMultiequipo(Map<Integer, Integer> seleccion, int idMesa) {
        final Map<Integer, Integer> copiaSeleccion = new HashMap<>(seleccion);
        this.enModoDuelo.postValue(true);
        this.mapaColoresDuelo.postValue(copiaSeleccion);
        this.uuidDueloActual = UUID.randomUUID().toString();
        this.tiempoInicioDuelo.postValue(System.currentTimeMillis());

        dueloRepository.prepararDueloPoolMultiequipo(uuidDueloActual, copiaSeleccion, idMesa, () -> {
            mainThreadHandler.post(() -> dbTrigger.setValue(System.currentTimeMillis()));
        });

        // 🔥 EVENTO BACKEND: Nace el Duelo de Pool (MESA_ABIERTA)
        executorService.execute(() -> {
            try {
                String regla = reglaCobroActiva.getValue() != null ? reglaCobroActiva.getValue() : "GANADOR_SALVA";

                Map<String, Object> payload = new HashMap<>();
                payload.put("idMesa", idMesa);
                payload.put("uuidDuelo", uuidDueloActual); // 🔥 CONECTA CON LA ENTIDAD DUELO
                payload.put("tipoJuego", "POOL");
                payload.put("reglaDuelo", regla);

                // Rescatamos la tarifa configurada
                Mesa mesaLocal = db.mesaDao().obtenerMesaPorId(idMesa);
                payload.put("tarifaTiempo", mesaLocal != null && mesaLocal.tarifaTiempo != null ? mesaLocal.tarifaTiempo : BigDecimal.ZERO);

                // Vinculamos el mesero (Usuario Slot)
                com.nodo.tpv.data.entities.Usuario usuario = new com.nodo.tpv.util.SessionManager(getApplication().getApplicationContext()).obtenerUsuario();
                if (usuario != null) {
                    payload.put("idUsuarioSlot", usuario.idUsuario);
                }

                registrarEventoOperativo("DUELO_INICIADO", new com.google.gson.Gson().toJson(payload));
            } catch (Exception e) {
                Log.e("ARENA_VIEWMODEL", "Error registrando MESA_ABIERTA Pool", e);
            }
        });
    }

    public void aplicarDanioMultiequipo(int colorEquipoGanador) {
        final Map<Integer, Integer> participantesMapa = (mapaColoresDuelo.getValue() != null) ? new HashMap<>(mapaColoresDuelo.getValue()) : null;
        final String uuid = uuidDueloActual;
        if (participantesMapa == null || uuid == null) return;

        procesandoPunto.postValue(true);

        executorService.execute(() -> {
            List<DetallePedido> bolsa = db.detallePedidoDao().obtenerDetallesMunicionSincrono(uuid);

            if (bolsa != null && !bolsa.isEmpty()) {
                List<Integer> afectados = new ArrayList<>();
                String regla = db.dueloDao().obtenerReglaCobroDuelo(uuid);

                if ("TODOS_PAGAN".equals(regla)) {
                    afectados.addAll(participantesMapa.keySet());
                } else {
                    for (Map.Entry<Integer, Integer> entry : participantesMapa.entrySet()) {
                        if (entry.getValue() != colorEquipoGanador) afectados.add(entry.getKey());
                    }
                }

                if (!afectados.isEmpty()) {
                    BigDecimal divisor = new BigDecimal(afectados.size());
                    String marcadorRelativo = obtenerMarcadorActualString();

                    for (DetallePedido dp : bolsa) {
                        BigDecimal precioReparto = dp.precioEnVenta.divide(divisor, 2, RoundingMode.HALF_UP);
                        for (Integer idCli : afectados) {
                            DetallePedido nuevo = new DetallePedido();
                            nuevo.idCliente = idCli;
                            nuevo.idProducto = dp.idProducto;
                            nuevo.idMesa = dp.idMesa;
                            nuevo.idDueloOrigen = uuid;
                            nuevo.cantidad = 1;
                            nuevo.precioEnVenta = precioReparto;
                            nuevo.esApuesta = true;
                            nuevo.estado = "REGISTRADO";
                            nuevo.marcadorAlMomento = marcadorRelativo;
                            nuevo.fechaLong = System.currentTimeMillis();
                            db.detallePedidoDao().insertarDetalle(nuevo);
                        }
                        db.detallePedidoDao().borrarDetallePorId(dp.idDetalle);
                    }
                }
            }

            mainThreadHandler.post(() -> {
                actualizarPuntajeEquipoDinamico(colorEquipoGanador);
                dbTrigger.setValue(System.currentTimeMillis());
                _eventoVentaExitosa.setValue(true);
                procesandoPunto.postValue(false);
            });
        });
    }

    public void aplicarDanioUltimoPaga(int colorGanador, int colorPerdedor) {
        final String uuid = uuidDueloActual;
        final Map<Integer, Integer> participantesMapa = (mapaColoresDuelo.getValue() != null) ? new HashMap<>(mapaColoresDuelo.getValue()) : null;
        if (participantesMapa == null || uuid == null) return;

        procesandoPunto.postValue(true);

        executorService.execute(() -> {
            List<DetallePedido> bolsa = db.detallePedidoDao().obtenerDetallesMunicionSincrono(uuid);

            if (bolsa != null && !bolsa.isEmpty()) {
                List<Integer> idsAfectados = new ArrayList<>();
                for (Map.Entry<Integer, Integer> entry : participantesMapa.entrySet()) {
                    if (entry.getValue() == colorPerdedor) idsAfectados.add(entry.getKey());
                }

                if (!idsAfectados.isEmpty()) {
                    BigDecimal divisor = new BigDecimal(idsAfectados.size());
                    String marcadorRelativo = obtenerMarcadorActualString();

                    for (DetallePedido dp : bolsa) {
                        BigDecimal precioIndividual = dp.precioEnVenta.divide(divisor, 2, RoundingMode.HALF_UP);
                        for (Integer idClientePerdedor : idsAfectados) {
                            DetallePedido nuevoCobro = crearDetalleDeuda(idClientePerdedor, dp, precioIndividual, uuid, marcadorRelativo);
                            db.detallePedidoDao().insertarDetalle(nuevoCobro);
                        }
                        db.detallePedidoDao().borrarDetallePorId(dp.idDetalle);
                    }
                }
            }

            mainThreadHandler.post(() -> {
                actualizarPuntajeEquipoDinamico(colorGanador);
                dbTrigger.setValue(System.currentTimeMillis());
                procesandoPunto.postValue(false);
            });
        });
    }

    private void actualizarPuntajeEquipoDinamico(int color) {
        Map<Integer, Integer> scores = scoresEquipos.getValue();
        Map<Integer, Integer> copiaScores = (scores == null) ? new HashMap<>() : new HashMap<>(scores);
        int actual = copiaScores.containsKey(color) ? copiaScores.get(color) : 0;
        copiaScores.put(color, actual + 1);
        scoresEquipos.postValue(copiaScores);
    }

    public String obtenerMarcadorActualString() {
        Map<Integer, Integer> scores = scoresEquipos.getValue();
        Map<Integer, Integer> coloresParticipantes = mapaColoresDuelo.getValue();
        if (scores == null || coloresParticipantes == null) return "0-0";

        StringBuilder sb = new StringBuilder();
        List<Integer> coloresActivos = new ArrayList<>();
        for (Integer color : coloresParticipantes.values()) {
            if (!coloresActivos.contains(color)) coloresActivos.add(color);
        }

        for (int i = 0; i < coloresActivos.size(); i++) {
            int color = coloresActivos.get(i);
            int puntos = scores.containsKey(color) ? scores.get(color) : 0;
            sb.append(getNombreColor(color)).append(": ").append(puntos);
            if (i < coloresActivos.size() - 1) sb.append(" | ");
        }
        return sb.toString();
    }

    private String getNombreColor(int color) {
        if (color == Color.parseColor("#00E5FF")) return "AZUL";
        if (color == Color.parseColor("#FF1744")) return "ROJO";
        if (color == Color.parseColor("#FFD54F")) return "AMAR";
        if (color == Color.parseColor("#4CAF50")) return "VERDE";
        if (color == Color.parseColor("#AA00FF")) return "MORAD";
        return "EQ";
    }

    // =========================================================================================
    // LÓGICA ARENA INDIVIDUAL (3 BANDAS)
    // =========================================================================================

    public LiveData<Map<Integer, Integer>> getScoresIndividualesInd() { return scoresIndividualesInd; }
    public LiveData<String> getReglaPagoInd() { return reglaPagoInd; }
    public LiveData<Integer> getMetaCarambolasInd() { return metaCarambolasInd; }
    public List<Cliente> getIntegrantesAzulCacheados() { return integrantesAzulCacheados; }

    public void setReglaPagoInd(String regla) {
        reglaPagoInd.postValue(regla);
        executorService.execute(() -> {
            if (uuidDueloActual != null) db.dueloTemporalIndDao().actualizarReglaPagoMesa(0, regla);
        });
    }

    public void setMetaCarambolasInd(int meta) { metaCarambolasInd.postValue(meta); }
    public void setScoresIndividualesManual(Map<Integer, Integer> nuevosScores) { this.scoresIndividualesInd.setValue(nuevosScores); }

    public void iniciarDueloIndPersistente(List<Integer> idsClientes, int idMesa) {
        this.idMesaActual = idMesa;
        this.tipoJuegoActual.postValue("3BANDAS");
        this.enModoDuelo.postValue(true);

        executorService.execute(() -> {
            List<Cliente> clientesReales = db.clienteDao().obtenerClientesPorIds(idsClientes);
            String uuidExistente = db.dueloDao().obtenerUuidDueloActivoIndPorMesa(idMesa);

            if (uuidExistente != null) this.uuidDueloActual = uuidExistente;
            else this.uuidDueloActual = UUID.randomUUID().toString();

            for (Cliente c : clientesReales) {
                DueloTemporalInd existente = db.dueloTemporalIndDao().obtenerEstadoCliente(idMesa, c.idCliente);
                if (existente == null) {
                    DueloTemporalInd nuevo = new DueloTemporalInd(this.uuidDueloActual, idMesa, c.idCliente, 20, "PERDEDORES");
                    nuevo.timestampInicio = System.currentTimeMillis();
                    db.dueloTemporalIndDao().insertarOActualizar(nuevo);
                }
            }

            mainThreadHandler.post(() -> {
                this.integrantesAzulCacheados = new ArrayList<>(clientesReales);
                this.dbTrigger.setValue(System.currentTimeMillis());
            });

            // 🔥 EVENTO BACKEND: Nace el Duelo de 3 Bandas (MESA_ABIERTA) - Solo si es nuevo
            if (uuidExistente == null) {
                try {
                    String regla = reglaPagoInd.getValue() != null ? reglaPagoInd.getValue() : "PERDEDORES";

                    Map<String, Object> payload = new HashMap<>();
                    payload.put("idMesa", idMesa);
                    payload.put("uuidDuelo", this.uuidDueloActual); // 🔥 CONECTA CON LA ENTIDAD DUELO
                    payload.put("tipoJuego", "3BANDAS");
                    payload.put("reglaDuelo", regla);

                    Mesa mesaLocal = db.mesaDao().obtenerMesaPorId(idMesa);
                    payload.put("tarifaTiempo", mesaLocal != null && mesaLocal.tarifaTiempo != null ? mesaLocal.tarifaTiempo : BigDecimal.ZERO);

                    com.nodo.tpv.data.entities.Usuario usuario = new com.nodo.tpv.util.SessionManager(getApplication().getApplicationContext()).obtenerUsuario();
                    if (usuario != null) {
                        payload.put("idUsuarioSlot", usuario.idUsuario);
                    }

                    registrarEventoOperativo("DUELO_INICIADO", new com.google.gson.Gson().toJson(payload));
                } catch (Exception e) {
                    Log.e("ARENA_VIEWMODEL", "Error registrando MESA_ABIERTA Ind", e);
                }
            }
        });
    }

    public void aplicarDanioInd(int idClienteAnotador, List<Cliente> todosLosParticipantes, Map<Integer, Integer> miniMarcadoresSnapshot) {
        if (uuidDueloActual == null) return;
        final Map<Integer, Integer> copiaMinis = new HashMap<>(miniMarcadoresSnapshot);

        executorService.execute(() -> {
            DueloTemporalInd cabecera = db.dueloTemporalIndDao().obtenerDueloPorMesaYCliente(idMesaActual, idClienteAnotador);
            if (cabecera == null) return;
            int nuevoScoreGlobal = cabecera.score + 1;

            db.runInTransaction(() -> {
                db.dueloTemporalIndDao().actualizarScore(idMesaActual, idClienteAnotador, nuevoScoreGlobal);

                StringBuilder sbMini = new StringBuilder();
                for (int i = 0; i < todosLosParticipantes.size(); i++) {
                    Cliente c = todosLosParticipantes.get(i);
                    int valorCarambolas = copiaMinis.getOrDefault(c.idCliente, 0);
                    sbMini.append(c.alias).append(": ").append(valorCarambolas);
                    if (i < todosLosParticipantes.size() - 1) sbMini.append(" | ");
                }

                DetalleDueloTemporalInd hito = new DetalleDueloTemporalInd(
                        uuidDueloActual, idMesaActual, idClienteAnotador, obtenerAliasCliente(idClienteAnotador),
                        nuevoScoreGlobal, sbMini.toString(), obtenerMarcadorActualString()
                );
                db.detalleDueloTemporalIndDao().insertarHito(hito);

                List<DetallePedido> bolsa = db.detallePedidoDao().obtenerMunicionBolsaParaReparto(uuidDueloActual);
                if (bolsa != null && !bolsa.isEmpty()) {
                    List<Integer> victimas = identificarIdsVictimasInd(idClienteAnotador, todosLosParticipantes);
                    BigDecimal divisor = new BigDecimal(victimas.size());

                    for (DetallePedido item : bolsa) {
                        BigDecimal precioRepartido = item.precioEnVenta.divide(divisor, 0, RoundingMode.HALF_UP);
                        for (Integer idV : victimas) {
                            DetallePedido dpNuevo = new DetallePedido();
                            dpNuevo.idCliente = idV;
                            dpNuevo.idProducto = item.idProducto;
                            dpNuevo.idMesa = idMesaActual;
                            dpNuevo.idDueloOrigen = uuidDueloActual;
                            dpNuevo.cantidad = 1;
                            dpNuevo.precioEnVenta = precioRepartido;
                            dpNuevo.esApuesta = true;
                            dpNuevo.estado = "REGISTRADO";
                            dpNuevo.fechaLong = System.currentTimeMillis();
                            dpNuevo.marcadorAlMomento = "PT_" + nuevoScoreGlobal + "_" + hito.aliasAnotador;
                            db.detallePedidoDao().insertarDetalle(dpNuevo);
                        }
                        db.detallePedidoDao().borrarDetallePorId(item.idDetalle);
                    }
                }
            });

            mainThreadHandler.post(() -> {
                Map<Integer, Integer> scoresActuales = scoresIndividualesInd.getValue();
                Map<Integer, Integer> copiaGlobales = (scoresActuales == null) ? new HashMap<>() : new HashMap<>(scoresActuales);
                copiaGlobales.put(idClienteAnotador, nuevoScoreGlobal);
                scoresIndividualesInd.setValue(copiaGlobales);
                dbTrigger.setValue(System.currentTimeMillis());
                _eventoVentaExitosa.setValue(true);
            });
        });
    }

    public void ejecutarRepartoBolsaInd(int idClienteAnotador, List<Cliente> todosLosParticipantes) {
        executorService.execute(() -> {
            List<DetallePedido> municionLista = db.detallePedidoDao().obtenerMunicionBolsaParaReparto(uuidDueloActual);
            if (municionLista == null || municionLista.isEmpty()) return;

            List<Integer> idsVictimas = identificarIdsVictimasInd(idClienteAnotador, todosLosParticipantes);
            if (idsVictimas.isEmpty()) return;

            BigDecimal divisor = new BigDecimal(idsVictimas.size());

            for (DetallePedido item : municionLista) {
                BigDecimal precioRepartido = item.precioEnVenta.divide(divisor, 2, RoundingMode.HALF_UP);
                for (Integer idV : idsVictimas) {
                    DetallePedido nuevo = new DetallePedido();
                    nuevo.idCliente = idV;
                    nuevo.idProducto = item.idProducto;
                    nuevo.idMesa = item.idMesa;
                    nuevo.idDueloOrigen = uuidDueloActual;
                    nuevo.cantidad = 1;
                    nuevo.precioEnVenta = precioRepartido;
                    nuevo.esApuesta = true;
                    nuevo.estado = "REGISTRADO";
                    nuevo.fechaLong = System.currentTimeMillis();
                    nuevo.marcadorAlMomento = "Impacto de: " + obtenerAliasCliente(idClienteAnotador);
                    db.detallePedidoDao().insertarDetalle(nuevo);
                }
                db.detallePedidoDao().borrarDetallePorId(item.idDetalle);
            }
            mainThreadHandler.post(() -> dbTrigger.setValue(System.currentTimeMillis()));
        });
    }

    private List<Integer> identificarIdsVictimasInd(int idClienteAnotador, List<Cliente> todosLosParticipantes) {
        List<Integer> victimas = new ArrayList<>();
        String regla = reglaPagoInd.getValue();
        if (regla == null) regla = "PERDEDORES";

        switch (regla) {
            case "PERDEDORES":
                for (Cliente c : todosLosParticipantes) {
                    if (c.idCliente != idClienteAnotador) victimas.add(c.idCliente);
                }
                break;
            case "TODOS":
                for (Cliente c : todosLosParticipantes) victimas.add(c.idCliente);
                break;
            case "ULTIMO":
                int idPeor = -1;
                int minPuntos = Integer.MAX_VALUE;
                Map<Integer, Integer> scores = scoresIndividualesInd.getValue();

                if (scores != null && !scores.isEmpty()) {
                    for (Cliente c : todosLosParticipantes) {
                        int pts = scores.containsKey(c.idCliente) ? scores.get(c.idCliente) : 0;
                        if (pts < minPuntos) {
                            minPuntos = pts;
                            idPeor = c.idCliente;
                        }
                    }
                }
                if (idPeor != -1) victimas.add(idPeor);
                else {
                    for (Cliente c : todosLosParticipantes) {
                        if (c.idCliente != idClienteAnotador) victimas.add(c.idCliente);
                    }
                }
                break;
        }
        return victimas;
    }

    public void retirarJugadorEspecificoInd(int idMesa, int idCliente) {
        executorService.execute(() -> {
            db.dueloTemporalIndDao().finalizarJugadorIndividual(idMesa, idCliente);
            mainThreadHandler.post(() -> {
                for (int i = 0; i < integrantesAzulCacheados.size(); i++) {
                    if (integrantesAzulCacheados.get(i).idCliente == idCliente) {
                        integrantesAzulCacheados.remove(i);
                        break;
                    }
                }
                dbTrigger.setValue(System.currentTimeMillis());
            });
        });
    }

    // =========================================================================================
    // CONSULTAS Y AYUDANTES DE LA BOLSA DE LA ARENA
    // =========================================================================================

    public LiveData<List<Producto>> getListaApuesta() { return listaApuesta; }
    public void limpiarApuesta() { listaApuesta.postValue(new ArrayList<>()); }

    public LiveData<List<Producto>> getListaApuestaEntregada() {
        return Transformations.switchMap(dbTrigger, trigger -> {
            MutableLiveData<List<Producto>> entregados = new MutableLiveData<>();
            executorService.execute(() -> {
                if (uuidDueloActual != null) {
                    List<Producto> lista = db.productoDao().obtenerProductosEntregadosDuelo(uuidDueloActual);
                    entregados.postValue(lista != null ? lista : new ArrayList<>());
                }
            });
            return entregados;
        });
    }

    public LiveData<List<Producto>> getBolsaIndEntregada() {
        return Transformations.switchMap(dbTrigger, trigger -> {
            MutableLiveData<List<Producto>> liveData = new MutableLiveData<>();
            executorService.execute(() -> {
                String uuid = (uuidDueloActual != null) ? uuidDueloActual : db.dueloDao().obtenerUuidDueloActivoIndPorMesa(idMesaActual);
                if (uuid != null) {
                    liveData.postValue(db.productoDao().obtenerProductosBolsaEntregados(uuid));
                } else {
                    liveData.postValue(new ArrayList<>());
                }
            });
            return liveData;
        });
    }

    public LiveData<BigDecimal> obtenerSaldoIndividualDuelo(int idCliente) {
        return Transformations.switchMap(dbTrigger, trigger -> {
            MutableLiveData<BigDecimal> saldo = new MutableLiveData<>();
            executorService.execute(() -> {
                if (uuidDueloActual != null) {
                    BigDecimal total = db.detallePedidoDao().obtenerTotalClienteEnDuelo(idCliente, uuidDueloActual);
                    saldo.postValue(total != null ? total : BigDecimal.ZERO);
                }
            });
            return saldo;
        });
    }

    public LiveData<BigDecimal> obtenerSaldoExtraIndividual(int idCliente) {
        return Transformations.switchMap(dbTrigger, trigger -> {
            MutableLiveData<BigDecimal> saldo = new MutableLiveData<>();
            executorService.execute(() -> {
                if (uuidDueloActual != null) {
                    BigDecimal total = db.detallePedidoDao().obtenerTotalExtraClienteEnDuelo(idCliente, uuidDueloActual);
                    saldo.postValue(total != null ? total : BigDecimal.ZERO);
                }
            });
            return saldo;
        });
    }

    public void confirmarEntregaBolsaInd(int idDetalle, int usuario) {
        executorService.execute(() -> {
            db.detallePedidoDao().despacharPedido(idDetalle, "ENTREGADO", usuario);
            mainThreadHandler.post(() -> dbTrigger.setValue(System.currentTimeMillis()));
        });
    }

    // =========================================================================================
    // CONFIGURACIÓN, REGLAS Y SEGURIDAD
    // =========================================================================================

    public LiveData<Boolean> getRequierePinDuelo() {
        return Transformations.switchMap(dbTrigger, trigger -> {
            MutableLiveData<Boolean> resultado = new MutableLiveData<>(true);
            executorService.execute(() -> {
                if (uuidDueloActual != null) {
                    Boolean requiere = db.dueloDao().obtenerRequierePinDuelo(uuidDueloActual);
                    resultado.postValue(requiere != null ? requiere : true);
                }
            });
            return resultado;
        });
    }

    public void actualizarSeguridadPinDuelo(boolean requiere) {
        executorService.execute(() -> {
            if (uuidDueloActual != null) {
                db.dueloDao().actualizarSeguridadPinDuelo(uuidDueloActual, requiere);
                mainThreadHandler.post(() -> dbTrigger.setValue(System.currentTimeMillis()));
            }
        });
    }

    public LiveData<String> getReglaCobroDuelo() {
        return Transformations.switchMap(dbTrigger, trigger -> {
            MutableLiveData<String> regla = new MutableLiveData<>("GANADOR_SALVA");
            executorService.execute(() -> {
                if (uuidDueloActual != null) {
                    String r = db.dueloDao().obtenerReglaCobroDuelo(uuidDueloActual);
                    regla.postValue(r != null ? r : "GANADOR_SALVA");
                }
            });
            return regla;
        });
    }

    public void actualizarReglaDuelo(String nuevaRegla) {
        executorService.execute(() -> {
            if (uuidDueloActual != null) {
                db.dueloDao().actualizarReglaCobroDuelo(uuidDueloActual, nuevaRegla);
                mainThreadHandler.post(() -> dbTrigger.setValue(System.currentTimeMillis()));
            }
        });
    }

    public void actualizarPerfilDueloInd(int idMesa, int puntos, String nivel) {
        executorService.execute(() -> {
            try {
                PerfilDueloInd nuevoPerfil = new PerfilDueloInd(idMesa, puntos, nivel, "PERDEDORES");
                db.perfilDueloIndDao().insertarOActualizar(nuevoPerfil);
                mainThreadHandler.post(() -> dbTrigger.setValue(System.currentTimeMillis()));
            } catch (Exception e) {
                Log.e("PERFIL_IND_ERR", "Error al guardar perfil: " + e.getMessage());
            }
        });
    }

    public LiveData<PerfilDueloInd> getPerfilDueloInd(int idMesa) {
        return dueloRepository.obtenerPerfilPorMesa(idMesa);
    }

    public void agregarJugadorADueloIndActivo(int idMesa, Cliente nuevoCliente) {
        executorService.execute(() -> {
            DueloTemporalInd dueloInd = new DueloTemporalInd(uuidDueloActual, idMesa, nuevoCliente.idCliente, 20, "PERDEDORES");
            db.dueloTemporalIndDao().insertarOActualizar(dueloInd);
            mainThreadHandler.post(() -> {
                if (!integrantesAzulCacheados.contains(nuevoCliente)) {
                    integrantesAzulCacheados.add(nuevoCliente);
                    dbTrigger.setValue(System.currentTimeMillis());
                }
            });
        });
    }

    // --- UTILIDADES ---
    public String obtenerAliasCliente(int idCliente) {
        for (Cliente c : todosLosParticipantesDuelo) {
            if (c.idCliente == idCliente) return c.alias;
        }
        return "Jugador " + idCliente;
    }

    private DetallePedido crearDetalleDeuda(int idCli, DetallePedido plantilla, BigDecimal precio, String uuid, String marcador) {
        DetallePedido nuevo = new DetallePedido();
        nuevo.idCliente = idCli;
        nuevo.idProducto = plantilla.idProducto;
        nuevo.idMesa = plantilla.idMesa;
        nuevo.idDueloOrigen = uuid;
        nuevo.cantidad = 1;
        nuevo.precioEnVenta = precio;
        nuevo.esApuesta = true;
        nuevo.estado = "REGISTRADO";
        nuevo.marcadorAlMomento = marcador;
        nuevo.fechaLong = System.currentTimeMillis();
        return nuevo;
    }

    public LiveData<List<LogAgrupadoDTO>> obtenerLogAgrupado(int idMesa) {
        return Transformations.switchMap(dbTrigger, trigger -> {
            MutableLiveData<List<LogAgrupadoDTO>> resultado = new MutableLiveData<>();
            executorService.execute(() -> {
                if (uuidDueloActual == null) {
                    resultado.postValue(new ArrayList<>());
                    return;
                }
                List<DetalleDueloTemporalInd> hitos = db.detalleDueloTemporalIndDao().obtenerHistorialHitosSincrono(uuidDueloActual);
                List<DetalleHistorialDuelo> todosLosProductos = db.detallePedidoDao().obtenerDeudaPorMesaIndSincrona(idMesa);
                List<LogAgrupadoDTO> listaAgrupada = new ArrayList<>();

                if (hitos != null) {
                    for (DetalleDueloTemporalInd hito : hitos) {
                        List<DetalleHistorialDuelo> productosDeEsteHito = new ArrayList<>();
                        String claveBusqueda = "PT_" + hito.scoreGlobalAnotador + "_" + hito.aliasAnotador;
                        if (todosLosProductos != null) {
                            for (DetalleHistorialDuelo p : todosLosProductos) {
                                if (claveBusqueda.equals(p.marcadorAlMomento)) productosDeEsteHito.add(p);
                            }
                        }
                        listaAgrupada.add(new LogAgrupadoDTO(hito, productosDeEsteHito));
                    }
                }
                resultado.postValue(listaAgrupada);
            });
            return resultado;
        });
    }

    public List<Integer> obtenerIdsParticipantesArena() {
        List<Integer> ids = new ArrayList<>();
        if (integrantesAzulCacheados != null) {
            for (Cliente c : integrantesAzulCacheados) ids.add(c.idCliente);
        }
        Map<Integer, Integer> mapa = mapaColoresDuelo.getValue();
        if (mapa != null) {
            for (Integer id : mapa.keySet()) {
                if (!ids.contains(id)) ids.add(id);
            }
        }
        return ids;
    }

    public void limpiarMesaDuelo(String uuidActual) {
        executorService.execute(() -> {
            db.bolaDueloDao().limpiarMesa(uuidActual);
        });
    }

    public void anotarBolaDuelo(String uuidDuelo, int colorEquipo, int numeroBola) {
        executorService.execute(() -> {
            com.nodo.tpv.data.entities.BolaAnotada bola = new com.nodo.tpv.data.entities.BolaAnotada(uuidDuelo, colorEquipo, numeroBola);
            db.bolaDueloDao().insertarBola(bola);
            historialBolasDueloActual.add(bola);
            mainThreadHandler.post(() -> dbTrigger.setValue(System.currentTimeMillis()));
        });
    }

    private void registrarEventoOperativo(String tipo, String json) {
        com.nodo.tpv.data.entities.ActividadOperativaLocal evento = new com.nodo.tpv.data.entities.ActividadOperativaLocal();
        evento.eventoId = java.util.UUID.randomUUID().toString();
        evento.tipoEvento = tipo;
        evento.fechaDispositivo = System.currentTimeMillis();
        evento.estadoSync = "PENDIENTE";
        evento.detallesJson = json;

        db.actividadOperativaLocalDao().insertar(evento);
        dispararSincronizacion();
    }

    public void revertirBolaDuelo(String uuid, int numero) {
        executorService.execute(() -> {
            db.bolaDueloDao().eliminarBola(uuid, numero);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                historialBolasDelDueloCompleto.removeIf(b -> b.numeroBola == numero);
            }
            mainThreadHandler.post(() -> dbTrigger.setValue(System.currentTimeMillis()));
        });
    }

    public void procesarCruceFaltas(String uuidActual, List<Integer> bolasAEliminar, List<BolaAnotada> bolasAInsertar) {
        executorService.execute(() -> {
            db.runInTransaction(() -> {
                if (bolasAEliminar != null) {
                    for (Integer idBola : bolasAEliminar) {
                        db.bolaDueloDao().eliminarBola(uuidActual, idBola);
                    }
                }
                if (bolasAInsertar != null) {
                    for (BolaAnotada bola : bolasAInsertar) {
                        db.bolaDueloDao().insertarBola(bola);
                    }
                }
            });
        });
    }

    public interface BolasBloqueadasCallback {
        void onLoaded(List<Integer> bolas);
    }

    public void obtenerBolasBloqueadas(String uuidActual, BolasBloqueadasCallback callback) {
        executorService.execute(() -> {
            List<Integer> bloqueadas = db.bolaDueloDao().obtenerBolasYaAnotadasSincrono(uuidActual);
            mainThreadHandler.post(() -> callback.onLoaded(bloqueadas != null ? bloqueadas : new ArrayList<>()));
        });
    }

    public LiveData<List<com.nodo.tpv.data.dto.EventoBatalla>> obtenerLogBatallaEnVivo(String uuidActual) {
        return Transformations.switchMap(dbTrigger, trigger -> {
            MutableLiveData<List<com.nodo.tpv.data.dto.EventoBatalla>> historialLiveData = new MutableLiveData<>();

            executorService.execute(() -> {
                List<com.nodo.tpv.data.dto.EventoBatalla> historialFinal = new ArrayList<>();
                if (uuidActual == null) {
                    mainThreadHandler.post(() -> historialLiveData.setValue(historialFinal));
                    return;
                }

                try {
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("hh:mm a", java.util.Locale.getDefault());

                    com.nodo.tpv.data.dto.EventoBatalla bannerPadre = new com.nodo.tpv.data.dto.EventoBatalla(
                            com.nodo.tpv.data.dto.EventoBatalla.TIPO_CIERRE_RONDA,
                            System.currentTimeMillis(),
                            sdf.format(new java.util.Date()),
                            "CRÓNICA DEL DUELO",
                            "Resumen consolidado por equipos",
                            obtenerMarcadorActualString(),
                            android.graphics.Color.WHITE
                    );
                    bannerPadre.setEsHijo(false);
                    historialFinal.add(bannerPadre);

                    List<BolaAnotada> todasLasBolas = db.bolaDueloDao().obtenerBolasPorDueloSincrono(uuidActual);
                    List<DetallePedido> todosLosPedidos = db.detallePedidoDao().obtenerDetallesMunicionSincrono(uuidActual);

                    Map<Integer, List<Integer>> puntosPorEquipo = new HashMap<>();
                    int totalMalas = 0;

                    if (todasLasBolas != null) {
                        for (BolaAnotada b : todasLasBolas) {
                            if (b.numeroBola < 0) {
                                totalMalas++;
                            } else {
                                if (!puntosPorEquipo.containsKey(b.colorEquipo)) {
                                    puntosPorEquipo.put(b.colorEquipo, new ArrayList<>());
                                }
                                puntosPorEquipo.get(b.colorEquipo).add(b.numeroBola);
                            }
                        }
                    }

                    for (Map.Entry<Integer, List<Integer>> entry : puntosPorEquipo.entrySet()) {
                        int color = entry.getKey();
                        List<Integer> bolas = entry.getValue();

                        StringBuilder sb = new StringBuilder("Bolas: ");
                        for (int i = 0; i < bolas.size(); i++) {
                            sb.append(bolas.get(i)).append(i == bolas.size() - 1 ? "" : ", ");
                        }

                        com.nodo.tpv.data.dto.EventoBatalla itemEquipo = new com.nodo.tpv.data.dto.EventoBatalla(
                                com.nodo.tpv.data.dto.EventoBatalla.TIPO_BOLA_ANOTADA,
                                0,
                                "Total: " + bolas.size(),
                                "PUNTOS EQUIPO " + getNombreColor(color),
                                sb.toString(),
                                "Efectividad: " + bolas.size() + " pts",
                                color
                        );
                        itemEquipo.setEsHijo(true);
                        historialFinal.add(itemEquipo);
                    }

                    if (totalMalas > 0) {
                        com.nodo.tpv.data.dto.EventoBatalla itemMalas = new com.nodo.tpv.data.dto.EventoBatalla(
                                com.nodo.tpv.data.dto.EventoBatalla.TIPO_FALTA,
                                0,
                                "Sanción",
                                "TOTAL DE FALTAS",
                                "Se registraron " + totalMalas + " infracciones en la mesa.",
                                "Descuento global",
                                android.graphics.Color.parseColor("#FF1744")
                        );
                        itemMalas.setEsHijo(true);
                        historialFinal.add(itemMalas);
                    }

                    if (todosLosPedidos != null && !todosLosPedidos.isEmpty()) {
                        for (DetallePedido p : todosLosPedidos) {
                            com.nodo.tpv.data.dto.EventoBatalla itemPedido = new com.nodo.tpv.data.dto.EventoBatalla(
                                    com.nodo.tpv.data.dto.EventoBatalla.TIPO_COMPRA_MUNICION,
                                    p.fechaLong,
                                    sdf.format(new java.util.Date(p.fechaLong)),
                                    "PEDIDO: " + p.cantidad + "x UNID.",
                                    "Consumo registrado",
                                    "$" + p.precioEnVenta,
                                    android.graphics.Color.parseColor("#FFD600")
                            );
                            itemPedido.setEsHijo(true);
                            historialFinal.add(itemPedido);
                        }
                    }

                } catch (Exception e) {
                    android.util.Log.e("ARENA_CONSOLIDADO", "Error: " + e.getMessage());
                }

                mainThreadHandler.post(() -> historialLiveData.setValue(historialFinal));
            });

            return historialLiveData;
        });
    }

    private com.nodo.tpv.data.dto.EventoBatalla crearEventoConsolidadoGhost(BolaAnotada ref, List<Integer> nums, java.text.SimpleDateFormat sdf) {
        int cantidad = nums.size();
        boolean esMala = ref.numeroBola < 0;
        String hora = sdf.format(new java.util.Date(ref.timestamp));

        String titulo;
        String descripcion;
        int color;

        if (esMala) {
            titulo = (cantidad == 1) ? "1 MALA COMETIDA" : cantidad + " MALAS TOTALES";
            descripcion = "Penalización aplicada al marcador";
            color = Color.parseColor("#FF1744");
        } else {
            titulo = (cantidad == 1) ? "PUNTO: BOLA #" + nums.get(0) : "RACHA: " + cantidad + " PUNTOS";

            StringBuilder sb = new StringBuilder("Bolas: ");
            for (int i = 0; i < nums.size(); i++) {
                sb.append(nums.get(i)).append(i == nums.size() - 1 ? "" : ", ");
            }
            descripcion = sb.toString();
            color = ref.colorEquipo;
        }

        return new com.nodo.tpv.data.dto.EventoBatalla(
                esMala ? com.nodo.tpv.data.dto.EventoBatalla.TIPO_FALTA : com.nodo.tpv.data.dto.EventoBatalla.TIPO_BOLA_ANOTADA,
                ref.timestamp,
                hora,
                titulo,
                descripcion,
                "Marcador tras racha: " + obtenerMarcadorActualString(),
                color
        );
    }

    private com.nodo.tpv.data.dto.EventoBatalla crearEventoConsolidado(BolaAnotada ref, List<Integer> nums, java.text.SimpleDateFormat sdf) {
        int cant = nums.size();
        boolean esMala = ref.numeroBola < 0;

        String titulo = esMala ? (cant == 1 ? "1 FALTA" : cant + " FALTAS")
                : (cant == 1 ? "PUNTO REGISTRADO" : "RACHA: " + cant + " PUNTOS");

        StringBuilder desc = new StringBuilder(esMala ? "Puntos descontados: " : "Bolas: ");
        for (int i = 0; i < nums.size(); i++) {
            desc.append(Math.abs(nums.get(i))).append(i == nums.size() - 1 ? "" : ", ");
        }

        return new com.nodo.tpv.data.dto.EventoBatalla(
                esMala ? com.nodo.tpv.data.dto.EventoBatalla.TIPO_FALTA : com.nodo.tpv.data.dto.EventoBatalla.TIPO_BOLA_ANOTADA,
                ref.timestamp,
                sdf.format(new java.util.Date(ref.timestamp)),
                titulo,
                desc.toString(),
                obtenerMarcadorActualString(),
                esMala ? android.graphics.Color.parseColor("#FF1744") : ref.colorEquipo
        );
    }

    private com.nodo.tpv.data.dto.EventoBatalla crearEventoDesdeRacha(BolaAnotada inicio, List<Integer> numeros, java.text.SimpleDateFormat sdf) {
        String hora = sdf.format(new java.util.Date(inicio.timestamp));
        int cantidad = numeros.size();

        if (inicio.numeroBola < 0) {
            return new com.nodo.tpv.data.dto.EventoBatalla(
                    com.nodo.tpv.data.dto.EventoBatalla.TIPO_FALTA,
                    inicio.timestamp,
                    hora,
                    cantidad == 1 ? "1 FALTA" : cantidad + " FALTAS",
                    "Penalización de racha",
                    "Castigo",
                    android.graphics.Color.parseColor("#FF1744")
            );
        } else {
            StringBuilder sb = new StringBuilder("Bolas: ");
            for (int i = 0; i < numeros.size(); i++) {
                sb.append(numeros.get(i)).append(i == numeros.size() - 1 ? "" : ", ");
            }
            return new com.nodo.tpv.data.dto.EventoBatalla(
                    com.nodo.tpv.data.dto.EventoBatalla.TIPO_BOLA_ANOTADA,
                    inicio.timestamp,
                    hora,
                    cantidad == 1 ? "BOLA #" + numeros.get(0) : "RACHA: " + cantidad + " PUNTOS",
                    sb.toString(),
                    "A favor",
                    inicio.colorEquipo
            );
        }
    }

    private void dispararSincronizacion() {
        Constraints constraints = new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
        OneTimeWorkRequest syncRequest = new OneTimeWorkRequest.Builder(com.nodo.tpv.data.sync.OperatividadSyncWorker.class)
                .setConstraints(constraints).build();
        WorkManager.getInstance(context).enqueueUniqueWork("SyncOperatividadInmediata", ExistingWorkPolicy.KEEP, syncRequest);
    }

}