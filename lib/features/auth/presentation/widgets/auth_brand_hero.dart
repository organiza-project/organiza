import 'package:flutter/material.dart';
import '../../../../core/constants/app_colors.dart';
import '../../../../core/constants/app_typography.dart';

class OrganizaLogoGlyph extends StatelessWidget {
  final double size;

  const OrganizaLogoGlyph({super.key, this.size = 56});

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: size,
      height: size,
      child: Stack(
        alignment: Alignment.center,
        children: [
          // Outer stylized ring 'O'
          Container(
            width: size,
            height: size,
            decoration: BoxDecoration(
              shape: BoxShape.circle,
              border: Border.all(
                color: Colors.white,
                width: size * 0.12,
              ),
              boxShadow: [
                BoxShadow(
                  color: AppColors.primaryAccent.withAlpha(50),
                  blurRadius: 16,
                  spreadRadius: 2,
                ),
              ],
            ),
          ),
          // Inner Laptop & Stacked Books icon badge
          Positioned(
            right: 2,
            bottom: 2,
            child: Container(
              padding: EdgeInsets.all(size * 0.08),
              decoration: BoxDecoration(
                color: AppColors.surface,
                borderRadius: BorderRadius.circular(size * 0.2),
                border: Border.all(
                  color: AppColors.primaryAccent,
                  width: 1.5,
                ),
              ),
              child: Icon(
                Icons.laptop_chromebook_rounded,
                size: size * 0.42,
                color: AppColors.primaryAccent,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class AuthBrandHero extends StatelessWidget {
  const AuthBrandHero({super.key});

  @override
  Widget build(BuildContext context) {
    return Container(
      color: AppColors.background,
      child: Stack(
        children: [
          // Subtle background decorative curved geometry in cyan & indigo
          Positioned(
            top: -80,
            left: -80,
            child: Container(
              width: 320,
              height: 320,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                border: Border.all(
                  color: AppColors.primaryAccent.withAlpha(25),
                  width: 36,
                ),
              ),
            ),
          ),
          Positioned(
            bottom: -100,
            right: -60,
            child: Container(
              width: 360,
              height: 360,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                border: Border.all(
                  color: AppColors.accent.withAlpha(35),
                  width: 44,
                ),
              ),
            ),
          ),

          // Main Hero Content
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 48, vertical: 48),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // Logo Glyph
                const OrganizaLogoGlyph(size: 64),
                const Spacer(),

                // Brand Name
                Text(
                  'organiza',
                  style: AppTypography.h1.copyWith(
                    fontSize: 44,
                    letterSpacing: -1.0,
                    fontWeight: FontWeight.w800,
                    color: Colors.white,
                  ),
                ),
                const SizedBox(height: 8),

                // Tagline
                Text(
                  'YOUR TIME. WELL MANAGED.',
                  style: AppTypography.accentMono.copyWith(
                    fontSize: 13,
                    letterSpacing: 2.5,
                    fontWeight: FontWeight.bold,
                    color: AppColors.primaryAccent,
                  ),
                ),
                const SizedBox(height: 36),

                // Bullet Highlights with custom glyphs
                _buildHighlightRow(
                  icon: Icons.shield_rounded,
                  title: 'Digital habits & cognitive defense',
                  subtitle: 'Unprivileged background tracking and distraction shields',
                ),
                const SizedBox(height: 20),
                _buildHighlightRow(
                  icon: Icons.local_fire_department_rounded,
                  title: 'Deep focus with strict Stoic lockouts',
                  subtitle: 'Earn Stars, level tiers and uphold daily vows',
                ),
                const SizedBox(height: 20),
                _buildHighlightRow(
                  icon: Icons.sync_rounded,
                  title: 'Sync across Linux, Android & Web',
                  subtitle: 'Local-first encrypted SQLite with optional cloud backup',
                ),

                const Spacer(),

                // Device Sync footer badge
                Row(
                  children: [
                    const Icon(Icons.devices_rounded,
                        color: AppColors.textSecondary, size: 22),
                    const SizedBox(width: 10),
                    Text(
                      'Syncs across devices',
                      style: AppTypography.bodySmall.copyWith(
                        color: AppColors.textSecondary,
                        fontSize: 12,
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildHighlightRow({
    required IconData icon,
    required String title,
    required String subtitle,
  }) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Container(
          margin: const EdgeInsets.only(top: 2),
          padding: const EdgeInsets.all(6),
          decoration: BoxDecoration(
            color: AppColors.primaryAccent.withAlpha(25),
            shape: BoxShape.circle,
            border: Border.all(color: AppColors.primaryAccent.withAlpha(80)),
          ),
          child: Icon(icon, color: AppColors.primaryAccent, size: 14),
        ),
        const SizedBox(width: 14),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(
                title,
                style: AppTypography.bodyMedium.copyWith(
                  fontWeight: FontWeight.bold,
                  color: Colors.white,
                ),
              ),
              const SizedBox(height: 2),
              Text(
                subtitle,
                style: AppTypography.bodySmall.copyWith(
                  color: AppColors.textSecondary,
                  fontSize: 12,
                ),
              ),
            ],
          ),
        ),
      ],
    );
  }
}
