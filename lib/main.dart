import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'core/theme/app_theme.dart';
import 'shell.dart';

void main() {
  runApp(
    const ProviderScope(
      child: OrganizaApp(),
    ),
  );
}

class OrganizaApp extends StatelessWidget {
  const OrganizaApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Organiza',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.darkTheme,
      darkTheme: AppTheme.darkTheme,
      themeMode: ThemeMode.dark,
      home: const AppShell(),
    );
  }
}
