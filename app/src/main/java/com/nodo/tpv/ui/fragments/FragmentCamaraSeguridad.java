package com.nodo.tpv.ui.fragments;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.camera.core.ImageCaptureException;

import android.util.Base64;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.common.util.concurrent.ListenableFuture;
import com.nodo.tpv.R;
import com.nodo.tpv.ui.main.MainActivity;
import com.nodo.tpv.viewmodel.ProductoViewModel;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;


public class FragmentCamaraSeguridad extends Fragment {
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private PreviewView previewView;
    private ImageCapture imageCapture;
    private ProductoViewModel productoViewModel;

    // Vistas para el intercambio
    private View layoutPreviewCamara, layoutConfirmacionFoto;
    private ImageView ivFotoCapturada;
    private Bitmap bitmapTemporal;

    // Datos temporales del pago
    private int idCliente;
    private String aliasCliente;
    private String metodoPago;

    public FragmentCamaraSeguridad() {
        // Required empty public constructor
    }

    public static FragmentCamaraSeguridad newInstance(int id, String alias, String metodo) {
        FragmentCamaraSeguridad fragment = new FragmentCamaraSeguridad();
        Bundle args = new Bundle();
        args.putInt("idCliente", id);
        args.putString("alias", alias);
        args.putString("metodo", metodo);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            idCliente = getArguments().getInt("idCliente");
            aliasCliente = getArguments().getString("alias");
            metodoPago = getArguments().getString("metodo");
        }
        productoViewModel = new ViewModelProvider(requireActivity()).get(ProductoViewModel.class);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.fragment_camara_seguridad, container, false);

        // Inicializar Referencias de UI
        previewView = v.findViewById(R.id.viewFinder);
        layoutPreviewCamara = v.findViewById(R.id.layoutPreviewCamara);
        layoutConfirmacionFoto = v.findViewById(R.id.layoutConfirmacionFoto);
        ivFotoCapturada = v.findViewById(R.id.ivFotoCapturada);

        Button btnReintentar = v.findViewById(R.id.btnReintentar);
        Button btnConfirmar = v.findViewById(R.id.btnConfirmarVentaFinal);

        // Configurar Cámara
        cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext());
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                bindPreview(cameraProvider);
            } catch (Exception e) {
                if (isAdded()) Toast.makeText(getContext(), "Error al iniciar cámara", Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(requireContext()));

        // Listener: Capturar foto
        v.findViewById(R.id.btnCapture).setOnClickListener(view -> capturePhoto());

        // Listener: Volver a intentar (Regresa a la cámara)
        btnReintentar.setOnClickListener(view -> {
            layoutConfirmacionFoto.setVisibility(View.GONE);
            layoutPreviewCamara.setVisibility(View.VISIBLE);
            bitmapTemporal = null;
        });

        // Listener: Confirmar y Guardar en Base de Datos
        btnConfirmar.setOnClickListener(view -> {
            if (bitmapTemporal != null) {
                String base64Foto = bitmapToBase64(bitmapTemporal);
                productoViewModel.finalizarCuenta(idCliente, aliasCliente, metodoPago, base64Foto);
                Toast.makeText(getContext(), "Venta Finalizada con Éxito", Toast.LENGTH_SHORT).show();
                // Cargar el fragmento de la lista de nuevo en el contenedor del 70%
                requireActivity().getSupportFragmentManager().beginTransaction()
                        .replace(R.id.container_fragments, new ListaClientesFragment())
                        .commit();
            }
        });

        return v;
    }

    private void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder().build();
        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(CameraSelector.LENS_FACING_FRONT).build();

        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        imageCapture = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setTargetRotation(requireView().getDisplay().getRotation())
                .build();

        cameraProvider.unbindAll();
        cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);
    }

    private void capturePhoto() {
        if (imageCapture == null) return;

        imageCapture.takePicture(ContextCompat.getMainExecutor(requireContext()),
                new ImageCapture.OnImageCapturedCallback() {
                    @Override
                    public void onCaptureSuccess(@NonNull ImageProxy image) {
                        bitmapTemporal = imageProxyToBitmap(image);
                        image.close();

                        if (bitmapTemporal != null && isAdded()) {
                            // Cambiar a la vista de confirmación en el hilo principal
                            requireActivity().runOnUiThread(() -> {
                                ivFotoCapturada.setImageBitmap(bitmapTemporal);
                                layoutPreviewCamara.setVisibility(View.GONE);
                                layoutConfirmacionFoto.setVisibility(View.VISIBLE);
                            });
                        }
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException exception) {
                        if (isAdded() && getContext() != null) {
                            Toast.makeText(getContext(), "Error: " + exception.getMessage(), Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private Bitmap imageProxyToBitmap(ImageProxy image) {
        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
    }

    private String bitmapToBase64(Bitmap bitmap) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        // 60% es suficiente para ver un comprobante y ahorra mucha memoria
        bitmap.compress(Bitmap.CompressFormat.JPEG, 60, outputStream);
        byte[] byteArray = outputStream.toByteArray();
        return Base64.encodeToString(byteArray, Base64.NO_WRAP);
    }
}