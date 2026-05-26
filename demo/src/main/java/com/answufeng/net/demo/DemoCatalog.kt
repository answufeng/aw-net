package com.answufeng.net.demo

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity

sealed class DemoListItem {
    data class Section(val title: String) : DemoListItem()

    data class Entry(
        val title: String,
        val subtitle: String,
        val iconRes: Int,
        val activityClass: Class<out AppCompatActivity>,
    ) : DemoListItem()
}

object DemoCatalog {
    fun items(): List<DemoListItem> =
        listOf(
            DemoListItem.Section("入门"),
            DemoListItem.Entry(
                title = "核心请求",
                subtitle = "execute · @RawResponse · 列表展示",
                iconRes = R.drawable.ic_demo_http,
                activityClass = BasicRequestActivity::class.java,
            ),
            DemoListItem.Entry(
                title = "MVVM 架构",
                subtitle = "Repository + ViewModel + StateFlow",
                iconRes = R.drawable.ic_demo_http,
                activityClass = MvvmDemoActivity::class.java,
            ),
            DemoListItem.Entry(
                title = "MVI 架构",
                subtitle = "Intent → State · dispatch 单向数据流",
                iconRes = R.drawable.ic_demo_http,
                activityClass = MviDemoActivity::class.java,
            ),
            DemoListItem.Section("响应与配置"),
            DemoListItem.Entry(
                title = "业务响应格式",
                subtitle = "字段映射 · Retrofit 注解参考",
                iconRes = R.drawable.ic_demo_http,
                activityClass = FieldMappingDemoActivity::class.java,
            ),
            DemoListItem.Entry(
                title = "运行时配置",
                subtitle = "NetworkConfigProvider 动态修改",
                iconRes = R.drawable.ic_demo_http,
                activityClass = AdvancedConfigActivity::class.java,
            ),
            DemoListItem.Section("传输与连接"),
            DemoListItem.Entry(
                title = "文件下载",
                subtitle = "进度 · Hash 校验 · 断点续传",
                iconRes = R.drawable.ic_demo_http,
                activityClass = DownloadActivity::class.java,
            ),
            DemoListItem.Entry(
                title = "文件上传",
                subtitle = "Multipart · 上传进度",
                iconRes = R.drawable.ic_demo_http,
                activityClass = UploadActivity::class.java,
            ),
            DemoListItem.Entry(
                title = "WebSocket",
                subtitle = "连接 · 心跳 · 自动重连",
                iconRes = R.drawable.ic_demo_http,
                activityClass = WebSocketActivity::class.java,
            ),
            DemoListItem.Section("进阶"),
            DemoListItem.Entry(
                title = "错误与重试",
                subtitle = "NetworkResult · 协程重试",
                iconRes = R.drawable.ic_demo_http,
                activityClass = ErrorHandlingActivity::class.java,
            ),
            DemoListItem.Entry(
                title = "网络状态",
                subtitle = "在线检测 · 连接类型",
                iconRes = R.drawable.ic_demo_http,
                activityClass = NetworkMonitorActivity::class.java,
            ),
            DemoListItem.Entry(
                title = "请求工具",
                subtitle = "去重 · 节流 · 轮询",
                iconRes = R.drawable.ic_demo_http,
                activityClass = AdvancedActivity::class.java,
            ),
            DemoListItem.Entry(
                title = "Token 鉴权",
                subtitle = "自动 Header · 401 刷新",
                iconRes = R.drawable.ic_demo_http,
                activityClass = AuthActivity::class.java,
            ),
        )

    fun intentFor(activity: AppCompatActivity, entry: DemoListItem.Entry): Intent =
        Intent(activity, entry.activityClass)
}
