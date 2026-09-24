@file:OptIn(ExperimentalFoundationApi::class)

package com.twinspace.app.ui

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.VisibilityOff
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.twinspace.app.CloneActivity
import com.twinspace.app.CloneHosts
import com.twinspace.app.data.Catalog
import com.twinspace.app.data.CatalogApp
import com.twinspace.app.data.CloneApp
import com.twinspace.app.data.TwinStore

@Composable
fun HomeScreen(
    clones: List<CloneApp>,
    space: String,
    showHidden: Boolean,
    secretBlocked: Boolean,
    onSpace: (String) -> Unit,
    onLock: () -> Unit,
    onCatalog: () -> Unit,
    onSettings: () -> Unit,
    onEdit: (String) -> Unit,
    onOpen: (String) -> Unit,
) {
    val context = LocalContext.current
    val visible = clones
        .filter { it.space == space }
        .filter { showHidden || !it.hidden }
        .sortedByDescending { if (it.lastOpenedAt == 0L) it.createdAt else it.lastOpenedAt }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 20.dp), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                StatusLabel(if (space == "home") "MAIN" else "SECRET")
                Text(if (space == "home") "Home space" else "Secret space", color = Fg, fontSize = 28.sp, fontWeight = FontWeight.Medium)
            }
            Icon(Icons.Outlined.Lock, contentDescription = "Lock", tint = Muted, modifier = Modifier.size(44.dp).clickable { onLock() }.padding(10.dp))
        }

        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surface).padding(6.dp),
        ) {
            SpaceChip("Home", space == "home", Modifier.weight(1f)) { onSpace("home") }
            SpaceChip("Secret", space == "secret", Modifier.weight(1f)) { onSpace("secret") }
        }

        Spacer(Modifier.height(20.dp))

        when {
            space == "secret" && secretBlocked -> {
                EmptyCard(
                    title = "Secret is locked",
                    body = "Enter your PIN to open this space.",
                    modifier = Modifier.weight(1f),
                    onAction = { onSpace("secret") },
                )
            }
            visible.isEmpty() -> {
                EmptyCard(
                    title = "No clones yet",
                    body = if (space == "home") {
                        "Add a second session of WhatsApp, Instagram, Gmail, or anything in the catalog."
                    } else {
                        "Secret space stays empty until you add a clone here."
                    },
                    modifier = Modifier.weight(1f),
                    onAction = onCatalog,
                )
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(visible, key = { it.id }) { clone ->
                        CloneTile(clone, onOpen = {
                            val app = Catalog.byId(clone.catalogId)
                            if (clone.locked) {
                                onEdit(clone.id)
                            } else if (app != null) {
                                onOpen(clone.id)
                                val intent = Intent(context, CloneHosts.activityFor(clone.id)).apply {
                                    putExtra(CloneActivity.EXTRA_URL, app.url)
                                    putExtra(CloneActivity.EXTRA_TITLE, clone.nickname.ifBlank { app.name })
                                    putExtra(CloneActivity.EXTRA_CLONE_ID, clone.id)
                                }
                                context.startActivity(intent)
                            }
                        }, onEdit = { onEdit(clone.id) })
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            Pill("Settings", Icons.Outlined.Settings, false, Modifier.weight(1f), onSettings)
            Pill("Add clone", Icons.Outlined.Add, true, Modifier.weight(1f), onCatalog)
        }
    }
}

@Composable
private fun SpaceChip(label: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (active) Elevated else Surface)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (active) Fg else Muted, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun EmptyCard(title: String, body: String, modifier: Modifier = Modifier, onAction: () -> Unit) {
    Column(
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Surface).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, color = Fg, fontSize = 16.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(8.dp))
        Text(body, color = Muted, fontSize = 14.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        Box(
            Modifier.clip(RoundedCornerShape(12.dp)).background(Accent).clickable { onAction() }.padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Text("Continue", color = AccentFg, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun CloneTile(clone: CloneApp, onOpen: () -> Unit, onEdit: () -> Unit) {
    val app = Catalog.byId(clone.catalogId)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.combinedClickable(onClick = onOpen, onLongClick = onEdit),
    ) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(Elevated), contentAlignment = Alignment.Center) {
            Icon(catalogIcon(clone.catalogId), contentDescription = null, tint = Fg, modifier = Modifier.size(22.dp))
            if (clone.locked || clone.hidden) {
                Box(
                    Modifier.align(Alignment.BottomEnd).size(16.dp).clip(CircleShape).background(Surface),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        if (clone.hidden) Icons.Outlined.VisibilityOff else Icons.Outlined.Lock,
                        contentDescription = null,
                        tint = Muted,
                        modifier = Modifier.size(10.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            clone.nickname.ifBlank { app?.name ?: "Clone" },
            color = Muted,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Pill(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, primary: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (primary) Accent else Elevated)
            .clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (primary) AccentFg else Fg, modifier = Modifier.size(16.dp))
        Spacer(Modifier.size(8.dp))
        Text(label, color = if (primary) AccentFg else Fg, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun CatalogScreen(space: String, onBack: () -> Unit, onAdd: (CatalogApp) -> Unit) {
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf("all") }
    val apps = Catalog.apps.filter { filter == "all" || it.category == filter }
        .filter { query.isBlank() || it.name.contains(query, true) || it.hint.contains(query, true) }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = Fg, modifier = Modifier.size(44.dp).clickable { onBack() }.padding(10.dp))
            Column {
                StatusLabel("CATALOG")
                Text("Clone into $space", color = Fg, fontSize = 20.sp, fontWeight = FontWeight.Medium)
            }
        }
        BasicTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            textStyle = TextStyle(color = Fg, fontSize = 14.sp),
            cursorBrush = SolidColor(Accent),
            modifier = Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp)).background(Elevated).padding(horizontal = 16.dp, vertical = 12.dp),
            decorationBox = { inner ->
                if (query.isEmpty()) Text("Search apps", color = Subtle, fontSize = 14.sp)
                inner()
            },
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Catalog.categories.take(4).forEach { id ->
                val label = if (id == "all") "All" else id.replaceFirstChar { it.uppercase() }
                val active = filter == id
                Text(
                    label,
                    color = if (active) AccentFg else Muted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(if (active) Accent else Elevated).clickable { filter = id }.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
            items(apps, key = { it.id }) { app ->
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surface).clickable { onAdd(app) }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Elevated), contentAlignment = Alignment.Center) {
                        Icon(catalogIcon(app.id), contentDescription = null, tint = Fg, modifier = Modifier.size(20.dp))
                    }
                    Spacer(Modifier.size(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(app.name, color = Fg, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        Text(app.hint, color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text("Add", color = Subtle, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun EditScreen(clone: CloneApp, onBack: () -> Unit, onChange: (CloneApp) -> Unit, onRemove: () -> Unit) {
    val context = LocalContext.current
    val app = Catalog.byId(clone.catalogId)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp, bottom = 20.dp)) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = Fg, modifier = Modifier.size(44.dp).clickable { onBack() }.padding(10.dp))
            Column {
                StatusLabel("CLONE")
                Text(app?.name ?: "Clone", color = Fg, fontSize = 20.sp, fontWeight = FontWeight.Medium)
            }
        }
        Text("Nickname", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        BasicTextField(
            value = clone.nickname,
            onValueChange = { onChange(clone.copy(nickname = it)) },
            singleLine = true,
            textStyle = TextStyle(color = Fg, fontSize = 14.sp),
            cursorBrush = SolidColor(Accent),
            modifier = Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp)).background(Elevated).padding(horizontal = 16.dp, vertical = 12.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text("Session notes", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        BasicTextField(
            value = clone.notes,
            onValueChange = { onChange(clone.copy(notes = it)) },
            textStyle = TextStyle(color = Fg, fontSize = 14.sp, lineHeight = 20.sp),
            cursorBrush = SolidColor(Accent),
            modifier = Modifier.fillMaxWidth().height(96.dp).clip(RoundedCornerShape(16.dp)).background(Elevated).padding(16.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text("Space", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surface).padding(6.dp)) {
            SpaceChip("Home", clone.space == "home", Modifier.weight(1f)) { onChange(clone.copy(space = "home")) }
            SpaceChip("Secret", clone.space == "secret", Modifier.weight(1f)) { onChange(clone.copy(space = "secret")) }
        }
        Spacer(Modifier.height(12.dp))
        ToggleRow("App lock", "Ask before opening this clone", clone.locked) { onChange(clone.copy(locked = !clone.locked)) }
        ToggleRow("Hidden", "Keep off the grid unless Show hidden is on", clone.hidden) { onChange(clone.copy(hidden = !clone.hidden)) }
        Spacer(Modifier.height(24.dp))
        Pill("Open session", Icons.Outlined.Add, true, Modifier.fillMaxWidth()) {
            if (app != null) {
                val intent = Intent(context, CloneHosts.activityFor(clone.id)).apply {
                    putExtra(CloneActivity.EXTRA_URL, app.url)
                    putExtra(CloneActivity.EXTRA_TITLE, clone.nickname.ifBlank { app.name })
                    putExtra(CloneActivity.EXTRA_CLONE_ID, clone.id)
                }
                context.startActivity(intent)
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp)).background(Elevated).clickable { onRemove() },
            contentAlignment = Alignment.Center,
        ) {
            Text("Remove clone", color = Danger, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun ToggleRow(label: String, hint: String, on: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(16.dp)).background(Surface).clickable { onToggle() }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, color = Fg, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(hint, color = Muted, fontSize = 12.sp)
        }
        Box(
            Modifier.widthToggle(on).height(24.dp).clip(RoundedCornerShape(999.dp)).background(if (on) Accent else Elevated),
        ) {
            Box(
                Modifier.padding(2.dp).size(20.dp).clip(CircleShape).background(Fg).align(if (on) Alignment.CenterEnd else Alignment.CenterStart),
            )
        }
    }
}

private fun Modifier.widthToggle(on: Boolean) = this.then(Modifier.size(width = 40.dp, height = 24.dp))

@Composable
fun SettingsScreen(
    store: TwinStore,
    cloneCount: Int,
    onBack: () -> Unit,
    onLock: () -> Unit,
    onReset: () -> Unit,
    onChanged: () -> Unit,
) {
    var disguise by remember { mutableStateOf(store.disguise()) }
    var relock by remember { mutableStateOf(store.relockSecret()) }
    var showHidden by remember { mutableStateOf(store.showHidden()) }
    var pinStep by remember { mutableStateOf(0) }
    var oldPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back", tint = Fg, modifier = Modifier.size(44.dp).clickable { onBack() }.padding(10.dp))
            Column {
                StatusLabel("SETTINGS")
                Text("TwinSpace", color = Fg, fontSize = 20.sp, fontWeight = FontWeight.Medium)
            }
        }
        Text("$cloneCount clone${if (cloneCount == 1) "" else "s"} on this device. PIN and notes never leave the phone.", color = Muted, fontSize = 14.sp)
        ToggleRow("Calculator disguise", "Lock screen becomes a working calculator. Type your PIN, then =", disguise) {
            disguise = !disguise
            store.setDisguise(disguise)
        }
        ToggleRow("Relock secret", "Ask for the PIN every time you open Secret", relock) {
            relock = !relock
            store.setRelockSecret(relock)
        }
        ToggleRow("Show hidden clones", "Reveal clones marked hidden", showHidden) {
            showHidden = !showHidden
            store.setShowHidden(showHidden)
            onChanged()
        }
        Spacer(Modifier.height(20.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Surface).padding(16.dp)) {
            Text("Change PIN", color = Fg, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            if (pinStep == 0) {
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp)).background(Elevated).clickable { pinStep = 1 },
                    contentAlignment = Alignment.Center,
                ) { Text("Replace PIN", color = Fg, fontSize = 14.sp) }
            } else {
                Spacer(Modifier.height(12.dp))
                Text(if (pinStep == 1) "Current PIN" else "New PIN", color = Muted, fontSize = 12.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                PinPad(
                    value = if (pinStep == 1) oldPin else newPin,
                    onChange = { if (pinStep == 1) oldPin = it else newPin = it },
                    onComplete = { pin ->
                        if (pinStep == 1) {
                            oldPin = pin
                            newPin = ""
                            pinStep = 2
                        } else {
                            message = if (store.changePin(oldPin, pin)) "PIN updated" else "Current PIN was wrong"
                            pinStep = 0
                            oldPin = ""
                            newPin = ""
                        }
                    },
                )
            }
            if (message.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(message, color = Muted, fontSize = 12.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            }
        }
        Spacer(Modifier.height(24.dp))
        Pill("Lock now", Icons.Outlined.Lock, false, Modifier.fillMaxWidth(), onLock)
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp)).background(Elevated).clickable { onReset() },
            contentAlignment = Alignment.Center,
        ) { Text("Erase this space", color = Danger, fontSize = 14.sp, fontWeight = FontWeight.Medium) }
        Spacer(Modifier.height(16.dp))
        Text("Simplified second space — isolated web sessions, not Android app virtualization.", color = Subtle, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp))
    }
}

@Composable
fun CalcScreen(onUnlock: (String) -> Boolean) {
    var display by remember { mutableStateOf("0") }
    var acc by remember { mutableStateOf<Double?>(null) }
    var op by remember { mutableStateOf<String?>(null) }
    var fresh by remember { mutableStateOf(true) }
    val keys = listOf("C", "⌫", "%", "÷", "7", "8", "9", "×", "4", "5", "6", "−", "1", "2", "3", "+", "00", "0", ".", "=")

    fun n() = display.toDoubleOrNull() ?: 0.0
    fun fmt(v: Double): String {
        val r = kotlin.math.round(v * 1e8) / 1e8
        return if (r == r.toLong().toDouble()) r.toLong().toString() else r.toString()
    }
    fun compute(a: Double, operator: String, b: Double) = when (operator) {
        "+" -> a + b
        "−", "-" -> a - b
        "×" -> a * b
        "÷" -> if (b == 0.0) a else a / b
        else -> b
    }

    fun press(key: String) {
        when (key) {
            "C" -> { display = "0"; acc = null; op = null; fresh = true }
            "⌫" -> display = if (display.length <= 1) "0" else display.dropLast(1)
            "=" -> {
                val digits = display.filter { it.isDigit() }
                val result = if (op != null && acc != null) compute(acc!!, op!!, n()) else n()
                onUnlock(digits)
                display = fmt(result)
                acc = null
                op = null
                fresh = true
            }
            "+", "−", "×", "÷" -> {
                val current = n()
                acc = if (op != null && acc != null) compute(acc!!, op!!, current) else current
                op = key
                display = fmt(acc!!)
                fresh = true
            }
            "%" -> { display = fmt(n() / 100); fresh = true }
            "." -> if (!display.contains(".")) { display = if (fresh) "0." else display + "."; fresh = false }
            else -> {
                val incoming = if (fresh || display == "0") {
                    if (key == "00") "0" else key.trimStart('0').ifEmpty { "0" }
                } else {
                    display + key
                }
                display = incoming
                fresh = false
            }
        }
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Bottom) {
        Text("CALC", color = Subtle, fontSize = 11.sp, letterSpacing = 2.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End)
        Spacer(Modifier.height(8.dp))
        Text(display, color = Fg, fontSize = 44.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.End, maxLines = 2)
        Spacer(Modifier.height(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 12.dp)) {
            keys.chunked(4).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { key ->
                        val primary = key == "="
                        Box(
                            Modifier.weight(1f).height(64.dp).clip(RoundedCornerShape(16.dp)).background(if (primary) Accent else Elevated).clickable { press(key) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(key, color = if (primary) AccentFg else Fg, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}
