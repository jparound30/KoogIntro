package com.github.jparound30

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeExecuteTool
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.dsl.extension.nodeLLMSendToolResult
import ai.koog.agents.core.dsl.extension.onAssistantMessage
import ai.koog.agents.core.dsl.extension.onToolCall
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.ext.tool.AskUser
import ai.koog.agents.ext.tool.ExitTool
import ai.koog.agents.ext.tool.SayToUser
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.agents.features.tracing.feature.Tracing
import ai.koog.agents.features.tracing.writer.TraceFeatureMessageLogWriter
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.llms.all.simpleGoogleAIExecutor
import io.github.cdimascio.dotenv.dotenv
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import kotlinx.io.files.SystemPathSeparator
import org.slf4j.LoggerFactory
import java.io.File
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

val dotenv = dotenv()
val key = dotenv["GOOGLE_AI_API_KEY"] ?: error("GOOGLE_AI_API_KEY environment variable not set")
val toolRegistry = ToolRegistry {
    tool(SayToUser)
    tool(AskUser)
    tool(ExitTool)
}


fun main() = runBlocking {
//    val logger = KotlinLogging.logger("LLMTracing")

    var sourceCode = File("ctx" + SystemPathSeparator + "Serializer.php ").readText(Charsets.UTF_8)
    var systemPrompt = SystemPrompt()
    var input = """
        |テストケース作成の対象となるソースコードは下記である。
        |まず、テスト対象の関数を列挙すること。
        |その後、列挙したテスト対象に関するテストケースを作成すること。
        |
        |## ソースコード
        |
        |{sourceCode}
    """.trimMargin().replace("{sourceCode}", sourceCode)


    @OptIn(ExperimentalUuidApi::class)
    val agent = AIAgent(
        executor = simpleGoogleAIExecutor(key),
        llmModel = GoogleModels.Gemini2_0Flash,
        toolRegistry = toolRegistry,
//        llmModel = GoogleModels.Gemini2_5Flash,
//        toolRegistry = ToolRegistry.EMPTY,
        systemPrompt = systemPrompt.generatePrompt(),
        maxIterations = 3,
    ) {
//        install(Tracing) {
//            addMessageProcessor(TraceFeatureMessageLogWriter(logger))
//        }
        handleEvents {
            onBeforeLLMCall { it ->
                println("[Before] LLMCall ${it.prompt}, ${it.model.capabilities}")
            }
            onAfterLLMCall { it ->
                println("LLMCallL ${it.responses}")
            }

            onAgentRunError { it ->
                println("Agent finished with result: ${it.throwable.message}")
            }
            onAgentFinished { it ->
                println("Agent finished.")
            }
            onToolCall { it ->
                println("Agent received tool call: ${it.toolCallId}")
            }
        }
    }

    val result = agent.run(input)
    println("[Agent]: \n$result")
}

fun main() = runBlocking {
    val result = agent.run("Hello! How can you help me?")
    println("[Agent]: $result")
}