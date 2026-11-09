package com.p2897777.ctec3703spec.data

import android.database.Cursor
import com.p2897777.ctec3703spec.model.Category
import com.p2897777.ctec3703spec.model.Product

class ProductRepository(private val db: CafeDatabase) {

    /**
     * Menu query with optional text search and category filter.
     * The LIKE pattern is a bound argument, and % and _ in user text are escaped.
     */
    fun search(query: String = "", category: Category? = null, availableOnly: Boolean = false): List<Product> {
        val where = mutableListOf<String>()
        val args = mutableListOf<String>()
        val q = query.trim()
        if (q.isNotEmpty()) {
            where += "(name LIKE ? ESCAPE '\\' OR description LIKE ? ESCAPE '\\')"
            val pattern = "%" + escapeLike(q) + "%"
            args += pattern
            args += pattern
        }
        if (category != null) {
            where += "category = ?"
            args += category.name
        }
        if (availableOnly) where += "available = 1"
        val sql = buildString {
            append("SELECT $COLUMNS FROM products")
            if (where.isNotEmpty()) append(" WHERE ").append(where.joinToString(" AND "))
            append(" ORDER BY available DESC, ").append(CATEGORY_ORDER).append(", name")
        }
        return db.readableDatabase.rawQuery(sql, args.toTypedArray()).use { c ->
            buildList { while (c.moveToNext()) add(c.toProduct()) }
        }
    }

    fun all(): List<Product> = search()

    fun findById(id: Long): Product? =
        db.readableDatabase.rawQuery("SELECT $COLUMNS FROM products WHERE id = ?", arrayOf(id.toString()))
            .use { c -> if (c.moveToFirst()) c.toProduct() else null }

    /** Average star rating per product from customer feedback. */
    fun averageRatings(): Map<Long, Pair<Double, Int>> =
        db.readableDatabase.rawQuery(
            "SELECT product_id, AVG(rating), COUNT(*) FROM feedback WHERE product_id IS NOT NULL GROUP BY product_id",
            null
        ).use { c ->
            buildMap { while (c.moveToNext()) put(c.getLong(0), c.getDouble(1) to c.getInt(2)) }
        }

    private fun escapeLike(s: String) = s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    companion object {
        const val COLUMNS = "id, name, description, category, price_pence, available, emoji, tags"

        /** Sorts the menu in the café's own order (drinks first) rather than alphabetically by category. */
        private val CATEGORY_ORDER = "CASE category " +
            Category.entries.joinToString(" ") { "WHEN '${it.name}' THEN ${it.ordinal}" } + " ELSE 99 END"
    }
}

/** Maps a row selected with [ProductRepository.COLUMNS] (in that order) to a Product. */
internal fun Cursor.toProduct() = Product(
    id = getLong(0),
    name = getString(1),
    description = getString(2),
    category = Category.fromName(getString(3)),
    pricePence = getInt(4),
    available = getInt(5) == 1,
    emoji = getString(6),
    tags = getString(7).split(",").filter { it.isNotBlank() }.toSet()
)
