import 'dart:async';
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:usage_stats/usage_stats.dart';

import '../../data/local/app_database.dart';

class AppInfoOption {
  final String name;
  final String packageName;

  const AppInfoOption({
    required this.name,
    required this.packageName,
  });
}

class AppUsageService {
  final AppDatabase? _db;
  Timer? _linuxPollTimer;

  static const Set<String> _systemProcessBlacklist = {
    'gnome-shell',
    'systemd',
    'organiza',
    'bash',
    'sh',
    'zsh',
    'Xorg',
    'mutter',
    'dbus-daemon',
    'pulseaudio',
    'pipewire',
    'wireplumber',
    'systemd-resolved',
    'snapd',
    'sshd',
    'cron',
    'dockerd',
    'gvfsd',
    'polkitd',
    'ibus-daemon',
    'dconf-service',
  };

  static const Map<String, String> _knownAppCatalog = {
    'com.amazon.kindle': 'Amazon Kindle',
    'com.duolingo': 'Duolingo',
    'com.ichi2.anki': 'AnkiDroid',
    'org.coursera.android': 'Coursera',
    'com.audible.application': 'Audible',
    'notion.id': 'Notion',
    'md.obsidian': 'Obsidian',
    'com.spotify.music': 'Spotify',
    'com.getsomeheadspace.android': 'Headspace',
    'com.leetcode.app': 'LeetCode',
    'com.instagram.android': 'Instagram',
    'com.google.android.youtube': 'YouTube',
    'com.zhiliaoapp.musically': 'TikTok',
    'com.twitter.android': 'X / Twitter',
    'com.reddit.frontpage': 'Reddit',
    'chrome': 'Google Chrome',
    'firefox': 'Mozilla Firefox',
    'code': 'VS Code',
    'discord': 'Discord',
    'spotify': 'Spotify',
    'slack': 'Slack',
    'telegram-desktop': 'Telegram',
    'obsidian': 'Obsidian',
  };

  AppUsageService([this._db]) {
    _startNativeTracking();
  }

  void dispose() {
    _linuxPollTimer?.cancel();
  }

  String _formatTodayDate() {
    final d = DateTime.now();
    final year = d.year.toString().padLeft(4, '0');
    final month = d.month.toString().padLeft(2, '0');
    final day = d.day.toString().padLeft(2, '0');
    return '$year-$month-$day';
  }

  void _startNativeTracking() {
    if (Platform.isLinux && _db != null) {
      _startLinuxWindowPoller();
    } else if (Platform.isAndroid && _db != null) {
      syncAndroidUsage();
    }
  }

  /// Linux poller: Runs every 5 seconds to query active window via xdotool / wmctrl / /proc
  void _startLinuxWindowPoller() {
    _linuxPollTimer?.cancel();
    _linuxPollTimer = Timer.periodic(const Duration(seconds: 5), (_) async {
      await pollActiveLinuxWindow();
    });
  }

  /// Polls the active Linux window and increments SQLite usage if a focused app is detected
  Future<void> pollActiveLinuxWindow() async {
    if (!Platform.isLinux || _db == null) return;

    try {
      String? detectedProcess;

      // 1. Try xdotool (works on X11 and XWayland)
      try {
        final res = await Process.run(
          'xdotool',
          ['getactivewindow', 'getwindowpid'],
          environment: {'DISPLAY': Platform.environment['DISPLAY'] ?? ':0'},
        );
        if (res.exitCode == 0) {
          final pidStr = res.stdout.toString().trim();
          final pid = int.tryParse(pidStr);
          if (pid != null && pid > 0) {
            final commFile = File('/proc/$pid/comm');
            if (await commFile.exists()) {
              detectedProcess = (await commFile.readAsString()).trim().toLowerCase();
            }
          }
        }
      } catch (_) {
        // xdotool not available or failed; fallback to window inspection
      }

      // 2. Wayland / fallback window inspection
      if (detectedProcess == null || detectedProcess.isEmpty) {
        detectedProcess = await _inspectLinuxForegroundFallback();
      }

      if (detectedProcess != null && detectedProcess.isNotEmpty) {
        if (_systemProcessBlacklist.contains(detectedProcess)) {
          return;
        }

        final cleanName = getCleanAppName(detectedProcess);
        final todayStr = _formatTodayDate();
        await _db.incrementUsage(detectedProcess, cleanName, todayStr, 5);
      }
    } catch (e) {
      debugPrint('Organiza: Linux usage poll error (gracefully handled): $e');
    }
  }

  Future<String?> _inspectLinuxForegroundFallback() async {
    try {
      // Check for running well-known productivity or tracked tools in /proc
      final procDir = Directory('/proc');
      if (!await procDir.exists()) return null;

      for (final candidate in _knownAppCatalog.keys) {
        if (candidate.contains('.')) continue; // skip android package IDs
        if (await isLinuxProcessRunning(candidate)) {
          return candidate;
        }
      }
    } catch (_) {}
    return null;
  }

  /// Sync Android usage from UsageStatsManager into SQLite
  Future<void> syncAndroidUsage() async {
    final db = _db;
    if (!Platform.isAndroid || db == null) return;

    try {
      final now = DateTime.now();
      final midnight = DateTime(now.year, now.month, now.day);
      final stats = await UsageStats.queryAndAggregateUsageStats(midnight, now);
      final todayStr = _formatTodayDate();

      for (final entry in stats.entries) {
        final ms = int.tryParse(entry.value.totalTimeInForeground ?? '0') ?? 0;
        final seconds = ms ~/ 1000;
        if (seconds > 0) {
          final cleanName = getCleanAppName(entry.key);
          await db.setAppUsage(entry.key, cleanName, todayStr, seconds);
        }
      }
    } catch (e) {
      debugPrint('Organiza: Android UsageStats sync notice: $e');
    }
  }

  Future<bool> checkPermission() async {
    if (!Platform.isAndroid) return true;
    try {
      final isGranted = await UsageStats.checkUsagePermission();
      return isGranted ?? false;
    } catch (_) {
      return false;
    }
  }

  Future<void> requestPermission() async {
    if (!Platform.isAndroid) return;
    try {
      await UsageStats.grantUsagePermission();
    } catch (_) {}
  }

  /// Aggregated usage for today strictly from Drift SQLite
  Future<Map<String, int>> getAggregatedUsageToday() async {
    final todayStr = _formatTodayDate();
    final db = _db;
    if (db != null) {
      if (Platform.isAndroid) {
        await syncAndroidUsage();
      }
      return await db.getTodayUsageMap(todayStr);
    }
    return {};
  }

  Future<int> getForegroundSecondsToday(String packageName) async {
    final todayMap = await getAggregatedUsageToday();
    return todayMap[packageName] ?? 0;
  }

  Future<bool> isLinuxProcessRunning(String packageName) async {
    if (!Platform.isLinux) return false;
    final binaryName = _mapPackageToBinaryName(packageName);
    try {
      final procDir = Directory('/proc');
      if (!await procDir.exists()) return false;
      await for (final entity in procDir.list()) {
        if (entity is Directory) {
          final pidName = entity.path.split('/').last;
          if (int.tryParse(pidName) != null) {
            final commFile = File('${entity.path}/comm');
            if (await commFile.exists()) {
              final comm = (await commFile.readAsString()).trim().toLowerCase();
              if (comm == binaryName.toLowerCase()) {
                return true;
              }
            }
          }
        }
      }
    } catch (_) {}
    return false;
  }

  String _mapPackageToBinaryName(String packageName) {
    if (packageName.contains('spotify')) return 'spotify';
    if (packageName.contains('obsidian')) return 'obsidian';
    if (packageName.contains('chrome')) return 'chrome';
    if (packageName.contains('code')) return 'code';
    if (packageName.contains('discord')) return 'discord';
    if (packageName.contains('slack')) return 'slack';
    return packageName.split('.').last;
  }

  Future<List<AppInfoOption>> getInstalledAppOptions() async {
    if (Platform.isAndroid || Platform.isIOS || Platform.isLinux) {
      return _knownAppCatalog.entries
          .map((e) => AppInfoOption(name: e.value, packageName: e.key))
          .toList();
    }
    return [];
  }

  String getCleanAppName(String? packageName) {
    if (packageName == null || packageName.trim().isEmpty) {
      return '';
    }
    final trimmed = packageName.trim().toLowerCase();
    if (_knownAppCatalog.containsKey(trimmed)) {
      return _knownAppCatalog[trimmed]!;
    }
    for (final entry in _knownAppCatalog.entries) {
      if (entry.key.toLowerCase() == trimmed) {
        return entry.value;
      }
    }
    final parts = trimmed.split('.');
    final lastChunk = parts.isNotEmpty ? parts.last : trimmed;
    if (lastChunk.isEmpty) return trimmed;
    return lastChunk[0].toUpperCase() + lastChunk.substring(1);
  }

  bool isCurfewActive(String? curfewStart, String? curfewEnd) {
    if (curfewStart == null || curfewEnd == null) return false;
    final now = DateTime.now();
    final nowMinutes = now.hour * 60 + now.minute;

    final startParts = curfewStart.split(':');
    final endParts = curfewEnd.split(':');
    if (startParts.length < 2 || endParts.length < 2) return false;

    final startMinutes =
        (int.tryParse(startParts[0]) ?? 0) * 60 + (int.tryParse(startParts[1]) ?? 0);
    final endMinutes =
        (int.tryParse(endParts[0]) ?? 0) * 60 + (int.tryParse(endParts[1]) ?? 0);

    if (startMinutes <= endMinutes) {
      return nowMinutes >= startMinutes && nowMinutes <= endMinutes;
    } else {
      // Overnight curfew (e.g. 22:00 to 07:00)
      return nowMinutes >= startMinutes || nowMinutes <= endMinutes;
    }
  }

  bool isOverDailyCap(int loggedSeconds, int dailyCapMinutes) {
    return (loggedSeconds ~/ 60) >= dailyCapMinutes;
  }
}
