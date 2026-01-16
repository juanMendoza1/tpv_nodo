package com.nodo.tpv.data.database;


import android.app.Application;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.nodo.tpv.data.converters.BigDecimalConverter;
import com.nodo.tpv.data.dao.ClienteDao;
import com.nodo.tpv.data.dao.DetallePedidoDao;
import com.nodo.tpv.data.dao.DueloDao;
import com.nodo.tpv.data.dao.DueloTemporalIndDao;
import com.nodo.tpv.data.dao.MesaDao;
import com.nodo.tpv.data.dao.ProductoDao;
import com.nodo.tpv.data.dao.TipoClienteDao;
import com.nodo.tpv.data.dao.UsuarioDao;
import com.nodo.tpv.data.entities.Cliente;
import com.nodo.tpv.data.entities.DetallePedido;
import com.nodo.tpv.data.entities.DueloTemporal;
import com.nodo.tpv.data.entities.DueloTemporalInd;
import com.nodo.tpv.data.entities.Mesa;
import com.nodo.tpv.data.entities.Producto;
import com.nodo.tpv.data.entities.TipoCliente;
import com.nodo.tpv.data.entities.Usuario;
import com.nodo.tpv.data.entities.VentaDetalleHistorial;
import com.nodo.tpv.data.entities.VentaHistorial;

import java.util.concurrent.Executors;

@TypeConverters({BigDecimalConverter.class})
@Database(
        entities = {
                Usuario.class,
                TipoCliente.class,
                Cliente.class,
                Mesa.class,
                Producto.class,
                DetallePedido.class,
                VentaHistorial.class,
                VentaDetalleHistorial.class,
                DueloTemporal.class,
                DueloTemporalInd.class
        },
        version = 1,
        exportSchema = false
)
public abstract class AppDatabase extends RoomDatabase {
    private static AppDatabase INSTANCE;

    public abstract UsuarioDao usuarioDao();
    public abstract TipoClienteDao tipoClienteDao();
    public abstract ClienteDao clienteDao();
    public abstract MesaDao mesaDao();
    public abstract ProductoDao productoDao();
    public abstract DetallePedidoDao detallePedidoDao();

    public abstract DueloDao dueloDao();

    public abstract DueloTemporalIndDao dueloTemporalIndDao();



    public static synchronized AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            AppDatabase.class,
                            "billar_db"
                    ).fallbackToDestructiveMigration()
                    .allowMainThreadQueries()
                    .addCallback(new Callback() {
                        @Override
                        public void onCreate(@NonNull SupportSQLiteDatabase db) {
                            Executors.newSingleThreadExecutor().execute(() -> {
                                AppDatabase database = INSTANCE;

                                database.tipoClienteDao().insertar(
                                        new TipoCliente(1, "INDIVIDUAL")
                                );
                                database.tipoClienteDao().insertar(
                                        new TipoCliente(2, "GRUPO")
                                );
                            });
                        }
                    })
                    .build();
        }
        return INSTANCE;
    }
}