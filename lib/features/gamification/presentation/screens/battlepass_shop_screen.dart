import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

import '../../../../core/constants/app_colors.dart';
import '../../../../core/constants/app_typography.dart';
import '../../../../core/network/supabase_service.dart';
import '../../../profile/presentation/controllers/profile_controller.dart';
import '../../../today/presentation/controllers/today_tasks_controller.dart';
import '../../domain/cosmetics_catalog.dart';

class BattlepassTier {
  final int tierNumber;
  final int requiredLevel;
  final int starCost;
  final String title;
  final String subtitle;
  final IconData icon;
  final Color accentColor;
  final bool isFree;

  const BattlepassTier({
    required this.tierNumber,
    required this.requiredLevel,
    required this.starCost,
    required this.title,
    required this.subtitle,
    required this.icon,
    required this.accentColor,
    this.isFree = false,
  });
}

class BattlepassShopScreen extends ConsumerStatefulWidget {
  const BattlepassShopScreen({super.key});

  static Future<void> show(BuildContext context) {
    return Navigator.of(context).push(
      MaterialPageRoute(
        builder: (context) => const BattlepassShopScreen(),
      ),
    );
  }

  @override
  ConsumerState<BattlepassShopScreen> createState() =>
      _BattlepassShopScreenState();
}

class _BattlepassShopScreenState extends ConsumerState<BattlepassShopScreen> {
  int get _userLevel => ref.watch(profileControllerProvider).level;
  int get _currentXp => ref.watch(profileControllerProvider).xpInCurrentLevel;
  int get _nextTierXp => ref.watch(profileControllerProvider).xpNeededForNextLevel;
  String get _userMoniker => ref.watch(profileControllerProvider).moniker;

  final Set<String> _ownedTierIds = {'tier_1', 'tier_2', 'tier_3'};
  String? _equippedTierId = 'tier_3';

  final Set<String> _ownedCosmeticIds = {
    'frame_stoic_laurel',
    'banner_deep_space',
    'effect_clean_white',
  };

  String? _equippedFrame = 'frame_stoic_laurel';
  String? _equippedBanner = 'banner_deep_space';
  String? _equippedEffect = 'effect_clean_white';

  static const List<BattlepassTier> _tiers = [
    BattlepassTier(
      tierNumber: 1,
      requiredLevel: 1,
      starCost: 0,
      title: 'Spartan Initiate Crest',
      subtitle: 'Awarded for taking your first oath of discipline',
      icon: Icons.shield_rounded,
      accentColor: AppColors.primaryAccent,
      isFree: true,
    ),
    BattlepassTier(
      tierNumber: 2,
      requiredLevel: 2,
      starCost: 50,
      title: 'Gamma Focus Soundscape',
      subtitle: '40Hz binaural waves for rapid flow induction',
      icon: Icons.graphic_eq_rounded,
      accentColor: Color(0xFF10B981),
    ),
    BattlepassTier(
      tierNumber: 3,
      requiredLevel: 4,
      starCost: 100,
      title: 'Midnight Obsidian Canvas',
      subtitle: 'Pure OLED black minimalism distraction shield',
      icon: Icons.dark_mode_rounded,
      accentColor: Color(0xFF8B5CF6),
    ),
    BattlepassTier(
      tierNumber: 4,
      requiredLevel: 6,
      starCost: 150,
      title: 'Marcus Aurelius Bust',
      subtitle: 'Meditations profile emblem and gilded border',
      icon: Icons.account_balance_rounded,
      accentColor: Color(0xFF3B82F6),
    ),
    BattlepassTier(
      tierNumber: 5,
      requiredLevel: 8,
      starCost: 250,
      title: 'Gilded Spartan Cuirass',
      subtitle: 'Armor plate badge for maintaining 20-day streak',
      icon: Icons.security_rounded,
      accentColor: Colors.amber,
    ),
    BattlepassTier(
      tierNumber: 6,
      requiredLevel: 10,
      starCost: 350,
      title: 'Seneca Epistle Recordings',
      subtitle: 'Daily spoken letters on time and inner peace',
      icon: Icons.auto_stories_rounded,
      accentColor: Color(0xFFF97316),
    ),
    BattlepassTier(
      tierNumber: 7,
      requiredLevel: 12,
      starCost: 500,
      title: 'Chariot of Citium Aura',
      subtitle: 'Animated leaderboard card frame with ember sparks',
      icon: Icons.local_fire_department_rounded,
      accentColor: Color(0xFFEC4899),
    ),
    BattlepassTier(
      tierNumber: 8,
      requiredLevel: 14,
      starCost: 750,
      title: 'Emperor\'s Golden Laurel',
      subtitle: 'Legendary laurel crown for cohort champions',
      icon: Icons.military_tech_rounded,
      accentColor: Color(0xFFFFD700),
    ),
    BattlepassTier(
      tierNumber: 9,
      requiredLevel: 16,
      starCost: 1000,
      title: 'Stoic Fortress Theme',
      subtitle: 'Complete marble and bronze visual environment',
      icon: Icons.castle_rounded,
      accentColor: Color(0xFF06B6D4),
    ),
    BattlepassTier(
      tierNumber: 10,
      requiredLevel: 20,
      starCost: 1500,
      title: 'Apex Sovereign Title',
      subtitle: 'Global prestige moniker across all leaderboards',
      icon: Icons.workspace_premium_rounded,
      accentColor: Color(0xFFA855F7),
    ),
  ];

  @override
  void initState() {
    super.initState();
    _loadState();
  }

  Future<void> _loadState() async {
    final prefs = await SharedPreferences.getInstance();
    final ownedTiers = prefs.getStringList('battlepass_owned_tiers') ??
        ['tier_1', 'tier_2', 'tier_3'];
    final equippedTier =
        prefs.getString('battlepass_equipped_tier') ?? 'tier_3';

    final ownedCosmetics = prefs.getStringList('owned_cosmetic_ids') ??
        ['frame_stoic_laurel', 'banner_deep_space', 'effect_clean_white'];
    final frame =
        prefs.getString('equipped_avatar_frame') ?? 'frame_stoic_laurel';
    final banner =
        prefs.getString('equipped_banner_url') ?? 'banner_deep_space';
    final effect =
        prefs.getString('equipped_name_effect') ?? 'effect_clean_white';

    if (mounted) {
      setState(() {
        _ownedTierIds.clear();
        _ownedTierIds.addAll(ownedTiers);
        _equippedTierId = equippedTier;

        _ownedCosmeticIds.clear();
        _ownedCosmeticIds.addAll(ownedCosmetics);
        _equippedFrame = frame;
        _equippedBanner = banner;
        _equippedEffect = effect;
      });
    }
  }

  Future<void> _claimOrEquipTier(BattlepassTier tier, int totalStars) async {
    final tierId = 'tier_${tier.tierNumber}';
    final isOwned = _ownedTierIds.contains(tierId);
    final isLevelMet = _userLevel >= tier.requiredLevel;

    if (isOwned) {
      setState(() {
        _equippedTierId = (_equippedTierId == tierId) ? null : tierId;
      });
      final prefs = await SharedPreferences.getInstance();
      if (_equippedTierId != null) {
        await prefs.setString('battlepass_equipped_tier', _equippedTierId!);
      }
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              _equippedTierId == tierId
                  ? 'Equipped ${tier.title}!'
                  : 'Unequipped ${tier.title}.',
            ),
            duration: const Duration(seconds: 1),
            backgroundColor: AppColors.surfaceElevated,
          ),
        );
      }
      return;
    }

    if (!isLevelMet) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            'Locked! Requires Player Level ${tier.requiredLevel}. (Current: Lvl $_userLevel)',
          ),
          backgroundColor: AppColors.errorRed,
        ),
      );
      return;
    }

    if (totalStars < tier.starCost) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            'Need ${tier.starCost - totalStars} more Stars! Complete daily habits to earn Stars.',
          ),
          backgroundColor: AppColors.errorRed,
        ),
      );
      return;
    }

    final supabase = ref.read(supabaseServiceProvider);
    if (supabase.isInitialized && supabase.currentUser != null) {
      final rpcResult = await supabase.purchaseCosmeticWithRpc(tierId);
      if (rpcResult['success'] == false && rpcResult['error'] != null) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text(
                  'Server purchase validation failed: ${rpcResult['error']}'),
              backgroundColor: AppColors.errorRed,
            ),
          );
        }
        return;
      }
    }

    setState(() {
      _ownedTierIds.add(tierId);
      _equippedTierId = tierId;
    });

    final prefs = await SharedPreferences.getInstance();
    await prefs.setStringList('battlepass_owned_tiers', _ownedTierIds.toList());
    await prefs.setString('battlepass_equipped_tier', tierId);

    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('Claimed Tier ${tier.tierNumber}: ${tier.title}! ⭐'),
          backgroundColor: AppColors.successGreen,
        ),
      );
    }
  }

  Future<void> _claimOrEquipCosmetic(
      CosmeticItem item, int totalStars) async {
    final isOwned = _ownedCosmeticIds.contains(item.id);
    final isLevelMet = _userLevel >= item.requiredLevel;

    if (isOwned) {
      setState(() {
        if (item.type == CosmeticType.avatarFrame) {
          _equippedFrame = item.id;
        } else if (item.type == CosmeticType.profileBanner) {
          _equippedBanner = item.id;
        } else if (item.type == CosmeticType.nameEffect) {
          _equippedEffect = item.id;
        }
      });

      final prefs = await SharedPreferences.getInstance();
      if (item.type == CosmeticType.avatarFrame) {
        await prefs.setString('equipped_avatar_frame', item.id);
      } else if (item.type == CosmeticType.profileBanner) {
        await prefs.setString('equipped_banner_url', item.id);
      } else if (item.type == CosmeticType.nameEffect) {
        await prefs.setString('equipped_name_effect', item.id);
      }

      await ref.read(supabaseServiceProvider).updateEquippedCosmetics(
            avatarFrame: _equippedFrame,
            bannerUrl: _equippedBanner,
            nameEffect: _equippedEffect,
          );

      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Equipped ${item.name}!'),
            duration: const Duration(seconds: 1),
            backgroundColor: AppColors.surfaceElevated,
          ),
        );
      }
      return;
    }

    if (!isLevelMet) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            'Locked! Requires Player Level ${item.requiredLevel}. (Current: Lvl $_userLevel)',
          ),
          backgroundColor: AppColors.errorRed,
        ),
      );
      return;
    }

    if (totalStars < item.costStars) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(
            'Need ${item.costStars - totalStars} more Stars! Complete daily habits to earn Stars.',
          ),
          backgroundColor: AppColors.errorRed,
        ),
      );
      return;
    }

    final supabase = ref.read(supabaseServiceProvider);
    if (supabase.isInitialized && supabase.currentUser != null) {
      final rpcResult = await supabase.purchaseCosmeticWithRpc(item.id);
      if (rpcResult['success'] == false && rpcResult['error'] != null) {
        if (mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text(
                  'Server purchase validation failed: ${rpcResult['error']}'),
              backgroundColor: AppColors.errorRed,
            ),
          );
        }
        return;
      }
    }

    setState(() {
      _ownedCosmeticIds.add(item.id);
      if (item.type == CosmeticType.avatarFrame) {
        _equippedFrame = item.id;
      } else if (item.type == CosmeticType.profileBanner) {
        _equippedBanner = item.id;
      } else if (item.type == CosmeticType.nameEffect) {
        _equippedEffect = item.id;
      }
    });

    final prefs = await SharedPreferences.getInstance();
    await prefs.setStringList(
        'owned_cosmetic_ids', _ownedCosmeticIds.toList());
    if (item.type == CosmeticType.avatarFrame) {
      await prefs.setString('equipped_avatar_frame', item.id);
    } else if (item.type == CosmeticType.profileBanner) {
      await prefs.setString('equipped_banner_url', item.id);
    } else if (item.type == CosmeticType.nameEffect) {
      await prefs.setString('equipped_name_effect', item.id);
    }

    await ref.read(supabaseServiceProvider).updateEquippedCosmetics(
          avatarFrame: _equippedFrame,
          bannerUrl: _equippedBanner,
          nameEffect: _equippedEffect,
        );

    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('Unlocked & Equipped ${item.name}! ⭐'),
          backgroundColor: AppColors.successGreen,
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final totalStarsAsync = ref.watch(totalStarsStreamProvider);
    final totalStars = totalStarsAsync.valueOrNull ?? 1600;

    return Scaffold(
      backgroundColor: AppColors.background,
      appBar: AppBar(
        backgroundColor: AppColors.background,
        elevation: 0,
        scrolledUnderElevation: 0,
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Battlepass & Star Shop', style: AppTypography.h1),
            const SizedBox(height: 2),
            Text(
              'Season 1 · The Discipline Initiative',
              style: AppTypography.bodySmall,
            ),
          ],
        ),
        actions: [
          Container(
            margin: const EdgeInsets.only(right: 16),
            padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
            decoration: BoxDecoration(
              color: Colors.amber.withAlpha(25),
              borderRadius: BorderRadius.circular(16),
              border: Border.all(color: Colors.amber.withAlpha(80)),
            ),
            child: Row(
              children: [
                const Icon(Icons.star_rounded, size: 18, color: Colors.amber),
                const SizedBox(width: 4),
                Text(
                  '★ $totalStars',
                  style: AppTypography.accentMono.copyWith(
                    fontSize: 14,
                    color: Colors.amber,
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.fromLTRB(16, 12, 16, 80),
          children: [
            // Top Banner: Season 1 & XP Progress
            _buildAngularHeaderBadge(),
            const SizedBox(height: 24),

            // Section 1: Battlepass Tier Track
            _buildSectionHeader(
              title: 'BATTLEPASS TIERS (SEASON 1)',
              subtitle: 'Scroll ↔ to unlock Spartan progression rewards',
            ),
            const SizedBox(height: 12),
            _buildHorizontalTierTrack(totalStars),
            const SizedBox(height: 28),

            // Section 2: Avatar Frames Grid
            _buildSectionHeader(
              title: 'AVATAR FRAMES',
              subtitle: 'Adorn your profile portrait across leaderboards',
            ),
            const SizedBox(height: 12),
            _buildAvatarFramesGrid(totalStars),
            const SizedBox(height: 28),

            // Section 3: Profile Banners Carousel
            _buildSectionHeader(
              title: 'PROFILE BANNERS',
              subtitle: 'Exclusive visual backgrounds for your profile header',
            ),
            const SizedBox(height: 12),
            _buildBannersCarousel(totalStars),
            const SizedBox(height: 28),

            // Section 4: Name Glow Shaders
            _buildSectionHeader(
              title: 'NAME GLOW SHADERS',
              subtitle: 'Pulsing chromatic aura applied to your moniker',
            ),
            const SizedBox(height: 12),
            _buildNameEffectsSection(totalStars),
          ],
        ),
      ),
    );
  }

  Widget _buildSectionHeader({
    required String title,
    required String subtitle,
  }) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(title, style: AppTypography.bodySmall),
        const SizedBox(height: 2),
        Text(
          subtitle,
          style: AppTypography.bodySmall.copyWith(
            fontSize: 11,
            color: AppColors.textSecondary,
          ),
        ),
      ],
    );
  }

  Widget _buildAvatarFramesGrid(int totalStars) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final crossAxisCount = constraints.maxWidth > 600 ? 3 : 2;
        return GridView.builder(
          shrinkWrap: true,
          physics: const NeverScrollableScrollPhysics(),
          gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
            crossAxisCount: crossAxisCount,
            crossAxisSpacing: 12,
            mainAxisSpacing: 12,
            childAspectRatio: 0.78,
          ),
          itemCount: CosmeticsCatalog.avatarFrames.length,
          itemBuilder: (context, index) {
            final item = CosmeticsCatalog.avatarFrames[index];
            final isOwned = _ownedCosmeticIds.contains(item.id);
            final isEquipped = _equippedFrame == item.id;
            final isLevelMet = _userLevel >= item.requiredLevel;

            return Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: AppColors.surface,
                borderRadius: BorderRadius.circular(16),
                border: Border.all(
                  color: isEquipped
                      ? AppColors.primaryAccent
                      : (isOwned
                          ? AppColors.successGreen.withAlpha(90)
                          : AppColors.border),
                  width: isEquipped ? 2 : 1,
                ),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  Expanded(
                    child: Container(
                      decoration: BoxDecoration(
                        color: item.primaryColor.withAlpha(20),
                        borderRadius: BorderRadius.circular(12),
                      ),
                      child: Center(
                        child: Container(
                          width: 58,
                          height: 58,
                          decoration: BoxDecoration(
                            shape: BoxShape.circle,
                            border: Border.all(
                              color: item.primaryColor,
                              width: 3,
                            ),
                            boxShadow: [
                              BoxShadow(
                                color: item.primaryColor.withAlpha(60),
                                blurRadius: 10,
                              ),
                            ],
                          ),
                          child: CircleAvatar(
                            backgroundColor: AppColors.surfaceElevated,
                            child: Icon(
                              Icons.person_rounded,
                              color: item.primaryColor,
                              size: 30,
                            ),
                          ),
                        ),
                      ),
                    ),
                  ),
                  const SizedBox(height: 8),
                  Text(
                    item.name,
                    style: AppTypography.bodySmall.copyWith(
                      fontWeight: FontWeight.bold,
                      color: AppColors.textPrimary,
                    ),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                  ),
                  const SizedBox(height: 2),
                  Text(
                    item.description,
                    style: AppTypography.bodySmall.copyWith(
                      fontSize: 10,
                      color: AppColors.textSecondary,
                    ),
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                  ),
                  const SizedBox(height: 8),
                  _buildEquipButton(
                    item: item,
                    isEquipped: isEquipped,
                    isOwned: isOwned,
                    isLevelMet: isLevelMet,
                    totalStars: totalStars,
                  ),
                ],
              ),
            );
          },
        );
      },
    );
  }

  Widget _buildBannersCarousel(int totalStars) {
    return SizedBox(
      height: 155,
      child: ListView.builder(
        scrollDirection: Axis.horizontal,
        itemCount: CosmeticsCatalog.banners.length,
        itemBuilder: (context, index) {
          final item = CosmeticsCatalog.banners[index];
          final isOwned = _ownedCosmeticIds.contains(item.id);
          final isEquipped = _equippedBanner == item.id;
          final isLevelMet = _userLevel >= item.requiredLevel;

          return Container(
            width: 250,
            margin: const EdgeInsets.only(right: 14),
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(16),
              border: Border.all(
                color: isEquipped
                    ? AppColors.primaryAccent
                    : (isOwned
                        ? AppColors.successGreen.withAlpha(90)
                        : AppColors.border),
                width: isEquipped ? 2 : 1,
              ),
            ),
            child: ClipRRect(
              borderRadius: BorderRadius.circular(15),
              child: Stack(
                children: [
                  Container(
                    decoration: BoxDecoration(
                      gradient: LinearGradient(
                        colors: [
                          item.primaryColor.withAlpha(160),
                          item.secondaryColor?.withAlpha(120) ??
                              AppColors.surfaceElevated,
                          AppColors.surface,
                        ],
                        begin: Alignment.topLeft,
                        end: Alignment.bottomRight,
                      ),
                    ),
                  ),
                  Padding(
                    padding: const EdgeInsets.all(12),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Row(
                          mainAxisAlignment: MainAxisAlignment.spaceBetween,
                          children: [
                            Container(
                              padding: const EdgeInsets.all(6),
                              decoration: BoxDecoration(
                                color: Colors.black38,
                                borderRadius: BorderRadius.circular(8),
                              ),
                              child: Icon(item.icon,
                                  color: Colors.white, size: 18),
                            ),
                            if (isEquipped)
                              Container(
                                padding: const EdgeInsets.symmetric(
                                    horizontal: 8, vertical: 2),
                                decoration: BoxDecoration(
                                  color: AppColors.primaryAccent,
                                  borderRadius: BorderRadius.circular(6),
                                ),
                                child: Text(
                                  'ACTIVE',
                                  style: AppTypography.accentMono.copyWith(
                                    fontSize: 9,
                                    fontWeight: FontWeight.bold,
                                    color: Colors.black,
                                  ),
                                ),
                              ),
                          ],
                        ),
                        const Spacer(),
                        Text(
                          item.name,
                          style: AppTypography.bodySmall.copyWith(
                            fontWeight: FontWeight.bold,
                            color: Colors.white,
                            fontSize: 12,
                          ),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                        Text(
                          item.description,
                          style: AppTypography.bodySmall.copyWith(
                            fontSize: 10,
                            color: Colors.white70,
                          ),
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                        const SizedBox(height: 8),
                        _buildEquipButton(
                          item: item,
                          isEquipped: isEquipped,
                          isOwned: isOwned,
                          isLevelMet: isLevelMet,
                          totalStars: totalStars,
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          );
        },
      ),
    );
  }

  Widget _buildNameEffectsSection(int totalStars) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final crossAxisCount = constraints.maxWidth > 600 ? 3 : 2;
        return GridView.builder(
          shrinkWrap: true,
          physics: const NeverScrollableScrollPhysics(),
          gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
            crossAxisCount: crossAxisCount,
            crossAxisSpacing: 12,
            mainAxisSpacing: 12,
            childAspectRatio: 1.1,
          ),
          itemCount: CosmeticsCatalog.nameEffects.length,
          itemBuilder: (context, index) {
            final item = CosmeticsCatalog.nameEffects[index];
            final isOwned = _ownedCosmeticIds.contains(item.id);
            final isEquipped = _equippedEffect == item.id;
            final isLevelMet = _userLevel >= item.requiredLevel;

            return Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: AppColors.surface,
                borderRadius: BorderRadius.circular(16),
                border: Border.all(
                  color: isEquipped
                      ? AppColors.primaryAccent
                      : (isOwned
                          ? AppColors.successGreen.withAlpha(90)
                          : AppColors.border),
                  width: isEquipped ? 2 : 1,
                ),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Container(
                    padding: const EdgeInsets.symmetric(vertical: 8),
                    decoration: BoxDecoration(
                      color: AppColors.surfaceElevated,
                      borderRadius: BorderRadius.circular(10),
                    ),
                    child: Center(
                      child: Text(
                        _userMoniker,
                        style: TextStyle(
                          fontSize: 13,
                          fontWeight: FontWeight.bold,
                          color: item.primaryColor,
                          shadows: [
                            Shadow(
                              color: item.primaryColor.withAlpha(180),
                              blurRadius: 10,
                            ),
                            if (item.secondaryColor != null)
                              Shadow(
                                color: item.secondaryColor!.withAlpha(120),
                                blurRadius: 16,
                              ),
                          ],
                        ),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                  ),
                  Text(
                    item.name,
                    style: AppTypography.bodySmall.copyWith(
                      fontWeight: FontWeight.bold,
                      fontSize: 11,
                    ),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    textAlign: TextAlign.center,
                  ),
                  _buildEquipButton(
                    item: item,
                    isEquipped: isEquipped,
                    isOwned: isOwned,
                    isLevelMet: isLevelMet,
                    totalStars: totalStars,
                  ),
                ],
              ),
            );
          },
        );
      },
    );
  }

  Widget _buildEquipButton({
    required CosmeticItem item,
    required bool isEquipped,
    required bool isOwned,
    required bool isLevelMet,
    required int totalStars,
  }) {
    return ElevatedButton(
      onPressed: () => _claimOrEquipCosmetic(item, totalStars),
      style: ElevatedButton.styleFrom(
        padding: const EdgeInsets.symmetric(vertical: 6),
        elevation: 0,
        backgroundColor: isEquipped
            ? AppColors.primaryAccent
            : (isOwned
                ? AppColors.surfaceElevated
                : (isLevelMet
                    ? Colors.amber.withAlpha(35)
                    : AppColors.surfaceElevated)),
        foregroundColor: isEquipped
            ? AppColors.background
            : (isOwned
                ? AppColors.textPrimary
                : (isLevelMet
                    ? Colors.amber
                    : AppColors.textSecondary)),
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(8),
          side: BorderSide(
            color: isEquipped
                ? AppColors.primaryAccent
                : (isOwned
                    ? AppColors.border
                    : (isLevelMet
                        ? Colors.amber
                        : Colors.transparent)),
          ),
        ),
      ),
      child: Text(
        isEquipped
            ? 'Equipped'
            : (isOwned
                ? 'Equip'
                : (isLevelMet
                    ? (item.costStars == 0 ? 'Free' : '${item.costStars} ★')
                    : 'Lvl ${item.requiredLevel}')),
        style: const TextStyle(fontSize: 11, fontWeight: FontWeight.bold),
      ),
    );
  }

  Widget _buildAngularHeaderBadge() {
    final progress = (_currentXp / _nextTierXp).clamp(0.0, 1.0);

    return Container(
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(18),
        border: Border.all(
            color: AppColors.primaryAccent.withAlpha(80), width: 1.2),
        boxShadow: [
          BoxShadow(
            color: AppColors.primaryAccent.withAlpha(20),
            blurRadius: 16,
            offset: const Offset(0, 4),
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Container(
                padding:
                    const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                decoration: BoxDecoration(
                  gradient: const LinearGradient(
                    colors: [Color(0xFFF59E0B), Color(0xFFEF4444)],
                  ),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Text(
                  'SEASON 1',
                  style: AppTypography.accentMono.copyWith(
                    fontSize: 12,
                    fontWeight: FontWeight.w900,
                    color: Colors.black,
                  ),
                ),
              ),
              Container(
                padding:
                    const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                decoration: BoxDecoration(
                  color: AppColors.surfaceElevated,
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(color: AppColors.border),
                ),
                child: Row(
                  children: [
                    const Icon(Icons.timer_outlined,
                        size: 14, color: AppColors.textSecondary),
                    const SizedBox(width: 4),
                    Text(
                      'Ends in 18 days',
                      style: AppTypography.bodySmall.copyWith(
                        fontSize: 11,
                        color: AppColors.textSecondary,
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 16),
          Row(
            children: [
              Container(
                width: 52,
                height: 52,
                decoration: BoxDecoration(
                  color: AppColors.primaryAccent.withAlpha(35),
                  shape: BoxShape.circle,
                  border:
                      Border.all(color: AppColors.primaryAccent, width: 2),
                ),
                child: Center(
                  child: Text(
                    '$_userLevel',
                    style: AppTypography.h1.copyWith(
                      color: AppColors.primaryAccent,
                      fontSize: 22,
                    ),
                  ),
                ),
              ),
              const SizedBox(width: 14),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      '$_userMoniker · Level $_userLevel',
                      style: AppTypography.h2.copyWith(fontSize: 17),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      _getNextTierSubtitle(_userLevel),
                      style: AppTypography.bodySmall.copyWith(fontSize: 11),
                    ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 16),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text('TIER XP PROGRESSION', style: AppTypography.bodySmall),
              Text(
                '$_currentXp / $_nextTierXp XP',
                style: AppTypography.accentMono.copyWith(fontSize: 12),
              ),
            ],
          ),
          const SizedBox(height: 6),
          ClipRRect(
            borderRadius: BorderRadius.circular(4),
            child: LinearProgressIndicator(
              value: progress,
              minHeight: 8,
              backgroundColor: AppColors.surfaceElevated,
              valueColor:
                  const AlwaysStoppedAnimation(AppColors.primaryAccent),
            ),
          ),
        ],
      ),
    );
  }

  String _getNextTierSubtitle(int level) {
    for (final t in _tiers) {
      if (t.requiredLevel > level) {
        return 'Next Tier Unlock at Level ${t.requiredLevel} (Tier ${t.tierNumber})';
      }
    }
    return 'All Battlepass Tiers Unlocked';
  }

  Widget _buildHorizontalTierTrack(int totalStars) {
    return SizedBox(
      height: 200,
      child: ListView.builder(
        scrollDirection: Axis.horizontal,
        itemCount: _tiers.length,
        itemBuilder: (context, index) {
          final tier = _tiers[index];
          final tierId = 'tier_${tier.tierNumber}';
          final isOwned = _ownedTierIds.contains(tierId);
          final isEquipped = _equippedTierId == tierId;
          final isLevelMet = _userLevel >= tier.requiredLevel;

          return Container(
            width: 140,
            margin: const EdgeInsets.only(right: 12),
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: AppColors.surface,
              borderRadius: BorderRadius.circular(16),
              border: Border.all(
                color: isEquipped
                    ? AppColors.primaryAccent
                    : (isOwned
                        ? AppColors.successGreen.withAlpha(90)
                        : AppColors.border),
                width: isEquipped ? 2 : 1,
              ),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Text(
                      'T${tier.tierNumber}',
                      style: AppTypography.accentMono.copyWith(
                        fontSize: 12,
                        fontWeight: FontWeight.bold,
                        color: tier.accentColor,
                      ),
                    ),
                    if (tier.isFree)
                      Container(
                        padding: const EdgeInsets.symmetric(
                            horizontal: 6, vertical: 2),
                        decoration: BoxDecoration(
                          color: AppColors.successGreen.withAlpha(35),
                          borderRadius: BorderRadius.circular(4),
                        ),
                        child: Text(
                          'FREE',
                          style: AppTypography.accentMono.copyWith(
                            fontSize: 9,
                            fontWeight: FontWeight.bold,
                            color: AppColors.successGreen,
                          ),
                        ),
                      ),
                  ],
                ),
                const Spacer(),
                Center(
                  child: Container(
                    width: 48,
                    height: 48,
                    decoration: BoxDecoration(
                      color: tier.accentColor.withAlpha(25),
                      shape: BoxShape.circle,
                    ),
                    child: Icon(tier.icon, color: tier.accentColor, size: 26),
                  ),
                ),
                const Spacer(),
                Text(
                  tier.title,
                  style: AppTypography.bodySmall.copyWith(
                    fontWeight: FontWeight.bold,
                    fontSize: 11,
                  ),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  textAlign: TextAlign.center,
                ),
                const SizedBox(height: 8),
                ElevatedButton(
                  onPressed: () => _claimOrEquipTier(tier, totalStars),
                  style: ElevatedButton.styleFrom(
                    padding: const EdgeInsets.symmetric(vertical: 8),
                    elevation: 0,
                    backgroundColor: isEquipped
                        ? AppColors.primaryAccent
                        : (isOwned
                            ? AppColors.surfaceElevated
                            : (isLevelMet
                                ? Colors.amber.withAlpha(35)
                                : AppColors.surfaceElevated)),
                    foregroundColor: isEquipped
                        ? AppColors.background
                        : (isOwned
                            ? AppColors.textPrimary
                            : (isLevelMet
                                ? Colors.amber
                                : AppColors.textSecondary)),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(8),
                      side: BorderSide(
                        color: isEquipped
                            ? AppColors.primaryAccent
                            : (isOwned
                                ? AppColors.border
                                : (isLevelMet
                                    ? Colors.amber
                                    : Colors.transparent)),
                      ),
                    ),
                  ),
                  child: Text(
                    isEquipped
                        ? 'Equipped'
                        : (isOwned
                            ? 'Equip'
                            : (isLevelMet
                                ? (tier.isFree
                                    ? 'Free'
                                    : '${tier.starCost} ★')
                                : 'Lvl ${tier.requiredLevel}')),
                    style: const TextStyle(
                        fontSize: 11, fontWeight: FontWeight.bold),
                  ),
                ),
              ],
            ),
          );
        },
      ),
    );
  }
}
