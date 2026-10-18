import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'package:shared_preferences/shared_preferences.dart';

import '../../../../core/config/supabase_config.dart';
import '../../../../core/constants/app_colors.dart';
import '../../../../core/constants/app_typography.dart';
import '../../../../core/network/supabase_service.dart';
import '../../../auth/presentation/controllers/auth_controller.dart';
import '../../../auth/presentation/widgets/custom_server_dialog.dart';
import '../../../gamification/domain/cosmetics_catalog.dart';
import '../../../gamification/presentation/screens/battlepass_shop_screen.dart';
import '../../../social/presentation/widgets/duel_challenge_dialog.dart';
import '../controllers/profile_controller.dart';
import 'settings_screen.dart';

class ProfileScreen extends ConsumerStatefulWidget {
  const ProfileScreen({super.key});

  @override
  ConsumerState<ProfileScreen> createState() => _ProfileScreenState();
}

class _ProfileScreenState extends ConsumerState<ProfileScreen> {
  final _friendSearchController = TextEditingController();
  List<Map<String, dynamic>> _friends = [];
  List<Map<String, dynamic>> _pendingRequests = [];
  bool _isLoadingFriends = false;
  bool _isAddingFriend = false;

  @override
  void initState() {
    super.initState();
    _loadFriends();
  }

  @override
  void dispose() {
    _friendSearchController.dispose();
    super.dispose();
  }

  Future<void> _loadFriends() async {
    setState(() => _isLoadingFriends = true);
    final supabase = ref.read(supabaseServiceProvider);
    final friends = await supabase.fetchFriends();
    final pending = await supabase.fetchPendingFriendRequests();
    if (mounted) {
      setState(() {
        _friends = friends;
        _pendingRequests = pending;
        _isLoadingFriends = false;
      });
    }
  }

  Future<void> _respondToRequest(String id, bool accept) async {
    final supabase = ref.read(supabaseServiceProvider);
    await supabase.respondToFriendRequest(id, accept);
    _loadFriends();
  }

  void _showAddFriendDialog() {
    showDialog(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (context, setDialogState) {
          return AlertDialog(
            backgroundColor: AppColors.surface,
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(16),
              side: const BorderSide(color: AppColors.border),
            ),
            title: Row(
              children: [
                const Icon(Icons.person_add_alt_1_rounded,
                    color: AppColors.primaryAccent),
                const SizedBox(width: 8),
                Text('Add Spartan Friend', style: AppTypography.h2),
              ],
            ),
            content: Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  'Enter their exact handle to dispatch a comrade request.',
                  style: AppTypography.bodySmall,
                ),
                const SizedBox(height: 14),
                TextField(
                  controller: _friendSearchController,
                  style: AppTypography.bodyMedium,
                  decoration: InputDecoration(
                    prefixText: '@',
                    prefixStyle: AppTypography.accentMono.copyWith(
                      color: AppColors.primaryAccent,
                    ),
                    hintText: 'alex_spartan',
                    hintStyle: AppTypography.bodyMedium.copyWith(
                      color: AppColors.textSecondary.withAlpha(120),
                    ),
                    filled: true,
                    fillColor: AppColors.surfaceElevated,
                    border: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(10),
                      borderSide: const BorderSide(color: AppColors.border),
                    ),
                    focusedBorder: OutlineInputBorder(
                      borderRadius: BorderRadius.circular(10),
                      borderSide:
                          const BorderSide(color: AppColors.primaryAccent),
                    ),
                  ),
                ),
              ],
            ),
            actions: [
              TextButton(
                onPressed: () => Navigator.of(ctx).pop(),
                child: Text('Cancel', style: AppTypography.bodySmall),
              ),
              ElevatedButton(
                onPressed: _isAddingFriend
                    ? null
                    : () async {
                        final handle =
                            _friendSearchController.text.trim().replaceAll('@', '');
                        if (handle.isEmpty) return;

                        setDialogState(() => _isAddingFriend = true);
                        final success = await ref
                            .read(supabaseServiceProvider)
                            .addFriendByUsername(handle);

                        setDialogState(() => _isAddingFriend = false);
                        if (context.mounted) {
                          Navigator.of(ctx).pop();
                          _friendSearchController.clear();
                          _loadFriends();
                          ScaffoldMessenger.of(context).showSnackBar(
                            SnackBar(
                              content: Text(
                                success
                                    ? 'Comrade request sent to @$handle! ⚔️'
                                    : 'User @$handle not found on Organiza network.',
                              ),
                              backgroundColor: success
                                  ? AppColors.successGreen
                                  : AppColors.errorRed,
                            ),
                          );
                        }
                      },
                style: ElevatedButton.styleFrom(
                  backgroundColor: AppColors.primaryAccent,
                  foregroundColor: AppColors.background,
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(8),
                  ),
                ),
                child: _isAddingFriend
                    ? const SizedBox(
                        width: 16,
                        height: 16,
                        child: CircularProgressIndicator(
                          strokeWidth: 2,
                          color: AppColors.background,
                        ),
                      )
                    : const Text('Add Friend',
                        style: TextStyle(fontWeight: FontWeight.bold)),
              ),
            ],
          );
        },
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final authState = ref.watch(authControllerProvider);
    final user = authState.user;
    final profile = ref.watch(profileControllerProvider);
    final totalStars = profile.stars;
    final totalXp = profile.totalXp;
    final currentStreak = profile.currentStreak;
    final userLevel = profile.level;
    final userMoniker = profile.moniker;

    final username = authState.username ??
        (user?.userMetadata?['username'] as String?) ??
        'Spartan Initiate';

    // Resolve cosmetic items from metadata or defaults
    final frameKey =
        (user?.userMetadata?['avatar_frame'] as String?) ?? 'frame_stoic_laurel';
    final bannerKey =
        (user?.userMetadata?['banner_url'] as String?) ?? 'banner_deep_space';
    final effectKey =
        (user?.userMetadata?['name_effect'] as String?) ?? 'effect_clean_white';

    final activeFrame = CosmeticsCatalog.getById(frameKey) ??
        CosmeticsCatalog.avatarFrames.first;
    final activeBanner = CosmeticsCatalog.getById(bannerKey) ??
        CosmeticsCatalog.banners.first;
    final activeEffect = CosmeticsCatalog.getById(effectKey) ??
        CosmeticsCatalog.nameEffects.first;

    return Scaffold(
      backgroundColor: AppColors.background,
      body: SafeArea(
        child: RefreshIndicator(
          onRefresh: _loadFriends,
          color: AppColors.primaryAccent,
          backgroundColor: AppColors.surface,
          child: ListView(
            padding: const EdgeInsets.only(bottom: 90),
            children: [
              // Discord-Style Profile Header Card
              _buildDiscordProfileHeader(
                username: username,
                banner: activeBanner,
                frame: activeFrame,
                effect: activeEffect,
                level: userLevel,
                moniker: userMoniker,
                customAvatarUrl: profile.customAvatarUrl,
                customBannerUrl: profile.customBannerUrl,
              ),

              const SizedBox(height: 16),

              // Stats Row: Streak, Star Vault, Total XP
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16.0),
                child: _buildStatsRow(
                  streak: currentStreak,
                  totalStars: totalStars,
                  totalXp: totalXp,
                ),
              ),

              const SizedBox(height: 20),

              // Customize & Battlepass Shop CTA Banner
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16.0),
                child: _buildCosmeticsBanner(context),
              ),

              const SizedBox(height: 24),

              // Friends Cohort Section
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16.0),
                child: _buildFriendsSection(),
              ),

              const SizedBox(height: 24),

              // Account Settings & Sign Out
              Padding(
                padding: const EdgeInsets.symmetric(horizontal: 16.0),
                child: _buildSettingsSection(authState),
              ),
            ],
          ),
        ),
      ),
    );
  }

  // Discord-style layered profile header
  Widget _buildDiscordProfileHeader({
    required String username,
    required CosmeticItem banner,
    required CosmeticItem frame,
    required CosmeticItem effect,
    required int level,
    required String moniker,
    String? customAvatarUrl,
    String? customBannerUrl,
  }) {
    final hasCustomBanner = customBannerUrl != null && customBannerUrl.isNotEmpty;
    final hasCustomAvatar = customAvatarUrl != null && customAvatarUrl.isNotEmpty;

    return Container(
      margin: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: AppColors.border),
      ),
      clipBehavior: Clip.antiAlias,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Banner Rectangle (Height: 140)
          Stack(
            clipBehavior: Clip.none,
            children: [
              Container(
                height: 140,
                width: double.infinity,
                decoration: BoxDecoration(
                  gradient: hasCustomBanner
                      ? null
                      : LinearGradient(
                          colors: [
                            banner.primaryColor,
                            banner.secondaryColor ?? banner.primaryColor.withAlpha(120),
                            AppColors.surface,
                          ],
                          begin: Alignment.topLeft,
                          end: Alignment.bottomRight,
                        ),
                  image: hasCustomBanner
                      ? DecorationImage(
                          image: NetworkImage(customBannerUrl),
                          fit: BoxFit.cover,
                        )
                      : null,
                ),
                child: hasCustomBanner
                    ? null
                    : Center(
                        child: Icon(
                          banner.icon,
                          size: 64,
                          color: Colors.white.withAlpha(25),
                        ),
                      ),
              ),

              // Settings Gear Icon (Top Right)
              Positioned(
                top: 8,
                right: 8,
                child: Container(
                  decoration: BoxDecoration(
                    color: Colors.black.withAlpha(120),
                    shape: BoxShape.circle,
                  ),
                  child: IconButton(
                    tooltip: 'Settings & Open Source',
                    icon: const Icon(Icons.settings_rounded, color: Colors.white, size: 20),
                    onPressed: () => SettingsScreen.show(context),
                  ),
                ),
              ),

              // Overlapping Avatar with Discord-style frame
              Positioned(
                bottom: -36,
                left: 18,
                child: Container(
                  width: 82,
                  height: 82,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    border: Border.all(
                      color: frame.primaryColor,
                      width: 3.5,
                    ),
                    boxShadow: [
                      BoxShadow(
                        color: frame.primaryColor.withAlpha(90),
                        blurRadius: 14,
                        spreadRadius: 2,
                      ),
                    ],
                  ),
                  child: CircleAvatar(
                    backgroundColor: AppColors.surfaceElevated,
                    backgroundImage: hasCustomAvatar ? NetworkImage(customAvatarUrl) : null,
                    child: hasCustomAvatar
                        ? null
                        : Text(
                            username.isNotEmpty ? username[0].toUpperCase() : 'S',
                            style: AppTypography.h1.copyWith(
                              fontSize: 32,
                              color: frame.primaryColor,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                  ),
                ),
              ),

              // Level Moniker Pill
              Positioned(
                right: 14,
                bottom: 12,
                child: Container(
                  padding:
                      const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                  decoration: BoxDecoration(
                    color: Colors.black.withAlpha(160),
                    borderRadius: BorderRadius.circular(8),
                    border: Border.all(color: AppColors.border),
                  ),
                  child: Text(
                    'LVL $level · $moniker',
                    style: AppTypography.accentMono.copyWith(
                      fontSize: 11,
                      color: Colors.amber,
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                ),
              ),
            ],
          ),

          const SizedBox(height: 44),

          // User Handles & Name Effect Shimmer
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 18.0),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    _buildNameWithEffect(username, effect),
                    const SizedBox(width: 8),
                    Container(
                      padding: const EdgeInsets.symmetric(
                          horizontal: 6, vertical: 2),
                      decoration: BoxDecoration(
                        color: AppColors.primaryAccent.withAlpha(30),
                        borderRadius: BorderRadius.circular(6),
                      ),
                      child: Text(
                        'YOU',
                        style: AppTypography.accentMono.copyWith(
                          fontSize: 10,
                          color: AppColors.primaryAccent,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 2),
                Text(
                  '@${username.toLowerCase().replaceAll(' ', '_')}',
                  style: AppTypography.bodySmall.copyWith(
                    color: AppColors.textSecondary,
                  ),
                ),
                const SizedBox(height: 10),
                Text(
                  '“You could be good today, but instead you choose tomorrow.”',
                  style: AppTypography.bodySmall.copyWith(
                    fontStyle: FontStyle.italic,
                    color: AppColors.textPrimary.withAlpha(220),
                  ),
                ),
                const SizedBox(height: 16),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildNameWithEffect(String username, CosmeticItem effect) {
    if (effect.secondaryColor != null) {
      return ShaderMask(
        shaderCallback: (bounds) => LinearGradient(
          colors: [effect.primaryColor, effect.secondaryColor!],
        ).createShader(bounds),
        child: Text(
          username,
          style: AppTypography.h1.copyWith(
            color: Colors.white,
            fontWeight: FontWeight.bold,
          ),
        ),
      );
    }

    return Text(
      username,
      style: AppTypography.h1.copyWith(
        color: effect.primaryColor,
        fontWeight: FontWeight.bold,
        shadows: effect.primaryColor != Colors.white
            ? [
                Shadow(
                  color: effect.primaryColor.withAlpha(160),
                  blurRadius: 10,
                )
              ]
            : null,
      ),
    );
  }

  Widget _buildStatsRow({
    required int streak,
    required int totalStars,
    required int totalXp,
  }) {
    return Row(
      children: [
        Expanded(
          child: _buildStatCard(
            title: 'Current Streak',
            value: '$streak ${streak == 1 ? "Day" : "Days"}',
            icon: Icons.local_fire_department_rounded,
            iconColor: streak > 0 ? Colors.deepOrangeAccent : AppColors.textSecondary,
          ),
        ),
        const SizedBox(width: 10),
        Expanded(
          child: _buildStatCard(
            title: 'Star Vault',
            value: '★ $totalStars',
            icon: Icons.star_rounded,
            iconColor: Colors.amber,
          ),
        ),
        const SizedBox(width: 10),
        Expanded(
          child: _buildStatCard(
            title: 'Total XP',
            value: '$totalXp XP',
            icon: Icons.bolt_rounded,
            iconColor: AppColors.primaryAccent,
          ),
        ),
      ],
    );
  }

  Widget _buildStatCard({
    required String title,
    required String value,
    required IconData icon,
    required Color iconColor,
  }) {
    return Container(
      padding: const EdgeInsets.symmetric(vertical: 14, horizontal: 10),
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: AppColors.border),
      ),
      child: Column(
        children: [
          Icon(icon, color: iconColor, size: 22),
          const SizedBox(height: 6),
          Text(
            value,
            style: AppTypography.accentMono.copyWith(
              fontSize: 13,
              fontWeight: FontWeight.bold,
            ),
          ),
          const SizedBox(height: 2),
          Text(
            title,
            style: AppTypography.bodySmall.copyWith(
              fontSize: 10,
              color: AppColors.textSecondary,
            ),
            textAlign: TextAlign.center,
          ),
        ],
      ),
    );
  }

  Widget _buildCosmeticsBanner(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        gradient: LinearGradient(
          colors: [
            AppColors.surfaceElevated,
            AppColors.surface,
          ],
        ),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(
          color: AppColors.primaryAccent.withAlpha(80),
        ),
      ),
      child: Row(
        children: [
          Container(
            padding: const EdgeInsets.all(10),
            decoration: BoxDecoration(
              color: AppColors.primaryAccent.withAlpha(35),
              borderRadius: BorderRadius.circular(12),
            ),
            child: const Icon(
              Icons.military_tech_rounded,
              color: AppColors.primaryAccent,
              size: 26,
            ),
          ),
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('Battlepass & Cosmetics', style: AppTypography.h2),
                const SizedBox(height: 2),
                Text(
                  'Equip frames, banners & neon text effects.',
                  style: AppTypography.bodySmall,
                ),
              ],
            ),
          ),
          ElevatedButton(
            onPressed: () => BattlepassShopScreen.show(context),
            style: ElevatedButton.styleFrom(
              backgroundColor: AppColors.primaryAccent,
              foregroundColor: AppColors.background,
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(8),
              ),
            ),
            child: const Text('Shop', style: TextStyle(fontWeight: FontWeight.bold)),
          ),
        ],
      ),
    );
  }

  Widget _buildFriendsSection() {
    final currentUserId = ref.read(supabaseServiceProvider).currentUser?.id;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        // Pending Friend Requests Banner / List
        if (_pendingRequests.isNotEmpty) ...[
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text('INCOMING FRIEND REQUESTS',
                  style: AppTypography.bodySmall
                      .copyWith(color: AppColors.primaryAccent)),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                decoration: BoxDecoration(
                  color: AppColors.primaryAccent.withAlpha(30),
                  borderRadius: BorderRadius.circular(6),
                ),
                child: Text(
                  '${_pendingRequests.length} pending',
                  style: AppTypography.accentMono.copyWith(
                    fontSize: 10,
                    color: AppColors.primaryAccent,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),
          ..._pendingRequests.map((req) {
            final reqId = req['id'] as String;
            final profile = req['profiles'] as Map<String, dynamic>? ?? {};
            final reqName = profile['username'] as String? ?? 'Spartan';
            final reqStreak = profile['streak_days'] as int? ?? 1;

            return Container(
              margin: const EdgeInsets.only(bottom: 8),
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: AppColors.surface,
                borderRadius: BorderRadius.circular(12),
                border: Border.all(
                    color: AppColors.primaryAccent.withAlpha(100), width: 1.2),
              ),
              child: Row(
                children: [
                  CircleAvatar(
                    radius: 16,
                    backgroundColor: AppColors.surfaceElevated,
                    child: Text(
                      reqName.isNotEmpty ? reqName[0].toUpperCase() : '?',
                      style: const TextStyle(
                        fontWeight: FontWeight.bold,
                        color: AppColors.primaryAccent,
                      ),
                    ),
                  ),
                  const SizedBox(width: 10),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text('@$reqName',
                            style: AppTypography.bodyMedium
                                .copyWith(fontWeight: FontWeight.bold)),
                        Text('$reqStreak-day streak',
                            style: AppTypography.bodySmall.copyWith(fontSize: 10)),
                      ],
                    ),
                  ),
                  TextButton(
                    onPressed: () => _respondToRequest(reqId, false),
                    style: TextButton.styleFrom(
                      foregroundColor: AppColors.textSecondary,
                      padding: const EdgeInsets.symmetric(
                          horizontal: 8, vertical: 4),
                      minimumSize: Size.zero,
                      tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                    ),
                    child: const Text('Decline', style: TextStyle(fontSize: 11)),
                  ),
                  const SizedBox(width: 6),
                  ElevatedButton(
                    onPressed: () => _respondToRequest(reqId, true),
                    style: ElevatedButton.styleFrom(
                      backgroundColor: AppColors.primaryAccent,
                      foregroundColor: AppColors.background,
                      padding: const EdgeInsets.symmetric(
                          horizontal: 10, vertical: 4),
                      minimumSize: Size.zero,
                      tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                    ),
                    child: const Text('Accept',
                        style: TextStyle(
                            fontSize: 11, fontWeight: FontWeight.bold)),
                  ),
                ],
              ),
            );
          }),
          const SizedBox(height: 16),
        ],

        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Text('COHORT SPARTANS & FRIENDS', style: AppTypography.bodySmall),
            TextButton.icon(
              onPressed: _showAddFriendDialog,
              icon: const Icon(Icons.person_add_rounded, size: 14),
              label: const Text('Add Friend',
                  style: TextStyle(fontSize: 11, fontWeight: FontWeight.bold)),
              style: TextButton.styleFrom(
                foregroundColor: AppColors.primaryAccent,
                padding: EdgeInsets.zero,
                minimumSize: Size.zero,
                tapTargetSize: MaterialTapTargetSize.shrinkWrap,
              ),
            ),
          ],
        ),
        const SizedBox(height: 12),

        if (_isLoadingFriends)
          const Center(
            child: Padding(
              padding: EdgeInsets.all(16.0),
              child: CircularProgressIndicator(color: AppColors.primaryAccent),
            ),
          )
        else if (_friends.isEmpty)
          Container(
            padding: const EdgeInsets.symmetric(vertical: 24, horizontal: 16),
            decoration: BoxDecoration(
              color: AppColors.surface,
              borderRadius: BorderRadius.circular(14),
              border: Border.all(color: AppColors.border),
            ),
            child: Center(
              child: Column(
                children: [
                  Icon(Icons.group_outlined,
                      size: 36, color: AppColors.textSecondary.withAlpha(120)),
                  const SizedBox(height: 8),
                  Text(
                    'No Spartans in your cohort yet.',
                    style: AppTypography.bodySmall.copyWith(
                      color: AppColors.textSecondary,
                    ),
                  ),
                  const SizedBox(height: 10),
                  OutlinedButton.icon(
                    onPressed: _showAddFriendDialog,
                    icon: const Icon(Icons.person_add_rounded, size: 14),
                    label: const Text('Add Your First Friend',
                        style: TextStyle(fontSize: 11)),
                    style: OutlinedButton.styleFrom(
                      side: const BorderSide(color: AppColors.border),
                      foregroundColor: AppColors.primaryAccent,
                    ),
                  ),
                ],
              ),
            ),
          )
        else
          ..._friends.map((f) {
            final profile = f['profiles'] as Map<String, dynamic>? ?? {};
            final friendName = profile['username'] as String? ?? 'Spartan';
            final friendXp = profile['total_xp'] as int? ?? 0;
            final friendStreak = profile['streak_days'] as int? ?? 1;

            final targetFriendId = (profile['id'] as String?) ??
                (f['friend_id'] == currentUserId
                    ? (f['user_id'] as String? ?? '')
                    : (f['friend_id'] as String? ?? ''));

            return Container(
              margin: const EdgeInsets.only(bottom: 8),
              padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
              decoration: BoxDecoration(
                color: AppColors.surface,
                borderRadius: BorderRadius.circular(12),
                border: Border.all(color: AppColors.border),
              ),
              child: Row(
                children: [
                  CircleAvatar(
                    radius: 18,
                    backgroundColor: AppColors.surfaceElevated,
                    child: Text(
                      friendName.isNotEmpty ? friendName[0].toUpperCase() : '?',
                      style: const TextStyle(
                        fontWeight: FontWeight.bold,
                        color: AppColors.primaryAccent,
                      ),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          '@$friendName',
                          style: AppTypography.bodyMedium.copyWith(
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                        Row(
                          children: [
                            const Icon(
                              Icons.local_fire_department_rounded,
                              size: 13,
                              color: Colors.deepOrangeAccent,
                            ),
                            const SizedBox(width: 3),
                            Text(
                              '$friendStreak-day streak',
                              style:
                                  AppTypography.bodySmall.copyWith(fontSize: 11),
                            ),
                          ],
                        ),
                      ],
                    ),
                  ),
                  Column(
                    crossAxisAlignment: CrossAxisAlignment.end,
                    children: [
                      Text(
                        '$friendXp XP',
                        style: AppTypography.accentMono.copyWith(fontSize: 12),
                      ),
                      const SizedBox(height: 4),
                      if (targetFriendId.isNotEmpty)
                        OutlinedButton.icon(
                          onPressed: () {
                            DuelChallengeDialog.show(
                              context,
                              friendId: targetFriendId,
                              friendName: friendName,
                            );
                          },
                          icon: const Icon(Icons.military_tech_rounded, size: 12),
                          label: const Text('Duel ⚔️',
                              style: TextStyle(
                                  fontSize: 10, fontWeight: FontWeight.bold)),
                          style: OutlinedButton.styleFrom(
                            side: const BorderSide(
                                color: AppColors.primaryAccent),
                            foregroundColor: AppColors.primaryAccent,
                            padding: const EdgeInsets.symmetric(
                                horizontal: 8, vertical: 2),
                            minimumSize: Size.zero,
                            tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                          ),
                        ),
                    ],
                  ),
                ],
              ),
            );
          }),
      ],
    );
  }

  Widget _buildSettingsSection(OrganizaAuthState authState) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text('ACCOUNT & INTEGRATIONS', style: AppTypography.bodySmall),
        const SizedBox(height: 12),
        Container(
          decoration: BoxDecoration(
            color: AppColors.surface,
            borderRadius: BorderRadius.circular(14),
            border: Border.all(color: AppColors.border),
          ),
          child: Column(
            children: [
              ListTile(
                leading: const Icon(Icons.cloud_done_rounded,
                    color: AppColors.successGreen),
                title: Text('Supabase Cloud Sync', style: AppTypography.bodyMedium),
                subtitle: Text(
                  authState.isGuest
                      ? 'Offline Guest Mode (Local SQLite)'
                      : 'Active • Connected',
                  style: AppTypography.bodySmall.copyWith(fontSize: 11),
                ),
              ),
              const Divider(color: AppColors.border, height: 1),
              ListTile(
                leading: const Icon(Icons.dns_rounded,
                    color: AppColors.primaryAccent),
                title: Text('Server Connection', style: AppTypography.bodyMedium),
                subtitle: FutureBuilder<String>(
                  future: SharedPreferences.getInstance().then(
                    (p) => SupabaseConfig.hasCustomServer(p)
                        ? (SupabaseConfig.getSavedCustomUrl(p) ?? 'Custom Server')
                        : 'Default Cloud',
                  ),
                  builder: (context, snapshot) => Text(
                    snapshot.data ?? 'Default Cloud',
                    style: AppTypography.bodySmall.copyWith(fontSize: 11),
                  ),
                ),
                trailing: const Icon(Icons.chevron_right_rounded,
                    color: AppColors.textSecondary),
                onTap: () async {
                  await CustomServerDialog.show(context);
                  if (mounted) setState(() {});
                },
              ),
              const Divider(color: AppColors.border, height: 1),
              ListTile(
                leading: const Icon(Icons.settings_suggest_rounded,
                    color: AppColors.primaryAccent),
                title: Text('Settings, Groq & Legal', style: AppTypography.bodyMedium),
                subtitle: Text(
                  'Display name, AI engine key, MIT open source & legal terms',
                  style: AppTypography.bodySmall.copyWith(fontSize: 11),
                ),
                trailing: const Icon(Icons.chevron_right_rounded,
                    color: AppColors.textSecondary),
                onTap: () async {
                  await SettingsScreen.show(context);
                  if (mounted) setState(() {});
                },
              ),
              const Divider(color: AppColors.border, height: 1),
              ListTile(
                leading:
                    const Icon(Icons.logout_rounded, color: AppColors.errorRed),
                title: Text(
                  'Sign Out of Organiza',
                  style: AppTypography.bodyMedium.copyWith(
                    color: AppColors.errorRed,
                    fontWeight: FontWeight.w600,
                  ),
                ),
                onTap: () async {
                  await ref.read(authControllerProvider.notifier).logout();
                },
              ),
            ],
          ),
        ),
      ],
    );
  }
}
