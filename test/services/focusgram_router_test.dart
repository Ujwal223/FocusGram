// test/services/focusgram_router_test.dart
//
// Tests for FocusGramRouter — the lightweight cross-widget URL notifier.
//
// Run with: flutter test test/services/focusgram_router_test.dart

import 'package:flutter_test/flutter_test.dart';
import 'package:focusgram/services/focusgram_router.dart';

void main() {
  // Reset between tests so state does not bleed across
  tearDown(() {
    FocusGramRouter.pendingUrl.value = null;
  });

  group('FocusGramRouter.pendingUrl', () {
    test('initial value is null', () {
      expect(FocusGramRouter.pendingUrl.value, isNull);
    });

    test('can be set to a URL string', () {
      FocusGramRouter.pendingUrl.value =
          'https://www.instagram.com/accounts/settings/';
      expect(
        FocusGramRouter.pendingUrl.value,
        'https://www.instagram.com/accounts/settings/',
      );
    });

    test('can be cleared back to null', () {
      FocusGramRouter.pendingUrl.value = 'https://www.instagram.com/';
      FocusGramRouter.pendingUrl.value = null;
      expect(FocusGramRouter.pendingUrl.value, isNull);
    });

    test('notifies listeners when value changes', () {
      bool notified = false;
      void listener() => notified = true;
      FocusGramRouter.pendingUrl.addListener(listener);

      FocusGramRouter.pendingUrl.value = 'https://www.instagram.com/';

      expect(notified, isTrue);
      FocusGramRouter.pendingUrl.removeListener(listener);
    });

    test('does NOT notify when value is set to same value', () {
      FocusGramRouter.pendingUrl.value = 'https://x.com/';
      int callCount = 0;
      void listener() => callCount++;
      FocusGramRouter.pendingUrl.addListener(listener);

      // Setting to the exact same value should not notify
      FocusGramRouter.pendingUrl.value = 'https://x.com/';
      expect(callCount, 0);

      FocusGramRouter.pendingUrl.removeListener(listener);
    });

    test('is a singleton — same instance across multiple accesses', () {
      final a = FocusGramRouter.pendingUrl;
      final b = FocusGramRouter.pendingUrl;
      expect(identical(a, b), isTrue);
    });
  });
}
