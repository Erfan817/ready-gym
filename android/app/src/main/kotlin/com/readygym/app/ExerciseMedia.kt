package com.readygym.app

/** Media variants are explicit; authorization and professional review are separate. */
enum class MediaStatus { AVAILABLE, MISSING }
data class MediaAttribution(val title: String, val author: String, val sourceUrl: String,
    val originalUrl: String, val license: String, val licenseUrl: String, val changes: String)
data class ExerciseMedia(val stableId: String, val displayTitle: String, val muscles: List<String>,
    val cues: List<String>, val cautions: List<String>, val status: MediaStatus,
    val localAsset: String? = null, val source: MediaAttribution? = null, val missingReason: String? = null,
    val reviewNotice: String = "动作说明为本地整理，未经专业审核；GIF仅供动作识别，不是医疗建议或安全认证",
    val localThumbnail: String? = null)
object ExerciseMediaCatalog {
    private const val Revision = "7455efae41b330c265e7cd4b78dfa848e7ce5ebd"
    private fun demo(id: String, title: String, muscles: List<String>, cues: List<String>,
        cautions: List<String>, sourceFile: String, sourceName: String, note: String) =
        ExerciseMedia(id, title, muscles, cues, cautions, MediaStatus.AVAILABLE,
            localAsset = "exercise-demos/$sourceFile.gif",
            source = MediaAttribution(sourceName, "© Gym visual — https://gymvisual.com/",
                "https://github.com/hasaneyldrm/exercises-dataset/blob/$Revision/videos/$sourceFile.gif",
                "https://raw.githubusercontent.com/hasaneyldrm/exercises-dataset/$Revision/videos/$sourceFile.gif",
                "用户已声明取得Ready Gym使用授权；媒体不属于MIT",
                "https://gymvisual.com/content/3-terms-and-conditions-of-use",
                "原始180×180 GIF未修改，离线打包；$note"),
            localThumbnail = "exercise-demos/$sourceFile.jpg")
    val all: List<ExerciseMedia> = listOf(
        demo("rg-v1-bodyweight-squat", "徒手深蹲", listOf("股四头肌", "臀部"),
            listOf("双脚稳定支撑，脚尖方向自然", "手臂前伸辅助平衡，髋膝一起弯曲", "在能控制且无痛的幅度内缓慢起落，不憋气"),
            listOf("上游名称为potty squat，演示的是无器械双脚蹲起，不要求模仿固定深度", "膝、髋或腰出现疼痛时停止"),
            "3119-75Bgtjy", "potty squat · source ID 3119", "保留上游变式名，未认定为专业标准动作"),
        demo("rg-v1-knee-push-up", "跪姿俯卧撑", listOf("胸部", "肱三头肌", "肩部"),
            listOf("手掌与双膝稳定支撑，肩到膝尽量保持一线", "弯肘缓慢下降，肘部不完全向外张开", "舒适幅度内推起，保持腹部控制"),
            listOf("本动图为膝盖支撑；上游指导有脚掌支撑错误，未采用该段文字", "手腕、肩或膝支撑不适时停止；不要塌腰"),
            "3211-ZOuKWir", "kneeling push-up (male) · source ID 3211", "使用已核对的膝支撑画面，舍弃矛盾的上游指导"),
        demo("rg-v1-glute-bridge", "徒手臀桥（低幅演示）", listOf("臀部", "大腿后侧"),
            listOf("仰卧屈膝，双脚平放，手臂放在身体两侧", "缓慢抬髋与回落，动图为低幅版本", "不靠腰部过度后仰抬高，只使用自己能控制的幅度"),
            listOf("避免憋气或用颈部顶住地面", "腰、髋出现疼痛时停止；不强求抬高"),
            "3013-u0cNiij", "low glute bridge on floor · source ID 3013", "低幅地面臀桥，非长凳或负重变式"),
        demo("rg-v1-dead-bug", "死虫式（固定手臂单腿伸展）", listOf("核心"),
            listOf("仰卧，髋膝弯曲，双臂固定朝上", "呼吸自然，单腿小幅缓慢伸展再收回；动图展示单侧循环", "躯干保持稳定，手臂保持向上，先缩小单腿伸展幅度学习"),
            listOf("腰部拱起或不能控制时缩小幅度", "本GIF未展示完整左右换侧，不据此增加速度或幅度；出现腰痛时停止"),
            "0276-iny3m5y", "dead bug · source ID 0276", "明确标为固定手臂单腿伸展，未采用对侧手脚版本说明"),
        demo("rg-v1-dumbbell-row", "双侧俯身哑铃划船", listOf("背部", "肘屈肌群"),
            listOf("双手持轻哑铃，屈髋俯身、膝微屈，脚掌稳定", "保持可控姿势，双肘向后拉，不耸肩", "缓慢放下，不摆动躯干借力"),
            listOf("双侧俯身无凳，不等同单臂凳支撑划船", "俯身无法稳定或腰部不适时不做；先请专业人士指导"),
            "0293-BJ0Hz5L", "dumbbell bent over row · source ID 0293", "双手同时划船、无外部凳支撑"),
        demo("rg-v1-barbell-row", "俯身杠铃划船", listOf("背部", "肘屈肌群"),
            listOf("先请专业人士检查俯身姿势，脚掌稳定、膝微屈", "轻负重且躯干稳定时，双肘向后拉杆", "缓慢回落，不弹跳或甩腰借力"),
            listOf("空杆也可能过重，不按动画推算自己的重量", "不能稳定俯身或腰部不适时不做"),
            "0027-eZyBC3j", "barbell bent over row · source ID 0027", "保留实际杠铃器械条件，不以哑铃动图代替")
    )
    fun find(stableId: String): ExerciseMedia? = all.firstOrNull { it.stableId == stableId }
}
