package com.nodo.tpv.ui.arena.managers;

import android.graphics.Color;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.transition.TransitionManager;

import com.nodo.tpv.R;

public class ArenaVARManager {

    private final WebView webViewCamara;
    private final View scrollMarcadores;
    private final ConstraintLayout rootLayout;

    private boolean isVarActive = false;
    private final String cameraUrl = "http://192.168.1.2:8080/";

    public ArenaVARManager(ConstraintLayout rootLayout, WebView webViewCamara, View scrollMarcadores) {
        this.rootLayout = rootLayout;
        this.webViewCamara = webViewCamara;
        this.scrollMarcadores = scrollMarcadores;
    }

    public void configurarWebView() {
        if (webViewCamara != null) {
            WebSettings settings = webViewCamara.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setSupportZoom(true);
            settings.setBuiltInZoomControls(true);
            settings.setDisplayZoomControls(false);
            settings.setLoadWithOverviewMode(true);
            settings.setUseWideViewPort(true);

            webViewCamara.setInitialScale(100);
            webViewCamara.setBackgroundColor(Color.BLACK);
            webViewCamara.setWebViewClient(new WebViewClient());
        }
    }

    public void toggleModoVAR() {
        isVarActive = !isVarActive;

        if (rootLayout == null) return;

        ConstraintSet set = new ConstraintSet();
        set.clone(rootLayout);

        if (isVarActive) {
            webViewCamara.setVisibility(View.VISIBLE);
            if (webViewCamara != null) {
                webViewCamara.loadUrl(cameraUrl + "?t=" + System.currentTimeMillis());
            }
            set.setGuidelinePercent(R.id.guidelineVAR, 0.45f);

            if (scrollMarcadores != null) {
                scrollMarcadores.setVisibility(View.GONE);
            }
        } else {
            if (webViewCamara != null) {
                webViewCamara.loadUrl("about:blank");
                webViewCamara.setVisibility(View.GONE);
            }
            set.setGuidelinePercent(R.id.guidelineVAR, 0.0f);

            if (scrollMarcadores != null) {
                scrollMarcadores.setVisibility(View.VISIBLE);
            }
        }

        TransitionManager.beginDelayedTransition(rootLayout);
        set.applyTo(rootLayout);
    }

    public void onResume() {
        if (isVarActive && webViewCamara != null) {
            webViewCamara.loadUrl(cameraUrl + "?t=" + System.currentTimeMillis());
        }
    }

    public void onStop() {
        if (webViewCamara != null) {
            webViewCamara.loadUrl("about:blank");
        }
    }
}