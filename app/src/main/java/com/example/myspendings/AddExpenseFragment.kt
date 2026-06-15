package com.example.myspendings

import android.app.Activity
import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class AddExpenseFragment : Fragment() {

    private var selectedDate = ""
    private var selectedCategoryId = -1
    private var receiptImageUri: String? = null
    private var categoryList: List<Category> = emptyList()
    private val IMAGE_PICK_CODE = 1001

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.fragment_add_expense, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ── IDs from fragment_add_expense.xml ─────────────────────────
        val etAmount        = view.findViewById<TextInputEditText>(R.id.etAmount)
        val etDescription   = view.findViewById<TextInputEditText>(R.id.etDescription)
        val tvSelectedDate  = view.findViewById<TextView>(R.id.tvSelectedDate)
        val cardDatePicker  = view.findViewById<View>(R.id.cardDatePicker)
        val spinnerCategory = view.findViewById<AutoCompleteTextView>(R.id.spinnerCategory)
        val btnChooseFile   = view.findViewById<MaterialButton>(R.id.btnChooseFile)
        val btnSaveExpense  = view.findViewById<MaterialButton>(R.id.btnSaveExpense)
        val btnCancel       = view.findViewById<MaterialButton>(R.id.btnCancel)
        val ivReceiptPreview= view.findViewById<ImageView>(R.id.ivReceiptPreview)
        val llUploadPrompt  = view.findViewById<View>(R.id.llUploadPrompt)

        // Default date = today
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        selectedDate = sdf.format(Date())
        tvSelectedDate.text = selectedDate

        // Load categories into the dropdown
        val userId = SessionManager.getUserId(requireContext())
        val db = AppDatabase.getDatabase(requireContext())

        CoroutineScope(Dispatchers.IO).launch {
            categoryList = db.categoryDao().getAllByUser(userId)
            withContext(Dispatchers.Main) {
                if (!isAdded) return@withContext
                val names = categoryList.map { it.name }
                val adapter = ArrayAdapter(
                    requireContext(),
                    android.R.layout.simple_dropdown_item_1line,
                    names
                )
                spinnerCategory.setAdapter(adapter)
                spinnerCategory.setOnItemClickListener { _, _, pos, _ ->
                    selectedCategoryId = categoryList[pos].id
                }
            }
        }

        // Date picker
        cardDatePicker.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(requireContext(), { _, y, m, d ->
                selectedDate = "%04d-%02d-%02d".format(y, m + 1, d)
                tvSelectedDate.text = selectedDate
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        // Choose receipt photo
        btnChooseFile.setOnClickListener { pickImage() }

        // Cancel → go back
        btnCancel.setOnClickListener {
            requireActivity().onBackPressed()
        }

        // Save expense
        btnSaveExpense.setOnClickListener {
            val amountText = etAmount.text.toString().trim()
            val description = etDescription.text.toString().trim()
            val amount = amountText.toDoubleOrNull()

            if (amount == null || amount <= 0) {
                etAmount.error = getString(R.string.error_amount_invalid)
                return@setOnClickListener
            }
            if (selectedCategoryId == -1) {
                Toast.makeText(requireContext(), getString(R.string.error_category_empty), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (description.isEmpty()) {
                etDescription.error = getString(R.string.error_description_empty)
                return@setOnClickListener
            }

            CoroutineScope(Dispatchers.IO).launch {
                db.expenseDao().insert(
                    Expense(
                        userId = userId,
                        categoryId = selectedCategoryId,
                        amount = amount,
                        description = description,
                        date = selectedDate,
                        receiptImagePath = receiptImageUri
                    )
                )
                withContext(Dispatchers.Main) {
                    if (!isAdded) return@withContext
                    Toast.makeText(requireContext(), getString(R.string.success_expense_saved), Toast.LENGTH_SHORT).show()
                    // Reset form
                    etAmount.text?.clear()
                    etDescription.text?.clear()
                    spinnerCategory.text.clear()
                    selectedCategoryId = -1
                    receiptImageUri = null
                    ivReceiptPreview.visibility = View.GONE
                    llUploadPrompt.visibility = View.VISIBLE
                    val sdf2 = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    selectedDate = sdf2.format(Date())
                    tvSelectedDate.text = selectedDate
                }
            }
        }
    }

    private fun pickImage() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(intent, IMAGE_PICK_CODE)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == IMAGE_PICK_CODE && resultCode == Activity.RESULT_OK) {
            val uri: Uri? = data?.data
            if (uri != null) {
                receiptImageUri = uri.toString()
                view?.findViewById<ImageView>(R.id.ivReceiptPreview)?.apply {
                    setImageURI(uri)
                    visibility = View.VISIBLE
                }
                view?.findViewById<View>(R.id.llUploadPrompt)?.visibility = View.GONE
            }
        }
    }
}
