import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/constants/app_colors.dart';
import '../../../../core/constants/app_typography.dart';
import '../controllers/auth_controller.dart';
import '../widgets/custom_server_dialog.dart';
import 'signup_screen.dart';

class LoginScreen extends ConsumerStatefulWidget {
  const LoginScreen({super.key});

  @override
  ConsumerState<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends ConsumerState<LoginScreen> {
  final _formKey = GlobalKey<FormState>();
  final _emailController = TextEditingController();
  final _passwordController = TextEditingController();
  bool _obscurePassword = true;

  @override
  void dispose() {
    _emailController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  Future<void> _submitLogin() async {
    if (!_formKey.currentState!.validate()) return;

    final success = await ref.read(authControllerProvider.notifier).login(
          email: _emailController.text.trim(),
          password: _passwordController.text,
        );

    if (!success && mounted) {
      final error = ref.read(authControllerProvider).errorMessage ?? 'Login failed';
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text(error),
          backgroundColor: AppColors.errorRed,
        ),
      );
    }
  }

  @override
  Widget build(BuildContext context) {
    final authState = ref.watch(authControllerProvider);
    final isLoading = authState.status == AuthStatus.loading;
    final size = MediaQuery.of(context).size;

    return Scaffold(
      backgroundColor: AppColors.background,
      body: SingleChildScrollView(
        child: Column(
          children: [
            // Prominent dark-curved header with cutout and logo
            Stack(
              children: [
                Container(
                  height: size.height * 0.32,
                  width: double.infinity,
                  decoration: const BoxDecoration(
                    color: AppColors.surface,
                    borderRadius: BorderRadius.only(
                      bottomLeft: Radius.circular(36),
                      bottomRight: Radius.circular(80),
                    ),
                  ),
                  child: SafeArea(
                    child: Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 28, vertical: 20),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Row(
                            children: [
                              Container(
                                width: 44,
                                height: 44,
                                decoration: BoxDecoration(
                                  color: AppColors.primaryAccent,
                                  borderRadius: BorderRadius.circular(14),
                                  boxShadow: [
                                    BoxShadow(
                                      color: AppColors.primaryAccent.withAlpha(90),
                                      blurRadius: 16,
                                      offset: const Offset(0, 4),
                                    ),
                                  ],
                                ),
                                child: const Icon(
                                  Icons.shield_rounded,
                                  color: AppColors.background,
                                  size: 26,
                                ),
                              ),
                              const SizedBox(width: 12),
                              Text(
                                'ORGANIZA',
                                style: AppTypography.h1.copyWith(
                                  letterSpacing: 2.0,
                                  fontSize: 20,
                                ),
                              ),
                            ],
                          ),
                          const Spacer(),
                          Text(
                            'Welcome Back',
                            style: AppTypography.h1.copyWith(fontSize: 28),
                          ),
                          const SizedBox(height: 6),
                          Text(
                            'Sign in to sync your Stoic discipline & focus systems.',
                            style: AppTypography.bodySmall.copyWith(
                              color: AppColors.textSecondary,
                            ),
                          ),
                          const SizedBox(height: 12),
                        ],
                      ),
                    ),
                  ),
                ),
                // Glowing accent pill line on header edge
                Positioned(
                  bottom: 0,
                  right: 40,
                  child: Container(
                    width: 60,
                    height: 4,
                    decoration: BoxDecoration(
                      color: AppColors.primaryAccent,
                      borderRadius: BorderRadius.circular(2),
                    ),
                  ),
                ),
              ],
            ),

            // High-contrast Form Container
            Padding(
              padding: const EdgeInsets.fromLTRB(24, 28, 24, 24),
              child: Form(
                key: _formKey,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    if (authState.status == AuthStatus.error &&
                        authState.errorMessage != null) ...[
                      Container(
                        padding: const EdgeInsets.symmetric(
                            horizontal: 14, vertical: 12),
                        margin: const EdgeInsets.only(bottom: 16),
                        decoration: BoxDecoration(
                          color: AppColors.errorRed.withAlpha(25),
                          borderRadius: BorderRadius.circular(12),
                          border: Border.all(
                            color: AppColors.errorRed.withAlpha(120),
                          ),
                        ),
                        child: Row(
                          children: [
                            const Icon(
                              Icons.error_outline_rounded,
                              color: AppColors.errorRed,
                              size: 20,
                            ),
                            const SizedBox(width: 10),
                            Expanded(
                              child: Text(
                                authState.errorMessage!,
                                style: AppTypography.bodySmall.copyWith(
                                  color: AppColors.errorRed,
                                  fontWeight: FontWeight.w500,
                                ),
                              ),
                            ),
                          ],
                        ),
                      ),
                    ],
                    // Email Field
                    Text('EMAIL ADDRESS', style: AppTypography.bodySmall),
                    const SizedBox(height: 8),
                    TextFormField(
                      controller: _emailController,
                      keyboardType: TextInputType.emailAddress,
                      style: AppTypography.bodyMedium,
                      validator: (val) {
                        if (val == null || val.trim().isEmpty) {
                          return 'Please enter your email';
                        }
                        if (!val.contains('@') || !val.contains('.')) {
                          return 'Enter a valid email address';
                        }
                        return null;
                      },
                      decoration: InputDecoration(
                        hintText: 'alexander@organiza.app',
                        hintStyle: AppTypography.bodySmall.copyWith(
                          color: AppColors.textSecondary.withAlpha(120),
                        ),
                        prefixIcon: const Icon(
                          Icons.mail_outline_rounded,
                          color: AppColors.textSecondary,
                          size: 20,
                        ),
                        filled: true,
                        fillColor: AppColors.surface,
                        border: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(14),
                          borderSide: const BorderSide(color: AppColors.border),
                        ),
                        enabledBorder: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(14),
                          borderSide: const BorderSide(color: AppColors.border),
                        ),
                        focusedBorder: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(14),
                          borderSide: const BorderSide(color: AppColors.primaryAccent),
                        ),
                        contentPadding: const EdgeInsets.symmetric(
                          horizontal: 16,
                          vertical: 16,
                        ),
                      ),
                    ),
                    const SizedBox(height: 20),

                    // Password Field
                    Text('PASSWORD', style: AppTypography.bodySmall),
                    const SizedBox(height: 8),
                    TextFormField(
                      controller: _passwordController,
                      obscureText: _obscurePassword,
                      style: AppTypography.bodyMedium,
                      validator: (val) {
                        if (val == null || val.isEmpty) {
                          return 'Please enter your password';
                        }
                        if (val.length < 6) {
                          return 'Password must be at least 6 characters';
                        }
                        return null;
                      },
                      decoration: InputDecoration(
                        hintText: '••••••••••••',
                        hintStyle: AppTypography.bodySmall.copyWith(
                          color: AppColors.textSecondary.withAlpha(120),
                        ),
                        prefixIcon: const Icon(
                          Icons.lock_outline_rounded,
                          color: AppColors.textSecondary,
                          size: 20,
                        ),
                        suffixIcon: IconButton(
                          icon: Icon(
                            _obscurePassword
                                ? Icons.visibility_off_outlined
                                : Icons.visibility_outlined,
                            color: AppColors.textSecondary,
                            size: 20,
                          ),
                          onPressed: () => setState(() => _obscurePassword = !_obscurePassword),
                        ),
                        filled: true,
                        fillColor: AppColors.surface,
                        border: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(14),
                          borderSide: const BorderSide(color: AppColors.border),
                        ),
                        enabledBorder: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(14),
                          borderSide: const BorderSide(color: AppColors.border),
                        ),
                        focusedBorder: OutlineInputBorder(
                          borderRadius: BorderRadius.circular(14),
                          borderSide: const BorderSide(color: AppColors.primaryAccent),
                        ),
                        contentPadding: const EdgeInsets.symmetric(
                          horizontal: 16,
                          vertical: 16,
                        ),
                      ),
                    ),
                    const SizedBox(height: 28),

                    // Action Button ("Sign in")
                    ElevatedButton(
                      onPressed: isLoading ? null : _submitLogin,
                      style: ElevatedButton.styleFrom(
                        backgroundColor: AppColors.primaryAccent,
                        foregroundColor: AppColors.background,
                        padding: const EdgeInsets.symmetric(vertical: 16),
                        elevation: 4,
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(14),
                        ),
                      ),
                      child: isLoading
                          ? const SizedBox(
                              height: 22,
                              width: 22,
                              child: CircularProgressIndicator(
                                strokeWidth: 2.5,
                                color: AppColors.background,
                              ),
                            )
                          : Text(
                              'Sign In',
                              style: AppTypography.bodyMedium.copyWith(
                                fontWeight: FontWeight.bold,
                                fontSize: 16,
                                color: AppColors.background,
                              ),
                            ),
                    ),
                    const SizedBox(height: 12),

                    // Self-hosted / custom server config
                    Center(
                      child: TextButton.icon(
                        onPressed: () => CustomServerDialog.show(context),
                        icon: const Icon(
                          Icons.dns_outlined,
                          size: 15,
                          color: AppColors.textSecondary,
                        ),
                        label: Text(
                          'Self-hosted or custom server? Tap to configure',
                          style: AppTypography.bodySmall.copyWith(
                            color: AppColors.textSecondary,
                            fontSize: 12,
                          ),
                        ),
                      ),
                    ),
                    const SizedBox(height: 8),

                    // Switch to Sign Up
                    Row(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Text(
                          "Don't have an account?",
                          style: AppTypography.bodySmall,
                        ),
                        TextButton(
                          onPressed: () {
                            Navigator.of(context).push(
                              MaterialPageRoute(
                                builder: (_) => const SignUpScreen(),
                              ),
                            );
                          },
                          child: Text(
                            'Register Handle',
                            style: AppTypography.bodySmall.copyWith(
                              color: AppColors.primaryAccent,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                        ),
                      ],
                    ),

                    const SizedBox(height: 20),
                    // Divider
                    Row(
                      children: [
                        const Expanded(child: Divider(color: AppColors.border)),
                        Padding(
                          padding: const EdgeInsets.symmetric(horizontal: 12),
                          child: Text('OR', style: AppTypography.bodySmall.copyWith(fontSize: 11)),
                        ),
                        const Expanded(child: Divider(color: AppColors.border)),
                      ],
                    ),
                    const SizedBox(height: 20),

                    // Guest / Offline Bypass
                    OutlinedButton.icon(
                      onPressed: () {
                        ref.read(authControllerProvider.notifier).continueAsGuest();
                      },
                      style: OutlinedButton.styleFrom(
                        side: const BorderSide(color: AppColors.border),
                        padding: const EdgeInsets.symmetric(vertical: 14),
                        shape: RoundedRectangleBorder(
                          borderRadius: BorderRadius.circular(14),
                        ),
                      ),
                      icon: const Icon(
                        Icons.person_outline_rounded,
                        color: AppColors.textSecondary,
                        size: 20,
                      ),
                      label: Text(
                        'Continue as Guest (Local SQLite Mode)',
                        style: AppTypography.bodySmall.copyWith(
                          color: AppColors.textSecondary,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
