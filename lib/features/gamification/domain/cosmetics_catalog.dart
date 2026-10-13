import 'package:flutter/material.dart';

enum CosmeticType {
  avatarFrame,
  profileBanner,
  nameEffect,
}

class CosmeticItem {
  final String id;
  final String name;
  final String description;
  final CosmeticType type;
  final int costStars;
  final int requiredLevel;
  final String assetOrStyleKey;
  final Color primaryColor;
  final Color? secondaryColor;
  final IconData icon;

  const CosmeticItem({
    required this.id,
    required this.name,
    required this.description,
    required this.type,
    required this.costStars,
    required this.requiredLevel,
    required this.assetOrStyleKey,
    required this.primaryColor,
    this.secondaryColor,
    required this.icon,
  });
}

class CosmeticsCatalog {
  static const List<CosmeticItem> avatarFrames = [
    CosmeticItem(
      id: 'frame_stoic_laurel',
      name: 'Stoic Laurel Wreath',
      description: 'Golden Roman laurels circling the determined mindset',
      type: CosmeticType.avatarFrame,
      costStars: 0,
      requiredLevel: 1,
      assetOrStyleKey: 'frame_stoic_laurel',
      primaryColor: Colors.amber,
      secondaryColor: Color(0xFFD97706),
      icon: Icons.military_tech_rounded,
    ),
    CosmeticItem(
      id: 'frame_cyber_glitch',
      name: 'Neon Cyber Ring',
      description: 'Pulsing dual-layer electric violet and cyan halo',
      type: CosmeticType.avatarFrame,
      costStars: 75,
      requiredLevel: 3,
      assetOrStyleKey: 'frame_cyber_glitch',
      primaryColor: Color(0xFF06B6D4),
      secondaryColor: Color(0xFFA855F7),
      icon: Icons.blur_on_rounded,
    ),
    CosmeticItem(
      id: 'frame_ember_forge',
      name: 'Spartan Ember Forge',
      description: 'Molten magma border forged in morning discipline',
      type: CosmeticType.avatarFrame,
      costStars: 150,
      requiredLevel: 6,
      assetOrStyleKey: 'frame_ember_forge',
      primaryColor: Color(0xFFEF4444),
      secondaryColor: Color(0xFFF97316),
      icon: Icons.local_fire_department_rounded,
    ),
    CosmeticItem(
      id: 'frame_obsidian_shield',
      name: 'Obsidian Hex Shield',
      description: 'Brushed carbon-fiber defense barrier against distractions',
      type: CosmeticType.avatarFrame,
      costStars: 250,
      requiredLevel: 10,
      assetOrStyleKey: 'frame_obsidian_shield',
      primaryColor: Color(0xFF64748B),
      secondaryColor: Color(0xFF0F172A),
      icon: Icons.shield_rounded,
    ),
    CosmeticItem(
      id: 'frame_emperor_celestial',
      name: 'Celestial Sovereign Ring',
      description: 'Transcendent starlight halo worn by cohort champions',
      type: CosmeticType.avatarFrame,
      costStars: 400,
      requiredLevel: 14,
      assetOrStyleKey: 'frame_emperor_celestial',
      primaryColor: Color(0xFFFFD700),
      secondaryColor: Color(0xFF38BDF8),
      icon: Icons.workspace_premium_rounded,
    ),
  ];

  static const List<CosmeticItem> banners = [
    CosmeticItem(
      id: 'banner_deep_space',
      name: 'Cosmic Nebula',
      description: 'Deep ultraviolet nebula with distant stellar points',
      type: CosmeticType.profileBanner,
      costStars: 0,
      requiredLevel: 1,
      assetOrStyleKey: 'banner_deep_space',
      primaryColor: Color(0xFF311042),
      secondaryColor: Color(0xFF0B0F19),
      icon: Icons.auto_awesome_rounded,
    ),
    CosmeticItem(
      id: 'banner_cyber_city',
      name: 'Neon Tokyo Grid',
      description: 'Cyberpunk midnight rain horizon over illuminated towers',
      type: CosmeticType.profileBanner,
      costStars: 100,
      requiredLevel: 4,
      assetOrStyleKey: 'banner_cyber_city',
      primaryColor: Color(0xFF0F172A),
      secondaryColor: Color(0xFFEC4899),
      icon: Icons.location_city_rounded,
    ),
    CosmeticItem(
      id: 'banner_stoic_pantheon',
      name: 'Roman Pantheon Pillars',
      description: 'Marble column architecture standing timeless against chaos',
      type: CosmeticType.profileBanner,
      costStars: 200,
      requiredLevel: 8,
      assetOrStyleKey: 'banner_stoic_pantheon',
      primaryColor: Color(0xFF451A03),
      secondaryColor: Color(0xFFB45309),
      icon: Icons.account_balance_rounded,
    ),
    CosmeticItem(
      id: 'banner_aurora_zen',
      name: 'Boreal Aurora',
      description: 'Glacial emerald ribbons dancing over still waters',
      type: CosmeticType.profileBanner,
      costStars: 300,
      requiredLevel: 12,
      assetOrStyleKey: 'banner_aurora_zen',
      primaryColor: Color(0xFF064E3B),
      secondaryColor: Color(0xFF10B981),
      icon: Icons.waves_rounded,
    ),
    CosmeticItem(
      id: 'banner_solar_apotheosis',
      name: 'Solar Apotheosis',
      description: 'Golden hour cresting the mountain peak of achievement',
      type: CosmeticType.profileBanner,
      costStars: 500,
      requiredLevel: 15,
      assetOrStyleKey: 'banner_solar_apotheosis',
      primaryColor: Color(0xFF7C2D12),
      secondaryColor: Color(0xFFF59E0B),
      icon: Icons.wb_sunny_rounded,
    ),
  ];

  static const List<CosmeticItem> nameEffects = [
    CosmeticItem(
      id: 'effect_clean_white',
      name: 'Standard Spartan Bold',
      description: 'Crisp high-contrast pure white typography',
      type: CosmeticType.nameEffect,
      costStars: 0,
      requiredLevel: 1,
      assetOrStyleKey: 'effect_clean_white',
      primaryColor: Colors.white,
      icon: Icons.text_fields_rounded,
    ),
    CosmeticItem(
      id: 'effect_amber_flame',
      name: 'Amber Forge Glow',
      description: 'Radiant ember outline with warm subtle shadow bloom',
      type: CosmeticType.nameEffect,
      costStars: 80,
      requiredLevel: 5,
      assetOrStyleKey: 'effect_amber_flame',
      primaryColor: Color(0xFFF59E0B),
      secondaryColor: Color(0xFFEF4444),
      icon: Icons.local_fire_department_rounded,
    ),
    CosmeticItem(
      id: 'effect_cyber_gradient',
      name: 'Vaporwave Shift',
      description: 'Horizontal gradient transitioning from neon pink to cyan',
      type: CosmeticType.nameEffect,
      costStars: 160,
      requiredLevel: 9,
      assetOrStyleKey: 'effect_cyber_gradient',
      primaryColor: Color(0xFFEC4899),
      secondaryColor: Color(0xFF06B6D4),
      icon: Icons.gradient_rounded,
    ),
    CosmeticItem(
      id: 'effect_emerald_matrix',
      name: 'Matrix Shimmer',
      description: 'Sharp emerald luminescence of relentless precision',
      type: CosmeticType.nameEffect,
      costStars: 260,
      requiredLevel: 11,
      assetOrStyleKey: 'effect_emerald_matrix',
      primaryColor: Color(0xFF10B981),
      secondaryColor: Color(0xFF34D399),
      icon: Icons.bolt_rounded,
    ),
    CosmeticItem(
      id: 'effect_gilded_emperor',
      name: 'Imperial Gold Spark',
      description: '24K gold mirror sheen reserved for mastery',
      type: CosmeticType.nameEffect,
      costStars: 450,
      requiredLevel: 16,
      assetOrStyleKey: 'effect_gilded_emperor',
      primaryColor: Color(0xFFFFD700),
      secondaryColor: Color(0xFFF59E0B),
      icon: Icons.stars_rounded,
    ),
  ];

  static List<CosmeticItem> get all => [
        ...avatarFrames,
        ...banners,
        ...nameEffects,
      ];

  static CosmeticItem? getById(String id) {
    try {
      return all.firstWhere((item) => item.id == id);
    } catch (_) {
      return null;
    }
  }
}
