package com.answufeng.net.http.util

import retrofit2.Retrofit

/**
 * Retrofit 客户端工厂。
 *
 * 用途：当项目需要为不同业务域创建独立的 Retrofit 实例（例如支付/鉴权/文件 CDN），
 * 可通过实现该接口返回自定义的 Retrofit。
 */
interface NetworkClientFactory {

    /**
     * 使用传入的 baseUrl 创建并返回一个 Retrofit 实例。
 */
    fun createRetrofit(baseUrl: String): Retrofit
}
