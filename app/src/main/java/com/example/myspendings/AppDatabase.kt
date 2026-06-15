package com.example.myspendings

import android.content.Context
import androidx.room.*

// ─────────────────────────────────────────────
//  ENTITIES (database tables)
// ─────────────────────────────────────────────

@Entity(tableName = "users")
data class User(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val username: String,
    val password: String = "",
    val firebaseUid: String = ""
)

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: Int,
    val name: String,
    val budgetLimit: Double = 0.0
)

@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userId: Int,
    val categoryId: Int,
    val amount: Double,
    val description: String,
    val date: String,                       // stored as "yyyy-MM-dd"
    val receiptImagePath: String? = null
)

// ─────────────────────────────────────────────
//  DAOs (database access)
// ─────────────────────────────────────────────

@Dao
interface UserDao {
    @Insert
    suspend fun insert(user: User): Long

    @Query("SELECT * FROM users WHERE username = :username AND password = :password LIMIT 1")
    suspend fun login(username: String, password: String): User?

    @Query("SELECT * FROM users WHERE username = :username LIMIT 1")
    suspend fun findByUsername(username: String): User?

    @Query("SELECT * FROM users WHERE firebaseUid = :firebaseUid LIMIT 1")
    suspend fun findByFirebaseUid(firebaseUid: String): User?
}

@Dao
interface CategoryDao {
    @Insert
    suspend fun insert(category: Category)

    @Update
    suspend fun update(category: Category)

    @Delete
    suspend fun delete(category: Category)

    @Query("SELECT * FROM categories WHERE userId = :userId ORDER BY name ASC")
    suspend fun getAllByUser(userId: Int): List<Category>

    @Query("SELECT * FROM categories WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): Category?
}

@Dao
interface ExpenseDao {
    @Insert
    suspend fun insert(expense: Expense)

    @Update
    suspend fun update(expense: Expense)

    @Delete
    suspend fun delete(expense: Expense)

    @Query("SELECT * FROM expenses WHERE userId = :userId ORDER BY date DESC")
    suspend fun getAllByUser(userId: Int): List<Expense>

    @Query("SELECT * FROM expenses WHERE userId = :userId AND date BETWEEN :from AND :to ORDER BY date DESC")
    suspend fun getByDateRange(userId: Int, from: String, to: String): List<Expense>

    @Query("SELECT * FROM expenses WHERE userId = :userId AND categoryId = :categoryId ORDER BY date DESC")
    suspend fun getByCategoryAndUser(userId: Int, categoryId: Int): List<Expense>

    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM expenses WHERE userId = :userId AND date BETWEEN :from AND :to")
    suspend fun getTotalSpending(userId: Int, from: String, to: String): Double

    @Query("SELECT COALESCE(SUM(amount), 0.0) FROM expenses WHERE userId = :userId AND categoryId = :categoryId AND date BETWEEN :from AND :to")
    suspend fun getTotalByCategory(userId: Int, categoryId: Int, from: String, to: String): Double
}

// ─────────────────────────────────────────────
//  DATABASE
// ─────────────────────────────────────────────

@Database(entities = [User::class, Category::class, Expense::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun categoryDao(): CategoryDao
    abstract fun expenseDao(): ExpenseDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "myspendings_db"
                )
                    .fallbackToDestructiveMigration() // OK for dev: schema changed to add firebaseUid
                    .build().also { INSTANCE = it }
            }
        }
    }
}