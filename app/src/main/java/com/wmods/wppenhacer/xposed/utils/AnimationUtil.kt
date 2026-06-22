package com.wmods.wppenhacer.xposed.utils;

import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.BounceInterpolator;
import android.view.animation.RotateAnimation;
import android.view.animation.ScaleAnimation;
import android.view.animation.TranslateAnimation;

public class AnimationUtil {

    public static Animation getAnimation(String animationName) {
        return switch (animationName.toLowerCase()) {
            case "fade_in" -> createFadeInAnimation();
            case "fade_out" -> createFadeOutAnimation();
            case "scale_up" -> createScaleUpAnimation();
            case "slide_up" -> createSlideUpAnimation();
            case "slide_right_to_left" -> createSlideRightToLeftAnimation();
            case "rotate" -> createRotateAnimation();
            case "bounce" -> createBounceAnimation();
            case "shrink" -> createShrinkAnimation();
            case "flip" -> createFlipAnimation();
            case "hyperspace_out" -> createHyperspaceOutAnimation();
            default -> null;
        };
    }

    private static Animation createFadeInAnimation() {
        AlphaAnimation anim = new AlphaAnimation(0.0f, 1.0f);
        anim.setDuration(500); // Duração da animação em milissegundos
        return anim;
    }

    private static Animation createFadeOutAnimation() {
        AlphaAnimation anim = new AlphaAnimation(1.0f, 0.0f);
        anim.setDuration(500); // Duração da animação em milissegundos
        return anim;
    }

    private static Animation createScaleUpAnimation() {
        ScaleAnimation anim = new ScaleAnimation(
                0.0f, 1.0f, // De e para escala em X
                0.0f, 1.0f, // De e para escala em Y
                Animation.RELATIVE_TO_SELF, 0.5f, // Ponto pivot X
                Animation.RELATIVE_TO_SELF, 0.5f  // Ponto pivot Y
        );
        anim.setDuration(500); // Duração da animação em milissegundos
        return anim;
    }

    private static Animation createSlideUpAnimation() {
        TranslateAnimation anim = new TranslateAnimation(
                Animation.RELATIVE_TO_PARENT, 0.0f, // De X
                Animation.RELATIVE_TO_PARENT, 0.0f, // Para X
                Animation.RELATIVE_TO_PARENT, 1.0f, // De Y
                Animation.RELATIVE_TO_PARENT, 0.0f  // Para Y
        );
        anim.setDuration(500); // Duração da animação em milissegundos
        return anim;
    }

    private static Animation createSlideRightToLeftAnimation() {
        TranslateAnimation anim = new TranslateAnimation(
                Animation.RELATIVE_TO_PARENT, 1.0f, // De X (direita)
                Animation.RELATIVE_TO_PARENT, 0.0f, // Para X (esquerda)
                Animation.RELATIVE_TO_PARENT, 0.0f, // De Y
                Animation.RELATIVE_TO_PARENT, 0.0f  // Para Y
        );
        anim.setDuration(500); // Duração da animação em milissegundos
        return anim;
    }

    private static Animation createRotateAnimation() {
        RotateAnimation anim = new RotateAnimation(
                0.0f, 360.0f, // De e para ângulo
                Animation.RELATIVE_TO_SELF, 0.5f, // Ponto pivot X
                Animation.RELATIVE_TO_SELF, 0.5f  // Ponto pivot Y
        );
        anim.setDuration(500); // Duração da animação em milissegundos
        return anim;
    }

    public static Animation createBounceAnimation() {
        // Configurando a animação de escala
        ScaleAnimation scaleAnimation = new ScaleAnimation(
                0.5f, 1.0f, // De e para escala em X
                0.5f, 1.0f, // De e para escala em Y
                Animation.RELATIVE_TO_SELF, 0.5f, // Ponto pivot X
                Animation.RELATIVE_TO_SELF, 0.5f  // Ponto pivot Y
        );
        scaleAnimation.setDuration(500); // Duração da animação em milissegundos

        // Configurando o interpolador de bounce
        scaleAnimation.setInterpolator(new BounceInterpolator());

        // Configurando o AnimationSet
        AnimationSet animationSet = new AnimationSet(true);
        animationSet.addAnimation(scaleAnimation);
        animationSet.setFillAfter(true); // Mantém o estado após a animação

        return animationSet;
    }

    private static Animation createShrinkAnimation() {
        ScaleAnimation anim = new ScaleAnimation(
                1.0f, 0.0f, // De e para escala em X
                1.0f, 0.0f, // De e para escala em Y
                Animation.RELATIVE_TO_SELF, 0.5f, // Ponto pivot X
                Animation.RELATIVE_TO_SELF, 0.5f  // Ponto pivot Y
        );
        anim.setDuration(500); // Duração da animação em milissegundos
        return anim;
    }

    private static Animation createFlipAnimation() {
        ScaleAnimation anim = new ScaleAnimation(
                1.0f, 1.0f, // De e para escala em X (não muda)
                1.0f, -1.0f, // De e para escala em Y (inverte verticalmente)
                Animation.RELATIVE_TO_SELF, 0.5f, // Ponto pivot X
                Animation.RELATIVE_TO_SELF, 0.5f  // Ponto pivot Y
        );
        anim.setDuration(500); // Duração da animação em milissegundos
        anim.setRepeatCount(1); // Repetir uma vez
        anim.setRepeatMode(Animation.REVERSE); // Reverter após a primeira execução
        return anim;
    }

    public static Animation createHyperspaceOutAnimation() {
        // Primeira animação de escala
        ScaleAnimation firstScaleAnimation = new ScaleAnimation(
                1.0f, 1.4f, // De e para escala em X
                1.0f, 0.6f, // De e para escala em Y
                Animation.RELATIVE_TO_SELF, 0.5f, // Ponto pivot X
                Animation.RELATIVE_TO_SELF, 0.5f  // Ponto pivot Y
        );
        firstScaleAnimation.setDuration(700);
        firstScaleAnimation.setInterpolator(new AccelerateDecelerateInterpolator());
        firstScaleAnimation.setFillAfter(false);

        // Segunda animação de escala dentro de um conjunto de animações
        ScaleAnimation secondScaleAnimation = new ScaleAnimation(
                1.4f, 0.0f, // De e para escala em X
                0.6f, 0.0f, // De e para escala em Y
                Animation.RELATIVE_TO_SELF, 0.5f, // Ponto pivot X
                Animation.RELATIVE_TO_SELF, 0.5f  // Ponto pivot Y
        );
        secondScaleAnimation.setDuration(400);

        // Animação de rotação dentro de um conjunto de animações
        RotateAnimation rotateAnimation = new RotateAnimation(
                0.0f, -45.0f, // De e para ângulo
                Animation.RELATIVE_TO_SELF, 0.5f, // Ponto pivot X
                Animation.RELATIVE_TO_SELF, 0.5f  // Ponto pivot Y
        );
        rotateAnimation.setDuration(400);

        // Conjunto de animações internas
        AnimationSet innerSet = new AnimationSet(true);
        innerSet.setInterpolator(new AccelerateInterpolator());
        innerSet.setStartOffset(700);
        innerSet.addAnimation(secondScaleAnimation);
        innerSet.addAnimation(rotateAnimation);

        // Conjunto de animações principal
        AnimationSet outerSet = new AnimationSet(false);
        outerSet.addAnimation(firstScaleAnimation);
        outerSet.addAnimation(innerSet);

        return outerSet;
    }
}