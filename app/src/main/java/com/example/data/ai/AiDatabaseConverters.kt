package com.example.data.ai

import androidx.room.TypeConverter
import com.example.ai.proposal.ApprovalIssuerType
import com.example.ai.proposal.ApprovalStatus
import com.example.ai.proposal.RevalidationStatus

class AiDatabaseConverters {
    @TypeConverter
    fun fromApprovalStatus(status: ApprovalStatus?): String? = status?.name

    @TypeConverter
    fun toApprovalStatus(value: String?): ApprovalStatus? {
        if (value == null) return null
        return try {
            ApprovalStatus.valueOf(value)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Unknown ApprovalStatus in database: $value", e)
        }
    }

    @TypeConverter
    fun fromApprovalIssuerType(issuerType: ApprovalIssuerType?): String? = issuerType?.name

    @TypeConverter
    fun toApprovalIssuerType(value: String?): ApprovalIssuerType? {
        if (value == null) return null
        return try {
            ApprovalIssuerType.valueOf(value)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Unknown ApprovalIssuerType in database: $value", e)
        }
    }

    @TypeConverter
    fun fromRevalidationStatus(status: RevalidationStatus?): String? = status?.name

    @TypeConverter
    fun toRevalidationStatus(value: String?): RevalidationStatus? {
        if (value == null) return null
        return try {
            RevalidationStatus.valueOf(value)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("Unknown RevalidationStatus in database: $value", e)
        }
    }
}
