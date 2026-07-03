package ua.hackteam.nickcatcher

import android.content.Context

/**
 * Просте локальне сховище: ім'я гравця + чи він адмін (Максим).
 * Ім'я також дублюється у Firebase, щоб інші учасники бачили список гравців.
 */
object Prefs {
    private const val FILE = "nickcatcher_prefs"
    private const val KEY_NAME = "user_name"
    private const val KEY_UID = "user_uid"

    fun getName(ctx: Context): String? =
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY_NAME, null)

    fun setName(ctx: Context, name: String) {
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString(KEY_NAME, name).apply()
    }

    fun getOrCreateUid(ctx: Context): String {
        val sp = ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        var uid = sp.getString(KEY_UID, null)
        if (uid == null) {
            uid = "u_" + System.currentTimeMillis() + "_" + (0..9999).random()
            sp.edit().putString(KEY_UID, uid).apply()
        }
        return uid
    }

    /** Головний адмін проєкту — рівно ім'я "Максим" (без урахування регістру). */
    fun isAdminName(name: String): Boolean =
        name.trim().equals("Максим", ignoreCase = true)
}
