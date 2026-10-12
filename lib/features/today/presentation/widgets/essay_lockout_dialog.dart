import 'dart:math';

import 'package:flutter/material.dart';

import '../../../../core/constants/app_colors.dart';
import '../../../../core/constants/app_typography.dart';
import '../../domain/stoic_passages.dart';

class EssayLockoutDialog extends StatefulWidget {
  final StoicPassage? initialPassage;

  const EssayLockoutDialog({
    super.key,
    this.initialPassage,
  });

  static Future<bool?> show(
    BuildContext context, {
    StoicPassage? passage,
  }) {
    return showDialog<bool>(
      context: context,
      barrierDismissible: false,
      builder: (context) => EssayLockoutDialog(
        initialPassage: passage,
      ),
    );
  }

  @override
  State<EssayLockoutDialog> createState() => _EssayLockoutDialogState();
}

class _EssayLockoutDialogState extends State<EssayLockoutDialog> {
  final TextEditingController _controller = TextEditingController();
  final FocusNode _focusNode = FocusNode();

  late StoicPassage _passage;
  String _previousText = '';
  String? _pasteWarning;

  double _similarity = 0.0;
  int _typedWordCount = 0;
  int _targetWordCount = 0;

  @override
  void initState() {
    super.initState();
    _passage = widget.initialPassage ?? StoicPassages.getRandom();
    _targetWordCount = _passage.wordCount;
  }

  @override
  void dispose() {
    _controller.dispose();
    _focusNode.dispose();
    super.dispose();
  }

  void _shufflePassage() {
    setState(() {
      _passage = StoicPassages.getRandom();
      _targetWordCount = _passage.wordCount;
      _controller.clear();
      _previousText = '';
      _pasteWarning = null;
      _similarity = 0.0;
      _typedWordCount = 0;
    });
  }

  String _normalize(String input) {
    return input
        .trim()
        .toLowerCase()
        .replaceAll(RegExp(r'[^\w\s]'), '') // remove punctuation for fair match
        .replaceAll(RegExp(r'\s+'), ' ');
  }

  int _levenshtein(String s, String t) {
    if (s == t) return 0;
    if (s.isEmpty) return t.length;
    if (t.isEmpty) return s.length;

    List<int> v0 = List<int>.generate(t.length + 1, (i) => i);
    List<int> v1 = List<int>.filled(t.length + 1, 0);

    for (int i = 0; i < s.length; i++) {
      v1[0] = i + 1;
      for (int j = 0; j < t.length; j++) {
        final cost = (s[i] == t[j]) ? 0 : 1;
        v1[j + 1] = min(v1[j] + 1, min(v0[j + 1] + 1, v0[j] + cost));
      }
      for (int j = 0; j <= t.length; j++) {
        v0[j] = v1[j];
      }
    }
    return v1[t.length];
  }

  void _calculateMetrics(String text) {
    final normInput = _normalize(text);
    final normTarget = _normalize(_passage.text);

    if (normInput.isEmpty) {
      _similarity = 0.0;
      _typedWordCount = 0;
      return;
    }

    final words = normInput.split(' ').where((w) => w.isNotEmpty).length;
    _typedWordCount = words;

    final dist = _levenshtein(normTarget, normInput);
    final maxLen = max(normTarget.length, normInput.length);
    if (maxLen == 0) {
      _similarity = 100.0;
    } else {
      final sim = max(0.0, (1.0 - (dist / maxLen)) * 100.0);
      _similarity = sim;
    }
  }

  void _onTextChanged(String text) {
    // Detect clipboard paste or batch injection (> 5 chars delta)
    final delta = text.length - _previousText.length;
    if (delta > 5) {
      // Revert text to previous
      _controller.text = _previousText;
      _controller.selection = TextSelection.collapsed(
        offset: _previousText.length,
      );
      setState(() {
        _pasteWarning = 'Paste rejected! Manual mindful typing required.';
      });
      return;
    }

    _previousText = text;
    _calculateMetrics(text);
    setState(() {
      _pasteWarning = null;
    });
  }

  @override
  Widget build(BuildContext context) {
    final isUnlocked = _similarity >= 95.0;

    return Dialog(
      backgroundColor: AppColors.surface,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16),
        side: const BorderSide(color: AppColors.border, width: 1),
      ),
      insetPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 20),
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 580, maxHeight: 720),
        child: Padding(
          padding: const EdgeInsets.all(20.0),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              // Header
              Row(
                children: [
                  Container(
                    padding: const EdgeInsets.all(8),
                    decoration: BoxDecoration(
                      color: AppColors.errorRed.withAlpha(35),
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: const Icon(
                      Icons.shield_rounded,
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
                          'Transcribe the passage below to override focus limits.',
                          style: AppTypography.bodySmall,
                        ),
                      ],
                    ),
                  ),
                  IconButton(
                    onPressed: _shufflePassage,
                    icon: const Icon(Icons.refresh_rounded, size: 20),
                    color: AppColors.textSecondary,
                    tooltip: 'New passage',
                  ),
                ],
              ),
              const SizedBox(height: 14),

              // Target Passage Blockquote Card
              Container(
                decoration: BoxDecoration(
                  color: AppColors.background,
                  borderRadius: BorderRadius.circular(10),
                  border: const Border(
                    left: BorderSide(color: AppColors.primaryAccent, width: 4),
                    top: BorderSide(color: AppColors.border),
                    right: BorderSide(color: AppColors.border),
                    bottom: BorderSide(color: AppColors.border),
                  ),
                ),
                padding: const EdgeInsets.all(14),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Container(
                          padding: const EdgeInsets.symmetric(
                              horizontal: 8, vertical: 3),
                          decoration: BoxDecoration(
                            color: AppColors.surfaceElevated,
                            borderRadius: BorderRadius.circular(6),
                            border: Border.all(
                                color: AppColors.primaryAccent.withAlpha(60)),
                          ),
                          child: Text(
                            _passage.category.toUpperCase(),
                            style: AppTypography.accentMono.copyWith(
                              fontSize: 10,
                              color: AppColors.primaryAccent,
                            ),
                          ),
                        ),
                        Text(
                          '— ${_passage.author}',
                          style: AppTypography.bodySmall.copyWith(
                            fontWeight: FontWeight.bold,
                            color: AppColors.textPrimary,
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 8),
                    SelectableText(
                      '"${_passage.text}"',
                      style: AppTypography.bodyMedium.copyWith(
                        fontStyle: FontStyle.italic,
                        height: 1.45,
                        color: AppColors.textPrimary,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 12),

              // Anti-Paste Warning
              if (_pasteWarning != null) ...[
                Container(
                  padding:
                      const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                  margin: const EdgeInsets.only(bottom: 8),
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
              ],

              // Transcription Typing Field
              Expanded(
                child: Container(
                  decoration: BoxDecoration(
                    color: AppColors.background,
                    borderRadius: BorderRadius.circular(10),
                    border: Border.all(
                      color: isUnlocked
                          ? AppColors.successGreen.withAlpha(150)
                          : AppColors.border,
                    ),
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
                      hintText: 'Type the passage here word-for-word...',
                      hintStyle: AppTypography.bodySmall,
                      border: InputBorder.none,
                      isDense: true,
                    ),
                  ),
                ),
              ),
              const SizedBox(height: 12),

              // Match Progress Indicators
              Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Text(
                        'Accuracy: ${_similarity.toStringAsFixed(1)}% / 95.0%',
                        style: AppTypography.accentMono.copyWith(
                          fontSize: 12,
                          color: isUnlocked
                              ? AppColors.successGreen
                              : (_similarity > 50
                                  ? AppColors.primaryAccent
                                  : AppColors.textSecondary),
                        ),
                      ),
                      Text(
                        'Words: $_typedWordCount / $_targetWordCount',
                        style: AppTypography.bodySmall.copyWith(
                          color: isUnlocked
                              ? AppColors.successGreen
                              : AppColors.textSecondary,
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 6),
                  ClipRRect(
                    borderRadius: BorderRadius.circular(4),
                    child: LinearProgressIndicator(
                      value: min(1.0, _similarity / 100.0),
                      minHeight: 6,
                      backgroundColor: AppColors.surfaceElevated,
                      valueColor: AlwaysStoppedAnimation<Color>(
                        isUnlocked
                            ? AppColors.successGreen
                            : (_similarity > 50
                                ? AppColors.primaryAccent
                                : AppColors.errorRed),
                      ),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 16),

              // Action Buttons
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
                        padding: const EdgeInsets.symmetric(vertical: 13),
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(8),
                        ),
                      ),
                      child: Text(
                        'Break Lockout',
                        style: AppTypography.bodyMedium.copyWith(
                          color: isUnlocked
                              ? AppColors.errorRed
                              : AppColors.textSecondary.withAlpha(80),
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
                        padding: const EdgeInsets.symmetric(vertical: 13),
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
