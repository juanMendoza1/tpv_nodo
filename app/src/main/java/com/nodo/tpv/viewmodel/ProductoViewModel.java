package com.nodo.tpv.viewmodel;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.nodo.tpv.data.database.AppDatabase;
import com.nodo.tpv.data.entities.Producto;
import com.nodo.tpv.data.repository.ProductoRepository;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProductoViewModel extends AndroidViewModel {

    private final AppDatabase db;
    private final ProductoRepository productoRepository;
    private final ExecutorService executorService = Executors.newFixedThreadPool(2);
    private final MutableLiveData<List<Producto>> productosResultados = new MutableLiveData<>();

    public ProductoViewModel(@NonNull Application application) {
        super(application);
        // Instanciamos la base de datos directa para las búsquedas locales
        db = AppDatabase.getInstance(application);
        productoRepository = new ProductoRepository(application);
    }

    // --- GETTERS ---
    public LiveData<List<Producto>> getProductosResultados() {
        return productosResultados;
    }

    public LiveData<List<Producto>> getProductosLiveData() {
        return productoRepository.getProductosLiveData();
    }

    // --- ACCIONES ---
    public void cargarTodosLosProductos() {
        // Consultamos directo al DAO para evitar errores en el Repositorio
        executorService.execute(() -> {
            List<Producto> lista = db.productoDao().obtenerTodosProductos();
            productosResultados.postValue(lista);
        });
    }

    public void buscarProducto(String query) {
        // Consultamos directo al DAO
        executorService.execute(() -> {
            List<Producto> resultados = db.productoDao().buscarProducto("%" + query + "%");
            productosResultados.postValue(resultados);
        });
    }

    public void refrescarStockSilencioso(long empresaId) {
        productoRepository.refrescarStockSilencioso(empresaId, () -> {
            // Notificar a la UI si es necesario
        });
    }
}