package com.p2897777.ctec3703spec

import com.p2897777.ctec3703spec.util.LoginRateLimiter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginRateLimiterTest {

    private var now = 1_000_000L
    private val limiter = LoginRateLimiter(LoginRateLimiter.MemoryStore(), { now })

    @Test fun fourFailuresDoNotLock() {
        repeat(4) { assertFalse(limiter.recordFailure("a@b.com")) }
        assertEquals(0L, limiter.remainingLockMillis("a@b.com"))
    }

    @Test fun fifthFailureLocksForSixtySeconds() {
        repeat(4) { limiter.recordFailure("a@b.com") }
        assertTrue(limiter.recordFailure("a@b.com"))
        assertEquals(60_000L, limiter.remainingLockMillis("a@b.com"))
    }

    @Test fun lockExpiresAfterSixtySeconds() {
        repeat(5) { limiter.recordFailure("a@b.com") }
        now += 59_999
        assertEquals(1L, limiter.remainingLockMillis("a@b.com"))
        now += 1
        assertEquals(0L, limiter.remainingLockMillis("a@b.com"))
    }

    @Test fun countStartsAgainAfterLockExpires() {
        repeat(5) { limiter.recordFailure("a@b.com") }
        now += 60_000
        assertFalse(limiter.recordFailure("a@b.com"))
        assertEquals(0L, limiter.remainingLockMillis("a@b.com"))
    }

    @Test fun successResetsTheCount() {
        repeat(4) { limiter.recordFailure("a@b.com") }
        limiter.recordSuccess("a@b.com")
        repeat(4) { assertFalse(limiter.recordFailure("a@b.com")) }
    }

    @Test fun emailCaseAndSpacesDoNotDodgeTheLock() {
        repeat(5) { limiter.recordFailure(" A@B.com ") }
        assertTrue(limiter.remainingLockMillis("a@b.com") > 0)
    }

    @Test fun otherEmailsAreNotAffected() {
        repeat(5) { limiter.recordFailure("a@b.com") }
        assertEquals(0L, limiter.remainingLockMillis("c@d.com"))
    }
}
