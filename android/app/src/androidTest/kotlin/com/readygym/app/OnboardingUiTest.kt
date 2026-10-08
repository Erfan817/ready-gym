package com.readygym.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

class OnboardingUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun completeProfileThenReturnAndEdit() {
        compose.onNodeWithText("年龄（岁）").performTextInput("30")
        compose.onNodeWithText("身高（cm）").performTextInput("170")
        compose.onNodeWithText("体重（kg）").performTextInput("65")
        compose.onNodeWithText("下一步").performScrollTo().performClick()
        compose.onNodeWithText("增肌").performClick()
        compose.onNodeWithText("下一步").performScrollTo().performClick()
        compose.onNodeWithText("家").performClick()
        compose.onNodeWithText("徒手").performScrollTo().performClick()
        compose.onNodeWithText("哑铃").performScrollTo().performClick()
        compose.onNodeWithText("下一步").performScrollTo().performClick()
        compose.onNodeWithText("年龄：30 岁").assertExists()
        compose.onNodeWithText("器械：徒手、哑铃").assertExists()
        compose.onNodeWithText("返回修改").performScrollTo().performClick()
        compose.onNodeWithText("训练条件").assertExists()
    }
}
