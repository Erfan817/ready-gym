package com.readygym.app

/** Availability is separate from exercise eligibility; missing media never substitutes another movement. */
enum class MediaStatus { AVAILABLE, MISSING }
data class MediaAttribution(val title: String, val author: String, val sourceUrl: String,
    val originalUrl: String, val license: String, val licenseUrl: String, val changes: String)
data class ExerciseMedia(val stableId: String, val displayTitle: String, val muscles: List<String>,
    val cues: List<String>, val cautions: List<String>, val status: MediaStatus,
    val localAsset: String? = null, val source: MediaAttribution? = null, val missingReason: String? = null,
    val reviewNotice: String = "动作说明为本地自编，未经专业审核；GIF仅供动作识别，不是医疗建议或安全认证")
object ExerciseMediaCatalog {
    private fun missing(id: String, title: String, muscles: List<String>, cues: List<String>, cautions: List<String>) =
        ExerciseMedia(id, title, muscles, cues, cautions, MediaStatus.MISSING,
            missingReason = "暂无已核验、动作匹配且许可明确的离线GIF，不用其他动作冒充")
    val all: List<ExerciseMedia> = listOf(
        missing("rg-v1-bodyweight-squat", "徒手深蹲", listOf("股四头肌", "臀部"),
            listOf("双脚稳定支撑，脚尖方向自然", "髋膝一起弯曲，膝盖跟随脚尖方向", "在能控制且无痛的幅度内缓慢起落，不憋气"),
            listOf("不强求深度，避免脚跟抬起或膝盖内扣", "膝、髋或腰出现疼痛时停止")).copy(
                status = MediaStatus.AVAILABLE, localAsset = "exercise-demos/squats-wensceslao.gif", missingReason = null,
                source = MediaAttribution("Squats.gif", "Wensceslao", "https://commons.wikimedia.org/wiki/File:Squats.gif",
                    "https://upload.wikimedia.org/wikipedia/commons/e/e6/Squats.gif", "CC BY-SA 4.0",
                    "https://creativecommons.org/licenses/by-sa/4.0/", "原始GIF未修改，仅本地重命名与等比显示")),
        missing("rg-v1-knee-push-up", "跪姿俯卧撑", listOf("胸部", "肱三头肌", "肩部"),
            listOf("手掌与双膝稳定支撑，肩到膝尽量保持一线", "弯肘缓慢下降，肘部不完全向外张开", "舒适幅度内推起，保持腹部控制"),
            listOf("这是跪姿，不是靠墙或标准俯卧撑", "手腕、肩或膝支撑不适时停止；不要塌腰")),
        missing("rg-v1-glute-bridge", "徒手臀桥", listOf("臀部", "大腿后侧"),
            listOf("仰卧屈膝，双脚平放，手臂放在身体两侧", "脚掌稳定，缓慢抬髋至能控制的位置", "不靠腰部过度后仰抬高，缓慢回落"),
            listOf("避免憋气或用颈部顶住地面", "腰、髋出现疼痛时停止；不强求抬高")),
        missing("rg-v1-dead-bug", "死虫式核心练习", listOf("核心"),
            listOf("仰卧，髋膝弯曲，双臂朝上", "呼吸自然，对侧手脚小幅缓慢伸展再收回", "躯干保持稳定，左右交替；先缩小幅度学习"),
            listOf("腰部拱起或不能控制时缩小幅度", "不是快速甩动手脚；出现腰痛时停止")),
        missing("rg-v1-dumbbell-row", "双侧俯身哑铃划船", listOf("背部", "肘屈肌群"),
            listOf("双手持轻哑铃，屈髋俯身、膝微屈，脚掌稳定", "背部姿势保持可控，双肘向后拉，不耸肩", "缓慢放下，不摆动躯干借力"),
            listOf("双侧俯身，不借助椅子或训练凳；不等同单臂凳支撑划船", "俯身无法稳定或腰部不适时不做；先请专业人士指导")),
        missing("rg-v1-barbell-row", "俯身杠铃划船", listOf("背部", "肘屈肌群"),
            listOf("先请专业人士检查俯身姿势，脚掌稳定、膝微屈", "在轻负重且躯干稳定的前提下，双肘向后拉杆", "缓慢回落，不弹跳或甩腰借力"),
            listOf("空杆也可能过重，不按动画推算自己的重量", "不能稳定俯身或腰部不适时不做"))
    )
    fun find(stableId: String): ExerciseMedia? = all.firstOrNull { it.stableId == stableId }
}
