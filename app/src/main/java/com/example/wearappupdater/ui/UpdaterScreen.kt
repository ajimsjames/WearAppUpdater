package com.example.wearappupdater.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.wear.compose.foundation.CurvedLayout
import androidx.wear.compose.foundation.CurvedModifier
import androidx.wear.compose.foundation.CurvedTextStyle
import androidx.wear.compose.foundation.curvedComposable
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.curvedText
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

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
            WatchAppInfo("WearHealthSuite", "ajimsjames/WearHealthSuite", "com.example.wearhealthsuite", listOf("com.example.wearhealthsuite"), "🏥"),
            WatchAppInfo("WearBLEScanner", "ajimsjames/WearBLEScanner", "com.example.wearblescanner", listOf("com.example.wearblescanner"), "📡"),
            WatchAppInfo("WearBaroAlt", "ajimsjames/WearBaroAlt", "com.example.wearbaroalt", listOf("com.example.wearbaroalt"), "🎈"),
            WatchAppInfo("WearOSBrowser", "ajimsjames/WearOSBrowser", "com.example.wearosbrowser", listOf("com.example.wearosbrowser"), "🌐"),
            WatchAppInfo("WearFileServer", "ajimsjames/WearFileServer", "com.example.wearfileserver", listOf("com.example.wearfileserver"), "⚡"),
            WatchAppInfo("WearFileManager", "ajimsjames/WearOSFileManager", "com.example.wearfilemanager", listOf("com.example.wearfilemanager", "com.example.wearosfilemanager"), "📁"),
            WatchAppInfo("WearDiagnostics", "ajimsjames/WearDiagnostics", "com.example.weardiagnostics", listOf("com.example.weardiagnostics"), "🩺"),
            WatchAppInfo("WearMaps", "ajimsjames/WearMaps", "com.example.wearmaps", listOf("com.example.wearmaps"), "🗺️"),
            WatchAppInfo("WearCompass", "ajimsjames/WearCompass", "com.example.wearcompass", listOf("com.example.wearcompass"), "🧭"),
            WatchAppInfo("WearWifiTools", "ajimsjames/WearWifiTools", "com.example.wearwifitools", listOf("com.example.wearwifitools"), "📶"),
            WatchAppInfo("WearPDFReader", "ajimsjames/WearOSPDFReader", "com.example.wearpdfreader", listOf("com.example.wearpdfreader"), "📄"),
            WatchAppInfo("WearGram", "ajimsjames/WearGram", "com.example.weargram", listOf("com.example.weargram"), "📱")
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

    // Initial scan on launch
    LaunchedEffect(Unit) {
        refreshAllStates()
    }

    // Query GitHub API for each app
    LaunchedEffect(isGlobalRefreshing) {
        if (isGlobalRefreshing) {
            withContext(Dispatchers.IO) {
                val updatedList = appStates.map { state ->
                    val (latestVer, notes, apkUrl) = fetchLatestGitHubReleaseDetails(state.info.repo)
                    val instVer = state.installedVersion
                    val statusMsg = when {
                        latestVer == null -> "No GitHub release"
                        instVer == null -> "📥 Not installed (v$latestVer)"
                        isVersionNewer(latestVer, instVer) -> "⚡ UPDATE AVAILABLE (v$latestVer)"
                        else -> "🟢 Up to date (v$instVer)"
                    }
                    state.copy(
                        latestGitHubVersion = latestVer,
                        releaseNotes = notes,
                        downloadUrl = apkUrl,
                        isChecking = false,
                        statusText = statusMsg
                    )
                }
                withContext(Dispatchers.Main) {
                    appStates = updatedList
                    isGlobalRefreshing = false
                }
            }
        }
    }

    val pendingUpdatesCount = remember(appStates) {
        appStates.count { s -> s.latestGitHubVersion != null && isVersionNewer(s.latestGitHubVersion!!, s.installedVersion ?: "") }
    }

    val filteredApps = remember(appStates, currentFilter) {
        when (currentFilter) {
            FilterType.ALL -> appStates
            FilterType.UPDATES_ONLY -> appStates.filter { s -> s.latestGitHubVersion != null && isVersionNewer(s.latestGitHubVersion!!, s.installedVersion ?: "") }
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
            // Pill Badge for Pending Updates Status
            Row(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (pendingUpdatesCount > 0) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFFF9100))
                            .clickable { refreshAllStates() }
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("⚡ $pendingUpdatesCount Updates Ready", color = Color.Black, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF1C1C1E))
                            .clickable { refreshAllStates() }
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text("🟢 All Apps Up To Date", color = Color(0xFF00E5FF), fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            // Curved Bezel Filter Pills
            Row(
                modifier = Modifier.fillMaxWidth(0.92f),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                FilterPill("All (${appStates.size})", currentFilter == FilterType.ALL) { currentFilter = FilterType.ALL }
                FilterPill("Updates ($pendingUpdatesCount)", currentFilter == FilterType.UPDATES_ONLY) { currentFilter = FilterType.UPDATES_ONLY }
                FilterPill("Installed (${appStates.count { it.installedVersion != null }})", currentFilter == FilterType.INSTALLED_ONLY) { currentFilter = FilterType.INSTALLED_ONLY }
            }

            Spacer(modifier = Modifier.height(4.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
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
                        onToggleExpand = {
                            appStates = appStates.map {
                                if (it.info.packageName == state.info.packageName) it.copy(isExpanded = !it.isExpanded) else it
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(6.dp))
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
    onToggleExpand: () -> Unit
) {
    val instVer = state.installedVersion
    val latestVer = state.latestGitHubVersion
    val hasUpdate = latestVer != null && (instVer == null || isVersionNewer(latestVer, instVer))

    val cardBg = when {
        hasUpdate -> Color(0xFF332000) // Amber accent for pending update
        instVer != null -> Color(0xFF161B22) // Sleek dark gray for installed
        else -> Color(0xFF1C1C1E)
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
        instVer == null && latestVer != null -> "📥 INSTALL"
        hasUpdate -> "⚡ UPDATE"
        else -> "🟢 RE-INSTALL"
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(cardBg)
            .clickable { onToggleExpand() }
            .padding(10.dp)
    ) {
        Column {
            // App Title & Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Text(state.info.iconEmoji, fontSize = 16.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = state.info.name,
                        color = Color.White,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Launch Button if app is installed
                    if (instVer != null && !state.isDownloading) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF00C853))
                                .clickable { onOpenClick() }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Text("▶️ OPEN", color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                    }

                    // Install / Update / Reinstall Button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(actionBtnColor)
                            .clickable(enabled = !state.isDownloading && !state.isChecking && state.downloadUrl != null) {
                                onActionClick()
                            }
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = actionBtnText,
                            color = Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // CLEAR SIDE-BY-SIDE VERSION READOUT
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF0D1117))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Installed:",
                        color = Color.Gray,
                        fontSize = 8.sp
                    )
                    Text(
                        text = if (instVer != null) "v$instVer" else "❌ Not Installed",
                        color = if (instVer != null) Color(0xFF00E5FF) else Color(0xFFFF5252),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "GitHub Latest:",
                        color = Color.Gray,
                        fontSize = 8.sp
                    )
                    Text(
                        text = if (latestVer != null) "v$latestVer" else "Checking...",
                        color = if (hasUpdate) Color(0xFFFFD54F) else Color(0xFF69F0AE),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Expandable Release Notes
            AnimatedVisibility(visible = state.isExpanded && !state.releaseNotes.isNull_or_Empty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF21262D))
                        .padding(6.dp)
                ) {
                    Text("📜 Release Notes (v${latestVer ?: ""}):", color = Color(0xFF58A6FF), fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = state.releaseNotes ?: "",
                        color = Color(0xFFC9D1D9),
                        fontSize = 8.sp,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis
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
    val apkUrl = state.downloadUrl ?: return
    onStateUpdate(state.copy(isDownloading = true, downloadProgress = 0, statusText = "Downloading APK..."))

    Thread {
        try {
            val url = URL(apkUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 10000
            connection.readTimeout = 20000
            connection.connect()

            val fileLength = connection.contentLength
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()

            val apkFile = File(downloadsDir, "${state.info.name}-latest.apk")
            val input = connection.inputStream
            val output = apkFile.outputStream()

            val data = ByteArray(8192)
            var total = 0L
            var count: Int
            while (input.read(data).also { count = it } != -1) {
                total += count
                output.write(data, 0, count)
                if (fileLength > 0) {
                    val progress = ((total * 100) / fileLength).toInt()
                    onStateUpdate(state.copy(isDownloading = true, downloadProgress = progress))
                }
            }

            output.flush()
            output.close()
            input.close()

            // Trigger Installation via FileProvider
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
            e.printStackTrace()
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
        e.printStackTrace()
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

// Query GitHub API for latest release tag, body release notes, and APK download URL
fun fetchLatestGitHubReleaseDetails(repoPath: String): Triple<String?, String?, String?> {
    return try {
        val url = URL("https://api.github.com/repos/$repoPath/releases/latest")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        conn.setRequestProperty("User-Agent", "WearAppUpdater/1.3.0")

        if (conn.responseCode == 200) {
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
            Triple(tag.ifEmpty { null }, body, downloadUrl)
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
        val lParts = latest.split(".").map { it.toIntOrNull() ?: 0 }
        val iParts = installed.split(".").map { it.toIntOrNull() ?: 0 }
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
