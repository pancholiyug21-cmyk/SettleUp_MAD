package com.example.splitwsie.domain.util

import java.security.SecureRandom
import java.util.UUID

/**
 * Short, shareable group invite codes.
 *
 * Groups previously had an `inviteCode` field that was never populated, so "join a group"
 * required pasting a raw UUID that the app never showed anywhere.
 */
object InviteCodes {

    /** Excludes I, O, 0 and 1, which are the characters people misread when typing a code. */
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private const val CODE_LENGTH = 6

    private val random = SecureRandom()

    fun generate(): String = buildString {
        repeat(CODE_LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
    }

    /** Guaranteed-unique fallback if random generation keeps colliding. */
    fun fallback(): String = UUID.randomUUID().toString().replace("-", "").take(10).uppercase()

    fun normalise(code: String): String = code.trim().uppercase().replace(" ", "")
}
