import 'dart:math' as math;

import 'package:drift/drift.dart' as drift;
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/constants/app_colors.dart';
import '../../../../core/constants/app_typography.dart';
import '../../../../core/services/app_usage_service.dart';
import '../../../../data/local/app_database.dart';
import '../../../../data/local/database_provider.dart';
import '../../../today/presentation/controllers/today_tasks_controller.dart';
import '../../../today/presentation/widgets/essay_lockout_dialog.dart';

enum TimeSegment { week, day, hour }

class AppUsageData {
  final String appName;
  final String packageName;
  final int usageMinutes;
  final String category;
  final IconData icon;
  final Color color;

  const AppUsageData({
    required this.appName,
    required this.packageName,
    required this.usageMinutes,
    required this.category,
    required this.icon,
    required this.color,
  });
}

class ScreenTimeBlockerScreen extends ConsumerStatefulWidget {
  const ScreenTimeBlockerScreen({super.key});

  static Future<void> show(BuildContext context) {
    return Navigator.of(context).push(
      MaterialPageRoute(
        builder: (context) => const ScreenTimeBlockerScreen(),
      ),
    );
  }

  @override
  ConsumerState<ScreenTimeBlockerScreen> createState() =>
      _ScreenTimeBlockerScreenState();
}

class _ScreenTimeBlockerScreenState
    extends ConsumerState<ScreenTimeBlockerScreen> {
  TimeSegment _selectedSegment = TimeSegment.day;
  DateTime _currentDate = DateTime.now();
  final Set<String> _expandedCategories = {};

  bool _isLoadingUsage = true;
  List<AppUsageData> _liveUsageList = [];

  @override
  void initState() {
    super.initState();
    _fetchLiveUsage();
  }

  Future<void> _fetchLiveUsage() async {
    setState(() => _isLoadingUsage = true);
    final usageService = ref.read(appUsageServiceProvider);
    final usageMap = await usageService.getAggregatedUsageToday();

    final List<AppUsageData> list = [];
    usageMap.forEach((pkg, seconds) {
      final mins = seconds ~/ 60;
      if (mins > 0 || seconds > 30) {
        final cleanName = usageService.getCleanAppName(pkg);
        final category = _resolveCategory(pkg);
        final icon = _resolveIcon(pkg);
        final color = _resolveColor(pkg);
        list.add(AppUsageData(
          appName: cleanName,
          packageName: pkg,
          usageMinutes: mins > 0 ? mins : 1,
          category: category,
          icon: icon,
          color: color,
        ));
      }
    });

    list.sort((a, b) => b.usageMinutes.compareTo(a.usageMinutes));

    if (mounted) {
      setState(() {
        _liveUsageList = list;
        _isLoadingUsage = false;
        if (list.isNotEmpty) {
          _expandedCategories.add(list.first.category);
        }
      });
    }
  }

  String _resolveCategory(String pkg) {
    final lower = pkg.toLowerCase();
    if (lower.contains('instagram') ||
        lower.contains('twitter') ||
        lower.contains('reddit') ||
        lower.contains('musically') ||
        lower.contains('tiktok')) {
      return 'Social & Video';
    }
    if (lower.contains('youtube') ||
        lower.contains('netflix') ||
        lower.contains('twitch')) {
      return 'Entertainment & Video';
    }
    if (lower.contains('whatsapp') ||
        lower.contains('telegram') ||
        lower.contains('discord') ||
        lower.contains('slack')) {
      return 'Productivity & Communication';
    }
    return 'General & Utility';
  }

  IconData _resolveIcon(String pkg) {
    final lower = pkg.toLowerCase();
    if (lower.contains('instagram')) return Icons.camera_alt_rounded;
    if (lower.contains('youtube')) return Icons.play_arrow_rounded;
    if (lower.contains('whatsapp')) return Icons.chat_rounded;
    if (lower.contains('discord')) return Icons.forum_rounded;
    if (lower.contains('twitter')) return Icons.alternate_email_rounded;
    if (lower.contains('reddit')) return Icons.fireplace_rounded;
    if (lower.contains('spotify')) return Icons.music_note_rounded;
    if (lower.contains('obsidian') || lower.contains('notion')) {
      return Icons.edit_note_rounded;
    }
    return Icons.apps_rounded;
  }

  Color _resolveColor(String pkg) {
    final lower = pkg.toLowerCase();
    if (lower.contains('instagram')) return const Color(0xFFE1306C);
    if (lower.contains('youtube')) return const Color(0xFFFF0000);
    if (lower.contains('whatsapp')) return const Color(0xFF25D366);
    if (lower.contains('discord')) return const Color(0xFF5865F2);
    if (lower.contains('twitter')) return const Color(0xFF1DA1F2);
    if (lower.contains('spotify')) return const Color(0xFF1DB954);
    if (lower.contains('reddit')) return const Color(0xFFFF4500);
    return AppColors.primaryAccent;
  }

  Future<void> _updateLimit(
    AppLimit item, {
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

  Future<void> _pickCurfewTime(
    AppLimit item, {
    required bool isStart,
  }) async {
    final currentStr = isStart ? item.curfewStart : item.curfewEnd;
    final parts = (currentStr ?? '22:00').split(':');
    final initial = TimeOfDay(
      hour: int.tryParse(parts[0]) ?? (isStart ? 22 : 7),
      minute: int.tryParse(parts.length > 1 ? parts[1] : '0') ?? 0,
    );

    final picked = await showTimePicker(
      context: context,
      initialTime: initial,
      builder: (context, child) => Theme(
        data: Theme.of(context).copyWith(
          colorScheme: const ColorScheme.dark(
            primary: AppColors.primaryAccent,
            surface: AppColors.surface,
            onSurface: AppColors.textPrimary,
          ),
        ),
        child: child!,
      ),
    );

    if (picked != null) {
      final formatted =
          '${picked.hour.toString().padLeft(2, '0')}:${picked.minute.toString().padLeft(2, '0')}';
      await _updateLimit(
        item,
        curfewStart: isStart ? formatted : null,
        curfewEnd: isStart ? null : formatted,
      );
    }
  }

  String _formatDuration(int minutes) {
    final hours = minutes ~/ 60;
    final mins = minutes % 60;
    if (hours > 0) {
      return '${hours}h ${mins}m';
    }
    return '${mins}m';
  }

  void _showAddCustomAppBlockModal() {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) => _AddCustomBlockSheet(
        onAdded: () {
          _fetchLiveUsage();
        },
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final limitsAsync = ref.watch(appLimitsStreamProvider);
    final limits = limitsAsync.value ?? [];
    final totalMinutes =
        _liveUsageList.fold<int>(0, (sum, a) => sum + a.usageMinutes);

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        backgroundColor: AppColors.background,
        elevation: 0,
        scrolledUnderElevation: 0,
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Screen Time & Blocker', style: AppTypography.h1),
            const SizedBox(height: 2),
            Text(
              'Digital Habits & Cognitive Defense',
              style: AppTypography.bodySmall,
            ),
          ],
        ),
        actions: [
          IconButton(
            tooltip: 'Add Custom App Block',
            icon: const Icon(Icons.add_circle_outline_rounded,
                color: AppColors.primaryAccent),
            onPressed: _showAddCustomAppBlockModal,
          ),
          IconButton(
            tooltip: 'Trigger Stoic Lockout',
            icon: const Icon(Icons.shield_rounded, color: AppColors.errorRed),
            onPressed: () => EssayLockoutDialog.show(context),
          ),
          const SizedBox(width: 8),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: _fetchLiveUsage,
        color: AppColors.primaryAccent,
        backgroundColor: AppColors.surface,
        child: ListView(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 80),
          children: [
            // Segment Picker: [Week] | [Day] | [Hour]
            _buildSegmentPicker(),
            const SizedBox(height: 16),

            // Date Navigation Header
            _buildDateNavigationHeader(totalMinutes),
            const SizedBox(height: 16),

            // Vertical Histogram Usage Bar Chart
            _buildUsageBarChartCard(totalMinutes),
            const SizedBox(height: 22),

            // Most Used Apps & Expandable Categories
            _buildMostUsedSection(totalMinutes),
            const SizedBox(height: 24),

            // Granular Limits & Social Feed Toggles
            _buildLimitsSection(limits),
          ],
        ),
      ),
    );
  }

  Widget _buildSegmentPicker() {
    return Container(
      padding: const EdgeInsets.all(4),
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: AppColors.border),
      ),
      child: Row(
        children: [
          _buildSegmentTab('Week', TimeSegment.week),
          _buildSegmentTab('Day', TimeSegment.day),
          _buildSegmentTab('Hour', TimeSegment.hour),
        ],
      ),
    );
  }

  Widget _buildSegmentTab(String title, TimeSegment segment) {
    final isSelected = _selectedSegment == segment;

    return Expanded(
      child: GestureDetector(
        onTap: () => setState(() => _selectedSegment = segment),
        child: AnimatedContainer(
          duration: const Duration(milliseconds: 200),
          padding: const EdgeInsets.symmetric(vertical: 10),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(12),
            gradient: isSelected
                ? const LinearGradient(
                    colors: [Color(0xFFF59E0B), Color(0xFF8B5CF6)],
                    begin: Alignment.topLeft,
                    end: Alignment.bottomRight,
                  )
                : null,
            color: isSelected ? null : Colors.transparent,
          ),
          child: Center(
            child: Text(
              title,
              style: AppTypography.bodySmall.copyWith(
                fontWeight: isSelected ? FontWeight.bold : FontWeight.w600,
                color: isSelected ? Colors.white : AppColors.textSecondary,
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildDateNavigationHeader(int totalMinutes) {
    const months = [
      'January', 'February', 'March', 'April', 'May', 'June',
      'July', 'August', 'September', 'October', 'November', 'December'
    ];
    final dateStr =
        'Today, ${months[_currentDate.month - 1]} ${_currentDate.day}';

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: AppColors.border),
      ),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          IconButton(
            icon: const Icon(Icons.chevron_left_rounded,
                color: AppColors.textSecondary),
            onPressed: () {
              setState(() {
                _currentDate = _currentDate.subtract(const Duration(days: 1));
              });
            },
          ),
          Row(
            children: [
              Text(
                dateStr,
                style: AppTypography.bodyMedium.copyWith(
                  fontWeight: FontWeight.bold,
                ),
              ),
              const SizedBox(width: 8),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                decoration: BoxDecoration(
                  color: AppColors.primaryAccent.withAlpha(35),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Text(
                  _formatDuration(totalMinutes),
                  style: AppTypography.accentMono.copyWith(
                    fontSize: 12,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ),
            ],
          ),
          IconButton(
            icon: const Icon(Icons.chevron_right_rounded,
                color: AppColors.textSecondary),
            onPressed: () {
              setState(() {
                _currentDate = _currentDate.add(const Duration(days: 1));
              });
            },
          ),
        ],
      ),
    );
  }

  Widget _buildUsageBarChartCard(int totalMinutes) {
    if (_isLoadingUsage) {
      return _buildSkeletonCard(height: 220);
    }

    if (totalMinutes == 0) {
      return Container(
        padding: const EdgeInsets.all(24),
        decoration: BoxDecoration(
          color: AppColors.surface,
          borderRadius: BorderRadius.circular(16),
          border: Border.all(color: AppColors.border),
        ),
        child: Column(
          children: [
            const Icon(Icons.hourglass_empty_rounded,
                size: 36, color: AppColors.textSecondary),
            const SizedBox(height: 12),
            Text(
              'No distraction metrics recorded yet today.',
              textAlign: TextAlign.center,
              style: AppTypography.bodyMedium.copyWith(
                color: AppColors.textSecondary,
                fontWeight: FontWeight.w600,
              ),
            ),
            const SizedBox(height: 6),
            Text(
              'Active app time from Android or linked habits will stream here.',
              textAlign: TextAlign.center,
              style: AppTypography.bodySmall.copyWith(fontSize: 11),
            ),
          ],
        ),
      );
    }

    final barData = _liveUsageList.take(7).map((e) => e.usageMinutes).toList();
    final labels = _liveUsageList.take(7).map((e) {
      final name = e.appName;
      return name.length > 5 ? name.substring(0, 4) : name;
    }).toList();

    while (barData.length < 3) {
      barData.add(0);
      labels.add('-');
    }

    final avg = (totalMinutes / math.max(1, _liveUsageList.length)).round();

    return Container(
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: AppColors.border),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('DAILY ACTIVITY HISTOGRAM',
                      style: AppTypography.bodySmall),
                  const SizedBox(height: 4),
                  Text('${_formatDuration(totalMinutes)} Active Screen Time',
                      style: AppTypography.h2.copyWith(fontSize: 18)),
                ],
              ),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                decoration: BoxDecoration(
                  color: AppColors.surfaceElevated,
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(color: AppColors.border),
                ),
                child: Row(
                  children: [
                    Container(
                      width: 12,
                      height: 2,
                      color: AppColors.primaryAccent,
                    ),
                    const SizedBox(width: 6),
                    Text(
                      'Avg ${_formatDuration(avg)}',
                      style: AppTypography.accentMono.copyWith(fontSize: 11),
                    ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 20),
          SizedBox(
            height: 160,
            child: CustomPaint(
              size: const Size(double.infinity, 160),
              painter: ScreenTimeChartPainter(
                values: barData,
                labels: labels,
                selectedIndex: 0,
                averageValue: avg > 0 ? avg : 15,
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildMostUsedSection(int totalMinutes) {
    if (_isLoadingUsage) {
      return Column(
        children: [
          _buildSkeletonCard(height: 70),
          const SizedBox(height: 8),
          _buildSkeletonCard(height: 70),
        ],
      );
    }

    if (_liveUsageList.isEmpty) {
      return const SizedBox.shrink();
    }

    final categories = <String, List<AppUsageData>>{};
    for (final app in _liveUsageList) {
      categories.putIfAbsent(app.category, () => []).add(app);
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Text('MOST USED APPS & CATEGORIES', style: AppTypography.bodySmall),
            Text('${_formatDuration(totalMinutes)} Total',
                style: AppTypography.accentMono.copyWith(fontSize: 11)),
          ],
        ),
        const SizedBox(height: 12),
        ...categories.entries.map((entry) {
          final catName = entry.key;
          final catApps = entry.value;
          final catTotal =
              catApps.fold<int>(0, (sum, a) => sum + a.usageMinutes);
          final isExpanded = _expandedCategories.contains(catName);

          return Container(
            margin: const EdgeInsets.only(bottom: 12),
            decoration: BoxDecoration(
              color: AppColors.surface,
              borderRadius: BorderRadius.circular(14),
              border: Border.all(color: AppColors.border),
            ),
            child: Column(
              children: [
                InkWell(
                  onTap: () {
                    setState(() {
                      if (isExpanded) {
                        _expandedCategories.remove(catName);
                      } else {
                        _expandedCategories.add(catName);
                      }
                    });
                  },
                  borderRadius: BorderRadius.circular(14),
                  child: Padding(
                    padding: const EdgeInsets.all(14.0),
                    child: Row(
                      children: [
                        Container(
                          padding: const EdgeInsets.all(8),
                          decoration: BoxDecoration(
                            color: AppColors.surfaceElevated,
                            borderRadius: BorderRadius.circular(10),
                          ),
                          child: Icon(
                            catName.contains('Social')
                                ? Icons.share_rounded
                                : (catName.contains('Video')
                                    ? Icons.smart_display_rounded
                                    : Icons.work_outline_rounded),
                            color: AppColors.primaryAccent,
                            size: 20,
                          ),
                        ),
                        const SizedBox(width: 12),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(catName,
                                  style: AppTypography.bodyMedium.copyWith(
                                    fontWeight: FontWeight.bold,
                                  )),
                              Text(
                                '${catApps.length} apps · ${_formatDuration(catTotal)}',
                                style: AppTypography.bodySmall
                                    .copyWith(fontSize: 11),
                              ),
                            ],
                          ),
                        ),
                        Icon(
                          isExpanded
                              ? Icons.keyboard_arrow_up_rounded
                              : Icons.keyboard_arrow_down_rounded,
                          color: AppColors.textSecondary,
                        ),
                      ],
                    ),
                  ),
                ),
                if (isExpanded)
                  Padding(
                    padding: const EdgeInsets.fromLTRB(14, 0, 14, 14),
                    child: Column(
                      children: catApps.map((app) {
                        final ratio = (app.usageMinutes /
                                math.max(1, totalMinutes))
                            .clamp(0.0, 1.0);

                        return Padding(
                          padding: const EdgeInsets.symmetric(vertical: 6.0),
                          child: Row(
                            children: [
                              Container(
                                width: 34,
                                height: 34,
                                decoration: BoxDecoration(
                                  color: app.color.withAlpha(35),
                                  borderRadius: BorderRadius.circular(8),
                                ),
                                child: Icon(app.icon,
                                    color: app.color, size: 18),
                              ),
                              const SizedBox(width: 12),
                              Expanded(
                                child: Column(
                                  crossAxisAlignment: CrossAxisAlignment.start,
                                  children: [
                                    Text(app.appName,
                                        style:
                                            AppTypography.bodySmall.copyWith(
                                          fontWeight: FontWeight.w600,
                                          color: AppColors.textPrimary,
                                        )),
                                    const SizedBox(height: 4),
                                    ClipRRect(
                                      borderRadius: BorderRadius.circular(3),
                                      child: LinearProgressIndicator(
                                        value: ratio,
                                        minHeight: 5,
                                        backgroundColor:
                                            AppColors.surfaceElevated,
                                        valueColor: AlwaysStoppedAnimation(
                                          ratio > 0.8
                                              ? AppColors.errorRed
                                              : app.color,
                                        ),
                                      ),
                                    ),
                                  ],
                                ),
                              ),
                              const SizedBox(width: 14),
                              Text(
                                _formatDuration(app.usageMinutes),
                                style: AppTypography.accentMono.copyWith(
                                  fontSize: 12,
                                  fontWeight: FontWeight.bold,
                                ),
                              ),
                            ],
                          ),
                        );
                      }).toList(),
                    ),
                  ),
              ],
            ),
          );
        }),
      ],
    );
  }

  Widget _buildLimitsSection(List<AppLimit> limits) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Text('GRANULAR LIMITS & CURFEWS', style: AppTypography.bodySmall),
            IconButton(
              tooltip: 'Add Custom App Block',
              icon: const Icon(Icons.add_rounded,
                  size: 20, color: AppColors.primaryAccent),
              onPressed: _showAddCustomAppBlockModal,
            ),
          ],
        ),
        const SizedBox(height: 12),
        if (limits.isEmpty)
          Container(
            padding: const EdgeInsets.all(20),
            decoration: BoxDecoration(
              color: AppColors.surface,
              borderRadius: BorderRadius.circular(14),
              border: Border.all(color: AppColors.border),
            ),
            child: Center(
              child: Column(
                children: [
                  const Icon(Icons.lock_outline_rounded,
                      color: AppColors.textSecondary, size: 28),
                  const SizedBox(height: 8),
                  Text('No app limits active.', style: AppTypography.bodySmall),
                  const SizedBox(height: 4),
                  Text(
                    'Tap + above to protect your attention with caps & curfews.',
                    style: AppTypography.bodySmall.copyWith(
                      color: AppColors.textSecondary,
                      fontSize: 11,
                    ),
                  ),
                ],
              ),
            ),
          ),
        ...limits.map((item) {
          final isReelsTarget =
              item.appName.toLowerCase().contains('instagram');
          final isShortsTarget = item.appName.toLowerCase().contains('youtube');

          return Container(
            margin: const EdgeInsets.only(bottom: 14),
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: AppColors.surface,
              borderRadius: BorderRadius.circular(14),
              border: Border.all(color: AppColors.border),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Row(
                      children: [
                        Text(
                          item.appName,
                          style: AppTypography.h2.copyWith(fontSize: 16),
                        ),
                        const SizedBox(width: 8),
                        IconButton(
                          icon: const Icon(Icons.delete_outline_rounded,
                              size: 18, color: AppColors.textSecondary),
                          tooltip: 'Remove limit',
                          onPressed: () async {
                            final db = ref.read(appDatabaseProvider);
                            await db.deleteAppLimit(item.id);
                          },
                        ),
                      ],
                    ),
                    Text(
                      '${item.dailyCapMinutes} min cap',
                      style: AppTypography.accentMono.copyWith(
                        color: AppColors.primaryAccent,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 6),
                SliderTheme(
                  data: SliderTheme.of(context).copyWith(
                    activeTrackColor: AppColors.primaryAccent,
                    inactiveTrackColor: AppColors.surfaceElevated,
                    thumbColor: AppColors.primaryAccent,
                    trackHeight: 4,
                  ),
                  child: Slider(
                    value: item.dailyCapMinutes.clamp(10, 180).toDouble(),
                    min: 10,
                    max: 180,
                    divisions: 17,
                    label: '${item.dailyCapMinutes}m',
                    onChanged: (val) {
                      _updateLimit(item, dailyCapMinutes: val.round());
                    },
                  ),
                ),
                const Divider(color: AppColors.border, height: 16),
                if (isReelsTarget) ...[
                  SwitchListTile.adaptive(
                    contentPadding: EdgeInsets.zero,
                    dense: true,
                    title: Text(
                      'Block Reels Feed Only (Keep DMs Active)',
                      style: AppTypography.bodySmall.copyWith(
                        color: AppColors.textPrimary,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                    subtitle: Text(
                      'Intercepts infinite vertical scrolling reels video tab',
                      style: AppTypography.bodySmall.copyWith(fontSize: 11),
                    ),
                    value: item.blockReelsShorts,
                    activeTrackColor: AppColors.primaryAccent,
                    onChanged: (val) {
                      _updateLimit(item, blockReels: val);
                    },
                  ),
                ],
                if (isShortsTarget) ...[
                  SwitchListTile.adaptive(
                    contentPadding: EdgeInsets.zero,
                    dense: true,
                    title: Text(
                      'Block YouTube Shorts Tab',
                      style: AppTypography.bodySmall.copyWith(
                        color: AppColors.textPrimary,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                    subtitle: Text(
                      'Blocks short-form algorithmic loop feed',
                      style: AppTypography.bodySmall.copyWith(fontSize: 11),
                    ),
                    value: item.blockReelsShorts,
                    activeTrackColor: AppColors.primaryAccent,
                    onChanged: (val) {
                      _updateLimit(item, blockReels: val);
                    },
                  ),
                ],
                SwitchListTile.adaptive(
                  contentPadding: EdgeInsets.zero,
                  dense: true,
                  title: Text(
                    'Require Stoic Lockout to Override',
                    style: AppTypography.bodySmall.copyWith(
                      color: AppColors.textPrimary,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  subtitle: Text(
                    'Must manually type philosophical reflection at >=95% accuracy to bypass',
                    style: AppTypography.bodySmall.copyWith(fontSize: 11),
                  ),
                  value: item.isEssayLocked,
                  activeTrackColor: AppColors.primaryAccent,
                  onChanged: (val) {
                    _updateLimit(item, isEssayLocked: val);
                  },
                ),
                const SizedBox(height: 10),
                Row(
                  children: [
                    Expanded(
                      child: OutlinedButton.icon(
                        style: OutlinedButton.styleFrom(
                          side: const BorderSide(color: AppColors.border),
                          padding: const EdgeInsets.symmetric(vertical: 10),
                        ),
                        onPressed: () => _pickCurfewTime(item, isStart: true),
                        icon: const Icon(Icons.nightlight_round,
                            size: 16, color: Colors.amber),
                        label: Text(
                          'Curfew: ${item.curfewStart ?? "22:00"}',
                          style: AppTypography.bodySmall.copyWith(
                            fontSize: 11,
                            color: AppColors.textPrimary,
                          ),
                        ),
                      ),
                    ),
                    const SizedBox(width: 8),
                    Expanded(
                      child: OutlinedButton.icon(
                        style: OutlinedButton.styleFrom(
                          side: const BorderSide(color: AppColors.border),
                          padding: const EdgeInsets.symmetric(vertical: 10),
                        ),
                        onPressed: () => _pickCurfewTime(item, isStart: false),
                        icon: const Icon(Icons.wb_sunny_rounded,
                            size: 16, color: Colors.amber),
                        label: Text(
                          'Wake: ${item.curfewEnd ?? "07:00"}',
                          style: AppTypography.bodySmall.copyWith(
                            fontSize: 11,
                            color: AppColors.textPrimary,
                          ),
                        ),
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
  }

  Widget _buildSkeletonCard({required double height}) {
    return Container(
      height: height,
      margin: const EdgeInsets.only(bottom: 12),
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: AppColors.border),
      ),
      child: Center(
        child: SizedBox(
          width: 24,
          height: 24,
          child: CircularProgressIndicator(
            strokeWidth: 2,
            valueColor: AlwaysStoppedAnimation(
                AppColors.primaryAccent.withAlpha(120)),
          ),
        ),
      ),
    );
  }
}

// Add Custom App Block Modal Bottom Sheet
class _AddCustomBlockSheet extends ConsumerStatefulWidget {
  final VoidCallback onAdded;

  const _AddCustomBlockSheet({required this.onAdded});

  @override
  ConsumerState<_AddCustomBlockSheet> createState() =>
      _AddCustomBlockSheetState();
}

class _AddCustomBlockSheetState extends ConsumerState<_AddCustomBlockSheet> {
  final _packageNameController = TextEditingController();
  final _appNameController = TextEditingController();
  int _capMinutes = 45;
  final String _curfewStart = '22:30';
  final String _curfewEnd = '07:00';
  bool _isEssayLocked = true;
  bool _blockShorts = false;
  List<AppInfoOption> _installedApps = [];
  String? _selectedPresetPkg;

  @override
  void initState() {
    super.initState();
    _loadInstalledApps();
  }

  Future<void> _loadInstalledApps() async {
    final service = ref.read(appUsageServiceProvider);
    final apps = await service.getInstalledAppOptions();
    if (mounted) {
      setState(() => _installedApps = apps);
    }
  }

  @override
  void dispose() {
    _packageNameController.dispose();
    _appNameController.dispose();
    super.dispose();
  }

  Future<void> _saveBlock() async {
    final pkg = _selectedPresetPkg ?? _packageNameController.text.trim();
    var name = _appNameController.text.trim();
    if (pkg.isEmpty) return;

    if (name.isEmpty) {
      final service = ref.read(appUsageServiceProvider);
      name = service.getCleanAppName(pkg);
    }

    final db = ref.read(appDatabaseProvider);
    final id = 'limit_${DateTime.now().millisecondsSinceEpoch}';

    await db.updateAppLimit(
      AppLimitsCompanion(
        id: drift.Value(id),
        packageName: drift.Value(pkg),
        appName: drift.Value(name),
        dailyCapMinutes: drift.Value(_capMinutes),
        curfewStart: drift.Value(_curfewStart),
        curfewEnd: drift.Value(_curfewEnd),
        blockReelsShorts: drift.Value(_blockShorts),
        isEssayLocked: drift.Value(_isEssayLocked),
      ),
    );

    widget.onAdded();
    if (mounted) {
      Navigator.of(context).pop();
    }
  }

  @override
  Widget build(BuildContext context) {
    final bottomInset = MediaQuery.of(context).viewInsets.bottom;

    return Container(
      padding: EdgeInsets.only(
        left: 20,
        right: 20,
        top: 20,
        bottom: bottomInset + 24,
      ),
      decoration: const BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.vertical(top: Radius.circular(20)),
        border: Border(top: BorderSide(color: AppColors.border, width: 1)),
      ),
      child: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Center(
              child: Container(
                width: 40,
                height: 4,
                margin: const EdgeInsets.only(bottom: 16),
                decoration: BoxDecoration(
                  color: AppColors.border,
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
            ),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text('Add App Blocker', style: AppTypography.h1),
                IconButton(
                  onPressed: () => Navigator.of(context).pop(),
                  icon: const Icon(Icons.close_rounded,
                      color: AppColors.textSecondary),
                ),
              ],
            ),
            const SizedBox(height: 16),
            if (_installedApps.isNotEmpty) ...[
              Text('SELECT PRESET APP', style: AppTypography.bodySmall),
              const SizedBox(height: 8),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 2),
                decoration: BoxDecoration(
                  color: AppColors.background,
                  borderRadius: BorderRadius.circular(10),
                  border: Border.all(color: AppColors.border),
                ),
                child: DropdownButtonHideUnderline(
                  child: DropdownButton<String?>(
                    value: _selectedPresetPkg,
                    isExpanded: true,
                    dropdownColor: AppColors.surfaceElevated,
                    style: AppTypography.bodyMedium,
                    hint: Text('Choose an app or enter below',
                        style: AppTypography.bodySmall),
                    items: [
                      const DropdownMenuItem<String?>(
                        value: null,
                        child: Text('Manual Package / Other'),
                      ),
                      ..._installedApps.map((a) => DropdownMenuItem<String?>(
                            value: a.packageName,
                            child: Text(a.name),
                          )),
                    ],
                    onChanged: (val) {
                      setState(() {
                        _selectedPresetPkg = val;
                        if (val != null) {
                          final match =
                              _installedApps.firstWhere((a) => a.packageName == val);
                          _appNameController.text = match.name;
                          _packageNameController.text = match.packageName;
                        }
                      });
                    },
                  ),
                ),
              ),
              const SizedBox(height: 14),
            ],
            if (_selectedPresetPkg == null) ...[
              Text('PACKAGE NAME', style: AppTypography.bodySmall),
              const SizedBox(height: 6),
              TextField(
                controller: _packageNameController,
                style: AppTypography.bodyMedium,
                decoration: InputDecoration(
                  hintText: 'e.g. com.zhiliaoapp.musically',
                  hintStyle: AppTypography.bodySmall,
                  filled: true,
                  fillColor: AppColors.background,
                  border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(10),
                    borderSide: const BorderSide(color: AppColors.border),
                  ),
                ),
              ),
              const SizedBox(height: 14),
              Text('APP DISPLAY NAME', style: AppTypography.bodySmall),
              const SizedBox(height: 6),
              TextField(
                controller: _appNameController,
                style: AppTypography.bodyMedium,
                decoration: InputDecoration(
                  hintText: 'e.g. TikTok',
                  hintStyle: AppTypography.bodySmall,
                  filled: true,
                  fillColor: AppColors.background,
                  border: OutlineInputBorder(
                    borderRadius: BorderRadius.circular(10),
                    borderSide: const BorderSide(color: AppColors.border),
                  ),
                ),
              ),
              const SizedBox(height: 16),
            ],
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text('DAILY TIME CAP', style: AppTypography.bodySmall),
                Text('$_capMinutes minutes',
                    style: AppTypography.accentMono.copyWith(
                      color: AppColors.primaryAccent,
                      fontWeight: FontWeight.bold,
                    )),
              ],
            ),
            Slider(
              value: _capMinutes.toDouble(),
              min: 10,
              max: 180,
              divisions: 17,
              activeColor: AppColors.primaryAccent,
              onChanged: (val) => setState(() => _capMinutes = val.round()),
            ),
            const SizedBox(height: 8),
            SwitchListTile.adaptive(
              contentPadding: EdgeInsets.zero,
              dense: true,
              title: Text('Require Stoic Reflection Lockout',
                  style: AppTypography.bodyMedium),
              subtitle: Text(
                  'Typing verification required to override limits',
                  style: AppTypography.bodySmall.copyWith(fontSize: 11)),
              value: _isEssayLocked,
              activeTrackColor: AppColors.primaryAccent,
              onChanged: (val) => setState(() => _isEssayLocked = val),
            ),
            SwitchListTile.adaptive(
              contentPadding: EdgeInsets.zero,
              dense: true,
              title: Text('Block Short-form Video Feed',
                  style: AppTypography.bodyMedium),
              subtitle: Text(
                  'Intercepts algorithmic reels/shorts vertical tabs',
                  style: AppTypography.bodySmall.copyWith(fontSize: 11)),
              value: _blockShorts,
              activeTrackColor: AppColors.primaryAccent,
              onChanged: (val) => setState(() => _blockShorts = val),
            ),
            const SizedBox(height: 16),
            ElevatedButton(
              onPressed: _saveBlock,
              style: ElevatedButton.styleFrom(
                backgroundColor: AppColors.primaryAccent,
                foregroundColor: AppColors.background,
                padding: const EdgeInsets.symmetric(vertical: 14),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(12),
                ),
              ),
              child: const Text('Add Cognitive Defense Shield',
                  style: TextStyle(fontWeight: FontWeight.bold, fontSize: 14)),
            ),
          ],
        ),
      ),
    );
  }
}

// Custom Painter for Vertical Histogram Usage Bar Chart
class ScreenTimeChartPainter extends CustomPainter {
  final List<int> values;
  final List<String> labels;
  final int selectedIndex;
  final int averageValue;

  ScreenTimeChartPainter({
    required this.values,
    required this.labels,
    required this.selectedIndex,
    required this.averageValue,
  });

  @override
  void paint(Canvas canvas, Size size) {
    if (values.isEmpty) return;

    final maxVal = math.max(values.reduce(math.max), averageValue) * 1.25;
    final safeMax = maxVal == 0 ? 10.0 : maxVal;
    final barWidth = (size.width / values.length) * 0.45;
    final slotWidth = size.width / values.length;
    final chartHeight = size.height - 24;

    // Draw average dashed line
    final avgY = chartHeight - (averageValue / safeMax * chartHeight);
    final avgPaint = Paint()
      ..color = AppColors.primaryAccent.withAlpha(120)
      ..strokeWidth = 1.2
      ..style = PaintingStyle.stroke;

    double startX = 0;
    while (startX < size.width) {
      canvas.drawLine(
        Offset(startX, avgY),
        Offset(startX + 6, avgY),
        avgPaint,
      );
      startX += 10;
    }

    // Draw bars
    for (int i = 0; i < values.length; i++) {
      final val = values[i];
      final barHeight = (val / safeMax) * chartHeight;
      final x = (i * slotWidth) + (slotWidth - barWidth) / 2;
      final y = chartHeight - barHeight;
      final isSelected = i == selectedIndex;

      final barRect = RRect.fromRectAndRadius(
        Rect.fromLTWH(x, y, barWidth, barHeight),
        const Radius.circular(6),
      );

      final barPaint = Paint()
        ..shader = LinearGradient(
          colors: isSelected
              ? const [Color(0xFFF59E0B), Color(0xFF8B5CF6)]
              : [
                  AppColors.surfaceElevated,
                  AppColors.primaryAccent.withAlpha(90),
                ],
          begin: Alignment.bottomCenter,
          end: Alignment.topCenter,
        ).createShader(Rect.fromLTWH(x, y, barWidth, barHeight));

      canvas.drawRRect(barRect, barPaint);

      // Label text
      final textSpan = TextSpan(
        text: labels[i],
        style: TextStyle(
          color: isSelected ? AppColors.primaryAccent : AppColors.textSecondary,
          fontSize: 10,
          fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
        ),
      );
      final textPainter = TextPainter(
        text: textSpan,
        textDirection: TextDirection.ltr,
      )..layout();
      textPainter.paint(
        canvas,
        Offset(x + (barWidth - textPainter.width) / 2, chartHeight + 6),
      );
    }
  }

  @override
  bool shouldRepaint(covariant ScreenTimeChartPainter oldDelegate) {
    return oldDelegate.selectedIndex != selectedIndex ||
        oldDelegate.values != values ||
        oldDelegate.averageValue != averageValue;
  }
}
