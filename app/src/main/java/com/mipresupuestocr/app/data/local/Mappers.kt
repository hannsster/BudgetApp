package com.mipresupuestocr.app.data.local

import com.mipresupuestocr.domain.model.Category
import com.mipresupuestocr.domain.model.CategoryBucket
import com.mipresupuestocr.domain.model.CurrencyCode
import com.mipresupuestocr.domain.model.ExchangeRate
import com.mipresupuestocr.domain.model.Holding
import com.mipresupuestocr.domain.model.Money
import com.mipresupuestocr.domain.model.Quote
import com.mipresupuestocr.domain.model.Transaction
import com.mipresupuestocr.domain.model.TransactionType
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

fun CategoryEntity.toDomain(): Category = Category(
    id = id,
    name = name,
    bucket = runCatching { CategoryBucket.valueOf(bucket) }.getOrDefault(CategoryBucket.OTHER),
    budgetTarget = targetMinor?.let { Money(it, CurrencyCode.CRC) },
    isDefault = isDefault,
    sortOrder = sortOrder,
)

fun Category.toEntity(): CategoryEntity = CategoryEntity(
    id = id,
    name = name,
    bucket = bucket.name,
    targetMinor = budgetTarget?.amountMinor,
    isDefault = isDefault,
    sortOrder = sortOrder,
)

fun TransactionEntity.toDomain(): Transaction = Transaction(
    id = id,
    type = runCatching { TransactionType.valueOf(type) }.getOrDefault(TransactionType.EXPENSE),
    amount = Money(amountMinor, CurrencyCode.fromIsoCode(currency) ?: CurrencyCode.CRC),
    categoryId = categoryId,
    date = LocalDate.ofEpochDay(dateEpochDay),
    note = note,
    createdAt = Instant.ofEpochMilli(createdAtMillis),
)

fun Transaction.toEntity(): TransactionEntity = TransactionEntity(
    id = id,
    type = type.name,
    amountMinor = amount.amountMinor,
    currency = amount.currency.isoCode,
    categoryId = categoryId,
    dateEpochDay = date.toEpochDay(),
    note = note,
    createdAtMillis = (createdAt ?: Instant.EPOCH).toEpochMilli(),
)

fun HoldingEntity.toDomain(): Holding = Holding(
    id = id,
    ticker = ticker,
    shares = runCatching { BigDecimal(shares) }.getOrDefault(BigDecimal.ZERO),
    averagePriceUsd = averagePriceUsd?.let { runCatching { BigDecimal(it) }.getOrNull() },
    notes = notes,
    createdAt = Instant.ofEpochMilli(createdAtMillis),
)

fun Holding.toEntity(): HoldingEntity = HoldingEntity(
    id = id,
    ticker = ticker,
    shares = shares.stripTrailingZeros().toPlainString(),
    averagePriceUsd = averagePriceUsd?.stripTrailingZeros()?.toPlainString(),
    notes = notes,
    createdAtMillis = (createdAt ?: Instant.EPOCH).toEpochMilli(),
)

fun RateCacheEntity.toDomain(): ExchangeRate = ExchangeRate(
    base = CurrencyCode.fromIsoCode(base) ?: CurrencyCode.USD,
    quote = CurrencyCode.fromIsoCode(quote) ?: CurrencyCode.CRC,
    rate = runCatching { BigDecimal(rate) }.getOrDefault(BigDecimal.ZERO),
    asOf = Instant.ofEpochMilli(asOfMillis),
    source = source,
    isManual = isManual,
)

fun ExchangeRate.toEntity(cacheKey: String): RateCacheEntity = RateCacheEntity(
    cacheKey = cacheKey,
    base = base.isoCode,
    quote = quote.isoCode,
    rate = rate.stripTrailingZeros().toPlainString(),
    asOfMillis = asOf.toEpochMilli(),
    source = source,
    isManual = isManual,
)

fun QuoteCacheEntity.toDomain(): Quote = Quote(
    ticker = ticker,
    priceUsd = runCatching { BigDecimal(priceUsd) }.getOrDefault(BigDecimal.ZERO),
    asOf = Instant.ofEpochMilli(asOfMillis),
    source = source,
)

fun Quote.toEntity(): QuoteCacheEntity = QuoteCacheEntity(
    ticker = ticker.uppercase(),
    priceUsd = priceUsd.stripTrailingZeros().toPlainString(),
    asOfMillis = asOf.toEpochMilli(),
    source = source,
)
