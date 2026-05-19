package com.answufeng.net.demo

import android.util.Log
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import com.answufeng.net.http.model.GlobalResponse
import com.answufeng.net.http.model.NetworkResult
import com.answufeng.net.http.model.fold
import com.answufeng.net.http.model.onBusinessFailure
import com.answufeng.net.http.model.onSuccess
import com.answufeng.net.http.model.onTechnicalFailure
import com.answufeng.net.http.util.NetworkExecutor
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.util.logging.Logger
import javax.inject.Inject

@AndroidEntryPoint
class BasicRequestActivity : BaseDemoActivity() {
    @Inject
    lateinit var executor: NetworkExecutor

    @Inject
    lateinit var api: JsonPlaceholderApi

    @Inject
    lateinit var ocrApi: OcrApi

    private lateinit var tvResult: TextView

    override fun getTitleText() = "基础请求"

    override fun setupContent(layout: LinearLayout) {

        addPrimaryButton("测试") { test() }


        addSectionTitle("GET 请求")
        addBodyText("从 JSONPlaceholder 获取帖子列表，使用 executeRawRequest 直接返回原始数据。")

        addPrimaryButton("发送 GET 请求") { performGetRequest() }

        addSectionTitle("POST 请求")
        addBodyText("向 JSONPlaceholder 创建新帖子，观察请求体和响应。")

        addPrimaryButton("发送 POST 请求") { performPostRequest() }

        addSectionTitle("自定义成功码")
        addBodyText("使用 executeRequest 的 successCode 参数或 @SuccessCode 注解。需要 BaseResponse 返回类型的 API。")
        addCodeBlock(
            """
            executor.executeRequest(
                successCode = 200
            ) { api.getUser() }

            // 或使用 @SuccessCode 注解
            @SuccessCode(200)
            @GET("legacy-api")
            suspend fun legacyApi(): GlobalResponse<Data>
            """.trimIndent(),
        )

        addDivider()

        addSectionTitle("请求结果")
        tvResult = addLogBlock("点击上方按钮发送请求...")
    }

    val a = "%2F9j%2F4AAQSkZJRgABAQAAAQABAAD%2F4gHYSUNDX1BST0ZJTEUAAQEAAAHIAAAAAAQwAABtbnRyUkdCIFhZWiAH4AABAAEAAAAAAABhY3NwAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAQAA9tYAAQAAAADTLQAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAlkZXNjAAAA8AAAACRyWFlaAAABFAAAABRnWFlaAAABKAAAABRiWFlaAAABPAAAABR3dHB0AAABUAAAABRyVFJDAAABZAAAAChnVFJDAAABZAAAAChiVFJDAAABZAAAAChjcHJ0AAABjAAAADxtbHVjAAAAAAAAAAEAAAAMZW5VUwAAAAgAAAAcAHMAUgBHAEJYWVogAAAAAAAAb6IAADj1AAADkFhZWiAAAAAAAABimQAAt4UAABjaWFlaIAAAAAAAACSgAAAPhAAAts9YWVogAAAAAAAA9tYAAQAAAADTLXBhcmEAAAAAAAQAAAACZmYAAPKnAAANWQAAE9AAAApbAAAAAAAAAABtbHVjAAAAAAAAAAEAAAAMZW5VUwAAACAAAAAcAEcAbwBvAGcAbABlACAASQBuAGMALgAgADIAMAAxADb%2F2wBDAAYEBQYFBAYGBQYHBwYIChAKCgkJChQODwwQFxQYGBcUFhYaHSUfGhsjHBYWICwgIyYnKSopGR8tMC0oMCUoKSj%2F2wBDAQcHBwoIChMKChMoGhYaKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCj%2FwAARCAHCAyADASIAAhEBAxEB%2F8QAFgABAQEAAAAAAAAAAAAAAAAAAAEI%2F8QAGhABAQEBAQEBAAAAAAAAAAAAAAFBETEhUf%2FEABQBAQAAAAAAAAAAAAAAAAAAAAD%2FxAAUEQEAAAAAAAAAAAAAAAAAAAAA%2F9oADAMBAAIRAxEAPwDLmAAAAAAAAAoIqKAgAKIAAAKAigAAAAAAAAAAAigAICgAAYAigAAFAAAAAAAAAAOBgAAAAAAAAAAAABYAAAAAAAAAIAAAAACoAAQAFAQAAAAAAAAAAVAFQAVOqAigAgCgAAUAAAAAAAggKBQAAAQFAAAAAACAAQAAAA0AAAAAAAAAAAADQAVAAoQAAANAChwAAAAA4AAAAABQAAABFBFEAFAEUARUAVAAVAAAVAAFAEAFEUEABQQFBAUAAAAwAAAKAAAAAAAAABpQgAAAAAAAABAAqoAAAAAAAAYAACoqAqXwAAABUAFQAAAAAAAAAoAAAIoAAAQAA0ARQBFAEABUAAUEVAAFBAAAAFQAVFAQUEVFBFAAAAAAAAIUAEBQAAAAAAAAAAICiAHgAAAAACpighFQFBACKgKgACgIAAogCgCAAAAqKgAoCAAAAAAAAAAAAQRQAAKAAi4QEUQAAFBAAABQEABUAAFgAgCgAIKAAAAAGgAAHgAAAAGAAAAAAACoCggKgAUAAAAVAFQBRFBAAAAURQAAEooCKgKIAqLABIABoAAegKgBoqUAAAADgAAAAAAAAAAigBEAABUAAAAAAAAVAAAVFiAqAAEUDAAAAAAAAAgAYABAAFARRAAABUAAAFQAKQAVABegIKAAAAAAAAAiiAoAAAAAIogHVEACKAIAAAGgAAAAAAAAAAAAAYQwAAAw4AgqAKICooCCoCoAAAAKAigAAAAAAAAAAAAAAAAAACiAqKgAAKhAAUBBUBRFAAA4AAAAAAigAAAAAAFAAAAAAAAABFAEFAEFBBUAAACgAAAKCAAQMAAARQAABBQEAAVFARUAVAAAFAgAICgAAABAAAAAAgAAABQAAAFBAoAqcUBABUAFAAAAAAAAAAAAAAAADAAAAAAAAAAAAAAAAABFQBUAAAAAAUBFQAABFAAAAAAAAAQAAFBFAEFQFQUAAAAAACgAAAB4AAAAAAAAACoACgIAKmgACgigAABSAAigAAAAAAAUAAAAACAAAAAAAAGgAAAAAQBFQBUoACggAKgAcAAABUAAAAAAAAAEUARUUBABRABUAUACHQAAAA4AAAAAAAAAAAAAogCoAKgAKAigAAAAAAAAAAAB6AAAABAAAAAoACggAF%2BAAAUAAAAAAAEAUARUUEAAgHABU0AAAADAAEUAAAAAABFABFAQAAABRAUCgAAQoAAAAAAAAARUAFQAAAVAFQAFRQDoACKAAAAAAAAAAABgAABQADwAAAAAAAAA0oAAABgAAAigAIAqAFBQQNUEBQQCAAAFCAQAAAAEBQAEUARQEBQQVAAAUAAAAOAAAAEADTQAAAAFEAKABooILEAABRFAAAAAAAAAAABaCGAABoAAAAAAAABQAAAAAoAAmqAAAAACAKiggAKgACKAAAAAAAAAAAABwABFQBUAAAAAUAAAAAAAAAAADgABAAVAAAAFAEIAogKigAAAAAAAAAAAAAABAAAAVKAAAAAAAEAAAAAAAAAAAAAAQAVFAEAAABUABQQAAAAAAAAAEUOgIoAioAAAACooAAAAAAB0AAAAAAVIBFRQEAAAFEUBFQFgAHAAAAAKAAAAABABUA4qAAAAUAVAAAAAADADAAAAAAA6BoACKAgACiAqACoqACoAAAAAAAACKAAAAAAIAKAgqAqCgAAAcAAAAAAAAABQQABUUEVAFEAAAURQBFAAANAAACAAAABAADwAXiAUAAAAAAAAAAAAAAAAAAAAEBaAAAAioAqAAAAAKgAAAAUEUMAAAAAAAAAqKgAKAgAoAAAAQAgAAAAAAAKgAAoAigiooIqKCKAIqKCKAAAAAAAAAAAAAAdKAABQoAHAAADgAAAAAAAAAAAAAAAAACAAKgKgAogAAAAAAAAAAAAAACKigAQBFAAAAAAAMAAAAAAAACABooIAAqAKgoIqAAqAqKACKAAAAAAAAAAAAAAAUAFQAVABUAAAAAAAA8AAAAAAQFAAABFRQQAFRQBNAFEAKAAAAAAEAAADAAAABAUABFQFwAAKABgBQAAAAAAAFQAABUAFEABUAABRAFEUAAAAAAA9AAACAAAAYABQ6ABQAAAAAAAAAAAAAAAAEUAQAFEAAAAAAAAAAAAAAAAAAOAAAJVQAUBFQAVAFAAAA0D6ACAsAAAAAAAA6ABAAAAFQBQQAFBFAARQAwAAAAAAADAAAAAAAAAAAAAAAAAAAAAAAEUAEAAAFQABUAAAAAAAAAAAgAGGAAAAACCoACggqAqACgAAAAAAAAAAAAAAcAAACAGgAAAAoCCggqAoFAAAAAAAAgAAAAAAAAAAAAAAAAAigAAAgKAAAACAAAAAqKgAKCUACmAAAAAAAAAACAoACKUEUABFAQUEUSAoAAAAigAAGAAAABAAAD0AVAAVAAAFQUEBQBABQAAAAAAAAAAAAAAAAAAoAigAAAAGAAAAIAAoAAIACoABQAgqUAAAOAAAAAAAEAADQAAAABKqAqCggKCKAAigCL4AAAAAAAAAAAaAFAABQRRANFQBUAAAFQAVDoAKABQAAAAAAAAAQFwAAEBQAAAEUAAABAFRQAQAUBAUEBQQFAQOgAAdAAAAAAAACgAAAgAogKCAogCoAKAAAAYAAAFAAAAgAAcAAAAAAIAAAAAAAAAHoBqocAAAVAFE4oAigAAAAIoBAAAAAAEU4AAAilBIACggCooIAAKgAUAAAAAABFAAADAAAAEWgAgAqAAqAKgCopAQAF4AAAAAAAAAAAAEAIQAAAAAAAIACoAAAAAEBQQVAVAAABRFBFRQEFBFAEUAAQFAATqoCiAKIoCCgIqAKgAAAqKCKgAqAAAAAAAAYAAAUAAAAABABRAFRUAABRFAQAURQAAAAAAAAAAD6AAAAABgAAAAAAAAAAAUAAAAVAKKgKIoCCgCACoAqACoqAoIBAAFEAVAFEABQEFQAABR ... (truncated)"

    private fun test() {
        tvResult.text = "请求中..."
        lifecycleScope.launch {
            val result: NetworkResult<String> =
                executor.executeRequest(
                    option = com.answufeng.net.http.model.RequestOption(successCode = 200),
                ) {
                    ocrApi.recognizeContainer(a)
                }

            result.onSuccess { json ->
                Log.e("BasicRequestActivity", "onSuccess data: $json")
            }.onTechnicalFailure {
                Log.e("BasicRequestActivity"," onTechnicalFailure result:"+it)
            }.onBusinessFailure { code, msg ->
                Log.e("BasicRequestActivity","onBusinessFailure result:"+msg)

            }

        }
    }
    private fun performGetRequest() {
        tvResult.text = "请求中..."
        lifecycleScope.launch {
            val result: NetworkResult<List<Post>> = executor.executeRawRequest { api.getPosts() }
            tvResult.text = formatResult("GET /posts", result)
        }
    }

    private fun performPostRequest() {
        tvResult.text = "请求中..."
        lifecycleScope.launch {
            val result: NetworkResult<Post> =
                executor.executeRawRequest {
                    api.createPost(PostBody(1, "aw-net 测试标题", "aw-net 测试内容"))
                }
            tvResult.text = formatResult("POST /posts", result)
        }
    }

    private fun <T> formatResult(
        endpoint: String,
        result: NetworkResult<T>,
    ): String {
        val sb = StringBuilder()
        sb.appendLine("── $endpoint ──")
        sb.appendLine()
        result.fold(
            onSuccess = { data ->
                sb.appendLine("SUCCESS")
                sb.appendLine(data.toString().take(500))
            },
            onTechnicalFailure = { ex ->
                sb.appendLine("TECHNICAL_FAILURE")
                sb.appendLine("  code: ${ex.code}")
                sb.appendLine("  msg: ${ex.message}")
            },
            onBusinessFailure = { code, msg ->
                sb.appendLine("BUSINESS_FAILURE")
                sb.appendLine("  code: $code")
                sb.appendLine("  msg: $msg")
            },
        )
        return sb.toString()
    }
}
