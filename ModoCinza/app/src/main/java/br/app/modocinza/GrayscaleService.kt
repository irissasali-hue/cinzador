package br.app.modocinza

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/**
 * Serviço de acessibilidade que só observa qual app veio para a frente.
 * Se o app estiver na lista, liga a escala de cinza; senão, desliga.
 * Ele não lê o conteúdo da tela.
 */
class GrayscaleService : AccessibilityService() {

    /** "pacote/classe" -> é uma Activity? (evita consultar o sistema toda hora) */
    private val activityCache = HashMap<String, Boolean>()

    // Estado do método "atalho de acessibilidade" (que funciona como liga/desliga).
    private val handler = Handler(Looper.getMainLooper())
    private var shortcutWant = false
    private var shortcutAssumed = false
    private var lastShortcutAt = 0L
    private val shortcutSync = Runnable { syncShortcut() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        // Se o serviço tinha sido encerrado com a tela cinza, volta ao normal.
        if (GrayController.method(this) != Method.SHORTCUT) GrayController.set(this, false)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        val cls = event.className?.toString() ?: return

        // Ignora teclado, barra de notificações, toasts, pop-ups etc.:
        // só conta quando uma tela (Activity) de um app vem para a frente.
        if (!isActivity(pkg, cls)) return

        val shouldBeGray = AppPrefs.isEnabled(this) && pkg in AppPrefs.selected(this)
        GrayController.set(this, shouldBeGray)
    }

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        handler.removeCallbacks(shortcutSync)
        if (GrayController.method(this) == Method.SHORTCUT) {
            shortcutWant = false
            lastShortcutAt = 0L
            syncShortcut()
        } else {
            GrayController.set(this, false)
        }
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (instance === this) instance = null
        super.onDestroy()
    }

    /**
     * O atalho só sabe alternar (liga/desliga). Por isso lemos o estado atual
     * antes de acionar e esperamos um pouco entre um acionamento e outro,
     * para não alternar duas vezes quando você troca de app rápido.
     */
    fun setGrayViaShortcut(on: Boolean): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false
        shortcutWant = on
        handler.removeCallbacks(shortcutSync)
        handler.postDelayed(shortcutSync, 150)
        return true
    }

    private fun syncShortcut() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val wait = lastShortcutAt + 700 - SystemClock.uptimeMillis()
        if (wait > 0) {
            handler.postDelayed(shortcutSync, wait)
            return
        }
        val isOn = Daltonizer.isOn(this) ?: shortcutAssumed
        if (isOn != shortcutWant &&
            performGlobalAction(AccessibilityService.GLOBAL_ACTION_ACCESSIBILITY_SHORTCUT)
        ) {
            lastShortcutAt = SystemClock.uptimeMillis()
            shortcutAssumed = shortcutWant
        }
    }

    @Suppress("DEPRECATION")
    private fun isActivity(pkg: String, cls: String): Boolean =
        activityCache.getOrPut("$pkg/$cls") {
            try {
                packageManager.getActivityInfo(ComponentName(pkg, cls), 0)
                true
            } catch (e: PackageManager.NameNotFoundException) {
                false
            }
        }

    companion object {
        /** Serviço em execução (o método do atalho precisa dele para acionar o atalho). */
        @Volatile
        var instance: GrayscaleService? = null
            private set

        private fun component(context: Context) =
            ComponentName(context, GrayscaleService::class.java)

        fun isEnabled(context: Context): Boolean {
            val flat = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val me = component(context)
            return flat.split(':').any { ComponentName.unflattenFromString(it) == me }
        }

        /**
         * Só com a permissão do ADB: o app liga o próprio serviço, sem passar
         * pela tela de Acessibilidade.
         */
        fun enableWithSecureSettings(context: Context): Boolean {
            val cr = context.contentResolver
            return try {
                val me = component(context)
                val current = Settings.Secure.getString(
                    cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
                ).orEmpty()
                val list = current.split(':').filter { it.isNotBlank() }.toMutableList()
                if (list.none { ComponentName.unflattenFromString(it) == me }) {
                    list += me.flattenToString()
                }
                Settings.Secure.putString(
                    cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, list.joinToString(":")
                )
                Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
                true
            } catch (e: SecurityException) {
                false
            }
        }
    }
}
