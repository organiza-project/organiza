package org.organiza.linux

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess

fun main(args: Array<String>) {
    when (val command = CliArgs.parse(args.toList())) {
        CliCommand.Help -> println(CliArgs.HELP)
        CliCommand.RunDaemon -> try {
            Daemon.start()
        } catch (failure: StorageUnavailable) {
            Daemon.log(failure.message.orEmpty())
            exitProcess(EXIT_CONFIG)
        }
        is CliCommand.Send -> exitProcess(print(ControlClient.send(command.request)))
        is CliCommand.ImportFile -> {
            val text = try {
                Files.readString(Path.of(command.path))
            } catch (_: IOException) {
                println("Could not read ${command.path}")
                exitProcess(1)
            }
            exitProcess(print(ControlClient.send(Request.Import(text))))
        }
    }
}

private fun print(reply: Reply): Int {
    println(reply.message)
    reply.status.forEach { println("  $it") }
    return if (reply.ok) 0 else 1
}

// sysexits.h EX_CONFIG, so systemd's logs show a configuration problem rather than a crash.
private const val EXIT_CONFIG = 78
