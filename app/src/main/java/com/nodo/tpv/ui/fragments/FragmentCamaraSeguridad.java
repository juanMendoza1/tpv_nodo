package com.nodo.tpv.ui.fragments;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.transition.TransitionManager;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.common.util.concurrent.ListenableFuture;
import com.nodo.tpv.R;
import com.nodo.tpv.viewmodel.ProductoViewModel;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.text.NumberFormat;
import java.util.Locale;

public class FragmentCamaraSeguridad extends Fragment {
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private PreviewView previewView;
    private ImageCapture imageCapture;
    private ProductoViewModel productoViewModel;

    private View layoutPreviewCamara, containerBotonesSeleccion, containerConfirmacionFoto, containerDatosPago;
    private ImageView ivFotoCapturada, ivCodigoQR;
    private TextView tvTotalMonto, tvTituloDatos, tvInfoBancaria;
    private FloatingActionButton btnCapture;
    private MaterialButton btnEfectivo, btnTransferencia, btnQR, btnConfirmarFinal, btnReintentar;
    private ImageButton btnCerrar;

    private Bitmap bitmapTemporal;
    private int idCliente;
    private String aliasCliente;
    private String metodoPagoActivo = "EFECTIVO";
    private BigDecimal montoTotal;

    public static FragmentCamaraSeguridad newInstance(int id, String alias, BigDecimal total) {
        FragmentCamaraSeguridad f = new FragmentCamaraSeguridad();
        Bundle a = new Bundle();
        a.putInt("id", id);
        a.putString("alias", alias);
        a.putSerializable("monto", total);
        f.setArguments(a);
        return f;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            idCliente = getArguments().getInt("id");
            aliasCliente = getArguments().getString("alias");
            montoTotal = (BigDecimal) getArguments().getSerializable("monto");
        }
        productoViewModel = new ViewModelProvider(requireActivity()).get(ProductoViewModel.class);

        // REQUERIMIENTO: Solo se puede salir con la X.
        // Bloqueamos el botón atrás del sistema.
        OnBackPressedCallback callback = new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                Toast.makeText(getContext(), "Use el botón (X) para salir", Toast.LENGTH_SHORT).show();
            }
        };
        requireActivity().getOnBackPressedDispatcher().addCallback(this, callback);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_camara_seguridad, container, false);

        // Vinculación de UI
        previewView = v.findViewById(R.id.viewFinder);
        layoutPreviewCamara = v.findViewById(R.id.layoutPreviewCamara);
        containerBotonesSeleccion = v.findViewById(R.id.containerBotonesSeleccion);
        containerConfirmacionFoto = v.findViewById(R.id.containerConfirmacionFoto);
        containerDatosPago = v.findViewById(R.id.containerDatosPago);

        ivFotoCapturada = v.findViewById(R.id.ivFotoCapturada);
        ivCodigoQR = v.findViewById(R.id.ivCodigoQR);
        tvTotalMonto = v.findViewById(R.id.tvTotalACobrar);
        tvTituloDatos = v.findViewById(R.id.tvTituloDatos);
        tvInfoBancaria = v.findViewById(R.id.tvInfoBancaria);

        btnEfectivo = v.findViewById(R.id.btnEfectivoCam);
        btnTransferencia = v.findViewById(R.id.btnTransferenciaCam);
        btnQR = v.findViewById(R.id.btnQRCam);
        btnCapture = v.findViewById(R.id.btnCapture);
        btnConfirmarFinal = v.findViewById(R.id.btnConfirmarFoto);
        btnReintentar = v.findViewById(R.id.btnReintentarFoto);
        btnCerrar = v.findViewById(R.id.btnCerrarFragment);

        if (montoTotal != null) {
            tvTotalMonto.setText(NumberFormat.getCurrencyInstance(new Locale("es", "CO")).format(montoTotal));
        }

        // --- LISTENERS ---

        // Única forma de salir del fragmento
        btnCerrar.setOnClickListener(view -> requireActivity().getSupportFragmentManager().popBackStack());

        btnEfectivo.setOnClickListener(view -> finalizarPago("EFECTIVO", ""));
        btnTransferencia.setOnClickListener(view -> configurarInterfazPago("TRANSFERENCIA"));
        btnQR.setOnClickListener(view -> configurarInterfazPago("QR_DIGITAL"));
        btnCapture.setOnClickListener(view -> capturePhoto());
        btnReintentar.setOnClickListener(view -> resetearCaptura());
        btnConfirmarFinal.setOnClickListener(view -> finalizarPago(metodoPagoActivo, bitmapToBase64(bitmapTemporal)));

        return v;
    }

    private void configurarInterfazPago(String metodo) {
        metodoPagoActivo = metodo;
        TransitionManager.beginDelayedTransition((ViewGroup) getView());

        layoutPreviewCamara.setVisibility(View.VISIBLE);
        containerBotonesSeleccion.setVisibility(View.GONE);
        containerDatosPago.setVisibility(View.VISIBLE);
        btnCapture.setVisibility(View.VISIBLE);

        if (metodo.equals("TRANSFERENCIA")) {
            tvTituloDatos.setText("DATOS PARA TRANSFERIR");
            tvTituloDatos.setTextColor(Color.parseColor("#1976D2"));
            String infoPro = "BANCO: NEQUI\nCUENTA: Ahorros\nNÚMERO: 310 123 4567\nTITULAR: Juan Mendoza\nCC: 1.234.567.890";
            tvInfoBancaria.setText(infoPro);
            tvInfoBancaria.setVisibility(View.VISIBLE);
            ivCodigoQR.setVisibility(View.GONE);
        } else {
            tvTituloDatos.setText("ESCANEE EL CÓDIGO QR");
            tvTituloDatos.setTextColor(Color.parseColor("#6A1B9A"));
            tvInfoBancaria.setVisibility(View.GONE);
            ivCodigoQR.setVisibility(View.VISIBLE);
            ivCodigoQR.setImageResource(R.drawable.image_qr_ok);
        }
        iniciarCamara();
    }

    private void iniciarCamara() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext());
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cp = cameraProviderFuture.get();
                bindPreview(cp);
            } catch (Exception e) { Log.e("CAM", "Error hardware"); }
        }, ContextCompat.getMainExecutor(requireContext()));
    }

    private void bindPreview(ProcessCameraProvider cp) {
        Preview p = new Preview.Builder().build();
        CameraSelector s = new CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build();
        p.setSurfaceProvider(previewView.getSurfaceProvider());
        imageCapture = new ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build();
        cp.unbindAll();
        cp.bindToLifecycle(this, s, p, imageCapture);
    }

    private void capturePhoto() {
        if (imageCapture == null) return;
        imageCapture.takePicture(ContextCompat.getMainExecutor(requireContext()), new ImageCapture.OnImageCapturedCallback() {
            @Override
            public void onCaptureSuccess(@NonNull ImageProxy image) {
                bitmapTemporal = imageProxyToBitmap(image);
                image.close();
                requireActivity().runOnUiThread(() -> {
                    ivFotoCapturada.setImageBitmap(bitmapTemporal);
                    ivFotoCapturada.setVisibility(View.VISIBLE);
                    previewView.setVisibility(View.GONE);
                    btnCapture.setVisibility(View.GONE);
                    containerConfirmacionFoto.setVisibility(View.VISIBLE);
                });
            }
        });
    }

    private void finalizarPago(String metodo, String foto) {
        productoViewModel.finalizarCuenta(idCliente, aliasCliente, metodo, foto);
        requireActivity().getSupportFragmentManager().popBackStack();
    }

    private void resetearCaptura() {
        TransitionManager.beginDelayedTransition((ViewGroup) getView());
        bitmapTemporal = null;
        layoutPreviewCamara.setVisibility(View.INVISIBLE);
        ivFotoCapturada.setVisibility(View.GONE);
        previewView.setVisibility(View.VISIBLE);
        containerDatosPago.setVisibility(View.GONE);
        containerConfirmacionFoto.setVisibility(View.GONE);
        containerBotonesSeleccion.setVisibility(View.VISIBLE);
        btnCapture.setVisibility(View.GONE);
        if (cameraProviderFuture != null) {
            try { cameraProviderFuture.get().unbindAll(); } catch (Exception e) {}
        }
    }

    private Bitmap imageProxyToBitmap(ImageProxy image) {
        ByteBuffer b = image.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[b.remaining()];
        b.get(bytes);
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
    }

    private String bitmapToBase64(Bitmap b) {
        if (b == null) return "";
        ByteArrayOutputStream s = new ByteArrayOutputStream();
        b.compress(Bitmap.CompressFormat.JPEG, 60, s);
        return Base64.encodeToString(s.toByteArray(), Base64.NO_WRAP);
    }
}