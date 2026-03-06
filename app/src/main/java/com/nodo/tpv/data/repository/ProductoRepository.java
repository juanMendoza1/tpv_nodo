package com.nodo.tpv.data.repository;

import android.app.Application;
import android.util.Log;

import androidx.lifecycle.LiveData;

import com.nodo.tpv.data.api.ApiService;
import com.nodo.tpv.data.api.RetrofitClient;
import com.nodo.tpv.data.database.AppDatabase;
import com.nodo.tpv.data.dao.ProductoDao;
import com.nodo.tpv.data.dto.ProductoDTO;
import com.nodo.tpv.data.entities.Producto;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ProductoRepository {

    private final ProductoDao productoDao;
    private final ApiService apiService;
    private final ExecutorService executorService;

    public ProductoRepository(Application application) {
        // Inicializamos la BD y Retrofit AQUÍ, fuera del ViewModel
        AppDatabase db = AppDatabase.getInstance(application);
        this.productoDao = db.productoDao();
        this.apiService = RetrofitClient.getInterface(application);

        // El repositorio maneja sus propios hilos para operaciones de datos
        this.executorService = Executors.newFixedThreadPool(4);
    }

    // 1. Obtener LiveData directamente de Room
    public LiveData<List<Producto>> getProductosLiveData() {
        return productoDao.obtenerTodosProductosLiveData();
    }

    // 2. Operación asíncrona local (Insertar prueba)
    public void insertarProductosPrueba(Runnable onComplete) {
        executorService.execute(() -> {
            if (productoDao.getProductoCount() == 0) {
                // ... (Copia aquí tu lógica de crearProducto de prueba) ...
                // productoDao.insertarOActualizar(listaPrueba);
            }
            if (onComplete != null) onComplete.run();
        });
    }

    // 3. Sincronización con el servidor (Híbrido: Retrofit -> Room)
    public void refrescarStockSilencioso(long empresaId, Runnable onUpdateLocalSuccess) {
        apiService.obtenerProductosPorEmpresa(empresaId).enqueue(new Callback<List<ProductoDTO>>() {
            @Override
            public void onResponse(Call<List<ProductoDTO>> call, Response<List<ProductoDTO>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    executorService.execute(() -> {
                        // Guardamos lo que llega de la API en nuestra base de datos local (Room)
                        for (ProductoDTO dto : response.body()) {
                            productoDao.actualizarStockYPrecio(
                                    dto.id.intValue(),
                                    dto.stockActual,
                                    dto.precioVenta
                            );
                        }
                        // Notificamos al ViewModel que Room fue actualizado
                        if (onUpdateLocalSuccess != null) {
                            onUpdateLocalSuccess.run();
                        }
                    });
                }
            }

            @Override
            public void onFailure(Call<List<ProductoDTO>> call, Throwable t) {
                Log.e("SYNC", "Error silencioso: " + t.getMessage());
            }
        });
    }
}