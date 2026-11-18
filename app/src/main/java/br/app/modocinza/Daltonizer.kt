package br.app.modocinza

import android.content.Context
import android.provider.Settings

/** Leitura da "Correção de cor" do Android (é ela que faz a escala de cinza). */
object Daltonizer {
    const val ENABLED = "accessibility_display_daltonizer_enabled"
    const val MODE = "accessibility_display_daltonizer"
    const val MODE_GRAYSCALE = 0
    const val MODE_ANDROID_DEFAULT = 12 // deuteranomalia, padrão do Android

    /** Retorna null se o Android não deixar ler a configuração. */
    fun readInt(context: Context, key: String, default: Int): Int? = try {
        Settings.Secure.getInt(context.contentResolver, key, default)
    } catch (e: SecurityException) {
        null
    }

    fun isOn(context: Context): Boolean? =
        readInt(context, ENABLED, 0)?.let { it == 1 }

    fun isGrayscaleMode(context: Context): Boolean? =
        readInt(context, MODE, MODE_ANDROID_DEFAULT)?.let { it == MODE_GRAYSCALE }
}
