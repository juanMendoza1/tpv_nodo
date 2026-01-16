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

import com.nodo.tpv.data.database.AppDatabase;
import com.nodo.tpv.data.dto.DetalleConNombre;
import com.nodo.tpv.data.dto.DetalleHistorialDuelo;
import com.nodo.tpv.data.entities.Cliente;
import com.nodo.tpv.data.entities.DetallePedido;
import com.nodo.tpv.data.entities.DueloTemporal;
import com.nodo.tpv.data.entities.DueloTemporalInd;
import com.nodo.tpv.data.entities.Producto;
import com.nodo.tpv.data.entities.VentaDetalleHistorial;
import com.nodo.tpv.data.entities.VentaHistorial;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
        this.integrantesAzulCacheados = new ArrayList<>(clientes);
        this.tipoJuegoActual.postValue("3BANDAS");
        this.enModoDuelo.postValue(true);
        this.scoresIndividualesInd.postValue(new HashMap<>());
        this.tiempoInicioDuelo.postValue(System.currentTimeMillis());

        executorService.execute(() -> {
            uuidDueloActual = UUID.randomUUID().toString();
            for (Cliente c : clientes) {
                DueloTemporalInd dueloInd = new DueloTemporalInd(
                        uuidDueloActual, idMesa, c.idCliente,
                        metaCarambolasInd.getValue(), reglaPagoInd.getValue());
                db.dueloTemporalIndDao().insertarOActualizar(dueloInd);
            }
            mainThreadHandler.post(() -> dbTrigger.setValue(System.currentTimeMillis()));
        });
    }

    public void aplicarDanioInd(int idClienteAnotador, List<Cliente> todosLosClientes) {
        List<Producto> apuesta = listaApuesta.getValue();
        if (apuesta == null || apuesta.isEmpty() || uuidDueloActual == null) return;

        Map<Integer, Integer> scores = scoresIndividualesInd.getValue();
        if (scores == null) scores = new HashMap<>();
        int nuevoScore = (scores.containsKey(idClienteAnotador) ? scores.get(idClienteAnotador) : 0) + 1;
        scores.put(idClienteAnotador, nuevoScore);
        scoresIndividualesInd.postValue(scores);

        List<Cliente> victimas = new ArrayList<>();
        String regla = reglaPagoInd.getValue();

        if ("PERDEDORES".equals(regla)) {
            for (Cliente c : todosLosClientes) if (c.idCliente != idClienteAnotador) victimas.add(c);
        } else if ("TODOS".equals(regla)) {
            victimas.addAll(todosLosClientes);
        }

        if (!victimas.isEmpty()) registrarConsumoDistribuido(victimas, apuesta, "Punto Cliente: " + idClienteAnotador);

        executorService.execute(() -> db.dueloTemporalIndDao().actualizarScore(0, idClienteAnotador, nuevoScore));
        limpiarApuesta();
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
            DetallePedido dp = new DetallePedido();
            dp.idCliente = idCliente;
            dp.idProducto = producto.idProducto;
            dp.cantidad = cantidad;
            dp.precioEnVenta = producto.getPrecioProducto();
            dp.esApuesta = false;
            dp.fechaLong = System.currentTimeMillis();
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
    public void finalizarCuenta(int id, String alias, String metodo, String foto) {
        executorService.execute(() -> {
            BigDecimal total = db.detallePedidoDao().obtenerTotalDirecto(id);
            List<DetalleConNombre> consumos = db.detallePedidoDao().obtenerDetalleConNombresSincrono(id);

            if (total != null && total.compareTo(BigDecimal.ZERO) > 0) {
                VentaHistorial vH = new VentaHistorial();
                vH.idCliente = id; vH.nombreCliente = alias; vH.montoTotal = total;
                vH.metodoPago = metodo; vH.fechaLong = System.currentTimeMillis();
                vH.estado = "COMPLETADO"; vH.fotoComprobante = foto;

                long idV = db.detallePedidoDao().insertarVentaHistorial(vH);
                List<VentaDetalleHistorial> listaDetalles = new ArrayList<>();
                for (DetalleConNombre it : consumos) {
                    VentaDetalleHistorial dH = new VentaDetalleHistorial();
                    dH.idVentaPadre = (int) idV;
                    dH.nombreProducto = it.nombreProducto;
                    dH.cantidad = it.detallePedido.cantidad;
                    dH.precioUnitario = it.detallePedido.precioEnVenta;
                    dH.esApuesta = it.detallePedido.esApuesta;
                    listaDetalles.add(dH);
                }
                db.detallePedidoDao().insertarDetallesHistorial(listaDetalles);
            }
            db.detallePedidoDao().borrarCuentaCliente(id);
            db.clienteDao().eliminarPorId(id);
            mainThreadHandler.post(() -> dbTrigger.setValue(System.currentTimeMillis()));
        });
    }

    public void obtenerDetallesTicket(int idVenta, OnDetallesCargadosListener listener) {
        executorService.execute(() -> {
            List<VentaDetalleHistorial> detalles = db.detallePedidoDao().obtenerDetallesDeVentaSincrono(idVenta);
            mainThreadHandler.post(() -> { if (listener != null) listener.onCargados(detalles); });
        });
    }

    // --- GESTIÓN DE LA ARENA ---

    public void iniciarDueloPersistente(List<Cliente> azul, List<Cliente> rojo, String tipo) {
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
    }

    public void finalizarDueloCompleto() {
        executorService.execute(() -> {
            // 1. Limpieza en Base de Datos
            db.dueloDao().finalizarDueloActual();
            db.dueloTemporalIndDao().finalizarDueloMesa(0);

            mainThreadHandler.post(() -> {
                // 2. Control de Estado General
                enModoDuelo.setValue(false);
                uuidDueloActual = null;
                tiempoInicioDuelo.setValue(0L);

                // 3. Limpieza de Arena Dinámica (POOL MULTIEQUIPO)
                // Es vital limpiar estos para desbloquear los botones de pago en la lista
                mapaColoresDuelo.setValue(new HashMap<>());
                scoresEquipos.setValue(new HashMap<>());

                // 4. Limpieza de Arena Clásica (Compatibilidad)
                scoreAzul.setValue(0);
                scoreRojo.setValue(0);
                integrantesAzulCacheados.clear();
                integrantesRojoCacheados.clear();

                // 5. Limpieza de Arena 3 BANDAS
                scoresIndividualesInd.setValue(new HashMap<>());

                // 6. Limpieza de Bolsa
                limpiarApuesta();

                // 7. Notificar a la UI para refrescar todo
                dbTrigger.setValue(System.currentTimeMillis());

                Log.d("ARENA", "Duelo finalizado y estados reseteados.");
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
    }


    public void prepararDueloPoolMultiequipo(Map<Integer, Integer> seleccion) {
        // 1. Crear una COPIA del mapa inmediatamente para evitar el error de concurrencia
        final Map<Integer, Integer> copiaSeleccion = new HashMap<>(seleccion);

        this.enModoDuelo.postValue(true);
        this.mapaColoresDuelo.postValue(copiaSeleccion); // Usamos la copia
        this.uuidDueloActual = UUID.randomUUID().toString();
        this.tiempoInicioDuelo.postValue(System.currentTimeMillis());

        executorService.execute(() -> {
            try {
                db.dueloDao().borrarDueloFallido();

                // 2. Recorremos la COPIA, que nadie más puede modificar
                for (Map.Entry<Integer, Integer> entry : copiaSeleccion.entrySet()) {
                    int idCliente = entry.getKey();
                    int colorAsignado = entry.getValue();

                    DueloTemporal dt = new DueloTemporal(
                            uuidDueloActual,
                            colorAsignado,
                            idCliente,
                            "ACTIVO"
                    );
                    db.dueloDao().insertarParticipante(dt);
                }

                mainThreadHandler.post(() -> dbTrigger.setValue(System.currentTimeMillis()));
            } catch (Exception e) {
                Log.e("DUELO_ERROR", "Error al insertar participantes", e);
            }
        });
    }

    public void setReglaCobroBD(int idMesa, String seleccion) {
        this.reglaCobroActiva.postValue(seleccion);
        executorService.execute(() -> {
            // Esto busca en tu MesaDao el método que creamos anteriormente
            db.mesaDao().actualizarReglaDuelo(idMesa, seleccion);
            Log.d("ARENA", "Regla persistida en BD para mesa: " + idMesa);
        });
    }

    public void aplicarDanioMultiequipo(int colorEquipoGanador) {
        List<Producto> productosBolsa = listaApuesta.getValue();
        Map<Integer, Integer> participantesMapa = mapaColoresDuelo.getValue();
        String regla = reglaCobroActiva.getValue() != null ? reglaCobroActiva.getValue() : "GANADOR_SALVA";

        if (productosBolsa == null || productosBolsa.isEmpty() || participantesMapa == null || uuidDueloActual == null) {
            return;
        }

        executorService.execute(() -> {
            // 1. Identificar quiénes pagan
            List<Integer> idsAfectados = new ArrayList<>();
            if ("GANADOR_SALVA".equals(regla)) {
                for (Map.Entry<Integer, Integer> entry : participantesMapa.entrySet()) {
                    if (entry.getValue() != colorEquipoGanador) {
                        idsAfectados.add(entry.getKey());
                    }
                }
            } else if ("TODOS_PAGAN".equals(regla)) {
                idsAfectados.addAll(participantesMapa.keySet());
            } else if ("ULTIMO_PAGA".equals(regla)) {
                // Buscamos al ID con menos puntos en el mapa de scores actual
                int idPeor = obtenerIdConMenorPuntaje();
                if (idPeor != -1) idsAfectados.add(idPeor);
            }

            if (idsAfectados.isEmpty()) return;

            // 2. Calcular y Registrar Daño
            BigDecimal totalBolsa = BigDecimal.ZERO;
            for (Producto p : productosBolsa) totalBolsa = totalBolsa.add(p.getPrecioProducto());

            // Cuota individual = Total / número de personas que pagan
            BigDecimal cuotaPersona = totalBolsa.divide(new BigDecimal(idsAfectados.size()), 2, RoundingMode.HALF_UP);

            for (Integer idCliente : idsAfectados) {
                // Registramos el detalle para cada cliente afectado
                for (Producto p : productosBolsa) {
                    // El precio en venta es la parte proporcional de ese producto
                    BigDecimal precioProporcional = p.getPrecioProducto().divide(new BigDecimal(idsAfectados.size()), 2, RoundingMode.HALF_UP);

                    DetallePedido dp = new DetallePedido();
                    dp.idCliente = idCliente;
                    dp.idProducto = p.idProducto;
                    dp.cantidad = 1;
                    dp.precioEnVenta = precioProporcional;
                    dp.esApuesta = true;
                    dp.idDueloOrigen = uuidDueloActual;
                    dp.marcadorAlMomento = obtenerMarcadorActualString(); // Función para generar el texto "1-0-2"
                    dp.fechaLong = System.currentTimeMillis();

                    db.detallePedidoDao().insertarDetalle(dp);
                }
            }

            // 3. Notificar cambios a la UI
            mainThreadHandler.post(() -> {
                listaApuesta.setValue(new ArrayList<>()); // Limpiar bolsa
                actualizarPuntajeEquipoDinamico(colorEquipoGanador); // Subir marcador
                dbTrigger.setValue(System.currentTimeMillis()); // Refrescar saldos en pantalla
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
        Map<Integer, Integer> mapa = mapaColoresDuelo.getValue();
        if (mapa != null) {
            return new ArrayList<>(mapa.keySet());
        }
        return new ArrayList<>();
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

}