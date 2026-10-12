import 'dart:io';
import 'package:usage_stats/usage_stats.dart';

class AppUsageService {
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
}
