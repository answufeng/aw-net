package com.answufeng.net.http.util

import com.answufeng.net.http.config.NetworkConfig
import com.answufeng.net.http.config.NetworkConfigProvider
import com.answufeng.net.http.model.BaseResponse
import com.answufeng.net.http.model.NetworkResult
import kotlinx.coroutines.test.runTest
import okhttp3.MultipartBody
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

class UploadExecutorTest {

    private lateinit var configProvider: NetworkConfigProvider
    private lateinit var uploadExecutor: UploadExecutor
    private lateinit var tempDir: File

    @Before
    fun setup() {
        val config = NetworkConfig(baseUrl = "https://api.example.com/")
        configProvider = NetworkConfigProvider(config)
        uploadExecutor = UploadExecutor(configProvider)
        tempDir = File(System.getProperty("java.io.tmpdir"), "upload_test_${System.nanoTime()}")
        tempDir.mkdirs()
    }

    @After
    fun teardown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun `createProgressPart creates valid MultipartBody Part`() {
        val testFile = File(tempDir, "test.txt")
        testFile.writeText("upload content")

        val part = uploadExecutor.createProgressPart("file", testFile, null)

        assertNotNull(part)
        assertEquals("file", part.headers?.values("Content-Disposition")?.firstOrNull()?.contains("name=\"file\"") ?: false, true)
    }

    @Test
    fun `uploadFile returns Success on successful upload`() = runTest {
        val testFile = File(tempDir, "upload.txt")
        testFile.writeText("content")

        val response = object : BaseResponse<String> {
            override val code = 0
            override val msg = "ok"
            override val data = "result"
        }

        val result = uploadExecutor.uploadFile(
            file = testFile,
            partName = "file",
            call = { _ -> response }
        )

        assertTrue(result is NetworkResult.Success)
        assertEquals("result", (result as NetworkResult.Success).data)
    }

    @Test
    fun `uploadFile returns BusinessFailure on non-success code`() = runTest {
        val testFile = File(tempDir, "upload_fail.txt")
        testFile.writeText("content")

        val response = object : BaseResponse<String> {
            override val code = 1001
            override val msg = "token expired"
            override val data = null
        }

        val result = uploadExecutor.uploadFile(
            file = testFile,
            partName = "file",
            call = { _ -> response }
        )

        assertTrue(result is NetworkResult.BusinessFailure)
        assertEquals(1001, (result as NetworkResult.BusinessFailure).code)
        assertEquals("token expired", result.msg)
    }

    @Test
    fun `uploadFile returns TechnicalFailure on exception`() = runTest {
        val testFile = File(tempDir, "upload_error.txt")
        testFile.writeText("content")

        val result: NetworkResult<String> = uploadExecutor.uploadFile(
            file = testFile,
            partName = "file",
            call = { _ -> throw java.io.IOException("network error") }
        )

        assertTrue(result is NetworkResult.TechnicalFailure)
    }

    @Test
    fun `uploadParts returns Success on successful upload`() = runTest {
        val part = MultipartBody.Part.createFormData("field", "value")

        val response = object : BaseResponse<String> {
            override val code = 0
            override val msg = "ok"
            override val data = "uploaded"
        }

        val result = uploadExecutor.uploadParts(
            parts = listOf(part),
            call = { _, _ -> response }
        )

        assertTrue(result is NetworkResult.Success)
        assertEquals("uploaded", (result as NetworkResult.Success).data)
    }

    @Test
    fun `uploadParts with custom successCode`() = runTest {
        val part = MultipartBody.Part.createFormData("field", "value")

        val response = object : BaseResponse<String> {
            override val code = 200
            override val msg = "ok"
            override val data = "result"
        }

        val result = uploadExecutor.uploadParts(
            parts = listOf(part),
            successCode = 200,
            call = { _, _ -> response }
        )

        assertTrue(result is NetworkResult.Success)
    }
}
