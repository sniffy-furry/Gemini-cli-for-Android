package com.gemini.agent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.termux.view.TerminalView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme(colorScheme = darkColorScheme()) { Surface(Modifier.fillMaxSize()) { App() } } }
    }
}

enum class Stage { SETUP, KEY, TERM }

@Composable
fun App() {
    val ctx = LocalContext.current
    fun next() = if (KeyStore.get(ctx).isNullOrBlank()) Stage.KEY else Stage.TERM
    var stage by remember { mutableStateOf(if (SetupManager.isReady(ctx)) next() else Stage.SETUP) }
    when (stage) {
        Stage.SETUP -> SetupScreen { stage = next() }
        Stage.KEY -> KeyScreen { stage = Stage.TERM }
        Stage.TERM -> TerminalScreen()
    }
}

@Composable
fun SetupScreen(done: () -> Unit) {
    val ctx = LocalContext.current
    var pct by remember { mutableStateOf(0) }
    var msg by remember { mutableStateOf("Pornesc...") }
    var err by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        try {
            withContext(Dispatchers.IO) { SetupManager.install(ctx) { p, m -> pct = p; msg = m } }
            done()
        } catch (t: Throwable) { err = t.toString() }
    }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("Configurare initiala", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))
        LinearProgressIndicator(progress = { pct / 100f }, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Text(err ?: msg)
    }
}

@Composable
fun KeyScreen(done: () -> Unit) {
    val ctx = LocalContext.current
    var key by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("Cheie API Gemini", style = MaterialTheme.typography.headlineSmall)
        Text("O obtii de la aistudio.google.com. Se salveaza criptat.")
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = key, onValueChange = { key = it }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), label = { Text("API key") },
            visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation()
        )
        Row { Checkbox(show, { show = it }); Text("Arata cheia", Modifier.padding(top = 12.dp)) }
        Button(enabled = key.isNotBlank(), onClick = { KeyStore.set(ctx, key.trim()); done() }) { Text("Continua") }
    }
}

@Composable
fun TerminalScreen() {
    val ctx = LocalContext.current
    val client = remember { TermClient(ctx) }
    val session = remember {
        Session.create(ctx, KeyStore.get(ctx) ?: "", client).also { client.session = it }
    }
    Column(Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            factory = { c ->
                TerminalView(c, null).apply {
                    setTerminalViewClient(client)
                    attachSession(session)
                    setTextSize(32)
                    isFocusable = true
                    isFocusableInTouchMode = true
                    requestFocus()
                    client.view = this
                }
            }
        )
        val keys = listOf(
            "ESC" to "\u001b", "TAB" to "\t", "^C" to "\u0003", "^D" to "\u0004",
            "\u2191" to "\u001b[A", "\u2193" to "\u001b[B", "\u2190" to "\u001b[D", "\u2192" to "\u001b[C",
            "|" to "|", "/" to "/", "-" to "-", "~" to "~"
        )
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            keys.forEach { (label, seq) ->
                TextButton(onClick = { session.write(seq) }) { Text(label) }
            }
        }
    }
}
