import 'dart:io';
import 'package:usage_stats/usage_stats.dart';

class AppInfoOption {
  final String name;
  final String packageName;

  const AppInfoOption({
    required this.name,
    required this.packageName,
  });
}

class AppUsageService {
  static final Map<String, int> _trackedUsageSeconds = {};

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
  };

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

  void recordActiveUsage(String packageName, int addedSeconds) {
    _trackedUsageSeconds[packageName] =
        (_trackedUsageSeconds[packageName] ?? 0) + addedSeconds;
  }

  Future<int> getForegroundSecondsToday(String packageName) async {
    if (Platform.isAndroid) {
      try {
        final now = DateTime.now();
        final midnight = DateTime(now.year, now.month, now.day);
        final stats =
            await UsageStats.queryAndAggregateUsageStats(midnight, now);

        if (stats.containsKey(packageName)) {
          final usage = stats[packageName]!;
          final ms = int.tryParse(usage.totalTimeInForeground ?? '0') ?? 0;
          return ms ~/ 1000;
        }
      } catch (_) {}
    } else if (Platform.isLinux) {
      final tracked = _trackedUsageSeconds[packageName] ?? 0;
      final isRunning = await isLinuxProcessRunning(packageName);
      if (isRunning && tracked == 0) {
        return 120; // Detected active running process
      }
      return tracked;
    }

    return _trackedUsageSeconds[packageName] ?? 0;
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
              if (comm.contains(binaryName.toLowerCase())) {
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
    if (packageName.contains('instagram')) return 'chrome';
    if (packageName.contains('youtube')) return 'chrome';
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
    final trimmed = packageName.trim();
    if (_knownAppCatalog.containsKey(trimmed)) {
      return _knownAppCatalog[trimmed]!;
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
