import 'dart:math' as math;

import 'package:drift/drift.dart' as drift;
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/constants/app_colors.dart';
import '../../../../core/constants/app_typography.dart';
import '../../../../data/local/app_database.dart';
import '../../../../data/local/database_provider.dart';
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
  final int _selectedBarIndex = 5; // Highlight Friday / Today by default
  final Set<String> _expandedCategories = {'Social & Video', 'Movies & Video'};

  // Sample realistic tracking cohort
  final List<AppUsageData> _appsUsage = const [
    AppUsageData(
      appName: 'Instagram',
      packageName: 'com.instagram.android',
      usageMinutes: 98,
      category: 'Social & Video',
      icon: Icons.camera_alt_rounded,
      color: Color(0xFFE1306C),
    ),
    AppUsageData(
      appName: 'YouTube',
      packageName: 'com.google.android.youtube',
      usageMinutes: 111,
      category: 'Movies & Video',
      icon: Icons.play_arrow_rounded,
      color: Color(0xFFFF0000),
    ),
    AppUsageData(
      appName: 'WhatsApp',
      packageName: 'com.whatsapp',
      usageMinutes: 44,
      category: 'Productivity & Communication',
      icon: Icons.chat_rounded,
      color: Color(0xFF25D366),
    ),
    AppUsageData(
      appName: 'Discord',
      packageName: 'com.discord',
      usageMinutes: 32,
      category: 'Productivity & Communication',
      icon: Icons.forum_rounded,
      color: Color(0xFF5865F2),
    ),
    AppUsageData(
      appName: 'X / Twitter',
      packageName: 'com.twitter.android',
      usageMinutes: 28,
      category: 'Social & Video',
      icon: Icons.alternate_email_rounded,
      color: Color(0xFF1DA1F2),
    ),
  ];

  final List<Map<String, dynamic>> _defaultApps = [
    {
      'id': 'instagram_limit',
      'packageName': 'com.instagram.android',
      'appName': 'Instagram',
      'cap': 45,
      'blockReels': true,
      'blockShorts': false,
      'essayLock': true,
      'curfewStart': '22:30',
      'curfewEnd': '07:00',
    },
    {
      'id': 'youtube_limit',
      'packageName': 'com.google.android.youtube',
      'appName': 'YouTube',
      'cap': 60,
      'blockReels': false,
      'blockShorts': true,
      'essayLock': true,
      'curfewStart': '23:00',
      'curfewEnd': '07:00',
    },
    {
      'id': 'whatsapp_limit',
      'packageName': 'com.whatsapp',
      'appName': 'WhatsApp',
      'cap': 90,
      'blockReels': false,
      'blockShorts': false,
      'essayLock': false,
      'curfewStart': '23:30',
      'curfewEnd': '06:30',
    },
    {
      'id': 'discord_limit',
      'packageName': 'com.discord',
      'appName': 'Discord',
      'cap': 45,
      'blockReels': false,
      'blockShorts': false,
      'essayLock': true,
      'curfewStart': '22:00',
      'curfewEnd': '07:00',
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
            curfewStart: drift.Value(a['curfewStart'] as String),
            curfewEnd: drift.Value(a['curfewEnd'] as String),
            blockReelsShorts: drift.Value(a['blockReels'] as bool),
            isEssayLocked: drift.Value(a['essayLock'] as bool),
          ),
        );
      }
    }
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

  @override
  Widget build(BuildContext context) {
    final db = ref.watch(appDatabaseProvider);
    final limitsStream = db.watchAppLimits();

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
            tooltip: 'Trigger Stoic Lockout',
            icon: const Icon(Icons.shield_rounded, color: AppColors.errorRed),
            onPressed: () => EssayLockoutDialog.show(context),
          ),
          const SizedBox(width: 8),
        ],
      ),
      body: StreamBuilder<List<AppLimit>>(
        stream: limitsStream,
        builder: (context, snapshot) {
          final limits = snapshot.data ?? [];

          return ListView(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 80),
            children: [
              // Segment Picker: [Week] | [Day] | [Hour]
              _buildSegmentPicker(),
              const SizedBox(height: 16),

              // Date Navigation Header: < Today, October 13 • 4h 54m >
              _buildDateNavigationHeader(),
              const SizedBox(height: 16),

              // Vertical Histogram Usage Bar Chart
              _buildUsageBarChartCard(),
              const SizedBox(height: 22),

              // Most Used Apps & Expandable Categories
              _buildMostUsedSection(),
              const SizedBox(height: 24),

              // Granular Limits & Social Feed Toggles
              _buildLimitsSection(limits),
            ],
          );
        },
      ),
    );
  }

  // Pill Segment Selector with Amber/Violet Gradient Indicator
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

  // Date Navigation Header: < Today, October 13 • 4h 54m >
  Widget _buildDateNavigationHeader() {
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
                  '4h 54m',
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

  // Usage Bar Chart with Average Line
  Widget _buildUsageBarChartCard() {
    final barData = _selectedSegment == TimeSegment.week
        ? [190, 240, 310, 280, 294, 210, 180]
        : _selectedSegment == TimeSegment.day
            ? [15, 30, 45, 60, 40, 55, 35, 14]
            : [10, 20, 25, 40, 15, 30];

    final labels = _selectedSegment == TimeSegment.week
        ? ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']
        : _selectedSegment == TimeSegment.day
            ? ['6A', '8A', '10A', '12P', '2P', '4P', '6P', '8P']
            : ['10m', '20m', '30m', '40m', '50m', '60m'];

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
                  Text('4h 54m Active Screen Time',
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
                      'Avg 3h 15m',
                      style: AppTypography.accentMono.copyWith(fontSize: 11),
                    ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 20),

          // Custom Painted Bar Chart
          SizedBox(
            height: 160,
            child: CustomPaint(
              size: const Size(double.infinity, 160),
              painter: ScreenTimeChartPainter(
                values: barData,
                labels: labels,
                selectedIndex: _selectedBarIndex % barData.length,
                averageValue: 210,
              ),
            ),
          ),
        ],
      ),
    );
  }

  // Most Used Apps & Category Breakdown
  Widget _buildMostUsedSection() {
    final categories = <String, List<AppUsageData>>{};
    for (final app in _appsUsage) {
      categories.putIfAbsent(app.category, () => []).add(app);
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Text('MOST USED APPS & CATEGORIES', style: AppTypography.bodySmall),
            Text('4h 54m Total',
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
                // Category Header (Tap to expand/collapse)
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
                                : (catName.contains('Movies')
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
                                style: AppTypography.bodySmall.copyWith(fontSize: 11),
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

                // Expanded App Items
                if (isExpanded)
                  Padding(
                    padding: const EdgeInsets.fromLTRB(14, 0, 14, 14),
                    child: Column(
                      children: catApps.map((app) {
                        final ratio = (app.usageMinutes / 120).clamp(0.0, 1.0);

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
                                        style: AppTypography.bodySmall.copyWith(
                                          fontWeight: FontWeight.w600,
                                          color: AppColors.textPrimary,
                                        )),
                                    const SizedBox(height: 4),
                                    ClipRRect(
                                      borderRadius: BorderRadius.circular(3),
                                      child: LinearProgressIndicator(
                                        value: ratio,
                                        minHeight: 5,
                                        backgroundColor: AppColors.surfaceElevated,
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

  // Granular Limits, Curfews & Social Feeds Section
  Widget _buildLimitsSection(List<AppLimit> limits) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Text('GRANULAR LIMITS & CURFEWS', style: AppTypography.bodySmall),
            const Icon(Icons.lock_clock_rounded,
                size: 16, color: AppColors.primaryAccent),
          ],
        ),
        const SizedBox(height: 12),

        ...limits.map((item) {
          final isReelsTarget = item.appName.toLowerCase().contains('instagram');
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
                // Header & Duration Slider
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text(
                      item.appName,
                      style: AppTypography.h2.copyWith(fontSize: 16),
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

                // Dedicated Social Feed Interception Toggles
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

                // Require Stoic Lockout to Override
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

                // Curfew Schedule Pickers (Bedtime Start & End)
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
    final barWidth = (size.width / values.length) * 0.45;
    final slotWidth = size.width / values.length;
    final chartHeight = size.height - 24;

    // Draw average dashed line
    final avgY = chartHeight - (averageValue / maxVal * chartHeight);
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
      final barHeight = (val / maxVal) * chartHeight;
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
