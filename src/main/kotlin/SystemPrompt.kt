package com.github.jparound30

import kotlinx.io.files.SystemPathSeparator
import java.io.File

class SystemPrompt {
    private fun readFileContent(filePath: String): String {
        return File(filePath).readText(Charsets.UTF_8)
    }

    fun generatePrompt(): String {
        val prompt = readFileContent("ctx" + SystemPathSeparator + "systemPrompt.md")
        val oldSystemFeature = readFileContent("ctx" + SystemPathSeparator + "oldSystemFeature.md")
        val newSystemFeature = readFileContent("ctx" + SystemPathSeparator + "newSystemFeature.md")
        val unittestPolicy = readFileContent("ctx" + SystemPathSeparator + "unittestPolicy.md")
        return prompt
            .replace("{oldSystemFeature}", oldSystemFeature)
            .replace("{newSystemFeature}", newSystemFeature)
            .replace("{unittestPolicy}", unittestPolicy)
    }
}