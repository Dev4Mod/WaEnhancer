package com.wmods.wppenhacer.xposed.utils

import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.view.animation.AnimationSet
import android.view.animation.BounceInterpolator
import android.view.animation.RotateAnimation
import android.view.animation.ScaleAnimation
import android.view.animation.TranslateAnimation

object AnimationUtil {

    @JvmStatic
    fun getAnimation(animationName: String): Animation? {
        return when (animationName.lowercase()) {
            "fade_in" -> createFadeInAnimation()
            "fade_out" -> createFadeOutAnimation()
            "scale_up" -> createScaleUpAnimation()
            "slide_up" -> createSlideUpAnimation()
            "slide_right_to_left" -> createSlideRightToLeftAnimation()
            "rotate" -> createRotateAnimation()
            "bounce" -> createBounceAnimation()
            "shrink" -> createShrinkAnimation()
            "flip" -> createFlipAnimation()
            "hyperspace_out" -> createHyperspaceOutAnimation()
            else -> null
        }
    }

    private fun createFadeInAnimation(): Animation {
        val anim = AlphaAnimation(0.0f, 1.0f)
        anim.duration = 500
        return anim
    }

    private fun createFadeOutAnimation(): Animation {
        val anim = AlphaAnimation(1.0f, 0.0f)
        anim.duration = 500
        return anim
    }

    private fun createScaleUpAnimation(): Animation {
        val anim = ScaleAnimation(
            0.0f, 1.0f,
            0.0f, 1.0f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        )
        anim.duration = 500
        return anim
    }

    private fun createSlideUpAnimation(): Animation {
        val anim = TranslateAnimation(
            Animation.RELATIVE_TO_PARENT, 0.0f,
            Animation.RELATIVE_TO_PARENT, 0.0f,
            Animation.RELATIVE_TO_PARENT, 1.0f,
            Animation.RELATIVE_TO_PARENT, 0.0f
        )
        anim.duration = 500
        return anim
    }

    private fun createSlideRightToLeftAnimation(): Animation {
        val anim = TranslateAnimation(
            Animation.RELATIVE_TO_PARENT, 1.0f,
            Animation.RELATIVE_TO_PARENT, 0.0f,
            Animation.RELATIVE_TO_PARENT, 0.0f,
            Animation.RELATIVE_TO_PARENT, 0.0f
        )
        anim.duration = 500
        return anim
    }

    private fun createRotateAnimation(): Animation {
        val anim = RotateAnimation(
            0.0f, 360.0f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        )
        anim.duration = 500
        return anim
    }

    @JvmStatic
    fun createBounceAnimation(): Animation {
        val scaleAnimation = ScaleAnimation(
            0.5f, 1.0f,
            0.5f, 1.0f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        )
        scaleAnimation.duration = 500
        scaleAnimation.interpolator = BounceInterpolator()

        val animationSet = AnimationSet(true)
        animationSet.addAnimation(scaleAnimation)
        animationSet.fillAfter = true
        return animationSet
    }

    private fun createShrinkAnimation(): Animation {
        val anim = ScaleAnimation(
            1.0f, 0.0f,
            1.0f, 0.0f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        )
        anim.duration = 500
        return anim
    }

    private fun createFlipAnimation(): Animation {
        val anim = ScaleAnimation(
            1.0f, 1.0f,
            1.0f, -1.0f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        )
        anim.duration = 500
        anim.repeatCount = 1
        anim.repeatMode = Animation.REVERSE
        return anim
    }

    @JvmStatic
    fun createHyperspaceOutAnimation(): Animation {
        val firstScaleAnimation = ScaleAnimation(
            1.0f, 1.4f,
            1.0f, 0.6f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        )
        firstScaleAnimation.duration = 700
        firstScaleAnimation.interpolator = AccelerateDecelerateInterpolator()
        firstScaleAnimation.fillAfter = false

        val secondScaleAnimation = ScaleAnimation(
            1.4f, 0.0f,
            0.6f, 0.0f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        )
        secondScaleAnimation.duration = 400

        val rotateAnimation = RotateAnimation(
            0.0f, -45.0f,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        )
        rotateAnimation.duration = 400

        val innerSet = AnimationSet(true)
        innerSet.interpolator = AccelerateInterpolator()
        innerSet.startOffset = 700
        innerSet.addAnimation(secondScaleAnimation)
        innerSet.addAnimation(rotateAnimation)

        val outerSet = AnimationSet(false)
        outerSet.addAnimation(firstScaleAnimation)
        outerSet.addAnimation(innerSet)

        return outerSet
    }
}
