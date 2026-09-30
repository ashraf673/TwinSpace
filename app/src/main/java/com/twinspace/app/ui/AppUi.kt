package com.twinspace.app.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.twinspace.app.InstalledApp
import com.twinspace.app.virtual.VirtualCore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Screen { Home, Picker, Settings }

@Composable
fun TwinApp() {
    val context = LocalContext.current
    var screen by remember { mutableStateOf(Screen.Home) }
    var clones by remember { mutableStateOf(VirtualCore.clones(context)) }
    var status by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun refresh() {
        clones = VirtualCore.clones(context)
    }

    fun cloneSelected() {
        val pkg = selected ?: return
        val label = clones.find { it.packageName == pkg }?.label
            ?: VirtualCore.installedOnPhone(context).find { it.packageName == pkg }?.label
            ?: pkg
        screen = Screen.Home
        status = "Installing a fresh copy of $label…"
        error = null
        scope.launch {
            try {
                withContext(Dispatchers.IO) { VirtualCore.installFromInstalled(context, pkg) }
                refresh()
                status = null
                selected = null
            } catch (e: Exception) {
                status = null
                error = e.message ?: "Couldn't clone this app."
            }
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        when (screen) {
            Screen.Home -> HomeScreen(
                clones = clones,
                status = status,
                error = error,
                onAdd = { screen = Screen.Picker },
                onSettings = { screen = Screen.Settings },
                onOpen = { app ->
                    try {
                        val act = context.findActivity()
                        if (act != null) VirtualCore.launch(act, app.packageName)
                        else Toast.makeText(context, "Couldn't open", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, e.message ?: "Couldn't open", Toast.LENGTH_LONG).show()
                    }
                },
                onRemove = { app ->
                    VirtualCore.uninstall(context, app.packageName)
                    refresh()
                }
            )
            Screen.Picker -> PickerScreen(
                already = clones.map { it.packageName }.toSet(),
                selected = selected,
                onSelect = { selected = it },
                onDone = ::cloneSelected,
                onClose = { screen = Screen.Home; selected = null }
            )
            Screen.Settings -> SettingsScreen(
                onBack = { screen = Screen.Home },
                onWipe = {
                    VirtualCore.wipe(context)
                    refresh()
                    screen = Screen.Home
                }
            )
        }
    }
}

@Composable
private fun HomeScreen(
    clones: List<InstalledApp>,
    status: String?,
    error: String?,
    onAdd: () -> Unit,
    onSettings: () -> Unit,
    onOpen: (InstalledApp) -> Unit,
    onRemove: (InstalledApp) -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(top = 12.dp, bottom = 8.dp)) {
                    Text("TWINSPACE", color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 3.sp, fontSize = 11.sp)
                    Text("Clones", fontSize = 32.sp, fontWeight = FontWeight.Medium)
                }
                IconButton(onClick = onSettings) {
                    Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                "Each clone runs inside TwinSpace with its own save. Your original app is not touched.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp
            )
            Spacer(Modifier.height(16.dp))
            if (status != null) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                    Text(status, fontSize = 14.sp)
                }
                Spacer(Modifier.height(12.dp))
            }
            if (error != null) {
                Text(error, color = MaterialTheme.colorScheme.error, fontSize = 14.sp, modifier = Modifier.padding(bottom = 12.dp))
            }
            if (clones.isEmpty() && status == null) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                        Text("Nothing cloned yet", fontSize = 18.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Tap + and pick a game. The copy opens like a brand-new install.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            fontSize = 14.sp
                        )
                    }
                }
                Spacer(Modifier.height(88.dp))
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 96.dp, top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(clones, key = { it.packageName }) { app ->
                        CloneTile(app = app, onOpen = { onOpen(app) }, onRemove = { onRemove(app) })
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = onAdd,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shape = CircleShape
        ) {
            Icon(Icons.Outlined.Add, contentDescription = "Add clone")
        }
    }
}

@Composable
private fun CloneTile(app: InstalledApp, onOpen: () -> Unit, onRemove: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.combinedClickable(onClick = onOpen, onLongClick = onRemove)
    ) {
        Box {
            AppIcon(app.icon, Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)))
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(app.label, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

@Composable
private fun PickerScreen(
    already: Set<String>,
    selected: String?,
    onSelect: (String) -> Unit,
    onDone: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var apps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.Default) { VirtualCore.installedOnPhone(context) }
    }
    val filtered = remember(apps, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) apps else apps.filter { it.label.lowercase().contains(q) || it.packageName.contains(q) }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
            IconButton(onClick = onClose) {
                Icon(Icons.Outlined.Close, contentDescription = "Close")
            }
            Text("Add a clone", fontSize = 20.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            TextButton(onClick = onDone, enabled = selected != null) { Text("Done") }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            if (query.isEmpty()) {
                Text("Search games and apps", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                textStyle = TextStyle(color = MaterialTheme.colorScheme.onBackground, fontSize = 16.sp),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(filtered, key = { it.packageName }) { app ->
                val cloned = app.packageName in already
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !cloned) { onSelect(app.packageName) }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AppIcon(app.icon, Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)))
                    Column(Modifier.weight(1f)) {
                        Text(app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            if (cloned) "Already in TwinSpace" else app.packageName,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (selected == app.packageName) {
                        Icon(Icons.Outlined.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(onBack: () -> Unit, onWipe: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        TextButton(onClick = onBack) { Text("Back") }
        Spacer(Modifier.height(12.dp))
        Text("Settings", fontSize = 32.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(20.dp))
        Text(
            "Clones run in TwinSpace’s container — a private copy of the APK with its own files, prefs, and databases. No work profile. Your original install stays on the phone.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 15.sp,
            lineHeight = 22.sp
        )
        Spacer(Modifier.height(24.dp))
        Text("Remove all clones", color = MaterialTheme.colorScheme.error, modifier = Modifier.clickable(onClick = onWipe))
        Spacer(Modifier.height(8.dp))
        Text("Deletes every cloned app and its data. Your original games are not touched.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
    }
}

@Composable
private fun AppIcon(drawable: Drawable, modifier: Modifier = Modifier) {
    val bitmap = remember(drawable) { drawable.toBitmapCompat() }
    Image(bitmap = bitmap.asImageBitmap(), contentDescription = null, modifier = modifier)
}

private fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

private fun Drawable.toBitmapCompat(): Bitmap {
    if (this is BitmapDrawable && bitmap != null) return bitmap
    val w = intrinsicWidth.coerceAtLeast(1)
    val h = intrinsicHeight.coerceAtLeast(1)
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bmp
}
