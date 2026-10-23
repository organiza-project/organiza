import 'dart:io';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:http/http.dart' as http;

import '../../../../core/config/supabase_config.dart';
import '../../../../core/constants/app_colors.dart';
import '../../../../core/constants/app_typography.dart';
import '../../../../core/network/supabase_service.dart';
import '../../../../core/services/sync_service.dart';
import '../../../../data/local/database_provider.dart';
import '../../../auth/presentation/controllers/auth_controller.dart';
import '../../../today/presentation/controllers/today_tasks_controller.dart';

class DiagnosticsScreen extends ConsumerStatefulWidget {
  const DiagnosticsScreen({super.key});

  @override
  ConsumerState<DiagnosticsScreen> createState() => _DiagnosticsScreenState();
}

class _DiagnosticsScreenState extends ConsumerState<DiagnosticsScreen> {
  bool _isLoading = true;
  bool _supabaseReachable = false;
  String _supabaseLatency = 'Checking...';
  int _pendingQueueDepth = 0;
  bool _usagePermissionGranted = false;
  SyncReport? _syncReport;
  bool _isManualSyncing = false;

  @override
  void initState() {
    super.initState();
    _loadDiagnostics();
  }

  Future<void> _loadDiagnostics() async {
    setState(() => _isLoading = true);
    final syncService = ref.read(syncServiceProvider);
    final usageService = ref.read(appUsageServiceProvider);
    final supabase = ref.read(supabaseServiceProvider);

    // 1. Check pending queue depth
    final queueDepth = await syncService.getPendingQueueDepth();

    // 2. Check usage permission
    final permGranted = await usageService.checkPermission();

    // 3. Check Supabase endpoint reachability
    bool reachable = false;
    String latencyStr = 'Unreachable';
    final clientUrl = supabase.client?.rest.url ?? SupabaseConfig.defaultUrl;
    try {
      final sw = Stopwatch()..start();
      final res = await http.get(Uri.parse(clientUrl)).timeout(const Duration(seconds: 4));
      sw.stop();
      if (res.statusCode < 500) {
        reachable = true;
        latencyStr = '${sw.elapsedMilliseconds} ms (HTTP ${res.statusCode})';
      }
    } catch (e) {
      latencyStr = 'Failed: $e';
    }

    if (!mounted) return;
    setState(() {
      _pendingQueueDepth = queueDepth;
      _usagePermissionGranted = permGranted;
      _supabaseReachable = reachable;
      _supabaseLatency = latencyStr;
      _syncReport = syncService.lastReport;
      _isLoading = false;
    });
  }

  Future<void> _triggerManualSync() async {
    setState(() => _isManualSyncing = true);
    final syncService = ref.read(syncServiceProvider);
    final report = await syncService.syncAll();
    final queueDepth = await syncService.getPendingQueueDepth();

    if (!mounted) return;
    setState(() {
      _syncReport = report;
      _pendingQueueDepth = queueDepth;
      _isManualSyncing = false;
    });
  }

  Future<void> _confirmPurgeUsage() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AppColors.surface,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(16),
          side: const BorderSide(color: AppColors.border),
        ),
        title: Text('Purge Usage Records?', style: AppTypography.h2),
        content: Text(
          'This will permanently delete all local app tracking and active window history from your device SQLite database. This action cannot be undone.',
          style: AppTypography.bodySmall,
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(false),
            child: Text('Cancel', style: AppTypography.bodyMedium.copyWith(color: AppColors.textSecondary)),
          ),
          ElevatedButton(
            onPressed: () => Navigator.of(ctx).pop(true),
            style: ElevatedButton.styleFrom(backgroundColor: AppColors.errorRed),
            child: const Text('Purge Records'),
          ),
        ],
      ),
    );

    if (confirmed == true && mounted) {
      final usageService = ref.read(appUsageServiceProvider);
      final count = await usageService.purgeUsageHistory();
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Successfully purged $count usage records.'),
            backgroundColor: AppColors.successGreen,
          ),
        );
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final db = ref.watch(appDatabaseProvider);
    final authState = ref.watch(authControllerProvider);
    final usageService = ref.watch(appUsageServiceProvider);
    final syncService = ref.watch(syncServiceProvider);

    final backendPlatform = Platform.isLinux
        ? 'Linux (xdotool / GNOME DBus / proc)'
        : Platform.isAndroid
            ? 'Android (UsageStatsManager)'
            : Platform.isWindows
                ? 'Windows (Desktop foreground)'
                : 'iOS (DeviceActivity / Manual)';

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        title: Text('System Diagnostics', style: AppTypography.h2),
        backgroundColor: AppColors.surface,
        elevation: 0,
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh_rounded),
            onPressed: _isLoading ? null : _loadDiagnostics,
          ),
        ],
      ),
      body: _isLoading
          ? const Center(child: CircularProgressIndicator())
          : ListView(
              padding: const EdgeInsets.all(16),
              children: [
                _buildSectionHeader('CLOUD BACKEND & NETWORK'),
                _buildCard([
                  _buildRow('Supabase Status', _supabaseReachable ? 'Online' : 'Offline',
                      isSuccess: _supabaseReachable),
                  _buildRow('Latency / Response', _supabaseLatency),
                  _buildRow('Session Status', authState.isGuest ? 'Guest Mode' : (authState.user != null ? 'Authenticated (${authState.user!.email})' : 'Unauthenticated')),
                  _buildRow('Last Cloud Sync', syncService.lastSuccessfulSync != null
                      ? syncService.lastSuccessfulSync!.toLocal().toString().split('.').first
                      : 'Never'),
                ]),
                const SizedBox(height: 20),

                _buildSectionHeader('LOCAL RELATIONAL DATABASE'),
                _buildCard([
                  _buildRow('Drift Engine', 'SQLite via Drift Native'),
                  _buildRow('Local Schema Version', 'v${db.schemaVersion}'),
                  _buildRow('Pending Sync Queue Depth', '$_pendingQueueDepth items',
                      isWarning: _pendingQueueDepth > 0),
                ]),
                const SizedBox(height: 20),

                _buildSectionHeader('ACTIVE USAGE TRACKER'),
                _buildCard([
                  _buildRow('Active Backend', backendPlatform),
                  _buildRow('Tracking Consent', usageService.isTrackingEnabled ? 'Granted (Active)' : 'Disabled / Withheld',
                      isSuccess: usageService.isTrackingEnabled),
                  _buildRow('System Permission', _usagePermissionGranted ? 'Granted' : 'Missing / Denied',
                      isSuccess: _usagePermissionGranted),
                  _buildRow('Active Poller State', usageService.isPolling ? 'Polling window' : 'Idle'),
                  const SizedBox(height: 12),
                  Row(
                    children: [
                      Expanded(
                        child: OutlinedButton.icon(
                          onPressed: () async {
                            if (usageService.isTrackingEnabled) {
                              await usageService.disableTracking();
                            } else {
                              await usageService.enableTracking();
                            }
                            setState(() {});
                          },
                          icon: Icon(usageService.isTrackingEnabled ? Icons.pause_circle_outline : Icons.play_circle_outline),
                          label: Text(usageService.isTrackingEnabled ? 'Pause Tracking' : 'Enable Tracking'),
                        ),
                      ),
                      const SizedBox(width: 8),
                      OutlinedButton.icon(
                        onPressed: _confirmPurgeUsage,
                        style: OutlinedButton.styleFrom(foregroundColor: AppColors.errorRed),
                        icon: const Icon(Icons.delete_sweep_outlined, size: 18),
                        label: const Text('Purge History'),
                      ),
                    ],
                  ),
                ]),
                const SizedBox(height: 20),

                _buildSectionHeader('OBSERVABLE SYNC DIAGNOSTICS'),
                _buildCard([
                  if (_syncReport != null) ...[
                    _buildRow('Overall Result', _syncReport!.overallSuccess ? 'Synchronized' : 'Failed / Incomplete',
                        isSuccess: _syncReport!.overallSuccess),
                    _buildRow('Tasks', '${_syncReport!.tasks.pushedCount} pushed, ${_syncReport!.tasks.pulledCount} pulled ${_syncReport!.tasks.errorMessage ?? ""}'),
                    _buildRow('Daily Logs', '${_syncReport!.dailyLogs.pushedCount} pushed ${_syncReport!.dailyLogs.errorMessage ?? ""}'),
                    _buildRow('Timetable', '${_syncReport!.timetable.pushedCount} pushed ${_syncReport!.timetable.errorMessage ?? ""}'),
                    _buildRow('Journal', '${_syncReport!.journal.pushedCount} pushed ${_syncReport!.journal.errorMessage ?? ""}'),
                    _buildRow('App Limits', '${_syncReport!.appLimits.pushedCount} pushed ${_syncReport!.appLimits.errorMessage ?? ""}'),
                    if (_syncReport!.authOrNetworkError != null)
                      _buildRow('Notice', _syncReport!.authOrNetworkError!, isWarning: true),
                  ] else ...[
                    Text('No sync operation recorded yet in this session.', style: AppTypography.bodySmall),
                  ],
                  const SizedBox(height: 12),
                  SizedBox(
                    width: double.infinity,
                    child: ElevatedButton.icon(
                      onPressed: _isManualSyncing ? null : _triggerManualSync,
                      style: ElevatedButton.styleFrom(backgroundColor: AppColors.primaryAccent),
                      icon: _isManualSyncing
                          ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2, color: AppColors.background))
                          : const Icon(Icons.sync_rounded),
                      label: Text(_isManualSyncing ? 'Syncing...' : 'Run Full Observable Sync'),
                    ),
                  ),
                ]),
                const SizedBox(height: 30),
              ],
            ),
    );
  }

  Widget _buildSectionHeader(String title) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 8, left: 4),
      child: Text(title, style: AppTypography.bodySmall.copyWith(fontWeight: FontWeight.bold, letterSpacing: 1.1)),
    );
  }

  Widget _buildCard(List<Widget> children) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: AppColors.border),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: children,
      ),
    );
  }

  Widget _buildRow(String label, String value, {bool? isSuccess, bool isWarning = false}) {
    Color valueColor = AppColors.textPrimary;
    if (isSuccess == true) valueColor = AppColors.successGreen;
    if (isSuccess == false) valueColor = AppColors.errorRed;
    if (isWarning) valueColor = Colors.orangeAccent;

    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(label, style: AppTypography.bodySmall.copyWith(color: AppColors.textSecondary)),
          const SizedBox(width: 8),
          Flexible(
            child: Text(
              value,
              textAlign: TextAlign.end,
              style: AppTypography.bodySmall.copyWith(
                color: valueColor,
                fontWeight: FontWeight.w600,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
