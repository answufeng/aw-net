package com.answufeng.net.http.auth

/**
 * 未授权处理器接口。
 *
 * 当基础库检测到 Token 刷新失败等未授权场景时，会回调此接口。
 * 项目层可通过 Dagger 可选绑定提供实现，用于执行 UI 跳转（如打开登录页）和会话清理。
 */
interface UnauthorizedHandler {
    /** 当未授权时调用（如 Token 刷新失败） 
 */
    fun onUnauthorized()
}

