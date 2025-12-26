import java.time.LocalDate

// Top-level build file where you can add configuration options common to all subprojects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    id("com.google.devtools.ksp") version "2.2.21-2.0.4" apply false
    kotlin("jvm")
}

fun executeGitCommand(command: String, default: String = ""): String {
    return try {
        providers.exec {
            commandLine(*command.split("\\s".toRegex()).toTypedArray())
        }.standardOutput.asText.get().trim()
    } catch (e: Exception) {
        e.printStackTrace()
        default
    }
}

val versionCode = executeGitCommand("git rev-list --count HEAD", "1")
val tagName = executeGitCommand("git tag --points-at HEAD")
val hash = executeGitCommand("git rev-parse --short HEAD", "nohash")
val branch = executeGitCommand("git rev-parse --abbrev-ref HEAD")
val outputsDir = "build/appOutputs/$branch/${tagName.ifBlank { "nightly/r$versionCode.$hash" }}"
val mdFileName = "ChangeLog.md"

ext {
    set("versionCode", versionCode)
    set("tagName", tagName)
    set("hash", hash)
    set("branch", branch)
    set("outputsDir", outputsDir)
    set("changeLog", mdFileName)
}


val changelogDir: File = project.rootProject.file(outputsDir)

tasks.register("generateChangelog") {
    group = "documentation"
    description = "生成MD文件并在index.json中引用"

    doLast {
        val dateStr = LocalDate.now().toString()


        if (tagName.isBlank()) {
            println("当前 HEAD 没有 Tag，跳过。")
            return@doLast
        }

        val prevTag = executeGitCommand("git describe --tags --abbrev=0 HEAD^")
        val range = if (prevTag.isNotEmpty()) "$prevTag..HEAD" else "HEAD"

        println("处理版本: $tagName (From $prevTag)")

        // 获取并解析 Log
        val logs = executeGitCommand("git log $range --pretty=format:%s").lines().distinct()
        // 分类容器: Type -> List of "Scope: Message"
        val changes = mutableMapOf<String, MutableList<String>>()

        // 正则: type(scope): message
        val regex = "^(feat|fix|perf|refactor|docs|style|test|chore)(\\((.*)\\))?: (.*)$".toRegex()

        logs.forEach { line ->
            val match = regex.find(line)
            if (match != null) {
                val (type, _, scope, message) = match.destructured
                if (type in arrayOf("test", "chore", "style", "docs")) return@forEach
                // 格式化单行文案: "Scope: Message" 或 "Message"
                val formattedMsg = if (scope.isNotBlank()) "**$scope**: $message" else message
                changes.getOrPut(type) { mutableListOf() }.add(formattedMsg)
            } else if (line.isNotBlank()) {
                changes.getOrPut("misc") { mutableListOf() }.add(line)
            }
        }

        if (changes.isEmpty()) {
            println("无变更记录")
            return@doLast
        }

        val mdFile = File(changelogDir, mdFileName)

        val mdContent = StringBuilder()
        mdContent.append("# Release $tagName\n\n-----\n\n")
        mdContent.append("> 发布日期: $dateStr\n\n")

        // 定义类型显示的优先级和标题映射
        val typeHeaders = mapOf(
            "feat" to "✨ New Features",
            "fix" to "🐛 Bug Fixes",
            "perf" to "⚡ Performance",
            "refactor" to "♻️ Refactor",
            "misc" to "🔧 Others",
        )

        // 按优先级顺序写入
        typeHeaders.forEach { (type, header) ->
            changes[type]?.let { msgs ->
                mdContent.append("## $header\n\n")
                msgs.forEach { msg -> mdContent.append("- $msg\n") }
                mdContent.append("\n")
            }
        }

        // 写入 MD 文件
        if (!(changelogDir.exists())) changelogDir.mkdirs()
        mdFile.writeText(mdContent.toString())
        println("Markdown 生成完毕: ${mdFile.name}")
    }
}

tasks.named("assemble") {
    finalizedBy("generateChangelog")
}