package com.aivn.meow.data

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClaudeBotTest {

    @Test
    fun graphqlBotLoginIsClaude() {
        assertTrue(isClaudeBot("claude", "Bot"))
        assertTrue(isClaudeBot("claude"))
    }

    @Test
    fun restBotLoginIsClaude() {
        assertTrue(isClaudeBot("claude[bot]"))
        assertTrue(isClaudeBot("Claude[bot]", "Bot"))
    }

    @Test
    fun otherClaudePrefixedBotIsClaude() {
        assertTrue(isClaudeBot("claude-code", "Bot"))
        assertTrue(isClaudeBot("ClaudeReviewer", "Bot"))
    }

    @Test
    fun humanWithClaudePrefixIsNotBot() {
        assertFalse(isClaudeBot("claude-kim", "User"))
        assertFalse(isClaudeBot("claudette"))
    }

    @Test
    fun otherBotsAndHumansAreNotClaude() {
        assertFalse(isClaudeBot("dependabot[bot]"))
        assertFalse(isClaudeBot("github-actions", "Bot"))
        assertFalse(isClaudeBot("hyunjine", "User"))
        assertFalse(isClaudeBot(null))
        assertFalse(isClaudeBot(""))
    }
}
