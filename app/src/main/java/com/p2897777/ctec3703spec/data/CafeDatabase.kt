package com.p2897777.ctec3703spec.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Local SQLite database for the Smart Café customer app.
 *
 * Schema (all money in pence):
 *  users(id, name, email UNIQUE, password_hash, password_salt, created_at)
 *  products(id, name, description, category, price_pence, available, emoji, tags)
 *  cart_items(user_id, product_id, quantity)              PK(user_id, product_id)
 *  orders(id, user_id, status, total_pence, note, created_at, updated_at, queue_ahead, predicted_wait_s)
 *  order_items(id, order_id, product_id, product_name, unit_price_pence, quantity)
 *  order_status_history(id, order_id, status, changed_at)
 *  feedback(id, user_id, order_id, product_id, rating 1-5, comment, created_at)
 *
 * All SQL uses bound parameters ("?") so user input is never concatenated into queries.
 */
class CafeDatabase private constructor(context: Context, name: String?) :
    SQLiteOpenHelper(context.applicationContext, name, null, VERSION) {

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE users (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                email TEXT NOT NULL UNIQUE,
                password_hash TEXT NOT NULL,
                password_salt TEXT NOT NULL,
                created_at INTEGER NOT NULL)"""
        )
        db.execSQL(
            """CREATE TABLE products (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                description TEXT NOT NULL,
                category TEXT NOT NULL,
                price_pence INTEGER NOT NULL CHECK (price_pence >= 0),
                available INTEGER NOT NULL DEFAULT 1,
                emoji TEXT NOT NULL DEFAULT '',
                tags TEXT NOT NULL DEFAULT '')"""
        )
        db.execSQL(
            """CREATE TABLE cart_items (
                user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                product_id INTEGER NOT NULL REFERENCES products(id) ON DELETE CASCADE,
                quantity INTEGER NOT NULL CHECK (quantity > 0),
                PRIMARY KEY (user_id, product_id))"""
        )
        db.execSQL(
            """CREATE TABLE orders (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                status TEXT NOT NULL,
                total_pence INTEGER NOT NULL CHECK (total_pence >= 0),
                note TEXT NOT NULL DEFAULT '',
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                queue_ahead INTEGER NOT NULL DEFAULT 0,
                predicted_wait_s INTEGER)"""
        )
        db.execSQL(
            """CREATE TABLE order_items (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                order_id INTEGER NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
                product_id INTEGER NOT NULL,
                product_name TEXT NOT NULL,
                unit_price_pence INTEGER NOT NULL,
                quantity INTEGER NOT NULL CHECK (quantity > 0))"""
        )
        db.execSQL(
            """CREATE TABLE order_status_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                order_id INTEGER NOT NULL REFERENCES orders(id) ON DELETE CASCADE,
                status TEXT NOT NULL,
                changed_at INTEGER NOT NULL)"""
        )
        db.execSQL(
            """CREATE TABLE feedback (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                user_id INTEGER NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                order_id INTEGER REFERENCES orders(id) ON DELETE SET NULL,
                product_id INTEGER REFERENCES products(id) ON DELETE SET NULL,
                rating INTEGER NOT NULL CHECK (rating BETWEEN 1 AND 5),
                comment TEXT NOT NULL DEFAULT '',
                created_at INTEGER NOT NULL)"""
        )
        db.execSQL("CREATE INDEX idx_orders_user ON orders(user_id, created_at)")
        db.execSQL("CREATE INDEX idx_orders_status ON orders(status)")
        db.execSQL("CREATE INDEX idx_order_items_order ON order_items(order_id)")
        seedMenu(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Each step upgrades one version, so a user on any old version is migrated in order
        // without losing their account, cart or order history.
        if (oldVersion < 2) {
            // v2: pickup-time prediction needs the queue size at checkout and the prediction made
            db.execSQL("ALTER TABLE orders ADD COLUMN queue_ahead INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE orders ADD COLUMN predicted_wait_s INTEGER")
        }
    }

    private fun seedMenu(db: SQLiteDatabase) {
        SeedMenu.items.forEach { p ->
            val cv = ContentValues().apply {
                put("name", p.name)
                put("description", p.description)
                put("category", p.category.name)
                put("price_pence", p.pricePence)
                put("available", if (p.available) 1 else 0)
                put("emoji", p.emoji)
                put("tags", p.tags.joinToString(","))
            }
            db.insertOrThrow("products", null, cv)
        }
    }

    companion object {
        const val NAME = "smart_cafe.db"
        const val VERSION = 2

        @Volatile private var instance: CafeDatabase? = null

        fun get(context: Context): CafeDatabase =
            instance ?: synchronized(this) {
                instance ?: CafeDatabase(context, NAME).also { instance = it }
            }

        /** Fresh in-memory database, used by instrumented tests. */
        fun inMemory(context: Context): CafeDatabase = CafeDatabase(context, null)
    }
}
