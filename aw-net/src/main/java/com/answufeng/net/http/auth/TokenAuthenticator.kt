package com.answufeng.net.http.auth

import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

class TokenAuthenticator(
    private val coordinator: TokenRefreshCoordinator,
    private val headerName: String = "Authorization",
    private val tokenPrefix: String = "Bearer ",
    private val unauthorizedHandler: UnauthorizedHandler? = null
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        val prior = response.priorResponse
        if (prior != null && prior.code == 401) return null

        val requestToken = response.request.header(headerName)?.removePrefix(tokenPrefix)
        val newHeader = coordinator.refreshIfNeededBlocking(requestToken) ?: run {
            notifyUnauthorized()
            return null
        }

        return response.request.newBuilder()
            .header(headerName, newHeader)
            .build()
    }

    private fun notifyUnauthorized() {
        try {
            unauthorizedHandler?.onUnauthorized()
        } catch (_: Exception) {
        }
    }
}
