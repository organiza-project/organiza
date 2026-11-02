package org.organiza.linux

import java.io.IOException
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.channels.ServerSocketChannel
import java.nio.channels.SocketChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import org.organiza.calendar.IcsCalendar
import org.organiza.domain.DomainName
import org.organiza.domain.Parsed
import org.organiza.domain.ProcessName
import org.organiza.domain.port.Capability
import org.organiza.domain.port.Clock
import org.organiza.enforcement.SessionPolicy
import org.organiza.presentation.Format
import org.organiza.scheduler.ManualRequest
import org.organiza.scheduler.RuleEdit
import org.organiza.scheduler.SessionScheduler
import org.organiza.scheduler.StartResult
import org.organiza.scheduler.StopResult
import org.organiza.storage.Database
import org.organiza.storage.MigrationResult
import org.organiza.storage.Migrations
import org.organiza.storage.SessionState
import org.organiza.storage.SessionStore
import org.organiza.storage.StoredRule
import org.organiza.storage.TamperStore

/** Where the daemon keeps things. systemd creates these directories (packaging/linux). */
data class DaemonPaths(
    val database: Path = Path.of("/var/lib/organiza/organiza.db"),
    val heartbeat: Path = Path.of("/var/lib/organiza/heartbeat"),
    val socket: Path = Path.of("/run/organiza/control.sock"),
    val hosts: Path = Path.of("/etc/hosts"),
    val proc: Path = Path.of("/proc"),
)

/** The root service: schedules sessions and enforces them on this machine (ADR-0013). */
class Daemon(private val paths: DaemonPaths, private val db: Database, private val clock: Clock) {
    private val lock = Any()
    private val scheduler = SessionScheduler(db, clock)
    private val sessions = SessionStore(db)
    private val tamper = TamperStore(db)
    private val heartbeat = Heartbeat(paths.heartbeat)
    private val processes = ProcessTable(paths.proc)
    private val guard = ProcessGuard(ProcessHandle.current().pid().toInt())
    private val terminated = mutableMapOf<Int, Instant>()
    private var policy = SessionPolicy.IDLE
    private var lastReconcile = Instant.EPOCH
    private var lastBeat = Instant.EPOCH

    fun run() {
        synchronized(lock) {
            recordGap()
            reconcile()
        }
        Thread(::serve, "organiza-control").apply { isDaemon = true }.start()
        while (true) {
            synchronized(lock) { tick() }
            Thread.sleep(TICK.toMillis())
        }
    }

    /** A silence in the heartbeat during an active session means someone stopped the daemon. */
    private fun recordGap() {
        val gapStart = heartbeat.gapSinceLastBeat(clock.now()) ?: return
        val active = sessions.inState(SessionState.ACTIVE).firstOrNull { it.start <= gapStart } ?: return
        tamper.record(active.id, Capability.DAEMON, gapStart)
        log("recorded a tamper event: the daemon was not running from $gapStart")
    }

    private fun tick() {
        val now = clock.now()
        if (Duration.between(lastReconcile, now) >= RECONCILE) reconcile()
        enforceProcesses(now)
        if (Duration.between(lastBeat, now) >= Heartbeat.INTERVAL) {
            heartbeat.beat(now)
            lastBeat = now
        }
    }

    private fun reconcile() {
        policy = scheduler.reconcile().policy
        lastReconcile = clock.now()
        try {
            HostsFile.apply(paths.hosts, policy.blockList.domains)
        } catch (failure: IOException) {
            log("could not update ${paths.hosts}: ${failure.javaClass.simpleName}")
        }
    }

    /** SIGTERM first, then SIGKILL for anything still running two seconds later. */
    private fun enforceProcesses(now: Instant) {
        val targets = guard.targets(processes.list(), policy.blockList.processes).toSet()
        terminated.keys.retainAll(targets)
        for (pid in targets) {
            val handle = ProcessHandle.of(pid.toLong()).orElse(null) ?: continue
            val since = terminated[pid]
            when {
                since == null -> {
                    handle.destroy()
                    terminated[pid] = now
                    log("ended blocked process $pid")
                }
                Duration.between(since, now) >= KILL_AFTER -> handle.destroyForcibly()
            }
        }
    }

    private fun serve() {
        Files.deleteIfExists(paths.socket)
        val server = ServerSocketChannel.open(StandardProtocolFamily.UNIX)
        server.bind(UnixDomainSocketAddress.of(paths.socket))
        // The directory and this mode keep the socket to root and the organiza group.
        Files.setPosixFilePermissions(paths.socket, PosixFilePermissions.fromString("rw-rw----"))
        while (true) {
            val client = server.accept()
            try {
                client.use(::handle)
            } catch (failure: IOException) {
                log("control connection failed: ${failure.javaClass.simpleName}")
            }
        }
    }

    private fun handle(client: SocketChannel) {
        val line = readLine(client) ?: return
        val reply = ControlProtocol.parseRequest(line)?.let { request -> synchronized(lock) { answer(request) } }
            ?: Reply(false, "That request was not understood.")
        client.write(ByteBuffer.wrap((ControlProtocol.encode(reply) + "\n").toByteArray()))
    }

    private fun readLine(client: SocketChannel): String? {
        val reader = Channels.newInputStream(client).bufferedReader()
        val builder = StringBuilder()
        while (builder.length <= MAX_REQUEST) {
            val next = reader.read()
            if (next == -1 || next == '\n'.code) break
            builder.append(next.toChar())
        }
        return builder.toString().takeIf { it.isNotEmpty() && builder.length <= MAX_REQUEST }
    }

    private fun answer(request: Request): Reply = when (request) {
        Request.Status -> Reply(true, "Status", status())
        Request.Stop -> when (scheduler.stopActive()) {
            StopResult.Stopped -> Reply(true, "Session stopped").also { reconcile() }
            StopResult.RefusedStrict -> Reply(false, "A strict session is running and cannot be stopped early.")
        }
        is Request.Start -> start(request)
        is Request.Import -> import(request.ics)
        is Request.Rule -> saveRule(request)
    }

    private fun saveRule(request: Request.Rule): Reply {
        val processes = request.processes.map(ProcessName::parse)
        val domains = request.domains.map(DomainName::parse)
        val bad = (request.processes.zip(processes) + request.domains.zip(domains))
            .filter { (_, parsed) -> parsed is Parsed.Invalid }.map { it.first }
        if (bad.isNotEmpty() || request.tag.isBlank()) return Reply(false, "Could not read: ${(bad + request.tag).joinToString()}")
        val rule = StoredRule(
            id = "tag:" + request.tag.lowercase(),
            name = request.tag,
            calendarTag = "#" + request.tag,
            strict = request.strict,
            blockShortForm = false,
            enabled = true,
            packages = emptySet(),
            domains = domains.map { (it as Parsed.Valid).value }.toSet(),
            processes = processes.map { (it as Parsed.Valid).value }.toSet(),
        )
        return when (scheduler.saveRule(rule)) {
            RuleEdit.Saved -> Reply(true, "Saved the rule for #${request.tag}")
            RuleEdit.RefusedStrict -> Reply(false, "That rule is in a running strict session and cannot be changed.")
        }
    }

    private fun start(request: Request.Start): Reply {
        val processes = request.processes.map(ProcessName::parse)
        val domains = request.domains.map(DomainName::parse)
        val rejected = (request.processes.zip(processes) + request.domains.zip(domains))
            .filter { (_, parsed) -> parsed is Parsed.Invalid }.map { it.first }
        if (rejected.isNotEmpty()) return Reply(false, "Could not read: ${rejected.joinToString()}")
        val result = scheduler.startManual(
            ManualRequest(
                packages = emptySet(),
                domains = domains.map { (it as Parsed.Valid).value }.toSet(),
                strict = request.strict,
                processes = processes.map { (it as Parsed.Valid).value }.toSet(),
            ),
            Duration.ofMinutes(request.minutes.toLong()),
        )
        return when (result) {
            StartResult.Started -> Reply(true, "Session started", status()).also { reconcile() }
            StartResult.InvalidDuration -> Reply(false, "That length is not allowed. Strict sessions run up to 24 hours.")
        }
    }

    private fun import(ics: String): Reply {
        val now = clock.now()
        val until = now.plus(CALENDAR_WINDOW)
        val read = IcsCalendar.read(ics, now, until, ZoneId.systemDefault())
        val sync = scheduler.syncCalendar(read.occurrences, now, until)
        reconcile()
        return Reply(
            true,
            "Calendar: ${sync.added} added, ${sync.updated} updated, ${sync.removed} removed, " +
                "${read.issues.size} events not understood",
            status(),
        )
    }

    private fun status(): List<String> {
        val active = sessions.inState(SessionState.ACTIVE)
        val until = active.maxOfOrNull { it.end }
        val list = policy.blockList
        return listOfNotNull(
            if (until == null) "No session is running" else
                "Session running for ${Format.duration(Duration.between(clock.now(), until))}" +
                    (if (policy.strict) ", strict" else ""),
            "Blocking ${Format.count(list.processes.size, "program", "programs")} and " +
                Format.count(list.domains.size, "site", "sites"),
            sessions.inState(SessionState.SCHEDULED).minByOrNull { it.start }?.let { "Next session starts ${it.start}" },
            "Tamper events: ${tamper.all().size}",
        )
    }

    companion object {
        private val TICK = Duration.ofSeconds(1)
        private val RECONCILE = Duration.ofSeconds(5)
        private val KILL_AFTER = Duration.ofSeconds(2)
        private val CALENDAR_WINDOW = Duration.ofDays(60)
        private const val MAX_REQUEST = 1_000_000

        fun log(message: String) = System.err.println("organiza: $message")

        /** Opens storage from the systemd credential and runs until stopped. */
        fun start(paths: DaemonPaths = DaemonPaths()) {
            val key = DaemonKey.load(System.getenv("CREDENTIALS_DIRECTORY")?.let(Path::of))
            val db = EncryptedDatabase.open(paths.database, key)
            key.fill(0)
            if (Migrations.migrate(db) is MigrationResult.TooNew) {
                throw StorageUnavailable("The database is from a newer Organiza. It was left untouched.")
            }
            Daemon(paths, db, Clock(Instant::now)).run()
        }
    }
}

/** The `organiza` command's side of the control socket. */
object ControlClient {
    fun send(request: Request, socket: Path = DaemonPaths().socket): Reply = try {
        SocketChannel.open(StandardProtocolFamily.UNIX).use { channel ->
            channel.connect(UnixDomainSocketAddress.of(socket))
            channel.write(ByteBuffer.wrap((ControlProtocol.encode(request) + "\n").toByteArray()))
            val line = Channels.newInputStream(channel).bufferedReader().readLine()
            line?.let(ControlProtocol::parseReply) ?: Reply(false, "The daemon sent no answer.")
        }
    } catch (failure: IOException) {
        val denied = failure is java.nio.file.AccessDeniedException || failure.message?.contains("Permission denied") == true
        Reply(
            false,
            if (denied) "Permission denied. Add yourself to the organiza group, then log in again."
            else "The organiza daemon is not running. Start it with: sudo systemctl start organiza",
        )
    }
}
