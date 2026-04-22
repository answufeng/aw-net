package com.answufeng.net.http.util

import com.answufeng.net.http.config.NetworkConfig
import com.answufeng.net.http.config.NetworkConfigProvider
import com.answufeng.net.http.model.NetworkResult
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

class DownloadExecutorTest {

    private lateinit var server: MockWebServer
    private lateinit var configProvider: NetworkConfigProvider
    private lateinit var downloadExecutor: DownloadExecutor
    private lateinit var tempDir: File

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        val config = NetworkConfig(baseUrl = server.url("/").toString())
        configProvider = NetworkConfigProvider(config)
        downloadExecutor = DownloadExecutor(configProvider)
        tempDir = File(System.getProperty("java.io.tmpdir"), "download_test_${System.nanoTime()}")
        tempDir.mkdirs()
    }

    @After
    fun teardown() {
        server.shutdown()
        tempDir.deleteRecursively()
    }

    @Test
    fun `downloadFile succeeds with valid response`() = runTest {
        val content = "Hello, World!"
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(content)
        )

        val targetFile = File(tempDir, "test.txt")
        val call: suspend () -> okhttp3.ResponseBody = {
            content.toResponseBody("text/plain".toMediaType())
        }

        val result = downloadExecutor.downloadFile(
            targetFile = targetFile,
            call = call
        )

        assertTrue(result is NetworkResult.Success)
        assertTrue(targetFile.exists())
        assertEquals(content, targetFile.readText())
    }

    @Test
    fun `downloadFile returns TechnicalFailure on exception`() = runTest {
        val targetFile = File(tempDir, "error.txt")
        val call: suspend () -> okhttp3.ResponseBody = {
            throw java.io.IOException("network error")
        }

        val result = downloadExecutor.downloadFile(
            targetFile = targetFile,
            call = call
        )

        assertTrue(result is NetworkResult.TechnicalFailure)
        assertFalse(targetFile.exists())
    }

    @Test
    fun `downloadFile deletes partial file on cancellation`() = runTest {
        val targetFile = File(tempDir, "cancel.txt")

        val call: suspend () -> okhttp3.ResponseBody = {
            throw kotlinx.coroutines.CancellationException("cancelled")
        }

        try {
            downloadExecutor.downloadFile(
                targetFile = targetFile,
                call = call
            )
        } catch (_: kotlinx.coroutines.CancellationException) {
        }

        assertFalse(targetFile.exists())
    }

    @Test
    fun `downloadFile with hash verification succeeds on match`() = runTest {
        val content = "test content for hash"
        val expectedHash = computeSha256(content)

        val targetFile = File(tempDir, "hashed.txt")
        val call: suspend () -> okhttp3.ResponseBody = {
            content.toResponseBody("text/plain".toMediaType())
        }

        val result = downloadExecutor.downloadFile(
            targetFile = targetFile,
            expectedHash = expectedHash,
            call = call
        )

        assertTrue(result is NetworkResult.Success)
        assertTrue(targetFile.exists())
    }

    @Test
    fun `downloadFile with hash verification fails on mismatch`() = runTest {
        val content = "test content for hash"

        val targetFile = File(tempDir, "hash_mismatch.txt")
        val call: suspend () -> okhttp3.ResponseBody = {
            content.toResponseBody("text/plain".toMediaType())
        }

        val result = downloadExecutor.downloadFile(
            targetFile = targetFile,
            expectedHash = "wrong_hash_value",
            hashStrategy = HashVerificationStrategy.DELETE_ON_MISMATCH,
            call = call
        )

        assertTrue(result is NetworkResult.TechnicalFailure)
        assertFalse(targetFile.exists())
    }

    @Test
    fun `downloadFile with KEEP_ON_MISMATCH preserves file`() = runTest {
        val content = "test content"

        val targetFile = File(tempDir, "keep_on_mismatch.txt")
        val call: suspend () -> okhttp3.ResponseBody = {
            content.toResponseBody("text/plain".toMediaType())
        }

        val result = downloadExecutor.downloadFile(
            targetFile = targetFile,
            expectedHash = "wrong_hash",
            hashStrategy = HashVerificationStrategy.KEEP_ON_MISMATCH,
            call = call
        )

        assertTrue(result is NetworkResult.TechnicalFailure)
        assertTrue(targetFile.exists())
    }

    private fun computeSha256(input: String): String {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val digest = md.digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
