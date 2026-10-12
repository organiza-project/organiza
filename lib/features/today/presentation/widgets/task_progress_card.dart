import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/constants/app_colors.dart';
import '../../../../core/constants/app_typography.dart';
import '../../../../data/local/app_database.dart';
import '../controllers/today_tasks_controller.dart';

class TaskProgressCard extends ConsumerStatefulWidget {
  final TaskWithDailyLog taskWithLog;

  const TaskProgressCard({
    super.key,
    required this.taskWithLog,
  });

  @override
  ConsumerState<TaskProgressCard> createState() => _TaskProgressCardState();
}

class _TaskProgressCardState extends ConsumerState<TaskProgressCard> {
  Timer? _ticker;
  int _sessionSeconds = 0;
  int _unflushedSeconds = 0;
  bool _isRunning = false;

  @override
  void dispose() {
    _ticker?.cancel();
    if (_unflushedSeconds > 0) {
      _flushTimeSync();
    }
    super.dispose();
  }

  void _flushTimeSync() {
    final unflushed = _unflushedSeconds;
    _unflushedSeconds = 0;
    final task = widget.taskWithLog.task;
    final targetSeconds = task.targetDurationSeconds ?? 1800;

    ref.read(todayTasksControllerProvider).addProgress(
          taskId: task.id,
          addedSeconds: unflushed,
          targetSeconds: targetSeconds,
          taskXp: task.xpValue,
        );
  }

  Future<void> _flushTime() async {
    if (_unflushedSeconds <= 0) return;
    final unflushed = _unflushedSeconds;
    _unflushedSeconds = 0;
    final task = widget.taskWithLog.task;
    final targetSeconds = task.targetDurationSeconds ?? 1800;

    await ref.read(todayTasksControllerProvider).addProgress(
          taskId: task.id,
          addedSeconds: unflushed,
          targetSeconds: targetSeconds,
          taskXp: task.xpValue,
        );
  }

  void _toggleTimer() {
    if (_isRunning) {
      // Pause
      _ticker?.cancel();
      _ticker = null;
      _flushTime();
      setState(() {
        _isRunning = false;
        _sessionSeconds = 0;
      });
    } else {
      // Start
      setState(() {
        _isRunning = true;
      });
      _ticker = Timer.periodic(const Duration(seconds: 1), (timer) {
        setState(() {
          _sessionSeconds++;
          _unflushedSeconds++;
        });

        if (_unflushedSeconds >= 10) {
          _flushTime();
        }
      });
    }
  }

  Future<void> _quickLog15Minutes() async {
    final task = widget.taskWithLog.task;
    final targetSeconds = task.targetDurationSeconds ?? 1800;
    await ref.read(todayTasksControllerProvider).addProgress(
          taskId: task.id,
          addedSeconds: 15 * 60,
          targetSeconds: targetSeconds,
          taskXp: task.xpValue,
        );
  }

  Future<void> _markComplete() async {
    final task = widget.taskWithLog.task;
    final baseLogged = widget.taskWithLog.log?.durationLoggedSeconds ?? 0;
    final targetSeconds = task.targetDurationSeconds ?? 1800;
    final neededSeconds =
        (targetSeconds > baseLogged) ? (targetSeconds - baseLogged) : 0;

    await ref.read(todayTasksControllerProvider).addProgress(
          taskId: task.id,
          addedSeconds: neededSeconds,
          targetSeconds: targetSeconds,
          taskXp: task.xpValue,
        );
  }

  String _formatTimerText(int totalSeconds) {
    final minutes = totalSeconds ~/ 60;
    final seconds = totalSeconds % 60;
    return '${minutes.toString().padLeft(2, '0')}:${seconds.toString().padLeft(2, '0')}';
  }

  @override
  Widget build(BuildContext context) {
    final task = widget.taskWithLog.task;
    final log = widget.taskWithLog.log;

    final baseLogged = log?.durationLoggedSeconds ?? 0;
    final effectiveLogged = baseLogged + _sessionSeconds;
    final targetSeconds = task.targetDurationSeconds ?? 1800;

    final progress = targetSeconds > 0
        ? (effectiveLogged / targetSeconds).clamp(0.0, 1.0)
        : (effectiveLogged > 0 ? 1.0 : 0.0);

    final isCompleted = (log?.isCompleted ?? false) || (effectiveLogged >= targetSeconds);

    final currentXp = isCompleted
        ? task.xpValue
        : (progress * task.xpValue).round();

    return Container(
      margin: const EdgeInsets.only(bottom: 14),
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(
          color: _isRunning
              ? AppColors.primaryAccent
              : (isCompleted
                  ? AppColors.successGreen.withAlpha(80)
                  : AppColors.border),
          width: _isRunning ? 1.5 : 1,
        ),
        boxShadow: _isRunning
            ? [
                BoxShadow(
                  color: AppColors.primaryAccent.withAlpha(30),
                  blurRadius: 10,
                  spreadRadius: 1,
                ),
              ]
            : null,
      ),
      child: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            // Top Row: Title, Badges, Checkmark
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        task.title,
                        style: AppTypography.h2.copyWith(
                          decoration: isCompleted
                              ? TextDecoration.lineThrough
                              : null,
                          color: isCompleted
                              ? AppColors.textSecondary
                              : AppColors.textPrimary,
                        ),
                      ),
                      const SizedBox(height: 6),
                      Wrap(
                        spacing: 6,
                        runSpacing: 4,
                        children: [
                          // Cadence badge
                          _buildPill(
                            icon: Icons.repeat_rounded,
                            label: '${task.targetFrequency}x / ${task.cadenceUnit}',
                            color: AppColors.textSecondary,
                          ),
                          // Best time badge
                          if (task.bestTimeStart != null &&
                              task.bestTimeStart!.isNotEmpty)
                            _buildPill(
                              icon: Icons.schedule_rounded,
                              label: task.bestTimeEnd != null &&
                                      task.bestTimeEnd!.isNotEmpty
                                  ? '${task.bestTimeStart} - ${task.bestTimeEnd}'
                                  : task.bestTimeStart!,
                              color: AppColors.primaryAccent,
                            ),
                        ],
                      ),
                    ],
                  ),
                ),
                const SizedBox(width: 10),
                // Dynamic XP Badge
                Container(
                  padding:
                      const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
                  decoration: BoxDecoration(
                    color: AppColors.surfaceElevated,
                    borderRadius: BorderRadius.circular(8),
                    border: Border.all(
                      color: isCompleted
                          ? AppColors.successGreen.withAlpha(100)
                          : AppColors.primaryAccent.withAlpha(80),
                    ),
                  ),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(
                        Icons.bolt_rounded,
                        size: 15,
                        color: isCompleted
                            ? AppColors.successGreen
                            : AppColors.primaryAccent,
                      ),
                      const SizedBox(width: 3),
                      Text(
                        '+$currentXp XP',
                        style: AppTypography.accentMono.copyWith(
                          fontSize: 12,
                          color: isCompleted
                              ? AppColors.successGreen
                              : AppColors.primaryAccent,
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(width: 8),
                // Completion Checkmark Button
                GestureDetector(
                  onTap: _markComplete,
                  child: AnimatedContainer(
                    duration: const Duration(milliseconds: 200),
                    padding: const EdgeInsets.all(5),
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      color: isCompleted
                          ? AppColors.successGreen
                          : AppColors.surfaceElevated,
                      border: Border.all(
                        color: isCompleted
                            ? AppColors.successGreen
                            : AppColors.border,
                        width: 1.5,
                      ),
                    ),
                    child: Icon(
                      Icons.check_rounded,
                      size: 18,
                      color: isCompleted
                          ? AppColors.background
                          : AppColors.textSecondary,
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 14),

            // Progress Bar
            ClipRRect(
              borderRadius: BorderRadius.circular(6),
              child: LinearProgressIndicator(
                value: progress,
                minHeight: 8,
                backgroundColor: AppColors.surfaceElevated,
                valueColor: AlwaysStoppedAnimation<Color>(
                  isCompleted
                      ? AppColors.successGreen
                      : AppColors.primaryAccent,
                ),
              ),
            ),
            const SizedBox(height: 8),

            // Time & Percent Info
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(
                  '${_formatTimerText(effectiveLogged)} / ${_formatTimerText(targetSeconds)} min',
                  style: AppTypography.accentMono.copyWith(
                    fontSize: 13,
                    color: _isRunning
                        ? AppColors.primaryAccent
                        : AppColors.textSecondary,
                  ),
                ),
                Text(
                  '${(progress * 100).toInt()}%',
                  style: AppTypography.accentMono.copyWith(
                    fontSize: 13,
                    color: isCompleted
                        ? AppColors.successGreen
                        : AppColors.textPrimary,
                  ),
                ),
              ],
            ),
            const SizedBox(height: 14),

            // Action Buttons: Start/Pause & +15m
            Row(
              children: [
                // Live Stopwatch Start / Pause
                Expanded(
                  child: ElevatedButton.icon(
                    onPressed: _toggleTimer,
                    icon: Icon(
                      _isRunning
                          ? Icons.pause_rounded
                          : Icons.play_arrow_rounded,
                      size: 18,
                    ),
                    label: Text(
                      _isRunning ? 'Pause Timer' : 'Start Focus',
                      style: AppTypography.bodyMedium.copyWith(
                        fontWeight: FontWeight.w600,
                        color: _isRunning
                            ? AppColors.primaryAccent
                            : AppColors.background,
                      ),
                    ),
                    style: ElevatedButton.styleFrom(
                      backgroundColor: _isRunning
                          ? AppColors.surfaceElevated
                          : AppColors.primaryAccent,
                      foregroundColor: _isRunning
                          ? AppColors.primaryAccent
                          : AppColors.background,
                      side: _isRunning
                          ? const BorderSide(color: AppColors.primaryAccent)
                          : BorderSide.none,
                      padding: const EdgeInsets.symmetric(vertical: 11),
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(8),
                      ),
                    ),
                  ),
                ),
                const SizedBox(width: 10),
                // +15m Quick Log Button
                OutlinedButton.icon(
                  onPressed: _quickLog15Minutes,
                  icon: const Icon(Icons.add_rounded, size: 16),
                  label: Text(
                    '+15m',
                    style: AppTypography.bodySmall.copyWith(
                      fontWeight: FontWeight.bold,
                      color: AppColors.textPrimary,
                    ),
                  ),
                  style: OutlinedButton.styleFrom(
                    side: const BorderSide(color: AppColors.border),
                    padding: const EdgeInsets.symmetric(
                      horizontal: 14,
                      vertical: 11,
                    ),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(8),
                    ),
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildPill({
    required IconData icon,
    required String label,
    required Color color,
  }) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 3),
      decoration: BoxDecoration(
        color: AppColors.surfaceElevated,
        borderRadius: BorderRadius.circular(6),
        border: Border.all(color: AppColors.border, width: 0.8),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(icon, size: 12, color: color),
          const SizedBox(width: 4),
          Text(
            label,
            style: AppTypography.bodySmall.copyWith(
              fontSize: 11,
              color: color,
            ),
          ),
        ],
      ),
    );
  }
}
