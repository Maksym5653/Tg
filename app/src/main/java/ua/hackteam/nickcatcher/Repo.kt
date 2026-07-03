package ua.hackteam.nickcatcher

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

/**
 * Модель одного "зловленого" ніку.
 */
data class CatchEntry(
    var id: String = "",
    var ownerUid: String = "",
    var ownerName: String = "",
    var nickname: String = "",
    var price: Int = 0,
    var timestamp: Long = 0L
)

object Repo {
    private val db by lazy { FirebaseDatabase.getInstance().reference }

    fun addCatch(uid: String, name: String, nickname: String, price: Int) {
        val ref = db.child("catches").push()
        val entry = mapOf(
            "ownerUid" to uid,
            "ownerName" to name,
            "nickname" to nickname,
            "price" to price,
            "timestamp" to System.currentTimeMillis()
        )
        ref.setValue(entry)
    }

    fun deleteCatch(entryId: String) {
        db.child("catches").child(entryId).removeValue()
    }

    fun listenCatches(onChange: (List<CatchEntry>) -> Unit) {
        db.child("catches").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<CatchEntry>()
                for (child in snapshot.children) {
                    val entry = CatchEntry(
                        id = child.key ?: continue,
                        ownerUid = child.child("ownerUid").getValue(String::class.java) ?: "",
                        ownerName = child.child("ownerName").getValue(String::class.java) ?: "",
                        nickname = child.child("nickname").getValue(String::class.java) ?: "",
                        price = child.child("price").getValue(Int::class.java) ?: 0,
                        timestamp = child.child("timestamp").getValue(Long::class.java) ?: 0L
                    )
                    list.add(entry)
                }
                onChange(list.sortedByDescending { it.timestamp })
            }

            override fun onCancelled(error: com.google.firebase.database.DatabaseError) {
                // Ігноруємо — UI просто не оновиться цього разу
            }
        })
    }

    /** Реєструє / оновлює ім'я гравця у спільному списку користувачів. */
    fun registerUser(uid: String, name: String) {
        db.child("users").child(uid).setValue(
            mapOf("name" to name, "isAdmin" to Prefs.isAdminName(name))
        )
    }
}
