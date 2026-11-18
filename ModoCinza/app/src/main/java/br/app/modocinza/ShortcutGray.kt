package br.app.modocinza

import android.content.Context
import android.provider.Settings

/**
 * Método "Atalho de acessibilidade" (Android 12+).
 *
 * O usuário configura, uma vez, o atalho das teclas de volume para ligar a
 * "Correção de cor" no modo "Escala de cinza". O serviço de acessibilidade
 * aciona esse atalho (GLOBAL_ACTION_ACCESSIBILITY_SHORTCUT) quando precisa.
 * O acionamento em si fica em GrayscaleService.setGrayViaShortcut().
 */
object ShortcutGray {
    private const val SHORTCUT_TARGETS = "accessibility_shortcut_target_service"

    /** O atalho das teclas de volume aponta para a correção de cor? null = não dá para ler. */
    fun isShortcutAssigned(context: Context): Boolean? = try {
        Settings.Secure.getString(context.contentResolver, SHORTCUT_TARGETS)
            ?.contains("Daltonizer") ?: false
    } catch (e: SecurityException) {
        null
    }

    fun isConfigured(context: Context): Boolean? {
        val assigned = isShortcutAssigned(context)
        val grayMode = Daltonizer.isGrayscaleMode(context)
        if (grayMode == false) return false
        if (assigned == true && grayMode == true) return true
        // Alguns fabricantes guardam o atalho em outro lugar: aí não dá para confirmar.
        return null
    }
}
