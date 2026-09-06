package com.chloemlla.synapse.mobile.ui

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.net.toUri

object UrlOpener {
    /** 只把网页地址交给系统解析，避免其它 scheme 唤起任意导出组件。 */
    fun open(context: Context, url: String) {
        val uri = url.toUri()
        if (uri.scheme?.lowercase() !in BROWSABLE_SCHEMES) {
            Toast.makeText(context, "无法打开链接：$url", Toast.LENGTH_LONG).show()
            return
        }
        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE),
            )
        } catch (e: Exception) {
            Toast.makeText(context, "无法打开链接：$url", Toast.LENGTH_LONG).show()
        }
    }
}

/** 只把网页地址交给系统解析，避免调用方传进来的其它 scheme 唤起任意导出组件。 */
private val BROWSABLE_SCHEMES = setOf("http", "https")
