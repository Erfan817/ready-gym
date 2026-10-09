package com.readygym.app

import android.graphics.Bitmap
import java.io.File
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class OnboardingLocalUiTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Before fun enterOfflineRules() {
        compose.onNodeWithText("离线规则模式（不使用AI）").performScrollTo().performClick()
    }
    private fun conditions() {
        compose.onNodeWithContentDescription("年龄（岁）").performScrollTo().performTextInput("30")
        compose.onNodeWithContentDescription("身高（cm）").performScrollTo().performTextInput("170")
        compose.onNodeWithContentDescription("体重（kg）").performScrollTo().performTextInput("65")
        compose.onNodeWithContentDescription("体重（kg）").performImeAction()
        compose.onNodeWithText("下一步").performClick()
        compose.onNodeWithText("增肌").performScrollTo().performClick()
        capture("02-goal")
        compose.onNodeWithText("下一步").performClick()
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        val file = File("../build/verification/screenshots/$name.png")
        file.parentFile?.mkdirs()
        compose.runOnIdle {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
    @Test fun profileSummarySupportsConfirmationReturnEditingAndClear() {
        capture("01-profile")
        conditions()
        capture("03-conditions")
        compose.onNodeWithText("家").performScrollTo().performClick()
        compose.onNodeWithText("徒手").performScrollTo().performClick()
        compose.onNodeWithText("哑铃").performScrollTo().performClick()
        capture("03-equipment-top")
        compose.onNodeWithText("固定器械").performScrollTo().assertIsDisplayed()
        capture("03-equipment-bottom")
        compose.onNodeWithText("下一步").performClick()
        compose.onNodeWithContentDescription("年龄：30 岁").assertExists()
        compose.onNodeWithContentDescription("器械：徒手、哑铃").assertExists()
        capture("04-confirm")
        compose.onNodeWithText("确认本次资料").performClick()
        compose.onNodeWithText("已确认").assertIsNotEnabled()
        compose.onNodeWithText("返回修改").performClick()
        compose.onNodeWithText("徒手").assertIsOn()
        compose.onNodeWithText("下一步").performClick()
        compose.onNodeWithText("确认本次资料").assertIsEnabled()
        compose.onNodeWithText("清空本次资料").performScrollTo().performClick()
        compose.onNodeWithText("先认识一下你").assertExists()
        compose.onNodeWithContentDescription("年龄（岁）").assertTextEquals("18–120", "岁", "")
    }
    @Test fun eachStepStartsAtItsHeadingAfterScrollingPreviousPage() {
        conditions()
        compose.onNodeWithText("你的训练主场").assertIsDisplayed()
        compose.onNodeWithText("家").performScrollTo().performClick()
        compose.onNodeWithText("固定器械").performScrollTo().performClick()
        compose.onNodeWithText("下一步").performClick()
        compose.onNodeWithText("检查一下，准备就绪").assertIsDisplayed()
    }
    @Test fun emptyProfileShowsInputRangesAndUnitSuffixes() {
        compose.onNodeWithText("18–120").assertExists()
        compose.onNodeWithText("100–250").assertExists()
        compose.onNodeWithText("30–300").assertExists()
        compose.onNodeWithText("cm").assertExists()
        compose.onNodeWithText("kg").assertExists()
    }
    @Test fun usageNotesAreCollapsedUntilRequested() {
        compose.onNodeWithText("先认识一下你").assertExists()
        compose.onNodeWithText("本版提供未专业审核的规则草稿，未接AI；不提供医疗建议，不根据BMI推断能力。", substring = true).assertDoesNotExist()
        compose.onNodeWithText("使用说明").performScrollTo().performClick()
        compose.onNodeWithText("本版提供未专业审核的规则草稿，未接AI；不提供医疗建议，不根据BMI推断能力。", substring = true).assertExists()
        compose.onNodeWithText("使用说明").performClick()
        compose.onNodeWithText("本版提供未专业审核的规则草稿，未接AI；不提供医疗建议，不根据BMI推断能力。", substring = true).assertDoesNotExist()
    }
    @Test fun equipmentIllustrationCardsExposeIndependentCheckboxes() {
        conditions()
        val body = compose.onNode(hasText("徒手") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
        val dumbbell = compose.onNode(hasText("哑铃") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Checkbox))
        body.performScrollTo().performClick().assertIsOn()
        dumbbell.performScrollTo().performClick().assertIsOn()
        body.assertIsOn().performScrollTo().performClick().assertIsOff()
        dumbbell.assertIsOn()
        compose.onNodeWithContentDescription("哑铃器械插画", useUnmergedTree = true).assertExists()
    }
    @Test fun locationCardsExposeExclusiveRadioSelection() {
        conditions()
        val home = compose.onNode(hasText("家") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        val gym = compose.onNode(hasText("健身房") and SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        home.performScrollTo().performClick().assertIsSelected()
        gym.performScrollTo().performClick().assertIsSelected()
        home.assertIsNotSelected()
    }
}
