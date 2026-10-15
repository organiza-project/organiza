import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'core/config/supabase_config.dart';
import 'core/constants/app_colors.dart';
import 'core/network/supabase_service.dart';
import 'core/theme/app_theme.dart';
import 'features/auth/presentation/controllers/auth_controller.dart';
import 'features/auth/presentation/screens/login_screen.dart';
import 'features/auth/presentation/screens/verify_email_screen.dart';
import 'shell.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();

  final prefs = await SharedPreferences.getInstance();
  final activeUrl = await SupabaseConfig.getActiveUrl(prefs);
  final activeAnonKey = await SupabaseConfig.getActiveAnonKey(prefs);

  final supabaseService = SupabaseService();
  if (SupabaseConfig.isConfigured || SupabaseConfig.hasCustomServer(prefs)) {
    await supabaseService.initialize(
      url: activeUrl,
      anonKey: activeAnonKey,
    );
  } else {
    debugPrint('Organiza: Supabase is not configured. Running offline mode.');
  }

  runApp(
    ProviderScope(
      overrides: [
        supabaseServiceProvider.overrideWithValue(supabaseService),
      ],
      child: const OrganizaApp(),
    ),
  );
}

class OrganizaApp extends ConsumerWidget {
  const OrganizaApp({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final authState = ref.watch(authControllerProvider);

    return MaterialApp(
      title: 'Organiza',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.darkTheme,
      darkTheme: AppTheme.darkTheme,
      themeMode: ThemeMode.dark,
      home: _buildRoot(authState),
    );
  }

  Widget _buildRoot(OrganizaAuthState authState) {
    switch (authState.status) {
      case AuthStatus.authenticated:
        // Email confirmation guard for registered Supabase accounts
        if (!authState.isGuest && authState.user != null) {
          final emailConfirmed = authState.user!.emailConfirmedAt != null;
          if (!emailConfirmed) {
            return VerifyEmailScreen(
              email: authState.user!.email ?? 'your email',
            );
          }
        }
        return const AppShell();
      case AuthStatus.unauthenticated:
      case AuthStatus.error:
        return const LoginScreen();
      case AuthStatus.initial:
      case AuthStatus.loading:
        return const Scaffold(
          backgroundColor: AppColors.background,
          body: Center(
            child: CircularProgressIndicator(
              color: AppColors.primaryAccent,
            ),
          ),
        );
    }
  }
}
