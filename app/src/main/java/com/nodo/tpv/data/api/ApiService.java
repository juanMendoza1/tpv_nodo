package com.nodo.tpv.data.api;

import com.nodo.tpv.data.dto.ProductoDTO;
import com.nodo.tpv.data.entities.Usuario;
import java.util.List;
import java.util.Map;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface ApiService {

    /**
     * Vinculación inicial de la tablet.
     * Cambiado de TerminalDispositivo a Map<String, Object> porque el
     * servidor está enviando un JSON plano con "empresaId" en la raíz.
     */
    @POST("api/terminales/vincular-qr")
    Call<Map<String, Object>> activarTerminal(@Body Map<String, Object> payload);

    // Obtener los trabajadores (slots) de la empresa de forma dinámica
    @GET("api/usuarios/empresa/{empresaId}")
    Call<List<Usuario>> obtenerUsuariosPorEmpresa(@Path("empresaId") Long empresaId);

    // Validar el PIN del trabajador seleccionado
    @POST("api/usuarios/login-tablet")
    Call<Map<String, String>> loginTablet(@Body Map<String, Object> credenciales);

    // Validar si la tablet sigue autorizada en el sistema
    @GET("api/terminales/validar/{uuid}")
    Call<Void> validarTerminal(@Path("uuid") String uuid);

    @GET("api/productos/empresa/{empresaId}")
    Call<List<ProductoDTO>> obtenerProductosPorEmpresa(@Path("empresaId") Long empresaId);

    @POST("api/inventario/despacho-mesa")
    Call<okhttp3.ResponseBody> reportarDespacho(
            @Query("productoId") Long productoId,
            @Query("cantidad") Integer cantidad,
            @Query("idDuelo") String idDuelo,
            @Query("loginOperativo") String loginOperativo
    );

    @POST("api/usuarios/log-sesion")
    Call<ResponseBody> reportarEventoSesion(
            @Query("idUsuario") int idUsuario,
            @Query("slot") int slot,
            @Query("tipo") String tipo,
            @Query("fecha") long fecha
    );
}