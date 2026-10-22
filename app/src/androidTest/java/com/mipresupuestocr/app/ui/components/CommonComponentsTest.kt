package com.mipresupuestocr.app.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mipresupuestocr.app.ui.theme.MiPresupuestoTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CommonComponentsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun emptyStateShowsCopyAndInvokesAction() {
        var clicked = false
        composeRule.setContent {
            MiPresupuestoTheme(dynamicColor = false) {
                EmptyState(
                    title = "No entries yet",
                    body = "Add your first transaction.",
                    actionLabel = "Add",
                    onAction = { clicked = true },
                )
            }
        }

        composeRule.onNodeWithText("No entries yet").assertIsDisplayed()
        composeRule.onNodeWithText("Add").performClick()
        assertTrue(clicked)
    }

    @Test
    fun categoryTargetRendersLabelAndAmounts() {
        composeRule.setContent {
            MiPresupuestoTheme(dynamicColor = false) {
                TargetProgress(
                    label = "Groceries",
                    supporting = "₡150 000,00 de ₡200 000,00",
                    fraction = 0.75,
                )
            }
        }

        composeRule.onNodeWithText("Groceries").assertIsDisplayed()
        composeRule.onNodeWithText("₡150 000,00 de ₡200 000,00").assertIsDisplayed()
    }
}
