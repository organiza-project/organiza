import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/constants/app_colors.dart';
import '../../../../core/constants/app_typography.dart';
import '../../../../data/local/app_database.dart';
import '../controllers/journal_controller.dart';

class CreateJournalModal extends ConsumerStatefulWidget {
  final JournalEntry? existingEntry;

  const CreateJournalModal({super.key, this.existingEntry});

  static Future<void> show(BuildContext context, {JournalEntry? existingEntry}) {
    return showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (context) => CreateJournalModal(existingEntry: existingEntry),
    );
  }

  @override
  ConsumerState<CreateJournalModal> createState() => _CreateJournalModalState();
}

class _CreateJournalModalState extends ConsumerState<CreateJournalModal> {
  final _contentController = TextEditingController();
  final _tagInputController = TextEditingController();

  DateTime _entryDateTime = DateTime.now();
  final List<String> _tags = [];
  bool _isSaving = false;
  String? _errorMessage;

  static const List<String> _suggestedTags = [
    'reflection',
    'focus',
    'gratitude',
    'blockers',
    'learning',
  ];

  @override
  void initState() {
    super.initState();
    if (widget.existingEntry != null) {
      final entry = widget.existingEntry!;
      _contentController.text = entry.content;
      _entryDateTime = entry.createdAt;
      if (entry.tags != null && entry.tags!.isNotEmpty) {
        _tags.addAll(entry.tags!.split(',').map((t) => t.trim()));
      }
    }
  }

  @override
  void dispose() {
    _contentController.dispose();
    _tagInputController.dispose();
    super.dispose();
  }

  int _calculateWords(String text) {
    final trimmed = text.trim();
    if (trimmed.isEmpty) return 0;
    return trimmed.split(RegExp(r'\s+')).where((w) => w.isNotEmpty).length;
  }

  void _addTag(String rawTag) {
    final clean = rawTag.replaceAll('#', '').trim().toLowerCase();
    if (clean.isNotEmpty && !_tags.contains(clean)) {
      setState(() {
        _tags.add(clean);
        _tagInputController.clear();
      });
    }
  }

  Future<void> _pickDateTime() async {
    final pickedDate = await showDatePicker(
      context: context,
      initialDate: _entryDateTime,
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

    if (pickedDate != null && mounted) {
      final pickedTime = await showTimePicker(
        context: context,
        initialTime: TimeOfDay.fromDateTime(_entryDateTime),
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

      if (pickedTime != null) {
        setState(() {
          _entryDateTime = DateTime(
            pickedDate.year,
            pickedDate.month,
            pickedDate.day,
            pickedTime.hour,
            pickedTime.minute,
          );
        });
      }
    }
  }

  Future<void> _saveEntry() async {
    final content = _contentController.text.trim();
    if (content.isEmpty) {
      setState(() => _errorMessage = 'Journal entry cannot be empty.');
      return;
    }

    setState(() {
      _isSaving = true;
      _errorMessage = null;
    });

    try {
      if (widget.existingEntry != null) {
        await ref.read(journalControllerProvider).updateEntry(
              id: widget.existingEntry!.id,
              content: content,
              tags: _tags,
            );
      } else {
        await ref.read(journalControllerProvider).addEntry(
              content: content,
              dateTime: _entryDateTime,
              tags: _tags,
            );
      }

      if (mounted) {
        Navigator.of(context).pop();
      }
    } catch (e) {
      setState(() {
        _isSaving = false;
        _errorMessage = 'Failed to save entry: $e';
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final bottomInset = MediaQuery.of(context).viewInsets.bottom;
    final wordCount = _calculateWords(_contentController.text);

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
                Text(
                  widget.existingEntry != null
                      ? 'Edit Reflection'
                      : 'Daily Journal Entry',
                  style: AppTypography.h1,
                ),
                IconButton(
                  onPressed: () => Navigator.of(context).pop(),
                  icon: const Icon(Icons.close_rounded,
                      color: AppColors.textSecondary),
                ),
              ],
            ),
            const SizedBox(height: 12),

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

            // Date & Time Selector
            Row(
              children: [
                OutlinedButton.icon(
                  onPressed: _pickDateTime,
                  icon: const Icon(Icons.calendar_today_rounded, size: 14),
                  label: Text(
                    '${_entryDateTime.year}-${_entryDateTime.month.toString().padLeft(2, '0')}-${_entryDateTime.day.toString().padLeft(2, '0')} • ${_entryDateTime.hour.toString().padLeft(2, '0')}:${_entryDateTime.minute.toString().padLeft(2, '0')}',
                    style: AppTypography.bodySmall.copyWith(
                      color: AppColors.primaryAccent,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                  style: OutlinedButton.styleFrom(
                    side: const BorderSide(color: AppColors.border),
                    padding:
                        const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(8),
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 14),

            // Content Multi-line text field
            Container(
              height: 180,
              decoration: BoxDecoration(
                color: AppColors.background,
                borderRadius: BorderRadius.circular(10),
                border: Border.all(color: AppColors.border),
              ),
              padding: const EdgeInsets.all(12),
              child: TextField(
                controller: _contentController,
                autofocus: true,
                maxLines: null,
                expands: true,
                style: AppTypography.bodyMedium,
                onChanged: (_) => setState(() {}),
                decoration: InputDecoration(
                  hintText:
                      'Capture what worked, what distracted you, and key insights today...',
                  hintStyle: AppTypography.bodySmall,
                  border: InputBorder.none,
                  isDense: true,
                ),
              ),
            ),
            const SizedBox(height: 8),

            // Word count display
            Row(
              mainAxisAlignment: MainAxisAlignment.end,
              children: [
                Text(
                  '$wordCount words',
                  style: AppTypography.accentMono.copyWith(fontSize: 12),
                ),
              ],
            ),
            const SizedBox(height: 12),

            // Tags Section
            Text('TAGS & TOPICS', style: AppTypography.bodySmall),
            const SizedBox(height: 8),
            Row(
              children: [
                Expanded(
                  child: TextField(
                    controller: _tagInputController,
                    style: AppTypography.bodySmall,
                    onSubmitted: (val) => _addTag(val),
                    decoration: InputDecoration(
                      hintText: 'Type tag and press enter (e.g. focus)',
                      hintStyle: AppTypography.bodySmall,
                      filled: true,
                      fillColor: AppColors.background,
                      border: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(8),
                        borderSide: const BorderSide(color: AppColors.border),
                      ),
                      enabledBorder: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(8),
                        borderSide: const BorderSide(color: AppColors.border),
                      ),
                      focusedBorder: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(8),
                        borderSide:
                            const BorderSide(color: AppColors.primaryAccent),
                      ),
                      contentPadding: const EdgeInsets.symmetric(
                        horizontal: 12,
                        vertical: 10,
                      ),
                    ),
                  ),
                ),
                const SizedBox(width: 8),
                IconButton(
                  onPressed: () => _addTag(_tagInputController.text),
                  icon: const Icon(Icons.add_rounded,
                      color: AppColors.primaryAccent),
                  tooltip: 'Add tag',
                ),
              ],
            ),
            const SizedBox(height: 10),

            // Tag suggestions & selected chips
            Wrap(
              spacing: 6,
              runSpacing: 6,
              children: [
                ..._tags.map(
                  (t) => Chip(
                    label: Text('#$t'),
                    deleteIcon:
                        const Icon(Icons.close_rounded, size: 14),
                    onDeleted: () {
                      setState(() => _tags.remove(t));
                    },
                    backgroundColor: AppColors.surfaceElevated,
                    labelStyle: AppTypography.bodySmall.copyWith(
                      color: AppColors.primaryAccent,
                      fontWeight: FontWeight.bold,
                    ),
                    side: BorderSide(
                        color: AppColors.primaryAccent.withAlpha(80)),
                  ),
                ),
                ..._suggestedTags.where((s) => !_tags.contains(s)).map(
                      (s) => ActionChip(
                        label: Text('+$s'),
                        onPressed: () => _addTag(s),
                        backgroundColor: AppColors.background,
                        labelStyle: AppTypography.bodySmall.copyWith(
                          color: AppColors.textSecondary,
                        ),
                        side: const BorderSide(color: AppColors.border),
                      ),
                    ),
              ],
            ),
            const SizedBox(height: 24),

            // Submit Button
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
                      widget.existingEntry != null
                          ? 'Update Entry'
                          : 'Save Reflection',
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
