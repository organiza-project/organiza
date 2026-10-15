// Template configuration for open-source contributors and self-hosters.
// Copy or adapt these settings to your own Supabase project.

class SupabaseConfigExample {
  static const String projectUrl = 'https://YOUR_PROJECT_ID.supabase.co';
  static const String anonKey = 'YOUR_ANON_KEY_HERE';

  // Instructions for self-hosters:
  // 1. Pass --dart-define=SUPABASE_URL="https://your-domain.com"
  //    and --dart-define=SUPABASE_ANON_KEY="your-anon-key" during build/run.
  // 2. Or configure via the in-app "Self-Hosted / Custom Server" dialog on the login/profile screen.
}
