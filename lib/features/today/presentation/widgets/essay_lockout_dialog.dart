import 'dart:math';

import 'package:flutter/material.dart';

import '../../../../core/constants/app_colors.dart';
import '../../../../core/constants/app_typography.dart';
import '../../domain/stoic_passages.dart';

enum LockoutUnlockResult {
  stayFocused,
  emergency5Min,
  dismissToday,
}

class EssayLockoutDialog extends StatefulWidget {
  final StoicPassage? initialPassage;
  final String appName;

  const EssayLockoutDialog({
    super.key,
    this.initialPassage,
    this.appName = 'Restricted App',
  });

  static Future<LockoutUnlockResult?> show(
    BuildContext context, {
    StoicPassage? passage,
    String appName = 'Restricted App',
  }) {
    return showDialog<LockoutUnlockResult>(
      context: context,
      barrierDismissible: false,
      builder: (context) => EssayLockoutDialog(
        initialPassage: passage,
        appName: appName,
      ),
    );
  }

  @override
  State<EssayLockoutDialog> createState() => _EssayLockoutDialogState();
}

class _EssayLockoutDialogState extends State<EssayLockoutDialog> {
  final TextEditingController _controller = TextEditingController();
  final FocusNode _focusNode = FocusNode();

  late StoicPassage activePassage;
  String _previousText = '';
  String? _pasteWarning;

  double _similarity = 0.0;
  int _typedWordCount = 0;

  @override
  void initState() {
    super.initState();
    activePassage = widget.initialPassage ??
        (StoicPassages.all.isNotEmpty
            ? StoicPassages.all[Random().nextInt(StoicPassages.all.length)]
            : const StoicPassage(
                id: 'fallback_marcus',
                author: 'Marcus Aurelius',
                title: 'Meditations',
                category: 'Procrastination',
                text:
                    'You could be good today, but instead you choose tomorrow. Concentrate every minute on doing what is in front of you with precise and genuine seriousness.',
              ));
  }

  @override
  void dispose() {
    _controller.dispose();
    _focusNode.dispose();
    super.dispose();
  }

  void _shufflePassage() {
    setState(() {
      activePassage = StoicPassages.getRandom();
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
        .replaceAll(RegExp(r'[^\w\s]'), '')
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
        final cost = (s.codeUnitAt(i) == t.codeUnitAt(j)) ? 0 : 1;
        v1[j + 1] = min(v1[j] + 1, min(v0[j + 1] + 1, v0[j] + cost));
      }
      for (int j = 0; j <= t.length; j++) {
        v0[j] = v1[j];
      }
    }
    return v1[t.length];
  }

  double _calculateSimilarity(String typed, String target) {
    final normTyped = _normalize(typed);
    final normTarget = _normalize(target);

    if (normTarget.isEmpty) return 100.0;
    if (normTyped.isEmpty) return 0.0;

    final dist = _levenshtein(normTyped, normTarget);
    final maxLen = max(normTyped.length, normTarget.length);
    final sim = ((maxLen - dist) / maxLen) * 100.0;
    return sim.clamp(0.0, 100.0);
  }

  void _onTextChanged(String text) {
    final delta = text.length - _previousText.length;
    if (delta > 5) {
      setState(() {
        _pasteWarning = 'Pasting is strictly prohibited. Type every word.';
        _controller.text = _previousText;
        _controller.selection = TextSelection.fromPosition(
          TextPosition(offset: _previousText.length),
        );
      });
      return;
    }

    _previousText = text;

    final words = text.trim().isEmpty
        ? 0
        : text.trim().split(RegExp(r'\s+')).length;
    final sim = _calculateSimilarity(text, activePassage.text);

    setState(() {
      _pasteWarning = null;
      _typedWordCount = words;
      _similarity = sim;
    });
  }

  void _showUnlockChoiceSheet() {
    showModalBottomSheet(
      context: context,
      backgroundColor: Colors.transparent,
      builder: (ctx) => Container(
        padding: const EdgeInsets.all(24),
        decoration: const BoxDecoration(
          color: AppColors.surface,
          borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
          border: Border(
            top: BorderSide(color: AppColors.primaryAccent, width: 2),
          ),
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
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
            Row(
              children: [
                Container(
                  padding: const EdgeInsets.all(10),
                  decoration: BoxDecoration(
                    color: AppColors.secondary.withAlpha(35),
                    shape: BoxShape.circle,
                  ),
                  child: const Icon(Icons.check_circle_rounded,
                      color: AppColors.secondary, size: 28),
                ),
                const SizedBox(width: 14),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('Discipline Check Passed!',
                          style: AppTypography.h2),
                      const SizedBox(height: 2),
                      Text('How would you like to proceed with ${widget.appName}?',
                          style: AppTypography.bodySmall),
                    ],
                  ),
                ),
              ],
            ),
            const SizedBox(height: 20),
            ElevatedButton.icon(
              onPressed: () {
                Navigator.of(ctx).pop();
                Navigator.of(context).pop(LockoutUnlockResult.emergency5Min);
              },
              icon: const Icon(Icons.timer_outlined, size: 20),
              label: const Text('Emergency 5 Minutes (Quick Bypass)'),
              style: ElevatedButton.styleFrom(
                backgroundColor: AppColors.primaryAccent,
                foregroundColor: AppColors.background,
                padding: const EdgeInsets.symmetric(vertical: 14),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(12),
                ),
              ),
            ),
            const SizedBox(height: 10),
            OutlinedButton.icon(
              onPressed: () {
                Navigator.of(ctx).pop();
                Navigator.of(context).pop(LockoutUnlockResult.dismissToday);
              },
              icon: const Icon(Icons.lock_open_rounded, size: 20),
              label: const Text('Dismiss Limit for Remainder of Today'),
              style: OutlinedButton.styleFrom(
                side: const BorderSide(color: AppColors.border),
                foregroundColor: AppColors.textPrimary,
                padding: const EdgeInsets.symmetric(vertical: 14),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(12),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final isUnlocked = _similarity >= 95.0;
    final totalWords = activePassage.text.trim().split(RegExp(r'\s+')).length;

    return PopScope(
      canPop: false,
      child: Dialog(
        backgroundColor: Colors.transparent,
        insetPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 24),
        child: Container(
          constraints: const BoxConstraints(maxWidth: 500, maxHeight: 680),
          decoration: BoxDecoration(
            color: AppColors.surface,
            borderRadius: BorderRadius.circular(20),
            border: Border.all(
              color: isUnlocked ? AppColors.secondary : AppColors.border,
              width: isUnlocked ? 2 : 1,
            ),
            boxShadow: [
              BoxShadow(
                color: isUnlocked
                    ? AppColors.secondary.withAlpha(40)
                    : AppColors.primaryAccent.withAlpha(20),
                blurRadius: 24,
                spreadRadius: 2,
              ),
            ],
          ),
          padding: const EdgeInsets.all(20),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              // Central Shield Icon with Cyan/Mint Ring Aura (Mockup #3 & #4)
              Center(
                child: Container(
                  width: 64,
                  height: 64,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    color: AppColors.surfaceElevated,
                    border: Border.all(
                      color: isUnlocked ? AppColors.secondary : AppColors.primaryAccent,
                      width: 2.5,
                    ),
                    boxShadow: [
                      BoxShadow(
                        color: (isUnlocked ? AppColors.secondary : AppColors.primaryAccent)
                            .withAlpha(50),
                        blurRadius: 16,
                      ),
                    ],
                  ),
                  child: Icon(
                    isUnlocked ? Icons.lock_open_rounded : Icons.shield_rounded,
                    color: isUnlocked ? AppColors.secondary : AppColors.primaryAccent,
                    size: 30,
                  ),
                ),
              ),
              const SizedBox(height: 12),
              Center(
                child: Text(
                  'Discipline Check',
                  style: AppTypography.h1.copyWith(fontSize: 20),
                ),
              ),
              const SizedBox(height: 2),
              Center(
                child: Text(
                  'Transcribe to unlock ${widget.appName}',
                  style: AppTypography.bodySmall,
                ),
              ),
              const SizedBox(height: 14),

              // Target Passage Card
              Container(
                decoration: BoxDecoration(
                  color: AppColors.background,
                  borderRadius: BorderRadius.circular(12),
                  border: Border.all(color: AppColors.border),
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
                            activePassage.category.toUpperCase(),
                            style: AppTypography.accentMono.copyWith(
                              fontSize: 10,
                              color: AppColors.primaryAccent,
                            ),
                          ),
                        ),
                        Row(
                          children: [
                            Text(
                              '— ${activePassage.author}',
                              style: AppTypography.bodySmall.copyWith(
                                fontWeight: FontWeight.bold,
                                color: AppColors.textPrimary,
                              ),
                            ),
                            const SizedBox(width: 4),
                            IconButton(
                              onPressed: _shufflePassage,
                              icon: const Icon(Icons.refresh_rounded, size: 16),
                              color: AppColors.textSecondary,
                              tooltip: 'Shuffle passage',
                              padding: EdgeInsets.zero,
                              constraints: const BoxConstraints(),
                            ),
                          ],
                        ),
                      ],
                    ),
                    const SizedBox(height: 8),
                    SelectableText(
                      activePassage.text,
                      style: const TextStyle(
                        color: Colors.white,
                        fontSize: 14,
                        height: 1.5,
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 10),

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
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(
                      color: isUnlocked
                          ? AppColors.secondary
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
              const SizedBox(height: 10),

              // Accuracy Progress Bar (Accuracy meter 95% to unlock)
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
                              ? AppColors.secondary
                              : (_similarity > 50
                                  ? AppColors.primaryAccent
                                  : AppColors.textSecondary),
                        ),
                      ),
                      Text(
                        'Words: $_typedWordCount / $totalWords (Paste disabled)',
                        style: AppTypography.bodySmall.copyWith(
                          color: isUnlocked
                              ? AppColors.secondary
                              : AppColors.textSecondary,
                          fontSize: 11,
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 6),
                  ClipRRect(
                    borderRadius: BorderRadius.circular(4),
                    child: LinearProgressIndicator(
                      value: (_similarity / 100.0).clamp(0.0, 1.0),
                      minHeight: 7,
                      backgroundColor: AppColors.surfaceElevated,
                      valueColor: AlwaysStoppedAnimation(
                        isUnlocked
                            ? AppColors.secondary
                            : (_similarity > 50
                                ? AppColors.primaryAccent
                                : AppColors.errorRed),
                      ),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 14),

              // Action Buttons
              Row(
                children: [
                  Expanded(
                    child: OutlinedButton(
                      onPressed: () =>
                          Navigator.of(context).pop(LockoutUnlockResult.stayFocused),
                      style: OutlinedButton.styleFrom(
                        side: const BorderSide(color: AppColors.border),
                        padding: const EdgeInsets.symmetric(vertical: 13),
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(10),
                        ),
                      ),
                      child: Text(
                        'Cancel',
                        style: AppTypography.bodyMedium.copyWith(
                          color: AppColors.textSecondary,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    flex: 2,
                    child: ElevatedButton(
                      onPressed: isUnlocked ? _showUnlockChoiceSheet : null,
                      style: ElevatedButton.styleFrom(
                        backgroundColor: isUnlocked
                            ? AppColors.secondary
                            : AppColors.surfaceElevated,
                        foregroundColor: isUnlocked
                            ? AppColors.background
                            : AppColors.textSecondary,
                        padding: const EdgeInsets.symmetric(vertical: 13),
                        elevation: 0,
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(10),
                        ),
                      ),
                      child: Text(
                        isUnlocked ? 'Verify & Choose Unlock' : 'Verify Transcription',
                        style: AppTypography.bodyMedium.copyWith(
                          color: isUnlocked
                              ? AppColors.background
                              : AppColors.textSecondary,
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
