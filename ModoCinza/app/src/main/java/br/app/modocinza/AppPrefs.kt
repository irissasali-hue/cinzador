package br.app.modocinza

import android.content.Context
import android.content.SharedPreferences

/** Guarda a lista de apps escolhidos e o liga/desliga geral. */
object AppPrefs {
    private const val FILE = "modo_cinza"
    private const val KEY_SELECTED = "selected_packages"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_METHOD = "method"

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun selected(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_SELECTED, emptySet())?.toSet() ?: emptySet()

    fun setSelected(context: Context, packages: Set<String>) {
        prefs(context).edit().putStringSet(KEY_SELECTED, HashSet(packages)).apply()
    }

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /** Método escolhido pelo usuário; null = automático (o recomendado). */
    fun method(context: Context): Method? =
        prefs(context).getString(KEY_METHOD, null)
            ?.let { name -> Method.entries.firstOrNull { it.name == name } }

    fun setMethod(context: Context, method: Method) {
        prefs(context).edit().putString(KEY_METHOD, method.name).apply()
    }
}
