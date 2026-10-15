import 'package:drift/drift.dart' as drift;
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/constants/app_colors.dart';
import '../../../../core/constants/app_typography.dart';
import '../../../../data/local/app_database.dart';
import '../../../../data/local/database_provider.dart';
import '../../../today/presentation/controllers/today_tasks_controller.dart';
import '../../../today/presentation/widgets/essay_lockout_dialog.dart';
import 'screen_time_blocker_screen.dart';


class DistractionBlockerScreen extends ConsumerStatefulWidget {
  const DistractionBlockerScreen({super.key});

  static Future<void> show(BuildContext context) {
    return ScreenTimeBlockerScreen.show(context);
  }

  @override
  ConsumerState<DistractionBlockerScreen> createState() =>
      _DistractionBlockerScreenState();
}

class _DistractionBlockerScreenState
    extends ConsumerState<DistractionBlockerScreen> {
  final List<Map<String, dynamic>> _defaultApps = [
    {
      'id': 'instagram_limit',
      'packageName': 'com.instagram.android',
      'appName': 'Instagram',
      'cap': 45,
      'blockReels': true,
      'essayLock': true,
    },
    {
      'id': 'youtube_limit',
      'packageName': 'com.google.android.youtube',
      'appName': 'YouTube',
      'cap': 60,
      'blockReels': true,
      'essayLock': true,
    },
    {
      'id': 'tiktok_limit',
      'packageName': 'com.zhiliaoapp.musically',
      'appName': 'TikTok',
      'cap': 30,
      'blockReels': true,
      'essayLock': true,
    },
    {
      'id': 'reddit_limit',
      'packageName': 'com.reddit.frontpage',
      'appName': 'Reddit',
      'cap': 30,
      'blockReels': false,
      'essayLock': false,
    },
    {
      'id': 'twitter_limit',
      'packageName': 'com.twitter.android',
      'appName': 'X / Twitter',
      'cap': 30,
      'blockReels': false,
      'essayLock': true,
    },
  ];

  @override
  void initState() {
    super.initState();
    _seedDefaultLimits();
  }

  Future<void> _seedDefaultLimits() async {
    final db = ref.read(appDatabaseProvider);
    final existing = await db.select(db.appLimits).get();
    if (existing.isEmpty) {
      for (final a in _defaultApps) {
        await db.updateAppLimit(
          AppLimitsCompanion(
            id: drift.Value(a['id'] as String),
            packageName: drift.Value(a['packageName'] as String),
            appName: drift.Value(a['appName'] as String),
            dailyCapMinutes: drift.Value(a['cap'] as int),
            curfewStart: const drift.Value('22:00'),
            curfewEnd: const drift.Value('07:00'),
            blockReelsShorts: drift.Value(a['blockReels'] as bool),
            isEssayLocked: drift.Value(a['essayLock'] as bool),
          ),
        );
      }
    }
  }

  Future<void> _updateLimit(AppLimit item, {
    int? dailyCapMinutes,
    bool? blockReels,
    bool? isEssayLocked,
    String? curfewStart,
    String? curfewEnd,
  }) async {
    final db = ref.read(appDatabaseProvider);
    await db.updateAppLimit(
      AppLimitsCompanion(
        id: drift.Value(item.id),
        packageName: drift.Value(item.packageName),
        appName: drift.Value(item.appName),
        dailyCapMinutes: drift.Value(dailyCapMinutes ?? item.dailyCapMinutes),
        curfewStart: drift.Value(curfewStart ?? item.curfewStart),
        curfewEnd: drift.Value(curfewEnd ?? item.curfewEnd),
        blockReelsShorts: drift.Value(blockReels ?? item.blockReelsShorts),
        isEssayLocked: drift.Value(isEssayLocked ?? item.isEssayLocked),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final limitsAsync = ref.watch(appLimitsStreamProvider);
    final appUsage = ref.watch(appUsageServiceProvider);

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        backgroundColor: AppColors.background,
        elevation: 0,
        scrolledUnderElevation: 0,
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Distraction Blocker', style: AppTypography.h1),
            Text(
              'Hardware & Process Enforcement (Screen 7)',
              style: AppTypography.bodySmall,
            ),
          ],
        ),
        actions: [
          IconButton(
            tooltip: 'Trigger Test Lockout',
            icon: const Icon(Icons.shield_rounded, color: AppColors.errorRed),
            onPressed: () => EssayLockoutDialog.show(context),
          ),
        ],
      ),
      body: limitsAsync.when(
        loading: () => const Center(
          child: CircularProgressIndicator(color: AppColors.primaryAccent),
        ),
        error: (err, _) => Center(
          child: Text('Error loading limits: $err', style: AppTypography.bodyMedium),
        ),
        data: (limits) {
          if (limits.isEmpty) {
            return Center(
              child: ElevatedButton(
                onPressed: _seedDefaultLimits,
                child: const Text('Initialize App Limits'),
              ),
            );
          }

          return ListView(
            padding: const EdgeInsets.all(16),
            children: [
              // Summary Shield Card
              Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: AppColors.surface,
                  borderRadius: BorderRadius.circular(14),
                  border: Border.all(color: AppColors.border),
                ),
                child: Row(
                  children: [
                    Container(
                      padding: const EdgeInsets.all(10),
                      decoration: BoxDecoration(
                        color: AppColors.errorRed.withAlpha(35),
                        shape: BoxShape.circle,
                      ),
                      child: const Icon(
                        Icons.security_rounded,
                        color: AppColors.errorRed,
                        size: 26,
                      ),
                    ),
                    const SizedBox(width: 14),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            'Active Protection Shield',
                            style: AppTypography.h2.copyWith(fontSize: 16),
                          ),
                          const SizedBox(height: 2),
                          Text(
                            'Curfew & Daily Usage caps enforced. Override requires manual Stoic transcription.',
                            style: AppTypography.bodySmall,
                          ),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 20),

              Text('CONFIGURED APP CAPS & RULES', style: AppTypography.bodySmall),
              const SizedBox(height: 10),

              ...limits.map((item) {
                final isCurfew = appUsage.isCurfewActive(
                    item.curfewStart, item.curfewEnd);

                return Container(
                  margin: const EdgeInsets.only(bottom: 14),
                  padding: const EdgeInsets.all(14),
                  decoration: BoxDecoration(
                    color: AppColors.surface,
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(
                      color: isCurfew
                          ? AppColors.errorRed.withAlpha(120)
                          : AppColors.border,
                    ),
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      // Header Row
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          Row(
                            children: [
                              Text(
                                item.appName,
                                style: AppTypography.bodyMedium.copyWith(
                                  fontWeight: FontWeight.bold,
                                  fontSize: 16,
                                ),
                              ),
                              const SizedBox(width: 8),
                              if (isCurfew)
                                Container(
                                  padding: const EdgeInsets.symmetric(
                                      horizontal: 6, vertical: 2),
                                  decoration: BoxDecoration(
                                    color: AppColors.errorRed.withAlpha(40),
                                    borderRadius: BorderRadius.circular(4),
                                  ),
                                  child: Text(
                                    'CURFEW ACTIVE',
                                    style: AppTypography.accentMono.copyWith(
                                      color: AppColors.errorRed,
                                      fontSize: 10,
                                    ),
                                  ),
                                ),
                            ],
                          ),
                          Text(
                            '${item.dailyCapMinutes} min/day',
                            style: AppTypography.accentMono,
                          ),
                        ],
                      ),
                      const SizedBox(height: 10),

                      // Slider for Cap
                      SliderTheme(
                        data: SliderTheme.of(context).copyWith(
                          activeTrackColor: AppColors.primaryAccent,
                          inactiveTrackColor: AppColors.background,
                          thumbColor: AppColors.primaryAccent,
                          overlayColor: AppColors.primaryAccent.withAlpha(30),
                        ),
                        child: Slider(
                          value: item.dailyCapMinutes.toDouble(),
                          min: 10,
                          max: 180,
                          divisions: 17,
                          label: '${item.dailyCapMinutes}m',
                          onChanged: (val) {
                            _updateLimit(item, dailyCapMinutes: val.round());
                          },
                        ),
                      ),

                      // Toggles
                      SwitchListTile.adaptive(
                        contentPadding: EdgeInsets.zero,
                        dense: true,
                        title: Text(
                          'Block Reels / Shorts Tab Only',
                          style: AppTypography.bodySmall.copyWith(
                            color: AppColors.textPrimary,
                          ),
                        ),
                        value: item.blockReelsShorts,
                        activeTrackColor: AppColors.primaryAccent,
                        onChanged: (val) {
                          _updateLimit(item, blockReels: val);
                        },
                      ),
                      SwitchListTile.adaptive(
                        contentPadding: EdgeInsets.zero,
                        dense: true,
                        title: Text(
                          'Require Stoic Lockout to Override',
                          style: AppTypography.bodySmall.copyWith(
                            color: AppColors.textPrimary,
                          ),
                        ),
                        value: item.isEssayLocked,
                        activeTrackColor: AppColors.primaryAccent,
                        onChanged: (val) {
                          _updateLimit(item, isEssayLocked: val);
                        },
                      ),

                      // Curfew Row
                      const Divider(color: AppColors.border, height: 16),
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          Text('Curfew Window:', style: AppTypography.bodySmall),
                          Text(
                            '${item.curfewStart ?? "22:00"} — ${item.curfewEnd ?? "07:00"}',
                            style: AppTypography.accentMono.copyWith(
                              fontSize: 12,
                              color: AppColors.textSecondary,
                            ),
                          ),
                        ],
                      ),
                    ],
                  ),
                );
              }),
            ],
          );
        },
      ),
    );
  }
}
