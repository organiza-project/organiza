import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/constants/app_colors.dart';
import '../../../../core/constants/app_typography.dart';
import '../../../../core/network/supabase_service.dart';
import '../controllers/auth_controller.dart';

class SignUpScreen extends ConsumerStatefulWidget {
  const SignUpScreen({super.key});

  @override
  ConsumerState<SignUpScreen> createState() => _SignUpScreenState();
}

class _SignUpScreenState extends ConsumerState<SignUpScreen> {
  final _formKey = GlobalKey<FormState>();
  final _usernameController = TextEditingController();
  final _emailController = TextEditingController();
  final _passwordController = TextEditingController();
  final _confirmPasswordController = TextEditingController();

  bool _obscurePassword = true;
  bool _obscureConfirmPassword = true;
  bool? _isHandleAvailable;
  bool _isCheckingHandle = false;

  @override
  void dispose() {
    _usernameController.dispose();
    _emailController.dispose();
    _passwordController.dispose();
    _confirmPasswordController.dispose();
    super.dispose();
  }

  Future<void> _checkHandle(String handle) async {
    final clean = handle.trim().replaceAll('@', '');
    if (clean.length < 3) {
      setState(() {
        _isHandleAvailable = null;
        _isCheckingHandle = false;
      });
      return;
    }

    setState(() => _isCheckingHandle = true);
    final available = await ref.read(supabaseServiceProvider).isUsernameAvailable(clean);
    if (mounted) {
      setState(() {
        _isHandleAvailable = available;
        _isCheckingHandle = false;
      });
    }
  }

  Future<void> _submitRegister() async {
    if (!_formKey.currentState!.validate()) return;

    if (_isHandleAvailable == false) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('This @username is already taken. Choose another.'),
          backgroundColor: AppColors.errorRed,
        ),
      );
      return;
    }

    final success = await ref.read(authControllerProvider.notifier).signUp(
          email: _emailController.text.trim(),
          password: _passwordController.text,
          username: _usernameController.text.trim(),
        );

    if (success && mounted) {
      Navigator.of(context).popUntil((route) => route.isFirst);
    } else if (mounted) {
      final error = ref.read(authControllerProvider).errorMessage ?? 'Registration failed';
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
            // Dark curved header with sheet cutout and branding
            Stack(
              children: [
                Container(
                  height: size.height * 0.28,
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
                      padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 16),
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Row(
                            children: [
                              IconButton(
                                onPressed: () => Navigator.of(context).pop(),
                                icon: const Icon(
                                  Icons.arrow_back_ios_new_rounded,
                                  color: AppColors.textPrimary,
                                  size: 20,
                                ),
                              ),
                              const SizedBox(width: 8),
                              Text(
                                'Create Identity',
                                style: AppTypography.h1.copyWith(fontSize: 22),
                              ),
                            ],
                          ),
                          const Spacer(),
                          Text(
                            'Begin Your Journey',
                            style: AppTypography.h2.copyWith(fontSize: 20),
                          ),
                          const SizedBox(height: 4),
                          Text(
                            'Claim your Spartan handle and activate cloud accountability.',
                            style: AppTypography.bodySmall,
                          ),
                          const SizedBox(height: 10),
                        ],
                      ),
                    ),
                  ),
                ),
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

            // Form container
            Padding(
              padding: const EdgeInsets.fromLTRB(24, 24, 24, 30),
              child: Form(
                key: _formKey,
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    // Unique Handle Field
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Text('SPARTAN HANDLE', style: AppTypography.bodySmall),
                        if (_isCheckingHandle)
                          const SizedBox(
                            width: 14,
                            height: 14,
                            child: CircularProgressIndicator(
                              strokeWidth: 2,
                              color: AppColors.primaryAccent,
                            ),
                          )
                        else if (_isHandleAvailable == true)
                          Row(
                            children: [
                              const Icon(Icons.check_circle_rounded,
                                  color: AppColors.successGreen, size: 14),
                              const SizedBox(width: 4),
                              Text(
                                'Available',
                                style: AppTypography.bodySmall.copyWith(
                                  color: AppColors.successGreen,
                                  fontSize: 11,
                                ),
                              ),
                            ],
                          )
                        else if (_isHandleAvailable == false)
                          Row(
                            children: [
                              const Icon(Icons.cancel_rounded,
                                  color: AppColors.errorRed, size: 14),
                              const SizedBox(width: 4),
                              Text(
                                'Taken',
                                style: AppTypography.bodySmall.copyWith(
                                  color: AppColors.errorRed,
                                  fontSize: 11,
                                ),
                              ),
                            ],
                          ),
                      ],
                    ),
                    const SizedBox(height: 8),
                    TextFormField(
                      controller: _usernameController,
                      style: AppTypography.accentMono.copyWith(fontSize: 15),
                      onChanged: _checkHandle,
                      validator: (val) {
                        if (val == null || val.trim().isEmpty) {
                          return 'Please choose a handle';
                        }
                        final clean = val.trim().replaceAll('@', '');
                        if (clean.length < 3) {
                          return 'Handle must be at least 3 characters';
                        }
                        if (!RegExp(r'^[a-zA-Z0-9_]+$').hasMatch(clean)) {
                          return 'Only letters, numbers, and underscores';
                        }
                        return null;
                      },
                      decoration: InputDecoration(
                        hintText: '@marcus_focus',
                        hintStyle: AppTypography.bodySmall.copyWith(
                          color: AppColors.textSecondary.withAlpha(120),
                        ),
                        prefixText: '@ ',
                        prefixStyle: AppTypography.accentMono.copyWith(
                          color: AppColors.primaryAccent,
                          fontWeight: FontWeight.bold,
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
                    const SizedBox(height: 18),

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
                    const SizedBox(height: 18),

                    // Password Field
                    Text('PASSWORD', style: AppTypography.bodySmall),
                    const SizedBox(height: 8),
                    TextFormField(
                      controller: _passwordController,
                      obscureText: _obscurePassword,
                      style: AppTypography.bodyMedium,
                      validator: (val) {
                        if (val == null || val.isEmpty) {
                          return 'Please enter a password';
                        }
                        if (val.length < 6) {
                          return 'Password must be at least 6 characters';
                        }
                        return null;
                      },
                      decoration: InputDecoration(
                        hintText: 'Minimum 6 characters',
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
                    const SizedBox(height: 18),

                    // Confirm Password Field
                    Text('CONFIRM PASSWORD', style: AppTypography.bodySmall),
                    const SizedBox(height: 8),
                    TextFormField(
                      controller: _confirmPasswordController,
                      obscureText: _obscureConfirmPassword,
                      style: AppTypography.bodyMedium,
                      validator: (val) {
                        if (val != _passwordController.text) {
                          return 'Passwords do not match';
                        }
                        return null;
                      },
                      decoration: InputDecoration(
                        hintText: 'Re-enter password',
                        hintStyle: AppTypography.bodySmall.copyWith(
                          color: AppColors.textSecondary.withAlpha(120),
                        ),
                        prefixIcon: const Icon(
                          Icons.shield_outlined,
                          color: AppColors.textSecondary,
                          size: 20,
                        ),
                        suffixIcon: IconButton(
                          icon: Icon(
                            _obscureConfirmPassword
                                ? Icons.visibility_off_outlined
                                : Icons.visibility_outlined,
                            color: AppColors.textSecondary,
                            size: 20,
                          ),
                          onPressed: () => setState(
                              () => _obscureConfirmPassword = !_obscureConfirmPassword),
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

                    // Register Button
                    ElevatedButton(
                      onPressed: isLoading ? null : _submitRegister,
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
                              'Register & Enlist',
                              style: AppTypography.bodyMedium.copyWith(
                                fontWeight: FontWeight.bold,
                                fontSize: 16,
                                color: AppColors.background,
                              ),
                            ),
                    ),
                    const SizedBox(height: 16),

                    // Switch back to Login
                    Row(
                      mainAxisAlignment: MainAxisAlignment.center,
                      children: [
                        Text('Already have an account?', style: AppTypography.bodySmall),
                        TextButton(
                          onPressed: () => Navigator.of(context).pop(),
                          child: Text(
                            'Sign In',
                            style: AppTypography.bodySmall.copyWith(
                              color: AppColors.primaryAccent,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                        ),
                      ],
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
