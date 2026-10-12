import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/constants/app_colors.dart';
import '../../../../core/constants/app_typography.dart';
import '../../../../data/local/app_database.dart';
import '../controllers/today_tasks_controller.dart';

class EditHabitModal extends ConsumerStatefulWidget {
  final TaskWithDailyLog item;

  const EditHabitModal({
    super.key,
    required this.item,
  });

  static Future<void> show(BuildContext context, TaskWithDailyLog item) {
    return showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (context) => EditHabitModal(item: item),
    );
  }

  @override
  ConsumerState<EditHabitModal> createState() => _EditHabitModalState();
}

class _EditHabitModalState extends ConsumerState<EditHabitModal> {
  late TextEditingController _titleController;
  late TextEditingController _descController;
  late int _loggedMinutes;
  late int _targetMinutes;
  late String _cadenceUnit;
  late int _targetFrequency;

  TimeOfDay? _startTime;
  TimeOfDay? _endTime;

  bool _isSaving = false;

  @override
  void initState() {
    super.initState();
    final task = widget.item.task;
    final log = widget.item.log;

    _titleController = TextEditingController(text: task.title);
    _descController = TextEditingController(text: task.description ?? '');
    _loggedMinutes = ((log?.durationLoggedSeconds ?? 0) / 60).round();
    _targetMinutes = ((task.targetDurationSeconds ?? 1800) / 60).round();
    _cadenceUnit = task.cadenceUnit;
    _targetFrequency = task.targetFrequency;

    if (task.bestTimeStart != null && task.bestTimeStart!.contains(':')) {
      final p = task.bestTimeStart!.split(':');
      _startTime = TimeOfDay(
        hour: int.tryParse(p[0]) ?? 7,
        minute: int.tryParse(p[1]) ?? 0,
      );
    }
    if (task.bestTimeEnd != null && task.bestTimeEnd!.contains(':')) {
      final p = task.bestTimeEnd!.split(':');
      _endTime = TimeOfDay(
        hour: int.tryParse(p[0]) ?? 8,
        minute: int.tryParse(p[1]) ?? 0,
      );
    }
  }

  @override
  void dispose() {
    _titleController.dispose();
    _descController.dispose();
    super.dispose();
  }

  String _formatTime(TimeOfDay t) {
    return '${t.hour.toString().padLeft(2, '0')}:${t.minute.toString().padLeft(2, '0')}';
  }

  Future<void> _pickTime({required bool isStart}) async {
    final initial = isStart
        ? (_startTime ?? const TimeOfDay(hour: 7, minute: 0))
        : (_endTime ?? const TimeOfDay(hour: 8, minute: 0));

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
      setState(() {
        if (isStart) {
          _startTime = picked;
        } else {
          _endTime = picked;
        }
      });
    }
  }

  Future<void> _confirmDelete() async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AppColors.surface,
        title: Text('Delete Habit?', style: AppTypography.h2),
        content: Text(
          'Are you sure you want to delete "${widget.item.task.title}"? This cannot be undone.',
          style: AppTypography.bodySmall,
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(false),
            child: const Text('Cancel', style: TextStyle(color: AppColors.textSecondary)),
          ),
          ElevatedButton(
            style: ElevatedButton.styleFrom(backgroundColor: AppColors.errorRed),
            onPressed: () => Navigator.of(ctx).pop(true),
            child: const Text('Delete', style: TextStyle(color: Colors.white)),
          ),
        ],
      ),
    );

    if (confirmed == true && mounted) {
      await ref
          .read(todayTasksControllerProvider)
          .deleteHabit(widget.item.task.id);
      if (mounted) Navigator.of(context).pop();
    }
  }

  Future<void> _saveChanges() async {
    final title = _titleController.text.trim();
    if (title.isEmpty) return;

    setState(() => _isSaving = true);
    final ctrl = ref.read(todayTasksControllerProvider);
    final task = widget.item.task;

    // 1. Update task definitions
    await ctrl.updateHabit(
      taskId: task.id,
      title: title,
      cadenceUnit: _cadenceUnit,
      targetFrequency: _targetFrequency,
      targetDurationSeconds: _targetMinutes * 60,
      bestTimeStart: _startTime != null ? _formatTime(_startTime!) : null,
      bestTimeEnd: _endTime != null ? _formatTime(_endTime!) : null,
      description: _descController.text.trim().isEmpty
          ? null
          : _descController.text.trim(),
      linkedPackageName: task.linkedPackageName,
    );

    // 2. Adjust logged progress for today
    await ctrl.setLoggedDuration(
      taskId: task.id,
      durationSeconds: _loggedMinutes * 60,
      targetSeconds: _targetMinutes * 60,
      taskXp: task.xpValue,
    );

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
        bottom: bottomInset + 20,
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
            // Drag Handle
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

            // Header
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text('Edit Habit & Adjust Progress', style: AppTypography.h1),
                IconButton(
                  onPressed: () => Navigator.of(context).pop(),
                  icon: const Icon(Icons.close_rounded,
                      color: AppColors.textSecondary),
                ),
              ],
            ),
            const SizedBox(height: 16),

            // Today's Progress Correction Slider
            Container(
              padding: const EdgeInsets.all(14),
              decoration: BoxDecoration(
                color: AppColors.background,
                borderRadius: BorderRadius.circular(12),
                border: Border.all(color: AppColors.primaryAccent.withAlpha(80)),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Row(
                        children: [
                          const Icon(Icons.tune_rounded,
                              size: 16, color: AppColors.primaryAccent),
                          const SizedBox(width: 6),
                          Text('TODAY\'S LOGGED TIME',
                              style: AppTypography.bodySmall),
                        ],
                      ),
                      Text(
                        '$_loggedMinutes min',
                        style: AppTypography.accentMono.copyWith(fontSize: 16),
                      ),
                    ],
                  ),
                  const SizedBox(height: 8),
                  SliderTheme(
                    data: SliderTheme.of(context).copyWith(
                      activeTrackColor: AppColors.primaryAccent,
                      inactiveTrackColor: AppColors.surfaceElevated,
                      thumbColor: AppColors.primaryAccent,
                    ),
                    child: Slider(
                      value: _loggedMinutes.clamp(0, 180).toDouble(),
                      min: 0,
                      max: 180,
                      divisions: 36,
                      label: '$_loggedMinutes m',
                      onChanged: (val) {
                        setState(() => _loggedMinutes = val.round());
                      },
                    ),
                  ),
                  Text(
                    'Use slider to correct accidental logs or reset today\'s timer.',
                    style: AppTypography.bodySmall.copyWith(
                      fontSize: 11,
                      color: AppColors.textSecondary,
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 16),

            // Habit Title
            Text('TITLE', style: AppTypography.bodySmall),
            const SizedBox(height: 6),
            TextField(
              controller: _titleController,
              style: AppTypography.bodyMedium,
              decoration: InputDecoration(
                filled: true,
                fillColor: AppColors.background,
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(10),
                  borderSide: const BorderSide(color: AppColors.border),
                ),
                enabledBorder: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(10),
                  borderSide: const BorderSide(color: AppColors.border),
                ),
                focusedBorder: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(10),
                  borderSide: const BorderSide(color: AppColors.primaryAccent),
                ),
                contentPadding: const EdgeInsets.symmetric(
                    horizontal: 14, vertical: 12),
              ),
            ),
            const SizedBox(height: 16),

            // Target Goal Duration Stepper
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text('TARGET GOAL DURATION', style: AppTypography.bodySmall),
                Text('$_targetMinutes min', style: AppTypography.accentMono),
              ],
            ),
            const SizedBox(height: 8),
            Row(
              children: [
                IconButton.filledTonal(
                  onPressed: _targetMinutes > 5
                      ? () => setState(() => _targetMinutes -= 5)
                      : null,
                  icon: const Icon(Icons.remove_rounded, size: 18),
                ),
                Expanded(
                  child: Slider(
                    value: _targetMinutes.clamp(5, 180).toDouble(),
                    min: 5,
                    max: 180,
                    divisions: 35,
                    activeColor: AppColors.primaryAccent,
                    onChanged: (val) => setState(() => _targetMinutes = val.round()),
                  ),
                ),
                IconButton.filledTonal(
                  onPressed: () => setState(() => _targetMinutes += 5),
                  icon: const Icon(Icons.add_rounded, size: 18),
                ),
              ],
            ),
            const SizedBox(height: 16),

            // Cadence Selector
            Text('CADENCE', style: AppTypography.bodySmall),
            const SizedBox(height: 8),
            SegmentedButton<String>(
              segments: const [
                ButtonSegment(value: 'day', label: Text('Daily')),
                ButtonSegment(value: 'week', label: Text('Weekly')),
                ButtonSegment(value: 'month', label: Text('Monthly')),
              ],
              selected: {_cadenceUnit},
              onSelectionChanged: (val) =>
                  setState(() => _cadenceUnit = val.first),
              style: SegmentedButton.styleFrom(
                backgroundColor: AppColors.background,
                selectedBackgroundColor: AppColors.surfaceElevated,
                selectedForegroundColor: AppColors.primaryAccent,
                foregroundColor: AppColors.textSecondary,
                side: const BorderSide(color: AppColors.border),
              ),
            ),
            const SizedBox(height: 16),

            // Time Window
            Text('OPTIMAL TIME WINDOW', style: AppTypography.bodySmall),
            const SizedBox(height: 8),
            Row(
              children: [
                Expanded(
                  child: OutlinedButton.icon(
                    onPressed: () => _pickTime(isStart: true),
                    icon: const Icon(Icons.access_time_rounded, size: 16),
                    label: Text(
                      _startTime == null ? 'Start' : _formatTime(_startTime!),
                      style: AppTypography.bodySmall.copyWith(
                        color: _startTime == null
                            ? AppColors.textSecondary
                            : AppColors.primaryAccent,
                      ),
                    ),
                    style: OutlinedButton.styleFrom(
                      side: const BorderSide(color: AppColors.border),
                      padding: const EdgeInsets.symmetric(vertical: 12),
                    ),
                  ),
                ),
                const Padding(
                  padding: EdgeInsets.symmetric(horizontal: 8),
                  child: Text('–',
                      style: TextStyle(color: AppColors.textSecondary)),
                ),
                Expanded(
                  child: OutlinedButton.icon(
                    onPressed: () => _pickTime(isStart: false),
                    icon: const Icon(Icons.access_time_filled_rounded, size: 16),
                    label: Text(
                      _endTime == null ? 'End' : _formatTime(_endTime!),
                      style: AppTypography.bodySmall.copyWith(
                        color: _endTime == null
                            ? AppColors.textSecondary
                            : AppColors.primaryAccent,
                      ),
                    ),
                    style: OutlinedButton.styleFrom(
                      side: const BorderSide(color: AppColors.border),
                      padding: const EdgeInsets.symmetric(vertical: 12),
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 24),

            // Action Buttons
            Row(
              children: [
                IconButton(
                  tooltip: 'Delete Habit',
                  onPressed: _confirmDelete,
                  icon: const Icon(Icons.delete_outline_rounded,
                      color: AppColors.errorRed),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: ElevatedButton(
                    onPressed: _isSaving ? null : _saveChanges,
                    style: ElevatedButton.styleFrom(
                      backgroundColor: AppColors.primaryAccent,
                      foregroundColor: AppColors.background,
                      padding: const EdgeInsets.symmetric(vertical: 14),
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(10),
                      ),
                    ),
                    child: _isSaving
                        ? const SizedBox(
                            width: 20,
                            height: 20,
                            child: CircularProgressIndicator(
                              strokeWidth: 2,
                              color: AppColors.background,
                            ),
                          )
                        : Text(
                            'Save Adjustments',
                            style: AppTypography.bodyMedium.copyWith(
                              fontWeight: FontWeight.bold,
                              color: AppColors.background,
                            ),
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
}
