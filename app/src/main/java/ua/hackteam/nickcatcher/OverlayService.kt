package ua.hackteam.nickcatcher

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.*
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.CountDownTimer
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Плаваюча кнопка поверх усіх додатків + попап оцінки нікнейму.
 * Стиль: "матричний" хакерський неон, плавні анімації появи/масштабування,
 * пульсація кнопки, друкарський ефект заголовка, живе оновлення статусу.
 */
class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var popupView: View? = null
    private var glowRing: View? = null

    private var popupVisible = false
    private val scope = CoroutineScope(Dispatchers.Main)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val neonGreen = Color.parseColor("#00FF41")
    private val neonGreenSoft = Color.parseColor("#0A8A2A")
    private val neonCyan = Color.parseColor("#00FFC8")
    private val bgBlack = Color.parseColor("#0A0F0A")
    private val bgInput = Color.parseColor("#111811")
    private val dangerRed = Color.parseColor("#FF3B3B")

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundNotification()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        addBubble()
    }

    override fun onDestroy() {
        super.onDestroy()
        bubbleView?.let { runCatching { windowManager.removeView(it) } }
        popupView?.let { runCatching { windowManager.removeView(it) } }
    }

    private fun startForegroundNotification() {
        val channelId = "nickcatcher_overlay"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "NickCatcher Overlay", NotificationManager.IMPORTANCE_MIN
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("NickCatcher активний")
            .setContentText("Кнопка оцінки ніків працює поверх додатків")
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .build()
        startForeground(1, notification)
    }

    // ========================= ПЛАВАЮЧА КНОПКА =========================

    private fun addBubble() {
        val size = dp(58)

        val bubble = FrameLayout(this)

        // Зовнішнє неонове сяйво (glow) — окремий шар під основним колом
        val glow = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.TRANSPARENT)
            setStroke(dp(1), Color.argb(90, 0, 255, 65))
        }
        val glowView = View(this).apply { background = glow }
        bubble.addView(glowView, FrameLayout.LayoutParams(size, size))
        glowRing = glowView

        // Основне коло кнопки з градієнтом
        val circle = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.parseColor("#101710"), Color.parseColor("#050805"))
        ).apply {
            shape = GradientDrawable.OVAL
            setStroke(dp(2), neonGreen)
        }
        val circleView = View(this).apply { background = circle }
        bubble.addView(
            circleView,
            FrameLayout.LayoutParams(size - dp(6), size - dp(6)).apply {
                gravity = Gravity.CENTER
            }
        )

        val icon = TextView(this).apply {
            text = "$"
            setTextColor(neonGreen)
            typeface = Typeface.MONOSPACE
            textSize = 24f
            gravity = Gravity.CENTER
        }
        bubble.addView(icon, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))

        // Постійна "дихаюча" пульсація сяйва
        startPulseAnimation(glowView)

        val params = WindowManager.LayoutParams(
            size, size,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            android.graphics.PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 20
        params.y = 300

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDrag = false

        bubble.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDrag = false
                    view.animate().scaleX(0.9f).scaleY(0.9f).setDuration(100).start()
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (kotlin.math.abs(dx) > 10 || kotlin.math.abs(dy) > 10) isDrag = true
                    params.x = initialX + dx
                    params.y = initialY + dy
                    windowManager.updateViewLayout(bubble, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    view.animate().scaleX(1f).scaleY(1f).setDuration(150)
                        .setInterpolator(OvershootInterpolator()).start()
                    if (!isDrag) togglePopup()
                    true
                }
                else -> false
            }
        }

        windowManager.addView(bubble, params)
        bubbleView = bubble
    }

    private fun startPulseAnimation(target: View) {
        val animator = ValueAnimator.ofFloat(1f, 1.35f).apply {
            duration = 1400
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            addUpdateListener { anim ->
                val scale = anim.animatedValue as Float
                target.scaleX = scale
                target.scaleY = scale
                target.alpha = 1f - (scale - 1f)
            }
        }
        animator.start()
    }

    // ========================= ПОПАП ВВЕДЕННЯ НІКУ =========================

    private fun togglePopup() {
        if (popupVisible) closePopup() else openPopup()
    }

    private fun openPopup() {
        val screenWidth = resources.displayMetrics.widthPixels
        val popupWidth = minOf(dp(300), (screenWidth * 0.85).toInt())

        // Зовнішній контейнер з подвійною рамкою (зовнішня тьмяна + внутрішня яскрава)
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.argb(235, 5, 10, 5))
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), Color.argb(120, 0, 255, 65))
            }
        }

        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(22), dp(18), dp(22), dp(18))
            background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                cornerRadius = dp(13).toFloat()
                setStroke(dp(2), neonGreen)
            }
        }

        // Заголовок з "лінією терміналу"
        val titleRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val dot = TextView(this).apply {
            text = "●  "
            setTextColor(neonGreen)
            textSize = 12f
        }
        val title = TextView(this).apply {
            text = ""
            setTextColor(neonGreen)
            typeface = Typeface.MONOSPACE
            textSize = 14f
            letterSpacing = 0.05f
        }
        titleRow.addView(dot)
        titleRow.addView(title)
        inner.addView(titleRow)
        typewriterEffect(title, "ОЦІНКА НІКНЕЙМУ TG_")

        val divider = View(this).apply {
            setBackgroundColor(Color.argb(90, 0, 255, 65))
        }
        inner.addView(divider, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(1)
        ).apply { topMargin = dp(10); bottomMargin = dp(14) })

        // Поле вводу з підсвіченою рамкою при фокусі
        val inputWrapper = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply {
                setColor(bgInput)
                cornerRadius = dp(8).toFloat()
                setStroke(dp(1), neonGreenSoft)
            }
            setPadding(dp(12), dp(2), dp(12), dp(2))
        }
        val prefixAt = TextView(this).apply {
            text = "@"
            setTextColor(neonGreenSoft)
            typeface = Typeface.MONOSPACE
            textSize = 15f
        }
        val input = EditText(this).apply {
            hint = "nickname"
            setHintTextColor(Color.argb(150, 10, 138, 42))
            setTextColor(neonGreen)
            typeface = Typeface.MONOSPACE
            textSize = 15f
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(dp(6), dp(10), dp(6), dp(10))
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
        }
        inputWrapper.addView(prefixAt)
        inputWrapper.addView(input, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val inputBg = inputWrapper.background as GradientDrawable
        input.setOnFocusChangeListener { _, hasFocus ->
            inputBg.setStroke(dp(if (hasFocus) 2 else 1), if (hasFocus) neonCyan else neonGreenSoft)
        }

        inner.addView(inputWrapper, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(4) })

        // Кнопка "ОЦІНИТИ" з градієнтом і натисканням
        val evalBtn = TextView(this).apply {
            text = "▶  ОЦІНИТИ"
            setTextColor(Color.parseColor("#04140A"))
            typeface = Typeface.MONOSPACE
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(12))
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(neonGreen, neonCyan)
            ).apply { cornerRadius = dp(8).toFloat() }
        }
        inner.addView(evalBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(14) })

        // Блок результату (спочатку прихований)
        val resultCard = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            background = GradientDrawable().apply {
                setColor(Color.argb(60, 0, 255, 65))
                cornerRadius = dp(8).toFloat()
            }
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        val resultText = TextView(this).apply {
            text = ""
            setTextColor(neonCyan)
            typeface = Typeface.MONOSPACE
            textSize = 15f
        }
        val resultSub = TextView(this).apply {
            text = ""
            setTextColor(neonGreenSoft)
            typeface = Typeface.MONOSPACE
            textSize = 11f
        }
        resultCard.addView(resultText)
        resultCard.addView(resultSub)
        inner.addView(resultCard, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(12) })

        val scanningDots = TextView(this).apply {
            text = ""
            setTextColor(neonGreenSoft)
            typeface = Typeface.MONOSPACE
            textSize = 12f
            visibility = View.GONE
        }
        inner.addView(scanningDots, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) })

        fun runEvaluation() {
            val nick = input.text.toString().trim().removePrefix("@")
            if (nick.isEmpty()) {
                shake(inputWrapper)
                return
            }
            hideKeyboard(input)
            resultCard.visibility = View.GONE
            scanningDots.visibility = View.VISIBLE
            animateScanningDots(scanningDots)

            evalBtn.animate().alpha(0.5f).setDuration(150).start()

            scope.launch {
                val result = NicknameValuator.estimate(nick)
                scanningDots.visibility = View.GONE
                evalBtn.animate().alpha(1f).setDuration(150).start()

                val sourceTag = if (result.source == "gemini") "gemini search" else "local heuristic"
                resultText.text = "@$nick  →  0$"
                resultSub.text = (if (result.taken) "● зайнятий" else "○ вільний") + "   [$sourceTag]"

                resultCard.visibility = View.VISIBLE
                resultCard.alpha = 0f
                resultCard.scaleX = 0.9f
                resultCard.scaleY = 0.9f
                resultCard.animate()
                    .alpha(1f).scaleX(1f).scaleY(1f)
                    .setDuration(250)
                    .setInterpolator(OvershootInterpolator())
                    .start()

                animateCountUp(resultText, "@$nick  →  ", "$", result.price)

                val uid = Prefs.getOrCreateUid(this@OverlayService)
                val name = Prefs.getName(this@OverlayService) ?: "Гість"
                Repo.addCatch(uid, name, nick, result.price)
            }
        }

        evalBtn.setOnClickListener {
            evalBtn.animate().scaleX(0.95f).scaleY(0.95f).setDuration(80)
                .withEndAction {
                    evalBtn.animate().scaleX(1f).scaleY(1f).setDuration(120).start()
                    runEvaluation()
                }.start()
        }
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE) {
                runEvaluation(); true
            } else false
        }

        val closeHint = TextView(this).apply {
            text = "тисни $-кнопку ще раз, щоб закрити ✕"
            setTextColor(Color.argb(150, 10, 138, 42))
            typeface = Typeface.MONOSPACE
            textSize = 10f
            gravity = Gravity.CENTER
        }
        inner.addView(closeHint, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(12) })

        outer.addView(inner, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { setMargins(dp(2), dp(2), dp(2), dp(2)) })

        // ВАЖЛИВО: без FLAG_ALT_FOCUSABLE_IM і без FLAG_NOT_FOCUSABLE —
        // саме це дозволяє відкритись клавіатурі. Вікно фокусабельне,
        // тому клавіатура зʼявляється, коли тапаєш по EditText.
        val params = WindowManager.LayoutParams(
            popupWidth, WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            android.graphics.PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = (screenWidth - popupWidth) / 2
        params.y = 260
        params.softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE

        // Анімація появи: масштаб + fade-in
        outer.alpha = 0f
        outer.scaleX = 0.85f
        outer.scaleY = 0.85f

        windowManager.addView(outer, params)
        popupView = outer
        popupVisible = true

        outer.animate()
            .alpha(1f).scaleX(1f).scaleY(1f)
            .setDuration(220)
            .setInterpolator(OvershootInterpolator(1.1f))
            .start()

        // Автофокус + клавіатура одразу після появи анімації
        mainHandler.postDelayed({
            input.requestFocus()
            val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }, 150)
    }

    private fun closePopup() {
        val view = popupView ?: return
        view.animate()
            .alpha(0f).scaleX(0.85f).scaleY(0.85f)
            .setDuration(160)
            .setListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    runCatching { windowManager.removeView(view) }
                }
            })
            .start()
        popupView = null
        popupVisible = false
    }

    // ========================= ЕФЕКТИ =========================

    private fun typewriterEffect(target: TextView, fullText: String) {
        var i = 0
        val delayMs = 28L
        val runnable = object : Runnable {
            override fun run() {
                if (i <= fullText.length) {
                    target.text = fullText.substring(0, i)
                    i++
                    mainHandler.postDelayed(this, delayMs)
                }
            }
        }
        mainHandler.post(runnable)
    }

    private fun animateScanningDots(target: TextView) {
        val frames = listOf(
            "[■□□□□□] сканування мережі...",
            "[■■■□□□] аналіз патернів...",
            "[■■■■■□] запит до Gemini...",
            "[■■■■■■] готово"
        )
        var i = 0
        val runnable = object : Runnable {
            override fun run() {
                if (target.visibility != View.VISIBLE) return
                target.text = frames[i % frames.size]
                i++
                mainHandler.postDelayed(this, 380)
            }
        }
        mainHandler.post(runnable)
    }

    private fun animateCountUp(target: TextView, prefix: String, suffix: String, finalValue: Int) {
        if (finalValue <= 0) {
            target.text = "$prefix$finalValue$suffix"
            return
        }
        val animator = ValueAnimator.ofInt(0, finalValue).apply {
            duration = 600
            addUpdateListener { anim ->
                target.text = "$prefix${anim.animatedValue}$suffix"
            }
        }
        animator.start()
    }

    private fun shake(view: View) {
        val animator = ObjectAnimatorShake(view)
        animator.start()
    }

    private fun ObjectAnimatorShake(view: View): ValueAnimator {
        val originalX = view.translationX
        return ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 350
            addUpdateListener { anim ->
                val t = anim.animatedFraction
                val offset = (kotlin.math.sin(t * Math.PI * 6) * dp(6) * (1 - t)).toFloat()
                view.translationX = originalX + offset
            }
        }
    }

    private fun hideKeyboard(view: View) {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
