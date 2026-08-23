package com.example.sld

/**
 * Centralized SLD semantic status color export contract (Step 7, Commit 4).
 *
 * These hex strings are the single source of truth for status colors exported
 * into positioned-node [PositionedSldNode.statusColorHex] values. Every value
 * is pinned to its legacy literal by SldSemanticColorStabilityTest - visible
 * export values are retained verbatim.
 */
object SldSemanticColors {
    // Shop-drawing export contract (ShopDrawingLayoutEngine).
    const val SHOP_VALID = "#3DD68C"
    const val SHOP_WARNING = "#FFC107"
    const val SHOP_INVALID = "#F44336"
    const val SHOP_NEUTRAL = "#9E9E9E"
    const val SHOP_DATA_REQUIRED = "#3B9EFF"
    const val SHOP_UNCONNECTED = "#9E9E9E"

    // Vertical-tier riser export contract (RiserLayoutEngine).
    const val RISER_VALID = "#10B981"
    const val RISER_WARNING = "#F59E0B"
    const val RISER_INVALID = "#EF4444"
    const val RISER_NEUTRAL = "#9CA3AF"
    const val RISER_DATA_REQUIRED = "#3B82F6"
}
