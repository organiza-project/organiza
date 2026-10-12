import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/constants/app_colors.dart';
import '../../../../core/constants/app_typography.dart';
import '../../../../core/services/app_usage_service.dart';
import '../controllers/today_tasks_controller.dart';

class CreateTaskModal extends ConsumerStatefulWidget {
  const CreateTaskModal({super.key});

  static Future<void> show(BuildContext context) {
    return showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (context) => const CreateTaskModal(),
    );
  }

  @override
  ConsumerState<CreateTaskModal> createState() => _CreateTaskModalState();
}

class _CreateTaskModalState extends ConsumerState<CreateTaskModal> {
  final _titleController = TextEditingController();
  final _descriptionController = TextEditingController();
  final _customDurationController = TextEditingController();

  int _durationMinutes = 30;
  bool _isCustomDuration = false;
  String _cadenceUnit = 'day';
  int _targetFrequency = 1;

  TimeOfDay? _startTime;
  TimeOfDay? _endTime;

  List<AppInfoOption> _availableApps = [];
  String? _selectedPackageName;

  bool _isSaving = false;
  String? _errorMessage;

  @override
  void initState() {
    super.initState();
    if (Platform.isAndroid || Platform.isIOS) {
      ref.read(appUsageServiceProvider).getInstalledAppOptions().then((apps) {
        if (mounted) {
          setState(() {
            _availableApps = apps;
          });
        }
      });
    }
  }

  @override
  void dispose() {
    _titleController.dispose();
    _descriptionController.dispose();
    _customDurationController.dispose();
    super.dispose();
  }

  String _formatTimeOfDay(TimeOfDay time) {
    final hour = time.hour.toString().padLeft(2, '0');
    final minute = time.minute.toString().padLeft(2, '0');
    return '$hour:$minute';
  }

  Future<void> _pickTime({required bool isStart}) async {
    final initial = isStart
        ? (_startTime ?? const TimeOfDay(hour: 7, minute: 0))
        : (_endTime ?? const TimeOfDay(hour: 8, minute: 0));

    final picked = await showTimePicker(
      context: context,
      initialTime: initial,
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

  Future<void> _saveTask() async {
    final title = _titleController.text.trim();
    if (title.isEmpty) {
      setState(() {
        _errorMessage = 'Task title cannot be empty.';
      });
      return;
    }

    if (_isCustomDuration) {
      final customMins = int.tryParse(_customDurationController.text.trim());
      if (customMins == null || customMins <= 0) {
        setState(() {
          _errorMessage = 'Please enter a valid duration in minutes.';
        });
        return;
      }
      _durationMinutes = customMins;
    }

    setState(() {
      _isSaving = true;
      _errorMessage = null;
    });

    final isMobile = Platform.isAndroid || Platform.isIOS;
    final linkedPkg = isMobile ? _selectedPackageName : null;

    try {
      await ref.read(todayTasksControllerProvider).createNewTask(
            title: title,
            description: _descriptionController.text.trim().isEmpty
                ? null
                : _descriptionController.text.trim(),
            cadenceUnit: _cadenceUnit,
            targetFrequency: _targetFrequency,
            targetDurationSeconds: _durationMinutes * 60,
            bestTimeStart:
                _startTime != null ? _formatTimeOfDay(_startTime!) : null,
            bestTimeEnd: _endTime != null ? _formatTimeOfDay(_endTime!) : null,
            xpValue: 50,
            linkedPackageName: linkedPkg,
          );

      if (mounted) {
        Navigator.of(context).pop();
      }
    } catch (e) {
      setState(() {
        _errorMessage = 'Failed to create task: $e';
        _isSaving = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final bottomInset = MediaQuery.of(context).viewInsets.bottom;
    final isMobile = Platform.isAndroid || Platform.isIOS;

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
                Text('New Habit / Task', style: AppTypography.h1),
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

            // Title Field
            Text('TITLE', style: AppTypography.bodySmall),
            const SizedBox(height: 6),
            TextField(
              controller: _titleController,
              autofocus: true,
              style: AppTypography.bodyMedium,
              decoration: InputDecoration(
                hintText: 'e.g. Deep Work Session, Morning Run',
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

            // Duration Goal Picker
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text('DAILY DURATION GOAL', style: AppTypography.bodySmall),
                Text(
                  '$_durationMinutes min',
                  style: AppTypography.accentMono,
                ),
              ],
            ),
            const SizedBox(height: 8),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                ...[15, 30, 45, 60, 90].map((mins) {
                  final selected = !_isCustomDuration && _durationMinutes == mins;
                  return ChoiceChip(
                    label: Text('${mins}m'),
                    selected: selected,
                    selectedColor: AppColors.primaryAccent,
                    backgroundColor: AppColors.background,
                    labelStyle: AppTypography.bodySmall.copyWith(
                      fontWeight: FontWeight.w600,
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
                      if (val) {
                        setState(() {
                          _isCustomDuration = false;
                          _durationMinutes = mins;
                        });
                      }
                    },
                  );
                }),
                ChoiceChip(
                  label: const Text('Custom'),
                  selected: _isCustomDuration,
                  selectedColor: AppColors.primaryAccent,
                  backgroundColor: AppColors.background,
                  labelStyle: AppTypography.bodySmall.copyWith(
                    fontWeight: FontWeight.w600,
                    color: _isCustomDuration
                        ? AppColors.background
                        : AppColors.textPrimary,
                  ),
                  side: BorderSide(
                    color: _isCustomDuration
                        ? AppColors.primaryAccent
                        : AppColors.border,
                  ),
                  onSelected: (val) {
                    setState(() {
                      _isCustomDuration = val;
                      if (_isCustomDuration &&
                          _customDurationController.text.isEmpty) {
                        _customDurationController.text =
                            _durationMinutes.toString();
                      }
                    });
                  },
                ),
              ],
            ),
            if (_isCustomDuration) ...[
              const SizedBox(height: 10),
              TextField(
                controller: _customDurationController,
                keyboardType: TextInputType.number,
                style: AppTypography.bodyMedium,
                decoration: InputDecoration(
                  hintText: 'Enter custom minutes (e.g. 25, 75, 120)',
                  hintStyle: AppTypography.bodySmall,
                  suffixText: 'minutes',
                  suffixStyle: AppTypography.accentMono,
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
                onChanged: (val) {
                  final v = int.tryParse(val.trim());
                  if (v != null && v > 0) {
                    setState(() => _durationMinutes = v);
                  }
                },
              ),
            ],
            const SizedBox(height: 16),

            // Linked App Tracking Section (Mobile Only)
            if (isMobile) ...[
              Row(
                children: [
                  const Icon(Icons.phone_android_rounded,
                      size: 16, color: AppColors.primaryAccent),
                  const SizedBox(width: 6),
                  Text('LINK APP TRACKING (OPTIONAL)',
                      style: AppTypography.bodySmall),
                ],
              ),
              const SizedBox(height: 4),
              Text(
                'Automatically track time when you use this app today.',
                style: AppTypography.bodySmall.copyWith(
                  color: AppColors.textSecondary,
                  fontSize: 11,
                ),
              ),
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
                    value: _selectedPackageName,
                    isExpanded: true,
                    dropdownColor: AppColors.surfaceElevated,
                    style: AppTypography.bodyMedium,
                    hint: Text(
                      'None (Manual timer only)',
                      style: AppTypography.bodySmall.copyWith(
                        color: AppColors.textSecondary,
                      ),
                    ),
                    items: [
                      DropdownMenuItem<String?>(
                        value: null,
                        child: Text(
                          'None (Manual timer only)',
                          style: AppTypography.bodyMedium.copyWith(
                            color: AppColors.textSecondary,
                          ),
                        ),
                      ),
                      ..._availableApps.map((app) {
                        return DropdownMenuItem<String?>(
                          value: app.packageName,
                          child: Text(
                            app.name,
                            style: AppTypography.bodyMedium.copyWith(
                              color: AppColors.textPrimary,
                              fontWeight: FontWeight.w500,
                            ),
                          ),
                        );
                      }),
                    ],
                    onChanged: (val) {
                      setState(() {
                        _selectedPackageName = val;
                      });
                    },
                  ),
                ),
              ),
              const SizedBox(height: 16),
            ],

            // Cadence & Frequency
            Text('CADENCE & FREQUENCY', style: AppTypography.bodySmall),
            const SizedBox(height: 8),
            Row(
              children: [
                Expanded(
                  child: SegmentedButton<String>(
                    segments: const [
                      ButtonSegment(value: 'day', label: Text('Daily')),
                      ButtonSegment(value: 'week', label: Text('Weekly')),
                    ],
                    selected: {_cadenceUnit},
                    onSelectionChanged: (val) {
                      setState(() {
                        _cadenceUnit = val.first;
                        if (_cadenceUnit == 'day') {
                          _targetFrequency = 1;
                        } else {
                          _targetFrequency = 5;
                        }
                      });
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
            const SizedBox(height: 10),
            if (_cadenceUnit == 'week') ...[
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text('Frequency per week:', style: AppTypography.bodySmall),
                  Text(
                    '$_targetFrequency times',
                    style: AppTypography.accentMono,
                  ),
                ],
              ),
              const SizedBox(height: 6),
              Wrap(
                spacing: 8,
                children: [1, 2, 3, 4, 5, 6, 7].map((freq) {
                  final selected = _targetFrequency == freq;
                  return ChoiceChip(
                    label: Text('${freq}x'),
                    selected: selected,
                    selectedColor: AppColors.primaryAccent,
                    backgroundColor: AppColors.background,
                    labelStyle: AppTypography.bodySmall.copyWith(
                      fontWeight: FontWeight.w600,
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
                      if (val) setState(() => _targetFrequency = freq);
                    },
                  );
                }).toList(),
              ),
              const SizedBox(height: 16),
            ] else
              const SizedBox(height: 8),

            // Best Time Slot
            Text('OPTIMAL TIME WINDOW (OPTIONAL)',
                style: AppTypography.bodySmall),
            const SizedBox(height: 8),
            Row(
              children: [
                Expanded(
                  child: OutlinedButton.icon(
                    onPressed: () => _pickTime(isStart: true),
                    icon: const Icon(Icons.access_time_rounded, size: 16),
                    label: Text(
                      _startTime == null
                          ? 'Start Time'
                          : _formatTimeOfDay(_startTime!),
                      style: AppTypography.bodySmall.copyWith(
                        color: _startTime == null
                            ? AppColors.textSecondary
                            : AppColors.primaryAccent,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                    style: OutlinedButton.styleFrom(
                      side: const BorderSide(color: AppColors.border),
                      padding: const EdgeInsets.symmetric(vertical: 12),
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(8),
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
                  child: OutlinedButton.icon(
                    onPressed: () => _pickTime(isStart: false),
                    icon:
                        const Icon(Icons.access_time_filled_rounded, size: 16),
                    label: Text(
                      _endTime == null
                          ? 'End Time'
                          : _formatTimeOfDay(_endTime!),
                      style: AppTypography.bodySmall.copyWith(
                        color: _endTime == null
                            ? AppColors.textSecondary
                            : AppColors.primaryAccent,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                    style: OutlinedButton.styleFrom(
                      side: const BorderSide(color: AppColors.border),
                      padding: const EdgeInsets.symmetric(vertical: 12),
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(8),
                      ),
                    ),
                  ),
                ),
                if (_startTime != null || _endTime != null) ...[
                  IconButton(
                    onPressed: () => setState(() {
                      _startTime = null;
                      _endTime = null;
                    }),
                    icon: const Icon(Icons.clear_rounded,
                        color: AppColors.textSecondary, size: 18),
                    tooltip: 'Clear Times',
                  ),
                ],
              ],
            ),
            const SizedBox(height: 24),

            // Submit Button
            ElevatedButton(
              onPressed: _isSaving ? null : _saveTask,
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
                      'Create Habit',
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
