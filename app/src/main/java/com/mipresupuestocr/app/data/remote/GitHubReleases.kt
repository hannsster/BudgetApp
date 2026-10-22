package com.mipresupuestocr.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * The app updates itself from its own GitHub Releases page: no store account, no server of ours.
 * The endpoint is public, unauthenticated and read-only; GitHub allows 60 requests per hour per IP,
 * which is far more than a manual check needs.
 */
@Serializable
data class GitHubAssetDto(
    @SerialName("name") val name: String? = null,
    @SerialName("browser_download_url") val downloadUrl: String? = null,
    @SerialName("size") val size: Long? = null,
)

@Serializable
data class GitHubReleaseDto(
    @SerialName("tag_name") val tagName: String? = null,
    @SerialName("name") val name: String? = null,
    @SerialName("body") val body: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    @SerialName("published_at") val publishedAt: String? = null,
    @SerialName("prerelease") val prerelease: Boolean = false,
    @SerialName("draft") val draft: Boolean = false,
    @SerialName("assets") val assets: List<GitHubAssetDto> = emptyList(),
)

interface GitHubReleaseService {
    @GET("repos/{owner}/{repo}/releases/latest")
    suspend fun latestRelease(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
    ): Response<GitHubReleaseDto>
}

/** Where the app looks for its own releases. Kept in one place so it is easy to point elsewhere. */
class UpdateConfig(
    val owner: String = OWNER,
    val repo: String = REPO,
) {
    val releasesPage: String get() = "https://github.com/$owner/$repo/releases"

    companion object {
        const val OWNER = "hannsster"
        const val REPO = "BudgetApp"
        const val BASE_URL = "https://api.github.com/"
    }
}

/** A newer release the user can install. */
data class AvailableUpdate(
    val version: String,
    val releaseUrl: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val notes: String,
)
