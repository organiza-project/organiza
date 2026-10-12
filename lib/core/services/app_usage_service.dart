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
  };

  Future<bool> checkPermission() async {
    if (!Platform.isAndroid) return false;
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

  Future<int> getForegroundSecondsToday(String packageName) async {
    if (!Platform.isAndroid) return 0;
    try {
      final now = DateTime.now();
      final midnight = DateTime(now.year, now.month, now.day);
      final stats = await UsageStats.queryAndAggregateUsageStats(midnight, now);

      if (stats.containsKey(packageName)) {
        final usage = stats[packageName]!;
        final ms = int.tryParse(usage.totalTimeInForeground ?? '0') ?? 0;
        return ms ~/ 1000;
      }
      return 0;
    } catch (_) {
      return 0;
    }
  }

  Future<List<AppInfoOption>> getInstalledAppOptions() async {
    if (Platform.isAndroid || Platform.isIOS) {
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
    // Fallback: format the last chunk of the package name into a capitalized title
    final parts = trimmed.split('.');
    final lastChunk = parts.isNotEmpty ? parts.last : trimmed;
    if (lastChunk.isEmpty) return trimmed;
    return lastChunk[0].toUpperCase() + lastChunk.substring(1);
  }
}
