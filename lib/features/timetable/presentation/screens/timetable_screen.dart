import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/constants/app_colors.dart';
import '../../../../core/constants/app_typography.dart';
import '../../../../data/local/app_database.dart';
import '../controllers/timetable_controller.dart';
import '../widgets/add_timetable_modal.dart';

class TimetableScreen extends ConsumerWidget {
  const TimetableScreen({super.key});

  String _formatDisplayDate(DateTime date) {
    const months = [
      'Jan',
      'Feb',
      'Mar',
      'Apr',
      'May',
      'Jun',
      'Jul',
      'Aug',
      'Sep',
      'Oct',
      'Nov',
      'Dec'
    ];
    return '${months[date.month - 1]} ${date.day}, ${date.year}';
  }

  Future<void> _showRescheduleDialog(
    BuildContext context,
    WidgetRef ref,
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
        builder: (context, setState) {
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
                            setState(() => startTime = picked);
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
                            setState(() => endTime = picked);
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
                child: Text('Cancel', style: AppTypography.bodyMedium),
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
  Widget build(BuildContext context, WidgetRef ref) {
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
            Text('Timetable & Calendar', style: AppTypography.h1),
            const SizedBox(height: 2),
            Text(
              _formatDisplayDate(selectedDate),
              style: AppTypography.bodySmall,
            ),
          ],
        ),
        actions: [
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
            // Horizontal Calendar Strip
            _buildCalendarStrip(context, ref, selectedDate),
            const SizedBox(height: 12),

            // Schedule Events List
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

                  return ListView.builder(
                    padding: const EdgeInsets.fromLTRB(16, 8, 16, 90),
                    itemCount: entries.length,
                    itemBuilder: (context, index) {
                      final item = entries[index];
                      return _buildEventCard(context, ref, item);
                    },
                  );
                },
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildCalendarStrip(
    BuildContext context,
    WidgetRef ref,
    DateTime selectedDate,
  ) {
    // Generate 14 days centered around today/selectedDate
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

  Widget _buildEventCard(
    BuildContext context,
    WidgetRef ref,
    TimetableEntry entry,
  ) {
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
            // Time Badge
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

            // Title & Details
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
                  // Recurrence Badge
                  Container(
                    padding:
                        const EdgeInsets.symmetric(horizontal: 7, vertical: 2),
                    decoration: BoxDecoration(
                      color: AppColors.surfaceElevated,
                      borderRadius: BorderRadius.circular(6),
                    ),
                    child: Text(
                      entry.dayOfWeek > 0
                          ? 'Weekly Recurring'
                          : 'One-off Event',
                      style: AppTypography.bodySmall.copyWith(
                        fontSize: 10,
                        color: AppColors.textSecondary,
                      ),
                    ),
                  ),
                ],
              ),
            ),

            // Context Actions Menu (Reschedule / Delete)
            PopupMenuButton<String>(
              color: AppColors.surfaceElevated,
              icon: const Icon(
                Icons.more_vert_rounded,
                color: AppColors.textSecondary,
              ),
              onSelected: (val) async {
                if (val == 'reschedule') {
                  await _showRescheduleDialog(context, ref, entry);
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
                      Text('Delete',
                          style: TextStyle(color: AppColors.errorRed)),
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
