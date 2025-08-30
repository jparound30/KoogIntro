package com.github.jparound30

import ai.koog.agents.core.agent.AIAgent
import ai.koog.agents.core.dsl.builder.forwardTo
import ai.koog.agents.core.dsl.builder.strategy
import ai.koog.agents.core.dsl.extension.nodeLLMRequest
import ai.koog.agents.core.tools.ToolRegistry
import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.ext.tool.AskUser
import ai.koog.agents.ext.tool.ExitTool
import ai.koog.agents.ext.tool.SayToUser
import ai.koog.agents.features.eventHandler.feature.handleEvents
import ai.koog.agents.features.tracing.feature.Tracing
import ai.koog.agents.features.tracing.writer.TraceFeatureMessageLogWriter
import ai.koog.prompt.executor.clients.google.GoogleModels
import ai.koog.prompt.executor.llms.all.simpleGoogleAIExecutor
import ai.koog.prompt.message.Message
import ai.koog.prompt.structure.StructuredResponse
import ai.koog.prompt.structure.json.JsonSchemaGenerator
import ai.koog.prompt.structure.json.JsonStructuredData
import io.github.cdimascio.dotenv.dotenv
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.io.File
import kotlin.uuid.ExperimentalUuidApi

val dotenv = dotenv()
val key = dotenv["GOOGLE_AI_API_KEY"] ?: error("GOOGLE_AI_API_KEY environment variable not set")
val toolRegistry = ToolRegistry {
    tool(SayToUser)
    tool(AskUser)
    tool(ExitTool)
}

@Serializable
@SerialName("TestTarget")
@LLMDescription("Test Target for a given source code.")
data class TestTarget(
    @property:LLMDescription("Function name")
    val targetName: String,
    @property:LLMDescription("Priority(private function:0, others: 1)")
    val priority: Int,
)

fun main(args: Array<String>) = runBlocking {
    System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "WARN")

    val debugLogFlag = dotenv["DEBUG_LOG_FLAG"]?.toBoolean() ?: false
    val traceLogFlag = dotenv["TRACE_LOG_FLAG"]?.toBoolean() ?: false

    val logger = KotlinLogging.logger("LLMTracing")

    if (args.isEmpty()) {
        error("Please provide the source code file path as a command line argument")
    }
    val sourceCode = File(args[0]).readText(Charsets.UTF_8)
    val systemPrompt = SystemPrompt()
    val input = """
        |テストケース作成の対象となるソースコードは下記である。
        |ソースコードの内容から言語を判断し、その言語の文法に応じて関数またはメソッドを列挙する。
        |まず、テスト対象の関数を列挙するだけにすること。
        |
        |## ソースコード
        |
        |{sourceCode}
    """.trimMargin().replace("{sourceCode}", sourceCode)

    val exampleTestTargets = listOf(
        listOf(
            TestTarget(
                targetName = "testFunction1",
                priority = 1,
            ),
            TestTarget(
                targetName = "testFunction2",
                priority = 0,
            ),
        ),
        listOf(
            TestTarget(
                targetName = "testFunction3",
                priority = 1,
            ),
            TestTarget(
                targetName = "testFunction4",
                priority = 0,
            ),
        ),

        )
    val testTargetStructure = JsonStructuredData.createJsonStructure<List<TestTarget>>(
        schemaFormat = JsonSchemaGenerator.SchemaFormat.JsonSchema,
        examples = exampleTestTargets,
        schemaType = JsonStructuredData.JsonSchemaType.SIMPLE
    )

    val myStrategy = strategy<String, String>("ut-testcase-gen-helper") {
        val setup by nodeLLMRequest()


        val getStructuredTestTarget by node<Message.Response, StructuredResponse<List<TestTarget>>?> { _ ->

            val structuredResponse = llm.writeSession {
                updatePrompt {
                    user(input)
                }
                this.requestLLMStructured(
                    structure = testTargetStructure,
                    fixingModel = GoogleModels.Gemini2_0Flash,
                )
            }

            """
            |Response structure:
            |$structuredResponse.
            """.trimMargin()

            structuredResponse.getOrNull()
        }

        val getTestCases by node<StructuredResponse<List<TestTarget>>?, String> {

            if (it == null) {
                return@node "失敗"
            }

            val genCasePrompt = """
                |次のソースコードの{{{testTarget}}}について、テストパターンを生成。
                |
                |## ソースコード
                |
                |{{{sourceCode}}}
                """.trimMargin().replace("{{{sourceCode}}}", sourceCode)

            val sb = StringBuilder()
            sb.appendLine("# テストパターン")

            for (testTarget in it.structure) {
                Thread.sleep(10_000)
                println("Test target: $testTarget")
                val response = llm.writeSession {
                    updatePrompt {
                        user(
                            genCasePrompt.replace("{{{testTarget}}}", testTarget.targetName)
                        )
                    }
                    this.requestLLM()
                }

                sb.appendLine("## テスト対象関数")
                sb.appendLine(testTarget.targetName)
                sb.appendLine("## テストケース")
                sb.appendLine(response.content)
            }

            sb.toString()
        }

        edge(nodeStart forwardTo setup)
        edge(setup forwardTo getStructuredTestTarget)
        edge(getStructuredTestTarget forwardTo getTestCases)
        edge(getTestCases forwardTo nodeFinish)
    }

    @OptIn(ExperimentalUuidApi::class)
    val agent = AIAgent(
        executor = simpleGoogleAIExecutor(key),
        llmModel = GoogleModels.Gemini2_0Flash,
        toolRegistry = toolRegistry,
//        llmModel = GoogleModels.Gemini2_5Flash,
//        toolRegistry = ToolRegistry.EMPTY,
        systemPrompt = systemPrompt.generatePrompt(),
        maxIterations = 10,
        strategy = myStrategy,
    ) {
        if (traceLogFlag) {
            install(Tracing) {
                addMessageProcessor(TraceFeatureMessageLogWriter(logger))
            }
        }
        handleEvents {
            onBeforeLLMCall {
                if (debugLogFlag) {
                    println("[onBeforeLLMCall] LLMCall ${it.prompt}, ${it.model.capabilities}")
                }
            }
            onAfterLLMCall {
                if (debugLogFlag)
                    println("[onAfterLLMCall] ${it.responses}")
            }

            onAgentRunError {
                if (debugLogFlag)
                    println("[onAgentRunError] Agent finished with result: ${it.throwable.message}")
            }
            onAgentFinished {
                if (debugLogFlag)
                    println("[onAgentFinished] Agent finished.")
            }
            onToolCall {
                if (debugLogFlag)
                    println("[onToolCall] Agent received tool call: ${it.toolCallId}")
            }
        }
    }

    val result = agent.run(input)
    println("[Agent]: \n$result")
}
