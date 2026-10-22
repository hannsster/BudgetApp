package com.mipresupuestocr.app.data.local

import android.content.Context
import com.mipresupuestocr.app.R
import com.mipresupuestocr.domain.model.Category
import com.mipresupuestocr.domain.model.CategoryBucket
import com.mipresupuestocr.domain.repository.CategoryRepository
import com.mipresupuestocr.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Inserts localized starter categories on first launch.
 *
 * Targets are deliberately left empty: the app never invents a budget for the user.
 */
@Singleton
class DefaultCategorySeeder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val categoryRepository: CategoryRepository,
    private val settingsRepository: SettingsRepository,
) {
    suspend fun seedIfNeeded() {
        val settings = settingsRepository.current()
        if (settings.setupCompleted) return

        if (categoryRepository.getAll().isEmpty()) {
            categoryRepository.replaceAll(defaultCategories())
        }
        settingsRepository.update { it.copy(setupCompleted = true) }
    }

    private fun defaultCategories(): List<Category> = listOf(
        Category(name = context.getString(R.string.category_housing), bucket = CategoryBucket.NEEDS, isDefault = true, sortOrder = 0),
        Category(name = context.getString(R.string.category_groceries), bucket = CategoryBucket.NEEDS, isDefault = true, sortOrder = 1),
        Category(name = context.getString(R.string.category_transport), bucket = CategoryBucket.NEEDS, isDefault = true, sortOrder = 2),
        Category(name = context.getString(R.string.category_utilities), bucket = CategoryBucket.NEEDS, isDefault = true, sortOrder = 3),
        Category(name = context.getString(R.string.category_health), bucket = CategoryBucket.NEEDS, isDefault = true, sortOrder = 4),
        Category(name = context.getString(R.string.category_dining), bucket = CategoryBucket.WANTS, isDefault = true, sortOrder = 5),
        Category(name = context.getString(R.string.category_entertainment), bucket = CategoryBucket.WANTS, isDefault = true, sortOrder = 6),
        Category(name = context.getString(R.string.category_shopping), bucket = CategoryBucket.WANTS, isDefault = true, sortOrder = 7),
        Category(name = context.getString(R.string.category_savings), bucket = CategoryBucket.SAVINGS, isDefault = true, sortOrder = 8),
        Category(name = context.getString(R.string.category_investing), bucket = CategoryBucket.SAVINGS, isDefault = true, sortOrder = 9),
        Category(name = context.getString(R.string.category_debt), bucket = CategoryBucket.SAVINGS, isDefault = true, sortOrder = 10),
        Category(name = context.getString(R.string.category_salary), bucket = CategoryBucket.INCOME, isDefault = true, sortOrder = 11),
    )
}
