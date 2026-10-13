import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/constants/app_colors.dart';
import '../../../../core/constants/app_typography.dart';
import '../../../../core/network/supabase_service.dart';
import '../../../gamification/presentation/screens/battlepass_shop_screen.dart';
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
            tooltip: 'Season 1 Battlepass',
            onPressed: () => BattlepassShopScreen.show(context),
            icon: const Icon(
              Icons.military_tech_rounded,
              color: Colors.amber,
              size: 24,
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
                  size: 24,
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
                      size: 9,
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
                  const SizedBox(height: 18),

                  // 7-Day / 30-Day Discipline Duel Banner
                  _buildChallengeBanner(context, ref),
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

  // 7-Day & 30-Day Discipline Duel Head-to-Head Banner
  Widget _buildChallengeBanner(BuildContext context, WidgetRef ref) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        gradient: LinearGradient(
          colors: [
            AppColors.surfaceElevated,
            AppColors.surface,
          ],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(
          color: AppColors.primaryAccent.withAlpha(70),
          width: 1.2,
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Row(
                children: [
                  Container(
                    padding: const EdgeInsets.all(6),
                    decoration: BoxDecoration(
                      color: AppColors.primaryAccent.withAlpha(35),
                      shape: BoxShape.circle,
                    ),
                    child: const Icon(
                      Icons.sports_kabaddi_rounded,
                      color: AppColors.primaryAccent,
                      size: 18,
                    ),
                  ),
                  const SizedBox(width: 10),
                  Text(
                    'DISCIPLINE DUEL',
                    style: AppTypography.accentMono.copyWith(
                      fontSize: 12,
                      fontWeight: FontWeight.bold,
                      letterSpacing: 1.0,
                    ),
                  ),
                ],
              ),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                decoration: BoxDecoration(
                  color: Colors.amber.withAlpha(25),
                  borderRadius: BorderRadius.circular(6),
                  border: Border.all(color: Colors.amber.withAlpha(80)),
                ),
                child: Text(
                  '7-Day Sprint',
                  style: AppTypography.bodySmall.copyWith(
                    fontSize: 10,
                    fontWeight: FontWeight.bold,
                    color: Colors.amber,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),

          // Head-to-Head Split Comparison Bar
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                'You: 1,850 XP',
                style: AppTypography.bodySmall.copyWith(
                  fontWeight: FontWeight.bold,
                  color: AppColors.primaryAccent,
                ),
              ),
              Text(
                'Alex: 1,620 XP',
                style: AppTypography.bodySmall.copyWith(
                  fontWeight: FontWeight.bold,
                  color: AppColors.textSecondary,
                ),
              ),
            ],
          ),
          const SizedBox(height: 6),
          ClipRRect(
            borderRadius: BorderRadius.circular(6),
            child: SizedBox(
              height: 8,
              child: Row(
                children: [
                  Expanded(
                    flex: 1850,
                    child: Container(color: AppColors.primaryAccent),
                  ),
                  const SizedBox(width: 2),
                  Expanded(
                    flex: 1620,
                    child: Container(color: AppColors.border),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 12),

          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                '3 days remaining · Stake: 50 ★',
                style: AppTypography.bodySmall.copyWith(fontSize: 11),
              ),
              ElevatedButton.icon(
                onPressed: () => _showChallengeModal(context),
                icon: const Icon(Icons.add_circle_outline_rounded, size: 14),
                label: const Text('New Duel', style: TextStyle(fontSize: 11, fontWeight: FontWeight.bold)),
                style: ElevatedButton.styleFrom(
                  padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                  backgroundColor: AppColors.primaryAccent,
                  foregroundColor: AppColors.background,
                  elevation: 0,
                  minimumSize: Size.zero,
                  tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(8),
                  ),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }

  // Interactive 7-Day & 30-Day Challenge Modal
  void _showChallengeModal(BuildContext context) {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) => _ChallengeModalSheet(),
    );
  }
}

class _ChallengeModalSheet extends StatefulWidget {
  @override
  State<_ChallengeModalSheet> createState() => _ChallengeModalSheetState();
}

class _ChallengeModalSheetState extends State<_ChallengeModalSheet> {
  final _handleController = TextEditingController();
  int _selectedDurationDays = 7;
  int _starWager = 50;

  @override
  void dispose() {
    _handleController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: EdgeInsets.only(
        bottom: MediaQuery.of(context).viewInsets.bottom,
      ),
      child: Container(
        decoration: const BoxDecoration(
          color: AppColors.surface,
          borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
          border: Border(
            top: BorderSide(color: AppColors.border, width: 1.5),
          ),
        ),
        padding: const EdgeInsets.fromLTRB(20, 16, 20, 28),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Center(
              child: Container(
                width: 40,
                height: 4,
                decoration: BoxDecoration(
                  color: AppColors.border,
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
            ),
            const SizedBox(height: 18),
            Row(
              children: [
                const Icon(
                  Icons.sports_kabaddi_rounded,
                  color: AppColors.primaryAccent,
                  size: 24,
                ),
                const SizedBox(width: 10),
                Text('Challenge a Spartan Friend', style: AppTypography.h2),
              ],
            ),
            const SizedBox(height: 6),
            Text(
              'Engage in a head-to-head XP sprint. Highest discipline wins the Star vault prize.',
              style: AppTypography.bodySmall,
            ),
            const SizedBox(height: 18),

            // Friend Handle Input
            Text('FRIEND HANDLE', style: AppTypography.bodySmall),
            const SizedBox(height: 6),
            TextField(
              controller: _handleController,
              style: AppTypography.bodyMedium,
              decoration: InputDecoration(
                prefixText: '@',
                prefixStyle: AppTypography.accentMono.copyWith(
                  color: AppColors.primaryAccent,
                  fontSize: 16,
                ),
                hintText: 'alex_spartan',
                hintStyle: AppTypography.bodyMedium.copyWith(
                  color: AppColors.textSecondary.withAlpha(120),
                ),
                filled: true,
                fillColor: AppColors.surfaceElevated,
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(12),
                  borderSide: const BorderSide(color: AppColors.border),
                ),
                enabledBorder: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(12),
                  borderSide: const BorderSide(color: AppColors.border),
                ),
                focusedBorder: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(12),
                  borderSide: const BorderSide(color: AppColors.primaryAccent),
                ),
              ),
            ),
            const SizedBox(height: 18),

            // Duration Selector (7-Day vs 30-Day)
            Text('CHALLENGE DURATION', style: AppTypography.bodySmall),
            const SizedBox(height: 8),
            Row(
              children: [
                Expanded(
                  child: GestureDetector(
                    onTap: () => setState(() => _selectedDurationDays = 7),
                    child: Container(
                      padding: const EdgeInsets.symmetric(vertical: 12),
                      decoration: BoxDecoration(
                        color: _selectedDurationDays == 7
                            ? AppColors.primaryAccent.withAlpha(30)
                            : AppColors.surfaceElevated,
                        borderRadius: BorderRadius.circular(10),
                        border: Border.all(
                          color: _selectedDurationDays == 7
                              ? AppColors.primaryAccent
                              : AppColors.border,
                        ),
                      ),
                      child: Column(
                        children: [
                          Text(
                            '7 Days',
                            style: AppTypography.h2.copyWith(
                              fontSize: 15,
                              color: _selectedDurationDays == 7
                                  ? AppColors.primaryAccent
                                  : AppColors.textPrimary,
                            ),
                          ),
                          const SizedBox(height: 2),
                          Text(
                            'High Intensity Sprint',
                            style: AppTypography.bodySmall.copyWith(fontSize: 10),
                          ),
                        ],
                      ),
                    ),
                  ),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: GestureDetector(
                    onTap: () => setState(() => _selectedDurationDays = 30),
                    child: Container(
                      padding: const EdgeInsets.symmetric(vertical: 12),
                      decoration: BoxDecoration(
                        color: _selectedDurationDays == 30
                            ? AppColors.primaryAccent.withAlpha(30)
                            : AppColors.surfaceElevated,
                        borderRadius: BorderRadius.circular(10),
                        border: Border.all(
                          color: _selectedDurationDays == 30
                              ? AppColors.primaryAccent
                              : AppColors.border,
                        ),
                      ),
                      child: Column(
                        children: [
                          Text(
                            '30 Days',
                            style: AppTypography.h2.copyWith(
                              fontSize: 15,
                              color: _selectedDurationDays == 30
                                  ? AppColors.primaryAccent
                                  : AppColors.textPrimary,
                            ),
                          ),
                          const SizedBox(height: 2),
                          Text(
                            'Monthly Endurance Cup',
                            style: AppTypography.bodySmall.copyWith(fontSize: 10),
                          ),
                        ],
                      ),
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 18),

            // Star Stake
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text('STAR WAGER STAKE', style: AppTypography.bodySmall),
                Text(
                  '★ $_starWager Stars',
                  style: AppTypography.accentMono.copyWith(
                    fontSize: 13,
                    color: Colors.amber,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ],
            ),
            Slider(
              value: _starWager.toDouble(),
              min: 10,
              max: 200,
              divisions: 19,
              activeColor: Colors.amber,
              inactiveColor: AppColors.surfaceElevated,
              label: '$_starWager Stars',
              onChanged: (val) => setState(() => _starWager = val.round()),
            ),
            const SizedBox(height: 16),

            // Send Challenge Button
            SizedBox(
              width: double.infinity,
              height: 48,
              child: ElevatedButton(
                onPressed: () {
                  final handle = _handleController.text.trim();
                  Navigator.of(context).pop();
                  ScaffoldMessenger.of(context).showSnackBar(
                    SnackBar(
                      content: Text(
                        handle.isEmpty
                            ? 'Issued open $_selectedDurationDays-day Discipline Duel! ⚡'
                            : 'Challenge sent to @$handle for $_selectedDurationDays days! ⚡',
                      ),
                      backgroundColor: AppColors.surfaceElevated,
                    ),
                  );
                },
                style: ElevatedButton.styleFrom(
                  backgroundColor: AppColors.primaryAccent,
                  foregroundColor: AppColors.background,
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(12),
                  ),
                ),
                child: Text(
                  'Issue Duel Challenge',
                  style: AppTypography.h2.copyWith(
                    color: AppColors.background,
                    fontSize: 15,
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
