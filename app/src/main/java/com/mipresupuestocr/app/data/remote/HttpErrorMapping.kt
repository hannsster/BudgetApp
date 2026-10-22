package com.mipresupuestocr.app.data.remote

import com.mipresupuestocr.domain.model.AppError
import kotlinx.serialization.SerializationException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Maps transport failures onto domain errors.
 *
 * Never carries exception messages forward: provider responses and request URLs can contain
 * credentials, and those must not reach logs or the UI.
 */
internal fun Throwable.toAppError(): AppError = when (this) {
    is SocketTimeoutException -> AppError.Timeout
    is UnknownHostException, is ConnectException, is SSLException -> AppError.Network
    is SerializationException -> AppError.MalformedResponse
    is IOException -> AppError.Network
    else -> AppError.Unknown
}

internal fun httpStatusError(code: Int): AppError = when (code) {
    401, 403 -> AppError.InvalidApiKey
    404 -> AppError.UnknownSymbol
    429 -> AppError.RateLimited
    in 500..599 -> AppError.Network
    else -> AppError.Unknown
}
