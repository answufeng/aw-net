package com.answufeng.net.http.auth

import com.answufeng.net.http.annotations.INetLogger
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

class TokenAuthenticator(
    private val tokenProvider: TokenProvider,
    private val headerName: String = "Authorization",
    private val tokenPrefix: String = "Bearer ",
    private val unauthorizedHandler: UnauthorizedHandler? = null,
    private val logger: INetLogger? = null,
    private val coordinator: TokenRefreshCoordinator? = null
) : Authenticator {

    private val lock = java.util.concurrent.locks.ReentrantLock()
    private val fallbackCoordinator by lazy {
        TokenRefreshCoordinator(tokenProvider, headerName, tokenPrefix, unauthorizedHandler, logger)
    }

    private val effectiveCoordinator: TokenRefreshCoordinator
        get() = coordinator ?: fallbackCoordinator

    override fun authenticate(route: Route?, response: Response): Request? {
        val prior = response.priorResponse
        if (prior != null && prior.code == 401) return null

        val requestToken = response.request.header(headerName)?.removePrefix(tokenPrefix)
        val newHeader = effectiveCoordinator.refreshIfNeededBlocking(requestToken) ?: return null

        return response.request.newBuilder()
            .header(headerName, newHeader)
            .build()
    }
}
