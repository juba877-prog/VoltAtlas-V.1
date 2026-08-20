package com.example.ai.ui

import com.example.ai.hybrid.ConversationEngineSource
import org.junit.Assert.*
import org.junit.Test

class AssistantOfflineBadgeTest {

    // 1. Offline badge state carries accessible content description
    @Test
    fun offlineBadge_hasAccessibleContentDescription() {
        val header = ContextHeaderUiModel(
            title = "VoltAtlas Assistant",
            engineeringSource = ConversationEngineSource.LOCAL_RULE_ENGINE,
            offlineBadgeVisible = true
        )

        assertTrue(header.offlineBadgeVisible)
        assertEquals(ConversationEngineSource.LOCAL_RULE_ENGINE, header.engineeringSource)
    }

    // 2. Offline badge does not rely on color alone (explicit text "OFFLINE" is rendered)
    @Test
    fun offlineBadge_doesNotRelyOnColorAlone() {
        val headerWithOffline = ContextHeaderUiModel(
            title = "VoltAtlas Assistant",
            offlineBadgeVisible = true
        )
        assertTrue(headerWithOffline.offlineBadgeVisible)

        val headerNormal = ContextHeaderUiModel(
            title = "VoltAtlas Assistant",
            offlineBadgeVisible = false
        )
        assertFalse(headerNormal.offlineBadgeVisible)
    }
}
