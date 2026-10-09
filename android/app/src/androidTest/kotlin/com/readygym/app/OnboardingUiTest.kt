package com.readygym.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

// Compiled for devices; host-side executed equivalent lives in src/test.
class OnboardingUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun completeProfileThenReturnAndEdit() {
        compose.onNodeWithText("离线规则模式（不使用AI）").performScrollTo().performClick()
        compose.onNodeWithContentDescription("年龄（岁）").performScrollTo().performTextInput("30")
        compose.onNodeWithContentDescription("身高（cm）").performScrollTo().performTextInput("170")
        compose.onNodeWithContentDescription("体重（kg）").performScrollTo().performTextInput("65")
        compose.onNodeWithText("下一步").performClick()
        compose.onNodeWithText("增肌").performScrollTo().performClick()
        compose.onNodeWithText("下一步").performClick()
        compose.onNodeWithText("家").performScrollTo().performClick()
        compose.onNodeWithText("健身房").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithText("家").assertIsNotSelected()
        compose.onNodeWithText("徒手").performScrollTo().performClick().assertIsOn()
        compose.onNodeWithText("哑铃").performScrollTo().performClick().assertIsOn()
        compose.onNodeWithText("徒手").assertIsOn()
        compose.onNodeWithText("下一步").performClick()
        compose.onNodeWithContentDescription("年龄：30 岁").assertExists()
        compose.onNodeWithContentDescription("器械：徒手、哑铃").assertExists()
        compose.onNodeWithText("返回修改").performClick()
        compose.onNodeWithText("训练条件").assertExists()
        compose.onNodeWithText("清空本次资料").performScrollTo().performClick()
        compose.onNodeWithText("先认识一下你").assertExists()
    }
}
