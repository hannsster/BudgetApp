package com.mipresupuestocr.app.data.update

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.mipresupuestocr.app.data.remote.GitHubReleaseService
import com.mipresupuestocr.app.data.remote.UpdateConfig
import com.mipresupuestocr.domain.model.AppError
import com.mipresupuestocr.domain.model.AppResult
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import retrofit2.Retrofit
import java.io.File

/**
 * The updater must only offer real newer releases that actually carry an APK, and a failed download
 * must never leave a file behind that the installer could pick up.
 */
class UpdateRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val server = MockWebServer()
    private lateinit var repository: UpdateRepository

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = true
    }

    @Before
    fun setUp() {
        server.start()
        val client = OkHttpClient()
        val service = Retrofit.Builder()
            .baseUrl(server.url("/"))
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(GitHubReleaseService::class.java)
        repository = UpdateRepository(service, client, UpdateConfig())
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun releaseBody(
        tag: String,
        apkUrl: String = "https://example.com/app.apk",
        prerelease: Boolean = false,
    ): String = """
        {
          "tag_name": "$tag",
          "name": "$tag",
          "body": "Fixes and polish",
          "html_url": "https://github.com/hannsster/BudgetApp/releases/tag/$tag",
          "published_at": "2026-10-22T10:00:00Z",
          "prerelease": $prerelease,
          "draft": false,
          "assets": [
            {"name": "checksums.txt", "browser_download_url": "$apkUrl.sha256", "size": 92},
            {"name": "MiPresupuestoCR.apk", "browser_download_url": "$apkUrl", "size": 2034632}
          ]
        }
    """.trimIndent()

    @Test
    fun `offers a newer release and picks the apk asset`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(releaseBody("v1.3.0")))

        val update = repository.checkForUpdate("1.2.0").getOrNull()

        assertNotNull(update)
        assertEquals("1.3.0", update?.version)
        assertEquals("https://example.com/app.apk", update?.downloadUrl)
        assertEquals(2_034_632L, update?.sizeBytes)
        assertTrue(update?.notes.orEmpty().isNotBlank())
        assertTrue(server.takeRequest().path.orEmpty().contains("/releases/latest"))
    }

    @Test
    fun `says nothing when the installed version is current or newer`() = runTest {
        server.enqueue(MockResponse().setResponseCode(200).setBody(releaseBody("v1.2.0")))
        assertNull(repository.checkForUpdate("1.2.0").getOrNull())

        server.enqueue(MockResponse().setResponseCode(200).setBody(releaseBody("v1.1.0")))
        assertNull(repository.checkForUpdate("1.2.0").getOrNull())
    }

    @Test
    fun `ignores prereleases`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(releaseBody("v1.4.0", prerelease = true)),
        )

        assertNull(repository.checkForUpdate("1.2.0").getOrNull())
    }

    @Test
    fun `a release without an apk asset cannot be installed`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """{"tag_name":"v1.3.0","assets":[{"name":"notes.txt","browser_download_url":"https://example.com/n.txt"}]}""",
            ),
        )

        val result = repository.checkForUpdate("1.2.0")

        assertEquals(AppError.NoData, result.errorOrNull())
    }

    @Test
    fun `server errors are reported, not hidden`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503).setBody("{}"))

        val result = repository.checkForUpdate("1.2.0")

        assertEquals(AppError.Network, result.errorOrNull())
    }

    @Test
    fun `download writes the apk and reports progress`() = runTest {
        val payload = ByteArray(2048) { it.toByte() }
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Length", payload.size)
                .setBody(okio.Buffer().write(payload)),
        )
        val available = com.mipresupuestocr.app.data.remote.AvailableUpdate(
            version = "1.3.0",
            releaseUrl = "https://example.com/release",
            downloadUrl = server.url("/app.apk").toString(),
            sizeBytes = payload.size.toLong(),
            notes = "",
        )
        var lastRead = 0L
        val target = File(tempFolder.newFolder("updates"), "app.apk")

        val result = repository.download(available, target) { read, _ -> lastRead = read }

        val file = (result as AppResult.Success).value
        assertEquals(payload.size.toLong(), file.length())
        assertEquals(payload.size.toLong(), lastRead)
        assertTrue(file.readBytes().contentEquals(payload))
    }

    @Test
    fun `a failed download leaves no file behind`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("{}"))
        val available = com.mipresupuestocr.app.data.remote.AvailableUpdate(
            version = "1.3.0",
            releaseUrl = "",
            downloadUrl = server.url("/missing.apk").toString(),
            sizeBytes = 0,
            notes = "",
        )
        val target = File(tempFolder.newFolder("failed"), "app.apk")

        val result = repository.download(available, target)

        assertEquals(AppError.Network, result.errorOrNull())
        assertFalse(target.exists())
    }
}
