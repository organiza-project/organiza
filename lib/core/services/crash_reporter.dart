import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Privacy-preserving crash and error reporting.
/// Scrubs PII (emails, JWTs, bearer tokens, file paths) and respects user telemetry consent.
class CrashReporter {
  static const String _prefKeyTelemetryConsent = 'telemetry_error_reporting_opt_in';
  static bool _optedIn = false;

  /// Initializes error hooks and loads consent state
  static Future<void> initialize() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      _optedIn = prefs.getBool(_prefKeyTelemetryConsent) ?? false;
    } catch (_) {
      _optedIn = false;
    }

    // Flutter UI framework errors
    FlutterError.onError = (FlutterErrorDetails details) {
      recordFlutterError(details);
    };

    // Uncaught asynchronous platform/Dart errors
    PlatformDispatcher.instance.onError = (Object error, StackTrace stack) {
      recordError(error, stack, isFatal: true);
      return true;
    };
  }

  static bool get isOptedIn => _optedIn;

  static Future<void> setOptIn(bool enabled) async {
    _optedIn = enabled;
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setBool(_prefKeyTelemetryConsent, enabled);
    } catch (_) {}
  }

  static void recordFlutterError(FlutterErrorDetails details) {
    if (kDebugMode) {
      FlutterError.dumpErrorToConsole(details);
    }
    recordError(
      details.exception,
      details.stack,
      context: details.context?.toDescription(),
      isFatal: false,
    );
  }

  static void recordError(
    Object error,
    StackTrace? stack, {
    String? context,
    bool isFatal = false,
  }) {
    final sanitizedMessage = _sanitize(error.toString());
    final sanitizedContext = context != null ? _sanitize(context) : null;

    if (kDebugMode) {
      debugPrint('[CrashReporter] ${isFatal ? "FATAL" : "ERROR"}: $sanitizedMessage');
      if (sanitizedContext != null) debugPrint('  Context: $sanitizedContext');
      if (stack != null) debugPrint('  Stack: $stack');
    }

    // If opted in, report sanitized diagnostics (no PII, no tokens)
    if (_optedIn) {
      // Diagnostic telemetry can be dispatched here to a self-hosted Sentry / PostHog
    }
  }

  /// Scrubs emails, JWTs, Bearer tokens, and local file paths from diagnostics
  static String _sanitize(String raw) {
    var text = raw;

    // Email addresses
    text = text.replaceAll(
      RegExp(r'[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}'),
      '[REDACTED_EMAIL]',
    );

    // JWT / Bearer tokens
    text = text.replaceAll(
      RegExp(r'eyJ[a-zA-Z0-9_-]{10,}\.[a-zA-Z0-9_-]{10,}\.[a-zA-Z0-9_-]{10,}'),
      '[REDACTED_JWT]',
    );
    text = text.replaceAll(
      RegExp(r'Bearer\s+[a-zA-Z0-9_\-\.~]+', caseSensitive: false),
      'Bearer [REDACTED_TOKEN]',
    );

    // File paths containing username directories (/home/username or C:\Users\username)
    text = text.replaceAll(
      RegExp(r'(/home/|/Users/|[A-Za-z]:\\Users\\)[^/\\\s]+'),
      r'$1[REDACTED_USER]',
    );

    return text;
  }
}
