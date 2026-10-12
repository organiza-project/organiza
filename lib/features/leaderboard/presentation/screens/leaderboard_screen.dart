import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/constants/app_colors.dart';
import '../../../../core/constants/app_typography.dart';
import '../../../../core/network/supabase_service.dart';
import '../../../gamification/presentation/screens/shop_profile_screen.dart';
import '../../../today/presentation/controllers/today_tasks_controller.dart';

final leaderboardUsersProvider =
    FutureProvider.autoDispose<List<LeaderboardUser>>((ref) async {
  final supabase = ref.watch(supabaseServiceProvider);
  return supabase.fetchLeaderboard();
});

class LeaderboardScreen extends ConsumerWidget {
  const LeaderboardScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final leaderboardAsync = ref.watch(leaderboardUsersProvider);
    final totalStarsAsync = ref.watch(totalStarsStreamProvider);
    final totalStars = totalStarsAsync.valueOrNull ?? 65;

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        backgroundColor: AppColors.background,
        elevation: 0,
        scrolledUnderElevation: 0,
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Discipline Leaderboard', style: AppTypography.h1),
            const SizedBox(height: 2),
            Text(
              'Weekly Stoic Cohort Rankings',
              style: AppTypography.bodySmall,
            ),
          ],
        ),
        actions: [
          Container(
            margin: const EdgeInsets.symmetric(vertical: 12),
            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
            decoration: BoxDecoration(
              color: Colors.amber.withAlpha(25),
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: Colors.amber.withAlpha(80)),
            ),
            child: Row(
              children: [
                const Icon(Icons.star_rounded, size: 16, color: Colors.amber),
                const SizedBox(width: 4),
                Text(
                  '$totalStars',
                  style: AppTypography.accentMono.copyWith(
                    fontSize: 12,
                    color: Colors.amber,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ],
            ),
          ),
          IconButton(
            tooltip: 'Star Shop & Profile',
            onPressed: () => ShopProfileScreen.show(context),
            icon: Stack(
              clipBehavior: Clip.none,
              children: [
                const Icon(
                  Icons.storefront_rounded,
                  color: AppColors.primaryAccent,
                  size: 26,
                ),
                Positioned(
                  right: -2,
                  top: -2,
                  child: Container(
                    padding: const EdgeInsets.all(2),
                    decoration: const BoxDecoration(
                      color: Colors.amber,
                      shape: BoxShape.circle,
                    ),
                    child: const Icon(
                      Icons.star_rounded,
                      size: 10,
                      color: Colors.black,
                    ),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(width: 8),
        ],
      ),
      body: SafeArea(
        child: leaderboardAsync.when(
          loading: () => const Center(
            child: CircularProgressIndicator(color: AppColors.primaryAccent),
          ),
          error: (err, _) => Center(
            child: Text(
              'Failed to load leaderboard: $err',
              style: AppTypography.bodySmall.copyWith(color: AppColors.errorRed),
            ),
          ),
          data: (users) {
            if (users.isEmpty) {
              return Center(
                child: Text('No participants yet.', style: AppTypography.bodySmall),
              );
            }

            final topThree = users.take(3).toList();
            final remainingUsers = users.skip(3).toList();

            return RefreshIndicator(
              onRefresh: () => ref.refresh(leaderboardUsersProvider.future),
              color: AppColors.primaryAccent,
              backgroundColor: AppColors.surface,
              child: ListView(
                padding: const EdgeInsets.fromLTRB(16, 8, 16, 80),
                children: [
                  // Top-3 Podium (Wireframe #9)
                  if (topThree.length >= 3)
                    _buildPodium(context, topThree),
                  const SizedBox(height: 20),

                  // Header for Cohort / Friends
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Text('COHORT SPARTANS & PEERS', style: AppTypography.bodySmall),
                      Text(
                        'Resets in 2d 14h',
                        style: AppTypography.bodySmall.copyWith(
                          fontSize: 11,
                          color: AppColors.textSecondary,
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 10),

                  // Remaining users list
                  ...remainingUsers.map((user) => _buildUserCard(context, ref, user)),
                ],
              ),
            );
          },
        ),
      ),
    );
  }

  // Top-3 Podium Widget (2nd, 1st, 3rd)
  Widget _buildPodium(BuildContext context, List<LeaderboardUser> topThree) {
    final first = topThree[0];
    final second = topThree[1];
    final third = topThree[2];

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 18),
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: AppColors.border),
      ),
      child: Column(
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.center,
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              // 2nd Place (Silver)
              Expanded(
                child: _buildPodiumColumn(
                  user: second,
                  pedestalHeight: 90,
                  pedestalColor: const Color(0xFF64748B),
                  medalColor: const Color(0xFFCBD5E1),
                  badgeLabel: '2nd',
                ),
              ),
              const SizedBox(width: 8),

              // 1st Place (Gold)
              Expanded(
                child: _buildPodiumColumn(
                  user: first,
                  pedestalHeight: 125,
                  pedestalColor: const Color(0xFFB45309),
                  medalColor: Colors.amber,
                  badgeLabel: '1st',
                  hasCrown: true,
                ),
              ),
              const SizedBox(width: 8),

              // 3rd Place (Bronze)
              Expanded(
                child: _buildPodiumColumn(
                  user: third,
                  pedestalHeight: 70,
                  pedestalColor: const Color(0xFF78350F),
                  medalColor: const Color(0xFFD97706),
                  badgeLabel: '3rd',
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildPodiumColumn({
    required LeaderboardUser user,
    required double pedestalHeight,
    required Color pedestalColor,
    required Color medalColor,
    required String badgeLabel,
    bool hasCrown = false,
  }) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        if (hasCrown) ...[
          const Icon(
            Icons.military_tech_rounded,
            color: Colors.amber,
            size: 24,
          ),
          const SizedBox(height: 2),
        ] else
          const SizedBox(height: 26),

        // Avatar Circle
        Stack(
          alignment: Alignment.center,
          clipBehavior: Clip.none,
          children: [
            Container(
              width: hasCrown ? 52 : 46,
              height: hasCrown ? 52 : 46,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: AppColors.surfaceElevated,
                border: Border.all(color: medalColor, width: 2),
              ),
              child: Center(
                child: Text(
                  user.username.isNotEmpty ? user.username[0] : '?',
                  style: AppTypography.h2.copyWith(
                    color: medalColor,
                    fontSize: hasCrown ? 18 : 15,
                  ),
                ),
              ),
            ),
            Positioned(
              bottom: -6,
              child: Container(
                padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 1),
                decoration: BoxDecoration(
                  color: medalColor,
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Text(
                  badgeLabel,
                  style: const TextStyle(
                    fontSize: 9,
                    fontWeight: FontWeight.bold,
                    color: Colors.black,
                  ),
                ),
              ),
            ),
          ],
        ),
        const SizedBox(height: 12),

        // Username
        Text(
          user.username,
          style: AppTypography.bodySmall.copyWith(
            fontWeight: FontWeight.bold,
            color: AppColors.textPrimary,
            fontSize: 12,
          ),
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          textAlign: TextAlign.center,
        ),
        const SizedBox(height: 2),

        // XP
        Text(
          '${user.xp} XP',
          style: AppTypography.accentMono.copyWith(
            fontSize: 11,
            color: medalColor,
          ),
        ),
        const SizedBox(height: 6),

        // Pedestal block
        Container(
          width: double.infinity,
          height: pedestalHeight,
          decoration: BoxDecoration(
            color: pedestalColor.withAlpha(60),
            borderRadius: const BorderRadius.vertical(top: Radius.circular(8)),
            border: Border.all(color: pedestalColor.withAlpha(120)),
          ),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(
                Icons.local_fire_department_rounded,
                size: 16,
                color: Colors.deepOrangeAccent.withAlpha(200),
              ),
              const SizedBox(height: 2),
              Text(
                '${user.streakDays}d streak',
                style: AppTypography.bodySmall.copyWith(
                  fontSize: 10,
                  color: AppColors.textSecondary,
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }

  Widget _buildUserCard(
    BuildContext context,
    WidgetRef ref,
    LeaderboardUser user,
  ) {
    return Container(
      margin: const EdgeInsets.only(bottom: 10),
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
      decoration: BoxDecoration(
        color: user.isCurrentUser
            ? AppColors.primaryAccent.withAlpha(20)
            : AppColors.surface,
        borderRadius: BorderRadius.circular(12),
        border: Border.all(
          color: user.isCurrentUser
              ? AppColors.primaryAccent.withAlpha(90)
              : AppColors.border,
        ),
      ),
      child: Row(
        children: [
          // Rank
          SizedBox(
            width: 24,
            child: Text(
              '#${user.rank}',
              style: AppTypography.accentMono.copyWith(
                fontSize: 13,
                color: user.isCurrentUser
                    ? AppColors.primaryAccent
                    : AppColors.textSecondary,
              ),
            ),
          ),
          const SizedBox(width: 8),

          // Avatar initial
          CircleAvatar(
            radius: 18,
            backgroundColor: user.isCurrentUser
                ? AppColors.primaryAccent
                : AppColors.surfaceElevated,
            foregroundColor: user.isCurrentUser
                ? AppColors.background
                : AppColors.textPrimary,
            child: Text(
              user.username.isNotEmpty ? user.username[0] : '?',
              style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 13),
            ),
          ),
          const SizedBox(width: 12),

          // Username & Streak
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Flexible(
                      child: Text(
                        user.username,
                        style: AppTypography.bodyMedium.copyWith(
                          fontWeight: user.isCurrentUser
                              ? FontWeight.bold
                              : FontWeight.w600,
                          color: user.isCurrentUser
                              ? AppColors.primaryAccent
                              : AppColors.textPrimary,
                        ),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                    if (user.isCurrentUser) ...[
                      const SizedBox(width: 6),
                      Container(
                        padding: const EdgeInsets.symmetric(
                            horizontal: 5, vertical: 1),
                        decoration: BoxDecoration(
                          color: AppColors.primaryAccent,
                          borderRadius: BorderRadius.circular(4),
                        ),
                        child: Text(
                          'YOU',
                          style: AppTypography.accentMono.copyWith(
                            fontSize: 9,
                            color: AppColors.background,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                      ),
                    ],
                  ],
                ),
                const SizedBox(height: 2),
                Row(
                  children: [
                    const Icon(
                      Icons.local_fire_department_rounded,
                      size: 13,
                      color: Colors.deepOrangeAccent,
                    ),
                    const SizedBox(width: 3),
                    Text(
                      '${user.streakDays}-day streak',
                      style: AppTypography.bodySmall.copyWith(fontSize: 11),
                    ),
                  ],
                ),
              ],
            ),
          ),

          // XP
          Text(
            '${user.xp} XP',
            style: AppTypography.accentMono.copyWith(
              fontSize: 13,
              fontWeight: FontWeight.bold,
            ),
          ),
          const SizedBox(width: 10),

          // Nudge Action Button (Wireframe Screen 9)
          if (!user.isCurrentUser)
            OutlinedButton(
              style: OutlinedButton.styleFrom(
                side: const BorderSide(color: AppColors.border),
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                minimumSize: Size.zero,
                tapTargetSize: MaterialTapTargetSize.shrinkWrap,
              ),
              onPressed: () async {
                await ref
                    .read(supabaseServiceProvider)
                    .nudgeFriend(user.id, user.username);
                if (context.mounted) {
                  ScaffoldMessenger.of(context).showSnackBar(
                    SnackBar(
                      content: Text('Nudged ${user.username} to stay disciplined! ⚡'),
                      duration: const Duration(seconds: 2),
                      backgroundColor: AppColors.surfaceElevated,
                    ),
                  );
                }
              },
              child: Text(
                'Nudge',
                style: AppTypography.bodySmall.copyWith(
                  color: AppColors.primaryAccent,
                  fontWeight: FontWeight.w600,
                  fontSize: 11,
                ),
              ),
            ),
        ],
      ),
    );
  }
}
