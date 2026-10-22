package com.mipresupuestocr.domain

import com.mipresupuestocr.domain.model.AppVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {

    @Test
    fun `parses release tags with and without a v prefix`() {
        assertEquals(AppVersion(1, 2, 0), AppVersion.parse("v1.2.0"))
        assertEquals(AppVersion(1, 2, 0), AppVersion.parse("1.2.0"))
        assertEquals(AppVersion(1, 2, 0), AppVersion.parse("V1.2.0"))
    }

    @Test
    fun `ignores build suffixes such as -debug`() {
        assertEquals(AppVersion(1, 3, 0), AppVersion.parse("1.3.0-debug"))
        assertEquals(AppVersion(1, 3, 0), AppVersion.parse("1.3.0 (42)"))
    }

    @Test
    fun `fills missing components with zero`() {
        assertEquals(AppVersion(2, 0, 0), AppVersion.parse("2"))
        assertEquals(AppVersion(2, 1, 0), AppVersion.parse("2.1"))
    }

    @Test
    fun `rejects text that is not a version`() {
        assertNull(AppVersion.parse(null))
        assertNull(AppVersion.parse(""))
        assertNull(AppVersion.parse("latest"))
    }

    @Test
    fun `orders numerically, not alphabetically`() {
        assertTrue(AppVersion.parse("1.10.0")!! > AppVersion.parse("1.9.0")!!)
        assertTrue(AppVersion.parse("2.0.0")!! > AppVersion.parse("1.99.99")!!)
        assertTrue(AppVersion.parse("1.2.1")!! > AppVersion.parse("1.2.0")!!)
        assertEquals(0, AppVersion.parse("1.2.0")!!.compareTo(AppVersion.parse("v1.2.0")!!))
    }

    @Test
    fun `prints a plain dotted version`() {
        assertEquals("1.2.0", AppVersion(1, 2, 0).toString())
    }
}
