package org.organiza.sync

import java.util.Base64

/** An unsigned JWT with [payload], enough for the key checks, which never verify signatures. */
fun jwt(payload: String): String {
    val encoder = Base64.getUrlEncoder().withoutPadding()
    val header = encoder.encodeToString("""{"alg":"HS256","typ":"JWT"}""".toByteArray())
    return "$header.${encoder.encodeToString(payload.toByteArray())}.c2lnbmF0dXJl"
}

val ANON_KEY = jwt("""{"iss":"supabase","role":"anon","exp":4102444800}""")
