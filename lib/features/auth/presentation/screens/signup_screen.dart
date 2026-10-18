import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/constants/app_colors.dart';
import '../../../../core/constants/app_typography.dart';
import '../../../../core/constants/legal_docs.dart';
import '../../../../core/network/supabase_service.dart';
import '../controllers/auth_controller.dart';
import '../widgets/auth_brand_hero.dart';
import 'verify_email_screen.dart';

enum PasswordStrength { weak, medium, strong }

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

  bool _obscurePassword = true;
  bool? _isHandleAvailable;
  bool _isCheckingHandle = false;
  bool _agreedToTerms = true;

  @override
  void dispose() {
    _usernameController.dispose();
    _emailController.dispose();
    _passwordController.dispose();
    super.dispose();
  }

  PasswordStrength _calculatePasswordStrength(String password) {
    if (password.length < 6) return PasswordStrength.weak;
    bool hasLetters = password.contains(RegExp(r'[a-zA-Z]'));
    bool hasDigits = password.contains(RegExp(r'[0-9]'));
    bool hasSpecial = password.contains(RegExp(r'[!@#$%^&*(),.?":{}|<>]'));

    if (password.length >= 10 && hasLetters && hasDigits && hasSpecial) {
      return PasswordStrength.strong;
    }
    if (password.length >= 6 && hasLetters && (hasDigits || hasSpecial)) {
      return PasswordStrength.medium;
    }
    return PasswordStrength.weak;
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
    final available =
        await ref.read(supabaseServiceProvider).isUsernameAvailable(clean);
    if (mounted) {
      setState(() {
        _isHandleAvailable = available;
        _isCheckingHandle = false;
      });
    }
  }

  void _showLegalDialog(String title, String content) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: AppColors.surface,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(16),
          side: const BorderSide(color: AppColors.border),
        ),
        title: Text(title, style: AppTypography.h2),
        content: SizedBox(
          width: 500,
          child: SingleChildScrollView(
            child: Text(
              content,
              style: AppTypography.bodySmall.copyWith(height: 1.5),
            ),
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(ctx).pop(),
            child: Text('Close',
                style: AppTypography.bodyMedium
                    .copyWith(color: AppColors.primaryAccent)),
          ),
        ],
      ),
    );
  }

  Future<void> _submitRegister() async {
    if (!_formKey.currentState!.validate()) return;

    if (!_agreedToTerms) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Please accept the Terms of Service to continue.'),
          backgroundColor: AppColors.errorRed,
        ),
      );
      return;
    }

    if (_isHandleAvailable == false) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('This @username is already taken. Choose another.'),
          backgroundColor: AppColors.errorRed,
        ),
      );
      return;
    }

    final email = _emailController.text.trim();
    final success = await ref.read(authControllerProvider.notifier).signUp(
          email: email,
          password: _passwordController.text,
          username: _usernameController.text.trim(),
        );

    if (success && mounted) {
      // Check if session user has email confirmation pending
      final auth = ref.read(authControllerProvider);
      final isConfirmed = auth.user?.emailConfirmedAt != null;
      if (!isConfirmed && !auth.isGuest) {
        Navigator.of(context).pushReplacement(
          MaterialPageRoute(
            builder: (context) => VerifyEmailScreen(email: email),
          ),
        );
      } else {
        Navigator.of(context).popUntil((route) => route.isFirst);
      }
    } else if (mounted) {
      final error = ref.read(authControllerProvider).errorMessage ??
          'Registration failed';
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

    return Scaffold(
      backgroundColor: AppColors.background,
      body: LayoutBuilder(
        builder: (context, constraints) {
          final isDesktop = constraints.maxWidth >= 900;
          if (isDesktop) {
            return Row(
              children: [
                const Expanded(
                  flex: 5,
                  child: AuthBrandHero(),
                ),
                Expanded(
                  flex: 5,
                  child: Container(
                    color: AppColors.surfaceElevated.withAlpha(50),
                    child: Center(
                      child: SingleChildScrollView(
                        padding: const EdgeInsets.symmetric(
                            horizontal: 40, vertical: 32),
                        child: ConstrainedBox(
                          constraints: const BoxConstraints(maxWidth: 440),
                          child: _buildSignUpCard(context, isLoading),
                        ),
                      ),
                    ),
                  ),
                ),
              ],
            );
          } else {
            return SafeArea(
              child: Center(
                child: SingleChildScrollView(
                  padding:
                      const EdgeInsets.symmetric(horizontal: 24, vertical: 24),
                  child: ConstrainedBox(
                    constraints: const BoxConstraints(maxWidth: 420),
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        const SizedBox(height: 12),
                        const OrganizaLogoGlyph(size: 64),
                        const SizedBox(height: 20),
                        _buildSignUpCard(context, isLoading),
                        const SizedBox(height: 20),
                        // Mobile carousel dots
                        Row(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            Container(
                              width: 6,
                              height: 6,
                              decoration: BoxDecoration(
                                shape: BoxShape.circle,
                                color: AppColors.textSecondary.withAlpha(80),
                              ),
                            ),
                            const SizedBox(width: 6),
                            Container(
                              width: 8,
                              height: 8,
                              decoration: const BoxDecoration(
                                shape: BoxShape.circle,
                                color: AppColors.primaryAccent,
                              ),
                            ),
                            const SizedBox(width: 6),
                            Container(
                              width: 6,
                              height: 6,
                              decoration: BoxDecoration(
                                shape: BoxShape.circle,
                                color: AppColors.textSecondary.withAlpha(80),
                              ),
                            ),
                            const SizedBox(width: 6),
                            Container(
                              width: 6,
                              height: 6,
                              decoration: BoxDecoration(
                                shape: BoxShape.circle,
                                color: AppColors.textSecondary.withAlpha(80),
                              ),
                            ),
                          ],
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            );
          }
        },
      ),
    );
  }

  Widget _buildSignUpCard(BuildContext context, bool isLoading) {
    final passwordStrength =
        _calculatePasswordStrength(_passwordController.text);

    return Container(
      padding: const EdgeInsets.all(32),
      decoration: BoxDecoration(
        color: AppColors.surface,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: AppColors.border, width: 1.2),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withAlpha(80),
            blurRadius: 30,
            offset: const Offset(0, 10),
          ),
        ],
      ),
      child: Form(
        key: _formKey,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(
              'Create your account',
              style: AppTypography.h1.copyWith(
                fontSize: 26,
                fontWeight: FontWeight.bold,
              ),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 6),
            Text(
              'Begin your oath of discipline and focused productivity.',
              style: AppTypography.bodySmall.copyWith(
                color: AppColors.textSecondary,
                fontSize: 13,
              ),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 24),

            // Email Address
            TextFormField(
              controller: _emailController,
              keyboardType: TextInputType.emailAddress,
              style: AppTypography.bodyMedium,
              decoration: InputDecoration(
                hintText: 'Email address',
                hintStyle: AppTypography.bodySmall,
                filled: true,
                fillColor: AppColors.background,
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(10),
                  borderSide: const BorderSide(color: AppColors.border),
                ),
                enabledBorder: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(10),
                  borderSide: const BorderSide(color: AppColors.border),
                ),
                prefixIcon: const Icon(Icons.mail_outline_rounded,
                    color: AppColors.textSecondary, size: 20),
                contentPadding: const EdgeInsets.symmetric(
                    horizontal: 16, vertical: 14),
              ),
              validator: (val) {
                if (val == null || val.trim().isEmpty) {
                  return 'Email is required';
                }
                if (!val.contains('@')) return 'Enter a valid email';
                return null;
              },
            ),
            const SizedBox(height: 14),

            // Username
            TextFormField(
              controller: _usernameController,
              style: AppTypography.bodyMedium,
              onChanged: _checkHandle,
              decoration: InputDecoration(
                hintText: '@username',
                hintStyle: AppTypography.bodySmall,
                filled: true,
                fillColor: AppColors.background,
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(10),
                  borderSide: const BorderSide(color: AppColors.border),
                ),
                enabledBorder: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(10),
                  borderSide: const BorderSide(color: AppColors.border),
                ),
                prefixIcon: const Icon(Icons.alternate_email_rounded,
                    color: AppColors.textSecondary, size: 20),
                suffixIcon: _isCheckingHandle
                    ? const Padding(
                        padding: EdgeInsets.all(12),
                        child: SizedBox(
                          width: 14,
                          height: 14,
                          child: CircularProgressIndicator(strokeWidth: 2),
                        ),
                      )
                    : _isHandleAvailable == null
                        ? null
                        : Icon(
                            _isHandleAvailable!
                                ? Icons.check_circle_rounded
                                : Icons.cancel_rounded,
                            color: _isHandleAvailable!
                                ? AppColors.successGreen
                                : AppColors.errorRed,
                            size: 20,
                          ),
                contentPadding: const EdgeInsets.symmetric(
                    horizontal: 16, vertical: 14),
              ),
              validator: (val) {
                if (val == null || val.trim().isEmpty) {
                  return 'Username handle is required';
                }
                if (val.trim().length < 3) return 'At least 3 characters';
                return null;
              },
            ),
            const SizedBox(height: 14),

            // Password
            TextFormField(
              controller: _passwordController,
              obscureText: _obscurePassword,
              style: AppTypography.bodyMedium,
              onChanged: (_) => setState(() {}),
              decoration: InputDecoration(
                hintText: 'Password',
                hintStyle: AppTypography.bodySmall,
                filled: true,
                fillColor: AppColors.background,
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(10),
                  borderSide: const BorderSide(color: AppColors.border),
                ),
                enabledBorder: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(10),
                  borderSide: const BorderSide(color: AppColors.border),
                ),
                prefixIcon: const Icon(Icons.lock_outline_rounded,
                    color: AppColors.textSecondary, size: 20),
                suffixIcon: IconButton(
                  icon: Icon(
                    _obscurePassword
                        ? Icons.visibility_outlined
                        : Icons.visibility_off_outlined,
                    color: AppColors.textSecondary,
                    size: 20,
                  ),
                  onPressed: () {
                    setState(() => _obscurePassword = !_obscurePassword);
                  },
                ),
                contentPadding: const EdgeInsets.symmetric(
                    horizontal: 16, vertical: 14),
              ),
              validator: (val) {
                if (val == null || val.isEmpty) {
                  return 'Password is required';
                }
                if (val.length < 6) return 'Minimum 6 characters';
                return null;
              },
            ),
            const SizedBox(height: 10),

            // Password Strength Indicator Bar
            _buildPasswordStrengthBar(passwordStrength),
            const SizedBox(height: 14),

            // Consent Checkbox
            Row(
              crossAxisAlignment: CrossAxisAlignment.center,
              children: [
                SizedBox(
                  width: 20,
                  height: 20,
                  child: Checkbox(
                    value: _agreedToTerms,
                    onChanged: (val) =>
                        setState(() => _agreedToTerms = val ?? true),
                    activeColor: AppColors.primaryAccent,
                    checkColor: AppColors.background,
                    side: const BorderSide(color: AppColors.textSecondary),
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(4),
                    ),
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: Wrap(
                    crossAxisAlignment: WrapCrossAlignment.center,
                    children: [
                      Text('I agree to the ',
                          style: AppTypography.bodySmall.copyWith(fontSize: 11)),
                      GestureDetector(
                        onTap: () => _showLegalDialog(
                            'Terms of Service', LegalDocs.termsOfService),
                        child: Text(
                          'Terms of Service',
                          style: AppTypography.bodySmall.copyWith(
                            color: AppColors.primaryAccent,
                            fontSize: 11,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                      ),
                      Text(' & ',
                          style: AppTypography.bodySmall.copyWith(fontSize: 11)),
                      GestureDetector(
                        onTap: () => _showLegalDialog(
                            'Privacy Policy', LegalDocs.privacyPolicy),
                        child: Text(
                          'Privacy Policy',
                          style: AppTypography.bodySmall.copyWith(
                            color: AppColors.primaryAccent,
                            fontSize: 11,
                            fontWeight: FontWeight.bold,
                          ),
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
            const SizedBox(height: 22),

            // Create Account Button
            SizedBox(
              height: 48,
              child: ElevatedButton(
                onPressed: isLoading ? null : _submitRegister,
                style: ElevatedButton.styleFrom(
                  backgroundColor: AppColors.primaryAccent,
                  foregroundColor: AppColors.background,
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(10),
                  ),
                  elevation: 0,
                ),
                child: isLoading
                    ? const SizedBox(
                        width: 20,
                        height: 20,
                        child: CircularProgressIndicator(
                          strokeWidth: 2,
                          color: AppColors.background,
                        ),
                      )
                    : Text(
                        'Create account',
                        style: AppTypography.bodyMedium.copyWith(
                          color: AppColors.background,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
              ),
            ),
            const SizedBox(height: 16),

            // "or" Divider
            Row(
              children: [
                const Expanded(child: Divider(color: AppColors.border)),
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 14),
                  child: Text(
                    'or',
                    style: AppTypography.bodySmall.copyWith(
                      color: AppColors.textSecondary,
                      fontSize: 12,
                    ),
                  ),
                ),
                const Expanded(child: Divider(color: AppColors.border)),
              ],
            ),
            const SizedBox(height: 16),

            // Continue with Google Button
            SizedBox(
              height: 46,
              child: ElevatedButton(
                onPressed: () {
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(
                      content: Text(
                          'Google OAuth: Connecting to Supabase Identity Provider...'),
                      backgroundColor: AppColors.primaryAccent,
                    ),
                  );
                },
                style: ElevatedButton.styleFrom(
                  backgroundColor: Colors.white,
                  foregroundColor: Colors.black87,
                  shape: RoundedRectangleBorder(
                    borderRadius: BorderRadius.circular(10),
                  ),
                  elevation: 0,
                ),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: [
                    Image.network(
                      'https://www.gstatic.com/firebasejs/ui/2.0.0/images/auth/google.svg',
                      width: 18,
                      height: 18,
                      errorBuilder: (context, error, stackTrace) => const Icon(
                        Icons.g_mobiledata_rounded,
                        color: Colors.red,
                        size: 24,
                      ),
                    ),
                    const SizedBox(width: 10),
                    Text(
                      'Sign up with Google',
                      style: AppTypography.bodyMedium.copyWith(
                        color: Colors.black87,
                        fontWeight: FontWeight.w600,
                        fontSize: 13,
                      ),
                    ),
                  ],
                ),
              ),
            ),
            const SizedBox(height: 16),

            // Already have an account? Sign in
            Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Text(
                  'Already have an account? ',
                  style: AppTypography.bodySmall.copyWith(
                    color: AppColors.textSecondary,
                  ),
                ),
                GestureDetector(
                  onTap: () => Navigator.of(context).pop(),
                  child: Text(
                    'Sign in',
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
    );
  }

  Widget _buildPasswordStrengthBar(PasswordStrength strength) {
    Color activeColor;
    String label;
    int filledSegments;

    switch (strength) {
      case PasswordStrength.weak:
        activeColor = AppColors.errorRed;
        label = 'Weak';
        filledSegments = 1;
        break;
      case PasswordStrength.medium:
        activeColor = Colors.amber;
        label = 'Medium';
        filledSegments = 2;
        break;
      case PasswordStrength.strong:
        activeColor = AppColors.successGreen;
        label = 'Strong';
        filledSegments = 4;
        break;
    }

    if (_passwordController.text.isEmpty) {
      filledSegments = 0;
      label = '';
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: List.generate(4, (index) {
            final isFilled = index < filledSegments;
            return Expanded(
              child: Container(
                height: 4,
                margin: EdgeInsets.only(right: index < 3 ? 4 : 0),
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(2),
                  color: isFilled
                      ? activeColor
                      : AppColors.surfaceElevated.withAlpha(120),
                ),
              ),
            );
          }),
        ),
        if (label.isNotEmpty) ...[
          const SizedBox(height: 4),
          Row(
            mainAxisAlignment: MainAxisAlignment.end,
            children: [
              Text(
                label,
                style: AppTypography.accentMono.copyWith(
                  fontSize: 10,
                  color: activeColor,
                  fontWeight: FontWeight.bold,
                ),
              ),
            ],
          ),
        ],
      ],
    );
  }
}
