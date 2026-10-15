import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/constants/app_colors.dart';
import '../../../../core/constants/app_typography.dart';
import '../../../../core/network/supabase_service.dart';

class DuelChallengeCard extends ConsumerWidget {
  final Map<String, dynamic> challenge;
  final VoidCallback? onRefresh;

  const DuelChallengeCard({
    super.key,
    required this.challenge,
    this.onRefresh,
  });

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final supabase = ref.watch(supabaseServiceProvider);
    final currentUserId = supabase.currentUser?.id;

    final challengeId = challenge['id'] as String? ?? '';
    final creator = challenge['creator'] as Map<String, dynamic>? ?? {};
    final opponent = challenge['opponent'] as Map<String, dynamic>? ?? {};
    final creatorId = challenge['creator_id'] as String? ?? '';
    final opponentId = challenge['opponent_id'] as String? ?? '';

    final creatorName = creator['username'] as String? ?? 'Spartan';
    final opponentName = opponent['username'] as String? ?? 'Challenger';
    final creatorXp = (creator['total_xp'] as num?)?.toInt() ?? 0;
    final opponentXp = (opponent['total_xp'] as num?)?.toInt() ?? 0;

    final durationDays = (challenge['duration_days'] as num?)?.toInt() ?? 7;
    final wagerStars = (challenge['wager_stars'] as num?)?.toInt() ?? 0;
    final status = challenge['status'] as String? ?? 'pending';

    final isOpponent = currentUserId == opponentId;
    final isPending = status == 'pending';
    final isActive = status == 'active';

    final totalDuelXp = creatorXp + opponentXp;
    final creatorRatio =
        totalDuelXp == 0 ? 0.5 : (creatorXp / totalDuelXp).clamp(0.05, 0.95);
    final opponentRatio = 1.0 - creatorRatio;

    return Container(
      margin: const EdgeInsets.only(bottom: 12),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(
          color: isActive
              ? AppColors.primaryAccent.withAlpha(100)
              : AppColors.border,
          width: isActive ? 1.5 : 1,
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          // Header: Duration badge, wager, and status
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                decoration: BoxDecoration(
                  color: AppColors.surfaceElevated,
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Text(
                  '$durationDays-Day Duel ⚔️',
                  style: AppTypography.accentMono.copyWith(
                    fontSize: 11,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ),
              Row(
                children: [
                  if (wagerStars > 0) ...[
                    Container(
                      padding: const EdgeInsets.symmetric(
                          horizontal: 8, vertical: 3),
                      decoration: BoxDecoration(
                        color: Colors.amber.withAlpha(25),
                        borderRadius: BorderRadius.circular(8),
                        border: Border.all(color: Colors.amber.withAlpha(80)),
                      ),
                      child: Row(
                        children: [
                          const Icon(Icons.star_rounded,
                              size: 14, color: Colors.amber),
                          const SizedBox(width: 3),
                          Text(
                            '$wagerStars Wager',
                            style: AppTypography.accentMono.copyWith(
                              fontSize: 11,
                              color: Colors.amber,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                        ],
                      ),
                    ),
                    const SizedBox(width: 8),
                  ],
                  Container(
                    padding:
                        const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                    decoration: BoxDecoration(
                      color: isActive
                          ? AppColors.successGreen.withAlpha(25)
                          : Colors.orange.withAlpha(25),
                      borderRadius: BorderRadius.circular(8),
                    ),
                    child: Text(
                      status.toUpperCase(),
                      style: AppTypography.accentMono.copyWith(
                        fontSize: 10,
                        fontWeight: FontWeight.bold,
                        color: isActive
                            ? AppColors.successGreen
                            : Colors.orange,
                      ),
                    ),
                  ),
                ],
              ),
            ],
          ),
          const SizedBox(height: 14),

          // Head-to-Head names & XP
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      creatorId == currentUserId ? 'You (@$creatorName)' : '@$creatorName',
                      style: AppTypography.bodySmall.copyWith(
                        fontWeight: FontWeight.bold,
                        color: AppColors.primaryAccent,
                      ),
                      overflow: TextOverflow.ellipsis,
                    ),
                    Text(
                      '$creatorXp XP',
                      style: AppTypography.accentMono.copyWith(
                        fontSize: 12,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ],
                ),
              ),
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 8.0),
                child: Text('VS',
                    style: AppTypography.accentMono.copyWith(
                      color: AppColors.textSecondary,
                      fontWeight: FontWeight.bold,
                      fontSize: 12,
                    )),
              ),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.end,
                  children: [
                    Text(
                      opponentId == currentUserId ? 'You (@$opponentName)' : '@$opponentName',
                      style: AppTypography.bodySmall.copyWith(
                        fontWeight: FontWeight.bold,
                        color: const Color(0xFFF59E0B),
                      ),
                      overflow: TextOverflow.ellipsis,
                    ),
                    Text(
                      '$opponentXp XP',
                      style: AppTypography.accentMono.copyWith(
                        fontSize: 12,
                        fontWeight: FontWeight.w600,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),

          // Head-to-Head XP Progress Bar
          ClipRRect(
            borderRadius: BorderRadius.circular(6),
            child: SizedBox(
              height: 10,
              child: Row(
                children: [
                  Expanded(
                    flex: (creatorRatio * 100).round(),
                    child: Container(
                      color: AppColors.primaryAccent,
                    ),
                  ),
                  const SizedBox(width: 2),
                  Expanded(
                    flex: (opponentRatio * 100).round(),
                    child: Container(
                      color: const Color(0xFFF59E0B),
                    ),
                  ),
                ],
              ),
            ),
          ),

          // Pending response actions if current user is opponent
          if (isPending && isOpponent) ...[
            const SizedBox(height: 14),
            Row(
              children: [
                Expanded(
                  child: OutlinedButton(
                    onPressed: () async {
                      await supabase.declineChallenge(challengeId);
                      onRefresh?.call();
                    },
                    style: OutlinedButton.styleFrom(
                      foregroundColor: AppColors.errorRed,
                      side: const BorderSide(color: AppColors.border),
                      padding: const EdgeInsets.symmetric(vertical: 8),
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(8),
                      ),
                    ),
                    child: const Text('Decline',
                        style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold)),
                  ),
                ),
                const SizedBox(width: 10),
                Expanded(
                  child: ElevatedButton(
                    onPressed: () async {
                      await supabase.acceptChallenge(challengeId);
                      onRefresh?.call();
                    },
                    style: ElevatedButton.styleFrom(
                      backgroundColor: AppColors.primaryAccent,
                      foregroundColor: AppColors.background,
                      padding: const EdgeInsets.symmetric(vertical: 8),
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(8),
                      ),
                    ),
                    child: const Text('Accept Duel ⚔️',
                        style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold)),
                  ),
                ),
              ],
            ),
          ] else if (isPending && creatorId == currentUserId) ...[
            const SizedBox(height: 10),
            Text(
              'Awaiting @$opponentName to accept the challenge...',
              style: AppTypography.bodySmall.copyWith(
                fontSize: 11,
                fontStyle: FontStyle.italic,
                color: AppColors.textSecondary,
              ),
              textAlign: TextAlign.center,
            ),
          ],
        ],
      ),
    );
  }
}
