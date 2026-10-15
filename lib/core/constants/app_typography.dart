import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'app_colors.dart';

abstract final class AppTypography {
  // Duolingo-inspired friendly, rounded & bold typography
  static final TextStyle h1 = GoogleFonts.nunito(
    fontSize: 24,
    fontWeight: FontWeight.w800,
    color: AppColors.textPrimary,
    letterSpacing: -0.2,
  );

  static final TextStyle h2 = GoogleFonts.nunito(
    fontSize: 18,
    fontWeight: FontWeight.w800,
    color: AppColors.textPrimary,
  );

  static final TextStyle h3 = GoogleFonts.nunito(
    fontSize: 15,
    fontWeight: FontWeight.w700,
    color: AppColors.textPrimary,
  );

  static final TextStyle bodyMedium = GoogleFonts.nunito(
    fontSize: 14,
    fontWeight: FontWeight.w600,
    color: AppColors.textPrimary,
  );

  static final TextStyle bodySmall = GoogleFonts.nunito(
    fontSize: 12,
    fontWeight: FontWeight.w600,
    color: AppColors.textSecondary,
  );

  // High precision monospace font for timers, meters, progress stats, and codes
  static final TextStyle accentMono = GoogleFonts.jetBrainsMono(
    fontSize: 14,
    fontWeight: FontWeight.w700,
    color: AppColors.primaryAccent,
  );

  static final TextStyle badge = GoogleFonts.nunito(
    fontSize: 11,
    fontWeight: FontWeight.w800,
    color: AppColors.textPrimary,
  );
}
