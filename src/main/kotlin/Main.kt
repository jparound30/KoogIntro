package com.github.jparound30

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.ext.tool.AskUser
import ai.koog.agents.ext.tool.ExitTool
import ai.koog.agents.ext.tool.SayToUser
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.llms.all.simpleGoogleAIExecutor
import io.github.cdimascio.dotenv.dotenv
import kotlinx.coroutines.runBlocking
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

val dotenv = dotenv()
val key = dotenv["GOOGLE_AI_API_KEY"]!!
val toolRegistry = ToolRegistry {
    tool(SayToUser)
    tool(AskUser)
    tool(ExitTool)
}

@OptIn(ExperimentalUuidApi::class)
val agent = AIAgent(
    executor = simpleGoogleAIExecutor(key),
    llmModel = GoogleModels.Gemini2_0Flash,
    systemPrompt = "You are a helpful assistant. Answer user questions concisely.",
    toolRegistry = toolRegistry,
) {
    handleEvents {
        onAgentRunError { it ->
            println("Agent finished with result: ${it.throwable.message}")
        }
        onAgentFinished { it ->
            println("Agent finished. result: ${it.result}")
        }
    }
}

fun main() = runBlocking {
    val result = agent.run("Hello! How can you help me?")
    println("[Agent]: $result")
}