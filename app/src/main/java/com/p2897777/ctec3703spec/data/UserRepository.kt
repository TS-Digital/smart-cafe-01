package com.p2897777.ctec3703spec.data
import android.content.ContentValues
import android.database.sqlite.SQLiteConstraintException
import com.p2897777.ctec3703spec.model.User
import com.p2897777.ctec3703spec.util.LoginRateLimiter
import com.p2897777.ctec3703spec.util.PasswordHasher
import com.p2897777.ctec3703spec.util.Validators

sealed class AuthResult {
    data class Success(val user: User) : AuthResult()
    data class Failure(val message: String) : AuthResult()
}

class UserRepository(
    private val db: CafeDatabase,
    private val clock: () -> Long = System::currentTimeMillis,
    private val limiter: LoginRateLimiter = LoginRateLimiter(LoginRateLimiter.MemoryStore(), clock)
) {

    fun register(name: String, email: String, password: String): AuthResult {
        // Validate again here, not only in the UI, so the data layer cannot be fed bad data
        Validators.name(name)?.let { return AuthResult.Failure(it) }
        Validators.email(email)?.let { return AuthResult.Failure(it) }
        Validators.password(password)?.let { return AuthResult.Failure(it) }

        val cleanEmail = Validators.normaliseEmail(email)
        if (findByEmail(cleanEmail) != null) {
            return AuthResult.Failure("An account with this email already exists")
        }
        val hashed = PasswordHasher.hash(password)
        val now = clock()
        val cv = ContentValues().apply {
            put("name", name.trim())
            put("email", cleanEmail)
            put("password_hash", hashed.hash)
            put("password_salt", hashed.salt)
            put("created_at", now)
        }
        return try {
            val id = db.writableDatabase.insertOrThrow("users", null, cv)
            AuthResult.Success(User(id, name.trim(), cleanEmail, now))
        } catch (e: SQLiteConstraintException) {
            AuthResult.Failure("An account with this email already exists")
        }
    }

    fun login(email: String, password: String): AuthResult {
        if (email.isBlank() || password.isEmpty()) {
            return AuthResult.Failure("Please enter your email and password")
        }
        val cleanEmail = Validators.normaliseEmail(email)
        // Check the lock before doing any password work
        val waitMs = limiter.remainingLockMillis(cleanEmail)
        if (waitMs > 0) return AuthResult.Failure(lockedMessage(waitMs))
        db.readableDatabase.rawQuery(
            "SELECT id, name, email, password_hash, password_salt, created_at FROM users WHERE email = ?",
            arrayOf(cleanEmail)
        ).use { c ->
            // Same message for unknown email and wrong password, so attackers can't tell which emails exist
            val ok = c.moveToFirst() && PasswordHasher.verify(password, c.getString(3), c.getString(4))
            if (!ok) {
                val nowLocked = limiter.recordFailure(cleanEmail)
                return AuthResult.Failure(if (nowLocked) lockedMessage(limiter.lockMillis) else INVALID_LOGIN)
            }
            limiter.recordSuccess(cleanEmail)
            return AuthResult.Success(User(c.getLong(0), c.getString(1), c.getString(2), c.getLong(5)))
        }
    }

    fun findById(id: Long): User? =
        db.readableDatabase.rawQuery(
            "SELECT id, name, email, created_at FROM users WHERE id = ?", arrayOf(id.toString())
        ).use { c -> if (c.moveToFirst()) User(c.getLong(0), c.getString(1), c.getString(2), c.getLong(3)) else null }

    private fun findByEmail(email: String): Long? =
        db.readableDatabase.rawQuery("SELECT id FROM users WHERE email = ?", arrayOf(email))
            .use { c -> if (c.moveToFirst()) c.getLong(0) else null }

    /** GDPR-style right to erasure: deletes the account and, via cascades, all their data. */
    fun deleteAccount(userId: Long): Boolean =
        db.writableDatabase.delete("users", "id = ?", arrayOf(userId.toString())) > 0

    private fun lockedMessage(waitMs: Long): String {
        val secs = ((waitMs + 999) / 1000).coerceAtLeast(1)
        return "Too many failed attempts. Please try again in $secs seconds."
    }

    companion object {
        const val INVALID_LOGIN = "Incorrect email or password"
    }
}

