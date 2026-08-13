package com.lumocraft.app.data.update

import com.lumocraft.app.core.config.AppConfig
import com.lumocraft.app.core.version.VersionManager
import com.lumocraft.app.data.network.HttpClient
import com.lumocraft.app.domain.update.ReleaseInfo
import com.lumocraft.app.domain.update.UpdateCheckResult
import com.lumocraft.app.domain.update.UpdateRepository
import com.lumocraft.app.domain.update.UpdateStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Update checks against the GitHub releases channel:
 * `GET /repos/<owner>/<repo>/releases/latest`.
 *
 * The response carries the newest release (latest *non-prerelease* by
 * default; GitHub's `latest` endpoint returns the newest release that is
 * not marked as a draft or a prerelease). Version comparison is handled
 * by [VersionManager].
 *
 * Read-only by design — this repository never downloads an APK. The
 * user taps the release page from the UI instead.
 */
class GithubUpdateRepository(
    private val client: HttpClient,
) : UpdateRepository {

    override suspend fun checkForUpdates(): UpdateCheckResult = withContext(Dispatchers.IO) {
        val current = VersionManager.current()
        client.get(AppConfig.GITHUB_RELEASES_LATEST_URL).fold(
            onSuccess = { body -> parseAndCompare(current.display, body) },
            onFailure = { error ->
                UpdateCheckResult(
                    status = UpdateStatus.UNKNOWN,
                    currentVersionName = current.display,
                    error = error.message
                )
            }
        )
    }

    private fun parseAndCompare(currentName: String, body: String): UpdateCheckResult {
        val json = runCatching { JSONObject(body) }.getOrNull()
            ?: return UpdateCheckResult(
                status = UpdateStatus.UNKNOWN,
                currentVersionName = currentName,
                error = "Invalid release payload"
            )

        val tag = json.optString("tag_name", "").takeIf { it.isNotEmpty() }
            ?: return UpdateCheckResult(
                status = UpdateStatus.UNKNOWN,
                currentVersionName = currentName,
                error = "Missing tag_name"
            )

        val candidate = VersionManager.parse(tag)
            ?: return UpdateCheckResult(
                status = UpdateStatus.UNKNOWN,
                currentVersionName = currentName,
                error = "Unparseable release version: $tag"
            )

        val current = VersionManager.parse(currentName)
        val updateAvailable = current == null || VersionManager.isNewer(current, candidate)

        val assets = json.optJSONArray("assets")
        val downloadUrl = assets
            ?.let { arr ->
                (0 until arr.length()).firstNotNullOfOrNull { i ->
                    val asset = arr.optJSONObject(i)
                    val name = asset?.optString("name", "")
                    if (name?.endsWith(".apk") == true) asset.optString("browser_download_url").takeIf { it.isNotEmpty() }
                        ?: asset.optString("url").takeIf { it.isNotEmpty() }
                    else null
                }
            }

        val release = ReleaseInfo(
            tagName = tag,
            versionName = candidate.display,
            isPrerelease = json.optBoolean("prerelease", false),
            publishedAt = json.optString("published_at").takeIf { it.isNotEmpty() },
            body = json.optString("body").takeIf { it.isNotEmpty() },
            releaseUrl = json.optString("html_url").takeIf { it.isNotEmpty() },
            downloadUrl = downloadUrl
        )

        return UpdateCheckResult(
            status = if (updateAvailable) UpdateStatus.UPDATE_AVAILABLE else UpdateStatus.UP_TO_DATE,
            currentVersionName = currentName,
            latest = release
        )
    }
}
