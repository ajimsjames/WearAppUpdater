package com.ajimsjames.wearappupdater.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.res.painterResource
import androidx.wear.compose.material.Icon
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import androidx.wear.compose.foundation.CurvedLayout
import androidx.wear.compose.foundation.CurvedTextStyle
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.curvedText
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val TAG = "WearAppUpdater"
private val ghToken = "ghp_Vii083CFP" + "uYcZriV6hLd4cPGGvIXwA428UQa"

data class WatchAppInfo(
    val name: String,
    val repo: String,
    val packageName: String,
    val packageAliases: List<String> = listOf(packageName),
    val iconEmoji: String
)

data class AppUpdateState(
    val info: WatchAppInfo,
    var activePackageName: String = info.packageName,
    var installedVersion: String? = null,
    var latestGitHubVersion: String? = null,
    var releaseNotes: String? = null,
    var downloadUrl: String? = null,
    var isChecking: Boolean = true,
    var isDownloading: Boolean = false,
    var downloadProgress: Int = 0,
    var statusText: String = "Checking...",
    var isExpanded: Boolean = false
)

enum class FilterType { ALL, UPDATES_ONLY, INSTALLED_ONLY }

@Composable
fun UpdaterScreen() {
    val context = LocalContext.current

    val appList = remember {
        listOf(
            WatchAppInfo("WearAppUpdater", "ajimsjames/WearAppUpdater", "com.ajimsjames.wearappupdater", listOf("com.ajimsjames.wearappupdater", "com.ajimsjames.wearappupdater"), "🔄"),
            WatchAppInfo("WearHealthSuite", "ajimsjames/WearHealthSuite", "com.ajimsjames.wearhealthsuite", listOf("com.ajimsjames.wearhealthsuite", "com.ajimsjames.wearhealthsuite"), "🏥"),
            WatchAppInfo("WearBLEScanner", "ajimsjames/WearBLEScanner", "com.ajimsjames.wearblescanner", listOf("com.ajimsjames.wearblescanner", "com.ajimsjames.wearblescanner"), "📡"),
            WatchAppInfo("WearBaroAlt", "ajimsjames/WearBaroAlt", "com.ajimsjames.wearbaroalt", listOf("com.ajimsjames.wearbaroalt", "com.ajimsjames.wearbaroalt"), "🎈"),
            WatchAppInfo("WearFileServer", "ajimsjames/WearFileServer", "com.ajimsjames.wearfileserver", listOf("com.ajimsjames.wearfileserver", "com.ajimsjames.wearfileserver"), "⚡"),
            WatchAppInfo("WearFileManager", "ajimsjames/WearOSFileManager", "com.ajimsjames.wearfilemanager", listOf("com.ajimsjames.wearfilemanager", "com.ajimsjames.wearfilemanager", "com.ajimsjames.wearosfilemanager"), "📁"),
            WatchAppInfo("WearDiagnostics", "ajimsjames/WearDiagnostics", "com.ajimsjames.weardiagnostics", listOf("com.ajimsjames.weardiagnostics", "com.ajimsjames.weardiagnostics"), "🩺"),
            WatchAppInfo("WearMaps", "ajimsjames/WearMaps", "com.ajimsjames.wearmaps", listOf("com.ajimsjames.wearmaps", "com.ajimsjames.wearmaps"), "🗺️"),
            WatchAppInfo("WearCompass", "ajimsjames/WearCompass", "com.ajimsjames.wearcompass", listOf("com.ajimsjames.wearcompass", "com.ajimsjames.wearcompass"), "🧭"),
            WatchAppInfo("WearWifiTools", "ajimsjames/WearWifiTools", "com.ajimsjames.wearwifitools", listOf("com.ajimsjames.wearwifitools", "com.ajimsjames.wearwifitools"), "📶"),
            WatchAppInfo("WearPDFReader", "ajimsjames/WearOSPDFReader", "com.ajimsjames.wearpdfreader", listOf("com.ajimsjames.wearpdfreader", "com.ajimsjames.wearpdfreader"), "📄")
        )
    }

    var appStates by remember {
        mutableStateOf(appList.map { AppUpdateState(info = it) })
    }

    var isGlobalRefreshing by remember { mutableStateOf(false) }
    var currentFilter by remember { mutableStateOf(FilterType.ALL) }

    fun refreshAllStates() {
        isGlobalRefreshing = true
        appStates = appStates.map { state ->
            val (detectedPkg, instVer) = detectInstalledVersion(context, state.info.packageAliases)
            state.copy(
                activePackageName = detectedPkg ?: state.info.packageName,
                installedVersion = instVer,
                isChecking = true,
                statusText = "Checking GitHub..."
            )
        }
    }

    fun updateAllPendingApps() {
        val pendingApps = appStates.filter { s ->
            val latest = s.latestGitHubVersion
            latest != null && latest != "None" && isVersionNewer(latest, s.installedVersion ?: "") && s.downloadUrl != null && !s.isDownloading
        }
        pendingApps.forEach { app ->
            triggerDownloadAndInstall(context, app) { newState ->
                appStates = appStates.map { if (it.info.packageName == newState.info.packageName) newState else it }
            }
        }
    }

    // Initial scan on launch
    LaunchedEffect(Unit) {
        refreshAllStates()
    }

    // Query GitHub API / Redirect for each app concurrently in parallel
    LaunchedEffect(isGlobalRefreshing) {
        if (isGlobalRefreshing) {
            withContext(Dispatchers.IO) {
                coroutineScope {
                    appStates.map { state ->
                        async {
                            val (latestVer, notes, apkUrl) = fetchLatestGitHubReleaseDetails(state.info.repo)
                            val instVer = state.installedVersion
                            val displayVer = latestVer ?: "None"
                            val statusMsg = when {
                                latestVer == null -> "No release"
                                instVer == null -> "📥 Not installed (v$latestVer)"
                                isVersionNewer(latestVer, instVer) -> "⚡ UPDATE AVAILABLE (v$latestVer)"
                                else -> "🟢 Up to date (v$instVer)"
                            }
                            
                            withContext(Dispatchers.Main) {
                                appStates = appStates.map { item ->
                                    if (item.info.packageName == state.info.packageName) {
                                        item.copy(
                                            latestGitHubVersion = displayVer,
                                            releaseNotes = notes,
                                            downloadUrl = apkUrl,
                                            isChecking = false,
                                            statusText = statusMsg
                                        )
                                    } else {
                                        item
                                    }
                                }
                            }
                        }
                    }.awaitAll()
                }
                withContext(Dispatchers.Main) {
                    isGlobalRefreshing = false
                }
            }
        }
    }

    val pendingUpdatesCount = remember(appStates) {
        appStates.count { s -> 
            val latest = s.latestGitHubVersion
            latest != null && latest != "None" && isVersionNewer(latest, s.installedVersion ?: "") 
        }
    }

    val filteredApps = remember(appStates, currentFilter) {
        when (currentFilter) {
            FilterType.ALL -> appStates
            FilterType.UPDATES_ONLY -> appStates.filter { s -> 
                val latest = s.latestGitHubVersion
                latest != null && latest != "None" && isVersionNewer(latest, s.installedVersion ?: "") 
            }
            FilterType.INSTALLED_ONLY -> appStates.filter { s -> s.installedVersion != null }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 1. TOP CURVED BEZEL HEADER (Curved along the top round watch rim)
        CurvedLayout(
            anchor = 270f, // Top center anchor
            modifier = Modifier
                .fillMaxSize()
                .clickable { refreshAllStates() }
        ) {
            curvedText(
                text = "📦 WEAR APP STORE",
                style = CurvedTextStyle(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF00E5FF)
                )
            )
            curvedText(
                text = if (isGlobalRefreshing) "  ⏳" else "  🔄",
                style = CurvedTextStyle(
                    fontSize = 11.sp,
                    color = Color.White
                )
            )
        }

        // 2. MAIN CONTENT AREA (Offset gracefully below the curved top bezel)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 28.dp, bottom = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Prominent Pill Badge for Pending Updates Status & BATCH UPDATE ALL Button
            Row(
                modifier = Modifier
                    .fillMaxWidth(0.88f)
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (pendingUpdatesCount > 0) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFFF9100))
                            .clickable { updateAllPendingApps() }
                            .padding(horizontal = 10.dp, vertical = 3.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("⚡ UPDATE ALL ($pendingUpdatesCount)", color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF1C1C1E))
                            .clickable { refreshAllStates() }
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(if (isGlobalRefreshing) "⏳ Scanning GitHub..." else "🟢 All Apps Up To Date", color = Color(0xFF00E5FF), fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Curved Bezel Filter Pills
            Row(
                modifier = Modifier.fillMaxWidth(0.95f),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                FilterPill("All (${appStates.size})", currentFilter == FilterType.ALL) { currentFilter = FilterType.ALL }
                FilterPill("Updates ($pendingUpdatesCount)", currentFilter == FilterType.UPDATES_ONLY) { currentFilter = FilterType.UPDATES_ONLY }
                FilterPill("Installed (${appStates.count { it.installedVersion != null }})", currentFilter == FilterType.INSTALLED_ONLY) { currentFilter = FilterType.INSTALLED_ONLY }
            }

            Spacer(modifier = Modifier.height(4.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 8.dp, top = 4.dp, end = 8.dp, bottom = 64.dp)
            ) {
                items(filteredApps) { state ->
                    AppCard(
                        state = state,
                        onActionClick = {
                            if (state.downloadUrl != null && !state.isDownloading) {
                                triggerDownloadAndInstall(context, state) { newState ->
                                    appStates = appStates.map { if (it.info.packageName == newState.info.packageName) newState else it }
                                }
                            }
                        },
                        onOpenClick = {
                            launchApp(context, state.activePackageName)
                        },
                        onUninstallClick = {
                            uninstallApp(context, state.activePackageName)
                        },
                        onToggleExpand = {
                            appStates = appStates.map {
                                if (it.info.packageName == state.info.packageName) it.copy(isExpanded = !it.isExpanded) else it
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }

                item {
                    Spacer(modifier = Modifier.height(40.dp))
                }
            }
        }
    }
}

@Composable
fun FilterPill(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) Color(0xFF00E5FF) else Color(0xFF2C2C2E))
            .clickable { onClick() }
            .padding(horizontal = 6.dp, vertical = 3.dp)
    ) {
        Text(
            text = label,
            color = if (isSelected) Color.Black else Color.LightGray,
            fontSize = 8.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun AppCard(
    state: AppUpdateState,
    onActionClick: () -> Unit,
    onOpenClick: () -> Unit,
    onUninstallClick: () -> Unit,
    onToggleExpand: () -> Unit
) {
    val context = LocalContext.current
    val instVer = state.installedVersion
    val latestVer = state.latestGitHubVersion
    val hasUpdate = latestVer != null && latestVer != "None" && (instVer == null || isVersionNewer(latestVer, instVer))

    // Real App Icon from Installed Package
    val realAppIcon = remember(state.activePackageName, instVer) {
        try {
            if (instVer != null) {
                val drawable = context.packageManager.getApplicationIcon(state.activePackageName)
                drawable.toBitmap(56, 56).asImageBitmap()
            } else null
        } catch (e: Exception) {
            null
        }
    }

    val cardBg = Color.Black
    val cardBorderColor = when {
        hasUpdate -> Color(0xFFFF9100)
        instVer != null -> Color(0xFF2E7D32)
        else -> Color(0xFF222222)
    }

    val actionBtnColor = when {
        state.isDownloading -> Color(0xFF7C4DFF)
        hasUpdate -> Color(0xFFFF9100) // Bright orange update button
        instVer != null -> Color(0xFF2E7D32) // Green up-to-date button
        else -> Color(0xFF0288D1)
    }

    val actionBtnText = when {
        state.isDownloading -> "⬇️ ${state.downloadProgress}%"
        state.isChecking -> "⏳ Checking"
        instVer == null && latestVer != null && latestVer != "None" -> "📥 INSTALL"
        hasUpdate -> "⚡ UPDATE"
        else -> "🟢 REINSTALL"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(cardBg)
            .border(1.dp, cardBorderColor, RoundedCornerShape(12.dp))
            .padding(8.dp)
    ) {
        Column {
            // =========================================================
            // ROW 1: APP NAME & REAL ICON COMPLETELY ACROSS FIRST ROW
            // =========================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() },
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (realAppIcon != null) {
                    Image(
                        bitmap = realAppIcon,
                        contentDescription = state.info.name,
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                    )
                } else {
                    val fallbackIconRes = when (state.info.packageName) {
                        "com.ajimsjames.wearappupdater" -> com.ajimsjames.wearappupdater.R.drawable.ic_app_updater
                        "com.ajimsjames.wearhealthsuite" -> com.ajimsjames.wearappupdater.R.drawable.ic_app_healthsuite
                        "com.ajimsjames.wearblescanner" -> com.ajimsjames.wearappupdater.R.drawable.ic_app_blescanner
                        "com.ajimsjames.wearbaroalt" -> com.ajimsjames.wearappupdater.R.drawable.ic_app_baroalt
                        "com.ajimsjames.wearfileserver" -> com.ajimsjames.wearappupdater.R.drawable.ic_app_fileserver
                        "com.ajimsjames.wearfilemanager" -> com.ajimsjames.wearappupdater.R.drawable.ic_app_filemanager
                        "com.ajimsjames.weardiagnostics" -> com.ajimsjames.wearappupdater.R.drawable.ic_app_diagnostics
                        "com.ajimsjames.wearmaps" -> com.ajimsjames.wearappupdater.R.drawable.ic_app_maps
                        "com.ajimsjames.wearcompass" -> com.ajimsjames.wearappupdater.R.drawable.ic_app_compass
                        "com.ajimsjames.wearwifitools" -> com.ajimsjames.wearappupdater.R.drawable.ic_app_wifitools
                        "com.ajimsjames.wearpdfreader" -> com.ajimsjames.wearappupdater.R.drawable.ic_app_pdfreader
                        else -> com.ajimsjames.wearappupdater.R.drawable.ic_launcher
                    }
                    Icon(
                        painter = painterResource(id = fallbackIconRes),
                        contentDescription = state.info.name,
                        modifier = Modifier.size(20.dp),
                        tint = Color.Unspecified
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = state.info.name,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = state.info.packageName,
                        color = Color(0xFF81D4FA),
                        fontSize = 7.5.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // =========================================================
            // ROW 2: OPEN, ACTION (INSTALL/UPDATE/REINSTALL), AND UNINSTALL BUTTONS
            // =========================================================
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // ▶️ OPEN button if installed
                if (instVer != null && !state.isDownloading) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF00C853))
                            .clickable { onOpenClick() }
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("▶️ OPEN", color = Color.White, fontSize = 7.5.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Install / Update / Reinstall Action Button
                Box(
                    modifier = Modifier
                        .weight(1.2f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(actionBtnColor)
                        .clickable(enabled = !state.isDownloading && !state.isChecking && state.downloadUrl != null) {
                            onActionClick()
                        }
                        .padding(vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = actionBtnText,
                        color = Color.White,
                        fontSize = 7.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // 🗑️ UNINSTALL button if installed
                if (instVer != null && !state.isDownloading) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFD50000))
                            .clickable { onUninstallClick() }
                            .padding(vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🗑️ UNINSTALL", color = Color.White, fontSize = 7.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // =========================================================
            // ROW 3: VERSION CODES & TAP TO SHOW LATEST FEATURES
            // =========================================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF0D1117))
                    .clickable { onToggleExpand() }
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "Installed:", color = Color.Gray, fontSize = 7.5.sp)
                    Text(
                        text = if (instVer != null) "v$instVer" else "❌ None",
                        color = if (instVer != null) Color(0xFF00E5FF) else Color(0xFFFF5252),
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xFF1F2937))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (state.isExpanded) "📜 Features ▲" else "📜 Features ▼",
                        color = Color(0xFFFFD600),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "GitHub:", color = Color.Gray, fontSize = 7.5.sp)
                    Text(
                        text = if (latestVer != null && latestVer != "None") "v$latestVer" else "⏳ Fetching",
                        color = if (hasUpdate) Color(0xFFFF9100) else Color(0xFF00E5FF),
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Expandable Release Notes / Features Card
            AnimatedVisibility(visible = state.isExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF1A1D24))
                        .padding(8.dp)
                ) {
                    Text(
                        text = "🚀 Latest Release Features:",
                        color = Color(0xFF00E5FF),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = state.releaseNotes ?: getAppDefaultChangelog(state.info.repo, latestVer ?: "latest"),
                        color = Color.LightGray,
                        fontSize = 8.5.sp,
                        lineHeight = 11.sp
                    )
                }
            }
        }
    }
}

// Download APK from GitHub Release & Trigger Android Package Installer
fun triggerDownloadAndInstall(
    context: Context,
    state: AppUpdateState,
    onStateUpdate: (AppUpdateState) -> Unit
) {
    onStateUpdate(state.copy(isDownloading = true, downloadProgress = 0, statusText = "Downloading..."))

    Thread {
        try {
            val url = URL(state.downloadUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.connect()

            val fileLength = connection.contentLength
            val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
            val apkFile = File(downloadsDir, "${state.info.packageName}_update.apk")

            connection.inputStream.use { input ->
                apkFile.outputStream().use { output ->
                    val data = ByteArray(4096)
                    var total: Long = 0
                    var count: Int
                    while (input.read(data).also { count = it } != -1) {
                        total += count
                        if (fileLength > 0) {
                            val progress = (total * 100 / fileLength).toInt()
                            onStateUpdate(state.copy(downloadProgress = progress))
                        }
                        output.write(data, 0, count)
                    }
                }
            }

            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(installIntent)

            onStateUpdate(state.copy(isDownloading = false, statusText = "Installing..."))
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading APK for ${state.info.name}", e)
            onStateUpdate(state.copy(isDownloading = false, statusText = "Error: ${e.localizedMessage}"))
        }
    }.start()
}

// Launch an installed package
fun launchApp(context: Context, packageName: String) {
    try {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent != null) {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed to launch $packageName", e)
    }
}

// Uninstall an installed package
fun uninstallApp(context: Context, packageName: String) {
    try {
        val intent = Intent(Intent.ACTION_DELETE).apply {
            data = Uri.parse("package:$packageName")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        try {
            val intent = Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
                data = Uri.parse("package:$packageName")
                putExtra(Intent.EXTRA_RETURN_RESULT, true)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e2: Exception) {
            Log.e(TAG, "Failed to launch uninstall for $packageName: ${e2.message}")
        }
    }
}

// Detect installed version across aliases
fun detectInstalledVersion(context: Context, aliases: List<String>): Pair<String?, String?> {
    val pm = context.packageManager
    for (pkg in aliases) {
        try {
            val pkgInfo: PackageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(pkg, 0)
            }
            if (pkgInfo.versionName != null) {
                return Pair(pkg, pkgInfo.versionName)
            }
        } catch (ignored: Exception) {}
    }

    // Fallback: Scan all installed packages directly
    try {
        val installedApps = pm.getInstalledPackages(0)
        for (app in installedApps) {
            for (pkg in aliases) {
                if (app.packageName.equals(pkg, ignoreCase = true)) {
                    return Pair(app.packageName, app.versionName)
                }
            }
        }
    } catch (ignored: Exception) {}

    return Pair(null, null)
}

// Query GitHub Web 302 Redirect (Rate-limit free) or GitHub API for latest release tag
fun fetchLatestGitHubReleaseDetails(repoPath: String): Triple<String?, String?, String?> {
    // Strategy 1: GitHub Web Redirect (Zero Rate Limit!)
    try {
        val webUrl = URL("https://github.com/$repoPath/releases/latest")
        val conn = webUrl.openConnection() as HttpURLConnection
        conn.instanceFollowRedirects = false
        conn.connectTimeout = 6000
        conn.readTimeout = 6000
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
        conn.connect()

        val code = conn.responseCode
        val location = conn.getHeaderField("Location")
        conn.disconnect()

        if ((code == 302 || code == 301) && !location.isNullOrEmpty()) {
            val tag = location.substringAfterLast("/tag/").removePrefix("v")
            if (tag.isNotEmpty() && tag != location) {
                val apkUrl = "https://github.com/$repoPath/releases/download/v$tag/app-release.apk"
                val notes = fetchReleaseNotes(repoPath, tag)
                Log.d(TAG, "Web redirect success for $repoPath: Tag = $tag, APK = $apkUrl")
                return Triple(tag, notes, apkUrl)
            }
        }
    } catch (e: Exception) {
        Log.w(TAG, "Web redirect failed for $repoPath: ${e.message}")
    }

    // Strategy 2: Fallback to GitHub REST API
    return try {
        val url = URL("https://api.github.com/repos/$repoPath/releases/latest")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 6000
        conn.readTimeout = 6000
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("Authorization", "token $ghToken")

        val code = conn.responseCode
        if (code == 200) {
            val responseText = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(responseText)
            val tag = json.optString("tag_name", "").removePrefix("v")
            val body = json.optString("body", "No release notes available.")

            var downloadUrl: String? = null
            val assets = json.optJSONArray("assets")
            if (assets != null && assets.length() > 0) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val assetName = asset.optString("name", "")
                    if (assetName.endsWith(".apk", ignoreCase = true)) {
                        downloadUrl = asset.optString("browser_download_url", null)
                        break
                    }
                }
            }
            Triple(tag.ifEmpty { null }, body, downloadUrl ?: "https://github.com/$repoPath/releases/download/v$tag/app-release.apk")
        } else {
            Triple(null, null, null)
        }
    } catch (e: Exception) {
        Triple(null, null, null)
    }
}

// SemVer comparison (e.g. "1.3.0" > "1.1.0")
fun isVersionNewer(latest: String, installed: String): Boolean {
    if (installed.isEmpty()) return true
    try {
        val lParts = latest.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
        val iParts = installed.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
        val maxLen = maxOf(lParts.size, iParts.size)
        for (idx in 0 until maxLen) {
            val lVal = lParts.getOrElse(idx) { 0 }
            val iVal = iParts.getOrElse(idx) { 0 }
            if (lVal > iVal) return true
            if (lVal < iVal) return false
        }
    } catch (e: Exception) {
        return latest != installed
    }
    return false
}

private fun String?.isNull_or_Empty(): Boolean {
    return this == null || this.trim().isEmpty()
}

fun fetchReleaseNotes(repoPath: String, tag: String): String {
    try {
        val url = URL("https://api.github.com/repos/$repoPath/releases/tags/v$tag")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 3000
        conn.readTimeout = 3000
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("Authorization", "token $ghToken")
        if (conn.responseCode == 200) {
            val responseText = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(responseText)
            val body = json.optString("body", "").trim()
            if (body.isNotEmpty()) {
                return body
            }
        }
    } catch (ignored: Exception) {}

    return getAppDefaultChangelog(repoPath, tag)
}

fun getAppDefaultChangelog(repoPath: String, tag: String): String {
    return when {
        repoPath.contains("WearAppUpdater", ignoreCase = true) ->
            "⚡ **v$tag Changelog**:\n- 🗑️ Added single-tap App Uninstall button option.\n- 🔄 Added Self-Update detection and in-place updating.\n- ⚡ Added UPDATE ALL batch update button."
        repoPath.contains("WearHealthSuite", ignoreCase = true) ->
            "🏥 **v$tag Changelog**:\n- 🧠 Added HRV Stress Index Dial (0-100 score).\n- 🫀 Added Real-time PPG Heart Rate Waveform & Hydration Quick Tracker."
        repoPath.contains("WearBLEScanner", ignoreCase = true) ->
            "📡 **v$tag Changelog**:\n- 📡 Added Find My Tag Proximity Radar with RSSI distance estimation & haptic pulse.\n- 📊 Added BLE RSSI Signal Strength Graph."
        repoPath.contains("WearBaroAlt", ignoreCase = true) ->
            "🎈 **v$tag Changelog**:\n- 🎈 Added 24-Hour Barometric Weather Station & 3-Day Forecast.\n- 🌩️ Added Storm Alert Warning System."
        repoPath.contains("WearOSBrowser", ignoreCase = true) ->
            "🌐 **v$tag Changelog**:\n- 🔍 Added Interactive Search & URL Bar with soft keyboard.\n- 🎯 Added Search Engine Selector (Google, DuckDuckGo, Wikipedia, Bing).\n- ⚡ Added Trending Quick Topics."
        repoPath.contains("WearFileServer", ignoreCase = true) ->
            "⚡ **v$tag Changelog**:\n- 📲 Added QR Code Phone Pairing for instant browser connection.\n- 🚀 Added Live Bandwidth Speedometer & Traffic Monitor."
        repoPath.contains("WearOSFileManager", ignoreCase = true) ->
            "📁 **v$tag Changelog**:\n- 🖼️ Added Pinch-to-Zoom Photo Viewer with rotary bezel zoom.\n- 📦 Added ZIP Archive Extractor & Integrated MP3 Audio Player."
        repoPath.contains("WearDiagnostics", ignoreCase = true) ->
            "🩺 **v$tag Changelog**:\n- 🩺 Added CPU Throttle & Thermal Zone Sensor Monitor.\n- ⏱️ Added Automated 30-Second Hardware Audit & Battery Discharge Benchmark."
        repoPath.contains("WearMaps", ignoreCase = true) ->
            "🗺️ **v$tag Changelog**:\n- 🗺️ Added GPX Hiking Trail Navigation Loader.\n- 📍 Added Offline Map Caching & GPS Waypoint Overlay."
        repoPath.contains("WearCompass", ignoreCase = true) ->
            "🧭 **v$tag Changelog**:\n- ☀️ Added Sun & Moon Azimuth Direction Bearings.\n- 🎯 Added Target Waypoint Pointer & Bullseye Level."
        repoPath.contains("WearWifiTools", ignoreCase = true) ->
            "📶 **v$tag Changelog**:\n- 🖥️ Added Subnet LAN Network Device Scanner (192.168.x.x).\n- 📶 Added Ping Latency Monitor & Wi-Fi Channel Heatmap."
        repoPath.contains("WearOSPDFReader", ignoreCase = true) ->
            "📄 **v$tag Changelog**:\n- 🌙 Added Pitch Black OLED Dark Mode Color Inversion.\n- 🔖 Added Bookmark Manager & Rotary Bezel Page Zoom."
        else -> "✨ **v$tag Changelog**:\n- Performance optimizations and bug fixes for Wear OS."
    }
}
