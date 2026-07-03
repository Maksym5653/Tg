package ua.hackteam.nickcatcher

import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var root: LinearLayout
    private lateinit var listContainer: LinearLayout
    private lateinit var totalTodayView: TextView
    private lateinit var totalAllView: TextView

    private var myName: String = ""
    private var myUid: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        myUid = Prefs.getOrCreateUid(this)

        val savedName = Prefs.getName(this)
        if (savedName == null) {
            showNamePrompt()
        } else {
            myName = savedName
            buildUi()
            Repo.registerUser(myUid, myName)
            listenData()
            ensureOverlayPermissionThenStart()
        }
    }

    override fun onResume() {
        super.onResume()
        // Якщо користувач повернувся зі "Settings" з дозволом overlay — запускаємо сервіс
        if (::root.isInitialized && Settings.canDrawOverlays(this)) {
            startOverlayService()
        }
    }

    // ---------- Екран введення імені ----------

    private fun showNamePrompt() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#0D0D0D"))
            setPadding(60, 200, 60, 60)
            gravity = Gravity.CENTER
        }

        val title = TextView(this).apply {
            text = ">> NICKCATCHER v1.0\n>> ВВЕДІТЬ СВОЄ ІМ'Я:"
            setTextColor(Color.parseColor("#00FF41"))
            typeface = Typeface.MONOSPACE
            textSize = 20f
            gravity = Gravity.CENTER
        }

        val input = EditText(this).apply {
            hint = "ім'я"
            setTextColor(Color.parseColor("#00FF41"))
            setHintTextColor(Color.parseColor("#0A8A2A"))
            typeface = Typeface.MONOSPACE
            setBackgroundColor(Color.parseColor("#111111"))
            setPadding(30, 30, 30, 30)
        }

        val btn = Button(this).apply {
            text = "ВХІД"
            setTextColor(Color.BLACK)
            setBackgroundColor(Color.parseColor("#00FF41"))
            typeface = Typeface.MONOSPACE
        }
        btn.setOnClickListener {
            val name = input.text.toString().trim()
            if (name.isNotEmpty()) {
                Prefs.setName(this, name)
                myName = name
                setContentView(View(this)) // очистка
                buildUi()
                Repo.registerUser(myUid, myName)
                listenData()
                ensureOverlayPermissionThenStart()
            }
        }

        layout.addView(title)
        val space = View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 40) }
        layout.addView(space)
        layout.addView(input)
        val space2 = View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 30) }
        layout.addView(space2)
        layout.addView(btn)

        setContentView(layout)
    }

    // ---------- Основний UI ----------

    private fun buildUi() {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0D0D0D"))
        }

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 60, 40, 60)
        }

        val header = TextView(this).apply {
            text = "root@nickcatcher:~$ " + (if (Prefs.isAdminName(myName)) "ADMIN MODE [$myName]" else "user: $myName")
            setTextColor(Color.parseColor("#00FF41"))
            typeface = Typeface.MONOSPACE
            textSize = 16f
        }
        root.addView(header)

        val divider = TextView(this).apply {
            text = "════════════════════════════════"
            setTextColor(Color.parseColor("#0A8A2A"))
            typeface = Typeface.MONOSPACE
        }
        root.addView(divider)

        listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(listContainer)

        val divider2 = TextView(this).apply {
            text = "════════════════════════════════"
            setTextColor(Color.parseColor("#0A8A2A"))
            typeface = Typeface.MONOSPACE
        }
        root.addView(divider2)

        totalTodayView = TextView(this).apply {
            setTextColor(Color.parseColor("#00FF41"))
            typeface = Typeface.MONOSPACE
            textSize = 15f
        }
        totalAllView = TextView(this).apply {
            setTextColor(Color.parseColor("#00FF41"))
            typeface = Typeface.MONOSPACE
            textSize = 15f
        }
        root.addView(totalTodayView)
        root.addView(totalAllView)

        val hint = TextView(this).apply {
            text = "\n>> Плаваюча кнопка активна поверх усіх додатків.\n>> Натисни на неї, введи @нікнейм телеграма — і отримай оцінку.\n"
            setTextColor(Color.parseColor("#0A8A2A"))
            typeface = Typeface.MONOSPACE
            textSize = 12f
        }
        root.addView(hint)

        scroll.addView(root)
        setContentView(scroll)
    }

    private fun listenData() {
        Repo.listenCatches { entries ->
            renderList(entries)
        }
    }

    private fun renderList(entries: List<CatchEntry>) {
        listContainer.removeAllViews()

        val byUser = entries.groupBy { it.ownerName }
        val sdfDay = SimpleDateFormat("yyyyMMdd", Locale.getDefault())
        val today = sdfDay.format(Date())

        var totalToday = 0
        var totalAll = 0

        for ((user, userEntries) in byUser.entries.sortedByDescending { e -> e.value.sumOf { it.price } }) {
            val nameLine = TextView(this).apply {
                text = "$user:"
                setTextColor(Color.parseColor("#00FFAA"))
                typeface = Typeface.MONOSPACE
                textSize = 16f
                setPadding(0, 24, 0, 4)
            }
            listContainer.addView(nameLine)

            var userEarned = 0
            for (entry in userEntries.sortedByDescending { it.timestamp }) {
                userEarned += entry.price
                totalAll += entry.price
                if (sdfDay.format(Date(entry.timestamp)) == today) totalToday += entry.price

                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }

                val nickLine = TextView(this).apply {
                    text = "  @${entry.nickname} (${entry.price}$)"
                    setTextColor(Color.parseColor("#00FF41"))
                    typeface = Typeface.MONOSPACE
                    textSize = 14f
                }
                row.addView(nickLine)

                if (Prefs.isAdminName(myName)) {
                    val delBtn = TextView(this).apply {
                        text = "  [x]"
                        setTextColor(Color.parseColor("#FF3333"))
                        typeface = Typeface.MONOSPACE
                        textSize = 14f
                        setOnClickListener {
                            AlertDialog.Builder(this@MainActivity)
                                .setTitle("Видалити?")
                                .setMessage("Видалити @${entry.nickname} у $user?")
                                .setPositiveButton("Так") { _, _ -> Repo.deleteCatch(entry.id) }
                                .setNegativeButton("Ні", null)
                                .show()
                        }
                    }
                    row.addView(delBtn)
                }

                listContainer.addView(row)
            }

            val earnedLine = TextView(this).apply {
                text = "  Earned: ${userEarned}$"
                setTextColor(Color.parseColor("#0A8A2A"))
                typeface = Typeface.MONOSPACE
                textSize = 13f
                setPadding(0, 4, 0, 0)
            }
            listContainer.addView(earnedLine)
        }

        totalTodayView.text = "\nEveryone earned today: ${totalToday}$"
        totalAllView.text = "Earned for all time: ${totalAll}$"
    }

    // ---------- Дозвіл на overlay ----------

    private fun ensureOverlayPermissionThenStart() {
        if (!Settings.canDrawOverlays(this)) {
            AlertDialog.Builder(this)
                .setTitle("Потрібен дозвіл")
                .setMessage("Дозволь показ поверх інших застосунків, щоб кнопка NickCatcher працювала всюди.")
                .setPositiveButton("Дозволити") { _, _ ->
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                }
                .setCancelable(false)
                .show()
        } else {
            startOverlayService()
        }
    }

    private fun startOverlayService() {
        val intent = Intent(this, OverlayService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }
}
