package com.aivn.meow.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PrBranchesTest {
    @Test
    fun labelJoinsHeadAndBaseWithArrow() {
        assertEquals("feat/#144-list-modal → dev/v1.5.0", PrBranches("feat/#144-list-modal", "dev/v1.5.0").label)
    }

    @Test
    fun ofReturnsNullWhenBranchMissing() {
        assertNull(PrBranches.of(null, "dev"))
        assertNull(PrBranches.of("feat/x", null))
        assertNull(PrBranches.of("", "dev"))
    }

    @Test
    fun ofPrefixesOwnerOnlyForCrossRepository() {
        assertEquals(PrBranches("feat/x", "main"), PrBranches.of("feat/x", "main", isCrossRepository = false, headOwner = "me"))
        assertEquals(PrBranches("fork:feat/x", "main"), PrBranches.of("feat/x", "main", isCrossRepository = true, headOwner = "fork"))
        assertEquals(PrBranches("feat/x", "main"), PrBranches.of("feat/x", "main", isCrossRepository = true, headOwner = null))
    }

    @Test
    fun middleEllipsisKeepsTextWithinMax() {
        assertEquals("feat/#1-a", middleEllipsis("feat/#1-a", 40))
        assertEquals("abcde", middleEllipsis("abcde", 5))
    }

    @Test
    fun middleEllipsisCutsMiddleToExactlyMax() {
        assertEquals("abc…hij", middleEllipsis("abcdefghij", 7))
        assertEquals("abcd…hij", middleEllipsis("abcdefghij", 8))
        val longHead = "feat/#168-show-pr-head-and-base-branches-in-list-and-detail"
        val cut = middleEllipsis(longHead, PrBranches.DEFAULT_MAX_HEAD)
        assertEquals(PrBranches.DEFAULT_MAX_HEAD, cut.length)
        assertEquals(true, cut.startsWith("feat/#168"))
        assertEquals(true, cut.endsWith("detail"))
    }

    @Test
    fun middleEllipsisEdgeCases() {
        assertEquals("", middleEllipsis("abc", 0))
        assertEquals("…", middleEllipsis("abc", 1))
        assertEquals("a…", middleEllipsis("abc", 2))
    }
}
