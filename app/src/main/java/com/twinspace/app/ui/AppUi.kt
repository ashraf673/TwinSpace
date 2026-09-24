package com.twinspace.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Backspace
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.twinspace.app.data.TwinStore

sealed class Screen {
    data object Setup : Screen()
    data object Lock : Screen()
    data object Calc : Screen()
    data object Home : Screen()
    data object Catalog : Screen()
    data object Settings : Screen()
    data object SecretLock : Screen()
    data class Edit(val id: String) : Screen()
}

@Composable
fun TwinApp(store: TwinStore) {
    var screen by remember {
        mutableStateOf<Screen>(
            when {
                !store.hasPin() -> Screen.Setup
                store.disguise() -> Screen.Calc
                else -> Screen.Lock
            },
        )
    }
    var space by remember { mutableStateOf("home") }
    var secretUnlocked by remember { mutableStateOf(false) }
    var clones by remember { mutableStateOf(store.clones()) }
    var showHidden by remember { mutableStateOf(store.showHidden()) }

    fun refresh() {
        clones = store.clones()
        showHidden = store.showHidden()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        when (val s = screen) {
            Screen.Setup -> SetupScreen(onDone = { pin ->
                store.setPin(pin)
                secretUnlocked = true
                screen = Screen.Home
            })
            Screen.Lock -> LockScreen(
                title = "Enter PIN",
                subtitle = "Unlock TwinSpace",
                onUnlock = { pin ->
                    if (store.checkPin(pin)) {
                        secretUnlocked = !store.relockSecret()
                        screen = Screen.Home
                        true
                    } else false
                },
            )
            Screen.SecretLock -> LockScreen(
                title = "Secret space",
                subtitle = "Enter PIN to open Secret",
                onBack = { screen = Screen.Home },
                onUnlock = { pin ->
                    if (store.checkPin(pin)) {
                        secretUnlocked = true
                        space = "secret"
                        screen = Screen.Home
                        true
                    } else false
                },
            )
            Screen.Calc -> CalcScreen(onUnlock = { pin ->
                if (store.checkPin(pin)) {
                    secretUnlocked = !store.relockSecret()
                    screen = Screen.Home
                    true
                } else false
            })
            Screen.Home -> HomeScreen(
                clones = clones,
                space = space,
                showHidden = showHidden,
                secretBlocked = store.relockSecret() && !secretUnlocked,
                onSpace = { next ->
                    if (next == "secret" && store.relockSecret() && !secretUnlocked) {
                        screen = Screen.SecretLock
                    } else {
                        space = next
                    }
                },
                onLock = {
                    secretUnlocked = false
                    screen = if (store.disguise()) Screen.Calc else Screen.Lock
                },
                onCatalog = { screen = Screen.Catalog },
                onSettings = { screen = Screen.Settings },
                onEdit = { screen = Screen.Edit(it) },
                onOpen = { id ->
                    val clone = clones.find { it.id == id }
                    if (clone == null) {
                        /* gone */
                    } else if (clone.locked) {
                        screen = Screen.Edit(id)
                    } else {
                        store.update(id) { it.copy(lastOpenedAt = System.currentTimeMillis()) }
                        refresh()
                    }
                },
            )
            Screen.Catalog -> CatalogScreen(
                space = space,
                onBack = { screen = Screen.Home },
                onAdd = { app ->
                    val name = if (space == "secret") "${app.name} · secret" else "${app.name} · 2"
                    store.addClone(app.id, space, name)
                    refresh()
                    screen = Screen.Home
                },
            )
            Screen.Settings -> SettingsScreen(
                store = store,
                cloneCount = clones.size,
                onBack = { screen = Screen.Home },
                onLock = {
                    secretUnlocked = false
                    screen = if (store.disguise()) Screen.Calc else Screen.Lock
                },
                onReset = {
                    store.reset()
                    clones = emptyList()
                    secretUnlocked = false
                    space = "home"
                    screen = Screen.Setup
                },
                onChanged = { refresh() },
            )
            is Screen.Edit -> {
                val clone = clones.find { it.id == s.id }
                if (clone == null) {
                    screen = Screen.Home
                } else {
                    EditScreen(
                        clone = clone,
                        onBack = { screen = Screen.Home },
                        onChange = { next ->
                            store.update(next.id) { next }
                            refresh()
                        },
                        onRemove = {
                            store.remove(clone.id)
                            refresh()
                            screen = Screen.Home
                        },
                    )
                }
            }
        }
    }
}

fun catalogIcon(id: String): ImageVector = when (id) {
    "whatsapp", "telegram", "messenger", "discord" -> Icons.Outlined.MailOutline
    "gmail", "outlook" -> Icons.Outlined.MailOutline
    "youtube", "spotify", "twitch", "tiktok" -> Icons.Outlined.PlayCircle
    "linkedin", "slack", "drive" -> Icons.Outlined.WorkOutline
    else -> Icons.Outlined.Share
}

@Composable
fun StatusLabel(text: String) {
    Text(
        text = text,
        color = Subtle,
        fontSize = 11.sp,
        letterSpacing = 2.sp,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
fun PinPad(value: String, onChange: (String) -> Unit, onComplete: (String) -> Unit) {
    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "del")
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(bottom = 24.dp)) {
            repeat(4) { i ->
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (i < value.length) Fg else Elevated),
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            keys.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { key ->
                        if (key.isEmpty()) {
                            Spacer(Modifier.size(64.dp))
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Elevated)
                                    .clickable {
                                        val next = when (key) {
                                            "del" -> value.dropLast(1)
                                            else -> if (value.length >= 4) value else value + key
                                        }
                                        onChange(next)
                                        if (next.length == 4) onComplete(next)
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (key == "del") Icon(Icons.Outlined.Backspace, contentDescription = "Delete", tint = Fg)
                                else Text(key, color = Fg, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SetupScreen(onDone: (String) -> Unit) {
    var step by remember { mutableStateOf(1) }
    var first by remember { mutableStateOf("") }
    var second by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 32.dp)) {
            StatusLabel("SECOND SPACE")
            Spacer(Modifier.height(12.dp))
            Text(if (step == 1) "Choose a PIN" else "Confirm PIN", color = Fg, fontSize = 28.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            Text(
                "Four digits lock Home and Secret. This stays on this device only.",
                color = Muted,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
            if (error.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(error, color = Danger, fontSize = 14.sp)
            }
        }
        PinPad(
            value = if (step == 1) first else second,
            onChange = { if (step == 1) first = it else second = it },
            onComplete = { pin ->
                if (step == 1) {
                    first = pin
                    second = ""
                    step = 2
                } else if (pin != first) {
                    error = "PINs did not match. Try again."
                    first = ""
                    second = ""
                    step = 1
                } else onDone(pin)
            },
        )
        Text(
            "Isolated web sessions — not Android app virtualization.",
            color = Subtle,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp),
        )
    }
}

@Composable
fun LockScreen(
    title: String,
    subtitle: String,
    onUnlock: (String) -> Boolean,
    onBack: (() -> Unit)? = null,
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 24.dp).fillMaxWidth()) {
            if (onBack != null) {
                Row(Modifier.fillMaxWidth()) {
                    Icon(
                        Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = Fg,
                        modifier = Modifier.size(44.dp).clickable { onBack() }.padding(10.dp),
                    )
                }
            }
            StatusLabel("LOCKED")
            Spacer(Modifier.height(12.dp))
            Text(title, color = Fg, fontSize = 28.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
            Text(subtitle, color = Muted, fontSize = 14.sp)
            if (error.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(error, color = Danger, fontSize = 14.sp)
            }
        }
        PinPad(
            value = pin,
            onChange = { pin = it },
            onComplete = {
                if (onUnlock(it)) {
                    pin = ""
                    error = ""
                } else {
                    error = "Wrong PIN"
                    pin = ""
                }
            },
        )
        Text("Session stays unlocked until you lock it.", color = Subtle, fontSize = 11.sp, modifier = Modifier.padding(bottom = 16.dp))
    }
}
