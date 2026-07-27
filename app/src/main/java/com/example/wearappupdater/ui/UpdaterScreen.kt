package com.example.wearappupdater.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
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
import androidx.wear.compose.material.Text
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
    val iconEmoji: String
)

data class AppUpdateState(
    val info: WatchAppInfo,
    var installedVersion: String? = null,
    var latestGitHubVersion: String? = null,
    var downloadUrl: String? = null,
    var isChecking: Boolean = true,
    var isDownloading: Boolean = false,
    var downloadProgress: Int = 0,
    var statusText: String = "Checking..."
)

@Composable
fun UpdaterScreen() {
    val context = LocalContext.current

    val appList = remember {
        listOf(
            WatchAppInfo("WearHealthSuite", "ajimsjames/WearHealthSuite", "com.example.wearhealthsuite", "🏥"),
            WatchAppInfo("WearBLEScanner", "ajimsjames/WearBLEScanner", "com.example.wearblescanner", "📡"),
            WatchAppInfo("WearBaroAlt", "ajimsjames/WearBaroAlt", "com.example.wearbaroalt", "🎈"),
            WatchAppInfo("WearOSBrowser", "ajimsjames/WearOSBrowser", "com.example.wearosbrowser", "🌐"),
            WatchAppInfo("WearFileServer", "ajimsjames/WearFileServer", "com.example.wearfileserver", "⚡"),
            WatchAppInfo("WearFileManager", "ajimsjames/WearOSFileManager", "com.example.wearosfilemanager", "📁"),
            WatchAppInfo("WearDiagnostics", "ajimsjames/WearDiagnostics", "com.example.weardiagnostics", "🩺"),
            WatchAppInfo("WearMaps", "ajimsjames/WearMaps", "com.example.wearmaps", "🗺️"),
            WatchAppInfo("WearCompass", "ajimsjames/WearCompass", "com.example.wearcompass", "🧭"),
            WatchAppInfo("WearWifiTools", "ajimsjames/WearWifiTools", "com.example.wearwifitools", "📶"),
            WatchAppInfo("WearPDFReader", "ajimsjames/WearOSPDFReader", "com.example.wearpdfreader", "📄")
        )
    }

    var appStates by remember {
        mutableStateOf(appList.map { AppUpdateState(info = it) })
    }

    var isGlobalRefreshing by remember { mutableStateOf(false) }

    fun refreshAllStates() {
        isGlobalRefreshing = true
        appStates = appStates.map { state ->
            val instVer = getInstalledVersion(context, state.info.packageName)
            state.copy(installedVersion = instVer, isChecking = true, statusText = "Checking GitHub...")
        }
    }

    // Initial check on launch
    LaunchedEffect(Unit) {
        refreshAllStates()
    }

    // Asynchronously query GitHub Releases API for each app
    LaunchedEffect(isGlobalRefreshing) {
        if (isGlobalRefreshing) {
            withContext(Dispatchers.IO) {
                val updatedList = appStates.map { state ->
                    val (latestVer, apkUrl) = fetchLatestGitHubRelease(state.info.repo)
                    val instVer = state.installedVersion
                    val statusMsg = when {
                        latestVer == null -> "No release"
                        instVer == null -> "📥 Not installed (v$latestVer)"
                        isVersionNewer(latestVer, instVer) -> "⚡ UPDATE AVAILABLE (v$latestVer)"
                        else -> "🟢 Up to date (v$instVer)"
                    }
                    state.copy(
                        latestGitHubVersion = latestVer,
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 38.dp, bottom = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Store Title Header
            Row(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF1C1C1E))
                    .clickable { refreshAllStates() }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "📦 Wear App Store & Updater",
                    color = Color(0xFF00E5FF),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (isGlobalRefreshing) "⏳" else "🔄",
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
            ) {
                items(appStates) { state ->
                    AppCard(
                        state = state,
                        onActionClick = {
                            if (state.downloadUrl != null && !state.isDownloading) {
                                triggerDownloadAndInstall(context, state) { newState ->
                                    appStates = appStates.map { if (it.info.packageName == newState.info.packageName) newState else it }
                                }
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
fun AppCard(state: AppUpdateState, onActionClick: () -> Unit) {
    val instVer = state.installedVersion
    val latestVer = state.latestGitHubVersion
    val hasUpdate = latestVer != null && (instVer == null || isVersionNewer(latestVer, instVer))

    val cardBg = when {
        hasUpdate -> Color(0xFF2E1C00) // Highlight amber when update available
        instVer != null -> Color(0xFF161B22) // Sleek dark gray when installed & up-to-date
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
            .padding(10.dp)
    ) {
        Column {
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

                // Action Button (Install / Update / Re-install)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(actionBtnColor)
                        .clickable(enabled = !state.isDownloading && !state.isChecking && state.downloadUrl != null) {
                            onActionClick()
                        }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = actionBtnText,
                        color = Color.White,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Version info readout
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Installed: ${instVer ?: "None"}",
                    color = if (instVer != null) Color.LightGray else Color.Gray,
                    fontSize = 9.sp
                )
                Text(
                    text = "GitHub: ${latestVer ?: "Checking..."}",
                    color = if (hasUpdate) Color(0xFFFFD54F) else Color(0xFF00E5FF),
                    fontSize = 9.sp,
                    fontWeight = if (hasUpdate) FontWeight.Bold else FontWeight.Normal
                )
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

// Helper: Query Package Manager for installed version
fun getInstalledVersion(context: Context, packageName: String): String? {
    return try {
        val info = context.packageManager.getPackageInfo(packageName, 0)
        info.versionName
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }
}

// Helper: Query GitHub API for latest release tag and APK download URL
fun fetchLatestGitHubRelease(repoPath: String): Pair<String?, String?> {
    return try {
        val url = URL("https://api.github.com/repos/$repoPath/releases/latest")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        conn.setRequestProperty("User-Agent", "WearAppUpdater/1.0.0")

        if (conn.responseCode == 200) {
            val responseText = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(responseText)
            val tag = json.optString("tag_name", "").removePrefix("v")

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
            Pair<String?, String?>(tag.ifEmpty { null }, downloadUrl)
        } else {
            Pair<String?, String?>(null, null)
        }
    } catch (e: Exception) {
        Pair<String?, String?>(null, null)
    }
}

// Helper: Simple SemVer comparison (e.g. "1.2.0" > "1.1.0")
fun isVersionNewer(latest: String, installed: String): Boolean {
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
