package com.mipresupuestocr.domain.model

/**
 * Errors that can surface from the domain or the data layer.
 *
 * Deliberately free of user-facing text: the UI maps each case to a localized string so that
 * nothing leaks provider responses (which may contain URLs or keys) into the interface.
 */
sealed interface AppError {
    data object MissingApiKey : AppError
    data object Network : AppError
    data object Timeout : AppError
    data object RateLimited : AppError
    data object QuotaExceeded : AppError
    data object InvalidApiKey : AppError
    data object EmptyResponse : AppError
    data object MalformedResponse : AppError
    data object UnsupportedCurrency : AppError
    data object InvalidRate : AppError
    data object InvalidAmount : AppError
    data object InvalidShares : AppError
    data object InvalidAccountName : AppError
    data object InvalidIncome : AppError
    data object InvalidSplit : AppError
    data class InvalidTicker(val ticker: String) : AppError
    data object UnknownSymbol : AppError
    data object UnsupportedAsset : AppError
    data object MissingPaydayAnchor : AppError
    data object SameAccountTransfer : AppError
    data object NoData : AppError
    data object Unknown : AppError
}

/** Result wrapper used across domain and data boundaries. */
sealed interface AppResult<out T> {
    data class Success<out T>(val value: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>

    fun getOrNull(): T? = (this as? Success)?.value

    fun errorOrNull(): AppError? = (this as? Failure)?.error

    fun <R> map(transform: (T) -> R): AppResult<R> = when (this) {
        is Success -> Success(transform(value))
        is Failure -> this
    }

    fun <R> flatMap(transform: (T) -> AppResult<R>): AppResult<R> = when (this) {
        is Success -> transform(value)
        is Failure -> this
    }

    companion object {
        fun <T> success(value: T): AppResult<T> = Success(value)
        fun <T> failure(error: AppError): AppResult<T> = Failure(error)
    }
}
