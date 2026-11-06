package com.takeyourbrain.app.service

import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Отладочный дамп дерева элементов экрана в Logcat (тег TYB_DUMP).
 * Нужен, чтобы узнать id элементов чужого приложения (например, плеера YouTube Shorts).
 * Смотреть: adb logcat -s TYB_DUMP. Работает только в отладочной сборке и если включён в настройках.
 */
object NodeDump {
    const val TAG = "TYB_DUMP"
    private const val MAX_DEPTH = 40
    private const val MAX_TEXT = 40

    fun log(root: AccessibilityNodeInfo) {
        Log.d(TAG, "===== ${root.packageName} =====")
        dump(root, 0)
    }

    private fun dump(node: AccessibilityNodeInfo, depth: Int) {
        if (depth > MAX_DEPTH) return
        val bounds = Rect().also { node.getBoundsInScreen(it) }
        val line = buildString {
            append("  ".repeat(depth))
            append(node.className?.toString()?.substringAfterLast('.'))
            node.viewIdResourceName?.let { append(" id=").append(it) }
            node.text?.let { append(" text=\"").append(it.take(MAX_TEXT)).append('"') }
            node.contentDescription?.let { append(" desc=\"").append(it.take(MAX_TEXT)).append('"') }
            append(' ').append(bounds.toShortString())
            if (!node.isVisibleToUser) append(" hidden")
        }
        Log.d(TAG, line)
        for (i in 0 until node.childCount) {
            node.getChild(i)?.let { dump(it, depth + 1) }
        }
    }
}
