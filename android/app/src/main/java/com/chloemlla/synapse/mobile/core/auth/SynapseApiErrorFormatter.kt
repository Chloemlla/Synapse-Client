package com.chloemlla.synapse.mobile.core.auth

import org.json.JSONArray
import org.json.JSONObject

internal object SynapseApiErrorFormatter {
    private const val MAX_DETAIL_LINES = 8

    fun failureMessage(
        method: String,
        url: String,
        statusCode: Int,
        requestFields: List<String>,
        responseText: String,
    ): String {
        val json = runCatching { JSONObject(responseText) }.getOrNull()
        val summary = json?.let(::extractSummary)
            ?: "Synapse API request failed."
        val details = json?.let(::extractDetails).orEmpty()
            .filterNot { it.equals(summary, ignoreCase = true) }
            .distinct()
            .take(MAX_DETAIL_LINES)

        return buildString {
            appendLine(summary)
            appendLine("API 请求：$method $url")
            appendLine("HTTP 状态：$statusCode")
            if (requestFields.isNotEmpty()) {
                appendLine("请求字段：${requestFields.joinToString(", ")}")
            }
            if (details.isNotEmpty()) {
                appendLine("详细原因：")
                details.forEach { detail ->
                    appendLine("- $detail")
                }
            }
        }.trim()
    }

    private fun extractSummary(json: JSONObject): String? =
        json.stringValue("message", "title", "error_description")
            ?: json.optJSONObject("error")?.stringValue("message", "title", "error_description")
            ?: json.stringValue("error")
            ?: "Synapse API request failed."

    private fun extractDetails(json: JSONObject): List<String> =
        buildList {
            addFrom(json.opt("details"))
            addFrom(json.opt("errors"))
            addFrom(json.opt("issues"))
            json.optJSONObject("error")?.let { error ->
                addFrom(error.opt("details"))
                addFrom(error.opt("errors"))
                addFrom(error.opt("issues"))
            }
        }

    private fun MutableList<String>.addFrom(value: Any?) {
        when (value) {
            is JSONArray -> {
                for (index in 0 until value.length()) {
                    addFrom(value.opt(index))
                }
            }

            is JSONObject -> addFromObject(value)
            is String -> value.takeIf { it.isNotBlank() }?.let(::add)
        }
    }

    private fun MutableList<String>.addFromObject(json: JSONObject) {
        val fieldErrors = json.optJSONObject("fieldErrors")
        if (fieldErrors != null) {
            fieldErrors.keys().asSequence().forEach { field ->
                addFieldMessages(field, fieldErrors.opt(field))
            }
        }

        addFrom(json.opt("formErrors"))

        val message = json.stringValue("message")
        if (message != null) {
            val field = json.fieldName()
            add(if (field != null) "$field：$message" else message)
            return
        }

        addFrom(json.opt("details"))
        addFrom(json.opt("errors"))
        addFrom(json.opt("issues"))

        json.keys().asSequence()
            .filterNot { it in setOf("fieldErrors", "formErrors", "details", "errors", "issues") }
            .forEach { key ->
                addFieldMessages(key, json.opt(key))
            }
    }

    private fun MutableList<String>.addFieldMessages(field: String, value: Any?) {
        when (value) {
            is JSONArray -> {
                for (index in 0 until value.length()) {
                    val item = value.opt(index)
                    when (item) {
                        is JSONObject -> addFromObject(item)
                        is String -> item.takeIf { it.isNotBlank() }?.let { add("$field：$it") }
                    }
                }
            }

            is JSONObject -> addFromObject(value)
            is String -> value.takeIf { it.isNotBlank() }?.let { add("$field：$it") }
        }
    }

    private fun JSONObject.fieldName(): String? =
        stringValue("field", "param", "property")
            ?: optJSONArray("path")?.joinValues()

    private fun JSONArray.joinValues(): String? {
        val values = buildList {
            for (index in 0 until length()) {
                optString(index).takeIf { it.isNotBlank() }?.let(::add)
            }
        }
        return values.takeIf { it.isNotEmpty() }?.joinToString(".")
    }

    private fun JSONObject.stringValue(vararg names: String): String? =
        names.firstNotNullOfOrNull { name ->
            if (!has(name) || isNull(name)) return@firstNotNullOfOrNull null
            when (val value = opt(name)) {
                is String -> value
                is Number, is Boolean -> value.toString()
                else -> null
            }?.takeIf { it.isNotBlank() }
        }
}
