package com.example.myspendings

import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class DashboardFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_dashboard, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadDashboard(view)
    }

    override fun onResume() {
        super.onResume()
        // Refresh whenever user comes back to this screen
        view?.let { loadDashboard(it) }
    }

    private fun loadDashboard(view: View) {
        val userId = SessionManager.getUserId(requireContext())
        val db = AppDatabase.getDatabase(requireContext())

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val cal = Calendar.getInstance()
        val toDate = sdf.format(cal.time)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        val fromDate = sdf.format(cal.time)

        CoroutineScope(Dispatchers.IO).launch {
            val totalSpent  = db.expenseDao().getTotalSpending(userId, fromDate, toDate)
            val categories  = db.categoryDao().getAllByUser(userId)
            val monthlyBudget = SessionManager.getMonthlyBudget(requireContext())
            val remaining   = monthlyBudget - totalSpent
            val pct         = if (monthlyBudget > 0) (totalSpent / monthlyBudget * 100).toInt() else 0

            // Per-category spending
            val catData = categories.map { cat ->
                val spent = db.expenseDao().getTotalByCategory(userId, cat.id, fromDate, toDate)
                Triple(cat, spent, cat.budgetLimit)
            }
            val overCount = catData.count { (_, spent, limit) -> limit > 0 && spent > limit }
            val underBudgetAmt = if (remaining > 0) remaining else 0.0

            withContext(Dispatchers.Main) {
                if (!isAdded) return@withContext

                // ── IDs from fragment_dashboard.xml ───────────────────────
                view.findViewById<TextView>(R.id.tvDashboardSubtitle)
                    ?.text = "Budget Overview — ${SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(Date())}"

                view.findViewById<TextView>(R.id.tvTotalSpentLarge)
                    ?.text = "R %.0f".format(totalSpent)

                view.findViewById<TextView>(R.id.tvRemaining)
                    ?.apply {
                        text = "R %.0f".format(remaining)
                        setTextColor(if (remaining >= 0) requireContext().getColor(R.color.status_green)
                        else requireContext().getColor(R.color.status_red))
                    }

                view.findViewById<TextView>(R.id.tvOfBudget)
                    ?.text = "of R %.0f budget".format(monthlyBudget)

                view.findViewById<ProgressBar>(R.id.progressBudget)
                    ?.progress = pct.coerceIn(0, 100)

                view.findViewById<TextView>(R.id.tvBudgetPct)
                    ?.text = "%.1f%% of budget used".format(if (monthlyBudget > 0) totalSpent / monthlyBudget * 100 else 0.0)

                view.findViewById<TextView>(R.id.tvSummaryTotalSpent)
                    ?.text = "R %.0f".format(totalSpent)

                view.findViewById<TextView>(R.id.tvUnderBudget)
                    ?.text = "R %.0f".format(underBudgetAmt)

                view.findViewById<TextView>(R.id.tvCategoriesOver)
                    ?.text = overCount.toString()

                // ── Category breakdown rows ───────────────────────────────
                val llBreakdown = view.findViewById<LinearLayout>(R.id.llCategoryBreakdown)
                val tvNoCat     = view.findViewById<TextView>(R.id.tvNoCategoryData)
                llBreakdown?.removeAllViews()

                if (catData.isEmpty()) {
                    tvNoCat?.visibility = View.VISIBLE
                } else {
                    tvNoCat?.visibility = View.GONE
                    catData.forEach { (cat, spent, limit) ->
                        val row = layoutInflater.inflate(
                            R.layout.item_category_progress, llBreakdown, false
                        )
                        row.findViewById<TextView>(R.id.tvCategoryName)?.text = cat.name
                        val pctCat = if (limit > 0) (spent / limit * 100).toInt() else 0
                        row.findViewById<TextView>(R.id.tvCategoryAmounts)
                            ?.text = "R %.0f / R %.0f".format(spent, limit)
                        row.findViewById<ProgressBar>(R.id.progressCategory)
                            ?.apply {
                                progress = pctCat.coerceIn(0, 100)
                                progressTintList = android.content.res.ColorStateList.valueOf(
                                    if (limit > 0 && spent > limit) Color.parseColor("#FF4757")
                                    else Color.parseColor("#00D4AA")
                                )
                            }
                        row.findViewById<TextView>(R.id.tvCategoryPct)
                            ?.text = "$pctCat% used"
                        val chipOver = row.findViewById<com.google.android.material.chip.Chip>(R.id.chipOverBudget)
                        val tvOverAmt = row.findViewById<TextView>(R.id.tvCategoryOverAmount)
                        if (limit > 0 && spent > limit) {
                            chipOver?.visibility = View.VISIBLE
                            tvOverAmt?.apply {
                                visibility = View.VISIBLE
                                text = "R %.2f over".format(spent - limit)
                            }
                        }
                        llBreakdown?.addView(row)
                    }
                }
            }
        }
    }
}
