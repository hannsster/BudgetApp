package com.mipresupuestocr.app.data.update

import com.mipresupuestocr.app.data.remote.AvailableUpdate
import com.mipresupuestocr.app.data.remote.GitHubReleaseDto
import com.mipresupuestocr.app.data.remote.GitHubReleaseService
import com.mipresupuestocr.app.data.remote.UpdateConfig
import com.mipresupuestocr.app.data.remote.toAppError
import com.mipresupuestocr.domain.model.AppError
import com.mipresupuestocr.domain.model.AppResult
import com.mipresupuestocr.domain.model.AppVersion
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import retrofit2.Response
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Asks GitHub Releases for the newest version and downloads its APK.
 *
 * The download is a plain file into the app cache; installing is left to the UI layer, which hands
 * the file to Android's package installer. Nothing here touches app data, so an update keeps every
 * entry the user has recorded — provided both APKs are signed with the same key.
 */
@Singleton
class UpdateRepository @Inject constructor(
    private val service: GitHubReleaseService,
    private val client: OkHttpClient,
    private val config: UpdateConfig,
) {

    /** Returns null when the installed build is already the newest. */
    suspend fun checkForUpdate(currentVersion: String): AppResult<AvailableUpdate?> {
        val current = AppVersion.parse(currentVersion) ?: return AppResult.failure(AppError.Unknown)

        val response: Response<GitHubReleaseDto> = try {
            service.latestRelease(config.owner, config.repo)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            return AppResult.failure(error.toAppError())
        }

        // Ticker-flavoured error messages would be nonsense here, so HTTP failures read as a
        // service problem rather than being mapped through the market-data vocabulary.
        if (!response.isSuccessful) return AppResult.failure(AppError.Network)
        val body = response.body() ?: return AppResult.failure(AppError.EmptyResponse)
        if (body.draft || body.prerelease) return AppResult.success(null)

        val releaseVersion = AppVersion.parse(body.tagName) ?: return AppResult.failure(AppError.MalformedResponse)
        if (releaseVersion <= current) return AppResult.success(null)

        val apk = body.assets.firstOrNull { it.downloadUrl != null && it.name?.endsWith(".apk") == true }
            ?: return AppResult.failure(AppError.NoData)

        return AppResult.success(
            AvailableUpdate(
                version = releaseVersion.toString(),
                releaseUrl = body.htmlUrl.orEmpty(),
                downloadUrl = apk.downloadUrl!!,
                sizeBytes = apk.size ?: 0L,
                notes = body.body.orEmpty().trim(),
            ),
        )
    }

    /**
     * Streams the APK to [destination], reporting bytes read against the expected total. A partial
     * file is deleted, so a failed download can never be offered for installation.
     */
    suspend fun download(
        update: AvailableUpdate,
        destination: File,
        onProgress: (bytesRead: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ): AppResult<File> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(update.downloadUrl).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext AppResult.failure(AppError.Network)
                }
                val body: ResponseBody = response.body
                    ?: return@withContext AppResult.failure(AppError.EmptyResponse)
                val total = body.contentLength().takeIf { it > 0 } ?: update.sizeBytes

                destination.parentFile?.mkdirs()
                body.byteStream().use { input ->
                    destination.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var read = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count <= 0) break
                            output.write(buffer, 0, count)
                            read += count
                            onProgress(read, total)
                        }
                    }
                }
            }

            if (destination.length() <= 0L) {
                destination.delete()
                AppResult.failure(AppError.EmptyResponse)
            } else {
                AppResult.success(destination)
            }
        } catch (cancellation: CancellationException) {
            destination.delete()
            throw cancellation
        } catch (error: Exception) {
            destination.delete()
            AppResult.failure(error.toAppError())
        }
    }
}
