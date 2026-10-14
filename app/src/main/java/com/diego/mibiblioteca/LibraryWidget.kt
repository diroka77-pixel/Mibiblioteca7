package com.diego.mibiblioteca

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

class LibraryWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val prefs = context.getSharedPreferences("library", Context.MODE_PRIVATE)
        val title = prefs.getString("last_opened_title", null) ?: "Continuar leyendo"
        ids.forEach { id ->
            val open = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pending = PendingIntent.getActivity(context, id, open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            manager.updateAppWidget(id, RemoteViews(context.packageName, R.layout.library_widget).apply {
                setTextViewText(R.id.widget_title, title)
                setOnClickPendingIntent(R.id.widget_root, pending)
            })
        }
    }
}
