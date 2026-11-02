package org.organiza.linux

sealed interface CliCommand {
    data class Send(val request: Request) : CliCommand

    data class ImportFile(val path: String) : CliCommand

    data object RunDaemon : CliCommand

    data object Help : CliCommand
}

object CliArgs {
    fun parse(args: List<String>): CliCommand = when (args.firstOrNull()) {
        "status" -> CliCommand.Send(Request.Status)
        "stop" -> CliCommand.Send(Request.Stop)
        "daemon" -> CliCommand.RunDaemon
        "import" -> args.getOrNull(1)?.let(CliCommand::ImportFile) ?: CliCommand.Help
        "start" -> parseStart(args.drop(1)) ?: CliCommand.Help
        "rule" -> parseRule(args.drop(1)) ?: CliCommand.Help
        else -> CliCommand.Help
    }

    private fun parseRule(options: List<String>): CliCommand? {
        val start = parseOptions(options, allowTag = true) ?: return null
        return CliCommand.Send(Request.Rule(start.tag ?: return null, start.processes, start.domains, start.strict))
    }

    private fun parseStart(options: List<String>): CliCommand? {
        val parsed = parseOptions(options, allowTag = false) ?: return null
        return CliCommand.Send(Request.Start(parsed.minutes ?: return null, parsed.processes, parsed.domains, parsed.strict))
    }

    private class Options(val minutes: Int?, val tag: String?, val processes: List<String>, val domains: List<String>, val strict: Boolean)

    private fun parseOptions(options: List<String>, allowTag: Boolean): Options? {
        var minutes: Int? = null
        var tag: String? = null
        var processes = emptyList<String>()
        var domains = emptyList<String>()
        var strict = false
        var index = 0
        while (index < options.size) {
            val value = options.getOrNull(index + 1)
            when (options[index]) {
                "--minutes" -> minutes = value?.toIntOrNull() ?: return null
                "--tag" -> if (allowTag) tag = value?.removePrefix("#") ?: return null else return null
                "--block" -> processes = value?.split(',')?.filter(String::isNotBlank) ?: return null
                "--site" -> domains = value?.split(',')?.filter(String::isNotBlank) ?: return null
                "--strict" -> {
                    strict = true
                    index++
                    continue
                }
                else -> return null
            }
            index += 2
        }
        return Options(minutes, tag, processes, domains, strict)
    }

    const val HELP = """organiza: focus enforcement for Linux

  organiza status
  organiza start --minutes 45 [--block steam,discord] [--site reddit.com,x.com] [--strict]
  organiza stop
  organiza import calendar.ics
  organiza rule --tag deepwork [--block steam] [--site reddit.com] [--strict]

A rule says what calendar events tagged #deepwork block. Import the calendar after saving it.

--block takes program names or Flatpak app IDs. Organiza never ends root or system processes,
shells, sudo, ssh, login or the desktop itself.
--site writes the domain and its www. and m. forms to /etc/hosts. A hosts file cannot block
every subdomain, and browsers using DNS-over-HTTPS bypass it.
A strict session cannot be stopped from here. Anyone with sudo can still stop the service;
the stop is recorded as a tamper event when it comes back."""
}
