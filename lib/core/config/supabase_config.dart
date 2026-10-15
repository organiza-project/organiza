import 'package:shared_preferences/shared_preferences.dart';

class SupabaseConfig {
  // Built-in default production endpoint and public anon key (protected via PostgreSQL RLS)
  static const String defaultUrl = String.fromEnvironment(
    'SUPABASE_URL',
    defaultValue: 'https://wpyuqgfpqpalnnohrixl.supabase.co',
  );
  static const String defaultAnonKey = String.fromEnvironment(
    'SUPABASE_ANON_KEY',
    defaultValue:
        'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6IndweXVxZ2ZwcXBhbG5ub2hyaXhsIiwicm9sZSI6ImFub24iLCJpYXQiOjE3OTE4MzY1ODMsImV4cCI6MjEwNzQxMjU4M30.4-AHfEgON5qWRovfZnMiQ6Nopf6C2-_oPBZi85Slfg8',
  );

  static const String _prefCustomUrlKey = 'custom_supabase_url';
  static const String _prefCustomAnonKey = 'custom_supabase_anon_key';

  static bool get isConfigured =>
      defaultUrl.startsWith('https://') &&
      !defaultUrl.contains('YOUR_PROJECT_ID');

  /// Resolves the active URL in order:
  /// 1. SharedPreferences custom server setting (if valid)
  /// 2. `--dart-define=SUPABASE_URL` (if non-empty)
  /// 3. `defaultUrl`
  static Future<String> getActiveUrl(SharedPreferences prefs) async {
    final customUrl = prefs.getString(_prefCustomUrlKey)?.trim();
    if (customUrl != null &&
        customUrl.isNotEmpty &&
        customUrl.startsWith('http')) {
      return customUrl;
    }
    const defineUrl = String.fromEnvironment('SUPABASE_URL');
    if (defineUrl.isNotEmpty) return defineUrl;
    return defaultUrl;
  }

  /// Resolves the active Anon Key in order:
  /// 1. SharedPreferences custom server setting (if valid)
  /// 2. `--dart-define=SUPABASE_ANON_KEY` (if non-empty)
  /// 3. `defaultAnonKey`
  static Future<String> getActiveAnonKey(SharedPreferences prefs) async {
    final customKey = prefs.getString(_prefCustomAnonKey)?.trim();
    if (customKey != null && customKey.isNotEmpty) {
      return customKey;
    }
    const defineKey = String.fromEnvironment('SUPABASE_ANON_KEY');
    if (defineKey.isNotEmpty) return defineKey;
    return defaultAnonKey;
  }

  static Future<void> saveCustomServer(
      SharedPreferences prefs, String url, String anonKey) async {
    await prefs.setString(_prefCustomUrlKey, url.trim());
    await prefs.setString(_prefCustomAnonKey, anonKey.trim());
  }

  static Future<void> resetToDefault(SharedPreferences prefs) async {
    await prefs.remove(_prefCustomUrlKey);
    await prefs.remove(_prefCustomAnonKey);
  }

  static bool hasCustomServer(SharedPreferences prefs) {
    final customUrl = prefs.getString(_prefCustomUrlKey);
    return customUrl != null && customUrl.trim().isNotEmpty;
  }

  static String? getSavedCustomUrl(SharedPreferences prefs) {
    return prefs.getString(_prefCustomUrlKey);
  }

  static String? getSavedCustomAnonKey(SharedPreferences prefs) {
    return prefs.getString(_prefCustomAnonKey);
  }
}
