import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/constants/app_colors.dart';
import '../../../../core/constants/app_typography.dart';
import '../../../../data/local/app_database.dart';
import '../controllers/timetable_controller.dart';
import '../widgets/add_timetable_modal.dart';

enum TimetableLayout { horizontalMatrix, verticalGrid, agendaList }

class TimetableScreen extends ConsumerStatefulWidget {
  const TimetableScreen({super.key});

  @override
  ConsumerState<TimetableScreen> createState() => _TimetableScreenState();
}

class _TimetableScreenState extends ConsumerState<TimetableScreen> {
  static const double _hourHeight = 64.0;
  static const double _hourWidth = 100.0;

  final ScrollController _verticalScrollController = ScrollController();
  final ScrollController _horizontalScrollController = ScrollController();

  TimetableLayout _layout = TimetableLayout.horizontalMatrix;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _scrollToCurrentTime();
    });
  }

  @override
  void dispose() {
    _verticalScrollController.dispose();
    _horizontalScrollController.dispose();
    super.dispose();
  }

  void _scrollToCurrentTime() {
    final now = DateTime.now();
    if (_horizontalScrollController.hasClients) {
      final hOffset = math.max(0.0, (now.hour - 1) * _hourWidth);
      _horizontalScrollController.jumpTo(hOffset);
    }
    if (_verticalScrollController.hasClients) {
      final vOffset = math.max(0.0, (now.hour - 1) * _hourHeight);
      _verticalScrollController.jumpTo(vOffset);
    }
  }

  String _formatDisplayDate(DateTime date) {
    const months = [
      'Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun',
      'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'
    ];
    return '${months[date.month - 1]} ${date.day}, ${date.year}';
  }

  int _parseTimeToMinutes(String timeStr) {
    final parts = timeStr.split(':');
    final hour = int.tryParse(parts[0]) ?? 0;
    final min = int.tryParse(parts.length > 1 ? parts[1] : '0') ?? 0;
    return hour * 60 + min;
  }

  Future<void> _showRescheduleDialog(
    BuildContext context,
    TimetableEntry entry,
  ) async {
    final startParts = entry.startTime.split(':');
    final endParts = entry.endTime.split(':');

    TimeOfDay startTime = TimeOfDay(
      hour: int.tryParse(startParts[0]) ?? 9,
      minute: int.tryParse(startParts.length > 1 ? startParts[1] : '0') ?? 0,
    );
    TimeOfDay endTime = TimeOfDay(
      hour: int.tryParse(endParts[0]) ?? 10,
      minute: int.tryParse(endParts.length > 1 ? endParts[1] : '0') ?? 0,
    );

    await showDialog(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (context, setDialogState) {
          String formatTime(TimeOfDay t) =>
              '${t.hour.toString().padLeft(2, '0')}:${t.minute.toString().padLeft(2, '0')}';

          return AlertDialog(
            backgroundColor: AppColors.surface,
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(16),
              side: const BorderSide(color: AppColors.border),
            ),
            title: Text('Reschedule: ${entry.title}', style: AppTypography.h2),
            content: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  'Choose new time slot for this session:',
                  style: AppTypography.bodySmall,
                ),
                const SizedBox(height: 16),
                Row(
                  children: [
                    Expanded(
                      child: OutlinedButton(
                        onPressed: () async {
                          final picked = await showTimePicker(
                            context: context,
                            initialTime: startTime,
                          );
                          if (picked != null) {
                            setDialogState(() => startTime = picked);
                          }
                        },
                        style: OutlinedButton.styleFrom(
                          side: const BorderSide(color: AppColors.border),
                          padding: const EdgeInsets.symmetric(vertical: 12),
                        ),
                        child: Text(
                          'Start: ${formatTime(startTime)}',
                          style: AppTypography.bodySmall.copyWith(
                            color: AppColors.primaryAccent,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                      ),
                    ),
                    const Padding(
                      padding: EdgeInsets.symmetric(horizontal: 8),
                      child: Text('–',
                          style: TextStyle(color: AppColors.textSecondary)),
                    ),
                    Expanded(
                      child: OutlinedButton(
                        onPressed: () async {
                          final picked = await showTimePicker(
                            context: context,
                            initialTime: endTime,
                          );
                          if (picked != null) {
                            setDialogState(() => endTime = picked);
                          }
                        },
                        style: OutlinedButton.styleFrom(
                          side: const BorderSide(color: AppColors.border),
                          padding: const EdgeInsets.symmetric(vertical: 12),
                        ),
                        child: Text(
                          'End: ${formatTime(endTime)}',
                          style: AppTypography.bodySmall.copyWith(
                            color: AppColors.primaryAccent,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
              ],
            ),
            actions: [
              TextButton(
                onPressed: () => Navigator.pop(ctx),
                child: const Text(
                  'Cancel',
                  style: TextStyle(color: AppColors.textSecondary),
                ),
              ),
              ElevatedButton(
                style: ElevatedButton.styleFrom(
                  backgroundColor: AppColors.primaryAccent,
                  foregroundColor: AppColors.background,
                ),
                onPressed: () async {
                  await ref.read(timetableControllerProvider).rescheduleEntry(
                        id: entry.id,
                        newStart: formatTime(startTime),
                        newEnd: formatTime(endTime),
                      );
                  if (ctx.mounted) Navigator.pop(ctx);
                },
                child: Text(
                  'Save Time',
                  style: AppTypography.bodyMedium.copyWith(
                    fontWeight: FontWeight.bold,
                    color: AppColors.background,
                  ),
                ),
              ),
            ],
          );
        },
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final selectedDate = ref.watch(selectedTimetableDateProvider);
    final timetableAsync = ref.watch(timetableEntriesStreamProvider);

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        backgroundColor: AppColors.background,
        elevation: 0,
        scrolledUnderElevation: 0,
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Timetable & Matrix', style: AppTypography.h1),
            const SizedBox(height: 2),
            Text(
              _formatDisplayDate(selectedDate),
              style: AppTypography.bodySmall,
            ),
          ],
        ),
        actions: [
          PopupMenuButton<TimetableLayout>(
            tooltip: 'Change Layout',
            icon: const Icon(
              Icons.dashboard_customize_rounded,
              color: AppColors.textSecondary,
            ),
            onSelected: (l) => setState(() => _layout = l),
            itemBuilder: (context) => [
              const PopupMenuItem(
                value: TimetableLayout.horizontalMatrix,
                child: Row(
                  children: [
                    Icon(Icons.view_column_rounded, size: 18, color: AppColors.primaryAccent),
                    SizedBox(width: 8),
                    Text('Horizontal Matrix'),
                  ],
                ),
              ),
              const PopupMenuItem(
                value: TimetableLayout.verticalGrid,
                child: Row(
                  children: [
                    Icon(Icons.calendar_view_day_rounded, size: 18, color: AppColors.primaryAccent),
                    SizedBox(width: 8),
                    Text('24-Hour Vertical Grid'),
                  ],
                ),
              ),
              const PopupMenuItem(
                value: TimetableLayout.agendaList,
                child: Row(
                  children: [
                    Icon(Icons.view_agenda_outlined, size: 18, color: AppColors.primaryAccent),
                    SizedBox(width: 8),
                    Text('Agenda List'),
                  ],
                ),
              ),
            ],
          ),
          IconButton(
            onPressed: () => AddTimetableModal.show(context),
            icon: const Icon(
              Icons.add_circle_outline_rounded,
              color: AppColors.primaryAccent,
              size: 26,
            ),
            tooltip: 'Add Schedule Event',
          ),
          const SizedBox(width: 8),
        ],
      ),
      body: SafeArea(
        child: Column(
          children: [
            // Top: 14-day horizontal date selector
            _buildCalendarStrip(context, selectedDate),
            const SizedBox(height: 8),

            // Timetable View
            Expanded(
              child: timetableAsync.when(
                loading: () => const Center(
                  child: CircularProgressIndicator(
                    color: AppColors.primaryAccent,
                  ),
                ),
                error: (err, _) => Center(
                  child: Padding(
                    padding: const EdgeInsets.all(16.0),
                    child: Text(
                      'Error loading schedule: $err',
                      style: AppTypography.bodyMedium.copyWith(
                        color: AppColors.errorRed,
                      ),
                    ),
                  ),
                ),
                data: (entries) {
                  if (entries.isEmpty) {
                    return _buildEmptyState(context);
                  }

                  switch (_layout) {
                    case TimetableLayout.horizontalMatrix:
                      return _buildHorizontalMatrix(selectedDate, entries);
                    case TimetableLayout.verticalGrid:
                      return _build24HourVerticalGrid(selectedDate, entries);
                    case TimetableLayout.agendaList:
                      return _buildAgendaList(entries);
                  }
                },
              ),
            ),
          ],
        ),
      ),
    );
  }

  // 14-Day Horizontal Date Selector with Day Abbreviations and Date Circles
  Widget _buildCalendarStrip(BuildContext context, DateTime selectedDate) {
    final today = DateTime.now();
    final startDate = today.subtract(const Duration(days: 3));
    const weekdays = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];

    return Container(
      height: 76,
      padding: const EdgeInsets.symmetric(vertical: 4),
      decoration: const BoxDecoration(
        color: AppColors.surface,
        border: Border(
          bottom: BorderSide(color: AppColors.border, width: 0.8),
        ),
      ),
      child: ListView.builder(
        scrollDirection: Axis.horizontal,
        padding: const EdgeInsets.symmetric(horizontal: 12),
        itemCount: 14,
        itemBuilder: (context, index) {
          final date = startDate.add(Duration(days: index));
          final isSelected = date.year == selectedDate.year &&
              date.month == selectedDate.month &&
              date.day == selectedDate.day;
          final isToday = date.year == today.year &&
              date.month == today.month &&
              date.day == today.day;

          return GestureDetector(
            onTap: () {
              ref.read(selectedTimetableDateProvider.notifier).state = date;
            },
            child: AnimatedContainer(
              duration: const Duration(milliseconds: 150),
              width: 54,
              margin: const EdgeInsets.symmetric(horizontal: 4, vertical: 2),
              decoration: BoxDecoration(
                color: isSelected
                    ? AppColors.primaryAccent
                    : (isToday ? AppColors.surfaceElevated : Colors.transparent),
                borderRadius: BorderRadius.circular(12),
                border: Border.all(
                  color: isSelected
                      ? AppColors.primaryAccent
                      : (isToday ? AppColors.border : Colors.transparent),
                ),
              ),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Text(
                    weekdays[date.weekday - 1],
                    style: AppTypography.bodySmall.copyWith(
                      fontSize: 11,
                      fontWeight:
                          isSelected ? FontWeight.bold : FontWeight.normal,
                      color: isSelected
                          ? AppColors.background
                          : AppColors.textSecondary,
                    ),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    '${date.day}',
                    style: AppTypography.accentMono.copyWith(
                      fontSize: 16,
                      fontWeight: FontWeight.bold,
                      color: isSelected
                          ? AppColors.background
                          : AppColors.textPrimary,
                    ),
                  ),
                ],
              ),
            ),
          );
        },
      ),
    );
  }

  // 1. HORIZONTAL TIMETABLE MATRIX (Deliverable for Subagent 3)
  Widget _buildHorizontalMatrix(
    DateTime selectedDate,
    List<TimetableEntry> entries,
  ) {
    final now = DateTime.now();
    final isSelectedToday = selectedDate.year == now.year &&
        selectedDate.month == now.month &&
        selectedDate.day == now.day;
    final currentMinutes = now.hour * 60 + now.minute;
    final redLineX = (currentMinutes / 60.0) * _hourWidth;

    const accentPalette = [
      Color(0xFF3B82F6), // Blue
      Color(0xFF10B981), // Emerald
      Color(0xFF8B5CF6), // Purple
      Color(0xFFF59E0B), // Amber
      Color(0xFFEC4899), // Pink
      Color(0xFF06B6D4), // Cyan
    ];

    const totalTrackWidth = 24 * _hourWidth;

    return SingleChildScrollView(
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 6),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text('24-HOUR HORIZONTAL TIMELINE', style: AppTypography.bodySmall),
                Text(
                  'Scroll ↔ to browse entire day',
                  style: AppTypography.bodySmall.copyWith(
                    color: AppColors.primaryAccent,
                    fontSize: 11,
                  ),
                ),
              ],
            ),
          ),

          // Horizontally Scrollable 24-Hour Track
          SingleChildScrollView(
            controller: _horizontalScrollController,
            scrollDirection: Axis.horizontal,
            child: Container(
              width: totalTrackWidth,
              height: 280,
              margin: const EdgeInsets.symmetric(vertical: 8),
              decoration: BoxDecoration(
                color: AppColors.surface,
                border: Border.all(color: AppColors.border),
              ),
              child: Stack(
                children: [
                  // 24 Hourly columns background & labels
                  Row(
                    children: List.generate(24, (hour) {
                      final hourStr = '${hour.toString().padLeft(2, '0')}:00';
                      return Container(
                        width: _hourWidth,
                        height: double.infinity,
                        decoration: BoxDecoration(
                          border: Border(
                            right: BorderSide(
                              color: AppColors.border.withAlpha(80),
                              width: 0.8,
                            ),
                          ),
                        ),
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Container(
                              padding: const EdgeInsets.symmetric(
                                horizontal: 6,
                                vertical: 4,
                              ),
                              child: Text(
                                hourStr,
                                style: AppTypography.accentMono.copyWith(
                                  fontSize: 11,
                                  color: AppColors.textSecondary,
                                ),
                              ),
                            ),
                          ],
                        ),
                      );
                    }),
                  ),

                  // Horizontal Cards Placed Proportionally
                  ...entries.asMap().entries.map((entryItem) {
                    final index = entryItem.key;
                    final entry = entryItem.value;
                    final startMin = _parseTimeToMinutes(entry.startTime);
                    var endMin = _parseTimeToMinutes(entry.endTime);
                    if (endMin <= startMin) endMin = startMin + 60;

                    final left = (startMin / 60.0) * _hourWidth;
                    final cardWidth = math.max(
                      ((endMin - startMin) / 60.0) * _hourWidth - 6,
                      76.0,
                    );
                    final accentColor =
                        accentPalette[index % accentPalette.length];

                    // Stack lane calculation (alternate vertical height for overlapping cards)
                    final top = 36.0 + ((index % 3) * 72.0);

                    return Positioned(
                      left: left,
                      top: top,
                      width: cardWidth,
                      height: 64,
                      child: GestureDetector(
                        onTap: () => _showRescheduleDialog(context, entry),
                        child: Container(
                          padding: const EdgeInsets.symmetric(
                            horizontal: 10,
                            vertical: 6,
                          ),
                          decoration: BoxDecoration(
                            color: AppColors.surfaceElevated,
                            borderRadius: BorderRadius.circular(10),
                            border: Border.all(
                              color: accentColor.withAlpha(140),
                              width: 1.2,
                            ),
                            boxShadow: [
                              BoxShadow(
                                color: Colors.black.withAlpha(50),
                                blurRadius: 4,
                                offset: const Offset(0, 2),
                              ),
                            ],
                          ),
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: [
                              Row(
                                children: [
                                  Container(
                                    width: 4,
                                    height: 12,
                                    decoration: BoxDecoration(
                                      color: accentColor,
                                      borderRadius: BorderRadius.circular(2),
                                    ),
                                  ),
                                  const SizedBox(width: 6),
                                  Expanded(
                                    child: Text(
                                      entry.title,
                                      style: AppTypography.bodySmall.copyWith(
                                        fontWeight: FontWeight.bold,
                                        color: AppColors.textPrimary,
                                        fontSize: 12,
                                      ),
                                      maxLines: 1,
                                      overflow: TextOverflow.ellipsis,
                                    ),
                                  ),
                                ],
                              ),
                              const SizedBox(height: 2),
                              Text(
                                '${entry.startTime} – ${entry.endTime}',
                                style: AppTypography.accentMono.copyWith(
                                  fontSize: 10,
                                  color: accentColor,
                                ),
                              ),
                            ],
                          ),
                        ),
                      ),
                    );
                  }),

                  // Vertical Red Indicator Line Marking Current Real-Time
                  if (isSelectedToday)
                    Positioned(
                      left: redLineX,
                      top: 0,
                      bottom: 0,
                      child: Column(
                        children: [
                          Container(
                            padding: const EdgeInsets.symmetric(
                              horizontal: 4,
                              vertical: 2,
                            ),
                            decoration: BoxDecoration(
                              color: AppColors.errorRed,
                              borderRadius: BorderRadius.circular(4),
                            ),
                            child: const Text(
                              'NOW',
                              style: TextStyle(
                                fontSize: 9,
                                fontWeight: FontWeight.bold,
                                color: Colors.white,
                              ),
                            ),
                          ),
                          Expanded(
                            child: Container(
                              width: 2,
                              color: AppColors.errorRed,
                            ),
                          ),
                        ],
                      ),
                    ),
                ],
              ),
            ),
          ),

          // Agenda list view below for quick glance
          Padding(
            padding: const EdgeInsets.all(16.0),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('DAY AGENDA SESSIONS', style: AppTypography.bodySmall),
                const SizedBox(height: 10),
                ...entries.map((item) => _buildEventCard(context, item)),
              ],
            ),
          ),
        ],
      ),
    );
  }

  // 24-Hour Vertical Visual Grid
  Widget _build24HourVerticalGrid(
    DateTime selectedDate,
    List<TimetableEntry> entries,
  ) {
    final now = DateTime.now();
    final isSelectedToday = selectedDate.year == now.year &&
        selectedDate.month == now.month &&
        selectedDate.day == now.day;
    final currentMinutes = now.hour * 60 + now.minute;
    final currentLineTop = (currentMinutes / 60.0) * _hourHeight;

    const accentPalette = [
      Color(0xFF3B82F6), Color(0xFF10B981), Color(0xFF8B5CF6),
      Color(0xFFF59E0B), Color(0xFFEC4899), Color(0xFF06B6D4),
    ];

    return SingleChildScrollView(
      controller: _verticalScrollController,
      padding: const EdgeInsets.only(bottom: 80),
      child: SizedBox(
        height: 24 * _hourHeight,
        child: Stack(
          children: [
            ...List.generate(24, (hour) {
              final top = hour * _hourHeight;
              final hourText = '${hour.toString().padLeft(2, '0')}:00';
              return Positioned(
                top: top,
                left: 0,
                right: 0,
                height: _hourHeight,
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    SizedBox(
                      width: 54,
                      child: Padding(
                        padding: const EdgeInsets.only(left: 10, top: 4),
                        child: Text(
                          hourText,
                          style: AppTypography.bodySmall.copyWith(
                            fontSize: 11,
                            color: AppColors.textSecondary.withAlpha(160),
                            fontFamily: 'monospace',
                          ),
                        ),
                      ),
                    ),
                    Expanded(
                      child: Container(
                        decoration: const BoxDecoration(
                          border: Border(
                            top: BorderSide(
                              color: AppColors.border,
                              width: 0.6,
                            ),
                          ),
                        ),
                      ),
                    ),
                  ],
                ),
              );
            }),

            ...entries.asMap().entries.map((entryItem) {
              final index = entryItem.key;
              final entry = entryItem.value;
              final startMin = _parseTimeToMinutes(entry.startTime);
              var endMin = _parseTimeToMinutes(entry.endTime);
              if (endMin <= startMin) endMin = startMin + 60;

              final top = (startMin / 60.0) * _hourHeight;
              final blockHeight = math.max(
                ((endMin - startMin) / 60.0) * _hourHeight - 2,
                30.0,
              );
              final accentColor = accentPalette[index % accentPalette.length];

              return Positioned(
                top: top,
                left: 60,
                right: 16,
                height: blockHeight,
                child: GestureDetector(
                  onTap: () => _showRescheduleDialog(context, entry),
                  child: Container(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 10,
                      vertical: 6,
                    ),
                    decoration: BoxDecoration(
                      color: AppColors.surface,
                      borderRadius: BorderRadius.circular(8),
                      border: Border.all(
                        color: accentColor.withAlpha(120),
                        width: 1.2,
                      ),
                    ),
                    child: Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Container(
                          width: 4,
                          height: double.infinity,
                          decoration: BoxDecoration(
                            color: accentColor,
                            borderRadius: BorderRadius.circular(2),
                          ),
                        ),
                        const SizedBox(width: 8),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              Text(
                                entry.title,
                                style: AppTypography.h2.copyWith(fontSize: 13),
                                maxLines: 1,
                                overflow: TextOverflow.ellipsis,
                              ),
                              Text(
                                '${entry.startTime} – ${entry.endTime}',
                                style: AppTypography.accentMono.copyWith(
                                  fontSize: 11,
                                  color: accentColor,
                                ),
                              ),
                            ],
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              );
            }),

            if (isSelectedToday)
              Positioned(
                top: currentLineTop,
                left: 48,
                right: 0,
                child: Row(
                  children: [
                    Container(
                      width: 8,
                      height: 8,
                      decoration: const BoxDecoration(
                        color: AppColors.errorRed,
                        shape: BoxShape.circle,
                      ),
                    ),
                    Expanded(
                      child: Container(
                        height: 2,
                        color: AppColors.errorRed,
                      ),
                    ),
                  ],
                ),
              ),
          ],
        ),
      ),
    );
  }

  // Agenda List View
  Widget _buildAgendaList(List<TimetableEntry> entries) {
    return ListView.builder(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 90),
      itemCount: entries.length,
      itemBuilder: (context, index) {
        final item = entries[index];
        return _buildEventCard(context, item);
      },
    );
  }

  Widget _buildEventCard(BuildContext context, TimetableEntry entry) {
    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: AppColors.border),
      ),
      child: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Row(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
              decoration: BoxDecoration(
                color: AppColors.surfaceElevated,
                borderRadius: BorderRadius.circular(8),
                border: Border.all(
                  color: AppColors.primaryAccent.withAlpha(80),
                ),
              ),
              child: Column(
                children: [
                  Text(
                    entry.startTime,
                    style: AppTypography.accentMono.copyWith(
                      fontSize: 12,
                      fontWeight: FontWeight.bold,
                      color: AppColors.primaryAccent,
                    ),
                  ),
                  Text(
                    'to',
                    style: AppTypography.bodySmall.copyWith(fontSize: 10),
                  ),
                  Text(
                    entry.endTime,
                    style: AppTypography.accentMono.copyWith(
                      fontSize: 12,
                      fontWeight: FontWeight.bold,
                      color: AppColors.primaryAccent,
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(width: 14),

            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    entry.title,
                    style: AppTypography.h2.copyWith(fontSize: 16),
                  ),
                  const SizedBox(height: 6),
                  if (entry.locationOrNotes != null &&
                      entry.locationOrNotes!.isNotEmpty) ...[
                    Row(
                      children: [
                        const Icon(
                          Icons.location_on_outlined,
                          size: 14,
                          color: AppColors.textSecondary,
                        ),
                        const SizedBox(width: 4),
                        Expanded(
                          child: Text(
                            entry.locationOrNotes!,
                            style: AppTypography.bodySmall,
                            maxLines: 2,
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 6),
                  ],
                  Container(
                    padding:
                        const EdgeInsets.symmetric(horizontal: 7, vertical: 2),
                    decoration: BoxDecoration(
                      color: AppColors.surfaceElevated,
                      borderRadius: BorderRadius.circular(6),
                    ),
                    child: Text(
                      entry.dayOfWeek > 0 ? 'Weekly Recurring' : 'One-off Event',
                      style: AppTypography.bodySmall.copyWith(
                        fontSize: 10,
                        color: AppColors.textSecondary,
                      ),
                    ),
                  ),
                ],
              ),
            ),

            PopupMenuButton<String>(
              color: AppColors.surfaceElevated,
              icon: const Icon(
                Icons.more_vert_rounded,
                color: AppColors.textSecondary,
              ),
              onSelected: (val) async {
                if (val == 'reschedule') {
                  await _showRescheduleDialog(context, entry);
                } else if (val == 'delete') {
                  await ref
                      .read(timetableControllerProvider)
                      .deleteEntry(entry.id);
                }
              },
              itemBuilder: (context) => [
                const PopupMenuItem(
                  value: 'reschedule',
                  child: Row(
                    children: [
                      Icon(Icons.schedule_rounded,
                          size: 16, color: AppColors.primaryAccent),
                      SizedBox(width: 8),
                      Text('Reschedule'),
                    ],
                  ),
                ),
                const PopupMenuItem(
                  value: 'delete',
                  child: Row(
                    children: [
                      Icon(Icons.delete_outline_rounded,
                          size: 16, color: AppColors.errorRed),
                      SizedBox(width: 8),
                      Text('Delete', style: TextStyle(color: AppColors.errorRed)),
                    ],
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildEmptyState(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 32.0),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Container(
              padding: const EdgeInsets.all(20),
              decoration: BoxDecoration(
                color: AppColors.surface,
                shape: BoxShape.circle,
                border: Border.all(color: AppColors.border),
              ),
              child: const Icon(
                Icons.event_available_rounded,
                size: 40,
                color: AppColors.primaryAccent,
              ),
            ),
            const SizedBox(height: 18),
            Text('No Scheduled Events', style: AppTypography.h2),
            const SizedBox(height: 8),
            Text(
              'No classes, syncs, or lectures scheduled for this day.',
              textAlign: TextAlign.center,
              style: AppTypography.bodySmall,
            ),
            const SizedBox(height: 20),
            ElevatedButton.icon(
              onPressed: () => AddTimetableModal.show(context),
              icon: const Icon(Icons.add_rounded, size: 18),
              label: Text(
                'Schedule Event',
                style: AppTypography.bodyMedium.copyWith(
                  fontWeight: FontWeight.bold,
                  color: AppColors.background,
                ),
              ),
              style: ElevatedButton.styleFrom(
                backgroundColor: AppColors.primaryAccent,
                foregroundColor: AppColors.background,
                padding:
                    const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(10),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
