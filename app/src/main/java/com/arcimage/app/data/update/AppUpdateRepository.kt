package com.arcimage.app.data.update

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.arcimage.app.BuildConfig
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Result of checking whether a newer version of ArcImage is available.
 *
 * Play Store & Google Play Protect compliance:
 * - Play-installed builds (`com.android.vending`) use Google's official Play In-App Updates API
 *   ([AppUpdateManager]) exclusively and never offer external download URLs.
 * - Non-Play builds query read-only HTTPS release metadata and open the verified release page in
 *   the system browser via [Intent.ACTION_VIEW] — ArcImage never requests `REQUEST_INSTALL_PACKAGES`
 *   and never downloads or executes APK binaries itself.
 */
sealed interface UpdateCheckResult {
    data class UpToDate(val currentVersion: String) : UpdateCheckResult

    data class PlayStoreUpdateAvailable(
        val availableVersionCode: Int,
        val isFlexibleAllowed: Boolean,
        val isImmediateAllowed: Boolean
    ) : UpdateCheckResult

    data object PlayStoreUpdateDownloaded : UpdateCheckResult

    data class ReleasePageUpdateAvailable(
        val currentVersion: String,
        val latestVersion: String,
        val releaseUrl: String,
        val releaseNotes: String = ""
    ) : UpdateCheckResult

    data class CheckFailed(val reason: String = "") : UpdateCheckResult

    val hasUpdate: Boolean
        get() = this is PlayStoreUpdateAvailable ||
            this is PlayStoreUpdateDownloaded ||
            this is ReleasePageUpdateAvailable
}

interface AppUpdateRepository {
    val currentVersionName: String
    fun isAutoCheckEnabled(): Boolean
    fun setAutoCheckEnabled(enabled: Boolean)
    suspend fun checkForUpdates(isManual: Boolean): UpdateCheckResult?
    fun launchUpdateFlow(activity: Activity, result: UpdateCheckResult)
    fun completeFlexiblePlayUpdate()
}

class AppUpdateRepositoryImpl(
    private val context: Context,
    private val currentVersionOverride: String? = null,
    private val clockMs: () -> Long = System::currentTimeMillis
) : AppUpdateRepository {

    companion object {
        private const val PREFS_NAME = "arcimage_update_prefs"
        private const val KEY_AUTO_CHECK = "auto_check_enabled"
        private const val KEY_LAST_AUTO_CHECK_MS = "last_auto_check_ms"
        private const val AUTO_CHECK_COOLDOWN_MS = 12L * 60L * 60L * 1000L // 12 hours
        private const val PLAY_STORE_INSTALLER = "com.android.vending"
        private const val GITHUB_LATEST_RELEASE_API =
            "https://api.github.com/repos/petrosvassiliou/ArcImage/releases/latest"
        private const val ALLOWED_RELEASE_URL_PREFIX =
            "https://github.com/petrosvassiliou/ArcImage/releases"
    }

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val appUpdateManager: AppUpdateManager? by lazy {
        runCatching { AppUpdateManagerFactory.create(context) }.getOrNull()
    }

    @Volatile
    private var cachedPlayUpdateInfo: AppUpdateInfo? = null

    private var installListenerRegistered = false
    private val installStateListener = InstallStateUpdatedListener { state ->
        if (state.installStatus() == InstallStatus.DOWNLOADED) {
            onPlayUpdateDownloaded?.invoke()
        }
    }

    var onPlayUpdateDownloaded: (() -> Unit)? = null

    override val currentVersionName: String
        get() = currentVersionOverride ?: BuildConfig.VERSION_NAME

    override fun isAutoCheckEnabled(): Boolean =
        runCatching { prefs.getBoolean(KEY_AUTO_CHECK, true) }.getOrDefault(true)

    override fun setAutoCheckEnabled(enabled: Boolean) {
        runCatching { prefs.edit().putBoolean(KEY_AUTO_CHECK, enabled).apply() }
    }

    override suspend fun checkForUpdates(isManual: Boolean): UpdateCheckResult? {
        if (!isManual) {
            if (!isAutoCheckEnabled()) return null
            val lastCheck = runCatching { prefs.getLong(KEY_LAST_AUTO_CHECK_MS, 0L) }.getOrDefault(0L)
            if (clockMs() - lastCheck < AUTO_CHECK_COOLDOWN_MS) return null
            runCatching { prefs.edit().putLong(KEY_LAST_AUTO_CHECK_MS, clockMs()).apply() }
        }

        val installedFromPlay = isInstalledFromPlayStore(context)

        // 1. Always prefer official Google Play In-App Updates when available or Play-installed
        val playResult = queryPlayInAppUpdate()
        if (playResult != null) {
            return playResult
        }
        if (installedFromPlay) {
            // Play-distributed builds strictly use Google Play only (Play policy compliance)
            return UpdateCheckResult.UpToDate(currentVersionName)
        }

        // 2. Non-Play installs: read-only HTTPS check against GitHub Releases
        return withContext(Dispatchers.IO) {
            queryGitHubLatestRelease()
        }
    }

    override fun launchUpdateFlow(activity: Activity, result: UpdateCheckResult) {
        when (result) {
            is UpdateCheckResult.PlayStoreUpdateDownloaded -> {
                completeFlexiblePlayUpdate()
            }

            is UpdateCheckResult.PlayStoreUpdateAvailable -> {
                val manager = appUpdateManager
                val info = cachedPlayUpdateInfo
                if (manager != null && info != null) {
                    val type = if (result.isFlexibleAllowed) {
                        registerInstallListenerIfNeeded(manager)
                        AppUpdateType.FLEXIBLE
                    } else {
                        AppUpdateType.IMMEDIATE
                    }
                    runCatching {
                        manager.startUpdateFlow(
                            info,
                            activity,
                            AppUpdateOptions.newBuilder(type).build()
                        )
                    }.onFailure {
                        openPlayStoreListing(activity)
                    }
                } else {
                    openPlayStoreListing(activity)
                }
            }

            is UpdateCheckResult.ReleasePageUpdateAvailable -> {
                if (result.releaseUrl.startsWith(ALLOWED_RELEASE_URL_PREFIX)) {
                    val viewIntent = Intent(Intent.ACTION_VIEW, Uri.parse(result.releaseUrl)).apply {
                        addCategory(Intent.CATEGORY_BROWSABLE)
                    }
                    runCatching { activity.startActivity(viewIntent) }
                }
            }

            else -> Unit
        }
    }

    override fun completeFlexiblePlayUpdate() {
        runCatching { appUpdateManager?.completeUpdate() }
    }

    private fun registerInstallListenerIfNeeded(manager: AppUpdateManager) {
        if (!installListenerRegistered) {
            runCatching {
                manager.registerListener(installStateListener)
                installListenerRegistered = true
            }
        }
    }

    private fun openPlayStoreListing(activity: Activity) {
        val pkg = activity.packageName
        val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
        runCatching { activity.startActivity(marketIntent) }.onFailure {
            runCatching {
                activity.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$pkg"))
                )
            }
        }
    }

    private suspend fun queryPlayInAppUpdate(): UpdateCheckResult? {
        val manager = appUpdateManager ?: return null
        val info = suspendCancellableCoroutine<AppUpdateInfo?> { cont ->
            manager.appUpdateInfo
                .addOnSuccessListener { result -> if (cont.isActive) cont.resume(result) }
                .addOnFailureListener { if (cont.isActive) cont.resume(null) }
        } ?: return null

        cachedPlayUpdateInfo = info
        return when {
            info.installStatus() == InstallStatus.DOWNLOADED -> {
                UpdateCheckResult.PlayStoreUpdateDownloaded
            }

            info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE ||
                info.updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS -> {
                UpdateCheckResult.PlayStoreUpdateAvailable(
                    availableVersionCode = info.availableVersionCode(),
                    isFlexibleAllowed = info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE),
                    isImmediateAllowed = info.isUpdateTypeAllowed(AppUpdateType.IMMEDIATE)
                )
            }

            else -> null
        }
    }

    private fun queryGitHubLatestRelease(): UpdateCheckResult {
        var connection: HttpURLConnection? = null
        return try {
            val url = URL(GITHUB_LATEST_RELEASE_API)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8_000
                readTimeout = 8_000
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "ArcImage/${currentVersionName}")
            }
            val status = connection.responseCode
            if (status !in 200..299) {
                return UpdateCheckResult.CheckFailed("HTTP $status")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            parseGithubLatestReleaseJson(body, currentVersionName)
        } catch (e: Exception) {
            UpdateCheckResult.CheckFailed(e.localizedMessage ?: "Network error")
        } finally {
            connection?.disconnect()
        }
    }

    private fun isInstalledFromPlayStore(ctx: Context): Boolean = runCatching {
        val installer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ctx.packageManager.getInstallSourceInfo(ctx.packageName).installingPackageName
        } else {
            @Suppress("DEPRECATION")
            ctx.packageManager.getInstallerPackageName(ctx.packageName)
        }
        installer == PLAY_STORE_INSTALLER
    }.getOrDefault(false)
}

internal object SemanticVersion {
    fun normalize(raw: String): List<Int> {
        val cleaned = raw.trim().removePrefix("v").removePrefix("V").substringBefore('-')
        return cleaned.split('.').map { it.toIntOrNull() ?: 0 }
    }

    fun isNewer(candidate: String, current: String): Boolean {
        val candParts = normalize(candidate)
        val currParts = normalize(current)
        val maxLen = maxOf(candParts.size, currParts.size)
        for (i in 0 until maxLen) {
            val a = candParts.getOrElse(i) { 0 }
            val b = currParts.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }
}

internal fun parseGithubLatestReleaseJson(
    rawJson: String,
    currentVersion: String
): UpdateCheckResult {
    return runCatching {
        val obj = JSONObject(rawJson)
        if (obj.optBoolean("draft", false) || obj.optBoolean("prerelease", false)) {
            return UpdateCheckResult.UpToDate(currentVersion)
        }
        val tagName = obj.optString("tag_name", "").trim()
        val htmlUrl = obj.optString("html_url", "").trim()
        val body = obj.optString("body", "").trim()
        val cleanVersion = tagName.removePrefix("v").removePrefix("V")

        if (cleanVersion.isBlank() ||
            !htmlUrl.startsWith("https://github.com/petrosvassiliou/ArcImage/releases")
        ) {
            return UpdateCheckResult.UpToDate(currentVersion)
        }

        if (SemanticVersion.isNewer(cleanVersion, currentVersion)) {
            UpdateCheckResult.ReleasePageUpdateAvailable(
                currentVersion = currentVersion,
                latestVersion = cleanVersion,
                releaseUrl = htmlUrl,
                releaseNotes = body.take(400)
            )
        } else {
            UpdateCheckResult.UpToDate(currentVersion)
        }
    }.getOrElse { e ->
        UpdateCheckResult.CheckFailed(e.localizedMessage ?: "Invalid response")
    }
}
