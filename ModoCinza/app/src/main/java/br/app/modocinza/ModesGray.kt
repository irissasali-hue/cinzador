package br.app.modocinza

import android.app.AutomaticZenRule
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Build
import android.service.notification.Condition
import android.service.notification.ZenDeviceEffects
import androidx.annotation.RequiresApi

/**
 * Método "Modos do Android" (Android 15+).
 *
 * Cria um Modo chamado "Modo Cinza" que NÃO silencia nada
 * (INTERRUPTION_FILTER_ALL) e só tem o efeito "escala de cinza".
 * O app liga esse Modo ao abrir um app escolhido e desliga ao sair.
 * Precisa apenas do acesso ao "Não perturbe", que o usuário dá numa chave.
 */
@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
object ModesGray {
    private val CONDITION_ID: Uri = Uri.parse("condition://br.app.modocinza/grayscale")
    private const val KEY_RULE_ID = "modes_rule_id"

    private fun nm(context: Context): NotificationManager =
        context.getSystemService(NotificationManager::class.java)

    fun hasAccess(context: Context): Boolean = nm(context).isNotificationPolicyAccessGranted

    fun set(context: Context, on: Boolean): Boolean {
        return try {
            val id = if (on) ensureRule(context) else savedRuleId(context)
            if (id == null) return !on
            val state = if (on) Condition.STATE_TRUE else Condition.STATE_FALSE
            nm(context).setAutomaticZenRuleState(
                id, Condition(CONDITION_ID, context.getString(R.string.app_name), state)
            )
            true
        } catch (e: SecurityException) {
            false
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    private fun savedRuleId(context: Context): String? =
        AppPrefs.prefs(context).getString(KEY_RULE_ID, null)

    /** Cria o Modo na primeira vez (ou de novo, se o usuário o apagou). */
    private fun ensureRule(context: Context): String? {
        val nm = nm(context)
        savedRuleId(context)?.let { id ->
            if (nm.getAutomaticZenRule(id) != null) return id
        }
        val rule = AutomaticZenRule.Builder(context.getString(R.string.app_name), CONDITION_ID)
            .setType(AutomaticZenRule.TYPE_OTHER)
            .setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            .setDeviceEffects(
                ZenDeviceEffects.Builder()
                    .setShouldDisplayGrayscale(true)
                    .build()
            )
            .setConfigurationActivity(ComponentName(context, MainActivity::class.java))
            .setIconResId(R.drawable.ic_mode)
            .setTriggerDescription(context.getString(R.string.mode_trigger))
            .build()
        val id: String = nm.addAutomaticZenRule(rule) ?: return null
        AppPrefs.prefs(context).edit().putString(KEY_RULE_ID, id).apply()
        return id
    }
}
