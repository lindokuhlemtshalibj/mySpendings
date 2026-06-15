package com.example.myspendings

import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class GamificationFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_gamification, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadBadges(view)
    }

    override fun onResume() {
        super.onResume()
        view?.let { loadBadges(it) }
    }

    private fun loadBadges(view: View) {
        val userId = SessionManager.getUserId(requireContext())
        val db     = AppDatabase.getDatabase(requireContext())
        val sdf    = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

        // Date ranges
        val today     = sdf.format(Date())
        val cal30     = Calendar.getInstance().also { it.add(Calendar.DAY_OF_YEAR, -30) }
        val from30    = sdf.format(cal30.time)
        val calMonth  = Calendar.getInstance().also { it.set(Calendar.DAY_OF_MONTH, 1) }
        val fromMonth = sdf.format(calMonth.time)
        val cal7      = Calendar.getInstance().also { it.add(Calendar.DAY_OF_YEAR, -7) }
        val from7     = sdf.format(cal7.time)

        CoroutineScope(Dispatchers.IO).launch {
            val expenses      = db.expenseDao().getAllByUser(userId)
            val categories    = db.categoryDao().getAllByUser(userId)
            val monthlyBudget = SessionManager.getMonthlyBudget(requireContext())
            val totalSpent30  = db.expenseDao().getTotalSpending(userId, from30, today)
            val totalMonth    = db.expenseDao().getTotalSpending(userId, fromMonth, today)
            val expensesMonth = expenses.filter { it.date >= fromMonth }
            val expenses7     = expenses.filter { it.date >= from7 }

            // ── Badge logic ────────────────────────────────────────────
            val badges = mutableListOf<Badge>()

            // 1. First Steps — logged first expense
            badges.add(Badge(
                emoji   = "🌱",
                title   = "First Steps",
                desc    = "Logged your first expense",
                earned  = expenses.isNotEmpty()
            ))

            // 2. Consistent Logger — logged expenses in last 7 days
            val uniqueDays7 = expenses7.map { it.date }.toSet().size
            badges.add(Badge(
                emoji   = "📅",
                title   = "Consistent Logger",
                desc    = "Logged expenses on $uniqueDays7/7 days this week",
                earned  = uniqueDays7 >= 5,
                progress = uniqueDays7,
                target  = 7
            ))

            // 3. Budget Guardian — stayed within monthly budget this month
            val withinBudget = monthlyBudget > 0 && totalMonth <= monthlyBudget
            badges.add(Badge(
                emoji   = "🛡️",
                title   = "Budget Guardian",
                desc    = if (monthlyBudget > 0) "Stayed within R %.0f monthly budget".format(monthlyBudget)
                else "Set a monthly budget to unlock",
                earned  = withinBudget
            ))

            // 4. Category Master — created 3+ categories
            badges.add(Badge(
                emoji   = "🗂️",
                title   = "Category Master",
                desc    = "Created ${categories.size} expense categories",
                earned  = categories.size >= 3,
                progress = categories.size,
                target  = 3
            ))

            // 5. Expense Explorer — logged 10+ total expenses
            badges.add(Badge(
                emoji   = "🔍",
                title   = "Expense Explorer",
                desc    = "Logged ${expenses.size} total expenses",
                earned  = expenses.size >= 10,
                progress = expenses.size,
                target  = 10
            ))

            // 6. Savings Hero — spent 20% less than budget this month
            val savingsGoal = monthlyBudget > 0 && totalMonth <= (monthlyBudget * 0.8)
            badges.add(Badge(
                emoji   = "💰",
                title   = "Savings Hero",
                desc    = "Spent 20% under budget this month",
                earned  = savingsGoal
            ))

            // 7. Streak Master — logged expenses 30 days in a row (simplified: 20+ unique days in 30)
            val uniqueDays30 = expenses.filter { it.date >= from30 }.map { it.date }.toSet().size
            badges.add(Badge(
                emoji   = "🔥",
                title   = "Streak Master",
                desc    = "Active on $uniqueDays30 days in the last 30",
                earned  = uniqueDays30 >= 20,
                progress = uniqueDays30,
                target  = 20
            ))

            // 8. Big Spender Awareness — caught spending over budget
            val overBudget = monthlyBudget > 0 && totalMonth > monthlyBudget
            badges.add(Badge(
                emoji   = "⚠️",
                title   = "Budget Alert",
                desc    = if (overBudget) "Over budget by R %.2f — review spending!".format(totalMonth - monthlyBudget)
                else "Stay within budget to avoid this",
                earned  = false,   // this is a warning badge, not an achievement
                isWarning = overBudget
            ))

            // Score: count earned badges (excluding warning)
            val score = badges.count { it.earned && !it.isWarning }
            val total = badges.count { !it.isWarning }

            withContext(Dispatchers.Main) {
                if (!isAdded) return@withContext

                // Score header
                view.findViewById<TextView>(R.id.tvGamificationScore)
                    ?.text = "$score / $total"
                view.findViewById<TextView>(R.id.tvGamificationSubtitle)
                    ?.text = when {
                    score == total -> "🏆 All badges earned! You're a budget master!"
                    score >= total / 2 -> "⭐ Great progress! Keep it up!"
                    else -> "💪 Keep logging to earn more badges!"
                }

                val progressBar = view.findViewById<ProgressBar>(R.id.progressGamification)
                progressBar?.max     = total
                progressBar?.progress = score

                // Render badges
                val container = view.findViewById<LinearLayout>(R.id.llBadgesContainer)
                container?.removeAllViews()

                badges.forEach { badge ->
                    val row = layoutInflater.inflate(R.layout.item_badge, container, false)
                    row.findViewById<TextView>(R.id.tvBadgeEmoji)?.text  = badge.emoji
                    row.findViewById<TextView>(R.id.tvBadgeTitle)?.text  = badge.title
                    row.findViewById<TextView>(R.id.tvBadgeDesc)?.text   = badge.desc

                    val statusIcon = row.findViewById<TextView>(R.id.tvBadgeStatus)
                    val progressBarBadge = row.findViewById<ProgressBar>(R.id.progressBadge)

                    when {
                        badge.isWarning -> {
                            statusIcon?.text = "⚠️"
                            row.alpha = if (badge.isWarning) 1f else 0.4f
                            row.setBackgroundColor(Color.parseColor("#1A3300"))
                        }
                        badge.earned -> {
                            statusIcon?.text = "✅"
                            row.alpha = 1f
                        }
                        else -> {
                            statusIcon?.text = "🔒"
                            row.alpha = 0.5f
                        }
                    }

                    // Show mini progress bar if badge has a target
                    if (badge.target != null && badge.progress != null && !badge.earned) {
                        progressBarBadge?.visibility = View.VISIBLE
                        progressBarBadge?.max      = badge.target
                        progressBarBadge?.progress = badge.progress.coerceAtMost(badge.target)
                    } else {
                        progressBarBadge?.visibility = View.GONE
                    }

                    container?.addView(row)
                }
            }
        }
    }

    data class Badge(
        val emoji: String,
        val title: String,
        val desc: String,
        val earned: Boolean,
        val progress: Int? = null,
        val target: Int? = null,
        val isWarning: Boolean = false
    )
}