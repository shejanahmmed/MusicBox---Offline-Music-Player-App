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

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.core.content.ContextCompat

/**
 * Google-Photos–style fast-scroll pill.
 *
 * • A small rounded pill (6 dp × 44 dp) floats on the right edge.
 * • Completely invisible at rest.
 * • Fades in automatically when the RecyclerView scrolls.
 * • Pill moves up/down to reflect scroll position.
 * • Drag the pill to fast-scroll; [onLetterSelected] fires with the
 *   current section letter so the caller can show a letter bubble.
 * • Fades out 1.5 s after the last scroll/touch event.
 */
class AlphabetIndexScrollbar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    // ── Public callbacks ───────────────────────────────────────────────────
    /** Fired while dragging — caller shows the section-letter bubble. */
    var onLetterSelected: ((String) -> Unit)? = null
    /** Fired when the user lifts their finger — caller hides the bubble. */
    var onDragEnded: (() -> Unit)? = null

    // ── RecyclerView binding ───────────────────────────────────────────────
    private var rv: RecyclerView? = null

    private val rvScrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
            if (!isDragging) {
                refreshScrollRatio()
                fadeIn()
                scheduleFadeOut()
            }
        }
    }

    fun attachToRecyclerView(recyclerView: RecyclerView) {
        rv?.removeOnScrollListener(rvScrollListener)
        rv = recyclerView
        recyclerView.addOnScrollListener(rvScrollListener)
    }

    // ── State ──────────────────────────────────────────────────────────────
    private var scrollRatio  = 0f
    private var isDragging   = false
    private var lastLetter   = ""   // tracks last emitted letter to avoid spam

    /** Master alpha (0 = invisible, 255 = fully shown). */
    private var masterAlpha = 0
    private var alphaAnimator: ValueAnimator? = null
    private var hideRunnable: Runnable? = null

    // ── Paint ──────────────────────────────────────────────────────────────
    private val pillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pillRect  = RectF()
    private var pillColor = 0
    private var colorsResolved = false

    // ── Pill geometry (dp → px) ────────────────────────────────────────────
    private val density get() = resources.displayMetrics.density
    private val pillHeightPx get() = 44f * density   // tall enough to grab
    private val pillWidthPx  get() = width.toFloat()  // view width = pill width

    // ──────────────────────────────────────────────────────────────────────
    // Kept for API compatibility (no-op for the moving-pill design)
    // ──────────────────────────────────────────────────────────────────────
    fun setAvailableLetters(list: List<String>) { /* no-op */ }

    // ──────────────────────────────────────────────────────────────────────
    // Colour resolution
    // ──────────────────────────────────────────────────────────────────────
    private fun resolveColors() {
        if (colorsResolved) return
        pillColor = ContextCompat.getColor(context, R.color.colorScrollbarActive)
        colorsResolved = true
    }

    // ──────────────────────────────────────────────────────────────────────
    // Measurement — thin strip (8 dp); pill drawn inside
    // ──────────────────────────────────────────────────────────────────────
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        // 24dp touch target — pill is drawn in the rightmost 8dp of this area
        val desired = (24 * density + 0.5f).toInt()
        setMeasuredDimension(
            resolveSize(desired, widthMeasureSpec),
            resolveSize(0, heightMeasureSpec)
        )
    }

    // ──────────────────────────────────────────────────────────────────────
    // Scroll ratio helper
    // ──────────────────────────────────────────────────────────────────────
    private fun refreshScrollRatio() {
        val r = rv ?: return
        val offset = r.computeVerticalScrollOffset().toFloat()
        val range  = (r.computeVerticalScrollRange() - r.computeVerticalScrollExtent()).toFloat()
        scrollRatio = if (range > 0f) (offset / range).coerceIn(0f, 1f) else 0f
        invalidate()
    }

    // ──────────────────────────────────────────────────────────────────────
    // Drawing — only the moving pill
    // ──────────────────────────────────────────────────────────────────────
    override fun onDraw(canvas: Canvas) {
        if (masterAlpha == 0) return
        resolveColors()

        val h      = height.toFloat()
        val pillW  = (8 * density)          // visual pill stays 8dp
        val pillH  = pillHeightPx.coerceAtMost(h)
        val track  = (h - pillH).coerceAtLeast(0f)
        val pillTop = track * scrollRatio

        // Draw pill right-aligned inside the wider touch zone
        val left = width.toFloat() - pillW
        pillRect.set(left, pillTop, width.toFloat(), pillTop + pillH)
        pillPaint.color = applyAlpha(pillColor, masterAlpha)
        val r = pillW / 2f
        canvas.drawRoundRect(pillRect, r, r, pillPaint)
    }

    // ──────────────────────────────────────────────────────────────────────
    // Touch — drag the pill to scroll
    // ──────────────────────────────────────────────────────────────────────

    /** Computes scroll ratio from touch y, scrolls the RV, fires letter callback if changed. */
    private fun pickAndScroll(touchY: Float) {
        val h     = height.toFloat()
        val pillH = pillHeightPx.coerceAtMost(h)
        val track = (h - pillH).coerceAtLeast(1f)
        scrollRatio = ((touchY - pillH / 2f) / track).coerceIn(0f, 1f)
        invalidate()

        val rvRef   = rv ?: return
        val adapter = rvRef.adapter as? TrackAdapter
        val count   = adapter?.itemCount ?: 0
        if (count > 0) {
            val targetPos = (scrollRatio * (count - 1)).toInt().coerceIn(0, count - 1)
            (rvRef.layoutManager as? LinearLayoutManager)
                ?.scrollToPositionWithOffset(targetPos, 0)

            val letter = adapter?.getLetterAtPosition(targetPos) ?: ""
            if (letter.isNotEmpty() && letter != lastLetter) {
                lastLetter = letter
                performHapticFeedback(HapticFeedbackConstants.TEXT_HANDLE_MOVE)
                onLetterSelected?.invoke(letter)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isDragging = true
                lastLetter = ""   // reset so onLetterSelected always fires on first touch
                cancelScheduledFade()
                fadeIn()
                pickAndScroll(event.y)
                parent?.requestDisallowInterceptTouchEvent(true)
                true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!isDragging) isDragging = true
                cancelScheduledFade()
                fadeIn()
                pickAndScroll(event.y)
                parent?.requestDisallowInterceptTouchEvent(true)
                true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                onDragEnded?.invoke()
                scheduleFadeOut()
                parent?.requestDisallowInterceptTouchEvent(false)
                true
            }
            else -> super.onTouchEvent(event)
        }
    }

    // ──────────────────────────────────────────────────────────────────────
    // Fade in / out
    // ──────────────────────────────────────────────────────────────────────
    private fun fadeIn() {
        if (masterAlpha == 255) return
        alphaAnimator?.cancel()
        alphaAnimator = ValueAnimator.ofInt(masterAlpha, 255).apply {
            duration = 120
            addUpdateListener { masterAlpha = it.animatedValue as Int; invalidate() }
            start()
        }
    }

    private fun scheduleFadeOut() {
        cancelScheduledFade()
        hideRunnable = Runnable {
            alphaAnimator?.cancel()
            alphaAnimator = ValueAnimator.ofInt(masterAlpha, 0).apply {
                duration = 350
                interpolator = DecelerateInterpolator()
                addUpdateListener { masterAlpha = it.animatedValue as Int; invalidate() }
                start()
            }
        }
        postDelayed(hideRunnable!!, 1500)
    }

    private fun cancelScheduledFade() {
        hideRunnable?.let { removeCallbacks(it) }
        hideRunnable = null
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        rv?.removeOnScrollListener(rvScrollListener)
        cancelScheduledFade()
        alphaAnimator?.cancel()
    }

    // ──────────────────────────────────────────────────────────────────────
    // Helper — scale a colour's alpha channel
    // ──────────────────────────────────────────────────────────────────────
    private fun applyAlpha(color: Int, alpha: Int): Int {
        val orig   = (color ushr 24) and 0xFF
        val scaled = (orig * alpha / 255)
        return (color and 0x00FFFFFF) or (scaled shl 24)
    }
}
