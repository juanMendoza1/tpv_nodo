package com.nodo.tpv.ui.arena.managers;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Path;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;

import androidx.constraintlayout.widget.ConstraintLayout;

import com.airbnb.lottie.LottieAnimationView;
import com.google.android.material.card.MaterialCardView;
import com.nodo.tpv.R;

import java.util.Random;

public class ArenaAnimationHelper {

    private final Context context;
    private final LottieAnimationView lottieCelebration;
    private final LinearLayout containerMarcadoresDinamicos;
    private final Random random = new Random();

    public ArenaAnimationHelper(Context context, LottieAnimationView lottieCelebration, LinearLayout containerMarcadoresDinamicos) {
        this.context = context;
        this.lottieCelebration = lottieCelebration;
        this.containerMarcadoresDinamicos = containerMarcadoresDinamicos;
    }

    public void dispararCelebracion() {
        if (lottieCelebration != null) {
            lottieCelebration.setVisibility(View.VISIBLE);
            lottieCelebration.playAnimation();
            lottieCelebration.addAnimatorListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    lottieCelebration.setVisibility(View.GONE);
                }
            });
        }
    }

    public void animarEquipoSalvado(View view) {
        view.animate().scaleX(1.1f).scaleY(1.1f).alpha(0.3f).setDuration(400)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    view.setScaleX(0.95f);
                    view.setScaleY(0.95f);
                }).start();
    }

    public void animarPerdedorFinal(int colorPerdedor) {
        if (containerMarcadoresDinamicos == null) return;
        for (int i = 0; i < containerMarcadoresDinamicos.getChildCount(); i++) {
            View v = containerMarcadoresDinamicos.getChildAt(i);
            if (v.getTag() instanceof Integer && (int) v.getTag() == colorPerdedor) {
                ObjectAnimator scaleX = ObjectAnimator.ofFloat(v, "scaleX", 1f, 1.15f, 1f);
                ObjectAnimator scaleY = ObjectAnimator.ofFloat(v, "scaleY", 1f, 1.15f, 1f);
                scaleX.setRepeatCount(3); scaleY.setRepeatCount(3);
                scaleX.setDuration(200); scaleY.setDuration(200);

                if (v instanceof MaterialCardView) {
                    ((MaterialCardView) v).setStrokeColor(ColorStateList.valueOf(Color.RED));
                    ((MaterialCardView) v).setStrokeWidth(12);
                }
                scaleX.start(); scaleY.start();
                break;
            }
        }
    }

    public void animarRestauracionUI() {
        if (containerMarcadoresDinamicos == null) return;
        for (int i = 0; i < containerMarcadoresDinamicos.getChildCount(); i++) {
            View v = containerMarcadoresDinamicos.getChildAt(i);
            v.animate().alpha(1.0f).scaleX(1.0f).scaleY(1.0f).setStartDelay(i * 100L).setDuration(400).start();
            if (v instanceof MaterialCardView && v.getTag() instanceof Integer) {
                ((MaterialCardView) v).setStrokeColor(ColorStateList.valueOf((int)v.getTag()));
                ((MaterialCardView) v).setStrokeWidth(6);
            }
            v.setEnabled(true);
        }
    }

    // --- NUEVO: RÁFAGA DE BOLAS EN ESPIRAL ---

    public void animarRafagaBolasYChoque(View vistaOrigenGeneral, LinearLayout contenedorBolas, View vistaDestino, int colorEquipo, Runnable accionAlChocar) {
        if (vistaDestino == null) return;

        // Si no hay bolas en el contenedor, disparamos una sola bola desde la tarjeta general
        if (contenedorBolas == null || contenedorBolas.getChildCount() == 0) {
            animarUnaBola(vistaOrigenGeneral, vistaDestino, colorEquipo, 0, accionAlChocar);
            return;
        }

        // Si hay bolas, las disparamos una por una en ráfaga
        int totalBolas = contenedorBolas.getChildCount();
        for (int i = 0; i < totalBolas; i++) {
            View bolaOriginal = contenedorBolas.getChildAt(i);
            boolean esLaUltimaBola = (i == totalBolas - 1);
            long delayRafaga = i * 150L; // 150ms de diferencia entre cada disparo

            // Disparamos la bola actual, y SOLO la última ejecuta la acción final
            animarUnaBola(bolaOriginal, vistaDestino, colorEquipo, delayRafaga, esLaUltimaBola ? accionAlChocar : null);
        }
    }

    private void animarUnaBola(View vistaOrigen, View vistaDestino, int colorBola, long startDelay, Runnable accionAlChocar) {
        ViewGroup root = (ViewGroup) ((Activity) context).findViewById(android.R.id.content);
        if (root == null) return;

        int sizePx = (int) (24 * context.getResources().getDisplayMetrics().density);
        View bolaClon = new View(context);
        bolaClon.setBackgroundResource(R.drawable.bg_avatar_circle);
        bolaClon.setBackgroundTintList(ColorStateList.valueOf(colorBola));
        bolaClon.setLayoutParams(new ViewGroup.LayoutParams(sizePx, sizePx));
        bolaClon.setElevation(100f);

        // Ocultamos la bola original exactamente cuando le toca salir a volar a su clon
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            vistaOrigen.setVisibility(View.INVISIBLE);
        }, startDelay);

        int[] locOrigen = new int[2];
        vistaOrigen.getLocationInWindow(locOrigen);
        int startX = locOrigen[0] + (vistaOrigen.getWidth() / 2) - (sizePx / 2);
        int startY = locOrigen[1] + (vistaOrigen.getHeight() / 2) - (sizePx / 2);

        int[] locDestino = new int[2];
        vistaDestino.getLocationInWindow(locDestino);
        int endX = locDestino[0] + (vistaDestino.getWidth() / 2) - (sizePx / 2);
        int endY = locDestino[1] + (vistaDestino.getHeight() / 2) - (sizePx / 2);

        root.addView(bolaClon);
        bolaClon.setVisibility(View.INVISIBLE); // Se mantiene invisible hasta que le toque salir

        // Matemática de la espiral caótica
        Path path = new Path();
        path.moveTo(startX, startY);
        // Puntos de control aleatorios para que cada bola haga una curva diferente
        int controlX = startX + (random.nextBoolean() ? 200 : -200) + random.nextInt(150);
        int controlY = startY - 200 - random.nextInt(300);
        path.quadTo(controlX, controlY, endX, endY);

        ObjectAnimator pathAnimator = ObjectAnimator.ofFloat(bolaClon, View.X, View.Y, path);
        pathAnimator.setDuration(700); // 0.7 segundos de vuelo
        pathAnimator.setStartDelay(startDelay);
        pathAnimator.setInterpolator(new DecelerateInterpolator());

        pathAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationStart(Animator animation) {
                bolaClon.setVisibility(View.VISIBLE);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                estallarVistaConParticulasDirecto(bolaClon, colorBola, root, endX, endY);
                sacudirMarcador(vistaDestino);

                if (accionAlChocar != null) {
                    accionAlChocar.run();
                }
            }
        });

        pathAnimator.start();
    }

    private void sacudirMarcador(View vista) {
        ObjectAnimator animX = ObjectAnimator.ofFloat(vista, "translationX", 0f, 15f, -15f, 10f, -10f, 5f, -5f, 0f);
        animX.setDuration(250);
        animX.start();
    }

    private void estallarVistaConParticulasDirecto(View vistaBola, int colorExplosion, ViewGroup root, int centerX, int centerY) {
        vistaBola.setVisibility(View.INVISIBLE);

        int totalParticulas = 15;
        long duracionVuelo = 500;

        for (int i = 0; i < totalParticulas; i++) {
            View particula = new View(context);
            particula.setBackgroundResource(R.drawable.bg_avatar_circle);
            particula.setBackgroundTintList(ColorStateList.valueOf(colorExplosion));

            int size = 6 + random.nextInt(8);
            int sizePx = (int) (size * context.getResources().getDisplayMetrics().density);

            particula.setLayoutParams(new ViewGroup.LayoutParams(sizePx, sizePx));
            particula.setX(centerX);
            particula.setY(centerY);
            particula.setElevation(100f);

            root.addView(particula);

            float angulo = random.nextFloat() * 360f;
            float distancia = 80f + random.nextFloat() * 150f;

            float tX = (float) (distancia * Math.cos(Math.toRadians(angulo)));
            float tY = (float) (distancia * Math.sin(Math.toRadians(angulo)));

            particula.animate()
                    .translationXBy(tX)
                    .translationYBy(tY)
                    .scaleX(0.1f)
                    .scaleY(0.1f)
                    .alpha(0f)
                    .setDuration(duracionVuelo)
                    .setInterpolator(new DecelerateInterpolator())
                    .withEndAction(() -> root.removeView(particula))
                    .start();
        }

        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> root.removeView(vistaBola), duracionVuelo);
    }

    // --- FUNCIÓN RESTAURADA PARA LIMPIAR LA MESA ---
    public void estallarUnaVistaConParticulas(View vistaABorrar, int colorExplosion) {
        if (vistaABorrar == null) return;
        ViewGroup root = (ViewGroup) vistaABorrar.getRootView();
        if (root == null) return;

        int[] location = new int[2];
        vistaABorrar.getLocationOnScreen(location);
        int centerX = location[0] + (vistaABorrar.getWidth() / 2);
        int centerY = location[1] + (vistaABorrar.getHeight() / 2);

        long duracionInflado = 400;
        vistaABorrar.animate()
                .scaleX(1.8f)
                .scaleY(1.8f)
                .alpha(0.5f)
                .setDuration(duracionInflado)
                .setInterpolator(new android.view.animation.OvershootInterpolator())
                .withEndAction(() -> {
                    vistaABorrar.setVisibility(View.INVISIBLE);

                    int totalParticulas = 30;
                    long duracionVueloParticulas = 800;

                    for (int i = 0; i < totalParticulas; i++) {
                        View particula = new View(context);
                        particula.setBackgroundResource(R.drawable.item_particula_visual); // Usa tu drawable original aquí
                        particula.getBackground().setTint(colorExplosion);
                        particula.setAlpha(1.0f);

                        int size = 10 + random.nextInt(15);
                        int sizePx = (int) (size * context.getResources().getDisplayMetrics().density);

                        ConstraintLayout.LayoutParams params = new ConstraintLayout.LayoutParams(sizePx, sizePx);
                        params.leftToLeft = ConstraintLayout.LayoutParams.PARENT_ID;
                        params.topToTop = ConstraintLayout.LayoutParams.PARENT_ID;
                        particula.setLayoutParams(params);
                        particula.setX(centerX - (sizePx / 2f));
                        particula.setY(centerY - (sizePx / 2f));

                        root.addView(particula);

                        float angulo = random.nextFloat() * 360f;
                        float distancia = 150f + random.nextFloat() * 300f;

                        float tX = (float) (distancia * Math.cos(Math.toRadians(angulo)));
                        float tY = (float) (distancia * Math.sin(Math.toRadians(angulo)));

                        particula.animate()
                                .translationXBy(tX)
                                .translationYBy(tY)
                                .scaleX(0.2f)
                                .scaleY(0.2f)
                                .alpha(0f)
                                .setDuration(duracionVueloParticulas)
                                .setInterpolator(new DecelerateInterpolator())
                                .withEndAction(() -> root.removeView(particula))
                                .start();
                    }
                })
                .start();
    }
}