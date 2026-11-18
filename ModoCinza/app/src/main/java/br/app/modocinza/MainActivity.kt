package br.app.modocinza

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    /** Muda a cada onResume, para reler o status ao voltar das Configurações. */
    private val resumeTick = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AppTheme {
                MainScreen(resumeTick.intValue)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeTick.intValue++
    }
}

@Composable
private fun AppTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colors, content = content)
}

data class AppEntry(val packageName: String, val label: String)

@Suppress("DEPRECATION")
private fun loadLaunchableApps(context: Context): List<AppEntry> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(intent, 0)
        .asSequence()
        .map {
            AppEntry(
                packageName = it.activityInfo.packageName,
                label = it.activityInfo.applicationInfo.loadLabel(pm).toString()
            )
        }
        .filter { it.packageName != context.packageName }
        .distinctBy { it.packageName }
        .sortedBy { it.label.lowercase() }
        .toList()
}

private fun adbCommand(context: Context) =
    "adb shell pm grant ${context.packageName} android.permission.WRITE_SECURE_SETTINGS"

private fun Context.open(action: String, uri: Uri? = null) {
    val intent = Intent(action)
    if (uri != null) intent.data = uri
    try {
        startActivity(intent)
    } catch (e: Exception) {
        startActivity(Intent(Settings.ACTION_SETTINGS))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(resumeTick: Int) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Tudo que depende das Configurações é relido ao voltar para o app.
    var localTick by remember { mutableIntStateOf(0) }
    val method = remember(resumeTick, localTick) { GrayController.method(context) }
    val recommended = remember(resumeTick, localTick) { GrayController.recommended(context) }
    val serviceOn = remember(resumeTick, localTick) { GrayscaleService.isEnabled(context) }
    val methodReady = remember(resumeTick, localTick) { GrayController.methodReady(context, method) }
    val hasAdb = remember(resumeTick, localTick) { AdbGray.hasPermission(context) }

    var enabled by remember { mutableStateOf(AppPrefs.isEnabled(context)) }
    var selected by remember { mutableStateOf(AppPrefs.selected(context)) }
    var apps by remember { mutableStateOf<List<AppEntry>?>(null) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val initiallySelected = selected
        apps = withContext(Dispatchers.Default) {
            // Os já escolhidos aparecem primeiro; o resto em ordem alfabética.
            loadLaunchableApps(context).sortedByDescending { it.packageName in initiallySelected }
        }
    }

    val filtered = remember(apps, query) {
        val all = apps.orEmpty()
        val q = query.trim()
        if (q.isEmpty()) all else all.filter { it.label.contains(q, ignoreCase = true) }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Modo Cinza") }) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = padding.calculateBottomPadding() + 16.dp
            )
        ) {
            item {
                SetupCard(
                    method = method,
                    serviceOn = serviceOn,
                    methodReady = methodReady,
                    hasAdb = hasAdb,
                    onChanged = { localTick++ },
                    onTest = {
                        scope.launch {
                            if (GrayController.set(context, true)) {
                                delay(3000)
                                GrayController.set(context, false)
                            } else {
                                Toast.makeText(
                                    context,
                                    "Não deu para ligar. Confira a configuração.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                )
            }

            item {
                MethodCard(
                    current = method,
                    recommended = recommended,
                    onSelect = { newMethod ->
                        if (newMethod != method) {
                            GrayController.set(context, false) // desliga pelo método antigo
                            AppPrefs.setMethod(context, newMethod)
                            localTick++
                        }
                    }
                )
            }

            item {
                ListItem(
                    headlineContent = { Text("Ativado") },
                    supportingContent = { Text("Desligue para pausar sem perder a lista") },
                    trailingContent = {
                        Switch(
                            checked = enabled,
                            onCheckedChange = {
                                enabled = it
                                AppPrefs.setEnabled(context, it)
                                if (!it) GrayController.set(context, false)
                            }
                        )
                    }
                )
            }

            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("Buscar app") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true
                )
            }

            item {
                Text(
                    text = "Apps em escala de cinza: ${selected.size}",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            if (apps == null) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }
                }
            }

            items(filtered, key = { it.packageName }) { app ->
                AppRow(
                    app = app,
                    checked = app.packageName in selected,
                    onToggle = {
                        selected = if (app.packageName in selected) {
                            selected - app.packageName
                        } else {
                            selected + app.packageName
                        }
                        AppPrefs.setSelected(context, selected)
                    }
                )
            }
        }
    }
}

@Composable
private fun SetupCard(
    method: Method,
    serviceOn: Boolean,
    methodReady: Boolean?,
    hasAdb: Boolean,
    onChanged: () -> Unit,
    onTest: () -> Unit
) {
    val allDone = serviceOn && methodReady == true
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (allDone) {
                StepTitle(done = true, text = "Tudo pronto")
                Text(
                    "Os apps marcados abaixo abrem em escala de cinza. Ao sair deles, as cores voltam.",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Text("Configuração (uma vez só)", style = MaterialTheme.typography.titleMedium)
                if (method == Method.ADB) {
                    MethodStep(method, methodReady)
                    ServiceStep(serviceOn, hasAdb, onChanged)
                } else {
                    ServiceStep(serviceOn, hasAdb, onChanged)
                    MethodStep(method, methodReady)
                }
            }

            val canTest = methodReady != false && (method != Method.SHORTCUT || serviceOn)
            if (canTest) {
                OutlinedButton(onClick = onTest) { Text("Testar por 3 segundos") }
            }
        }
    }
}

@Composable
private fun ServiceStep(serviceOn: Boolean, hasAdb: Boolean, onChanged: () -> Unit) {
    val context = LocalContext.current
    StepTitle(done = serviceOn, text = "Serviço de acessibilidade do Modo Cinza")
    if (serviceOn) return

    Text(
        "Ele só detecta qual app está aberto, não lê nada da tela. Em Acessibilidade, " +
            "procure \"Modo Cinza\" (às vezes dentro de \"Apps instalados\") e ative.",
        style = MaterialTheme.typography.bodyMedium
    )
    Button(onClick = {
        if (hasAdb && GrayscaleService.enableWithSecureSettings(context)) {
            onChanged()
        } else {
            context.open(Settings.ACTION_ACCESSIBILITY_SETTINGS)
        }
    }) { Text("Ativar") }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasAdb) {
        Text(
            "Apareceu \"Configuração restrita\"? Abra as informações do app, toque em ⋮ " +
                "(canto superior) e em \"Permitir configurações restritas\". Depois tente de novo.",
            style = MaterialTheme.typography.bodySmall
        )
        TextButton(onClick = {
            context.open(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null)
            )
        }) { Text("Abrir informações do app") }
    }
}

@Composable
private fun MethodStep(method: Method, methodReady: Boolean?) {
    val context = LocalContext.current
    when (method) {
        Method.MODES -> {
            StepTitle(done = methodReady == true, text = "Acesso ao \"Não perturbe\"")
            if (methodReady != true) {
                Text(
                    "Na lista que vai abrir, ative o Modo Cinza. Ele não silencia notificações: " +
                        "só usa o efeito de escala de cinza dos Modos do Android.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Button(onClick = {
                    context.open(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                }) { Text("Abrir configuração") }
            }
        }

        Method.SHORTCUT -> {
            StepTitle(done = methodReady == true, text = "Atalho da correção de cor")
            if (methodReady != true) {
                Text(
                    "Em Acessibilidade, abra \"Correção de cor\":\n" +
                        "• escolha \"Escala de cinza\" (não precisa ligar a correção);\n" +
                        "• ligue o atalho e marque \"Manter as teclas de volume pressionadas\".\n" +
                        "Deixe só a correção de cor nesse atalho.",
                    style = MaterialTheme.typography.bodyMedium
                )
                if (methodReady == null) {
                    Text(
                        "Neste celular não dá para confirmar isso automaticamente. " +
                            "Depois de configurar, toque em Testar.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Button(onClick = {
                    context.open(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                }) { Text("Abrir Acessibilidade") }
            }
        }

        Method.ADB -> {
            StepTitle(done = methodReady == true, text = "Permissão via ADB")
            if (methodReady != true) {
                val command = adbCommand(context)
                Text(
                    "Com o celular ligado ao computador (ou pela depuração por Wi-Fi), rode:",
                    style = MaterialTheme.typography.bodyMedium
                )
                SelectionContainer {
                    Text(
                        command,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                OutlinedButton(onClick = {
                    val cm = context.getSystemService(ClipboardManager::class.java)
                    cm.setPrimaryClip(ClipData.newPlainText("adb", command))
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                        Toast.makeText(context, "Comando copiado", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Copiar comando") }
            }
        }
    }
}

@Composable
private fun MethodCard(current: Method, recommended: Method, onSelect: (Method) -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Text(
                "Como deixar a tela cinza",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            Method.entries.forEach { m ->
                val supported = m.isSupported()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = supported) { onSelect(m) }
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = m == current,
                        onClick = { onSelect(m) },
                        enabled = supported
                    )
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text(
                            m.title + if (m == recommended) " (recomendado)" else "",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            if (supported) m.description
                            else "Precisa do Android ${m.androidVersion} ou mais novo.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StepTitle(done: Boolean, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (done) Icons.Filled.CheckCircle else Icons.Filled.Info,
            contentDescription = if (done) "Concluído" else "Pendente",
            tint = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun AppRow(app: AppEntry, checked: Boolean, onToggle: () -> Unit) {
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(initialValue = null, app.packageName) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager.getApplicationIcon(app.packageName)
                    .toBitmap(96, 96)
                    .asImageBitmap()
            }.getOrNull()
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(40.dp)) {
            icon?.let {
                Image(bitmap = it, contentDescription = null, modifier = Modifier.fillMaxSize())
            }
        }
        Spacer(Modifier.width(16.dp))
        Text(
            text = app.label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
    }
}
