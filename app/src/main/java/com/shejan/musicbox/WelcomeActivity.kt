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

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.button.MaterialButton

class WelcomeActivity : AppCompatActivity() {

    private lateinit var layoutS1: View
    private lateinit var layoutS2: View
    private lateinit var layoutS3: View

    private lateinit var flVinylContainer: View
    private lateinit var vPulseRing: View
    private lateinit var btnGetStarted: MaterialButton

    private lateinit var vpSlides: ViewPager2
    private lateinit var btnSkip: TextView
    private lateinit var btnNext: MaterialButton

    private lateinit var btnOpenLibrary: MaterialButton

    private lateinit var logoBar1: View
    private lateinit var logoBar2: View
    private lateinit var logoBar3: View
    private lateinit var logoBar4: View
    private lateinit var logoCircleGroup: View
    private lateinit var logoCircle: View
    private val logoAnimators = mutableListOf<ValueAnimator>()

    private var activeScreen = 1
    private var isTransitioning = false
    private var vinylRotationAnimator: ObjectAnimator? = null
    private var pulseRingAnimator: ObjectAnimator? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_welcome)

        // Apply WindowInsets to root container
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(view.paddingLeft, systemBars.top, view.paddingRight, systemBars.bottom)
            insets
        }

        // Initialize Screens
        layoutS1 = findViewById(R.id.layout_s1)
        layoutS2 = findViewById(R.id.layout_s2)
        layoutS3 = findViewById(R.id.layout_s3)

        // Initialize Screen 1 Views & Animations
        flVinylContainer = findViewById(R.id.fl_vinyl_container)
        vPulseRing = findViewById(R.id.v_pulse_ring)
        btnGetStarted = findViewById(R.id.btn_get_started)

        setupSplashAnimations()

        btnGetStarted.setOnClickListener {
            transitionToScreen(2)
        }

        // Initialize Screen 2 Pager & Buttons
        vpSlides = findViewById(R.id.vp_slides)
        btnSkip = findViewById(R.id.btn_skip)
        btnNext = findViewById(R.id.btn_next)

        setupFeaturesSlideshow()

        btnSkip.setOnClickListener {
            transitionToScreen(3)
        }

        btnNext.setOnClickListener {
            val currentItem = vpSlides.currentItem
            if (currentItem < 3) {
                vpSlides.currentItem = currentItem + 1
            } else {
                transitionToScreen(3)
            }
        }

        // Initialize Screen 3 Views
        btnOpenLibrary = findViewById(R.id.btn_open_library)
        btnOpenLibrary.setOnClickListener {
            completeOnboarding()
        }

        // Initialize Animated Logo Views
        logoBar1 = findViewById(R.id.logo_bar_1)
        logoBar2 = findViewById(R.id.logo_bar_2)
        logoBar3 = findViewById(R.id.logo_bar_3)
        logoBar4 = findViewById(R.id.logo_bar_4)
        logoCircleGroup = findViewById(R.id.logo_circle_group)
        logoCircle = findViewById(R.id.logo_circle)

        resetLogoViews()

        val tvVersion = findViewById<TextView>(R.id.tv_ready_version)
        tvVersion.text = "MUSICBOX · VERSION ${BuildConfig.VERSION_NAME}"
    }

    private fun setupSplashAnimations() {
        // Continuous Spinning Vinyl Record
        vinylRotationAnimator = ObjectAnimator.ofFloat(flVinylContainer, "rotation", 0f, 360f).apply {
            duration = 8000
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
        }
        vinylRotationAnimator?.start()

        // Continuous Pulsing Ring Effect
        val scaleX = PropertyValuesHolder.ofFloat("scaleX", 1.0f, 1.18f)
        val scaleY = PropertyValuesHolder.ofFloat("scaleY", 1.0f, 1.18f)
        val alpha = PropertyValuesHolder.ofFloat("alpha", 0.6f, 0.0f)
        pulseRingAnimator = ObjectAnimator.ofPropertyValuesHolder(vPulseRing, scaleX, scaleY, alpha).apply {
            duration = 2400
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
        }
        pulseRingAnimator?.start()
    }

    private fun setupFeaturesSlideshow() {
        val slides = listOf(
            OnboardingSlide(
                R.drawable.ic_onboarding_pure_sound,
                "PLAYBACK",
                "Pure Sound",
                "Full-quality local playback. No streaming. No compression. Your files, played exactly as they were mastered."
            ),
            OnboardingSlide(
                R.drawable.ic_equalizer,
                "CONTROL",
                "Your EQ",
                "Shape every frequency. Built-in equalizer with presets for any genre — or dial it in yourself."
            ),
            OnboardingSlide(
                R.drawable.ic_library_music,
                "LIBRARY",
                "Stay Organized",
                "Auto-scans your device for music. Albums, artists, playlists — always sorted and ready."
            ),
            OnboardingSlide(
                R.drawable.ic_onboarding_offline,
                "PRIVACY",
                "100% Offline",
                "No account. No internet required. No data leaves your device — ever. Just you and your music."
            )
        )

        vpSlides.adapter = SlidesAdapter(slides)
        vpSlides.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateDots(position)
                if (position == 3) {
                    btnNext.setIconResource(R.drawable.ic_check)
                } else {
                    btnNext.setIconResource(R.drawable.ic_arrow_forward)
                }
            }
        })
    }

    private fun updateDots(position: Int) {
        val dots = arrayOf(
            findViewById<View>(R.id.dot_0),
            findViewById<View>(R.id.dot_1),
            findViewById<View>(R.id.dot_2),
            findViewById<View>(R.id.dot_3)
        )
        val density = resources.displayMetrics.density
        dots.forEachIndexed { index, view ->
            val lp = view.layoutParams as LinearLayout.LayoutParams
            if (index == position) {
                view.setBackgroundResource(R.drawable.shape_dot_active)
                lp.width = (22 * density).toInt()
            } else {
                view.setBackgroundResource(R.drawable.shape_dot_inactive)
                lp.width = (6 * density).toInt()
            }
            view.layoutParams = lp
        }
    }

    private fun transitionToScreen(screenNumber: Int) {
        if (isTransitioning) return
        val fromView = when (activeScreen) {
            1 -> layoutS1
            2 -> layoutS2
            3 -> layoutS3
            else -> null
        }
        val toView = when (screenNumber) {
            1 -> layoutS1
            2 -> layoutS2
            3 -> layoutS3
            else -> null
        }

        if (fromView != null && toView != null) {
            isTransitioning = true
            val movingForward = screenNumber > activeScreen
            activeScreen = screenNumber

            val width = resources.displayMetrics.widthPixels.toFloat()
            val fromTranslationTarget = if (movingForward) -width else width
            val toTranslationStart = if (movingForward) width else -width

            // Prepare toView with initial state
            toView.alpha = 0f
            toView.scaleX = 0.9f
            toView.scaleY = 0.9f
            toView.translationX = toTranslationStart
            toView.visibility = View.VISIBLE

            // Animate fromView out
            fromView.animate()
                .translationX(fromTranslationTarget)
                .scaleX(0.9f)
                .scaleY(0.9f)
                .alpha(0f)
                .setDuration(450)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .withEndAction {
                    fromView.visibility = View.GONE
                    // Reset properties to clean state
                    fromView.translationX = 0f
                    fromView.scaleX = 1f
                    fromView.scaleY = 1f
                    fromView.alpha = 1f
                }
                .start()

            // Animate toView in
            toView.animate()
                .translationX(0f)
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(450)
                .setInterpolator(AccelerateDecelerateInterpolator())
                .withEndAction {
                    isTransitioning = false
                    if (screenNumber == 3) {
                        startLogoAnimation()
                    }
                }
                .start()
        }
    }

    private fun completeOnboarding() {
        val prefs = getSharedPreferences("MusicBoxPrefs", MODE_PRIVATE)
        prefs.edit().apply {
            putString("USER_NAME", "LISTENER")
            putBoolean("IS_FIRST_RUN", false)
            apply()
        }

        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun resetLogoViews() {
        logoAnimators.forEach { it.cancel() }
        logoAnimators.clear()

        logoBar1.scaleY = 0f
        logoBar1.alpha = 0f

        val heightBars = listOf(logoBar2, logoBar3, logoBar4)
        heightBars.forEach { bar ->
            bar.alpha = 0f
            val lp = bar.layoutParams
            lp.height = 0
            bar.layoutParams = lp
        }
        logoCircleGroup.scaleX = 0f
        logoCircleGroup.scaleY = 0f
        logoCircleGroup.alpha = 0f
    }

    private fun startLogoAnimation() {
        resetLogoViews()

        val density = resources.displayMetrics.density

        // Pixel heights for Bars 2, 3, 4
        val barHeightsMax = intArrayOf(
            (128.4f * density).toInt(),
            (95.3f * density).toInt(),
            (149.7f * density).toInt()
        )
        val barHeightsMin = intArrayOf(
            (70.6f * density).toInt(),
            (52.4f * density).toInt(),
            (82.3f * density).toInt()
        )

        val riseDelays = longArrayOf(150, 375, 600, 825)
        val riseDurations = 750L
        val riseInterpolator = android.view.animation.OvershootInterpolator(1.4f)

        val beatDurations = longArrayOf(2100, 1650, 2400, 1800)
        val beatDelays = longArrayOf(1100, 1300, 1500, 1700)

        // ═════ BAR 1 (Circular shape, keep scale-based animations) ═════
        logoBar1.pivotX = logoBar1.width.toFloat() / 2f
        logoBar1.pivotY = logoBar1.height.toFloat()

        val bar1RiseX = ObjectAnimator.ofFloat(logoBar1, "scaleX", 0f, 1f).apply {
            duration = riseDurations
            startDelay = riseDelays[0]
            interpolator = riseInterpolator
        }
        val bar1RiseY = ObjectAnimator.ofFloat(logoBar1, "scaleY", 0f, 1f).apply {
            duration = riseDurations
            startDelay = riseDelays[0]
            interpolator = riseInterpolator
        }
        val bar1RiseAlpha = ObjectAnimator.ofFloat(logoBar1, "alpha", 0f, 1f).apply {
            duration = riseDurations
            startDelay = riseDelays[0]
            interpolator = riseInterpolator
        }

        bar1RiseX.start()
        bar1RiseY.start()
        bar1RiseAlpha.start()

        logoAnimators.add(bar1RiseX)
        logoAnimators.add(bar1RiseY)
        logoAnimators.add(bar1RiseAlpha)

        val bar1Beat = ObjectAnimator.ofFloat(logoBar1, "scaleY", 1f, 0.55f).apply {
            duration = beatDurations[0] / 2
            startDelay = beatDelays[0]
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }
        bar1Beat.start()
        logoAnimators.add(bar1Beat)

        // ═════ BARS 2, 3, 4 (Capsule shapes, use layout height animations) ═════
        val heightBars = listOf(logoBar2, logoBar3, logoBar4)
        heightBars.forEachIndexed { index, bar ->
            val maxH = barHeightsMax[index]
            val minH = barHeightsMin[index]
            val delayIdx = index + 1

            bar.scaleX = 1f
            bar.scaleY = 1f

            val riseAlpha = ObjectAnimator.ofFloat(bar, "alpha", 0f, 1f).apply {
                duration = riseDurations
                startDelay = riseDelays[delayIdx]
                interpolator = riseInterpolator
            }
            riseAlpha.start()
            logoAnimators.add(riseAlpha)

            val riseHeight = ValueAnimator.ofInt(0, maxH).apply {
                duration = riseDurations
                startDelay = riseDelays[delayIdx]
                interpolator = riseInterpolator
                addUpdateListener { animator ->
                    val lp = bar.layoutParams
                    lp.height = animator.animatedValue as Int
                    bar.layoutParams = lp
                }
            }
            riseHeight.start()
            logoAnimators.add(riseHeight)

            val beatHeight = ValueAnimator.ofInt(maxH, minH).apply {
                duration = beatDurations[delayIdx] / 2
                startDelay = beatDelays[delayIdx]
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener { animator ->
                    val lp = bar.layoutParams
                    lp.height = animator.animatedValue as Int
                    bar.layoutParams = lp
                }
            }
            beatHeight.start()
            logoAnimators.add(beatHeight)
        }

        // ═════ CIRCLE (Same drawing & pulsing loops) ═════
        logoCircleGroup.post {
            logoCircleGroup.pivotX = logoCircleGroup.width.toFloat() / 2f
            logoCircleGroup.pivotY = logoCircleGroup.height.toFloat() / 2f
        }

        val circleDrawX = ObjectAnimator.ofFloat(logoCircleGroup, "scaleX", 0f, 1f).apply {
            duration = 1200
            startDelay = 1000
            interpolator = AccelerateDecelerateInterpolator()
        }
        val circleDrawY = ObjectAnimator.ofFloat(logoCircleGroup, "scaleY", 0f, 1f).apply {
            duration = 1200
            startDelay = 1000
            interpolator = AccelerateDecelerateInterpolator()
        }
        val circleDrawAlpha = ObjectAnimator.ofFloat(logoCircleGroup, "alpha", 0f, 1f).apply {
            duration = 1200
            startDelay = 1000
            interpolator = AccelerateDecelerateInterpolator()
        }

        circleDrawX.start()
        circleDrawY.start()
        circleDrawAlpha.start()

        logoAnimators.add(circleDrawX)
        logoAnimators.add(circleDrawY)
        logoAnimators.add(circleDrawAlpha)

        val circlePulseX = ObjectAnimator.ofFloat(logoCircleGroup, "scaleX", 1f, 1.08f).apply {
            duration = 1500
            startDelay = 2200
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }
        val circlePulseY = ObjectAnimator.ofFloat(logoCircleGroup, "scaleY", 1f, 1.08f).apply {
            duration = 1500
            startDelay = 2200
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }
        val circlePulseAlpha = ObjectAnimator.ofFloat(logoCircleGroup, "alpha", 1f, 0.6f).apply {
            duration = 1500
            startDelay = 2200
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }

        circlePulseX.start()
        circlePulseY.start()
        circlePulseAlpha.start()

        logoAnimators.add(circlePulseX)
        logoAnimators.add(circlePulseY)
        logoAnimators.add(circlePulseAlpha)
    }

    override fun onDestroy() {
        super.onDestroy()
        vinylRotationAnimator?.cancel()
        pulseRingAnimator?.cancel()
        logoAnimators.forEach { it.cancel() }
        logoAnimators.clear()
    }

    private data class OnboardingSlide(
        val iconRes: Int,
        val tag: String,
        val title: String,
        val body: String
    )

    private class SlidesAdapter(
        private val slides: List<OnboardingSlide>
    ) : RecyclerView.Adapter<SlidesAdapter.SlideViewHolder>() {

        class SlideViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val icon: ImageView = view.findViewById(R.id.iv_slide_icon)
            val tag: TextView = view.findViewById(R.id.tv_slide_tag)
            val title: TextView = view.findViewById(R.id.tv_slide_title)
            val body: TextView = view.findViewById(R.id.tv_slide_body)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SlideViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_onboarding_slide, parent, false)
            return SlideViewHolder(view)
        }

        override fun onBindViewHolder(holder: SlideViewHolder, position: Int) {
            val slide = slides[position]
            holder.icon.setImageResource(slide.iconRes)
            holder.tag.text = slide.tag
            holder.title.text = slide.title
            holder.body.text = slide.body
        }

        override fun getItemCount(): Int = slides.size
    }
}
