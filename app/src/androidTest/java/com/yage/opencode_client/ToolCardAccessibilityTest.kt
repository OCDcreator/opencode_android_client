package com.yage.opencode_client

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import com.yage.opencode_client.data.model.MessageWithParts
import com.yage.opencode_client.data.model.Part
import com.yage.opencode_client.data.repository.OpenCodeRepository
import com.yage.opencode_client.ui.chat.ChatMessageList
import kotlinx.serialization.json.Json
import org.junit.Rule
import org.junit.Test

/**
 * Layer 2 (component) test for the read/write tool-card accessibility distinction.
 *
 * A read file card and a write file card differ only by icon color, which Compose
 * does not expose to the semantics tree. The render code therefore encodes the
 * read/write nature into the testTag (toolcard.read.* / toolcard.write.*) and the
 * icon contentDescription. This test renders ChatMessageList with a fake read part
 * and a fake write part and asserts the semantics tree distinguishes them.
 */
class ToolCardAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private fun toolMessage(tool: String, path: String): MessageWithParts {
        val payload = """
            [{"info":{"id":"msg_1","role":"assistant","sessionID":"ses_1"},
              "parts":[{"type":"tool","id":"p1","sessionID":"ses_1","messageID":"msg_1",
                        "tool":"$tool","state":"completed","metadata":{"path":"$path"},
                        "output":"done"}]}]
        """.trimIndent()
        return json.decodeFromString<List<MessageWithParts>>(payload)[0]
    }

    private val repository: OpenCodeRepository = OpenCodeRepository()

    @Test
    fun readToolCardGetsReadTagAndDescription() {
        composeRule.setContent {
            MaterialTheme {
                ChatMessageList(
                    currentSessionId = "ses_1",
                    messages = listOf(toolMessage(tool = "read", path = "src/config.kt")),
                    streamingPartTexts = emptyMap(),
                    streamingReasoningPart = null,
                    isLoading = false,
                    messageLimit = 30,
                    repository = repository,
                    workspaceDirectory = null,
                    onLoadMore = {},
                    onFileClick = {},
                    onMarkdownLinkClick = {},
                    onForkFromMessage = {}
                )
            }
        }

        composeRule.onNodeWithTag("toolcard.read.config.kt").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Read file config.kt via read").assertIsDisplayed()
    }

    @Test
    fun writeToolCardGetsWriteTagAndDescription() {
        composeRule.setContent {
            MaterialTheme {
                ChatMessageList(
                    currentSessionId = "ses_1",
                    messages = listOf(toolMessage(tool = "write", path = "src/config.kt")),
                    streamingPartTexts = emptyMap(),
                    streamingReasoningPart = null,
                    isLoading = false,
                    messageLimit = 30,
                    repository = repository,
                    workspaceDirectory = null,
                    onLoadMore = {},
                    onFileClick = {},
                    onMarkdownLinkClick = {},
                    onForkFromMessage = {}
                )
            }
        }

        composeRule.onNodeWithTag("toolcard.write.config.kt").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Write file config.kt via write").assertIsDisplayed()
    }

    @Test
    fun editToolCardCountsAsWrite() {
        composeRule.setContent {
            MaterialTheme {
                ChatMessageList(
                    currentSessionId = "ses_1",
                    messages = listOf(toolMessage(tool = "edit", path = "src/main.kt")),
                    streamingPartTexts = emptyMap(),
                    streamingReasoningPart = null,
                    isLoading = false,
                    messageLimit = 30,
                    repository = repository,
                    workspaceDirectory = null,
                    onLoadMore = {},
                    onFileClick = {},
                    onMarkdownLinkClick = {},
                    onForkFromMessage = {}
                )
            }
        }

        composeRule.onNodeWithTag("toolcard.write.main.kt").assertIsDisplayed()
    }
}
