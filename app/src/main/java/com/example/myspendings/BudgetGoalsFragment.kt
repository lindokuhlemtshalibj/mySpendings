package com.example.myspendings

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.gridlayout.widget.GridLayout
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.*

class BudgetGoalsFragment : Fragment() {

    // We store references to the category input fields so we can read them on Save
    private val categoryInputMap = mutableMapOf<Int, TextInputEditText>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_budget_goals, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadGoals(view)
    }

    private fun loadGoals(view: View) {
        val userId       = SessionManager.getUserId(requireContext())
        val db           = AppDatabase.getDatabase(requireContext())
        val savedMonthly = SessionManager.getMonthlyBudget(requireContext())

        // ── IDs from fragment_budget_goals.xml ────────────────────────
        val etMonthlyLimit      = view.findViewById<TextInputEditText>(R.id.etMonthlyLimit)
        val gridCategoryLimits  = view.findViewById<GridLayout>(R.id.gridCategoryLimits)
        val tvTotalCatBudgets   = view.findViewById<TextView>(R.id.tvTotalCategoryBudgets)
        val tvMonthlyBudgetSum  = view.findViewById<TextView>(R.id.tvMonthlyBudgetSummary)
        val tvDifference        = view.findViewById<TextView>(R.id.tvDifference)
        val btnSaveGoals        = view.findViewById<MaterialButton>(R.id.btnSaveGoals)

        if (savedMonthly > 0) etMonthlyLimit.setText("%.0f".format(savedMonthly))

        CoroutineScope(Dispatchers.IO).launch {
            val categories = db.categoryDao().getAllByUser(userId)

            withContext(Dispatchers.Main) {
                if (!isAdded) return@withContext
                gridCategoryLimits.removeAllViews()
                categoryInputMap.clear()

                categories.forEach { cat ->
                    // Build a small TextInputLayout + EditText for each category
                    val col = LayoutInflater.from(requireContext())
                        .inflate(R.layout.item_budget_goal_input, gridCategoryLimits, false) as ViewGroup

                    val label = col.findViewById<TextView>(R.id.tvBudgetCatLabel)
                    val et    = col.findViewById<TextInputEditText>(R.id.etBudgetCatLimit)
                    label.text = cat.name
                    if (cat.budgetLimit > 0) et.setText("%.0f".format(cat.budgetLimit))
                    categoryInputMap[cat.id] = et

                    val params = GridLayout.LayoutParams().apply {
                        width  = 0
                        columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                        setMargins(8, 8, 8, 8)
                    }
                    col.layoutParams = params
                    gridCategoryLimits.addView(col)
                }

                // Live update of summary row as monthly limit changes
                etMonthlyLimit.addTextChangedListener(object : android.text.TextWatcher {
                    override fun afterTextChanged(s: android.text.Editable?) { updateSummary(view) }
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                })
            }
        }

        btnSaveGoals.setOnClickListener {
            val monthlyText = etMonthlyLimit.text.toString().trim()
            val monthly     = monthlyText.toDoubleOrNull() ?: 0.0
            SessionManager.saveMonthlyBudget(requireContext(), monthly)

            CoroutineScope(Dispatchers.IO).launch {
                val db2 = AppDatabase.getDatabase(requireContext())
                categoryInputMap.forEach { (catId, et) ->
                    val limit = et.text.toString().toDoubleOrNull() ?: 0.0
                    val cat   = db2.categoryDao().getById(catId)
                    if (cat != null) db2.categoryDao().update(cat.copy(budgetLimit = limit))
                }
                withContext(Dispatchers.Main) {
                    if (!isAdded) return@withContext
                    Toast.makeText(requireContext(), getString(R.string.success_goals_saved), Toast.LENGTH_SHORT).show()
                    updateSummary(view)
                }
            }
        }
    }

    private fun updateSummary(view: View) {
        val monthly = view.findViewById<TextInputEditText>(R.id.etMonthlyLimit)
            ?.text.toString().toDoubleOrNull() ?: 0.0
        val totalCat = categoryInputMap.values.sumOf {
            it.text.toString().toDoubleOrNull() ?: 0.0
        }
        val diff = monthly - totalCat

        view.findViewById<TextView>(R.id.tvTotalCategoryBudgets)?.text = "R %.2f".format(totalCat)
        view.findViewById<TextView>(R.id.tvMonthlyBudgetSummary)?.text = "R %.2f".format(monthly)
        val tvDiff = view.findViewById<TextView>(R.id.tvDifference)
        if (diff >= 0) {
            tvDiff?.text = "R %.2f under".format(diff)
            tvDiff?.setTextColor(requireContext().getColor(R.color.status_green))
        } else {
            tvDiff?.text = "R %.2f over".format(-diff)
            tvDiff?.setTextColor(requireContext().getColor(R.color.status_red))
        }
    }
}
