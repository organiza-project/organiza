package org.organiza.linux

import java.nio.file.Files
import java.nio.file.Path

/** Builds a fake /proc tree, so scanning is tested without touching real processes. */
class ProcFixture(val root: Path = Files.createTempDirectory("proc")) {
    fun add(
        pid: Int,
        uid: Int,
        comm: String,
        exe: String? = null,
        argv0: String = comm,
        cgroup: String = "0::/user.slice/user-$uid.slice/session-2.scope",
    ) {
        val dir = Files.createDirectories(root.resolve(pid.toString()))
        Files.writeString(dir.resolve("status"), "Name:\t$comm\nUmask:\t0002\nState:\tS\nUid:\t$uid\t$uid\t$uid\t$uid\n")
        Files.writeString(dir.resolve("comm"), "$comm\n")
        Files.write(dir.resolve("cmdline"), "$argv0\u0000--flag\u0000".toByteArray())
        Files.writeString(dir.resolve("cgroup"), "$cgroup\n")
        if (exe != null) Files.createSymbolicLink(dir.resolve("exe"), Path.of(exe))
    }
}
