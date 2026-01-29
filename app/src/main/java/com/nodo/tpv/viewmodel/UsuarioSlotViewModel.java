package com.nodo.tpv.viewmodel;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.nodo.tpv.data.database.AppDatabase;
import com.nodo.tpv.data.entities.LogSesion;
import com.nodo.tpv.data.entities.Usuario;
import com.nodo.tpv.data.entities.UsuarioSlot;
import com.nodo.tpv.data.sync.SessionSyncWorker;

import org.mindrot.jbcrypt.BCrypt;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UsuarioSlotViewModel extends AndroidViewModel {

    private final AppDatabase db;
    private final ExecutorService executorService = Executors.newFixedThreadPool(2);
    private final Handler mainThreadHandler = new Handler(Looper.getMainLooper());

    // Estados para observar desde los Fragmentos
    private final MutableLiveData<String> mensajeError = new MutableLiveData<>();
    private final MutableLiveData<Boolean> operacionExitosa = new MutableLiveData<>();

    public UsuarioSlotViewModel(@NonNull Application application) {
        super(application);
        db = AppDatabase.getInstance(application);
    }

    public LiveData<String> getMensajeError() { return mensajeError; }
    public LiveData<Boolean> getOperacionExitosa() { return operacionExitosa; }

    /**
     * Lógica de Validación Híbrida:
     * Compara el PIN ingresado contra el Hash local usando jBCrypt.
     */
    public void loginOperario(int idSlot, String login, String passwordPlana) {
        executorService.execute(() -> {
            try {
                // 1. Buscar usuario localmente
                Usuario usuario = db.usuarioDao().obtenerUsuarioPorLoginSincrono(login);

                if (usuario == null) {
                    mainThreadHandler.post(() -> mensajeError.setValue("Operario no encontrado en esta terminal"));
                    return;
                }

                if (usuario.passwordHash == null) {
                    mainThreadHandler.post(() -> mensajeError.setValue("Falta sincronización de seguridad para este usuario"));
                    return;
                }

                // 2. Verificación Offline (BCrypt)
                if (BCrypt.checkpw(passwordPlana, usuario.passwordHash)) {

                    // 3. Persistir estado del Slot
                    UsuarioSlot slot = new UsuarioSlot();
                    slot.idSlot = idSlot;
                    slot.idUsuario = usuario.idUsuario;
                    slot.loginUsuario = usuario.login;
                    slot.estado = "ACTIVO";
                    slot.lastAccessTimestamp = System.currentTimeMillis();

                    db.usuarioSlotDao().actualizarSlot(slot);

                    // 4. Registrar evento para el WorkManager
                    registrarEventoYProgramarSync(usuario.idUsuario, idSlot, "LOGIN");

                    mainThreadHandler.post(() -> operacionExitosa.setValue(true));

                } else {
                    mainThreadHandler.post(() -> mensajeError.setValue("Contraseña o PIN incorrecto"));
                }

            } catch (Exception e) {
                Log.e("AUTH_OFFLINE", "Error crítico en login", e);
                mainThreadHandler.post(() -> mensajeError.setValue("Error de validación: " + e.getMessage()));
            }
        });
    }

    /**
     * Cierra la sesión en el slot y prepara el log de auditoría.
     */
    public void logoutOperario(int idSlot) {
        executorService.execute(() -> {
            UsuarioSlot slotActual = db.usuarioSlotDao().obtenerSlot(idSlot);

            if (slotActual != null && "ACTIVO".equals(slotActual.estado)) {
                int idUsuario = slotActual.idUsuario;

                // 1. Liberar el slot localmente
                db.usuarioSlotDao().liberarSlot(idSlot);

                // 2. Registrar evento de salida
                registrarEventoYProgramarSync(idUsuario, idSlot, "LOGOUT");

                mainThreadHandler.post(() -> operacionExitosa.setValue(true));
            }
        });
    }

    /**
     * Crea el registro en Room y avisa al WorkManager que hay trabajo pendiente.
     */
    private void registrarEventoYProgramarSync(int idUsuario, int slot, String tipo) {
        LogSesion log = new LogSesion();
        log.idUsuario = idUsuario;
        log.slot = slot;
        log.tipoEvento = tipo;
        log.timestamp = System.currentTimeMillis();
        log.sincronizado = 0;

        db.usuarioSlotDao().insertarLogSesion(log);

        // Disparar sincronización inteligente
        Constraints restricciones = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();

        OneTimeWorkRequest syncRequest = new OneTimeWorkRequest.Builder(SessionSyncWorker.class)
                .setConstraints(restricciones)
                .addTag("session_sync_task")
                .build();

        WorkManager.getInstance(getApplication()).enqueue(syncRequest);
    }
}