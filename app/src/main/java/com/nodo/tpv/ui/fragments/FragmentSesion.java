package com.nodo.tpv.ui.fragments;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.provider.Settings;
import android.util.Log;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import com.nodo.tpv.R;
import com.nodo.tpv.data.api.RetrofitClient;
import com.nodo.tpv.data.database.AppDatabase;
import com.nodo.tpv.data.dto.ProductoDTO;
import com.nodo.tpv.data.entities.TerminalDispositivo;
import com.nodo.tpv.data.entities.Usuario;
import com.nodo.tpv.adapters.UsuarioSlotAdapter;
import com.nodo.tpv.util.SessionManager;
import com.nodo.tpv.viewmodel.UsuarioSlotViewModel;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class FragmentSesion extends Fragment {

    private static final String TAG = "TPV_SCANNER";
    private LinearLayout layoutLogin, layoutComandos, layoutActivacion;
    private MaterialCardView cardCamera;
    private PreviewView previewView;
    private CircularProgressIndicator loadingScanner;
    private TextInputEditText etPin;
    private TextInputLayout tilPin;
    private TextView tvUsuarioActivo;
    private Button btnLogin, btnSalir, btnEscanearQR;

    // Elementos para el listado de Slots
    private RecyclerView rvSlots;
    private UsuarioSlotAdapter adapter;
    private Usuario usuarioSeleccionado;

    private SessionManager sessionManager;
    private ExecutorService cameraExecutor;
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();
    private String androidId;
    private boolean isScanning = false;
    private ProcessCameraProvider cameraProvider;
    private BarcodeScanner qrScanner;

    private UsuarioSlotViewModel usuarioSlotViewModel;
    private int slotIdActual = 1;

    private CircularProgressIndicator loadingSlots;

    public interface OnSesionListener {
        void onLoginExitoso(Usuario usuario);
        void onLogout();
        void onComandoAbrirMesa(int idMesa, String tipoJuego);
        void onComandoAbrirMesa();
    }

    private OnSesionListener listener;

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        if (context instanceof OnSesionListener) listener = (OnSesionListener) context;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_sesion, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Inicialización de utilidades y ViewModels
        sessionManager = new SessionManager(requireContext());
        cameraExecutor = Executors.newSingleThreadExecutor();

        // NUEVO: Inicializar el UsuarioSlotViewModel
        usuarioSlotViewModel = new ViewModelProvider(this).get(UsuarioSlotViewModel.class);

        // 1. Vincular Vistas
        layoutActivacion = view.findViewById(R.id.layoutActivacion);
        layoutLogin = view.findViewById(R.id.layoutLogin);
        layoutComandos = view.findViewById(R.id.layoutComandos);
        cardCamera = view.findViewById(R.id.cardCamera);
        previewView = view.findViewById(R.id.previewView);
        loadingScanner = view.findViewById(R.id.loadingScanner);

        etPin = view.findViewById(R.id.etPinLogin);
        tilPin = view.findViewById(R.id.tilPin);
        tvUsuarioActivo = view.findViewById(R.id.tvUsuarioActivo);
        btnLogin = view.findViewById(R.id.btnLogin);
        btnSalir = view.findViewById(R.id.btnCerrarSesion);
        btnEscanearQR = view.findViewById(R.id.btnEscanearQR);
        rvSlots = view.findViewById(R.id.rvSlots);

        // 2. Configurar RecyclerView de Slots
        if (rvSlots != null) {
            rvSlots.setLayoutManager(new LinearLayoutManager(getContext()));
            adapter = new UsuarioSlotAdapter(usuario -> {
                usuarioSeleccionado = usuario;
                Log.d(TAG, "Usuario seleccionado: " + usuario.nombreUsuario);
                if (tilPin != null) tilPin.setVisibility(View.VISIBLE);
                if (btnLogin != null) btnLogin.setVisibility(View.VISIBLE);
                etPin.setText(""); // Limpiar PIN previo
                etPin.requestFocus();
            });
            rvSlots.setAdapter(adapter);
        }

        androidId = Settings.Secure.getString(requireContext().getContentResolver(), Settings.Secure.ANDROID_ID);

        loadingSlots = view.findViewById(R.id.loadingSlots);
        // 3. Scanner Setup
        BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build();
        qrScanner = BarcodeScanning.getClient(options);

        // 4. NUEVO: Observadores del ViewModel para Login Offline/Online
        configurarObservadoresViewModel();

        validarEstadoTerminal();

        // 5. Listeners de Botones
        btnEscanearQR.setOnClickListener(v -> checkCameraPermission());

        btnLogin.setOnClickListener(v -> {
            String pin = etPin.getText().toString();
            if (usuarioSeleccionado != null && !pin.isEmpty()) {
                // Se asume slotIdActual = 1 o el que maneje tu lógica de UI
                int slotId = 1;
                // Llamamos a la lógica híbrida del ViewModel
                usuarioSlotViewModel.loginOperario(slotId, usuarioSeleccionado.login, pin);
            } else {
                Toast.makeText(getContext(), "Seleccione un usuario e ingrese el PIN", Toast.LENGTH_SHORT).show();
            }
        });

        btnSalir.setOnClickListener(v -> logoutConfirm());
    }

    /**
     * NUEVO: Configura la escucha de eventos del UsuarioSlotViewModel
     */
    // En FragmentSesion.java

    private void configurarObservadoresViewModel() {
        usuarioSlotViewModel.getMensajeError().observe(getViewLifecycleOwner(), error -> {
            if (error != null) {
                Toast.makeText(getContext(), error, Toast.LENGTH_LONG).show();
            }
        });

        usuarioSlotViewModel.getOperacionExitosa().observe(getViewLifecycleOwner(), exito -> {
            if (exito != null && exito) {
                // 1. Informar al sistema que la sesión es válida
                if (usuarioSeleccionado != null) {
                    // Sincronizamos con el SessionManager para mantener compatibilidad
                    sessionManager.guardarUsuario(usuarioSeleccionado);

                    // 2. DISPARAR TU LÓGICA ORIGINAL
                    // Esto llamará a onLoginExitoso en MainActivity
                    if (listener != null) {
                        listener.onLoginExitoso(usuarioSeleccionado);
                    }

                    // 3. Limpiar estado visual
                    etPin.setText("");
                    if (tilPin != null) tilPin.setVisibility(View.GONE);
                    if (btnLogin != null) btnLogin.setVisibility(View.GONE);
                }
            }
        });
    }

    private void checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 101);
        }
    }

    private void startCamera() {
        cardCamera.setVisibility(View.VISIBLE);
        btnEscanearQR.setVisibility(View.GONE);
        loadingScanner.setVisibility(View.GONE);
        previewView.setAlpha(1.0f);
        isScanning = true;

        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext());
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setTargetResolution(new Size(1280, 720))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(cameraExecutor, this::procesarImagenQR);

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis);
            } catch (Exception e) {
                Log.e(TAG, "Error startCamera: " + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(requireContext()));
    }

    @SuppressLint("UnsafeOptInUsageError")
    private void procesarImagenQR(@NonNull ImageProxy imageProxy) {
        if (imageProxy.getImage() == null || !isScanning) {
            imageProxy.close();
            return;
        }

        InputImage image = InputImage.fromMediaImage(
                imageProxy.getImage(),
                imageProxy.getImageInfo().getRotationDegrees()
        );

        qrScanner.process(image)
                .addOnSuccessListener(barcodes -> {
                    if (barcodes.size() > 0 && isScanning) {
                        for (Barcode barcode : barcodes) {
                            String rawValue = barcode.getRawValue();
                            if (rawValue != null && !rawValue.isEmpty()) {
                                isScanning = false;
                                manejarResultadoQR(rawValue);
                                break;
                            }
                        }
                    }
                })
                .addOnFailureListener(e -> Log.e(TAG, "Error MLKit: " + e.getMessage()))
                .addOnCompleteListener(task -> imageProxy.close());
    }

    private void manejarResultadoQR(String rawValue) {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(() -> {
            vibrarDeteccion();
            loadingScanner.setVisibility(View.VISIBLE);
            previewView.setAlpha(0.4f);

            if (cameraProvider != null) cameraProvider.unbindAll();

            try {
                Long empresaId = 0L;
                String programaCod = rawValue;

                if (rawValue.contains("|")) {
                    String[] parts = rawValue.split("\\|");
                    empresaId = Long.parseLong(parts[0]);
                    programaCod = parts[1];
                }

                ejecutarActivacionTerminal(empresaId, programaCod);
            } catch (Exception e) {
                Log.e(TAG, "Error procesando código: " + e.getMessage());
                resetStateAfterError("Código QR no tiene el formato correcto");
            }
        });
    }

    private void vibrarDeteccion() {
        try {
            Vibrator vibrator = (Vibrator) requireContext().getSystemService(Context.VIBRATOR_SERVICE);
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    vibrator.vibrate(200);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error vibración: " + e.getMessage());
        }
    }

    private void ejecutarActivacionTerminal(Long idDelQR, String token) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("token", token);
        payload.put("uuidHardware", androidId);
        payload.put("marca", Build.MANUFACTURER);
        payload.put("modelo", Build.MODEL);
        // IMPORTANTE: Agregamos el alias porque el Backend lo pide con .toString()
        payload.put("alias", "Tablet " + Build.MODEL);

        Log.d(TAG, "Enviando activación... Token: " + token);

        RetrofitClient.getInterface(requireContext()).activarTerminal(payload)
                .enqueue(new Callback<Map<String, Object>>() {
                    @Override
                    public void onResponse(@NonNull Call<Map<String, Object>> call, @NonNull Response<Map<String, Object>> response) {
                        if (!isAdded()) return; // Siempre verifica si el fragmento sigue activo

                        if (response.isSuccessful() && response.body() != null) {
                            Map<String, Object> body = response.body();
                            long idReal = 0;

                            if (body.containsKey("empresaId")) {
                                Object val = body.get("empresaId");
                                if (val != null) {
                                    // GSON convierte números a Double en Maps, esto es seguro:
                                    idReal = Double.valueOf(val.toString()).longValue();
                                }
                            }

                            // Respaldo dinámico
                            if (idReal <= 0) idReal = idDelQR;

                            if (idReal > 0) {
                                Log.d(TAG, "Empresa vinculada: " + idReal);
                                sessionManager.guardarConfiguracionTerminal(androidId, idReal);
                                activarModoLogin();
                            } else {
                                resetStateAfterError("Error: El servidor no asignó una empresa.");
                            }
                        } else {
                            // Aquí capturamos el error 400/500 del servidor
                            Log.e(TAG, "Error en el servidor: " + response.code());
                            resetStateAfterError("Fallo en vinculación: " + response.code());
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<Map<String, Object>> call, @NonNull Throwable t) {
                        if (isAdded()) resetStateAfterError("Error de red: " + t.getMessage());
                    }
                });
    }

    private void cargarSlots() {
        long empresaId = sessionManager.getEmpresaId();
        if (empresaId <= 0) return;

        // 🔥 1. ACTIVAR LOADING ANTES DE LA PETICIÓN
        if (rvSlots != null) rvSlots.setVisibility(View.GONE);
        if (loadingSlots != null) loadingSlots.setVisibility(View.VISIBLE);

        RetrofitClient.getInterface(requireContext()).obtenerUsuariosPorEmpresa(empresaId)
                .enqueue(new Callback<List<Usuario>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<Usuario>> call, @NonNull Response<List<Usuario>> response) {
                        if (!isAdded()) return;

                        // 🔥 2. OCULTAR AL RECIBIR RESPUESTA (ÉXITO)
                        if (loadingSlots != null) loadingSlots.setVisibility(View.GONE);
                        if (rvSlots != null) rvSlots.setVisibility(View.VISIBLE);

                        if (response.isSuccessful() && response.body() != null) {
                            adapter.setUsuarios(response.body());
                            // ... guardar en Room
                        } else {
                            cargarSlotsDesdeDBLocal();
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<Usuario>> call, @NonNull Throwable t) {
                        if (!isAdded()) return;

                        // 🔥 3. OCULTAR AL RECIBIR FALLO (ERROR DE RED)
                        if (loadingSlots != null) loadingSlots.setVisibility(View.GONE);
                        if (rvSlots != null) rvSlots.setVisibility(View.VISIBLE);

                        Toast.makeText(getContext(), "Error de red: Cargando perfiles locales", Toast.LENGTH_SHORT).show();
                        cargarSlotsDesdeDBLocal();
                    }
                });
    }

    private void cargarSlotsDesdeDBLocal() {
        dbExecutor.execute(() -> {
            List<Usuario> locales = AppDatabase.getInstance(requireContext()).usuarioDao().obtenerTodos();
            if (getActivity() != null) {
                getActivity().runOnUiThread(() -> {
                    // 🔥 3. Aseguramos que la lista se vea tras la carga local
                    if (rvSlots != null) rvSlots.setVisibility(View.VISIBLE);

                    if (locales.isEmpty()) {
                        Toast.makeText(getContext(), "No hay operarios locales. Se requiere internet.", Toast.LENGTH_LONG).show();
                    } else {
                        adapter.setUsuarios(locales);
                    }
                });
            }
        });
    }

    private void procesarLoginConPin() {
        if (usuarioSeleccionado == null) return;
        String pin = etPin.getText().toString().trim();

        // 1. Obtenemos el ID de empresa que la tablet ya tiene guardado
        long empresaId = sessionManager.getEmpresaId();

        if (pin.isEmpty()) {
            Toast.makeText(getContext(), "Digite su PIN", Toast.LENGTH_SHORT).show();
            return;
        }

        // 2. Construimos el JSON con los parámetros de seguridad
        Map<String, Object> creds = new HashMap<>();
        creds.put("usuarioId", usuarioSeleccionado.idUsuario);
        creds.put("empresaId", empresaId);
        creds.put("pin", pin);

        RetrofitClient.getInterface(requireContext()).loginTablet(creds)
                .enqueue(new Callback<Map<String, String>>() {
                    @Override
                    public void onResponse(Call<Map<String, String>> call, Response<Map<String, String>> response) {
                        if (response.isSuccessful()) {
                            // 3. Guardamos el usuario en la sesión local
                            sessionManager.guardarUsuario(usuarioSeleccionado);

                            // 4. BLOQUE CRÍTICO: Antes de entrar, sincronizamos el catálogo real
                            Log.d(TAG, "Login exitoso. Iniciando descarga de productos para empresa: " + empresaId);
                            sincronizarCatalogo(empresaId);

                        } else {
                            String errorMsg = "Acceso Denegado";
                            try {
                                if (response.errorBody() != null) {
                                    errorMsg = response.errorBody().string();
                                }
                            } catch (IOException e) { e.printStackTrace(); }

                            Toast.makeText(getContext(), errorMsg, Toast.LENGTH_LONG).show();
                            etPin.setText("");
                        }
                    }

                    @Override
                    public void onFailure(Call<Map<String, String>> call, Throwable t) {
                        Toast.makeText(getContext(), "Error de red: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void resetStateAfterError(String msg) {
        Toast.makeText(getContext(), msg, Toast.LENGTH_LONG).show();
        btnEscanearQR.setVisibility(View.VISIBLE);
        cardCamera.setVisibility(View.GONE);
        isScanning = false;
    }

    private void validarEstadoTerminal() {
        if (!sessionManager.estaTerminalVinculada()) {
            mostrarModoActivacion();
            return;
        }

        // NO llamar a activarModoLogin() aquí todavía si queremos que sea 100% dinámico

        RetrofitClient.getInterface(requireContext()).validarTerminal(androidId).enqueue(new Callback<Void>() {
            @Override
            public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) {
                if (!isAdded()) return;

                if (response.isSuccessful()) {
                    // Si la validación es exitosa, ahora sí cargamos la interfaz
                    activarModoLogin();

                    Usuario user = sessionManager.obtenerUsuario();
                    if (user != null) activarModoComandos(user);
                } else {
                    // Si el servidor la rechazó (ej: la borraste del panel administrativo)
                    sessionManager.desvincularTerminalTotalmente();
                    mostrarModoActivacion();
                }
            }

            @Override
            public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) {
                if (!isAdded()) return;
                // Modo offline: Solo si falla la red, usamos lo que tenemos en memoria
                activarModoLogin();
                Usuario user = sessionManager.obtenerUsuario();
                if (user != null) activarModoComandos(user);
            }
        });
    }

    private void mostrarModoActivacion() {
        layoutActivacion.setVisibility(View.VISIBLE);
        layoutLogin.setVisibility(View.GONE);
        layoutComandos.setVisibility(View.GONE);
        cardCamera.setVisibility(View.GONE);
        btnEscanearQR.setVisibility(View.VISIBLE);
    }

    private void activarModoLogin() {
        layoutActivacion.setVisibility(View.GONE);
        layoutLogin.setVisibility(View.VISIBLE);
        layoutComandos.setVisibility(View.GONE);
        if (tilPin != null) tilPin.setVisibility(View.GONE);
        if (btnLogin != null) btnLogin.setVisibility(View.GONE);
        cargarSlots();
    }

    private void activarModoComandos(Usuario usuario) {
        layoutActivacion.setVisibility(View.GONE);
        layoutLogin.setVisibility(View.GONE);
        layoutComandos.setVisibility(View.VISIBLE);
        tvUsuarioActivo.setText(usuario.nombreUsuario);
    }

    private void logoutConfirm() {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Cerrar Sesión")
                .setMessage("¿Deseas finalizar el turno?")
                .setPositiveButton("Cerrar", (d, w) -> {
                    sessionManager.borrarSesion();
                    activarModoLogin();
                    if (listener != null) listener.onLogout();
                })
                .setNegativeButton("Cancelar", null).show();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) cameraExecutor.shutdown();
        dbExecutor.shutdown();
        if (qrScanner != null) qrScanner.close();
    }

    // Dentro de FragmentSesion.java

    private void sincronizarCatalogo(long empresaId) {
        // Capturamos el contexto de la aplicación para evitar fugas de memoria en hilos de fondo
        Context appContext = requireContext().getApplicationContext();

        // 1. Mostrar diálogo de carga
        MaterialAlertDialogBuilder loadingDialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Sincronizando")
                .setMessage("Descargando catálogo de productos...")
                .setCancelable(false);
        AlertDialog dialog = loadingDialog.show();

        RetrofitClient.getInterface(appContext).obtenerProductosPorEmpresa(empresaId)
                .enqueue(new Callback<List<ProductoDTO>>() {
                    @Override
                    public void onResponse(@NonNull Call<List<ProductoDTO>> call, @NonNull Response<List<ProductoDTO>> response) {
                        if (response.isSuccessful() && response.body() != null) {

                            // 2. Persistir en Room (Hilo de fondo) para no bloquear la UI
                            dbExecutor.execute(() -> {
                                try {
                                    AppDatabase db = AppDatabase.getInstance(appContext);
                                    List<com.nodo.tpv.data.entities.Producto> entidades = new ArrayList<>();

                                    for (ProductoDTO dto : response.body()) {
                                        com.nodo.tpv.data.entities.Producto p = new com.nodo.tpv.data.entities.Producto();

                                        // Mapeo exhaustivo
                                        p.idProducto = dto.id.intValue();
                                        p.nombreProducto = dto.nombre;
                                        p.precioProducto = dto.precioVenta;
                                        p.precioCosto = dto.precioCosto;
                                        p.stockActual = (dto.stockActual != null) ? dto.stockActual : 0;
                                        p.categoria = dto.categoriaNombre;

                                        entidades.add(p);
                                    }

                                    // Actualización masiva en la DB local
                                    db.productoDao().insertarOActualizar(entidades);

                                    // 3. Regresar al hilo principal para navegar
                                    if (getActivity() != null) {
                                        getActivity().runOnUiThread(() -> {
                                            if (dialog != null) dialog.dismiss();
                                            activarModoComandos(usuarioSeleccionado); // Cambiar UI local del fragment

                                            if (listener != null) {
                                                listener.onLoginExitoso(usuarioSeleccionado); // Navegar al menú principal
                                            }
                                        });
                                    }
                                } catch (Exception e) {
                                    Log.e("SYNC_ERR", "Error guardando productos: " + e.getMessage());
                                }
                            });
                        } else {
                            if (dialog != null) dialog.dismiss();
                            Toast.makeText(getContext(), "Error: " + response.code(), Toast.LENGTH_SHORT).show();
                            // Opcional: Permitir entrar aunque falle la sincronización si hay datos locales
                            if (listener != null) listener.onLoginExitoso(usuarioSeleccionado);
                        }
                    }

                    @Override
                    public void onFailure(@NonNull Call<List<ProductoDTO>> call, @NonNull Throwable t) {
                        if (dialog != null) dialog.dismiss();
                        Log.e("API_ERR", "Fallo al descargar productos", t);
                        Toast.makeText(getContext(), "Error de red: " + t.getMessage(), Toast.LENGTH_LONG).show();
                        // Modo offline: Permitir entrar si ya existen datos
                        if (listener != null) listener.onLoginExitoso(usuarioSeleccionado);
                    }
                });
    }


    private void ejecutarCierreSesion() {
        // Esto liberará el slot en Room y creará el Log de auditoría
        usuarioSlotViewModel.logoutOperario(slotIdActual);
    }

}