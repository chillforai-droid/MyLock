package com.example.mylock

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.example.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class MyLockComposeCujRobolectricTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun `dashboard displays core status cards and navigates across modules`() {
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("dashboard_enroll_button").assertIsDisplayed()
        composeRule.onNodeWithTag("dashboard_test_voice_button").assertIsDisplayed()

        composeRule.onNodeWithTag("dashboard_list")
            .performScrollToNode(hasTestTag("dashboard_compatibility_banner"))
        composeRule.onNodeWithTag("dashboard_compatibility_banner").assertIsDisplayed()

        composeRule.onNodeWithTag("dashboard_list")
            .performScrollToNode(hasTestTag("card_voice_auth_status"))
        composeRule.onNodeWithTag("card_voice_auth_status").assertIsDisplayed()

        composeRule.onNodeWithTag("dashboard_list")
            .performScrollToNode(hasTestTag("card_device_compatibility"))
        composeRule.onNodeWithTag("card_device_compatibility").assertIsDisplayed()

        // Navigate to Enrollment and verify offline privacy disclosure
        composeRule.onNodeWithTag("nav_enroll").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("enrollment_accept_privacy_button").assertIsDisplayed()

        // Navigate to Settings and verify bilingual toggle
        composeRule.onNodeWithTag("nav_settings").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("settings_list")
            .performScrollToNode(hasTestTag("setting_lang_hi"))
        composeRule.onNodeWithTag("setting_lang_hi").assertIsDisplayed()
        composeRule.onNodeWithTag("setting_lang_en").assertIsDisplayed()
    }
}
