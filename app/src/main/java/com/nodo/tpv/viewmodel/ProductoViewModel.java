package com.nodo.tpv.viewmodel;

import android.app.Application;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.nodo.tpv.data.api.RetrofitClient;
import com.nodo.tpv.data.database.AppDatabase;
import com.nodo.tpv.data.dto.DetalleConNombre;
import com.nodo.tpv.data.dto.DetalleHistorialDuelo;
import com.nodo.tpv.data.dto.LogAgrupadoDTO;
import com.nodo.tpv.data.dto.ProductoDTO;
import com.nodo.tpv.data.entities.Cliente;
import com.nodo.tpv.data.entities.DetalleDueloTemporalInd;
import com.nodo.tpv.data.entities.DetallePedido;
import com.nodo.tpv.data.entities.DueloTemporal;
import com.nodo.tpv.data.entities.DueloTemporalInd;
import com.nodo.tpv.data.entities.PerfilDueloInd;
import com.nodo.tpv.data.entities.Producto;
import com.nodo.tpv.data.entities.VentaDetalleHistorial;
import com.nodo.tpv.data.entities.VentaHistorial;
import com.nodo.tpv.data.sync.StockSyncWorker;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ProductoViewModel extends AndroidViewModel {
    private final AppDatabase db;
    private final MutableLiveData<Long> dbTrigger = new MutableLiveData<>(System.currentTimeMillis());
    private final ExecutorService executorService = Executors.newFixedThreadPool(4);
    private final Handler mainThreadHandler = new Handler(Looper.getMainLooper());

    // --- ESTADOS DE CONTROL GENERAL ---
    private final MutableLiveData<String> tipoJuegoActual = new MutableLiveData<>("POOL");
    private final MutableLiveData<Boolean> enModoDuelo = new MutableLiveData<>(false);
    private final MutableLiveData<List<Producto>> listaApuesta = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<List<Producto>> productosResultados = new MutableLiveData<>();

    // --- ESTADOS ARENA DUELO (EQUIPOS) ---
    private final MutableLiveData<Integer> scoreAzul = new MutableLiveData<>(0);
    private final MutableLiveData<Integer> scoreRojo = new MutableLiveData<>(0);

    // --- ESTADOS ARENA INDIVIDUAL (3 BANDAS) ---
    private final MutableLiveData<Map<Integer, Integer>> scoresIndividualesInd = new MutableLiveData<>(new HashMap<>());
    private final MutableLiveData<String> reglaPagoInd = new MutableLiveData<>("PERDEDORES");
    private final MutableLiveData<Integer> metaCarambolasInd = new MutableLiveData<>(15);
    private final MutableLiveData<Long> tiempoInicioDuelo = new MutableLiveData<>(0L);

    // --- CACHÉ ARENA ---
    private List<Cliente> integrantesAzulCacheados = new ArrayList<>();
    private List<Cliente> integrantesRojoCacheados = new ArrayList<>();
    private String uuidDueloActual = null;

    private List<Cliente> todosLosParticipantesDuelo = new ArrayList<>();

    private int idMesaActual;

    // --- NUEVOS ESTADOS PARA ARENA MULTIEQUIPO ---
// Mapa para saber qué color tiene cada cliente en la arena: <IdCliente, ColorResId>
    private final MutableLiveData<Map<Integer, Integer>> mapaColoresDuelo = new MutableLiveData<>(new HashMap<>());

    // Getter para la Arena
    public LiveData<Map<Integer, Integer>> getMapaColoresDuelo() {
        return mapaColoresDuelo;
    }

    public interface OnDetallesCargadosListener { void onCargados(List<VentaDetalleHistorial> detalles); }

    public ProductoViewModel(@NonNull Application application) {
        super(application);
        db = AppDatabase.getInstance(application);
    }

    private final MutableLiveData<Map<Integer, Integer>> scoresEquipos = new MutableLiveData<>(new HashMap<>());

    public LiveData<Map<Integer, Integer>> getScoresEquipos() {
        return scoresEquipos;
    }

    // Estado de la regla en memoria para la UI
    private final MutableLiveData<String> reglaCobroActiva = new MutableLiveData<>("GANADOR_SALVA");

    public LiveData<String> getReglaCobroActiva() {
        return reglaCobroActiva;
    }

    // --- GETTERS ---
    public LiveData<String> getTipoJuegoActual() { return tipoJuegoActual; }
    public LiveData<Boolean> getEnModoDuelo() { return enModoDuelo; }
    public LiveData<Integer> getScoreAzul() { return scoreAzul; }
    public LiveData<Integer> getScoreRojo() { return scoreRojo; }
    public LiveData<List<Producto>> getListaApuesta() { return listaApuesta; }
    public LiveData<Long> getDbTrigger() { return dbTrigger; }
    public LiveData<List<Producto>> getProductosResultados() { return productosResultados; }
    public String getUuidDueloActual() { return uuidDueloActual; }
    public List<Cliente> getIntegrantesAzulCacheados() { return integrantesAzulCacheados; }
    public List<Cliente> getIntegrantesRojoCacheados() { return integrantesRojoCacheados; }
    public LiveData<Long> getTiempoInicioDuelo() { return tiempoInicioDuelo; }

    // --- GETTERS ESPECÍFICOS IND ---
    public LiveData<Map<Integer, Integer>> getScoresIndividualesInd() { return scoresIndividualesInd; }
    public LiveData<String> getReglaPagoInd() { return reglaPagoInd; }
    public LiveData<Integer> getMetaCarambolasInd() { return metaCarambolasInd; }


    // --- CONFIGURACIÓN DE MESA ---
    public void setTipoJuego(String tipo) { tipoJuegoActual.postValue(tipo); }

    // --- FUNCIONES CORE ARENA (POOL & INDIVIDUAL) ---

    public LiveData<List<DetalleHistorialDuelo>> obtenerHistorialItemsActivo() {
        if (uuidDueloActual == null) {
            executorService.execute(() -> uuidDueloActual = db.dueloDao().obtenerUuidDueloActivo());
        }
        return db.detallePedidoDao().obtenerHistorialItemsDuelo(uuidDueloActual);
    }

    public LiveData<BigDecimal> obtenerTotalEquipo(List<Cliente> equipo) {
        MutableLiveData<BigDecimal> resultado = new MutableLiveData<>();
        if (equipo == null || equipo.isEmpty()) {
            resultado.setValue(BigDecimal.ZERO);
            return resultado;
        }
        List<Integer> ids = new ArrayList<>();
        for (Cliente c : equipo) ids.add(c.idCliente);

        executorService.execute(() -> {
            if (uuidDueloActual == null) uuidDueloActual = db.dueloDao().obtenerUuidDueloActivo();
            BigDecimal suma = db.detallePedidoDao().obtenerSumaSaldosDuelo(ids, uuidDueloActual);
            resultado.postValue(suma != null ? suma : BigDecimal.ZERO);
        });
        return resultado;
    }

    // --- FUNCIONES ESPECÍFICAS IND (3 BANDAS) ---

    public void setReglaPagoInd(String regla) {
        reglaPagoInd.postValue(regla);
        executorService.execute(() -> {
            if (uuidDueloActual != null) db.dueloTemporalIndDao().actualizarReglaPagoMesa(0, regla);
        });
    }

    public void setMetaCarambolasInd(int meta) { metaCarambolasInd.postValue(meta); }

    public void iniciarDueloIndPersistente(List<Cliente> clientes, int idMesa) {
        // 1. Preparación inmediata de la caché en memoria para la UI
        this.idMesaActual = idMesa;
        this.integrantesAzulCacheados = new ArrayList<>(clientes);
        this.tipoJuegoActual.postValue("3BANDAS");
        this.enModoDuelo.postValue(true);

        executorService.execute(() -> {
            // 2. RECUPERAR O GENERAR EL UUID DEL DUELO
            // Es vital que uuidDueloActual tenga valor antes de disparar el dbTrigger
            String uuidExistente = db.dueloDao().obtenerUuidDueloActivoInd();
            if (uuidExistente != null) {
                this.uuidDueloActual = uuidExistente;
            } else {
                this.uuidDueloActual = UUID.randomUUID().toString();
            }

            // 3. PERSISTENCIA INDIVIDUAL DE JUGADORES
            for (Cliente c : clientes) {
                DueloTemporalInd existente = db.dueloTemporalIndDao().obtenerEstadoCliente(idMesa, c.idCliente);

                if (existente == null) {
                    // Si es nuevo en la mesa, creamos su registro con su propio tiempo de entrada
                    DueloTemporalInd nuevo = new DueloTemporalInd(
                            this.uuidDueloActual,
                            idMesa,
                            c.idCliente,
                            20, // Meta inicial
                            "PERDEDORES" // Regla por defecto
                    );
                    nuevo.timestampInicio = System.currentTimeMillis();
                    db.dueloTemporalIndDao().insertarOActualizar(nuevo);
                }
            }

            // 4. NOTIFICACIÓN FINAL A LA UI
            mainThreadHandler.post(() -> {
                // Esto dispara todos los observadores del Fragment (Bolsa, Badge, Cronómetros)
                this.dbTrigger.setValue(System.currentTimeMillis());
                Log.d("ARENA_DUELO", "Duelo Individual iniciado/recuperado: " + this.uuidDueloActual);
            });
        });
    }

    // Dentro de ProductoViewModel.java

    // Dentro de ProductoViewModel.java

    public void aplicarDanioInd(int idClienteAnotador, List<Cliente> todosLosParticipantes, Map<Integer, Integer> miniMarcadoresSnapshot) {
        if (uuidDueloActual == null) return;

        // 🔥 SEGURIDAD: Clonamos el mapa inmediatamente para que el hilo de fondo
        // vea los valores exactos de este preciso momento.
        final Map<Integer, Integer> copiaMinis = new HashMap<>(miniMarcadoresSnapshot);

        executorService.execute(() -> {
            // 1. Obtener cabecera para score global + 1
            DueloTemporalInd cabecera = db.dueloTemporalIndDao().obtenerDueloPorMesaYCliente(idMesaActual, idClienteAnotador);
            if (cabecera == null) return;
            int nuevoScoreGlobal = cabecera.score + 1;

            db.runInTransaction(() -> {
                // 2. Actualizar score global en DB
                db.dueloTemporalIndDao().actualizarScore(idMesaActual, idClienteAnotador, nuevoScoreGlobal);

                // 3. Construir la "Foto" de TODOS los minimarcadores
                StringBuilder sbMini = new StringBuilder();
                for (int i = 0; i < todosLosParticipantes.size(); i++) {
                    Cliente c = todosLosParticipantes.get(i);
                    // Usamos la COPIA para asegurar que no leamos un 0 si el Fragment ya reseteó
                    int valorCarambolas = copiaMinis.getOrDefault(c.idCliente, 0);

                    sbMini.append(c.alias).append(": ").append(valorCarambolas);
                    if (i < todosLosParticipantes.size() - 1) sbMini.append(" | ");
                }

                // 4. INSERTAR EL HITO "FUERTE"
                DetalleDueloTemporalInd hito = new DetalleDueloTemporalInd(
                        uuidDueloActual,
                        idMesaActual,
                        idClienteAnotador,
                        obtenerAliasCliente(idClienteAnotador),
                        nuevoScoreGlobal,
                        sbMini.toString(), // Aquí va la cadena: "Juan: 20 | Pedro: 15..."
                        obtenerMarcadorActualString()
                );
                db.detalleDueloTemporalIndDao().insertarHito(hito);

                // 5. REPARTO DE BOLSA (Solo lo ENTREGADO)
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

            // 6. Refrescar UI
            mainThreadHandler.post(() -> {
                Map<Integer, Integer> scoresActuales = scoresIndividualesInd.getValue();
                Map<Integer, Integer> copiaGlobales = (scoresActuales == null) ? new HashMap<>() : new HashMap<>(scoresActuales);
                copiaGlobales.put(idClienteAnotador, nuevoScoreGlobal);
                scoresIndividualesInd.setValue(copiaGlobales);
                dbTrigger.setValue(System.currentTimeMillis());
            });
        });
    }

    // Agrega el parámetro idMesa a la firma de la función
    private void actualizarPuntajeIndividualDinamico(int idMesa, int idCliente) {
        executorService.execute(() -> {
            // Ahora idMesa es accesible porque viene por parámetro
            DueloTemporalInd duelo = db.dueloTemporalIndDao().obtenerDueloPorMesaYCliente(idMesa, idCliente);

            if (duelo != null) {
                duelo.score = duelo.score + 1;
                db.dueloTemporalIndDao().actualizar(duelo);

                mainThreadHandler.post(() -> {
                    Map<Integer, Integer> currentScores = scoresIndividualesInd.getValue();
                    Map<Integer, Integer> copia = (currentScores == null) ? new HashMap<>() : new HashMap<>(currentScores);
                    copia.put(idCliente, duelo.score);
                    scoresIndividualesInd.setValue(copia);

                    dbTrigger.setValue(System.currentTimeMillis());
                });
            }
        });
    }

    // --- LÓGICA DE BOLSA (APUESTA) ---
    public void actualizarListaApuesta(List<Producto> nuevaLista) {
        if (Looper.myLooper() == Looper.getMainLooper()) listaApuesta.setValue(nuevaLista);
        else listaApuesta.postValue(nuevaLista);
    }

    public void agregarProductoAApuesta(Producto p) {
        List<Producto> actual = listaApuesta.getValue();
        if (actual == null) actual = new ArrayList<>();
        actual.add(p);
        actualizarListaApuesta(actual);
    }

    public void limpiarApuesta() { actualizarListaApuesta(new ArrayList<>()); }

    // --- GESTIÓN DE PEDIDOS E INSERCIONES ---

    public void insertarConsumoDirecto(int idCliente, Producto producto, int cantidad) {
        executorService.execute(() -> {
            // 1. Obtener datos básicos
            int idMesa = db.clienteDao().obtenerMesaDelCliente(idCliente);
            String uuidDuelo = db.dueloDao().obtenerUuidDueloActivo(); // Buscamos si hay un duelo en curso

            // 2. Crear el objeto DetallePedido
            DetallePedido dp = new DetallePedido();
            dp.idCliente = idCliente;
            dp.idProducto = producto.idProducto;
            dp.idMesa = idMesa;
            dp.cantidad = cantidad;
            dp.precioEnVenta = producto.getPrecioProducto();
            dp.fechaLong = System.currentTimeMillis();
            dp.estado = "PENDIENTE"; // Siempre nace pendiente para el Badge

            // 3. VÍNCULO CON EL DUELO (Lo que pediste)
            // Verificamos si este cliente pertenece al duelo activo
            boolean clienteEnDuelo = db.dueloDao().verificarClienteEnDuelo(uuidDuelo, idCliente);

            if (clienteEnDuelo) {
                dp.esApuesta = true; // Se marca como parte de la deuda del duelo
                dp.idDueloOrigen = uuidDuelo;
                dp.marcadorAlMomento = obtenerMarcadorActualString(); // Guardamos el marcador actual
            } else {
                dp.esApuesta = false; // Es un consumo normal fuera de duelo
            }

            // 4. Insertar y Notificar
            db.detallePedidoDao().insertarDetalle(dp);
            mainThreadHandler.post(() -> dbTrigger.setValue(System.currentTimeMillis()));
        });
    }

    public void aplicarDanioAlPerdedor(int equipoAnotador, List<Cliente> azul, List<Cliente> rojo) {
        List<Producto> apuesta = listaApuesta.getValue();
        if (apuesta == null || apuesta.isEmpty()) return;

        int futAzul = (scoreAzul.getValue() != null ? scoreAzul.getValue() : 0);
        int futRojo = (scoreRojo.getValue() != null ? scoreRojo.getValue() : 0);

        if (equipoAnotador == 1) futAzul++; else futRojo++;
        String marcadorActual = futAzul + " - " + futRojo;
        List<Cliente> victimas = (equipoAnotador == 1) ? rojo : azul;

        registrarConsumoDistribuido(victimas, apuesta, marcadorActual);

        if (equipoAnotador == 1) scoreAzul.postValue(futAzul); else scoreRojo.postValue(futRojo);
        limpiarApuesta();
    }

    private void registrarConsumoDistribuido(List<Cliente> victimas, List<Producto> productos, String marcador) {
        executorService.execute(() -> {
            if (victimas.isEmpty() || uuidDueloActual == null) return;
            for (Producto p : productos) {
                BigDecimal precioInd = p.getPrecioProducto().divide(new BigDecimal(victimas.size()), 2, RoundingMode.HALF_UP);
                for (Cliente c : victimas) {
                    DetallePedido dp = new DetallePedido();
                    dp.idCliente = c.idCliente;
                    dp.idProducto = p.idProducto;
                    dp.cantidad = 1;
                    dp.precioEnVenta = precioInd;
                    dp.esApuesta = true;
                    dp.idDueloOrigen = uuidDueloActual;
                    dp.marcadorAlMomento = marcador;
                    dp.fechaLong = System.currentTimeMillis();
                    db.detallePedidoDao().insertarDetalle(dp);
                }
            }
            mainThreadHandler.post(() -> dbTrigger.setValue(System.currentTimeMillis()));
        });
    }

    // --- CIERRE DE CUENTA E HISTORIAL ---
    public void finalizarCuenta(int id, String alias, String metodo, String fotoBase64) {
        executorService.execute(() -> {
            // 1. Obtener los datos actuales del cliente antes de borrarlos
            BigDecimal total = db.detallePedidoDao().obtenerTotalDirecto(id);
            List<DetalleConNombre> consumos = db.detallePedidoDao().obtenerDetalleConNombresSincrono(id);

            // 2. Validar que existan consumos para procesar la venta
            if (total != null && total.compareTo(BigDecimal.ZERO) > 0 && consumos != null) {

                // --- PASO A: Crear la Venta Principal (Padre) ---
                VentaHistorial vH = new VentaHistorial();
                vH.idCliente = id;
                vH.nombreCliente = alias;
                vH.montoTotal = total;
                vH.metodoPago = metodo;
                vH.fechaLong = System.currentTimeMillis();
                vH.estado = "PENDIENTE"; // Crítico para auditoría posterior
                vH.fotoComprobante = fotoBase64; // String Base64 capturado

                // Insertamos y recuperamos el ID autogenerado
                long idVentaPadre = db.detallePedidoDao().insertarVentaHistorial(vH);

                // --- PASO B: Migrar detalles al historial ---
                List<VentaDetalleHistorial> listaDetallesHistorial = new ArrayList<>();

                for (DetalleConNombre item : consumos) {
                    VentaDetalleHistorial vDet = new VentaDetalleHistorial();
                    vDet.idVentaPadre = (int) idVentaPadre;
                    vDet.nombreProducto = item.nombreProducto;
                    vDet.cantidad = item.detallePedido.cantidad;
                    vDet.precioUnitario = item.detallePedido.precioEnVenta;

                    // Mapear si es apuesta
                    // Asumiendo que DetallePedido tiene el campo esApuesta
                    vDet.esApuesta = item.detallePedido.esApuesta;

                    listaDetallesHistorial.add(vDet);
                }

                // Insertar todos los detalles en bloque para optimizar la DB
                db.detallePedidoDao().insertarDetallesHistorial(listaDetallesHistorial);

                // --- PASO C: Limpiar datos temporales ---
                db.detallePedidoDao().borrarCuentaCliente(id);
                db.clienteDao().eliminarPorId(id);

                // Notificar a la UI para refrescar pantallas
                mainThreadHandler.post(() -> dbTrigger.setValue(System.currentTimeMillis()));
            }
        });
    }

    public void obtenerDetallesTicket(int idVenta, OnDetallesCargadosListener listener) {
        executorService.execute(() -> {
            List<VentaDetalleHistorial> detalles = db.detallePedidoDao().obtenerDetallesDeVentaSincrono(idVenta);
            mainThreadHandler.post(() -> { if (listener != null) listener.onCargados(detalles); });
        });
    }

    // --- GESTIÓN DE LA ARENA ---

    /*public void iniciarDueloPersistente(List<Cliente> azul, List<Cliente> rojo, String tipo) {
        this.integrantesAzulCacheados = new ArrayList<>(azul);
        this.integrantesRojoCacheados = new ArrayList<>(rojo);
        this.tipoJuegoActual.postValue(tipo);
        this.enModoDuelo.postValue(true);
        this.tiempoInicioDuelo.postValue(System.currentTimeMillis());

        executorService.execute(() -> {
            String uuidExistente = db.dueloDao().obtenerUuidDueloActivo();
            if (uuidExistente == null) {
                uuidDueloActual = UUID.randomUUID().toString();
                db.dueloDao().borrarDueloFallido();
                for (Cliente c : azul) db.dueloDao().insertarParticipante(new DueloTemporal(uuidDueloActual, 1, c.idCliente, "ACTIVO"));
                for (Cliente c : rojo) db.dueloDao().insertarParticipante(new DueloTemporal(uuidDueloActual, 2, c.idCliente, "ACTIVO"));
            } else { uuidDueloActual = uuidExistente; }
            mainThreadHandler.post(() -> dbTrigger.setValue(System.currentTimeMillis()));
        });
    }*/

    public void finalizarDueloCompleto(int idMesa, String tipoJuego) {
        executorService.execute(() -> {
            // 1. Limpieza en Base de Datos diferenciada
            if ("POOL".equals(tipoJuego)) {
                // Finaliza registros en la tabla duelos_temporales
                db.dueloDao().finalizarDueloActual();
            } else if ("3BANDAS".equals(tipoJuego)) {
                // Finaliza registros en la tabla duelos_temporales_ind usando el ID real
                db.dueloTemporalIndDao().finalizarDueloMesa(idMesa);
            }

            mainThreadHandler.post(() -> {
                // 2. Control de Estado General
                enModoDuelo.setValue(false);
                uuidDueloActual = null;
                tiempoInicioDuelo.setValue(0L);

                // 3. Limpieza de Arena Dinámica (POOL MULTIEQUIPO)
                mapaColoresDuelo.setValue(new HashMap<>());
                scoresEquipos.setValue(new HashMap<>());

                // 4. Limpieza de Arena Clásica y Caché de integrantes
                scoreAzul.setValue(0);
                scoreRojo.setValue(0);
                if (integrantesAzulCacheados != null) integrantesAzulCacheados.clear();
                if (integrantesRojoCacheados != null) integrantesRojoCacheados.clear();

                // 5. Limpieza de Arena 3 BANDAS (Score Individual)
                scoresIndividualesInd.setValue(new HashMap<>());

                // 6. Limpieza de Bolsa de munición/apuesta
                limpiarApuesta();

                // 7. Gatillo de actualización global (Refresca Badge, Listas y Botones)
                dbTrigger.setValue(System.currentTimeMillis());

                Log.d("ARENA_FINISH", "Duelo " + tipoJuego + " en Mesa #" + idMesa + " finalizado correctamente.");
            });
        });
    }

    // --- MÉTODOS DE APOYO Y PRODUCTOS ---
    public void cargarTodosLosProductos() {
        executorService.execute(() -> productosResultados.postValue(db.productoDao().obtenerTodosProductos()));
    }

    public void insertarProductosPrueba() {
        executorService.execute(() -> {
            if (db.productoDao().getProductoCount() == 0) {
                List<Producto> listaPrueba = new ArrayList<>();
                listaPrueba.add(crearProducto("Cerveza Poker 330ml", "5500", "BEBIDAS"));
                listaPrueba.add(crearProducto("Cerveza Club Colombia", "6500", "BEBIDAS"));
                listaPrueba.add(crearProducto("Cerveza Aguila Light", "5500", "BEBIDAS"));
                listaPrueba.add(crearProducto("Cerveza Corona", "9500", "BEBIDAS"));
                listaPrueba.add(crearProducto("Gaseosa Coca-Cola 400ml", "3500", "BEBIDAS"));
                listaPrueba.add(crearProducto("Agua Mineral", "3000", "BEBIDAS"));
                listaPrueba.add(crearProducto("Empanada de Carne", "2500", "COMIDA"));
                listaPrueba.add(crearProducto("Picada Familiar", "45000", "COMIDA"));
                listaPrueba.add(crearProducto("Porción de Papas", "8000", "COMIDA"));
                listaPrueba.add(crearProducto("Hamburguesa Especial", "18000", "COMIDA"));
                listaPrueba.add(crearProducto("Aguardiente Antioqueño (Media)", "65000", "LICORES"));
                listaPrueba.add(crearProducto("Whisky Old Parr (Trago)", "15000", "LICORES"));

                db.productoDao().insertarOActualizar(listaPrueba);
                mainThreadHandler.post(this::cargarTodosLosProductos);
            }
        });
    }

    public void buscarProducto(String query) {
        executorService.execute(() -> productosResultados.postValue(db.productoDao().buscarProducto("%" + query + "%")));
    }

    private Producto crearProducto(String nombre, String precio, String categoria) {
        Producto p = new Producto();
        p.setNombreProducto(nombre);
        p.setPrecioProducto(new BigDecimal(precio));
        p.setCategoria(categoria);
        return p;
    }

    public LiveData<List<VentaHistorial>> obtenerTodoElHistorial() { return db.detallePedidoDao().obtenerTodoElHistorial(); }
    public LiveData<List<DetalleConNombre>> obtenerDetalleCliente(int idCliente) { return db.detallePedidoDao().obtenerDetalleConNombres(idCliente); }

    public void guardarIntegrantesDuelo(List<Cliente> azul, List<Cliente> rojo) {
        this.integrantesAzulCacheados = new ArrayList<>(azul);
        this.integrantesRojoCacheados = new ArrayList<>(rojo);
        this.enModoDuelo.postValue(true);

        // 🔥 INTEGRACIÓN MÍNIMA: Si no hay UUID, lo generamos aquí mismo
        if (this.uuidDueloActual == null) {
            // Generamos un ID único para que la bolsa no quede NULL
            this.uuidDueloActual = UUID.randomUUID().toString();

            // Opcional: Si quieres que sea ultra seguro ante cierres,
            // búscalo en la DB antes de generar uno nuevo
            executorService.execute(() -> {
                String uuidExistente = db.dueloDao().obtenerUuidDueloActivo();
                if (uuidExistente != null) {
                    this.uuidDueloActual = uuidExistente;
                }
                // Refrescamos para que los observadores de la bolsa se despierten
                mainThreadHandler.post(() -> dbTrigger.setValue(System.currentTimeMillis()));
            });
        }
    }


    public void prepararDueloPoolMultiequipo(Map<Integer, Integer> seleccion, int idMesa) {
        final Map<Integer, Integer> copiaSeleccion = new HashMap<>(seleccion);

        this.enModoDuelo.postValue(true);
        this.mapaColoresDuelo.postValue(copiaSeleccion);
        this.uuidDueloActual = UUID.randomUUID().toString();
        this.tiempoInicioDuelo.postValue(System.currentTimeMillis());

        executorService.execute(() -> {
            try {
                db.dueloDao().borrarDueloFallido();

                for (Map.Entry<Integer, Integer> entry : copiaSeleccion.entrySet()) {
                    int idCliente = entry.getKey();
                    int colorAsignado = entry.getValue();

                    // 🔥 Pasamos el idMesa al constructor actualizado
                    DueloTemporal dt = new DueloTemporal(
                            uuidDueloActual,
                            colorAsignado,
                            idCliente,
                            "ACTIVO",
                            idMesa
                    );

                    db.dueloDao().insertarParticipante(dt);
                }

                mainThreadHandler.post(() -> dbTrigger.setValue(System.currentTimeMillis()));
            } catch (Exception e) {
                Log.e("DUELO_ERROR", "Error al insertar participantes", e);
            }
        });
    }

    public void aplicarDanioMultiequipo(int colorEquipoGanador) {
        // 1. Capturamos los datos actuales de forma segura
        final Map<Integer, Integer> participantesMapa = (mapaColoresDuelo.getValue() != null)
                ? new HashMap<>(mapaColoresDuelo.getValue()) : null;
        final String uuid = uuidDueloActual;

        if (participantesMapa == null || uuid == null) return;

        executorService.execute(() -> {
            // 2. Buscamos la munición lista (ENTREGADO e idCliente = 0)
            List<DetallePedido> bolsa = db.detallePedidoDao().obtenerDetallesMunicionSincrono(uuid);
            if (bolsa == null || bolsa.isEmpty()) return;

            // 3. IDENTIFICAR AFECTADOS (Aquí estaba la falla)
            List<Integer> afectados = new ArrayList<>();
            String regla = db.dueloDao().obtenerReglaCobroDuelo(uuid); // Leemos directo de la BD por seguridad

            if ("TODOS_PAGAN".equals(regla)) {
                // SI TODOS PAGAN, METEMOS A TODOS LOS IDS DEL MAPA SIN EXCEPCIÓN
                afectados.addAll(participantesMapa.keySet());
            } else {
                // REGLA NORMAL: El ganador se salva, los otros equipos pagan
                for (Map.Entry<Integer, Integer> entry : participantesMapa.entrySet()) {
                    if (entry.getValue() != colorEquipoGanador) {
                        afectados.add(entry.getKey());
                    }
                }
            }

            if (afectados.isEmpty()) return;

            BigDecimal divisor = new BigDecimal(afectados.size());
            String marcadorRelativo = obtenerMarcadorActualString();

            // 4. TRANSACCIÓN DE REPARTO
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
                    nuevo.estado = "REGISTRADO"; // <-- Estado final de cobro
                    nuevo.marcadorAlMomento = marcadorRelativo;
                    nuevo.fechaLong = System.currentTimeMillis();
                    db.detallePedidoDao().insertarDetalle(nuevo);
                }
                // 5. BORRAMOS EL BALÍN (El registro de la bolsa)
                db.detallePedidoDao().borrarDetallePorId(dp.idDetalle);
            }

            // 6. ACTUALIZACIÓN FINAL
            mainThreadHandler.post(() -> {
                actualizarPuntajeEquipoDinamico(colorEquipoGanador);
                dbTrigger.setValue(System.currentTimeMillis()); // Esto refresca las burbujas
            });
        });
    }

    /**
     * Mantiene el conteo de puntos por color de equipo
     */
    private void actualizarPuntajeEquipoDinamico(int color) {
        Map<Integer, Integer> scores = scoresEquipos.getValue();
        // Siempre crear una copia nueva para que LiveData dispare la notificación
        Map<Integer, Integer> copiaScores = (scores == null) ? new HashMap<>() : new HashMap<>(scores);

        int actual = copiaScores.containsKey(color) ? copiaScores.get(color) : 0;
        copiaScores.put(color, actual + 1);

        scoresEquipos.postValue(copiaScores);
    }

    public String obtenerMarcadorActualString() {
        Map<Integer, Integer> scores = scoresEquipos.getValue();
        Map<Integer, Integer> coloresParticipantes = mapaColoresDuelo.getValue(); // Para saber qué colores están activos

        if (scores == null || coloresParticipantes == null) return "0-0";

        StringBuilder sb = new StringBuilder();

        // Obtenemos los colores únicos que están participando en el duelo
        List<Integer> coloresActivos = new ArrayList<>();
        for (Integer color : coloresParticipantes.values()) {
            if (!coloresActivos.contains(color)) {
                coloresActivos.add(color);
            }
        }

        // Construimos la cadena recorriendo los colores activos
        for (int i = 0; i < coloresActivos.size(); i++) {
            int color = coloresActivos.get(i);
            int puntos = scores.containsKey(color) ? scores.get(color) : 0;

            sb.append(getNombreColor(color)).append(": ").append(puntos);

            if (i < coloresActivos.size() - 1) {
                sb.append(" | "); // Separador entre equipos
            }
        }

        return sb.toString();
    }

    /**
     * Función de apoyo para traducir el código de color a texto legible
     */
    private String getNombreColor(int color) {
        if (color == Color.parseColor("#00E5FF")) return "AZUL";
        if (color == Color.parseColor("#FF1744")) return "ROJO";
        if (color == Color.parseColor("#FFD54F")) return "AMAR";
        if (color == Color.parseColor("#4CAF50")) return "VERDE";
        if (color == Color.parseColor("#AA00FF")) return "MORAD";
        return "EQ";
    }

    private int obtenerIdConMenorPuntaje() {
        Map<Integer, Integer> scores = scoresEquipos.getValue();
        Map<Integer, Integer> participantes = mapaColoresDuelo.getValue();

        if (scores == null || participantes == null || participantes.isEmpty()) return -1;

        int colorPerdedor = -1;
        int minPuntos = Integer.MAX_VALUE;

        // Buscamos cuál es el color que tiene menos puntos registrados
        for (Integer color : new ArrayList<>(participantes.values())) {
            int pts = scores.containsKey(color) ? scores.get(color) : 0;
            if (pts < minPuntos) {
                minPuntos = pts;
                colorPerdedor = color;
            }
        }

        // Devolvemos el ID de uno de los clientes de ese bando (o podrías afectar a todos los de ese color)
        for (Map.Entry<Integer, Integer> entry : participantes.entrySet()) {
            if (entry.getValue() == colorPerdedor) return entry.getKey();
        }

        return -1;
    }

    public List<Integer> obtenerIdsParticipantesArena() {
        List<Integer> ids = new ArrayList<>();

        // 1. Agregar IDs de la Arena Individual (3 Bandas)
        if (integrantesAzulCacheados != null) {
            for (Cliente c : integrantesAzulCacheados) {
                ids.add(c.idCliente);
            }
        }

        // 2. Agregar IDs de la Arena Grupal (Mapa de Colores Pool)
        Map<Integer, Integer> mapa = mapaColoresDuelo.getValue();
        if (mapa != null) {
            for (Integer id : mapa.keySet()) {
                if (!ids.contains(id)) ids.add(id);
            }
        }

        return ids;
    }

    // En ProductoViewModel.java
    public String obtenerAliasCliente(int idCliente) {
        for (Cliente c : todosLosParticipantesDuelo) {
            if (c.idCliente == idCliente) return c.alias;
        }
        return "Jugador " + idCliente;
    }


    // Nuevo método para rehidratar los datos desde la BD al volver a la Arena
    public void recuperarDueloActivo() {
        executorService.execute(() -> {
            String uuid = db.dueloDao().obtenerUuidDueloActivo();
            if (uuid != null) {
                this.uuidDueloActual = uuid;

                // 1. Recuperamos los objetos Cliente reales para los nombres
                this.todosLosParticipantesDuelo = db.dueloDao().obtenerTodosLosParticipantesDuelo();

                // 2. Recuperamos la estructura de equipos (IDs y Colores)
                List<DueloTemporal> participantes = db.dueloDao().obtenerParticipantesSincrono();
                Map<Integer, Integer> mapaRecuperado = new HashMap<>();

                for (DueloTemporal dt : participantes) {
                    // dt.idEquipo ahora es el COLOR (int) que guardamos en la selección de POOL
                    mapaRecuperado.put(dt.idCliente, dt.idEquipo);
                }

                mainThreadHandler.post(() -> {
                    this.mapaColoresDuelo.setValue(mapaRecuperado);
                    this.enModoDuelo.setValue(true);
                    executorService.execute(() -> {
                        String reglaDB = db.dueloDao().obtenerReglaCobroDuelo(uuidDueloActual);
                        if (reglaDB != null) reglaCobroActiva.postValue(reglaDB);
                    });
                    this.dbTrigger.setValue(System.currentTimeMillis()); // 🔥 Forzar refresco de saldos
                });
            }
        });
    }

    // Función para obtener el saldo individual (LO QUE PEDISTE)
    public LiveData<BigDecimal> obtenerSaldoIndividualDuelo(int idCliente) {
        // Usamos switchMap para que cada vez que dbTrigger cambie, se recalculen los saldos
        return androidx.lifecycle.Transformations.switchMap(dbTrigger, trigger -> {
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

    /**
     * Observa en tiempo real cuántos pedidos faltan por entregar en una mesa.
     * Se usa para el Badge (notificación) de la esquina superior izquierda.
     */
    public LiveData<Integer> observarConteoPendientesMesa(int idMesa) {
        return db.detallePedidoDao().observarConteoPendientesMesa(idMesa);
    }


    /**
     * Obtiene solo los productos que ya han sido despachados (ENTREGADO).
     * Se usa para calcular el valor real de la "Bolsa" o apuesta acumulada en la UI.
     */
    public LiveData<List<Producto>> getListaApuestaEntregada() {
        return Transformations.switchMap(dbTrigger, trigger -> {
            MutableLiveData<List<Producto>> entregadosLiveData = new MutableLiveData<>();
            executorService.execute(() -> {
                if (uuidDueloActual != null) {
                    // Consulta al DAO que cruza DetallePedido con Producto filtrando por 'ENTREGADO'
                    List<Producto> lista = db.productoDao().obtenerProductosEntregadosDuelo(uuidDueloActual);
                    entregadosLiveData.postValue(lista != null ? lista : new ArrayList<>());
                }
            });
            return entregadosLiveData;
        });
    }

    public void marcarComoEntregado(int idDetalle, int idUsuario, String loginOperativo) {
        executorService.execute(() -> {
            // 1. Marcamos localmente como ENTREGADO (sincronizado sigue en 0 por defecto)
            db.detallePedidoDao().despacharPedidoLocal(idDetalle, "ENTREGADO", idUsuario);

            // 2. Encolamos el Worker para que revise todo lo que tenga sincronizado = 0
            dispararSincronizacion();

            mainThreadHandler.post(() -> dbTrigger.setValue(System.currentTimeMillis()));
        });
    }

    private void dispararSincronizacion() {
        Constraints restricciones = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        OneTimeWorkRequest syncRequest = new OneTimeWorkRequest.Builder(StockSyncWorker.class)
                .setConstraints(restricciones)
                .build();

        WorkManager.getInstance(getApplication()).enqueue(syncRequest);
    }

    public void despacharTodoLaMesa(int idMesa, int idUsuario, String loginOperativo) {
        executorService.execute(() -> {
            List<DetallePedido> pendientes = db.detallePedidoDao().obtenerPendientesMesaSincrono(idMesa);
            if (pendientes == null || pendientes.isEmpty()) return;

            // Actualizamos todos localmente
            for (DetallePedido dp : pendientes) {
                db.detallePedidoDao().despacharPedidoLocal(dp.idDetalle, "ENTREGADO", idUsuario);
            }

            // Un solo Worker se encargará de buscar todos los registros con 'sincronizado = 0'
            OneTimeWorkRequest syncRequest = new OneTimeWorkRequest.Builder(StockSyncWorker.class)
                    .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build();

            WorkManager.getInstance(getApplication()).enqueue(syncRequest);

            mainThreadHandler.post(() -> dbTrigger.setValue(System.currentTimeMillis()));
        });
    }

    public LiveData<List<DetalleHistorialDuelo>> obtenerSoloPendientesMesa(int idMesa) {
        return db.detallePedidoDao().obtenerSoloPendientesMesa(idMesa);
    }

    public void insertarMunicionDueloPendiente(int idMesa, Producto producto, int cantidad) {
        executorService.execute(() -> {
            // 1. Intentamos obtener el UUID de la variable en memoria
            String uuid = this.uuidDueloActual;

            // 2. Si es null (por reinicio), buscamos en la tabla de 3 BANDAS primero
            if (uuid == null) {
                // Buscamos en la tabla que mostraste en tu imagen (duelos_temporales_ind)
                // Necesitas agregar este Query en tu DueloTemporalIndDao si no lo tienes
                uuid = db.dueloTemporalIndDao().obtenerIdDueloPorMesaSincrono(idMesa);
            }

            // 3. Si sigue siendo null, buscamos en la tabla de POOL
            if (uuid == null) {
                uuid = db.dueloDao().obtenerUuidDueloActivo();
            }

            if (uuid == null) {
                mainThreadHandler.post(() -> Log.e("ERROR_VINCULO", "No se encontró un duelo activo para vincular el pedido"));
                return;
            }

            for (int i = 0; i < cantidad; i++) {
                DetallePedido dp = new DetallePedido();
                dp.idProducto = producto.idProducto;
                dp.idMesa = idMesa;
                dp.idDueloOrigen = uuid; // 🔥 AHORA SÍ TIENE EL ID DE TU IMAGEN
                dp.precioEnVenta = producto.getPrecioProducto();
                dp.cantidad = 1;
                dp.idCliente = 0; // Bolsa
                dp.estado = "PENDIENTE";
                dp.esApuesta = true;
                dp.fechaLong = System.currentTimeMillis();
                db.detallePedidoDao().insertarDetalle(dp);
            }

            mainThreadHandler.post(() -> dbTrigger.setValue(System.currentTimeMillis()));
        });
    }

    public void insertarConsumoDirectoEntregado(int idCliente, Producto producto, int cantidad) {
        executorService.execute(() -> {
            DetallePedido dp = new DetallePedido();
            dp.idCliente = idCliente;
            dp.idProducto = producto.idProducto;
            dp.cantidad = cantidad;
            dp.precioEnVenta = producto.getPrecioProducto();
            dp.estado = "ENTREGADO"; // ✅ Se entrega de una vez, no pasa por Badge
            dp.esApuesta = false;
            dp.fechaLong = System.currentTimeMillis();

            db.detallePedidoDao().insertarDetalle(dp);
            mainThreadHandler.post(() -> dbTrigger.setValue(System.currentTimeMillis()));
        });
    }

    /**
     * Cambia el estado de un producto de PENDIENTE a CANCELADO.
     * Esto hace que desaparezca del resumen del catálogo y del Badge de la arena.
     */
    public void eliminarDetallePendiente(int idDetalle) {
        executorService.execute(() -> {
            db.detallePedidoDao().cancelarDetallePorId(idDetalle);
            // Notificamos a la UI para que el resumen del catálogo y el Badge se refresquen
            mainThreadHandler.post(() -> dbTrigger.setValue(System.currentTimeMillis()));
        });
    }

    /**
     * Cancela toda la munición que aún no ha sido entregada para una mesa.
     */
    public void cancelarMunicionPendienteMesa(int idMesa) {
        executorService.execute(() -> {
            db.detallePedidoDao().cancelarTodosLosPendientesMesa(idMesa);
            // Notificamos a la UI para limpiar el resumen y poner el Badge en 0
            mainThreadHandler.post(() -> {
                dbTrigger.setValue(System.currentTimeMillis());
                Log.d("LOGISTICA", "Bolsa de mesa " + idMesa + " cancelada.");
            });
        });
    }

    // --- ProductoViewModel.java ---

    private List<Integer> identificarPerdedores(Map<Integer, Integer> participantesMapa, int colorEquipoGanador) {
        List<Integer> idsAfectados = new ArrayList<>();
        String regla = getReglaCobroDuelo().getValue();
        if (regla == null) regla = "GANADOR_SALVA";

        switch (regla) {
            case "TODOS_PAGAN":
                // 🔥 CORRECCIÓN: Aquí no importa quién ganó.
                // Agregamos ABSOLUTAMENTE TODOS los IDs del mapa.
                idsAfectados.addAll(participantesMapa.keySet());
                break;

            case "GANADOR_SALVA":
                // Solo los que NO son del color ganador
                for (Map.Entry<Integer, Integer> entry : participantesMapa.entrySet()) {
                    if (entry.getValue() != colorEquipoGanador) {
                        idsAfectados.add(entry.getKey());
                    }
                }
                break;

            case "ULTIMO_PAGA":
                // Por ahora lo dejamos igual o como fallback
                int idPeor = obtenerIdConMenorPuntaje();
                if (idPeor != -1) idsAfectados.add(idPeor);
                else idsAfectados.addAll(participantesMapa.keySet());
                break;
        }
        return idsAfectados;
    }

    // --- GESTIÓN DE SEGURIDAD Y CONFIGURACIÓN DEL DUELO ---

    /**
     * Observa si el duelo actual requiere PIN.
     * Se dispara automáticamente cuando cambia en la DB o mediante dbTrigger.
     */
    public LiveData<Boolean> getRequierePinDuelo() {
        return Transformations.switchMap(dbTrigger, trigger -> {
            MutableLiveData<Boolean> resultado = new MutableLiveData<>(true); // Default
            executorService.execute(() -> {
                if (uuidDueloActual != null) {
                    Boolean requiere = db.dueloDao().obtenerRequierePinDuelo(uuidDueloActual);
                    resultado.postValue(requiere != null ? requiere : true);
                }
            });
            return resultado;
        });
    }

    /**
     * Actualiza la preferencia de seguridad (PIN) en la base de datos para el duelo activo.
     */
    public void actualizarSeguridadPinDuelo(boolean requiere) {
        executorService.execute(() -> {
            if (uuidDueloActual != null) {
                db.dueloDao().actualizarSeguridadPinDuelo(uuidDueloActual, requiere);
                // Notificamos a la UI para que cualquier observador se refresque
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

    public void aplicarDanioUltimoPaga(int colorGanador, int colorPerdedor) {
        final String uuid = uuidDueloActual;
        final Map<Integer, Integer> participantesMapa = (mapaColoresDuelo.getValue() != null)
                ? new HashMap<>(mapaColoresDuelo.getValue()) : null;

        if (participantesMapa == null || uuid == null) return;

        executorService.execute(() -> {
            // 1. Obtener munición real de la bolsa (idCliente = 0 y estado ENTREGADO)
            List<DetallePedido> bolsa = db.detallePedidoDao().obtenerDetallesMunicionSincrono(uuid);

            // Si no hay productos en la bolsa, no hay nada que cobrar
            if (bolsa == null || bolsa.isEmpty()) return;

            // 2. Identificar a los clientes específicos que pertenecen al color del equipo perdedor
            List<Integer> idsAfectados = new ArrayList<>();
            for (Map.Entry<Integer, Integer> entry : participantesMapa.entrySet()) {
                if (entry.getValue() == colorPerdedor) {
                    idsAfectados.add(entry.getKey());
                }
            }

            // Si por alguna razón no hay clientes en ese equipo, abortamos
            if (idsAfectados.isEmpty()) return;

            // 3. Distribución de la deuda
            BigDecimal divisor = new BigDecimal(idsAfectados.size());
            String marcadorRelativo = obtenerMarcadorActualString();

            for (DetallePedido dp : bolsa) {
                // Dividimos el precio del producto entre los integrantes del equipo que perdió
                BigDecimal precioIndividual = dp.precioEnVenta.divide(divisor, 2, RoundingMode.HALF_UP);

                for (Integer idClientePerdedor : idsAfectados) {
                    // Creamos el nuevo registro de deuda vinculado al cliente
                    DetallePedido nuevoCobro = crearDetalleDeuda(idClientePerdedor, dp, precioIndividual, uuid, marcadorRelativo);
                    db.detallePedidoDao().insertarDetalle(nuevoCobro);
                }

                // 4. IMPORTANTE: Borramos el registro original de la bolsa para que no se duplique
                db.detallePedidoDao().borrarDetallePorId(dp.idDetalle);
            }

            // 5. Actualización de la Interfaz (Punto y Refresco)
            mainThreadHandler.post(() -> {
                // El punto se le suma al equipo que el Fragment marcó como Ganador (colorGanador)
                actualizarPuntajeEquipoDinamico(colorGanador);

                // Disparamos el gatillo para refrescar los saldos (burbujas) y vaciar el Badge de la bolsa
                dbTrigger.setValue(System.currentTimeMillis());
            });
        });
    }

    /**
     * Función auxiliar para estandarizar la creación de deudas en la DB.
     */
    private DetallePedido crearDetalleDeuda(int idCli, DetallePedido plantilla, BigDecimal precio, String uuid, String marcador) {
        DetallePedido nuevo = new DetallePedido();
        nuevo.idCliente = idCli;
        nuevo.idProducto = plantilla.idProducto;
        nuevo.idMesa = plantilla.idMesa;
        nuevo.idDueloOrigen = uuid;
        nuevo.cantidad = 1;
        nuevo.precioEnVenta = precio;
        nuevo.esApuesta = true;
        nuevo.estado = "REGISTRADO"; // Pasa directo a la cuenta del cliente
        nuevo.marcadorAlMomento = marcador;
        nuevo.fechaLong = System.currentTimeMillis();
        return nuevo;
    }

    // --- FUNCIONES PARA DUELO INDIVIDUAL (PERSISTENCIA DE PERFIL) ---

    /**
     * Actualiza o inserta el perfil de configuración para un duelo individual.
     * Guarda la meta de puntos y el nombre del nivel para la mesa actual.
     */
    public void actualizarPerfilDueloInd(int idMesa, int puntos, String nivel) {
        executorService.execute(() -> {
            try {
                // Creamos el perfil con la regla por defecto "PERDEDORES"
                PerfilDueloInd nuevoPerfil = new PerfilDueloInd(idMesa, puntos, nivel, "PERDEDORES");

                // Usamos el DAO directamente desde la instancia 'db'
                db.perfilDueloIndDao().insertarOActualizar(nuevoPerfil);

                // Notificamos a la UI para que los observadores de meta se actualicen
                mainThreadHandler.post(() -> {
                    dbTrigger.setValue(System.currentTimeMillis());
                    Log.d("PERFIL_IND", "Meta guardada: Mesa " + idMesa + " -> " + puntos + " pts (" + nivel + ")");
                });
            } catch (Exception e) {
                Log.e("PERFIL_IND_ERR", "Error al guardar perfil: " + e.getMessage());
            }
        });
    }

    /**
     * Obtiene el perfil de duelo individual configurado para una mesa específica.
     * Devuelve un LiveData para que la UI reaccione a cambios de nivel en tiempo real.
     */
    public LiveData<PerfilDueloInd> getPerfilDueloInd(int idMesa) {
        // Consultamos el perfil vinculado a la mesa actual
        return db.perfilDueloIndDao().obtenerPerfilPorMesa(idMesa);
    }

    /**
     * Agrega un nuevo jugador a un duelo individual ya iniciado (Reclutamiento en caliente).
     */
    public void agregarJugadorADueloIndActivo(int idMesa, Cliente nuevoCliente) {
        executorService.execute(() -> {
            // Usamos el idMesa para asegurar que se guarda en el duelo correcto
            DueloTemporalInd dueloInd = new DueloTemporalInd(
                    uuidDueloActual, idMesa, nuevoCliente.idCliente, 20, "PERDEDORES");

            db.dueloTemporalIndDao().insertarOActualizar(dueloInd);

            mainThreadHandler.post(() -> {
                if (!integrantesAzulCacheados.contains(nuevoCliente)) {
                    integrantesAzulCacheados.add(nuevoCliente);
                    dbTrigger.setValue(System.currentTimeMillis()); // Esto refresca la Arena
                }
            });
        });
    }

    public long obtenerTiempoTranscurridoInd(int idMesa, int idCliente) {
        // Para no saturar la DB cada segundo, lo ideal es que el Fragment
        // maneje los timestamps de inicio en un Map local que se llena al cargar
        return 0; // Se manejará con la lógica optimizada abajo
    }


    public long obtenerTiempoTranscurridoIndividual(int idMesa, int idCliente) {
        // 1. Buscamos en la base de datos el registro específico de este cliente en esta mesa
        // Nota: Para optimizar, podrías cargar esto en un Map al iniciar el Fragment
        DueloTemporalInd estado = db.dueloTemporalIndDao().obtenerEstadoCliente(idMesa, idCliente);

        if (estado == null || estado.timestampInicio == 0) return 0;

        // 2. Si el jugador NO está pausado, calculamos: Ahora - Inicio
        // (Si implementas pausas, tendrías que restar el tiempo que estuvo pausado)
        return System.currentTimeMillis() - estado.timestampInicio;
    }

    public long obtenerTimestampInicioPorCliente(int idMesa, int idCliente) {
        // Como Room no permite consultas en el hilo principal,
        // este método debe ser llamado desde un hilo de fondo.
        return db.dueloTemporalIndDao().obtenerTimestampInicioPorCliente(idMesa, idCliente);
    }

    public void insertarMunicionBolsaIndPendiente(int idMesa, Producto producto) {
        executorService.execute(() -> {
            if (this.uuidDueloActual == null) {
                this.uuidDueloActual = db.dueloDao().obtenerUuidDueloActivo();
            }
            DetallePedido dp = new DetallePedido();
            dp.idProducto = producto.idProducto;
            dp.idMesa = idMesa;
            dp.idCliente = 0; // 0 = Identificador de "La Bolsa"
            dp.idDueloOrigen = uuidDueloActual;
            dp.precioEnVenta = producto.getPrecioProducto();
            dp.cantidad = 1;
            dp.estado = "PENDIENTE"; // Activará el Badge de notificación
            dp.esApuesta = true;
            dp.fechaLong = System.currentTimeMillis();

            db.detallePedidoDao().insertarDetalle(dp);
            mainThreadHandler.post(() -> dbTrigger.setValue(System.currentTimeMillis()));
        });
    }

    // En ProductoViewModel.java

    public LiveData<List<Producto>> getBolsaIndEntregada() {
        return Transformations.switchMap(dbTrigger, trigger -> {
            MutableLiveData<List<Producto>> liveData = new MutableLiveData<>();
            executorService.execute(() -> {
                // Re-validamos el UUID en cada disparo del trigger
                String uuid = (uuidDueloActual != null) ? uuidDueloActual : db.dueloDao().obtenerUuidDueloActivo();

                if (uuid != null) {
                    List<Producto> lista = db.productoDao().obtenerProductosBolsaEntregados(uuid);
                    liveData.postValue(lista);
                } else {
                    // Si aún no hay duelo, devolvemos lista vacía para no romper la UI
                    liveData.postValue(new ArrayList<>());
                }
            });
            return liveData;
        });
    }

    public void ejecutarRepartoBolsaInd(int idClienteAnotador, List<Cliente> todosLosParticipantes) {
        executorService.execute(() -> {
            // 1. Buscamos SOLO lo que está ENTREGADO en la bolsa (idCliente = 0)
            List<DetallePedido> municionLista = db.detallePedidoDao().obtenerMunicionBolsaParaReparto(uuidDueloActual);

            if (municionLista == null || municionLista.isEmpty()) return;

            // 2. Identificar víctimas según la regla (PERDEDORES, TODOS, etc.)
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
                    nuevo.estado = "REGISTRADO"; // Pasa directo a la cuenta del cliente
                    nuevo.fechaLong = System.currentTimeMillis();
                    nuevo.marcadorAlMomento = "Impacto de: " + obtenerAliasCliente(idClienteAnotador);
                    db.detallePedidoDao().insertarDetalle(nuevo);
                }
                // 3. Eliminamos el rastro de la bolsa (El "balín" ya impactó)
                db.detallePedidoDao().borrarDetallePorId(item.idDetalle);
            }

            mainThreadHandler.post(() -> dbTrigger.setValue(System.currentTimeMillis()));
        });
    }

    private List<Integer> identificarIdsVictimasInd(int idClienteAnotador, List<Cliente> todosLosParticipantes) {
        List<Integer> victimas = new ArrayList<>();
        String regla = reglaPagoInd.getValue(); // "PERDEDORES", "TODOS", "ULTIMO"

        if (regla == null) regla = "PERDEDORES"; // Fallback de seguridad

        switch (regla) {
            case "PERDEDORES":
                // Todos menos el que hizo el punto
                for (Cliente c : todosLosParticipantes) {
                    if (c.idCliente != idClienteAnotador) {
                        victimas.add(c.idCliente);
                    }
                }
                break;

            case "TODOS":
                // Incluye al que hizo el punto (Reparto equitativo total)
                for (Cliente c : todosLosParticipantes) {
                    victimas.add(c.idCliente);
                }
                break;

            case "ULTIMO":
                // Buscamos quién tiene menos carambolas en el marcador local
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

                // Si encontramos al último, solo él paga.
                // Si hay empate o no hay scores, pagan todos los perdedores por defecto.
                if (idPeor != -1) {
                    victimas.add(idPeor);
                } else {
                    for (Cliente c : todosLosParticipantes) {
                        if (c.idCliente != idClienteAnotador) victimas.add(c.idCliente);
                    }
                }
                break;
        }
        return victimas;
    }

    public void confirmarEntregaBolsaInd(int idDetalle, int usuario) {
        executorService.execute(() -> {
            // Usamos el método despacharPedido que ya tienes en tu DAO
            db.detallePedidoDao().despacharPedido(idDetalle, "ENTREGADO", usuario);

            // Notificamos a la UI para que el Badge baje y la Bolsa suba
            mainThreadHandler.post(() -> dbTrigger.setValue(System.currentTimeMillis()));
        });
    }

    public LiveData<List<DetalleHistorialDuelo>> obtenerDetalleDeudaRegistrada(int idMesa) {
        return db.detallePedidoDao().obtenerDeudaPorMesa(idMesa);
    }

    public LiveData<List<DueloTemporalInd>> obtenerScoresDesdePersistencia(int idMesa) {
        // Consulta al DAO para obtener los registros de la mesa activa
        return db.dueloTemporalIndDao().obtenerScoresDesdePersistencia(idMesa);
    }

    // También es útil agregar este método para actualizar los marcadores manualmente desde el Fragment
    public void setScoresIndividualesManual(Map<Integer, Integer> nuevosScores) {
        this.scoresIndividualesInd.setValue(nuevosScores);
    }

    /**
     * Cruza la tabla de Hitos (DetalleDueloTemporalInd) con los productos repartidos
     * para generar la lista agrupada con subtotales que el Log necesita.
     */
    public LiveData<List<LogAgrupadoDTO>> obtenerLogAgrupado(int idMesa) {
        return Transformations.switchMap(dbTrigger, trigger -> {
            MutableLiveData<List<LogAgrupadoDTO>> resultado = new MutableLiveData<>();

            executorService.execute(() -> {
                // Si no hay UUID actual, no hay partida activa, devolvemos lista vacía
                if (uuidDueloActual == null) {
                    resultado.postValue(new ArrayList<>());
                    return;
                }

                // 1. Obtener hitos de la base de datos filtrados por el UUID activo
                List<DetalleDueloTemporalInd> hitos = db.detalleDueloTemporalIndDao()
                        .obtenerHistorialHitosSincrono(uuidDueloActual);

                // 2. Obtener los productos ya repartidos (Estado REGISTRADO)
                List<DetalleHistorialDuelo> todosLosProductos = db.detallePedidoDao()
                        .obtenerDeudaPorMesaIndSincrona(idMesa);

                List<LogAgrupadoDTO> listaAgrupada = new ArrayList<>();

                if (hitos != null) {
                    for (DetalleDueloTemporalInd hito : hitos) {
                        List<DetalleHistorialDuelo> productosDeEsteHito = new ArrayList<>();

                        // Usamos la clave única que generamos al repartir la bolsa
                        String claveBusqueda = "PT_" + hito.scoreGlobalAnotador + "_" + hito.aliasAnotador;

                        if (todosLosProductos != null) {
                            for (DetalleHistorialDuelo p : todosLosProductos) {
                                if (claveBusqueda.equals(p.marcadorAlMomento)) {
                                    productosDeEsteHito.add(p);
                                }
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

    public LiveData<List<DetalleHistorialDuelo>> obtenerDeudaPorMesaInd(int idMesa) {
        return db.detallePedidoDao().obtenerDeudaPorMesaInd(idMesa);
    }

    // En ProductoViewModel.java

    public void finalizarDueloIndividualMesa(int idMesa) {
        executorService.execute(() -> {
            // 1. Persistencia: Cambiamos estados en la DB a FINALIZADO
            db.dueloTemporalIndDao().finalizarDueloMesa(idMesa);

            // 2. Limpieza de UI
            mainThreadHandler.post(() -> {
                enModoDuelo.setValue(false);
                uuidDueloActual = null;
                integrantesAzulCacheados.clear(); // Limpiamos la lista de la Arena
                dbTrigger.setValue(System.currentTimeMillis());
            });
        });
    }

    public void retirarJugadorEspecificoInd(int idMesa, int idCliente) {
        executorService.execute(() -> {
            // Marcamos como finalizado en DB para que persista el registro
            db.dueloTemporalIndDao().finalizarJugadorIndividual(idMesa, idCliente);

            mainThreadHandler.post(() -> {
                // Buscamos y removemos de la lista en memoria (Caché)
                for (int i = 0; i < integrantesAzulCacheados.size(); i++) {
                    if (integrantesAzulCacheados.get(i).idCliente == idCliente) {
                        integrantesAzulCacheados.remove(i);
                        break;
                    }
                }
                dbTrigger.setValue(System.currentTimeMillis()); // Refresca la Arena
            });
        });
    }

    // En ProductoViewModel.java

    public void refrescarStockSilencioso(long empresaId) {
        // 1. No bloqueamos la UI con loadings.
        // 2. Pedimos al API los datos frescos.
        RetrofitClient.getInterface(getApplication()).obtenerProductosPorEmpresa(empresaId)
                .enqueue(new Callback<List<ProductoDTO>>() {
                    @Override
                    public void onResponse(Call<List<ProductoDTO>> call, Response<List<ProductoDTO>> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            executorService.execute(() -> {
                                // Actualizamos Room solo con los cambios
                                for (ProductoDTO dto : response.body()) {
                                    db.productoDao().actualizarStockYPrecio(
                                            dto.id.intValue(),
                                            dto.stockActual,
                                            dto.precioVenta
                                    );
                                }
                                // Disparamos el trigger para que el catálogo se refresque visualmente
                                mainThreadHandler.post(() -> dbTrigger.setValue(System.currentTimeMillis()));
                            });
                        }
                    }
                    @Override
                    public void onFailure(Call<List<ProductoDTO>> call, Throwable t) {
                        Log.e("SYNC", "Error silencioso: " + t.getMessage());
                    }
                });
    }

    public LiveData<List<Producto>> getProductosLiveData() {
        return db.productoDao().obtenerTodosProductosLiveData();
    }

}