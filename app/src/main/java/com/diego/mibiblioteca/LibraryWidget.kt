package com.diego.mibiblioteca

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews

class LibraryWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        updateWidgets(context, manager, ids)
    }

    override fun onEnabled(context: Context) {
        refresh(context)
    }

    companion object {
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, LibraryWidget::class.java))
            if (ids.isNotEmpty()) updateWidgets(context, manager, ids)
        }

        private fun updateWidgets(context: Context, manager: AppWidgetManager, ids: IntArray) {
            val prefs = context.getSharedPreferences("library", Context.MODE_PRIVATE)
            val readerPrefs = context.getSharedPreferences("reader", Context.MODE_PRIVATE)
            val title = widgetBookTitle(prefs.getString("last_opened_title", null))
            val lastUri = prefs.getString("last_opened_book", null)?.let(Uri::parse)
            val percent = lastUri?.let {
                readerPrefs.getInt("percent_" + readerProgressKey(it), 0)
            }?.coerceIn(0, 100) ?: 0
            ids.forEach { id ->
                val continueReading = Intent(context, MainActivity::class.java).apply {
                    action = ACTION_CONTINUE_READING
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val openLibrary = Intent(context, MainActivity::class.java).apply {
                    action = ACTION_OPEN_LIBRARY
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val continuePending = PendingIntent.getActivity(context, id * 2, continueReading,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                val libraryPending = PendingIntent.getActivity(context, id * 2 + 1, openLibrary,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                manager.updateAppWidget(id, RemoteViews(context.packageName, R.layout.library_widget).apply {
                    setTextViewText(R.id.widget_title, title)
                    setTextViewText(R.id.widget_progress_text, widgetProgressLabel(percent))
                    setProgressBar(R.id.widget_progress, 100, percent, false)
                    setViewVisibility(R.id.widget_progress, if (percent > 0) View.VISIBLE else View.GONE)
                    setOnClickPendingIntent(R.id.widget_root, continuePending)
                    setOnClickPendingIntent(R.id.widget_continue, continuePending)
                    setOnClickPendingIntent(R.id.widget_library, libraryPending)
                })
            }
        }
    }
}

internal fun widgetBookTitle(savedTitle: String?): String =
    savedTitle?.trim().takeUnless { it.isNullOrBlank() } ?: "Continúa tu lectura"

internal fun widgetProgressLabel(percent: Int): String = when (percent.coerceIn(0, 100)) {
    0 -> "Sin progreso guardado"
    100 -> "Lectura terminada"
    else -> "${percent.coerceIn(0, 100)} % leído"
}
