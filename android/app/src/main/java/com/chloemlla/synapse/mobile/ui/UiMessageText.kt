package com.chloemlla.synapse.mobile.ui

/**
 * 界面文案分层：诊断类消息（异常类型 / HTTP 状态 / 原因链 / 请求字段）不默认铺在界面上，
 * 只展示第一行人类可读摘要，其余交给「详情」折叠或复制反馈。
 */
internal data class UiMessageText(
    val summary: String,
    val details: String?,
) {
    val hasDetails: Boolean get() = !details.isNullOrBlank()
}

internal fun splitUiMessage(message: String): UiMessageText {
    val trimmed = message.trim()
    val lines = trimmed.lines()
    val summary = lines.firstOrNull()?.trim().orEmpty().ifBlank { trimmed }
    val rest = lines.drop(1).joinToString("\n").trim()
    return UiMessageText(summary = summary, details = rest.ifBlank { null })
}

/** 只取摘要行；用于信息卡片这类没有展开入口的位置。 */
internal fun uiMessageSummary(message: String): String = splitUiMessage(message).summary
