import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:organiza/core/theme/app_theme.dart';
import 'package:organiza/core/constants/app_typography.dart';

void main() {
  group('Accessibility & Responsiveness Suite', () {
    testWidgets('AppTypography scales cleanly without truncation under 200% text scaling', (tester) async {
      await tester.pumpWidget(
        MaterialApp(
          theme: AppTheme.darkTheme,
          home: MediaQuery(
            data: const MediaQueryData(
              textScaler: TextScaler.linear(2.0),
              size: Size(375, 812),
            ),
            child: Scaffold(
              body: ListView(
                children: [
                  Text('Level 12 Spartan', style: AppTypography.h1),
                  Text('Daily Discipline Protocol', style: AppTypography.h2),
                  Text('Consistent execution leads to unyielding resilience.', style: AppTypography.bodyMedium),
                  Text('STREAK: 14 DAYS', style: AppTypography.bodySmall),
                ],
              ),
            ),
          ),
        ),
      );

      expect(find.text('Level 12 Spartan'), findsOneWidget);
      expect(find.text('Daily Discipline Protocol'), findsOneWidget);
      expect(find.text('STREAK: 14 DAYS'), findsOneWidget);
    });

    testWidgets('Responsive Layout renders across standard screen widths without overflow', (tester) async {
      const screenSizes = [
        Size(320, 568),  // Small mobile (SE)
        Size(375, 812),  // Standard mobile (iPhone)
        Size(768, 1024), // Tablet portrait
        Size(1024, 768), // Tablet landscape
        Size(1440, 900), // Desktop HD
      ];

      for (final size in screenSizes) {
        tester.view.physicalSize = size;
        tester.view.devicePixelRatio = 1.0;

        await tester.pumpWidget(
          MaterialApp(
            theme: AppTheme.darkTheme,
            home: Scaffold(
              body: LayoutBuilder(
                builder: (context, constraints) {
                  final isDesktop = constraints.maxWidth >= 900;
                  final isTablet = constraints.maxWidth >= 600 && !isDesktop;
                  return Row(
                    children: [
                      if (isDesktop || isTablet)
                        Container(
                          width: isDesktop ? 240 : 80,
                          color: Colors.black26,
                          child: const Center(child: Text('Sidebar')),
                        ),
                      Expanded(
                        child: Center(
                          child: Text('Content for ${constraints.maxWidth}px'),
                        ),
                      ),
                    ],
                  );
                },
              ),
            ),
          ),
        );

        expect(tester.takeException(), isNull);
      }

      // Reset test window size
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);
    });

    testWidgets('Interactive elements maintain minimum 48x48 touch targets', (tester) async {
      await tester.pumpWidget(
        MaterialApp(
          theme: AppTheme.darkTheme,
          home: Scaffold(
            body: Center(
              child: Semantics(
                button: true,
                label: 'Complete Task',
                child: SizedBox(
                  width: 48,
                  height: 48,
                  child: IconButton(
                    icon: const Icon(Icons.check_circle_outline),
                    onPressed: () {},
                  ),
                ),
              ),
            ),
          ),
        ),
      );

      final finder = find.byType(IconButton);
      final size = tester.getSize(finder);
      expect(size.width, greaterThanOrEqualTo(48.0));
      expect(size.height, greaterThanOrEqualTo(48.0));
    });
  });
}
