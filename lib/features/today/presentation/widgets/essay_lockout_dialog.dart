import 'package:flutter/material.dart';

import '../../../../core/constants/app_colors.dart';
import '../../../../core/constants/app_typography.dart';

class EssayLockoutDialog extends StatefulWidget {
  final String? customPrompt;
  final int targetWordCount;

  const EssayLockoutDialog({
    super.key,
    this.customPrompt,
    this.targetWordCount = 150,
  });

  static Future<bool?> show(
    BuildContext context, {
    String? customPrompt,
    int targetWordCount = 150,
  }) {
    return showDialog<bool>(
      context: context,
      barrierDismissible: false,
      builder: (context) => EssayLockoutDialog(
        customPrompt: customPrompt,
        targetWordCount: targetWordCount,
      ),
    );
  }

  @override
  State<EssayLockoutDialog> createState() => _EssayLockoutDialogState();
}

class _EssayLockoutDialogState extends State<EssayLockoutDialog> {
  final TextEditingController _controller = TextEditingController();
  final FocusNode _focusNode = FocusNode();

  String _previousText = '';
  String? _pasteWarning;
  int _wordCount = 0;

  @override
  void dispose() {
    _controller.dispose();
    _focusNode.dispose();
    super.dispose();
  }

  int _calculateWords(String text) {
    final trimmed = text.trim();
    if (trimmed.isEmpty) return 0;
    return trimmed.split(RegExp(r'\s+')).where((w) => w.isNotEmpty).length;
  }

  void _onTextChanged(String text) {
    // Detect clipboard injection / paste (typing adds 1-2 chars at a time)
    final delta = text.length - _previousText.length;
    if (delta > 5) {
      // Revert text to previous
      _controller.text = _previousText;
      _controller.selection = TextSelection.collapsed(
        offset: _previousText.length,
      );
      setState(() {
        _pasteWarning = 'Paste detected! Manual typing required.';
      });
      return;
    }

    _previousText = text;
    final words = _calculateWords(text);
    setState(() {
      _wordCount = words;
      _pasteWarning = null;
    });
  }

  @override
  Widget build(BuildContext context) {
    final remainingWords = widget.targetWordCount - _wordCount;
    final isUnlocked = _wordCount >= widget.targetWordCount;

    return Dialog(
      backgroundColor: AppColors.surface,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16),
        side: const BorderSide(color: AppColors.border, width: 1),
      ),
      insetPadding: const EdgeInsets.symmetric(horizontal: 20, vertical: 24),
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 500, maxHeight: 600),
        child: Padding(
          padding: const EdgeInsets.all(20.0),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Row(
                children: [
                  Container(
                    padding: const EdgeInsets.all(8),
                    decoration: BoxDecoration(
                      color: AppColors.errorRed.withAlpha(40),
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: const Icon(
                      Icons.lock_clock_rounded,
                      color: AppColors.errorRed,
                      size: 24,
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          'Intentionality Lockout',
                          style: AppTypography.h2,
                        ),
                        const SizedBox(height: 2),
                        Text(
                          'Anti-impulse friction protocol active',
                          style: AppTypography.bodySmall,
                        ),
                      ],
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 16),
              Text(
                widget.customPrompt ??
                    'State your intention and why you must break this focus session / alter this limit.',
                style: AppTypography.bodyMedium.copyWith(
                  fontWeight: FontWeight.w600,
                  color: AppColors.textPrimary,
                ),
              ),
              const SizedBox(height: 8),
              Text(
                'Copy-pasting and context menus are disabled. You must type at least ${widget.targetWordCount} words reflecting on this action.',
                style: AppTypography.bodySmall,
              ),
              const SizedBox(height: 12),
              if (_pasteWarning != null) ...[
                Container(
                  padding: const EdgeInsets.all(8),
                  decoration: BoxDecoration(
                    color: AppColors.errorRed.withAlpha(35),
                    borderRadius: BorderRadius.circular(6),
                    border: Border.all(color: AppColors.errorRed),
                  ),
                  child: Row(
                    children: [
                      const Icon(Icons.warning_amber_rounded,
                          color: AppColors.errorRed, size: 16),
                      const SizedBox(width: 6),
                      Expanded(
                        child: Text(
                          _pasteWarning!,
                          style: AppTypography.bodySmall.copyWith(
                            color: AppColors.errorRed,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 8),
              ],
              Expanded(
                child: Container(
                  decoration: BoxDecoration(
                    color: AppColors.background,
                    borderRadius: BorderRadius.circular(8),
                    border: Border.all(color: AppColors.border),
                  ),
                  padding: const EdgeInsets.all(12),
                  child: TextField(
                    controller: _controller,
                    focusNode: _focusNode,
                    maxLines: null,
                    expands: true,
                    enableInteractiveSelection: false,
                    contextMenuBuilder: (context, editableTextState) =>
                        const SizedBox.shrink(),
                    onChanged: _onTextChanged,
                    style: AppTypography.bodyMedium,
                    decoration: InputDecoration(
                      hintText: 'Start typing your justification here...',
                      hintStyle: AppTypography.bodySmall,
                      border: InputBorder.none,
                      isDense: true,
                    ),
                  ),
                ),
              ),
              const SizedBox(height: 12),
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Text(
                    'Words: $_wordCount / ${widget.targetWordCount}',
                    style: AppTypography.accentMono.copyWith(
                      color: isUnlocked
                          ? AppColors.successGreen
                          : AppColors.primaryAccent,
                    ),
                  ),
                  Text(
                    isUnlocked
                        ? 'Requirement met'
                        : '$remainingWords more words needed',
                    style: AppTypography.bodySmall.copyWith(
                      color: isUnlocked
                          ? AppColors.successGreen
                          : AppColors.textSecondary,
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 16),
              Row(
                children: [
                  Expanded(
                    child: OutlinedButton(
                      onPressed: isUnlocked
                          ? () => Navigator.of(context).pop(true)
                          : null,
                      style: OutlinedButton.styleFrom(
                        side: BorderSide(
                          color: isUnlocked
                              ? AppColors.errorRed
                              : AppColors.border,
                        ),
                        padding: const EdgeInsets.symmetric(vertical: 12),
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(8),
                        ),
                      ),
                      child: Text(
                        'Confirm Override',
                        style: AppTypography.bodyMedium.copyWith(
                          color: isUnlocked
                              ? AppColors.errorRed
                              : AppColors.textSecondary.withAlpha(100),
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: ElevatedButton(
                      onPressed: () => Navigator.of(context).pop(false),
                      style: ElevatedButton.styleFrom(
                        backgroundColor: AppColors.primaryAccent,
                        foregroundColor: AppColors.background,
                        padding: const EdgeInsets.symmetric(vertical: 12),
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(8),
                        ),
                      ),
                      child: Text(
                        'Stay Focused',
                        style: AppTypography.bodyMedium.copyWith(
                          color: AppColors.background,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}
