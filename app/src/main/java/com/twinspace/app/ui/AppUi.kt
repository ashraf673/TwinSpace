package com.twinspace.app.ui

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.Intent
import android.content.pm.CrossProfileApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.twinspace.app.Admin
import com.twinspace.app.CloneEngine
import com.twinspace.app.ClonedApp
import com.twinspace.app.InstalledApp
import com.twinspace.app.WorkApps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Screen { Setup, Home, Picker, Settings }

@Composable
fun TwinApp() {
    val context = LocalContext.current
    var screen by remember { mutableStateOf(if (Admin.hasWorkProfile(context)) Screen.Home else Screen.Setup) }
    var clones by remember { mutableStateOf(emptyList<ClonedApp>()) }
    var status by remember { mutableStateOf<String?>(null) }
    var selected by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun refresh() {
        clones = if (Admin.hasWorkProfile(context)) WorkApps.clones(context) else emptyList()
        if (Admin.hasWorkProfile(context) && screen == Screen.Setup) screen = Screen.Home
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refresh()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val provision = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        refresh()
        if (Admin.hasWorkProfile(context)) screen = Screen.Home
        else if (it.resultCode != Activity.RESULT_OK) {
            error = "Setup was cancelled. Android needs a work profile to keep the second copy separate."
        }
    }

    fun createSpace() {
        error = null
        if (Admin.hasWorkProfile(context)) {
            screen = Screen.Home
            return
        }
        if (!Admin.canProvision(context)) {
            error = "This phone already has a work profile (work email, Island, Shelter, or a company policy). Remove that profile in Android Settings first."
            return
        }
        val intent = Intent(DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE).apply {
            putExtra(
                DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME,
                Admin.component(context)
            )
            putExtra(DevicePolicyManager.EXTRA_PROVISIONING_SKIP_ENCRYPTION, true)
        }
        provision.launch(intent)
    }

    fun connectProfiles() {
        if (Build.VERSION.SDK_INT >= 30) {
            val cpa = context.getSystemService(CrossProfileApps::class.java)
            val intent = cpa.createRequestInteractAcrossProfilesIntent()
            context.startActivity(intent)
        }
    }

    fun cloneSelected() {
        val pkg = selected ?: return
        val label = CloneEngine.appLabel(context, pkg)
        screen = Screen.Home
        status = "Preparing a fresh copy of $label…"
        error = null
        scope.launch {
            try {
                val uris = withContext(Dispatchers.IO) { CloneEngine.stageApks(context, pkg) }
                status = "Installing into Second Space…"
                withContext(Dispatchers.Main) {
                    CloneEngine.requestClone(context, pkg, uris)
                }
                repeat(50) {
                    delay(400)
                    if (WorkApps.isCloned(context, pkg)) {
                        refresh()
                        status = null
                        selected = null
                        return@launch
                    }
                }
                refresh()
                status = "If Android asked to install, tap Install. Then come back here."
                delay(2500)
                status = null
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
            Screen.Setup -> SetupScreen(
                error = error,
                onCreate = ::createSpace
            )
            Screen.Home -> HomeScreen(
                clones = clones,
                status = status,
                error = error,
                needsConnect = Admin.hasWorkProfile(context) &&
                    Build.VERSION.SDK_INT >= 30 &&
                    !Admin.canTalkAcrossProfiles(context),
                onAdd = { screen = Screen.Picker },
                onSettings = { screen = Screen.Settings },
                onConnect = ::connectProfiles,
                onOpen = { app ->
                    try {
                        WorkApps.launch(context, app)
                    } catch (e: Exception) {
                        Toast.makeText(context, e.message ?: "Couldn't open", Toast.LENGTH_SHORT).show()
                    }
                },
                onRemove = { app ->
                    try {
                        CloneEngine.requestUninstall(context, app.packageName)
                        status = "Removing ${app.label}…"
                        scope.launch {
                            delay(1500)
                            refresh()
                            status = null
                        }
                    } catch (e: Exception) {
                        error = e.message
                    }
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
                    try {
                        CloneEngine.requestWipe(context)
                    } catch (e: Exception) {
                        Toast.makeText(context, e.message ?: "Couldn't remove space", Toast.LENGTH_LONG).show()
                    }
                }
            )
        }
    }
}

@Composable
private fun SetupScreen(error: String?, onCreate: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text("TWINSPACE", color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 4.sp, fontSize = 11.sp)
            Spacer(Modifier.height(28.dp))
            Text("A second copy.\nYour save stays yours.", fontSize = 34.sp, fontWeight = FontWeight.Medium, lineHeight = 40.sp)
            Spacer(Modifier.height(16.dp))
            Text(
                "Install a fresh Hill Climb Racing (or any app) next to the one you already play. Your brother's game starts from zero. Yours is untouched.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 16.sp,
                lineHeight = 24.sp
            )
        }
        Column {
            if (error != null) {
                Text(error, color = MaterialTheme.colorScheme.error, fontSize = 14.sp, modifier = Modifier.padding(bottom = 16.dp))
            }
            PrimaryButton("Create second space", onCreate)
            Spacer(Modifier.height(12.dp))
            Text(
                "Android will ask to set up a work profile. That's the isolated space. TwinSpace is the owner of it — not your employer.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun HomeScreen(
    clones: List<ClonedApp>,
    status: String?,
    error: String?,
    needsConnect: Boolean,
    onAdd: () -> Unit,
    onSettings: () -> Unit,
    onConnect: () -> Unit,
    onOpen: (ClonedApp) -> Unit,
    onRemove: (ClonedApp) -> Unit
) {
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(top = 12.dp, bottom = 8.dp)) {
                    Text("SECOND SPACE", color = MaterialTheme.colorScheme.onSurfaceVariant, letterSpacing = 3.sp, fontSize = 11.sp)
                    Text("Clones", fontSize = 32.sp, fontWeight = FontWeight.Medium)
                }
                IconButton(onClick = onSettings) {
                    Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                "Fresh copies. Your original saves stay on the main app.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 14.sp
            )
            Spacer(Modifier.height(16.dp))
            if (needsConnect) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(16.dp)
                ) {
                    Text("Allow TwinSpace to reach Second Space", fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(6.dp))
                    Text("Android needs one extra permission so clones can be installed.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    TextButton(onClick = onConnect) { Text("Open connected apps") }
                }
                Spacer(Modifier.height(12.dp))
            }
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
private fun CloneTile(app: ClonedApp, onOpen: () -> Unit, onRemove: () -> Unit) {
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
        apps = withContext(Dispatchers.Default) { WorkApps.installedOnPhone(context) }
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
                            if (cloned) "Already in Second Space" else app.packageName,
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
        Text("Second Space is an Android work profile owned by TwinSpace. Apps inside it have their own data — a new save, a new login.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp, lineHeight = 22.sp)
        Spacer(Modifier.height(24.dp))
        Text("Remove second space", color = MaterialTheme.colorScheme.error, modifier = Modifier.clickable(onClick = onWipe))
        Spacer(Modifier.height(8.dp))
        Text("Deletes every cloned app and its data. Your original games are not touched.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
    }
}

@Composable
private fun PrimaryButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primary)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun AppIcon(drawable: Drawable, modifier: Modifier = Modifier) {
    val bitmap = remember(drawable) { drawable.toBitmapCompat() }
    Image(bitmap = bitmap.asImageBitmap(), contentDescription = null, modifier = modifier)
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
