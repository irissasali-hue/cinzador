package br.app.modocinza

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings

/**
 * Método ADB: escreve direto na "Correção de cor" do sistema.
 * Precisa da permissão WRITE_SECURE_SETTINGS, dada uma vez com:
 * adb shell pm grant br.app.modocinza android.permission.WRITE_SECURE_SETTINGS
 *
 * Antes de ligar, salva a correção de cor que o usuário já usava e a devolve depois.
 */
object AdbGray {
    private const val KEY_APPLIED = "gray_applied"
    private const val KEY_ORIG_ENABLED = "gray_orig_enabled"
    private const val KEY_ORIG_MODE = "gray_orig_mode"

    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

    fun apply(context: Context): Boolean {
        if (!hasPermission(context)) return false
        val prefs = AppPrefs.prefs(context)
        if (prefs.getBoolean(KEY_APPLIED, false)) return true
        val origEnabled = Daltonizer.readInt(context, Daltonizer.ENABLED, 0) ?: 0
        val origMode = Daltonizer.readInt(context, Daltonizer.MODE, Daltonizer.MODE_ANDROID_DEFAULT)
            ?: Daltonizer.MODE_ANDROID_DEFAULT
        val cr = context.contentResolver
        return try {
            prefs.edit()
                .putBoolean(KEY_APPLIED, true)
                .putInt(KEY_ORIG_ENABLED, origEnabled)
                .putInt(KEY_ORIG_MODE, origMode)
                .commit()
            Settings.Secure.putInt(cr, Daltonizer.MODE, Daltonizer.MODE_GRAYSCALE)
            Settings.Secure.putInt(cr, Daltonizer.ENABLED, 1)
            true
        } catch (e: SecurityException) {
            prefs.edit().putBoolean(KEY_APPLIED, false).apply()
            false
        }
    }

    fun restore(context: Context): Boolean {
        val prefs = AppPrefs.prefs(context)
        if (!prefs.getBoolean(KEY_APPLIED, false)) return true
        val cr = context.contentResolver
        return try {
            Settings.Secure.putInt(cr, Daltonizer.ENABLED, prefs.getInt(KEY_ORIG_ENABLED, 0))
            Settings.Secure.putInt(
                cr, Daltonizer.MODE,
                prefs.getInt(KEY_ORIG_MODE, Daltonizer.MODE_ANDROID_DEFAULT)
            )
            prefs.edit().putBoolean(KEY_APPLIED, false).apply()
            true
        } catch (e: SecurityException) {
            false
        }
    }
}
