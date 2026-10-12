import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/constants/app_colors.dart';
import '../../../../core/constants/app_typography.dart';
import '../../../../data/local/app_database.dart';
import '../../../blocker/presentation/screens/distraction_blocker_screen.dart';
import '../controllers/today_tasks_controller.dart';
import '../widgets/create_task_modal.dart';
import '../widgets/essay_lockout_dialog.dart';
import '../widgets/task_progress_card.dart';

class TodayScreen extends ConsumerWidget {
  const TodayScreen({super.key});

  String _formatDisplayDate(DateTime date) {
    const days = [
      'Monday',
      'Tuesday',
      'Wednesday',
      'Thursday',
      'Friday',
      'Saturday',
      'Sunday'
    ];
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
    final dayName = days[date.weekday - 1];
    final monthName = months[date.month - 1];
    return '$dayName, $monthName ${date.day}';
  }

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final tasksAsync = ref.watch(todayTasksStreamProvider);
    final earnedXp = ref.watch(todayEarnedXpProvider);
    final earnedStars = ref.watch(todayEarnedStarsProvider);
    final todayDate = DateTime.now();

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        backgroundColor: AppColors.background,
        elevation: 0,
        scrolledUnderElevation: 0,
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              "Today's Focus & Habits",
              style: AppTypography.h1,
            ),
            const SizedBox(height: 2),
            Text(
              _formatDisplayDate(todayDate),
              style: AppTypography.bodySmall,
            ),
          ],
        ),
        actions: [
          IconButton(
            tooltip: 'Sync Linked Apps',
            onPressed: () async {
              await ref.read(todayTasksControllerProvider).syncAllLinkedApps();
              if (context.mounted) {
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(
                    content: Text('Synced all linked apps.'),
                    duration: Duration(seconds: 1),
                    backgroundColor: AppColors.surfaceElevated,
                  ),
                );
              }
            },
            icon: const Icon(
              Icons.sync_rounded,
              color: AppColors.textSecondary,
            ),
          ),
          IconButton(
            tooltip: 'App Limits & Curfew Blocker',
            onPressed: () => DistractionBlockerScreen.show(context),
            icon: const Icon(
              Icons.hourglass_bottom_rounded,
              color: AppColors.primaryAccent,
            ),
          ),
          IconButton(
            tooltip: 'Emergency Focus Lockout',
            onPressed: () => EssayLockoutDialog.show(context),
            icon: const Icon(
              Icons.shield_outlined,
              color: AppColors.textSecondary,
            ),
          ),
          const SizedBox(width: 8),
        ],
      ),
      floatingActionButton: FloatingActionButton(
        onPressed: () => CreateTaskModal.show(context),
        backgroundColor: AppColors.primaryAccent,
        foregroundColor: AppColors.background,
        elevation: 3,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
        child: const Icon(Icons.add_rounded, size: 28),
      ),
      body: SafeArea(
        child: Column(
          children: [
            // Daily Goal & Circular Progress Ring (Wireframe #2)
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 8.0),
              child: tasksAsync.maybeWhen(
                data: (tasks) {
                  final totalCount = tasks.length;
                  final completedCount = tasks.where((t) {
                    final target = t.task.targetDurationSeconds ?? 1800;
                    return (t.log?.isCompleted ?? false) ||
                        ((t.log?.durationLoggedSeconds ?? 0) >= target);
                  }).length;
                  final double progress = totalCount > 0 ? (completedCount / totalCount) : 0.0;
                  final int percent = (progress * 100).round();

                  return Container(
                    padding: const EdgeInsets.all(16),
                    decoration: BoxDecoration(
                      color: AppColors.surface,
                      borderRadius: BorderRadius.circular(16),
                      border: Border.all(color: AppColors.border),
                    ),
                    child: Row(
                      children: [
                        // Circular Progress Ring
                        SizedBox(
                          width: 72,
                          height: 72,
                          child: Stack(
                            alignment: Alignment.center,
                            children: [
                              CircularProgressIndicator(
                                value: totalCount == 0 ? 0.0 : progress,
                                strokeWidth: 7,
                                strokeCap: StrokeCap.round,
                                backgroundColor: AppColors.surfaceElevated,
                                valueColor: const AlwaysStoppedAnimation(
                                  AppColors.primaryAccent,
                                ),
                              ),
                              Column(
                                mainAxisSize: MainAxisSize.min,
                                children: [
                                  Text(
                                    '$percent%',
                                    style: AppTypography.accentMono.copyWith(
                                      fontSize: 16,
                                      fontWeight: FontWeight.bold,
                                      color: AppColors.textPrimary,
                                    ),
                                  ),
                                ],
                              ),
                            ],
                          ),
                        ),
                        const SizedBox(width: 18),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(
                                'Daily Goal Completion',
                                style: AppTypography.h2.copyWith(fontSize: 16),
                              ),
                              const SizedBox(height: 3),
                              Text(
                                totalCount == 0
                                    ? 'No habits scheduled for today.'
                                    : '$completedCount of $totalCount habits completed',
                                style: AppTypography.bodySmall,
                              ),
                              const SizedBox(height: 10),
                              // XP & Stars Badges
                              Wrap(
                                spacing: 8,
                                runSpacing: 6,
                                children: [
                                  Container(
                                    padding: const EdgeInsets.symmetric(
                                      horizontal: 8,
                                      vertical: 3,
                                    ),
                                    decoration: BoxDecoration(
                                      color: AppColors.primaryAccent.withAlpha(30),
                                      borderRadius: BorderRadius.circular(6),
                                      border: Border.all(
                                        color: AppColors.primaryAccent.withAlpha(80),
                                      ),
                                    ),
                                    child: Row(
                                      mainAxisSize: MainAxisSize.min,
                                      children: [
                                        const Icon(
                                          Icons.bolt_rounded,
                                          size: 14,
                                          color: AppColors.primaryAccent,
                                        ),
                                        const SizedBox(width: 4),
                                        Text(
                                          '$earnedXp XP',
                                          style: AppTypography.accentMono.copyWith(
                                            fontSize: 11,
                                            fontWeight: FontWeight.bold,
                                          ),
                                        ),
                                      ],
                                    ),
                                  ),
                                  Container(
                                    padding: const EdgeInsets.symmetric(
                                      horizontal: 8,
                                      vertical: 3,
                                    ),
                                    decoration: BoxDecoration(
                                      color: Colors.amber.withAlpha(30),
                                      borderRadius: BorderRadius.circular(6),
                                      border: Border.all(
                                        color: Colors.amber.withAlpha(80),
                                      ),
                                    ),
                                    child: Row(
                                      mainAxisSize: MainAxisSize.min,
                                      children: [
                                        const Icon(
                                          Icons.star_rounded,
                                          size: 14,
                                          color: Colors.amber,
                                        ),
                                        const SizedBox(width: 4),
                                        Text(
                                          '+$earnedStars Stars',
                                          style: AppTypography.accentMono.copyWith(
                                            fontSize: 11,
                                            fontWeight: FontWeight.bold,
                                            color: Colors.amber,
                                          ),
                                        ),
                                      ],
                                    ),
                                  ),
                                ],
                              ),
                            ],
                          ),
                        ),
                      ],
                    ),
                  );
                },
                orElse: () => const SizedBox.shrink(),
              ),
            ),

            // Tasks List
            Expanded(
              child: tasksAsync.when(
                loading: () => const Center(
                  child: CircularProgressIndicator(
                    color: AppColors.primaryAccent,
                  ),
                ),
                error: (err, stack) => Center(
                  child: Padding(
                    padding: const EdgeInsets.all(16.0),
                    child: Text(
                      'Error loading tasks: $err',
                      style: AppTypography.bodyMedium.copyWith(
                        color: AppColors.errorRed,
                      ),
                    ),
                  ),
                ),
                data: (tasks) {
                  if (tasks.isEmpty) {
                    return _buildEmptyState(context);
                  }

                  // Sort: incomplete first, completed at the bottom
                  final sortedTasks = List<TaskWithDailyLog>.from(tasks)
                    ..sort((a, b) {
                      final aDone = (a.log?.isCompleted ?? false) ||
                          ((a.log?.durationLoggedSeconds ?? 0) >=
                              (a.task.targetDurationSeconds ?? 1800));
                      final bDone = (b.log?.isCompleted ?? false) ||
                          ((b.log?.durationLoggedSeconds ?? 0) >=
                              (b.task.targetDurationSeconds ?? 1800));
                      if (aDone == bDone) return 0;
                      return aDone ? 1 : -1;
                    });

                  return RefreshIndicator(
                    onRefresh: () =>
                        ref.read(todayTasksControllerProvider).syncAllLinkedApps(),
                    color: AppColors.primaryAccent,
                    backgroundColor: AppColors.surface,
                    child: ListView.builder(
                      padding: const EdgeInsets.fromLTRB(16, 8, 16, 80),
                      itemCount: sortedTasks.length,
                      itemBuilder: (context, index) {
                        final taskWithLog = sortedTasks[index];
                        return TaskProgressCard(
                          key: ValueKey(taskWithLog.task.id),
                          taskWithLog: taskWithLog,
                        );
                      },
                    ),
                  );
                },
              ),
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
                Icons.task_alt_rounded,
                size: 44,
                color: AppColors.primaryAccent,
              ),
            ),
            const SizedBox(height: 18),
            Text(
              'No Habits or Tasks Today',
              style: AppTypography.h2,
            ),
            const SizedBox(height: 8),
            Text(
              'Build your discipline system. Create your first habit or focus block to start tracking time.',
              textAlign: TextAlign.center,
              style: AppTypography.bodySmall,
            ),
            const SizedBox(height: 20),
            ElevatedButton.icon(
              onPressed: () => CreateTaskModal.show(context),
              icon: const Icon(Icons.add_rounded, size: 18),
              label: Text(
                'Create First Habit',
                style: AppTypography.bodyMedium.copyWith(
                  fontWeight: FontWeight.bold,
                  color: AppColors.background,
                ),
              ),
              style: ElevatedButton.styleFrom(
                backgroundColor: AppColors.primaryAccent,
                foregroundColor: AppColors.background,
                padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
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
