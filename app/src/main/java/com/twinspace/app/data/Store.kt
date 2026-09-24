package com.twinspace.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

data class CloneApp(
    val id: String,
    val catalogId: String,
    val nickname: String,
    val space: String,
    val locked: Boolean,
    val hidden: Boolean,
    val notes: String,
    val createdAt: Long,
    val lastOpenedAt: Long,
)

class TwinStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("twinspace.v1", Context.MODE_PRIVATE)

    fun pinHash(): String? = prefs.getString("pinHash", null)
    fun disguise(): Boolean = prefs.getBoolean("disguise", false)
    fun relockSecret(): Boolean = prefs.getBoolean("relockSecret", false)
    fun showHidden(): Boolean = prefs.getBoolean("showHidden", false)

    fun setDisguise(value: Boolean) = prefs.edit().putBoolean("disguise", value).apply()
    fun setRelockSecret(value: Boolean) = prefs.edit().putBoolean("relockSecret", value).apply()
    fun setShowHidden(value: Boolean) = prefs.edit().putBoolean("showHidden", value).apply()

    fun hasPin(): Boolean = pinHash() != null

    fun setPin(pin: String) {
        prefs.edit().putString("pinHash", hashPin(pin)).apply()
    }

    fun checkPin(pin: String): Boolean {
        val expected = pinHash() ?: return false
        return hashPin(pin) == expected
    }

    fun changePin(old: String, next: String): Boolean {
        if (!checkPin(old)) return false
        setPin(next)
        return true
    }

    fun clones(): List<CloneApp> {
        val raw = prefs.getString("clones", "[]") ?: "[]"
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            CloneApp(
                id = o.getString("id"),
                catalogId = o.getString("catalogId"),
                nickname = o.getString("nickname"),
                space = o.getString("space"),
                locked = o.optBoolean("locked"),
                hidden = o.optBoolean("hidden"),
                notes = o.optString("notes"),
                createdAt = o.optLong("createdAt"),
                lastOpenedAt = o.optLong("lastOpenedAt"),
            )
        }
    }

    fun saveClones(list: List<CloneApp>) {
        val arr = JSONArray()
        list.forEach { c ->
            arr.put(
                JSONObject().apply {
                    put("id", c.id)
                    put("catalogId", c.catalogId)
                    put("nickname", c.nickname)
                    put("space", c.space)
                    put("locked", c.locked)
                    put("hidden", c.hidden)
                    put("notes", c.notes)
                    put("createdAt", c.createdAt)
                    put("lastOpenedAt", c.lastOpenedAt)
                },
            )
        }
        prefs.edit().putString("clones", arr.toString()).apply()
    }

    fun addClone(catalogId: String, space: String, nickname: String): CloneApp {
        val clone = CloneApp(
            id = UUID.randomUUID().toString(),
            catalogId = catalogId,
            nickname = nickname,
            space = space,
            locked = false,
            hidden = false,
            notes = "",
            createdAt = System.currentTimeMillis(),
            lastOpenedAt = 0L,
        )
        saveClones(clones() + clone)
        return clone
    }

    fun update(id: String, transform: (CloneApp) -> CloneApp) {
        saveClones(clones().map { if (it.id == id) transform(it) else it })
    }

    fun remove(id: String) {
        saveClones(clones().filterNot { it.id == id })
    }

    fun reset() {
        prefs.edit().clear().apply()
    }

    companion object {
        fun hashPin(pin: String): String {
            val md = MessageDigest.getInstance("SHA-256")
            val bytes = md.digest("twinspace.v1.$pin".toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}
