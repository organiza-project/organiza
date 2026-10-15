import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:organiza/core/config/supabase_config.dart';

void main() {
  group('SupabaseConfig Tests', () {
    setUp(() {
      SharedPreferences.setMockInitialValues({});
    });

    test('resolves default production constants when no overrides are set', () async {
      final prefs = await SharedPreferences.getInstance();

      expect(SupabaseConfig.hasCustomServer(prefs), isFalse);
      expect(SupabaseConfig.isConfigured, isTrue);
      expect(SupabaseConfig.defaultUrl, contains('wpyuqgfpqpalnnohrixl.supabase.co'));

      final activeUrl = await SupabaseConfig.getActiveUrl(prefs);
      final activeKey = await SupabaseConfig.getActiveAnonKey(prefs);

      expect(activeUrl, equals(SupabaseConfig.defaultUrl));
      expect(activeKey, equals(SupabaseConfig.defaultAnonKey));
    });

    test('saves and resolves custom self-hosted server overrides', () async {
      final prefs = await SharedPreferences.getInstance();

      const testUrl = 'https://custom-instance.example.com';
      const testKey = 'custom-secret-anon-key-12345';

      await SupabaseConfig.saveCustomServer(prefs, testUrl, testKey);

      expect(SupabaseConfig.hasCustomServer(prefs), isTrue);
      expect(SupabaseConfig.getSavedCustomUrl(prefs), equals(testUrl));
      expect(SupabaseConfig.getSavedCustomAnonKey(prefs), equals(testKey));

      final resolvedUrl = await SupabaseConfig.getActiveUrl(prefs);
      final resolvedKey = await SupabaseConfig.getActiveAnonKey(prefs);

      expect(resolvedUrl, equals(testUrl));
      expect(resolvedKey, equals(testKey));
    });

    test('resets to default successfully clearing custom settings', () async {
      final prefs = await SharedPreferences.getInstance();

      await SupabaseConfig.saveCustomServer(
        prefs,
        'https://selfhosted.org',
        'sample-anon-key',
      );
      expect(SupabaseConfig.hasCustomServer(prefs), isTrue);

      await SupabaseConfig.resetToDefault(prefs);
      expect(SupabaseConfig.hasCustomServer(prefs), isFalse);

      final resolvedUrl = await SupabaseConfig.getActiveUrl(prefs);
      final resolvedKey = await SupabaseConfig.getActiveAnonKey(prefs);

      expect(resolvedUrl, equals(SupabaseConfig.defaultUrl));
      expect(resolvedKey, equals(SupabaseConfig.defaultAnonKey));
    });

    test('ignores invalid or empty custom URL and falls back to defaultUrl', () async {
      final prefs = await SharedPreferences.getInstance();

      await SupabaseConfig.saveCustomServer(prefs, 'not-a-valid-url', '');
      final resolvedUrl = await SupabaseConfig.getActiveUrl(prefs);
      final resolvedKey = await SupabaseConfig.getActiveAnonKey(prefs);

      expect(resolvedUrl, equals(SupabaseConfig.defaultUrl));
      expect(resolvedKey, equals(SupabaseConfig.defaultAnonKey));
    });
  });
}
