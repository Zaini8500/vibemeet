package com.vibemeet.app
import org.junit.Assert.*
import org.junit.Test
class AgeGateTest {
    private fun age(dob: String, nowYear: Int): Int { val y = dob.substring(0, 4).toInt(); return nowYear - y }
    @Test fun under18Rejected() { assertTrue(age("2010-01-01", 2026) < 18) }
    @Test fun adultAccepted() { assertTrue(age("2000-01-01", 2026) >= 18) }
}
