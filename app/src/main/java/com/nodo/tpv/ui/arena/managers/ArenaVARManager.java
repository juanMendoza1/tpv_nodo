package com.nodo.tpv.ui.arena.managers;

import android.content.Context;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.work.Constraints;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.nodo.tpv.data.database.AppDatabase;
import com.nodo.tpv.data.entities.ActividadOperativaLocal;
import com.nodo.tpv.data.entities.BolaAnotada;
import com.nodo.tpv.data.sync.OperatividadSyncWorker;

import java.util.concurrent.Executors;

public class ArenaVARManager {

    private final AppDatabase db;
    private final Context context;
    private final ConstraintLayout rootLayout;
    private final WebView webViewCamara;
    private final View scrollMarcadores;

    private boolean modoVARActivo = false;

    // --- CONSTRUCTOR 1: Para el Fragmento (Con UI) ---
    public ArenaVARManager(AppDatabase db, Context context, ConstraintLayout rootLayout, WebView webViewCamara, View scrollMarcadores) {
        this.db = db;
        this.context = context;
        this.rootLayout = rootLayout;
        this.webViewCamara = webViewCamara;
        this.scrollMarcadores = scrollMarcadores;
    }

    // 🔥 CONSTRUCTOR 2 NUEVO: Para el ViewModel (Solo Datos/Sync)
    public ArenaVARManager(AppDatabase db, Context context) {
        this.db = db;
        this.context = context;
        this.rootLayout = null;
        this.webViewCamara = null;
        this.scrollMarcadores = null;
    }

    public void registrarFalta(int idMesa, int idCliente, int colorEquipo, String tipoFalta, String nota) {
        Executors.newSingleThreadExecutor().execute(() -> {
            String uuidDuelo = db.dueloDao().obtenerUuidDueloActivoPorMesa(idMesa);
            if (uuidDuelo != null) {
                // Registro local en Room
                BolaAnotada bola = new BolaAnotada(uuidDuelo, colorEquipo, -1);
                db.bolaDueloDao().insertarBola(bola);
            }

            // Registro en Cola de Sincronización Universal
            ActividadOperativaLocal pendiente = new ActividadOperativaLocal();
            pendiente.eventoId = java.util.UUID.randomUUID().toString();
            pendiente.tipoEvento = "FALTA_REGISTRADA";
            pendiente.fechaDispositivo = System.currentTimeMillis();
            pendiente.estadoSync = "PENDIENTE";
            pendiente.detallesJson = "{ \"idMesa\": " + idMesa + ", \"idCliente\": " + idCliente + ", \"colorEquipo\": " + colorEquipo + ", \"tipoFalta\": \"" + tipoFalta + "\", \"notaVAR\": \"" + nota + "\" }";

            db.actividadOperativaLocalDao().insertar(pendiente);
            dispararSincronizacion();
        });
    }

    private void dispararSincronizacion() {
        Constraints constraints = new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
        OneTimeWorkRequest syncRequest = new OneTimeWorkRequest.Builder(OperatividadSyncWorker.class)
                .setConstraints(constraints).build();
        WorkManager.getInstance(context).enqueueUniqueWork("SyncOperatividadInmediata", ExistingWorkPolicy.KEEP, syncRequest);
    }

    // Métodos visuales (Solo funcionan si se usó el Constructor 1)
    public void configurarWebView() {
        if (webViewCamara == null) return;
        WebSettings settings = webViewCamara.getSettings();
        settings.setJavaScriptEnabled(true);
        webViewCamara.setWebViewClient(new WebViewClient());
        webViewCamara.loadUrl("about:blank");
    }

    public void toggleModoVAR() {
        if (webViewCamara == null || scrollMarcadores == null) return;
        modoVARActivo = !modoVARActivo;
        webViewCamara.setVisibility(modoVARActivo ? View.VISIBLE : View.GONE);
        scrollMarcadores.setVisibility(modoVARActivo ? View.GONE : View.VISIBLE);
    }

    public void onStop() { if (webViewCamara != null) webViewCamara.onPause(); }
    public void onResume() { if (webViewCamara != null) webViewCamara.onResume(); }
}