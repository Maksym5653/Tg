package ua.hackteam.nickcatcher

import android.app.*
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var bubbleView: View? = null
    private var popupView: View? = null

    private var popupVisible = false
    private val scope = CoroutineScope(Dispatchers.Main)

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

    // ---------- Плаваюча кнопка ----------

    private fun addBubble() {
        val size = dp(56)
        val bubble = FrameLayout(this)
        val circle = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(Color.parseColor("#0D0D0D"))
            setStroke(dp(2), Color.parseColor("#00FF41"))
        }
        bubble.background = circle
        val icon = TextView(this).apply {
            text = "$"
            setTextColor(Color.parseColor("#00FF41"))
            typeface = Typeface.MONOSPACE
            textSize = 22f
            gravity = Gravity.CENTER
        }
        bubble.addView(icon, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
        ))

        val params = WindowManager.LayoutParams(
            size, size,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 20
        params.y = 300

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDrag = false

        bubble.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDrag = false
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
                    if (!isDrag) togglePopup()
                    true
                }
                else -> false
            }
        }

        windowManager.addView(bubble, params)
        bubbleView = bubble
    }

    // ---------- Попап введення ніку ----------

    private fun togglePopup() {
        if (popupVisible) {
            closePopup()
        } else {
            openPopup()
        }
    }

    private fun openPopup() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(20), dp(24), dp(20))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0D0D0D"))
                setStroke(dp(2), Color.parseColor("#00FF41"))
                cornerRadius = dp(8).toFloat()
            }
        }

        val title = TextView(this).apply {
            text = ">> ОЦІНКА НІКНЕЙМУ TELEGRAM"
            setTextColor(Color.parseColor("#00FF41"))
            typeface = Typeface.MONOSPACE
            textSize = 14f
        }
        container.addView(title)

        val input = EditText(this).apply {
            hint = "@nickname"
            setHintTextColor(Color.parseColor("#0A8A2A"))
            setTextColor(Color.parseColor("#00FF41"))
            typeface = Typeface.MONOSPACE
            setBackgroundColor(Color.parseColor("#111111"))
            setPadding(dp(12), dp(10), dp(12), dp(10))
            setSingleLine(true)
        }
        container.addView(input, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(10) })

        val resultText = TextView(this).apply {
            text = ""
            setTextColor(Color.parseColor("#00FFAA"))
            typeface = Typeface.MONOSPACE
            textSize = 14f
        }

        val evalBtn = Button(this).apply {
            text = "ОЦІНИТИ"
            setTextColor(Color.BLACK)
            setBackgroundColor(Color.parseColor("#00FF41"))
            typeface = Typeface.MONOSPACE
            isAllCaps = false
        }
        evalBtn.setOnClickListener {
            val nick = input.text.toString().trim().removePrefix("@")
            if (nick.isEmpty()) return@setOnClickListener
            resultText.text = "сканування...(searching)"
            scope.launch {
                val result = NicknameValuator.estimate(nick)
                val sourceTag = if (result.source == "gemini") "gemini" else "locally"
                resultText.text = "@$nick ≈ ${result.price}$" +
                    (if (result.taken) " [зайнятий]" else " [вільний]") +
                    " ($sourceTag)"

                val uid = Prefs.getOrCreateUid(this@OverlayService)
                val name = Prefs.getName(this@OverlayService) ?: "Гість"
                Repo.addCatch(uid, name, nick, result.price)
            }
        }
        container.addView(evalBtn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(10) })

        container.addView(resultText, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(10) })

        val closeHint = TextView(this).apply {
            text = "натисни $-кнопку ще раз, щоб закрити"
            setTextColor(Color.parseColor("#0A8A2A"))
            typeface = Typeface.MONOSPACE
            textSize = 10f
        }
        container.addView(closeHint, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) })

        val params = WindowManager.LayoutParams(
            dp(280), WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 40
        params.y = 400

        windowManager.addView(container, params)
        popupView = container
        popupVisible = true
    }

    private fun closePopup() {
        popupView?.let { runCatching { windowManager.removeView(it) } }
        popupView = null
        popupVisible = false
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
