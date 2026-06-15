package com.example.myspendings

import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class AnalyticsFragment : Fragment() {

    private val CHART_COLORS = listOf(
        "#00D4AA", "#FF6348", "#A29BFE", "#74B9FF",
        "#FD79A8", "#55EFC4", "#FDCB6E", "#636E72"
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_analytics, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ── IDs from fragment_analytics.xml ──────────────────────────
        val spinnerPeriod = view.findViewById<AutoCompleteTextView>(R.id.spinnerAnalyticsPeriod)
        val periods = listOf("Last 7 Days", "Last 30 Days", "Last 3 Months", "This Month")
        spinnerPeriod.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, periods)
        )
        spinnerPeriod.setOnItemClickListener { _, _, pos, _ -> loadAnalytics(view, pos) }

        loadAnalytics(view, 1) // default: Last 30 days
    }

    override fun onResume() {
        super.onResume()
        view?.let { loadAnalytics(it, 1) }
    }

    private fun loadAnalytics(view: View, periodIndex: Int) {
        val userId = SessionManager.getUserId(requireContext())
        val db = AppDatabase.getDatabase(requireContext())
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val cal = Calendar.getInstance()
        val toDate = sdf.format(cal.time)
        val fromDate = when (periodIndex) {
            0 -> { val c = Calendar.getInstance(); c.add(Calendar.DAY_OF_YEAR, -7); sdf.format(c.time) }
            1 -> { val c = Calendar.getInstance(); c.add(Calendar.DAY_OF_YEAR, -30); sdf.format(c.time) }
            2 -> { val c = Calendar.getInstance(); c.add(Calendar.MONTH, -3); sdf.format(c.time) }
            else -> { val c = Calendar.getInstance(); c.set(Calendar.DAY_OF_MONTH, 1); sdf.format(c.time) }
        }

        CoroutineScope(Dispatchers.IO).launch {
            val expenses   = db.expenseDao().getByDateRange(userId, fromDate, toDate)
            val categories = db.categoryDao().getAllByUser(userId)
            val catMap     = categories.associateBy { it.id }
            val total      = expenses.sumOf { it.amount }
            val days       = ((System.currentTimeMillis() / 86400000) -
                    (SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(fromDate)!!.time / 86400000)).toInt().coerceAtLeast(1)
            val dailyAvg   = total / days

            // Per-category totals
            val catTotals = categories.map { cat ->
                cat to expenses.filter { it.categoryId == cat.id }.sumOf { it.amount }
            }.filter { it.second > 0 }

            // Daily spending for line chart (last N days)
            val dailyMap = mutableMapOf<String, Double>()
            expenses.forEach { exp ->
                dailyMap[exp.date] = (dailyMap[exp.date] ?: 0.0) + exp.amount
            }
            val sortedDates = dailyMap.keys.sorted()

            withContext(Dispatchers.Main) {
                if (!isAdded) return@withContext

                view.findViewById<TextView>(R.id.tvAnalyticsTotal)?.text = "R %.2f".format(total)
                view.findViewById<TextView>(R.id.tvDailyAverage)?.text   = "R %.2f".format(dailyAvg)

                // ── LINE CHART ────────────────────────────────────────────
                val lineChart = view.findViewById<LineChart>(R.id.lineChart)
                if (sortedDates.isNotEmpty()) {
                    val entries = sortedDates.mapIndexed { idx, date ->
                        Entry(idx.toFloat(), (dailyMap[date] ?: 0.0).toFloat())
                    }
                    val dataSet = LineDataSet(entries, "Daily Spending").apply {
                        color           = Color.parseColor("#6C5CE7")
                        setCircleColor(Color.parseColor("#6C5CE7"))
                        lineWidth       = 2f
                        circleRadius    = 3f
                        setDrawFilled(true)
                        fillColor       = Color.parseColor("#2D2550")
                        valueTextColor  = Color.parseColor("#8B949E")
                        valueTextSize   = 9f
                    }
                    lineChart?.apply {
                        data = LineData(dataSet)
                        xAxis.valueFormatter = IndexAxisValueFormatter(
                            sortedDates.map { it.substring(5) } // show MM-dd
                        )
                        xAxis.textColor     = Color.parseColor("#8B949E")
                        axisLeft.textColor  = Color.parseColor("#8B949E")
                        axisRight.isEnabled = false
                        legend.textColor    = Color.parseColor("#8B949E")
                        description.isEnabled = false
                        setBackgroundColor(Color.TRANSPARENT)
                        invalidate()
                    }
                }

                // ── PIE CHART ─────────────────────────────────────────────
                val pieChart = view.findViewById<PieChart>(R.id.pieChart)
                if (catTotals.isNotEmpty()) {
                    val pieEntries = catTotals.mapIndexed { idx, (cat, amt) ->
                        PieEntry(amt.toFloat(), cat.name)
                    }
                    val pieDataSet = PieDataSet(pieEntries, "").apply {
                        colors = CHART_COLORS.map { Color.parseColor(it) }
                        valueTextColor = Color.WHITE
                        valueTextSize  = 10f
                    }
                    pieChart?.apply {
                        data = PieData(pieDataSet)
                        setHoleColor(Color.parseColor("#1C2333"))
                        holeRadius       = 40f
                        setBackgroundColor(Color.TRANSPARENT)
                        legend.textColor = Color.parseColor("#8B949E")
                        description.isEnabled = false
                        invalidate()
                    }
                }

                // ── BAR CHART ─────────────────────────────────────────────
                val barChart = view.findViewById<BarChart>(R.id.barChart)
                if (catTotals.isNotEmpty()) {
                    val barEntries = catTotals.mapIndexed { idx, (_, amt) ->
                        BarEntry(idx.toFloat(), amt.toFloat())
                    }
                    val barDataSet = BarDataSet(barEntries, "By Category").apply {
                        colors = CHART_COLORS.map { Color.parseColor(it) }
                        valueTextColor = Color.parseColor("#8B949E")
                        valueTextSize  = 9f
                    }
                    barChart?.apply {
                        data = BarData(barDataSet)
                        xAxis.valueFormatter = IndexAxisValueFormatter(
                            catTotals.map { it.first.name }
                        )
                        xAxis.textColor     = Color.parseColor("#8B949E")
                        axisLeft.textColor  = Color.parseColor("#8B949E")
                        axisRight.isEnabled = false
                        legend.textColor    = Color.parseColor("#8B949E")
                        description.isEnabled = false
                        setBackgroundColor(Color.TRANSPARENT)
                        invalidate()
                    }
                }

                // ── INSIGHTS ──────────────────────────────────────────────
                val llInsights = view.findViewById<LinearLayout>(R.id.llInsights)
                llInsights?.removeAllViews()
                if (catTotals.isNotEmpty()) {
                    val topCat = catTotals.maxByOrNull { it.second }
                    topCat?.let { (cat, amt) ->
                        val tv = TextView(requireContext()).apply {
                            text = "💡 Highest spending: ${cat.name} (R %.2f)".format(amt)
                            setTextColor(Color.parseColor("#8B949E"))
                            textSize = 13f
                            setPadding(0, 4, 0, 4)
                        }
                        llInsights?.addView(tv)
                    }
                    val tv2 = TextView(requireContext()).apply {
                        text = "📊 Daily average spend: R %.2f".format(dailyAvg)
                        setTextColor(Color.parseColor("#8B949E"))
                        textSize = 13f
                        setPadding(0, 4, 0, 4)
                    }
                    llInsights?.addView(tv2)
                } else {
                    val tv = TextView(requireContext()).apply {
                        text = "No spending data in this period."
                        setTextColor(Color.parseColor("#8B949E"))
                        textSize = 13f
                    }
                    llInsights?.addView(tv)
                }
            }
        }
    }
}
