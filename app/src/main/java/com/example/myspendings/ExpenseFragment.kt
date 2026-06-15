package com.example.myspendings

import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class ExpensesFragment : Fragment() {

    private val CATEGORY_COLORS = listOf(
        "#00D4AA", "#FF6348", "#A29BFE", "#74B9FF",
        "#FD79A8", "#55EFC4", "#FDCB6E", "#636E72"
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_expense, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ── IDs from fragment_expense.xml ────────────────────────────
        val spinnerPeriod  = view.findViewById<AutoCompleteTextView>(R.id.spinnerPeriod)
        val tvTotalSpent   = view.findViewById<TextView>(R.id.tvTotalSpent)
        val tvExpenseCount = view.findViewById<TextView>(R.id.tvExpenseCount)
        val tvNoExpenses   = view.findViewById<TextView>(R.id.tvNoExpenses)
        val rvExpenses     = view.findViewById<RecyclerView>(R.id.rvExpenses)
        val btnAddExpense  = view.findViewById<MaterialButton>(R.id.btnAddExpense)

        rvExpenses.layoutManager = LinearLayoutManager(requireContext())

        // Period dropdown options
        val periods = listOf("This Month", "Last 30 Days", "Last 3 Months", "All Time")
        spinnerPeriod.setAdapter(
            ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, periods)
        )
        spinnerPeriod.setOnItemClickListener { _, _, pos, _ ->
            loadExpenses(view, pos)
        }

        btnAddExpense.setOnClickListener {
            requireActivity().let { activity ->
                if (activity is MainActivity) {
                    activity.onNavigationItemSelected(
                        activity.findViewById<com.google.android.material.navigation.NavigationView>(R.id.navigationView).menu.findItem(R.id.nav_add_expense)
                    )
                }
            }
        }

        loadExpenses(view, 0) // default: This Month
    }

    override fun onResume() {
        super.onResume()
        view?.let { loadExpenses(it, 0) }
    }

    private fun loadExpenses(view: View, periodIndex: Int) {
        val userId = SessionManager.getUserId(requireContext())
        val db = AppDatabase.getDatabase(requireContext())

        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val cal = Calendar.getInstance()
        val toDate = sdf.format(cal.time)
        val fromDate = when (periodIndex) {
            0 -> { cal.set(Calendar.DAY_OF_MONTH, 1); sdf.format(cal.time) }   // This Month
            1 -> { cal.add(Calendar.DAY_OF_YEAR, -30); sdf.format(cal.time) }  // Last 30 days
            2 -> { cal.add(Calendar.MONTH, -3); sdf.format(cal.time) }         // Last 3 months
            else -> "2000-01-01"                                                 // All time
        }

        CoroutineScope(Dispatchers.IO).launch {
            val expenses   = db.expenseDao().getByDateRange(userId, fromDate, toDate)
            val categories = db.categoryDao().getAllByUser(userId)
            val catMap     = categories.associateBy { it.id }
            val total      = expenses.sumOf { it.amount }

            withContext(Dispatchers.Main) {
                if (!isAdded) return@withContext

                view.findViewById<TextView>(R.id.tvTotalSpent)
                    ?.text = "R %.2f".format(total)
                view.findViewById<TextView>(R.id.tvExpenseCount)
                    ?.text = "${expenses.size} expenses in this period"

                val tvNoExp = view.findViewById<TextView>(R.id.tvNoExpenses)
                val rv      = view.findViewById<RecyclerView>(R.id.rvExpenses)

                if (expenses.isEmpty()) {
                    tvNoExp?.visibility = View.VISIBLE
                    rv?.visibility = View.GONE
                } else {
                    tvNoExp?.visibility = View.GONE
                    rv?.visibility = View.VISIBLE
                    rv?.adapter = ExpenseAdapter(expenses, catMap, CATEGORY_COLORS)
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
//  ADAPTER for item_expense.xml
// ─────────────────────────────────────────────
class ExpenseAdapter(
    private val expenses: List<Expense>,
    private val categoryMap: Map<Int, Category>,
    private val colors: List<String>
) : RecyclerView.Adapter<ExpenseAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvDesc     : TextView = view.findViewById(R.id.tvExpenseDescription)
        val tvDate     : TextView = view.findViewById(R.id.tvExpenseDate)
        val tvAmount   : TextView = view.findViewById(R.id.tvExpenseAmount)
        val chipCat    : Chip     = view.findViewById(R.id.chipCategory)
        val ivPhoto    : android.widget.ImageView = view.findViewById(R.id.ivPhotoIndicator)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_expense, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val expense = expenses[position]
        val cat     = categoryMap[expense.categoryId]

        holder.tvDesc.text   = expense.description
        holder.tvDate.text   = expense.date
        holder.tvAmount.text = "-R %.2f".format(expense.amount)

        if (cat != null) {
            holder.chipCat.text = cat.name
            val colorIdx = (cat.id - 1) % colors.size
            holder.chipCat.chipBackgroundColor =
                android.content.res.ColorStateList.valueOf(
                    android.graphics.Color.parseColor(colors[colorIdx])
                )
        }

        // Show camera icon only if receipt photo exists
        holder.ivPhoto.visibility =
            if (!expense.receiptImagePath.isNullOrEmpty()) View.VISIBLE else View.GONE
    }

    override fun getItemCount() = expenses.size
}
