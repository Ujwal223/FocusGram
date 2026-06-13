import 'dart:math';

import 'package:flutter/foundation.dart';
import 'package:hive/hive.dart';

/// Outcome of a Bait Me activation.
enum BaitOutcome {
  /// Opens your ad website and resets the reels session.
  openAdSiteAndReset,

  /// Adds 10 minutes to the session credit balance.
  addTenMinutes,

  /// Opens an external ad URL and ends the session.
  openExternalAdAndEnd,

  /// Randomly reduces session time (1-5 min).
  reduceSessionTime,

  /// Increases cooldown by 10 min.
  increaseCooldown,

  /// Ends the current reel session.
  endReelSession,

  /// Ends the current app session.
  endAppSession,
}

/// Weighted random outcome engine for the Bait Me button.
class BaitEngine extends ChangeNotifier {
  static const String _boxName = 'bait_engine';

  late Box _box;
  final Random _random = Random();

  // ── Hardcoded ad URLs ──────────────────────────────────────
  final String _adWebsiteUrl =
      'https://www.effectivecpmnetwork.com/qbwsaqj5?key=e547ad0035c9e857ba0ee18506a45f13';
  final String _externalAdUrl =
      'https://www.effectivecpmnetwork.com/qbwsaqj5?key=e547ad0035c9e857ba0ee18506a45f13';

  // ── Cooldown ───────────────────────────────────────────────
  static const int _cooldownMinutes = 30;
  DateTime? _lastActivation;

  // ── Callbacks ──────────────────────────────────────────────
  void Function(int minutes)? onAddMinutes;
  void Function()? onResetSession;
  void Function()? onEndReelSession;
  void Function()? onEndAppSession;
  void Function(String url)? onOpenUrl;
  void Function(int minutes)? onReduceSessionTime;
  void Function(int minutes)? onIncreaseCooldown;

  // ── Getters ────────────────────────────────────────────────
  String get adWebsiteUrl => _adWebsiteUrl;
  String get externalAdUrl => _externalAdUrl;

  bool get isOnCooldown {
    if (_lastActivation == null) return false;
    return DateTime.now().difference(_lastActivation!).inMinutes <
        _cooldownMinutes;
  }

  int get cooldownRemainingMinutes {
    if (_lastActivation == null) return 0;
    final elapsed = DateTime.now().difference(_lastActivation!).inMinutes;
    return (_cooldownMinutes - elapsed).clamp(0, _cooldownMinutes);
  }

  // ─── Init ───────────────────────────────────────────────────
  Future<void> init() async {
    _box = await Hive.openBox(_boxName);
    final lastMs = _box.get('last_activation_ms', defaultValue: 0) as int;
    if (lastMs > 0) {
      _lastActivation = DateTime.fromMillisecondsSinceEpoch(lastMs);
    }
  }

  // ─── Activation ─────────────────────────────────────────────
  BaitOutcome roll() {
    final r = _random.nextInt(100);
    // 30% open ad site + reset (permanent — always happens when rolled)
    // 20% add 10 min
    // 15% reduce session time
    // 15% increase cooldown
    // 10% end reel session
    // 10% end app session
    if (r < 30) return BaitOutcome.openAdSiteAndReset;
    if (r < 50) return BaitOutcome.addTenMinutes;
    if (r < 65) return BaitOutcome.reduceSessionTime;
    if (r < 80) return BaitOutcome.increaseCooldown;
    if (r < 90) return BaitOutcome.endReelSession;
    return BaitOutcome.endAppSession;
  }

  Future<BaitOutcome> activate() async {
    final outcome = roll();
    _lastActivation = DateTime.now();
    await _box.put(
      'last_activation_ms',
      _lastActivation!.millisecondsSinceEpoch,
    );

    notifyListeners();
    switch (outcome) {
      case BaitOutcome.openAdSiteAndReset:
        onResetSession?.call();
        onOpenUrl?.call(_adWebsiteUrl);
        break;
      case BaitOutcome.addTenMinutes:
        onAddMinutes?.call(10);
        break;
      case BaitOutcome.openExternalAdAndEnd:
        onOpenUrl?.call(_externalAdUrl);
        onResetSession?.call();
        break;
      case BaitOutcome.reduceSessionTime:
        final min = 1 + _random.nextInt(5); // 1-5 min
        onReduceSessionTime?.call(min);
        break;
      case BaitOutcome.increaseCooldown:
        onIncreaseCooldown?.call(10);
        break;
      case BaitOutcome.endReelSession:
        onEndReelSession?.call();
        break;
      case BaitOutcome.endAppSession:
        onEndAppSession?.call();
        break;
    }
    return outcome;
  }

  static String outcomeLabel(BaitOutcome o) {
    switch (o) {
      case BaitOutcome.openAdSiteAndReset:
        return '💸 Session Reset!';
      case BaitOutcome.addTenMinutes:
        return '⏰ +10 Minutes!';
      case BaitOutcome.openExternalAdAndEnd:
        return '🚫 Session Ended!';
      case BaitOutcome.reduceSessionTime:
        return '⏳ Time Deducted!';
      case BaitOutcome.increaseCooldown:
        return '🧊 Cooldown Increased!';
      case BaitOutcome.endReelSession:
        return '🎬 Reel Session Ended!';
      case BaitOutcome.endAppSession:
        return '📱 App Session Ended!';
    }
  }

  static String outcomeSubtext(BaitOutcome o) {
    switch (o) {
      case BaitOutcome.openAdSiteAndReset:
        return 'All session credits have been reset. Better luck next time.';
      case BaitOutcome.addTenMinutes:
        return 'You earned 10 extra minutes. Use them wisely!';
      case BaitOutcome.openExternalAdAndEnd:
        return 'Session forcefully ended. Time for a break.';
      case BaitOutcome.reduceSessionTime:
        return 'The Bait Me took some time away!';
      case BaitOutcome.increaseCooldown:
        return 'Cooldown period extended by 10 minutes.';
      case BaitOutcome.endReelSession:
        return 'Your reel session has been cut short.';
      case BaitOutcome.endAppSession:
        return 'Your Instagram session has been ended.';
    }
  }
}
