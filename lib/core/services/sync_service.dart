import 'dart:async';
import 'package:flutter/foundation.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:drift/drift.dart';

import '../../data/local/app_database.dart';
import '../../data/local/database_provider.dart';
import '../network/supabase_service.dart';

final syncServiceProvider = Provider<SyncService>((ref) {
  final db = ref.watch(appDatabaseProvider);
  final supabase = ref.watch(supabaseServiceProvider);
  return SyncService(db, supabase);
});

class SyncEntityResult {
  final String entityName;
  final bool success;
  final int pushedCount;
  final int pulledCount;
  final String? errorMessage;

  const SyncEntityResult({
    required this.entityName,
    required this.success,
    this.pushedCount = 0,
    this.pulledCount = 0,
    this.errorMessage,
  });
}

class SyncReport {
  final bool overallSuccess;
  final DateTime timestamp;
  final SyncEntityResult tasks;
  final SyncEntityResult dailyLogs;
  final SyncEntityResult timetable;
  final SyncEntityResult journal;
  final SyncEntityResult appLimits;
  final String? authOrNetworkError;

  const SyncReport({
    required this.overallSuccess,
    required this.timestamp,
    required this.tasks,
    required this.dailyLogs,
    required this.timetable,
    required this.journal,
    required this.appLimits,
    this.authOrNetworkError,
  });

  int get failureCount {
    int count = 0;
    if (!tasks.success) count++;
    if (!dailyLogs.success) count++;
    if (!timetable.success) count++;
    if (!journal.success) count++;
    if (!appLimits.success) count++;
    return count;
  }
}

class SyncService {
  final AppDatabase _db;
  final SupabaseService _supabase;
  bool _isSyncing = false;
  SyncReport? _lastReport;
  DateTime? _lastSuccessfulSync;

  SyncService(this._db, this._supabase);

  bool get isSyncing => _isSyncing;
  SyncReport? get lastReport => _lastReport;
  DateTime? get lastSuccessfulSync => _lastSuccessfulSync;

  Future<int> getPendingQueueDepth() async {
    final pendingTasks = await (_db.select(_db.tasks)
          ..where((t) => t.syncStatus.equals('pending')))
        .get();
    return pendingTasks.length;
  }

  Future<SyncReport> syncAll() async {
    if (_isSyncing) {
      return SyncReport(
        overallSuccess: false,
        timestamp: DateTime.now(),
        tasks: const SyncEntityResult(entityName: 'tasks', success: false, errorMessage: 'Sync already running'),
        dailyLogs: const SyncEntityResult(entityName: 'daily_logs', success: false),
        timetable: const SyncEntityResult(entityName: 'timetable', success: false),
        journal: const SyncEntityResult(entityName: 'journal', success: false),
        appLimits: const SyncEntityResult(entityName: 'app_limits', success: false),
        authOrNetworkError: 'Sync currently in progress',
      );
    }

    final client = _supabase.client;
    final user = _supabase.currentUser;
    if (client == null || user == null) {
      debugPrint('Organiza: Supabase not authenticated, skipping cloud sync.');
      final unauthReport = SyncReport(
        overallSuccess: false,
        timestamp: DateTime.now(),
        tasks: const SyncEntityResult(entityName: 'tasks', success: false),
        dailyLogs: const SyncEntityResult(entityName: 'daily_logs', success: false),
        timetable: const SyncEntityResult(entityName: 'timetable', success: false),
        journal: const SyncEntityResult(entityName: 'journal', success: false),
        appLimits: const SyncEntityResult(entityName: 'app_limits', success: false),
        authOrNetworkError: 'Unauthenticated: No active Supabase session',
      );
      _lastReport = unauthReport;
      return unauthReport;
    }

    _isSyncing = true;
    try {
      final tasksRes = await _syncTasks(client, user.id);
      final logsRes = await _syncDailyLogs(client, user.id);
      final timetableRes = await _syncTimetable(client, user.id);
      final journalRes = await _syncJournal(client, user.id);
      final limitsRes = await _syncAppLimits(client, user.id);

      final isAllSuccess = tasksRes.success &&
          logsRes.success &&
          timetableRes.success &&
          journalRes.success &&
          limitsRes.success;

      final report = SyncReport(
        overallSuccess: isAllSuccess,
        timestamp: DateTime.now(),
        tasks: tasksRes,
        dailyLogs: logsRes,
        timetable: timetableRes,
        journal: journalRes,
        appLimits: limitsRes,
      );

      _lastReport = report;
      if (isAllSuccess) {
        _lastSuccessfulSync = report.timestamp;
      }
      return report;
    } catch (e) {
      final failureReport = SyncReport(
        overallSuccess: false,
        timestamp: DateTime.now(),
        tasks: SyncEntityResult(entityName: 'tasks', success: false, errorMessage: e.toString()),
        dailyLogs: const SyncEntityResult(entityName: 'daily_logs', success: false),
        timetable: const SyncEntityResult(entityName: 'timetable', success: false),
        journal: const SyncEntityResult(entityName: 'journal', success: false),
        appLimits: const SyncEntityResult(entityName: 'app_limits', success: false),
        authOrNetworkError: e.toString(),
      );
      _lastReport = failureReport;
      return failureReport;
    } finally {
      _isSyncing = false;
    }
  }

  Future<SyncEntityResult> _syncTasks(dynamic client, String userId) async {
    int pushed = 0;
    int pulled = 0;
    String? error;

    // 1. Push pending local tasks
    try {
      final localTasks = await (_db.select(_db.tasks)
            ..where((t) => t.syncStatus.equals('pending')))
          .get();

      for (final t in localTasks) {
        try {
          await client.from('tasks').upsert({
            'id': t.id,
            'user_id': userId,
            'title': t.title,
            'description': t.description,
            'cadence_unit': t.cadenceUnit,
            'target_frequency': t.targetFrequency,
            'target_duration_seconds': t.targetDurationSeconds,
            'best_time_start': t.bestTimeStart,
            'best_time_end': t.bestTimeEnd,
            'xp_value': t.xpValue,
            'linked_package_name': t.linkedPackageName,
            'is_deleted': t.isDeleted,
            'updated_at': t.updatedAt.toIso8601String(),
          });
          await _db.updateTask(
            t.id,
            const TasksCompanion(syncStatus: Value('synced')),
          );
          pushed++;
        } catch (e) {
          debugPrint('Organiza: Sync task push item error ($t.id): $e');
        }
      }
    } catch (e) {
      error = 'Push tasks failed: $e';
    }

    // 2. Pull remote tasks
    try {
      final remoteRes = await client
          .from('tasks')
          .select()
          .eq('user_id', userId);

      for (final r in (remoteRes as List)) {
        final id = r['id'] as String;
        final remoteUpdated = DateTime.tryParse(r['updated_at'] ?? '') ??
            DateTime.fromMillisecondsSinceEpoch(0);
        final remoteIsDeleted = r['is_deleted'] as bool? ?? false;

        final local = await (_db.select(_db.tasks)..where((t) => t.id.equals(id)))
            .getSingleOrNull();

        if (local == null) {
          // Insert remote record preserving tombstone if deleted
          await _db.insertTask(
            TasksCompanion(
              id: Value(id),
              title: Value(r['title'] as String? ?? 'Untitled'),
              description: Value(r['description'] as String?),
              cadenceUnit: Value(r['cadence_unit'] as String? ?? 'day'),
              targetFrequency: Value(r['target_frequency'] as int? ?? 1),
              targetDurationSeconds:
                  Value(r['target_duration_seconds'] as int?),
              bestTimeStart: Value(r['best_time_start'] as String?),
              bestTimeEnd: Value(r['best_time_end'] as String?),
              xpValue: Value(r['xp_value'] as int? ?? 50),
              linkedPackageName: Value(r['linked_package_name'] as String?),
              isDeleted: Value(remoteIsDeleted),
              createdAt: Value(DateTime.tryParse(r['created_at'] ?? '') ??
                  DateTime.now()),
              updatedAt: Value(remoteUpdated),
              syncStatus: const Value('synced'),
            ),
          );
          pulled++;
        } else {
          // LWW: If local has unsynced pending changes newer than remote, local wins
          if (local.syncStatus == 'pending' && local.updatedAt.isAfter(remoteUpdated)) {
            continue;
          }

          // Tombstone safety: If local was deleted after remote update, do not resurrect
          if (local.isDeleted && !remoteIsDeleted && local.updatedAt.isAfter(remoteUpdated)) {
            continue;
          }

          if (remoteUpdated.isAfter(local.updatedAt)) {
            await _db.updateTask(
              id,
              TasksCompanion(
                title: Value(r['title'] as String? ?? local.title),
                description: Value(r['description'] as String?),
                cadenceUnit: Value(r['cadence_unit'] as String? ?? local.cadenceUnit),
                targetFrequency:
                    Value(r['target_frequency'] as int? ?? local.targetFrequency),
                targetDurationSeconds:
                    Value(r['target_duration_seconds'] as int?),
                bestTimeStart: Value(r['best_time_start'] as String?),
                bestTimeEnd: Value(r['best_time_end'] as String?),
                xpValue: Value(r['xp_value'] as int? ?? local.xpValue),
                linkedPackageName: Value(r['linked_package_name'] as String?),
                isDeleted: Value(remoteIsDeleted),
                updatedAt: Value(remoteUpdated),
                syncStatus: const Value('synced'),
              ),
            );
            pulled++;
          }
        }
      }
    } catch (e) {
      error = (error != null) ? '$error; Pull tasks failed: $e' : 'Pull tasks failed: $e';
    }

    return SyncEntityResult(
      entityName: 'tasks',
      success: error == null,
      pushedCount: pushed,
      pulledCount: pulled,
      errorMessage: error,
    );
  }

  Future<SyncEntityResult> _syncDailyLogs(dynamic client, String userId) async {
    int pushed = 0;
    String? error;
    try {
      final localLogs = await _db.select(_db.dailyLogs).get();
      for (final l in localLogs) {
        try {
          await client.from('daily_logs').upsert({
            'id': l.id,
            'task_id': l.taskId,
            'user_id': userId,
            'log_date': l.logDate,
            'duration_logged_seconds': l.durationLoggedSeconds,
            'is_completed': l.isCompleted,
            'xp_awarded': l.xpAwarded,
            'stars': l.stars,
            'notes': l.notes,
            'updated_at': l.updatedAt.toIso8601String(),
          });
          pushed++;
        } catch (e) {
          debugPrint('Organiza: Log push item error: $e');
        }
      }
    } catch (e) {
      error = 'Sync daily logs failed: $e';
    }

    return SyncEntityResult(
      entityName: 'daily_logs',
      success: error == null,
      pushedCount: pushed,
      errorMessage: error,
    );
  }

  Future<SyncEntityResult> _syncTimetable(dynamic client, String userId) async {
    int pushed = 0;
    String? error;
    try {
      final entries = await _db.select(_db.timetableEntries).get();
      for (final t in entries) {
        try {
          await client.from('timetable_entries').upsert({
            'id': t.id,
            'user_id': userId,
            'title': t.title,
            'day_of_week': t.dayOfWeek,
            'specific_date': t.specificDate,
            'start_time': t.startTime,
            'end_time': t.endTime,
            'location_or_notes': t.locationOrNotes,
            'is_canceled': t.isCanceled,
          });
          pushed++;
        } catch (e) {
          debugPrint('Organiza: Timetable push item error: $e');
        }
      }
    } catch (e) {
      error = 'Sync timetable failed: $e';
    }

    return SyncEntityResult(
      entityName: 'timetable',
      success: error == null,
      pushedCount: pushed,
      errorMessage: error,
    );
  }

  Future<SyncEntityResult> _syncJournal(dynamic client, String userId) async {
    int pushed = 0;
    String? error;
    try {
      final entries = await _db.select(_db.journalEntries).get();
      for (final j in entries) {
        try {
          await client.from('journal_entries').upsert({
            'id': j.id,
            'user_id': userId,
            'entry_date': j.entryDate,
            'content': j.content,
            'tags': j.tags,
            'created_at': j.createdAt.toIso8601String(),
          });
          pushed++;
        } catch (e) {
          debugPrint('Organiza: Journal push item error: $e');
        }
      }
    } catch (e) {
      error = 'Sync journal failed: $e';
    }

    return SyncEntityResult(
      entityName: 'journal',
      success: error == null,
      pushedCount: pushed,
      errorMessage: error,
    );
  }

  Future<SyncEntityResult> _syncAppLimits(dynamic client, String userId) async {
    int pushed = 0;
    String? error;
    try {
      final limits = await _db.select(_db.appLimits).get();
      for (final al in limits) {
        try {
          await client.from('app_limits').upsert({
            'id': al.id,
            'user_id': userId,
            'package_name': al.packageName,
            'app_name': al.appName,
            'daily_cap_minutes': al.dailyCapMinutes,
            'curfew_start': al.curfewStart,
            'curfew_end': al.curfewEnd,
            'block_reels_shorts': al.blockReelsShorts,
            'is_essay_locked': al.isEssayLocked,
          });
          pushed++;
        } catch (e) {
          debugPrint('Organiza: App limits push item error: $e');
        }
      }
    } catch (e) {
      error = 'Sync app limits failed: $e';
    }

    return SyncEntityResult(
      entityName: 'app_limits',
      success: error == null,
      pushedCount: pushed,
      errorMessage: error,
    );
  }
}
