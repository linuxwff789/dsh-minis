package com.openminis.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.openminis.app.sandbox.PRootKernel
import com.openminis.app.sandbox.RootfsManager
import com.openminis.app.sandbox.TerminalSession
import com.openminis.app.ui.sandbox.RootfsManagementScreen
import com.openminis.app.ui.terminal.TerminalScreen
import com.openminis.app.ui.theme.MinisTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Minimal DSH terminal app: rootfs management + proot Alpine shell terminal.
 * Forked from OpenMinis, agent features stripped.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MinisTheme {
                AppRoot()
            }
        }
    }
}

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var screen by remember { mutableStateOf("rootfs") }
    val terminalSession = remember { TerminalSession(context.applicationContext) }
    var terminalStarted by remember { mutableStateOf(false) }
    var bootError by remember { mutableStateOf<String?>(null) }

    when (screen) {
        "terminal" -> {
            LaunchedEffect(Unit) {
                if (!terminalStarted) {
                    try {
                        PRootKernel.boot(context)
                        terminalSession.start()
                        terminalStarted = true
                    } catch (t: Throwable) {
                        bootError = t.message ?: t.javaClass.simpleName
                    }
                }
            }
            if (bootError != null) {
                Box(Modifier.fillMaxSize().padding(24.dp)) {
                    Column {
                        Text("Boot failed: $bootError", color = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { bootError = null; screen = "rootfs" }) {
                            Text("Back")
                        }
                    }
                }
            } else {
                TerminalScreen(
                    terminalSession = terminalSession,
                    onBack = { screen = "rootfs" },
                )
            }
        }
        else -> {
            var dshInstalled by remember { mutableStateOf(DshManager.getInstance(context).isInstalled()) }
            var dshRunning by remember { mutableStateOf(DshManager.getInstance(context).isServerRunning()) }
            var installBusy by remember { mutableStateOf(false) }
            var installLog by remember { mutableStateOf("") }

            Column(Modifier.fillMaxSize()) {
                Box(Modifier.weight(1f)) {
                    RootfsManagementScreen(
                        onBack = { (context as? Activity)?.finish() },
                        onBrowseFiles = {},
                    )
                }
                // DSH (deepseek-harness) section
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val dsh = DshManager.getInstance(context)
                    Text("DSH (deepseek-harness)", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    if (installBusy) {
                        Text("Installing… (log: dsh-install.log)", color = MaterialTheme.colorScheme.primary)
                    } else if (!dshInstalled) {
                        Button(onClick = {
                            installBusy = true
                            scope.launch {
                                try {
                                    PRootKernel.boot(context)
                                    val p = dsh.install()
                                    if (p == null) {
                                        installLog = "failed to start installer"
                                        installBusy = false
                                    } else {
                                        p.waitFor()
                                        installBusy = false
                                        dshInstalled = dsh.isInstalled()
                                        installLog = "install exit: " + p.exitValue()
                                    }
                                } catch (t: Throwable) {
                                    installBusy = false
                                    installLog = t.message ?: t.javaClass.simpleName
                                }
                            }
                        }) { Text("Install DSH") }
                    } else if (!dshRunning) {
                        Row {
                            Button(onClick = {
                                scope.launch {
                                    try {
                                        PRootKernel.boot(context)
                                        dsh.startServer()
                                        // wait for port
                                        repeat(60) {
                                            if (dsh.isServerRunning()) return@repeat
                                            delay(1000)
                                        }
                                        dshRunning = dsh.isServerRunning()
                                    } catch (t: Throwable) {
                                        installLog = t.message ?: t.javaClass.simpleName
                                    }
                                }
                            }) { Text("Start DSH") }
                        }
                    } else {
                        Row {
                            Button(onClick = {
                                context.startActivity(Intent(context, DshWebActivity::class.java))
                            }) { Text("Open DSH Web") }
                            Spacer(Modifier.height(0.dp))
                            OutlinedButton(onClick = {
                                scope.launch {
                                    try {
                                        dsh.startServer()
                                    } catch (_: Throwable) {}
                                }
                            }) { Text("Restart") }
                        }
                    }
                    if (installLog.isNotEmpty()) {
                        Text(installLog, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                // Launch terminal button pinned at the bottom.
                Button(
                    onClick = {
                        if (RootfsManager.getInstance(context).isInstalled) {
                            screen = "terminal"
                        } else {
                            scope.launch {
                                try {
                                    PRootKernel.boot(context)
                                    screen = "terminal"
                                } catch (t: Throwable) {
                                    bootError = t.message ?: t.javaClass.simpleName
                                    screen = "terminal"
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text("启动终端 / Launch Terminal")
                }
            }
        }
    }
}
