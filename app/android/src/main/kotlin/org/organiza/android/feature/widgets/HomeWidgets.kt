package org.organiza.android.feature.widgets

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import org.organiza.android.MainActivity
import org.organiza.android.OrganizaApp
import org.organiza.android.R
import org.organiza.presentation.HomeWidget

/**
 * The home screen widgets (ADR-0039), drawn from the core's widget model whenever it changes. They show
 * only what the app shows, and tapping a habit's circle marks it done through the core.
 */
object HomeWidgets {
    @Volatile private var shown: HomeWidget? = null

    /** Main thread. Redraws only when what the widgets show has changed. */
    fun show(context: Context, widget: HomeWidget?) {
        if (widget == null || widget == shown) return
        shown = widget
        render(context)
    }

    fun render(context: Context) {
        val widget = shown ?: return
        val manager = AppWidgetManager.getInstance(context)
        manager.getAppWidgetIds(ComponentName(context, TodayWidget::class.java)).forEach { manager.updateAppWidget(it, today(context, widget)) }
        manager.getAppWidgetIds(ComponentName(context, StreakWidget::class.java)).forEach { manager.updateAppWidget(it, streak(context, widget)) }
    }

    internal fun today(context: Context, widget: HomeWidget) = RemoteViews(context.packageName, R.layout.widget_today).apply {
        setTextViewText(R.id.percent, widget.percent)
        setTextViewText(R.id.headline, widget.headline)
        setTextViewText(R.id.footer, listOfNotNull(widget.screenTime, widget.next).joinToString(" · "))
        setOnClickPendingIntent(R.id.root, open(context))
        ROWS.forEachIndexed { index, (row, name, check) ->
            val habit = widget.habits.getOrNull(index)
            setViewVisibility(row, if (habit == null) View.GONE else View.VISIBLE)
            if (habit == null) return@forEachIndexed
            setTextViewText(name, habit.name)
            setTextViewText(check, if (habit.doneToday) "✓" else "")
            setInt(check, "setBackgroundResource", if (habit.doneToday) R.drawable.widget_check_done else R.drawable.widget_check_open)
            setContentDescription(check, if (habit.doneToday) "${habit.name}, done today" else "Mark ${habit.name} done")
            val toggle = Intent(context, TodayWidget::class.java)
                .setAction(TOGGLE)
                .putExtra(NAME, habit.name)
                .putExtra(DONE, !habit.doneToday)
            setOnClickPendingIntent(
                check,
                PendingIntent.getBroadcast(context, habit.id.hashCode(), toggle, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
            )
        }
    }

    internal fun streak(context: Context, widget: HomeWidget) = RemoteViews(context.packageName, R.layout.widget_streak).apply {
        setTextViewText(R.id.streak, widget.streak.toString())
        setTextViewText(R.id.nudge, widget.nudge)
        setContentDescription(R.id.root, "${widget.streak}-day streak. ${widget.nudge}")
        setOnClickPendingIntent(R.id.root, open(context))
    }

    private fun open(context: Context): PendingIntent =
        PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)

    internal const val TOGGLE = "org.organiza.widget.TOGGLE"
    internal const val NAME = "name"
    internal const val DONE = "done"

    private val ROWS = listOf(
        Triple(R.id.row0, R.id.name0, R.id.check0),
        Triple(R.id.row1, R.id.name1, R.id.check1),
        Triple(R.id.row2, R.id.name2, R.id.check2),
        Triple(R.id.row3, R.id.name3, R.id.check3),
    )
}

/** The large widget. A new widget asks the core for a fresh snapshot, which draws it. */
class TodayWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        HomeWidgets.render(context)
        OrganizaApp.from(context).core.requestUiRefresh()
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == HomeWidgets.TOGGLE) {
            val name = intent.getStringExtra(HomeWidgets.NAME) ?: return
            OrganizaApp.from(context).core.markHabit(name, intent.getBooleanExtra(HomeWidgets.DONE, true))
            return
        }
        super.onReceive(context, intent)
    }
}

/** The small streak widget. */
class StreakWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        HomeWidgets.render(context)
        OrganizaApp.from(context).core.requestUiRefresh()
    }
}
