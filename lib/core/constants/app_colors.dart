import 'package:flutter/material.dart';

abstract final class AppColors {
  // Brand Base Palette
  static const Color background = Color(0xFF002734);
  static const Color surface = Color(0xFF033342);
  static const Color surfaceElevated = Color(0xFF0A4455);
  static const Color border = Color(0xFF0F576C);

  // Vibrant Functional Accents
  static const Color primaryAccent = Color(0xFF00B0C3); // Electric Cyan
  static const Color secondary = Color(0xFF00CB95); // Mint Green
  static const Color accent = Color(0xFF20269E); // Cobalt Indigo

  // Typography
  static const Color textPrimary = Color(0xFFFFFFFF); // Pure White
  static const Color textSecondary = Color(0xFF87A7B3); // Soft Muted Cyan

  // Status Colors
  static const Color errorRed = Color(0xFFEF4444);
  static const Color successGreen = Color(0xFF00CB95);
  static const Color starGold = Color(0xFFFFC107);
}
