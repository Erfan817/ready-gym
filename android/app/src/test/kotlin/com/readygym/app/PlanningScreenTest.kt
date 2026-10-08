package com.readygym.app

import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PlanningScreenTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun startPlanning() {
        compose.onNodeWithContentDescription("年龄（岁）").performScrollTo().performTextInput("30")
        compose.onNodeWithContentDescription("身高（cm）").performScrollTo().performTextInput("170")
        compose.onNodeWithContentDescription("体重（kg）").performScrollTo().performTextInput("65")
        compose.onNodeWithContentDescription("体重（kg）").performImeAction()
        compose.onNodeWithText("下一步").performClick()
        compose.onNodeWithText("增肌").performScrollTo().performClick()
        compose.onNodeWithText("下一步").performClick()
        compose.onNodeWithText("家").performScrollTo().performClick()
        compose.onNodeWithText("哑铃").performScrollTo().performClick()
        compose.onNodeWithText("下一步").performClick()
        compose.onNodeWithText("确认本次资料").performClick()
        compose.onNodeWithText("制定训练计划").performClick()
    }

    @Test fun confirmedOnboardingProfileEntersPlanningPreferences() {
        startPlanning()
        compose.onNodeWithText("安排你的训练周").assertIsDisplayed()
        compose.onNodeWithText("30岁 · 增肌 · 家 · 哑铃").assertExists()
    }

    private fun chooseSafeReport() {
        compose.onNodeWithText("没有当前不适或危险信号").performScrollTo().performClick()
        compose.onNodeWithText("没有医学训练限制").performScrollTo().performClick()
    }

    @Test fun explicitSafetyAndSelectedPreferencesGenerateOnlyARuleDraft() {
        startPlanning()
        compose.onNodeWithText("生成规则草稿").assertIsNotEnabled()
        compose.onNodeWithText("每周2天").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithText("20分钟").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithText("恢复训练").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithText("没有当前不适或危险信号").performScrollTo().performClick()
        compose.onNodeWithText("生成规则草稿").assertIsNotEnabled()
        compose.onNodeWithText("没有医学训练限制").performScrollTo().performClick()
        capture("07-planning-preferences")
        compose.onNodeWithText("生成规则草稿").performClick()
        compose.onNodeWithText("待确认 v1").assertExists()
        compose.onNodeWithText("每周2天 · 每次20分钟 · 恢复训练").assertExists()
        compose.onNodeWithText("RULE TEMPLATE · 未接模型 · 未专业审核").assertExists()
        compose.onNodeWithText("徒手深蹲 · 1组 × 8–10次 · 组间休息60秒").assertExists()
        capture("08-plan-draft")
    }

    private fun generateDraft() {
        startPlanning()
        chooseSafeReport()
        compose.onNodeWithText("生成规则草稿").performClick()
    }

    @Test fun draftBecomesActiveOnlyAfterExplicitVersionConfirmation() {
        generateDraft()
        compose.onNodeWithText("尚无生效计划").assertExists()
        compose.onNodeWithText("确认采用 v1").performClick()
        compose.onNodeWithText("当前生效 v1").assertExists()
        compose.onNodeWithText("待确认 v1").assertDoesNotExist()
        compose.onNodeWithText("本次确认仅在会话内有效，不是保存或安全认证。").assertExists()
    }

    @Test fun regeneratedDraftDoesNotOverwriteConfirmedPlan() {
        generateDraft()
        compose.onNodeWithText("确认采用 v1").performClick()
        compose.onNodeWithText("调整偏好 / 重新生成").performClick()
        compose.onNodeWithText("每周2天").performScrollTo().performClick()
        compose.onNodeWithText("生成规则草稿").performClick()
        compose.onNodeWithText("当前生效 v1").assertExists()
        compose.onNodeWithText("待确认 v2").assertExists()
        compose.onNodeWithText("放弃草稿").performClick()
        compose.onNodeWithText("当前生效 v1").assertExists()
        compose.onNodeWithText("每周3天 · 每次30分钟 · 初学").assertExists()
    }

    @Test fun weekSelectorShowsRestAndCurrentExerciseDetailWithOfflineDemo() {
        generateDraft()
        compose.onNodeWithText("选择训练日").performScrollTo().performClick()
        compose.onNodeWithText("周二 · 休息").performClick()
        compose.onNodeWithText("休息与恢复，不自动加练").assertExists()
        compose.onNodeWithText("选择训练日").performClick()
        compose.onNodeWithText("周三 · 训练").performClick()
        compose.onNodeWithTag("exercise-rg-v1-bodyweight-squat").performScrollTo().performClick()
        compose.onNodeWithText("动作详情：徒手深蹲").assertExists()
        compose.onNodeWithText("播放动作演示").assertExists()
        capture("09-exercise-detail")
        compose.onNodeWithText("关闭详情").performClick()
        compose.onNodeWithText("动作详情：徒手深蹲").assertDoesNotExist()
        compose.onNodeWithText("主肌群：股四头肌", substring = true).assertExists()
    }

    @Test fun reportedDangerBlocksConfirmationAndEquipmentReplacement() {
        generateDraft()
        compose.onNodeWithText("身体不适").performClick()
        compose.onNodeWithText("胸痛、头晕、急性损伤或关节疼痛").performScrollTo().performClick()
        compose.onNodeWithText("没有医学训练限制").performScrollTo().performClick()
        compose.onNodeWithText("评估安全反馈").performClick()
        compose.onNodeWithText("停止训练，不用替换动作继续", substring = true).assertExists()
        compose.onNodeWithText("关闭安全反馈").performClick()
        compose.onNodeWithText("确认采用 v1").assertIsNotEnabled()
        compose.onNodeWithTag("replace-rg-v1-bodyweight-squat").performScrollTo().assertIsNotEnabled()
    }

    @Test fun equipmentReplacementProposesARevisionWithoutMutatingActivePlan() {
        generateDraft()
        compose.onNodeWithText("确认采用 v1").performClick()
        compose.onNodeWithTag("replace-rg-v1-dumbbell-row").performScrollTo().performClick()
        compose.onNodeWithText("当前可用：哑铃").performClick().assertIsOff()
        compose.onNodeWithText("当前可用：杠铃").performClick().assertIsOn()
        compose.onNodeWithText("查找替换候选").performClick()
        compose.onNodeWithText("候选：俯身杠铃划船").performScrollTo().performClick()
        compose.onNodeWithText("双侧俯身哑铃划船 → 俯身杠铃划船").assertExists()
        compose.onNodeWithText("原计划未改变；确认后才采用。").assertExists()
        capture("10-replacement-proposal")
        compose.onNodeWithText("待确认 v2").assertExists()
        compose.onNodeWithText("当前生效 v1").assertExists()
        compose.onNodeWithText("确认采用 v2").performClick()
        compose.onNodeWithText("当前生效 v2").assertExists()
        compose.onNodeWithTag("replace-rg-v1-barbell-row").performScrollTo().assertExists()
        compose.onNodeWithTag("replace-rg-v1-bodyweight-squat").performScrollTo().performClick()
        compose.onNodeWithText("查找替换候选").performClick()
        compose.onNodeWithText("没有合适候选", substring = true).assertExists()
        compose.onNodeWithText("取消替换").performClick()
        compose.onNodeWithText("当前生效 v2").assertExists()
    }

    @Test fun cancellingRepeatedReplacementsRestoresWholeRoundBaselineAndConfirmedPlan() {
        generateDraft()
        proposeBarbellRow()
        compose.onNodeWithText("待确认 v2").assertExists()
        proposeDumbbellRow()
        compose.onNodeWithText("待确认 v3").assertExists()
        compose.onNodeWithTag("plan-revision-3-changes-2").assertExists()
        compose.onNodeWithText("放弃草稿").performClick()
        compose.onNodeWithText("待确认 v1").assertExists()
        compose.onNodeWithText("尚无生效计划").assertExists()
        compose.onNodeWithTag("plan-revision-1-changes-0").assertExists()
        compose.onNodeWithText("缺器械替换提案").assertDoesNotExist()
        compose.onNodeWithText("放弃草稿").assertDoesNotExist()
        compose.onNodeWithTag("exercise-rg-v1-dumbbell-row").assertExists()
        compose.onNodeWithTag("exercise-rg-v1-barbell-row").assertDoesNotExist()
        compose.onNodeWithText("确认采用 v1").performClick()
        proposeBarbellRow()
        proposeDumbbellRow()
        compose.onNodeWithText("待确认 v5").assertExists()
        compose.onNodeWithText("当前生效 v1").assertExists()
        compose.onNodeWithText("放弃草稿").performClick()
        compose.onNodeWithText("当前生效 v1").assertExists()
        compose.onNodeWithText("已确认的周计划").assertExists()
        compose.onNodeWithTag("plan-revision-1-changes-0").assertExists()
        compose.onNodeWithText("待确认", substring = true).assertDoesNotExist()
        compose.onNodeWithText("缺器械替换提案").assertDoesNotExist()
        compose.onNodeWithText("放弃草稿").assertDoesNotExist()
        compose.onNodeWithTag("exercise-rg-v1-dumbbell-row").assertExists()
        compose.onNodeWithTag("exercise-rg-v1-barbell-row").assertDoesNotExist()
    }

    private fun proposeBarbellRow() {
        compose.onNodeWithTag("replace-rg-v1-dumbbell-row").performScrollTo().performClick()
        compose.onNodeWithText("当前可用：哑铃").performClick()
        compose.onNodeWithText("当前可用：杠铃").performClick()
        compose.onNodeWithText("查找替换候选").performClick()
        compose.onNodeWithText("候选：俯身杠铃划船").performScrollTo().performClick()
        compose.onNodeWithText("双侧俯身哑铃划船 → 俯身杠铃划船").assertExists()
    }

    private fun proposeDumbbellRow() {
        compose.onNodeWithTag("replace-rg-v1-barbell-row").performScrollTo().performClick()
        compose.onNodeWithText("查找替换候选").performClick()
        compose.onNodeWithText("候选：双侧俯身哑铃划船").performScrollTo().performClick()
        compose.onNodeWithText("俯身杠铃划船 → 双侧俯身哑铃划船").assertExists()
    }

    @Test fun safetyBlockCannotBeBypassedByRegeneratingWithAnOlderSafeReport() {
        generateDraft()
        compose.onNodeWithText("身体不适").performClick()
        compose.onNodeWithText("胸痛、头晕、急性损伤或关节疼痛").performScrollTo().performClick()
        compose.onNodeWithText("没有医学训练限制").performScrollTo().performClick()
        compose.onNodeWithText("评估安全反馈").performClick()
        compose.onNodeWithText("关闭安全反馈").performClick()
        compose.onNodeWithText("调整偏好 / 重新生成").performClick()
        compose.onNodeWithText("生成规则草稿").performClick()
        compose.onNodeWithText("停止训练，不用替换动作继续", substring = true).assertExists()
        compose.onNodeWithText("待确认 v2").assertDoesNotExist()
    }

    @Test fun urgentPreferencesEscalateEarlierReviewBlockWithoutAllowingSafeDowngrade() {
        generateDraft()
        compose.onNodeWithText("身体不适").performClick()
        compose.onNodeWithText("没有医学训练限制").performScrollTo().performClick()
        compose.onNodeWithText("没有当前不适或危险信号").performScrollTo().performClick()
        // Submit real UI callbacks in one frame: Robolectric loops measuring a dialog TextField.
        val other = compose.onNodeWithText("当前有其他不适").fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsActions.OnClick].action!!
        val evaluate = compose.onNodeWithText("评估安全反馈").fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsActions.OnClick].action!!
        val close = compose.onNodeWithText("关闭安全反馈").fetchSemanticsNode()
            .config[androidx.compose.ui.semantics.SemanticsActions.OnClick].action!!
        compose.runOnUiThread { other(); evaluate(); close() }
        compose.onNodeWithText("暂停普通模板生成", substring = true).assertExists()
        compose.onNodeWithText("调整偏好 / 重新生成").performClick()
        compose.onNodeWithText("胸痛、头晕、急性损伤或关节疼痛").performScrollTo().performClick()
        compose.onNodeWithText("生成规则草稿").performClick()
        compose.onNodeWithText("停止训练，不用替换动作继续", substring = true).assertExists()
        compose.onNodeWithText("当地急救。不作诊断。", substring = true).assertExists()
        compose.onNodeWithText("待确认 v2").assertDoesNotExist()
        chooseSafeReport()
        compose.onNodeWithText("生成规则草稿").performClick()
        compose.onNodeWithText("停止训练，不用替换动作继续", substring = true).assertExists()
        compose.onNodeWithText("待确认 v2").assertDoesNotExist()
    }

    @Test fun newlyEditedDiscomfortOverridesEarlierSymptomFreeReport() {
        generateDraft()
        compose.onNodeWithText("调整偏好 / 重新生成").performClick()
        compose.onNodeWithText("当前有其他不适").performScrollTo().performClick()
        compose.onNodeWithText("生成规则草稿").performClick()
        compose.onNodeWithText("暂停普通模板生成", substring = true).assertExists()
        compose.onNodeWithText("待确认 v2").assertDoesNotExist()
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val file = File("../build/verification/screenshots/$name.png")
        file.parentFile?.mkdirs()
        compose.runOnIdle {
            val view = org.robolectric.shadows.ShadowDialog.getLatestDialog()?.takeIf { it.isShowing }?.window?.decorView ?: compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
