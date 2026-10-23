import 'dart:ffi';
import 'package:flutter_test/flutter_test.dart';
import 'package:drift/native.dart';
import 'package:drift/drift.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:sqlite3/open.dart';
import 'package:organiza/core/services/app_usage_service.dart';
import 'package:organiza/data/local/app_database.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  open.overrideFor(OperatingSystem.linux, () {
    try {
      return DynamicLibrary.open('libsqlite3.so.0');
    } catch (_) {
      return DynamicLibrary.open('/lib/x86_64-linux-gnu/libsqlite3.so.0');
    }
  });

  driftRuntimeOptions.dontWarnAboutMultipleDatabases = true;

  group('AppUsageService Canonical Accounting & Consent Tests', () {
    late AppDatabase db;
    late AppUsageService usageService;

    setUp(() {
      SharedPreferences.setMockInitialValues({});
      db = AppDatabase(NativeDatabase.memory());
      usageService = AppUsageService(db);
    });

    tearDown(() async {
      usageService.dispose();
      await db.close();
    });

    test('tracking is opt-in by default and requires explicit consent', () async {
      final prefs = await SharedPreferences.getInstance();
      expect(usageService.isTrackingEnabled, isFalse);

      await usageService.enableTracking(prefs);
      expect(usageService.isTrackingEnabled, isTrue);
      expect(prefs.getBool(AppUsageService.prefTrackingConsentKey), isTrue);

      await usageService.disableTracking(prefs);
      expect(usageService.isTrackingEnabled, isFalse);
      expect(prefs.getBool(AppUsageService.prefTrackingConsentKey), isFalse);
    });

    test('canonical daily snapshot and delta increment accounting produce consistent totals', () async {
      const pkg = 'com.amazon.kindle';
      const appName = 'Amazon Kindle';
      const date = '2026-10-23';

      // 1. Snapshot replaces cumulative total
      await db.recordDailyUsageSnapshot(pkg, appName, date, 600);
      var usage = await db.getTodayUsageMap(date);
      expect(usage[pkg], equals(600));

      // 2. Incremental delta adds to cumulative total
      await db.incrementDailyUsage(pkg, appName, date, 120);
      usage = await db.getTodayUsageMap(date);
      expect(usage[pkg], equals(720));

      // 3. New snapshot updates accurately
      await db.recordDailyUsageSnapshot(pkg, appName, date, 900);
      usage = await db.getTodayUsageMap(date);
      expect(usage[pkg], equals(900));
    });

    test('midnight rollover partitions usage by date cleanly', () async {
      const pkg = 'com.duolingo';
      const appName = 'Duolingo';
      const day1 = '2026-10-22';
      const day2 = '2026-10-23';

      await db.recordDailyUsageSnapshot(pkg, appName, day1, 1500);
      await db.recordDailyUsageSnapshot(pkg, appName, day2, 300);

      final day1Usage = await db.getTodayUsageMap(day1);
      final day2Usage = await db.getTodayUsageMap(day2);

      expect(day1Usage[pkg], equals(1500));
      expect(day2Usage[pkg], equals(300));
    });

    test('purgeUsageHistory completely clears all local tracking records', () async {
      await db.recordDailyUsageSnapshot('chrome', 'Google Chrome', '2026-10-23', 3600);
      await db.recordDailyUsageSnapshot('code', 'VS Code', '2026-10-23', 7200);

      var usage = await db.getTodayUsageMap('2026-10-23');
      expect(usage.length, equals(2));

      final deletedCount = await usageService.purgeUsageHistory();
      expect(deletedCount, equals(2));

      usage = await db.getTodayUsageMap('2026-10-23');
      expect(usage.isEmpty, isTrue);
    });
  });
}
