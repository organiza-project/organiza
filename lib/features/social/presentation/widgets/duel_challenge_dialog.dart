import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/constants/app_colors.dart';
import '../../../../core/constants/app_typography.dart';
import '../../../../core/network/supabase_service.dart';

class DuelChallengeDialog extends ConsumerStatefulWidget {
  final String friendId;
  final String friendName;

  const DuelChallengeDialog({
    super.key,
    required this.friendId,
    required this.friendName,
  });

  static Future<bool?> show(
    BuildContext context, {
    required String friendId,
    required String friendName,
  }) {
    return showDialog<bool>(
      context: context,
      builder: (context) => DuelChallengeDialog(
        friendId: friendId,
        friendName: friendName,
      ),
    );
  }

  @override
  ConsumerState<DuelChallengeDialog> createState() =>
      _DuelChallengeDialogState();
}

class _DuelChallengeDialogState extends ConsumerState<DuelChallengeDialog> {
  int _durationDays = 7;
  int _wagerStars = 25;
  bool _isSubmitting = false;

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      backgroundColor: AppColors.surface,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(20),
        side: const BorderSide(color: AppColors.border),
      ),
      title: Row(
        children: [
          Container(
            padding: const EdgeInsets.all(8),
            decoration: BoxDecoration(
              color: AppColors.primaryAccent.withAlpha(30),
              borderRadius: BorderRadius.circular(10),
            ),
            child: const Icon(
              Icons.military_tech_rounded,
              color: AppColors.primaryAccent,
              size: 24,
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Challenge @${widget.friendName}', style: AppTypography.h2),
                Text('Head-to-Head XP Duel', style: AppTypography.bodySmall),
              ],
            ),
          ),
        ],
      ),
      content: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('DUEL DURATION', style: AppTypography.bodySmall),
            const SizedBox(height: 8),
            Row(
              children: [
                Expanded(
                  child: _buildDurationOption(
                    title: '7-Day Sprint',
                    days: 7,
                    subtitle: 'Fast-paced weekly battle',
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: _buildDurationOption(
                    title: '30-Day Siege',
                    days: 30,
                    subtitle: 'Epic monthly discipline trial',
                  ),
                ),
              ],
            ),
            const SizedBox(height: 20),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text('STAR WAGER', style: AppTypography.bodySmall),
                Container(
                  padding:
                      const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                  decoration: BoxDecoration(
                    color: Colors.amber.withAlpha(25),
                    borderRadius: BorderRadius.circular(8),
                    border: Border.all(color: Colors.amber.withAlpha(80)),
                  ),
                  child: Row(
                    children: [
                      const Icon(Icons.star_rounded,
                          size: 16, color: Colors.amber),
                      const SizedBox(width: 4),
                      Text(
                        '$_wagerStars Stars',
                        style: AppTypography.accentMono.copyWith(
                          color: Colors.amber,
                          fontWeight: FontWeight.bold,
                          fontSize: 12,
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
            const SizedBox(height: 8),
            Slider(
              value: _wagerStars.toDouble(),
              min: 10,
              max: 100,
              divisions: 9,
              activeColor: Colors.amber,
              inactiveColor: AppColors.surfaceElevated,
              label: '$_wagerStars Stars',
              onChanged: (val) => setState(() => _wagerStars = val.round()),
            ),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text('10 Stars', style: AppTypography.bodySmall.copyWith(fontSize: 10)),
                Text('50 Stars', style: AppTypography.bodySmall.copyWith(fontSize: 10)),
                Text('100 Stars', style: AppTypography.bodySmall.copyWith(fontSize: 10)),
              ],
            ),
            const SizedBox(height: 16),
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: AppColors.surfaceElevated,
                borderRadius: BorderRadius.circular(10),
                border: Border.all(color: AppColors.border),
              ),
              child: Row(
                children: [
                  const Icon(Icons.info_outline_rounded,
                      size: 16, color: AppColors.textSecondary),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      'The Spartan with the highest XP earned by the end of the duration wins the wager vault.',
                      style: AppTypography.bodySmall.copyWith(fontSize: 11),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(context).pop(false),
          child: Text('Cancel', style: AppTypography.bodySmall),
        ),
        ElevatedButton(
          onPressed: _isSubmitting ? null : _submitChallenge,
          style: ElevatedButton.styleFrom(
            backgroundColor: AppColors.primaryAccent,
            foregroundColor: AppColors.background,
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(10),
            ),
          ),
          child: _isSubmitting
              ? const SizedBox(
                  width: 16,
                  height: 16,
                  child: CircularProgressIndicator(
                    strokeWidth: 2,
                    color: AppColors.background,
                  ),
                )
              : const Text(
                  'Issue Duel Challenge ⚔️',
                  style: TextStyle(fontWeight: FontWeight.bold),
                ),
        ),
      ],
    );
  }

  Widget _buildDurationOption({
    required String title,
    required int days,
    required String subtitle,
  }) {
    final isSelected = _durationDays == days;

    return InkWell(
      onTap: () => setState(() => _durationDays = days),
      borderRadius: BorderRadius.circular(12),
      child: Container(
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: isSelected
              ? AppColors.primaryAccent.withAlpha(25)
              : AppColors.surfaceElevated,
          borderRadius: BorderRadius.circular(12),
          border: Border.all(
            color: isSelected ? AppColors.primaryAccent : AppColors.border,
            width: isSelected ? 1.5 : 1,
          ),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              title,
              style: AppTypography.bodyMedium.copyWith(
                fontWeight: FontWeight.bold,
                color: isSelected
                    ? AppColors.primaryAccent
                    : AppColors.textPrimary,
              ),
            ),
            const SizedBox(height: 4),
            Text(
              subtitle,
              style: AppTypography.bodySmall.copyWith(fontSize: 10),
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _submitChallenge() async {
    setState(() => _isSubmitting = true);
    final supabase = ref.read(supabaseServiceProvider);

    final success = await supabase.createChallenge(
      opponentId: widget.friendId,
      durationDays: _durationDays,
      wagerStars: _wagerStars,
    );

    setState(() => _isSubmitting = false);

    if (mounted) {
      Navigator.of(context).pop(success);
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            success
                ? 'Duel challenge dispatched to @${widget.friendName}! ⚔️'
                : 'Could not send challenge. Check your internet connection.',
          ),
          backgroundColor:
              success ? AppColors.successGreen : AppColors.errorRed,
        ),
      );
    }
  }
}
