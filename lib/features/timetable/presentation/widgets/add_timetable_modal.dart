import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/constants/app_colors.dart';
import '../../../../core/constants/app_typography.dart';
import '../controllers/timetable_controller.dart';

class AddTimetableModal extends ConsumerStatefulWidget {
  const AddTimetableModal({super.key});

  static Future<void> show(BuildContext context) {
    return showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (context) => const AddTimetableModal(),
    );
  }

  @override
  ConsumerState<AddTimetableModal> createState() => _AddTimetableModalState();
}

class _AddTimetableModalState extends ConsumerState<AddTimetableModal> {
  final _titleController = TextEditingController();
  final _notesController = TextEditingController();

  bool _isRecurring = false;
  int _selectedDayOfWeek = 1; // 1 = Monday .. 7 = Sunday
  DateTime _selectedDate = DateTime.now();

  TimeOfDay _startTime = const TimeOfDay(hour: 9, minute: 0);
  TimeOfDay _endTime = const TimeOfDay(hour: 10, minute: 30);

  bool _isSaving = false;
  String? _errorMessage;

  @override
  void dispose() {
    _titleController.dispose();
    _notesController.dispose();
    super.dispose();
  }

  String _formatTime(TimeOfDay t) {
    final h = t.hour.toString().padLeft(2, '0');
    final m = t.minute.toString().padLeft(2, '0');
    return '$h:$m';
  }

  Future<void> _pickTime({required bool isStart}) async {
    final picked = await showTimePicker(
      context: context,
      initialTime: isStart ? _startTime : _endTime,
      builder: (context, child) {
        return Theme(
          data: Theme.of(context).copyWith(
            colorScheme: const ColorScheme.dark(
              primary: AppColors.primaryAccent,
              surface: AppColors.surface,
              onSurface: AppColors.textPrimary,
            ),
          ),
          child: child!,
        );
      },
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

  Future<void> _pickSpecificDate() async {
    final picked = await showDatePicker(
      context: context,
      initialDate: _selectedDate,
      firstDate: DateTime(2020),
      lastDate: DateTime(2035),
      builder: (context, child) {
        return Theme(
          data: Theme.of(context).copyWith(
            colorScheme: const ColorScheme.dark(
              primary: AppColors.primaryAccent,
              surface: AppColors.surface,
              onSurface: AppColors.textPrimary,
            ),
          ),
          child: child!,
        );
      },
    );

    if (picked != null) {
      setState(() {
        _selectedDate = picked;
      });
    }
  }

  Future<void> _saveEntry() async {
    final title = _titleController.text.trim();
    if (title.isEmpty) {
      setState(() => _errorMessage = 'Title cannot be empty.');
      return;
    }

    setState(() {
      _isSaving = true;
      _errorMessage = null;
    });

    try {
      final startTimeStr = _formatTime(_startTime);
      final endTimeStr = _formatTime(_endTime);
      final notes = _notesController.text.trim().isEmpty
          ? null
          : _notesController.text.trim();

      await ref.read(timetableControllerProvider).addEntry(
            title: title,
            startTime: startTimeStr,
            endTime: endTimeStr,
            dayOfWeek: _isRecurring ? _selectedDayOfWeek : 0,
            specificDate: _isRecurring ? null : formatDate(_selectedDate),
            locationOrNotes: notes,
          );

      if (mounted) {
        Navigator.of(context).pop();
      }
    } catch (e) {
      setState(() {
        _isSaving = false;
        _errorMessage = 'Failed to save event: $e';
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final bottomInset = MediaQuery.of(context).viewInsets.bottom;
    const weekdays = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'];

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
        border: Border(
          top: BorderSide(color: AppColors.border, width: 1),
        ),
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
                Text('Add Schedule Event', style: AppTypography.h1),
                IconButton(
                  onPressed: () => Navigator.of(context).pop(),
                  icon: const Icon(Icons.close_rounded,
                      color: AppColors.textSecondary),
                ),
              ],
            ),
            const SizedBox(height: 16),

            if (_errorMessage != null) ...[
              Container(
                padding: const EdgeInsets.all(10),
                margin: const EdgeInsets.only(bottom: 12),
                decoration: BoxDecoration(
                  color: AppColors.errorRed.withAlpha(30),
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(color: AppColors.errorRed.withAlpha(80)),
                ),
                child: Text(
                  _errorMessage!,
                  style: AppTypography.bodySmall.copyWith(
                    color: AppColors.errorRed,
                    fontWeight: FontWeight.w600,
                  ),
                ),
              ),
            ],

            // Title
            Text('EVENT TITLE', style: AppTypography.bodySmall),
            const SizedBox(height: 6),
            TextField(
              controller: _titleController,
              autofocus: true,
              style: AppTypography.bodyMedium,
              decoration: InputDecoration(
                hintText: 'e.g. Maths Lecture, Team Standup',
                hintStyle: AppTypography.bodySmall,
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
                  horizontal: 14,
                  vertical: 12,
                ),
              ),
            ),
            const SizedBox(height: 16),

            // Frequency Type: Specific Date vs Weekly Recurring
            Row(
              children: [
                Expanded(
                  child: SegmentedButton<bool>(
                    segments: const [
                      ButtonSegment(value: false, label: Text('Specific Date')),
                      ButtonSegment(value: true, label: Text('Recurring Weekly')),
                    ],
                    selected: {_isRecurring},
                    onSelectionChanged: (val) {
                      setState(() => _isRecurring = val.first);
                    },
                    style: SegmentedButton.styleFrom(
                      backgroundColor: AppColors.background,
                      selectedBackgroundColor: AppColors.surfaceElevated,
                      foregroundColor: AppColors.textSecondary,
                      selectedForegroundColor: AppColors.primaryAccent,
                      side: const BorderSide(color: AppColors.border),
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 14),

            // Day Selector
            if (_isRecurring) ...[
              Text('RECURRING DAY OF WEEK', style: AppTypography.bodySmall),
              const SizedBox(height: 8),
              Wrap(
                spacing: 8,
                children: List.generate(7, (i) {
                  final dayIndex = i + 1; // 1 = Mon
                  final selected = _selectedDayOfWeek == dayIndex;
                  return ChoiceChip(
                    label: Text(weekdays[i]),
                    selected: selected,
                    selectedColor: AppColors.primaryAccent,
                    backgroundColor: AppColors.background,
                    labelStyle: AppTypography.bodySmall.copyWith(
                      fontWeight: FontWeight.bold,
                      color: selected
                          ? AppColors.background
                          : AppColors.textPrimary,
                    ),
                    side: BorderSide(
                      color: selected
                          ? AppColors.primaryAccent
                          : AppColors.border,
                    ),
                    onSelected: (val) {
                      if (val) setState(() => _selectedDayOfWeek = dayIndex);
                    },
                  );
                }),
              ),
            ] else ...[
              Text('DATE', style: AppTypography.bodySmall),
              const SizedBox(height: 8),
              OutlinedButton.icon(
                onPressed: _pickSpecificDate,
                icon: const Icon(Icons.calendar_today_rounded, size: 16),
                label: Text(
                  '${_selectedDate.year}-${_selectedDate.month.toString().padLeft(2, '0')}-${_selectedDate.day.toString().padLeft(2, '0')}',
                  style: AppTypography.bodyMedium.copyWith(
                    fontWeight: FontWeight.w600,
                    color: AppColors.primaryAccent,
                  ),
                ),
                style: OutlinedButton.styleFrom(
                  side: const BorderSide(color: AppColors.border),
                  padding: const EdgeInsets.symmetric(vertical: 12),
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(10),
                  ),
                ),
              ),
            ],
            const SizedBox(height: 16),

            // Start & End Time Pickers
            Text('TIME WINDOW', style: AppTypography.bodySmall),
            const SizedBox(height: 8),
            Row(
              children: [
                Expanded(
                  child: OutlinedButton.icon(
                    onPressed: () => _pickTime(isStart: true),
                    icon: const Icon(Icons.access_time_rounded, size: 16),
                    label: Text(
                      'Start: ${_formatTime(_startTime)}',
                      style: AppTypography.bodySmall.copyWith(
                        color: AppColors.primaryAccent,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                    style: OutlinedButton.styleFrom(
                      side: const BorderSide(color: AppColors.border),
                      padding: const EdgeInsets.symmetric(vertical: 12),
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(10),
                      ),
                    ),
                  ),
                ),
                const Padding(
                  padding: EdgeInsets.symmetric(horizontal: 8),
                  child: Text('–', style: TextStyle(color: AppColors.textSecondary)),
                ),
                Expanded(
                  child: OutlinedButton.icon(
                    onPressed: () => _pickTime(isStart: false),
                    icon: const Icon(Icons.access_time_filled_rounded, size: 16),
                    label: Text(
                      'End: ${_formatTime(_endTime)}',
                      style: AppTypography.bodySmall.copyWith(
                        color: AppColors.primaryAccent,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                    style: OutlinedButton.styleFrom(
                      side: const BorderSide(color: AppColors.border),
                      padding: const EdgeInsets.symmetric(vertical: 12),
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(10),
                      ),
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 16),

            // Location / Notes
            Text('LOCATION / NOTES (OPTIONAL)', style: AppTypography.bodySmall),
            const SizedBox(height: 6),
            TextField(
              controller: _notesController,
              style: AppTypography.bodyMedium,
              decoration: InputDecoration(
                hintText: 'e.g. Hall B, Zoom Link, Bring calculator',
                hintStyle: AppTypography.bodySmall,
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
                  horizontal: 14,
                  vertical: 12,
                ),
              ),
            ),
            const SizedBox(height: 24),

            // Save Button
            ElevatedButton(
              onPressed: _isSaving ? null : _saveEntry,
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
                      'Schedule Event',
                      style: AppTypography.bodyMedium.copyWith(
                        fontWeight: FontWeight.bold,
                        color: AppColors.background,
                      ),
                    ),
            ),
          ],
        ),
      ),
    );
  }
}
