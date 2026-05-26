package com.answufeng.net.demo



import android.widget.LinearLayout

import com.answufeng.net.http.config.NetworkConfigProvider

import com.answufeng.net.http.model.ResponseFieldMapping
import com.answufeng.net.http.model.booleanStatusMessageData
import com.answufeng.net.http.model.codeMsgData
import com.answufeng.net.http.model.statusMessageData

import com.google.android.material.snackbar.Snackbar

import dagger.hilt.android.AndroidEntryPoint

import javax.inject.Inject



@AndroidEntryPoint

class FieldMappingDemoActivity : BaseDemoActivity() {

    @Inject lateinit var configProvider: NetworkConfigProvider



    override fun getTitleText() = "业务响应格式"



    override fun setupContent(layout: LinearLayout) {

        addLead("自有后端为 {code,msg,data} 时，接口直接声明 User?。字段名不同或 status 为 boolean 时，在此切换全局映射。")



        addSectionTitle("运行时切换映射")

        addButtonRow(

            { text = "code/msg/data"; setOnClickListener { applyMapping(ResponseFieldMapping.codeMsgData(), 0, "默认") } },

            { text = "status/msg"; setOnClickListener { applyMapping(ResponseFieldMapping.statusMessageData(200), 200, "status/message") } },

        )

        addOutlinedButton("boolean status") {

            applyMapping(ResponseFieldMapping.booleanStatusMessageData(), 0, "boolean status")

        }



        addDivider()



        addSectionTitle("Retrofit 注解参考")

        addInfoCard(

            title = "@ResponseFields",

            description = "单接口覆盖 code / msg / data 字段名与成功码。",

            hint = "@ResponseFields(...) @GET(\"x\") suspend fun load(): User?",

        )

        addInfoCard(

            title = "@SuccessCode",

            description = "单接口业务成功码，配合 suspend fun (): T?。",

            hint = "@SuccessCode(200) @GET(\"x\") suspend fun load(): Data?",

        )

        addInfoCard(

            title = "@BaseUrl",

            description = "切换请求域名，用于 CDN、第三方 API。",

            hint = "@BaseUrl(\"https://cdn.example.com/\") @GET(\"file\")",

        )

        addInfoCard(

            title = "@Timeout / @Retry",

            description = "按接口覆盖超时；OkHttp 层按接口重试（勿与协程重试叠开）。",

            hint = "@Timeout(read=60)  @Retry(maxAttempts=3)",

        )

        addInfoCard(

            title = "@RawResponse",

            description = "裸 JSON，无业务包装。第三方接口常配合 @BaseUrl。",

            hint = "见「核心请求」页的 httpbin 示例",

        )

    }



    private fun applyMapping(

        mapping: ResponseFieldMapping,

        successCode: Int,

        label: String,

    ) {

        configProvider.update {

            it.copy(

                responseFieldMapping = mapping,

                defaultSuccessCode = successCode,

            )

        }

        Snackbar.make(contentLayout, "已切换：$label", Snackbar.LENGTH_SHORT).show()

    }

}


