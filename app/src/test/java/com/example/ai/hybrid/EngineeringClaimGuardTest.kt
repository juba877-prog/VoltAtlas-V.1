package com.example.ai.hybrid

import org.junit.Assert.*
import org.junit.Test

class EngineeringClaimGuardTest {

    // 1. Text response asserting bare engineering recommendation without tool backing is rejected
    @Test
    fun textResponseWithBareEngineeringNumber_isRejected() {
        val rawText = "I recommend you size this feeder using 300 mm² copper cable."
        val isUnsupported = EngineeringClaimGuard.containsUnsupportedEngineeringClaim(
            rawText = rawText,
            hasBackingToolResult = false
        )
        assertTrue("Bare recommendation of 300 mm² without tool backing must be flagged", isUnsupported)
    }

    // 2. Text response with engineering number backed by tool result is accepted
    @Test
    fun textResponseWithEngineeringNumber_butHasBackingToolResult_isAccepted() {
        val rawText = "Based on calculation results, 300 mm² meets the 3.0% voltage drop criterion."
        val isUnsupported = EngineeringClaimGuard.containsUnsupportedEngineeringClaim(
            rawText = rawText,
            hasBackingToolResult = true
        )
        assertFalse("Claim backed by tool result must be accepted", isUnsupported)
    }

    // 3. Text response with no engineering claim is accepted as-is
    @Test
    fun textResponseWithNoEngineeringClaim_isAcceptedAsIs() {
        val rawText = "Could you please specify whether the cable is laid in ducts or on tray?"
        val isUnsupported = EngineeringClaimGuard.containsUnsupportedEngineeringClaim(
            rawText = rawText,
            hasBackingToolResult = false
        )
        assertFalse("Clarifying question with no numbers must be accepted", isUnsupported)
    }

    // 4. Arabic recommendation phrasing with units is detected
    @Test
    fun arabicRecommendationPhrasing_isAlsoDetected() {
        val arabicText = "نوصي باختيار كابل سعة 240 مم² لهذا المغذي"
        val isUnsupported = EngineeringClaimGuard.containsUnsupportedEngineeringClaim(
            rawText = arabicText,
            hasBackingToolResult = false
        )
        assertTrue("Arabic unbacked claim with 240 mm² must be flagged", isUnsupported)
    }

    // 5. Breaker and voltage unbacked claims are detected
    @Test
    fun breakerAndVoltageClaims_areDetected() {
        val breakerClaim = "Select a 400 A breaker for this circuit."
        assertTrue(EngineeringClaimGuard.containsUnsupportedEngineeringClaim(breakerClaim, false))

        val voltageClaim = "Set to 400 V nominal voltage."
        assertTrue(EngineeringClaimGuard.containsUnsupportedEngineeringClaim(voltageClaim, false))
    }
}
