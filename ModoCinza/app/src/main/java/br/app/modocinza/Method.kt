package br.app.modocinza

import android.content.Context
import android.os.Build

/** As três formas de deixar a tela cinza, da mais simples para a mais técnica. */
enum class Method(
    val minSdk: Int,
    val androidVersion: String,
    val title: String,
    val description: String
) {
    MODES(
        Build.VERSION_CODES.VANILLA_ICE_CREAM, "15",
        "Modos do Android",
        "Usa o efeito \"escala de cinza\" dos Modos. Pede só o acesso ao \"Não perturbe\", " +
            "mas não silencia nada."
    ),
    SHORTCUT(
        Build.VERSION_CODES.S, "12",
        "Atalho de acessibilidade",
        "O app aciona o atalho das teclas de volume, configurado para a correção de cor."
    ),
    ADB(
        Build.VERSION_CODES.O, "8",
        "Permissão via ADB",
        "A troca é mais rápida e precisa, mas exige um comando no computador uma vez."
    );

    fun isSupported(): Boolean = Build.VERSION.SDK_INT >= minSdk
}

/** Liga/desliga a escala de cinza pelo método escolhido. */
object GrayController {

    fun recommended(context: Context): Method = when {
        AdbGray.hasPermission(context) -> Method.ADB
        Method.MODES.isSupported() -> Method.MODES
        Method.SHORTCUT.isSupported() -> Method.SHORTCUT
        else -> Method.ADB
    }

    fun method(context: Context): Method =
        AppPrefs.method(context)?.takeIf { it.isSupported() } ?: recommended(context)

    fun set(context: Context, on: Boolean): Boolean = when (method(context)) {
        Method.ADB -> if (on) AdbGray.apply(context) else AdbGray.restore(context)
        Method.MODES ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                ModesGray.set(context, on)
            } else {
                false
            }
        Method.SHORTCUT -> GrayscaleService.instance?.setGrayViaShortcut(on) ?: false
    }

    /** true = pronto; false = falta configurar; null = não dá para confirmar (use Testar). */
    fun methodReady(context: Context, method: Method): Boolean? = when (method) {
        Method.ADB -> AdbGray.hasPermission(context)
        Method.MODES ->
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM &&
                ModesGray.hasAccess(context)
        Method.SHORTCUT -> ShortcutGray.isConfigured(context)
    }
}
