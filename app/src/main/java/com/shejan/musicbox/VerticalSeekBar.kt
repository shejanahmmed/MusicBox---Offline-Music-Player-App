/*
 * Copyright (C) 2026 Shejan
 *
 * This file is part of MusicBox.
 *
 * MusicBox is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MusicBox is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MusicBox.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.shejan.musicbox

import android.content.Context
import android.graphics.Canvas
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.appcompat.widget.AppCompatSeekBar

class VerticalSeekBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.seekBarStyle
) : AppCompatSeekBar(context, attrs, defStyleAttr) {

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(h, w, oldh, oldw)
    }

    @Synchronized
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(heightMeasureSpec, widthMeasureSpec)
        setMeasuredDimension(measuredHeight, measuredWidth)
    }

    override fun onDraw(canvas: Canvas) {
        thumb?.let { thumbDrawable ->
            val progressRatio = progress.toFloat() / max
            val available = height - paddingLeft - paddingRight
            val thumbX = paddingLeft + (progressRatio * available).toInt()
            
            val thumbWidth = thumbDrawable.intrinsicWidth
            val thumbHeight = thumbDrawable.intrinsicHeight
            
            val centerY = paddingTop + (width - paddingTop - paddingBottom) / 2
            val left = thumbX - (paddingLeft - thumbOffset) - thumbWidth / 2
            val right = thumbX - (paddingLeft - thumbOffset) + thumbWidth / 2
            val top = centerY - thumbHeight / 2
            val bottom = centerY + thumbHeight / 2
            
            thumbDrawable.setBounds(left, top, right, bottom)
        }

        canvas.rotate(-90f)
        canvas.translate(-height.toFloat(), 0f)
        super.onDraw(canvas)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) {
            return false
        }

        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                val touchAvailable = height - paddingLeft - paddingRight
                if (touchAvailable > 0) {
                    val progressRatio = 1f - (event.y - paddingRight) / touchAvailable
                    val clampedRatio = progressRatio.coerceIn(0f, 1f)
                    
                    val superAvailable = width - paddingLeft - paddingRight
                    val mappedX = paddingLeft + clampedRatio * superAvailable
                    // Keep event.y as the vertical component so the hotspot receives actual vertical coordinates
                    event.setLocation(mappedX, event.y)
                }
            }
        }
        return super.onTouchEvent(event)
    }

    override fun drawableHotspotChanged(x: Float, y: Float) {
        // Direct the view's ripple hotspot to center horizontally and track touch y vertically
        super.drawableHotspotChanged(width / 2f, y)

        // Direct the thumb drawable's internal focus/press halo to match rotated bounds
        thumb?.let { thumbDrawable ->
            val progressRatio = progress.toFloat() / max
            val available = height - paddingLeft - paddingRight
            val thumbX = paddingLeft + (progressRatio * available)
            val centerY = paddingTop + (width - paddingTop - paddingBottom) / 2f
            thumbDrawable.setHotspot(thumbX, centerY)
        }
    }
}
