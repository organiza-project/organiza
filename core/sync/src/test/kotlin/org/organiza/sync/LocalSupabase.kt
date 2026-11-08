package org.organiza.sync

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import java.net.InetSocketAddress
import java.sql.SQLException
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

/**
 * A self-hosted Supabase in miniature, on loopback: the password endpoints of Supabase Auth and
 * PostgREST's `/rest/v1/rpc` route, over a real PostgreSQL running `supabase/migrations`. The SQL
 * functions see `auth.uid()` from the bearer token, as they do behind PostgREST.
 */
class LocalSupabase : AutoCloseable {
    private val postgres = EmbeddedPostgres.start().also { pg ->
        pg.postgresDatabase.connection.use { db ->
            db.createStatement().use { statement ->
                statement.execute(SupabaseStub.SQL)
                SupabaseStub.migrations().forEach { statement.execute(it.readText()) }
            }
        }
    }

    private class User(val id: UUID, val email: String, val password: String)

    private val users = ConcurrentHashMap<String, User>()
    private val refreshTokens = ConcurrentHashMap<String, User>()
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { exchange -> exchange.use(::handle) }
        start()
    }

    val url: String get() = "http://127.0.0.1:${server.address.port}"
    val anonKey: String = ANON_KEY

    /** Every relay row as the database holds it, for checking what the backend can see. */
    fun messages(): List<Pair<UUID, ByteArray>> = postgres.postgresDatabase.connection.use { db ->
        db.createStatement().use { statement ->
            statement.executeQuery("select sender, data from public.arena_message order by cursor").use { rows ->
                buildList { while (rows.next()) add(rows.getObject(1) as UUID to rows.getBytes(2)) }
            }
        }
    }

    fun userId(email: String): UUID? = users[email]?.id

    override fun close() {
        server.stop(0)
        postgres.close()
    }

    private fun handle(exchange: HttpExchange) {
        val body = exchange.requestBody.readBytes().decodeToString()
        if (exchange.requestHeaders.getFirst("apikey") != anonKey) return exchange.reply(401, """{"message":"Invalid API key"}""")
        val path = exchange.requestURI.path
        val grant = exchange.requestURI.query?.substringAfter("grant_type=", "")
        val json = Json.parseToJsonElement(body.ifEmpty { "{}" }).jsonObject
        when {
            path == "/auth/v1/signup" -> signUp(exchange, json)
            path == "/auth/v1/token" && grant == "password" -> password(exchange, json)
            path == "/auth/v1/token" && grant == "refresh_token" -> {
                val user = refreshTokens[json.text("refresh_token")]
                if (user == null) exchange.reply(400, """{"error_code":"invalid_grant"}""") else exchange.reply(200, session(user))
            }
            path.startsWith("/rest/v1/rpc/") -> rpc(exchange, path.removePrefix("/rest/v1/rpc/"), json)
            else -> exchange.reply(404, "{}")
        }
    }

    private fun signUp(exchange: HttpExchange, json: JsonObject) {
        val email = json.text("email")
        if (users.containsKey(email)) return exchange.reply(422, """{"error_code":"user_already_exists"}""")
        val user = User(UUID.randomUUID(), email, json.text("password"))
        postgres.postgresDatabase.connection.use { db -> db.createStatement().use { it.execute("insert into auth.users (id) values ('${user.id}')") } }
        users[email] = user
        // Signed in straight away, as with email confirmation off.
        exchange.reply(200, session(user))
    }

    private fun password(exchange: HttpExchange, json: JsonObject) {
        val user = users[json.text("email")]
        if (user == null || user.password != json.text("password")) return exchange.reply(400, """{"error_code":"invalid_credentials"}""")
        exchange.reply(200, session(user))
    }

    private fun session(user: User): String {
        val refresh = UUID.randomUUID().toString()
        refreshTokens[refresh] = user
        return buildJsonObject {
            put("access_token", jwt("""{"sub":"${user.id}","role":"authenticated"}"""))
            put("refresh_token", refresh)
            put("expires_in", 3600)
            put("user", buildJsonObject { put("id", user.id.toString()); put("email", user.email) })
        }.toString()
    }

    private fun rpc(exchange: HttpExchange, function: String, args: JsonObject) {
        val bearer = exchange.requestHeaders.getFirst("Authorization")?.removePrefix("Bearer ")
        val sub = bearer?.split('.')?.getOrNull(1)?.let { payload ->
            (Json.parseToJsonElement(Base64.getUrlDecoder().decode(payload).decodeToString()) as JsonObject).text("sub").ifEmpty { null }
        }
        try {
            postgres.postgresDatabase.connection.use { db ->
                db.createStatement().use { statement ->
                    statement.execute("set role ${if (sub == null) "anon" else "authenticated"}")
                    statement.execute("select set_config('request.jwt.claim.sub', '${sub.orEmpty()}', false)")
                }
                val reply = when (function) {
                    "arena_append" -> db.prepareStatement("select public.arena_append(?, ?)").use { call ->
                        call.setString(1, args.text("p_mailbox"))
                        call.setString(2, args.text("p_data"))
                        call.executeQuery().use { it.next(); it.getLong(1).toString() }
                    }
                    "arena_fetch" -> db.prepareStatement("select cursor, data from public.arena_fetch(?, ?)").use { call ->
                        call.setString(1, args.text("p_mailbox"))
                        call.setLong(2, args["p_after"]!!.jsonPrimitive.long)
                        call.executeQuery().use { rows ->
                            buildJsonArray {
                                while (rows.next()) add(buildJsonObject { put("cursor", rows.getLong(1)); put("data", rows.getString(2)) })
                            }.toString()
                        }
                    }
                    else -> return exchange.reply(404, """{"message":"Could not find the function"}""")
                }
                exchange.reply(200, reply)
            }
        } catch (failure: SQLException) {
            // PostgREST answers a raised exception with a 4xx and its message.
            val status = if (failure.sqlState == "28000" || failure.sqlState == "42501") 401 else 400
            exchange.reply(status, buildJsonObject { put("message", failure.message) }.toString())
        }
    }

    private fun JsonObject.text(name: String): String = (this[name] as? JsonPrimitive)?.content.orEmpty()

    private fun HttpExchange.reply(status: Int, body: String) {
        val bytes = body.toByteArray()
        responseHeaders.add("Content-Type", "application/json")
        sendResponseHeaders(status, bytes.size.toLong())
        responseBody.write(bytes)
    }
}
