package com.answufeng.net.http.model

object NetCode {

    object Business {
        const val SUCCESS = 0
        const val UNAUTHORIZED = 401
        const val FORBIDDEN = 403
        const val NOT_FOUND = 404
    }

    object Technical {
        const val TIMEOUT = -1
        const val NO_NETWORK = -2
        const val SSL_ERROR = -3
        const val REQUEST_CANCELED = -999
        const val UNKNOWN = -1000
        const val PARSE_ERROR = -1001
    }
}
