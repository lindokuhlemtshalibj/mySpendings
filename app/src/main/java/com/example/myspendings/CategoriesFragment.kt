package com.example.myspendings

import android.app.AlertDialog
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class CategoriesFragment : Fragment() {

    private val CATEGORY_COLORS = listOf(
        "#00D4AA", "#FF6348", "#A29BFE", "#74B9FF",
        "#FD79A8", "#55EFC4", "#FDCB6E", "#636E72"
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_categories, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ── IDs from fragment_categories.xml ─────────────────────────
        val btnAddCategory = view.findViewById<MaterialButton>(R.id.btnAddCategory)
        val rvCategories   = view.findViewById<RecyclerView>(R.id.rvCategories)
        rvCategories.layoutManager = GridLayoutManager(requireContext(), 2)

        btnAddCategory.setOnClickListener { showAddCategoryDialog() }
        loadCategories(view)
    }

    override fun onResume() {
        super.onResume()
        view?.let { loadCategories(it) }
    }

    private fun loadCategories(view: View) {
        val userId = SessionManager.getUserId(requireContext())
        val db     = AppDatabase.getDatabase(requireContext())
        val sdf    = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val cal    = Calendar.getInstance()
        val toDate = sdf.format(cal.time)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        val fromDate = sdf.format(cal.time)

        CoroutineScope(Dispatchers.IO).launch {
            val cats = db.categoryDao().getAllByUser(userId)
            val catWithCount = cats.map { cat ->
                val expCount = db.expenseDao().getByCategoryAndUser(userId, cat.id).size
                val total = db.expenseDao().getTotalByCategory(userId, cat.id, fromDate, toDate)
                Triple(cat, expCount, total)
            }

            withContext(Dispatchers.Main) {
                if (!isAdded) return@withContext
                val tvNoCats = view.findViewById<TextView>(R.id.tvNoCategories)
                val rv       = view.findViewById<RecyclerView>(R.id.rvCategories)

                if (cats.isEmpty()) {
                    tvNoCats?.visibility = View.VISIBLE
                    rv?.visibility       = View.GONE
                } else {
                    tvNoCats?.visibility = View.GONE
                    rv?.visibility       = View.VISIBLE
                    rv?.adapter = CategoryAdapter(
                        catWithCount, CATEGORY_COLORS,
                        onDelete = { cat -> showDeleteDialog(cat) },
                        onEdit   = { cat -> showEditDialog(cat) }
                    )
                }
            }
        }
    }

    private fun showAddCategoryDialog() {
        // Uses dialog_add_category.xml  — only has etCategoryName + btnCreateCategory
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_add_category, null)
        val etName    = dialogView.findViewById<TextInputEditText>(R.id.etCategoryName)
        val btnCreate = dialogView.findViewById<MaterialButton>(R.id.btnCreateCategory)

        val dialog = AlertDialog.Builder(requireContext(), R.style.DarkDialog)
            .setView(dialogView).create()

        btnCreate.setOnClickListener {
            val name = etName.text.toString().trim()
            if (name.isEmpty()) {
                etName.error = getString(R.string.error_category_name_empty)
                return@setOnClickListener
            }
            val userId = SessionManager.getUserId(requireContext())
            CoroutineScope(Dispatchers.IO).launch {
                AppDatabase.getDatabase(requireContext())
                    .categoryDao().insert(Category(userId = userId, name = name))
                withContext(Dispatchers.Main) {
                    dialog.dismiss()
                    Toast.makeText(requireContext(), getString(R.string.success_category_created), Toast.LENGTH_SHORT).show()
                    view?.let { loadCategories(it) }
                }
            }
        }
        dialog.show()
    }

    private fun showEditDialog(category: Category) {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_add_category, null)
        val etName    = dialogView.findViewById<TextInputEditText>(R.id.etCategoryName)
        val btnCreate = dialogView.findViewById<MaterialButton>(R.id.btnCreateCategory)
        etName.setText(category.name)
        btnCreate.text = "Update Category"

        val dialog = AlertDialog.Builder(requireContext(), R.style.DarkDialog)
            .setView(dialogView).create()

        btnCreate.setOnClickListener {
            val newName = etName.text.toString().trim()
            if (newName.isEmpty()) {
                etName.error = getString(R.string.error_category_name_empty)
                return@setOnClickListener
            }
            CoroutineScope(Dispatchers.IO).launch {
                AppDatabase.getDatabase(requireContext())
                    .categoryDao().update(category.copy(name = newName))
                withContext(Dispatchers.Main) {
                    dialog.dismiss()
                    view?.let { loadCategories(it) }
                }
            }
        }
        dialog.show()
    }

    private fun showDeleteDialog(category: Category) {
        AlertDialog.Builder(requireContext(), R.style.DarkDialog)
            .setTitle("Delete Category")
            .setMessage(getString(R.string.confirm_delete_category))
            .setPositiveButton(getString(R.string.btn_delete)) { _, _ ->
                CoroutineScope(Dispatchers.IO).launch {
                    AppDatabase.getDatabase(requireContext())
                        .categoryDao().delete(category)
                    withContext(Dispatchers.Main) {
                        view?.let { loadCategories(it) }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}

// ─────────────────────────────────────────────
//  ADAPTER for item_category_card.xml
// ─────────────────────────────────────────────
class CategoryAdapter(
    private val items: List<Triple<Category, Int, Double>>,
    private val colors: List<String>,
    private val onDelete: (Category) -> Unit,
    private val onEdit: (Category) -> Unit
) : RecyclerView.Adapter<CategoryAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvName    : TextView   = view.findViewById(R.id.tvCategoryName)
        val tvCount   : TextView   = view.findViewById(R.id.tvExpenseCount)
        val tvTotal   : TextView   = view.findViewById(R.id.tvCategoryTotal)
        val ivIcon    : ImageView  = view.findViewById(R.id.ivCategoryIcon)
        val ivEdit    : ImageView  = view.findViewById(R.id.ivEditCategory)
        val ivDelete  : ImageView  = view.findViewById(R.id.ivDeleteCategory)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_category_card, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val (cat, count, total) = items[position]
        val colorHex = colors[(position) % colors.size]
        val colorInt = android.graphics.Color.parseColor(colorHex)

        holder.tvName.text    = cat.name
        holder.tvCount.text   = "$count expenses"
        holder.tvTotal.text   = "R %.0f".format(total)
        holder.ivIcon.setColorFilter(colorInt)
        holder.ivEdit.setOnClickListener   { onEdit(cat) }
        holder.ivDelete.setOnClickListener { onDelete(cat) }
    }

    override fun getItemCount() = items.size
}
