/*
 * Copyright (c) 2024 Auxio Project
 * Animations.kt is part of Auxio.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
 
package org.oxycblt.auxio.ui

import android.content.Context
import android.view.View
import androidx.annotation.AttrRes
import androidx.annotation.StyleRes
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import com.google.android.material.R as MR
import com.google.android.material.motion.MotionUtils
import org.oxycblt.auxio.util.scale

class Spatial private constructor(@AttrRes val attr: Int, val defaultStyle: Int) {
    private fun resolve(context: Context) =
        MotionUtils.resolveThemeSpringForce(context, attr, defaultStyle)

    fun scale(view: View, to: Float, jumpOnCancellation: Boolean = false): SpringAnimation {
        val from = view.scale
        val springForce = resolve(view.context)
        return SpringAnimation(FloatValueHolder(from)).apply {
            spring = springForce
            setStartValue(from)
            setMinimumVisibleChange(0.0001f)
            addUpdateListener { _, value, _ -> view.scale = value }
            addEndListener { _, canceled, value, _ ->
                view.scale = if (!canceled || jumpOnCancellation) to else value
            }
            animateToFinalPosition(to)
        }
    }

    fun translateX(view: View, to: Float, jumpOnCancellation: Boolean = false): SpringAnimation {
        val from = view.translationX
        val springForce = resolve(view.context)
        return SpringAnimation(FloatValueHolder(from)).apply {
            spring = springForce
            setStartValue(from)
            setMinimumVisibleChange(0.0001f)
            addUpdateListener { _, value, _ -> view.translationX = value }
            addEndListener { _, canceled, value, _ ->
                view.translationX = if (!canceled || jumpOnCancellation) to else value
            }
            animateToFinalPosition(to)
        }
    }

    companion object {
        val FAST =
            Spatial(
                MR.attr.motionSpringFastSpatial,
                MR.style.Motion_Material3_Spring_Standard_Fast_Spatial,
            )
        val DEFAULT =
            Spatial(
                MR.attr.motionSpringDefaultSpatial,
                MR.style.Motion_Material3_Spring_Standard_Default_Spatial,
            )
        val SLOW =
            Spatial(
                MR.attr.motionSpringSlowSpatial,
                MR.style.Motion_Material3_Spring_Standard_Slow_Spatial,
            )
    }
}

class Effect private constructor(@AttrRes val attr: Int, @StyleRes val defaultStyle: Int) {
    fun resolve(context: Context) = MotionUtils.resolveThemeSpringForce(context, attr, defaultStyle)

    fun alpha(view: View, to: Float, jumpOnCancellation: Boolean = false): SpringAnimation {
        val from = view.alpha
        val springForce = resolve(view.context)
        return SpringAnimation(FloatValueHolder(from)).apply {
            spring = springForce
            setStartValue(from)
            setMinimumVisibleChange(1f / 255f)
            addUpdateListener { _, value, _ -> view.alpha = value }
            addEndListener { _, canceled, value, _ ->
                view.scale = if (!canceled || jumpOnCancellation) to else value
            }
            animateToFinalPosition(to)
        }
    }

    companion object {
        val DEFAULT =
            Effect(
                MR.attr.motionSpringDefaultEffects,
                MR.style.Motion_Material3_Spring_Standard_Default_Effects,
            )
        val SLOW =
            Effect(
                MR.attr.motionSpringSlowEffects,
                MR.style.Motion_Material3_Spring_Standard_Slow_Effects,
            )
        val FAST =
            Effect(
                MR.attr.motionSpringFastEffects,
                MR.style.Motion_Material3_Spring_Standard_Fast_Effects,
            )
    }
}
