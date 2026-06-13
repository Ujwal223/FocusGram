import 'dart:convert';
import 'dart:math';

import 'package:flutter/foundation.dart';
import 'package:flutter_secure_storage/flutter_secure_storage.dart';
import 'package:local_auth/local_auth.dart';
import 'package:shared_preferences/shared_preferences.dart';

/// Manages app lock: PIN, biometrics, and two independent lock modes.
///
/// Modes (both can be on at the same time):
/// - **App-wide lock** — shown on cold start (before WebView) and after
///   background timeout.
/// - **Messages tab lock** — shown when navigating to Instagram DMs.
///
/// Both use the same PIN (stored in secure storage).
class AppLockService extends ChangeNotifier {
  static const _pinAppWideKey = 'app_lock_pin_app_wide';
  static const _pinMessagesKey = 'app_lock_pin_messages';
  static const _prefAppWide = 'app_lock_app_wide';
  static const _prefLockMessages = 'app_lock_lock_messages';
  static const _prefScramble = 'app_lock_scramble_keypad';
  static const _prefBio = 'app_lock_biometrics_enabled';
  static const _prefTimeout = 'app_lock_timeout_ms';

  final _secure = const FlutterSecureStorage();
  final _auth = LocalAuthentication();

  // ─── Mode toggles ──────────────────────────────────────────
  bool _lockAppWide = false; // locks the whole app on start / bg timeout
  bool _lockMessages = false; // locks only the DMs tab

  // ─── Settings ──────────────────────────────────────────────
  bool _scramble = false;
  bool _bioEnabled = false;
  int _timeoutMs = 120000; // 2 min
  bool _hasPin = false;

  // ─── Runtime state ─────────────────────────────────────────
  bool _isShowingLock = false; // true while lock screen is displayed
  DateTime? _bgAt;

  // ─── Getters ───────────────────────────────────────────────
  bool get lockAppWide => _lockAppWide;
  bool get lockMessages => _lockMessages;
  bool get isShowingLock => _isShowingLock;
  bool get scrambleKeypad => _scramble;
  bool get biometricsEnabled => _bioEnabled;
  bool get hasPin => _hasPin;
  bool get anyLockEnabled => _lockAppWide || _lockMessages;

  /// Whether the app-wide lock screen should show on cold start.
  bool get needsUnlockOnStart => _lockAppWide && _hasPin;

  /// Whether the messages tab lock is enabled and can function.
  bool get messagesLockReady => _lockMessages && _hasPin;

  // ─── Init ──────────────────────────────────────────────────
  Future<void> init() async {
    final p = await SharedPreferences.getInstance();
    _lockAppWide = p.getBool(_prefAppWide) ?? false;
    _lockMessages = p.getBool(_prefLockMessages) ?? false;
    _scramble = p.getBool(_prefScramble) ?? false;
    _bioEnabled = p.getBool(_prefBio) ?? true;
    _timeoutMs = p.getInt(_prefTimeout) ?? 120000;

    // Check if either PIN exists
    final hashA = await _secure.read(key: _pinAppWideKey);
    final hashM = await _secure.read(key: _pinMessagesKey);
    _hasPin =
        (hashA != null && hashA.isNotEmpty) ||
        (hashM != null && hashM.isNotEmpty);
  }

  // ─── PIN management ────────────────────────────────────────
  String _hash(String pin) => utf8
      .encode('fg_${pin}_salt26')
      .map((x) => x.toRadixString(16).padLeft(2, '0'))
      .join();

  /// Set PIN for a specific lock mode.
  Future<void> setPin(String pin, {required bool forAppWide}) async {
    final key = forAppWide ? _pinAppWideKey : _pinMessagesKey;
    await _secure.write(key: key, value: _hash(pin));
    _hasPin = true;
    notifyListeners();
  }

  /// Verify PIN for the given mode.
  Future<bool> verifyPin(String pin, {required bool forAppWide}) async {
    final key = forAppWide ? _pinAppWideKey : _pinMessagesKey;
    final stored = await _secure.read(key: key);
    return stored != null && stored == _hash(pin);
  }

  /// Check whether a specific mode has a PIN set.
  Future<bool> hasPinFor({required bool forAppWide}) async {
    final key = forAppWide ? _pinAppWideKey : _pinMessagesKey;
    final hash = await _secure.read(key: key);
    return hash != null && hash.isNotEmpty;
  }

  // ─── Toggles ───────────────────────────────────────────────
  Future<void> setLockAppWide(bool v) async {
    _lockAppWide = v;
    (await SharedPreferences.getInstance()).setBool(_prefAppWide, v);
    if (!v && !_isShowingLock) _isShowingLock = false;
    notifyListeners();
  }

  Future<void> setLockMessages(bool v) async {
    _lockMessages = v;
    (await SharedPreferences.getInstance()).setBool(_prefLockMessages, v);
    notifyListeners();
  }

  Future<void> setScrambleKeypad(bool v) async {
    _scramble = v;
    (await SharedPreferences.getInstance()).setBool(_prefScramble, v);
    notifyListeners();
  }

  Future<void> setBiometricsEnabled(bool v) async {
    _bioEnabled = v;
    (await SharedPreferences.getInstance()).setBool(_prefBio, v);
    notifyListeners();
  }

  // ─── Lock / Unlock lifecycle ───────────────────────────────

  /// Call when app-wide lock screen is opened.
  void onLockScreenShown() {
    _isShowingLock = true;
    notifyListeners();
  }

  /// Call after successful unlock (PIN or biometric).
  void onUnlocked() {
    _isShowingLock = false;
    _bgAt = null;
    notifyListeners();
  }

  /// Call when app goes to background.
  void onBackgrounded() {
    _bgAt = DateTime.now();
  }

  /// Whether the app-wide lock should trigger on resume.
  bool get shouldLockOnResume {
    if (!_lockAppWide || !_hasPin || _bgAt == null) return false;
    return DateTime.now().difference(_bgAt!).inMilliseconds >= _timeoutMs;
  }

  // ─── Biometrics ────────────────────────────────────────────
  Future<bool> isBiometricsAvailable() async {
    try {
      return await _auth.canCheckBiometrics || await _auth.isDeviceSupported();
    } catch (_) {
      return false;
    }
  }

  Future<bool> authenticateWithBiometrics() async {
    if (!_bioEnabled) return false;
    try {
      return await _auth.authenticate(
        localizedReason: 'Unlock FocusGram',
        options: const AuthenticationOptions(
          biometricOnly: false,
          stickyAuth: true,
        ),
      );
    } catch (_) {
      return false;
    }
  }

  // ─── Scrambled keypad ──────────────────────────────────────
  List<int> getScrambledDigits() {
    final d = List<int>.generate(10, (i) => i);
    d.shuffle(Random());
    return d;
  }
}
