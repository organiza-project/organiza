import 'dart:async';
import 'dart:io';

import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';
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

  static const String prefTrackingConsentKey = 'app_tracking_consent_granted';

  bool _isTrackingEnabled = false;
  bool _isPolling = false;

  bool get isTrackingEnabled => _isTrackingEnabled;
  bool get isPolling => _isPolling;

  AppUsageService([this._db]) {
    initTrackingConsent();
  }

  Future<void> initTrackingConsent([SharedPreferences? prefs]) async {
    try {
      final p = prefs ?? await SharedPreferences.getInstance();
      _isTrackingEnabled = p.getBool(prefTrackingConsentKey) ?? false;
      if (_isTrackingEnabled) {
        _startNativeTracking();
      }
    } catch (_) {}
  }

  Future<void> enableTracking([SharedPreferences? prefs]) async {
    _isTrackingEnabled = true;
    try {
      final p = prefs ?? await SharedPreferences.getInstance();
      await p.setBool(prefTrackingConsentKey, true);
    } catch (_) {}
    _startNativeTracking();
  }

  Future<void> disableTracking([SharedPreferences? prefs]) async {
    _isTrackingEnabled = false;
    _linuxPollTimer?.cancel();
    _linuxPollTimer = null;
    try {
      final p = prefs ?? await SharedPreferences.getInstance();
      await p.setBool(prefTrackingConsentKey, false);
    } catch (_) {}
  }

  Future<int> purgeUsageHistory() async {
    return await _db?.purgeUsageHistory() ?? 0;
  }

  void dispose() {
    _linuxPollTimer?.cancel();
    _linuxPollTimer = null;
  }

  String _formatTodayDate() {
    final d = DateTime.now();
    final year = d.year.toString().padLeft(4, '0');
    final month = d.month.toString().padLeft(2, '0');
    final day = d.day.toString().padLeft(2, '0');
    return '$year-$month-$day';
  }

  void _startNativeTracking() {
    if (!_isTrackingEnabled) return;
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
    if (!Platform.isLinux || _db == null || !_isTrackingEnabled) return;
    if (_isPolling) return;

    _isPolling = true;
    final stopwatch = Stopwatch()..start();
    try {
      String? detectedProcess;

      // 1. Try xdotool (works on X11 and XWayland) with timeout
      try {
        final res = await Process.run(
          'xdotool',
          ['getactivewindow', 'getwindowpid'],
          environment: {'DISPLAY': Platform.environment['DISPLAY'] ?? ':0'},
        ).timeout(const Duration(seconds: 2));
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
        // xdotool not available or timed out; fallback to Wayland DBus
      }

      // 2. GNOME Wayland unprivileged session DBus fallback with timeout
      if (detectedProcess == null || detectedProcess.isEmpty) {
        try {
          final dbusRes = await Process.run('gdbus', [
            'call',
            '--session',
            '--dest',
            'org.gnome.Shell',
            '--object-path',
            '/org/gnome/Shell',
            '--method',
            'org.gnome.Shell.Eval',
            "global.display.focus_window ? global.display.focus_window.get_wm_class() : ''",
          ]).timeout(const Duration(seconds: 2));
          if (dbusRes.exitCode == 0) {
            final out = dbusRes.stdout.toString();
            final match = RegExp(r"'([^']*)'").firstMatch(out);
            if (match != null && match.group(1) != null) {
              final wmClass = match.group(1)!.trim().toLowerCase().replaceAll('"', '');
              if (wmClass.isNotEmpty && wmClass != 'null') {
                detectedProcess = wmClass;
              }
            }
          }
        } catch (_) {}
      }

      // 3. Fallback inspection via /proc
      if (detectedProcess == null || detectedProcess.isEmpty) {
        detectedProcess = await _inspectLinuxForegroundFallback();
      }

      if (detectedProcess != null && detectedProcess.isNotEmpty) {
        if (_systemProcessBlacklist.contains(detectedProcess)) {
          return;
        }

        final cleanName = getCleanAppName(detectedProcess);
        final todayStr = _formatTodayDate();
        await _db.incrementDailyUsage(detectedProcess, cleanName, todayStr, 5);

        if (kDebugMode) {
          debugPrint(
              'Organiza: Linux tracked "$cleanName" ($detectedProcess) in ${stopwatch.elapsedMilliseconds}ms');
        }
      }
    } catch (e) {
      if (kDebugMode) {
        debugPrint('Organiza: Linux usage poll notice: $e');
      }
    } finally {
      _isPolling = false;
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
    if (!Platform.isAndroid || db == null || !_isTrackingEnabled) return;

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
          await db.recordDailyUsageSnapshot(entry.key, cleanName, todayStr, seconds);
        }
      }
    } catch (e) {
      if (kDebugMode) {
        debugPrint('Organiza: Android UsageStats sync notice: $e');
      }
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

  static final Map<String, String?> _iconPathCache = {};

  /// Lookup app icon path from unprivileged desktop files in Linux
  Future<String?> findLinuxAppIcon(String processOrWmClass) async {
    if (!Platform.isLinux) return null;
    final key = processOrWmClass.toLowerCase();
    if (_iconPathCache.containsKey(key)) return _iconPathCache[key];

    try {
      final appDirs = [
        Directory('/usr/share/applications'),
        Directory('${Platform.environment['HOME']}/.local/share/applications'),
      ];

      for (final dir in appDirs) {
        if (!await dir.exists()) continue;
        await for (final file in dir.list()) {
          if (file is File && file.path.endsWith('.desktop')) {
            final name = file.uri.pathSegments.last.toLowerCase();
            if (name.contains(key)) {
              final lines = await file.readAsLines();
              for (final line in lines) {
                if (line.startsWith('Icon=')) {
                  final iconName = line.substring(5).trim();
                  if (File(iconName).existsSync()) {
                    _iconPathCache[key] = iconName;
                    return iconName;
                  }
                  final pixmap = File('/usr/share/pixmaps/$iconName.png');
                  if (await pixmap.exists()) {
                    _iconPathCache[key] = pixmap.path;
                    return pixmap.path;
                  }
                  final pixmapSvg = File('/usr/share/pixmaps/$iconName.svg');
                  if (await pixmapSvg.exists()) {
                    _iconPathCache[key] = pixmapSvg.path;
                    return pixmapSvg.path;
                  }
                }
              }
            }
          }
        }
      }
    } catch (_) {}
    _iconPathCache[key] = null;
    return null;
  }

  /// Weekly aggregation: past 7 days daily minutes list [Mon..Sun or 7 days ago..today]
  Future<List<int>> getWeeklyUsageHistory() async {
    final db = _db;
    if (db == null) return List.filled(7, 0);

    final now = DateTime.now();
    final List<int> dailyTotals = [];

    for (int i = 6; i >= 0; i--) {
      final day = now.subtract(Duration(days: i));
      final dateStr =
          '${day.year.toString().padLeft(4, '0')}-${day.month.toString().padLeft(2, '0')}-${day.day.toString().padLeft(2, '0')}';
      final map = await db.getTodayUsageMap(dateStr);
      final totalSec = map.values.fold<int>(0, (sum, sec) => sum + sec);
      dailyTotals.add(totalSec ~/ 60);
    }
    return dailyTotals;
  }

  /// Hourly breakdown: divides active screen time into 6 time intervals (0-4h, 4-8h, 8-12h, 12-16h, 16-20h, 20-24h)
  Future<List<int>> getHourlyUsageDistribution() async {
    final db = _db;
    if (db == null) return List.filled(6, 0);

    final todayStr = _formatTodayDate();
    final todayMap = await db.getTodayUsageMap(todayStr);
    final totalSec = todayMap.values.fold<int>(0, (sum, sec) => sum + sec);
    final totalMin = totalSec ~/ 60;

    if (totalMin == 0) return List.filled(6, 0);

    final nowHour = DateTime.now().hour;
    final currentSlot = (nowHour ~/ 4).clamp(0, 5);

    final result = List.filled(6, 0);
    if (currentSlot == 0) {
      result[0] = totalMin;
    } else {
      final perSlot = totalMin ~/ (currentSlot + 1);
      final remainder = totalMin % (currentSlot + 1);
      for (int i = 0; i <= currentSlot; i++) {
        result[i] = perSlot + (i == currentSlot ? remainder : 0);
      }
    }
    return result;
  }
}
