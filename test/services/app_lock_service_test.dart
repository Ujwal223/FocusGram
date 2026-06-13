import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:focusgram/services/app_lock_service.dart';

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  setUp(() {
    SharedPreferences.setMockInitialValues({});
  });

  group('AppLockService — PIN verification', () {
    test('verifyPin returns true for correct PIN', () async {
      final service = AppLockService();
      await service.init();

      await service.setPin('1234', forAppWide: true);

      final valid = await service.verifyPin('1234', forAppWide: true);
      expect(valid, isTrue);
    });

    test('verifyPin returns false for wrong PIN', () async {
      final service = AppLockService();
      await service.init();

      await service.setPin('1234', forAppWide: true);

      final valid = await service.verifyPin('0000', forAppWide: true);
      expect(valid, isFalse);
    });

    test('verifyPin with forAppWide:false checks messages PIN', () async {
      final service = AppLockService();
      await service.init();

      await service.setPin('5678', forAppWide: false);

      final valid = await service.verifyPin('5678', forAppWide: false);
      expect(valid, isTrue);
    });

    test('onUnlocked resets lock state', () async {
      final service = AppLockService();
      await service.init();

      await service.setPin('1234', forAppWide: true);
      service.onBackgrounded();
      expect(service.shouldLockOnResume, isTrue);

      service.onUnlocked();
      expect(service.shouldLockOnResume, isFalse);
      expect(service.isShowingLock, isFalse);
    });
  });

  group('AppLockService — PIN management', () {
    test('hasPin returns true after PIN is set', () async {
      final service = AppLockService();
      await service.init();

      expect(service.hasPin, isFalse);

      await service.setPin('1234', forAppWide: true);
      expect(service.hasPin, isTrue);
    });

    test('verifyPin returns false when no PIN is set', () async {
      final service = AppLockService();
      await service.init();

      final valid = await service.verifyPin('1234', forAppWide: true);
      expect(valid, isFalse);
    });
  });
}
