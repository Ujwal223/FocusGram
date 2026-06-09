import 'dart:async';
import 'dart:math';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import 'package:url_launcher/url_launcher.dart';
import '../services/bait_engine.dart';
import '../services/credit_store.dart';
import '../services/level_service.dart';
import '../services/session_manager.dart';

/// The Bait Me button widget.
///
/// Shows a gamble-themed button that triggers random outcomes.
/// Gated behind Level 3. Cooldown prevents spam.
class BaitMeButton extends StatefulWidget {
  const BaitMeButton({super.key});

  @override
  State<BaitMeButton> createState() => _BaitMeButtonState();
}

class _BaitMeButtonState extends State<BaitMeButton>
    with SingleTickerProviderStateMixin {
  bool _isSpinning = false;
  late AnimationController _spinController;
  late Animation<double> _spinAnimation;

  @override
  void initState() {
    super.initState();
    _spinController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1200),
    );
    _spinAnimation = Tween<double>(begin: 0, end: 1).animate(
      CurvedAnimation(parent: _spinController, curve: Curves.easeOutBack),
    );
  }

  @override
  void dispose() {
    _spinController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final levelService = context.watch<LevelService>();
    final baitEngine = context.read<BaitEngine>();
    final isUnlocked = levelService.isFeatureUnlocked(AppFeature.baitMe);

    if (!isUnlocked) {
      return const SizedBox.shrink();
    }

    final isDark = Theme.of(context).brightness == Brightness.dark;

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 4, vertical: 2),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          // The button
          SizedBox(
            width: 48,
            height: 48,
            child: Stack(
              children: [
                AnimatedBuilder(
                  animation: _spinAnimation,
                  builder: (context, child) {
                    return Transform.rotate(
                      angle: _isSpinning
                          ? _spinAnimation.value * 2 * pi * 3
                          : 0,
                      child: child,
                    );
                  },
                  child: Container(
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      color: baitEngine.isOnCooldown
                          ? Colors.grey.withValues(alpha: 0.3)
                          : Colors.purpleAccent.withValues(alpha: 0.2),
                      border: Border.all(
                        color: baitEngine.isOnCooldown
                            ? Colors.grey
                            : Colors.purpleAccent,
                        width: 2,
                      ),
                    ),
                    child: Material(
                      color: Colors.transparent,
                      child: InkWell(
                        borderRadius: BorderRadius.circular(24),
                        onTap: baitEngine.isOnCooldown
                            ? null
                            : _onBaitMe,
                        child: Center(
                          child: Icon(
                            Icons.casino_rounded,
                            color: baitEngine.isOnCooldown
                                ? Colors.grey
                                : Colors.purpleAccent,
                            size: 22,
                          ),
                        ),
                      ),
                    ),
                  ),
                ),
                // Cooldown badge
                if (baitEngine.isOnCooldown)
                  Positioned(
                    right: 0,
                    bottom: 0,
                    child: Container(
                      padding: const EdgeInsets.all(2),
                      decoration: BoxDecoration(
                        color: Colors.black87,
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: Text(
                        '${baitEngine.cooldownRemainingMinutes}m',
                        style: const TextStyle(
                          color: Colors.white,
                          fontSize: 8,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ),
                  ),
              ],
            ),
          ),
          const SizedBox(height: 2),
          Text(
            'Bait Me',
            style: TextStyle(
              fontSize: 9,
              color: isDark ? Colors.white60 : Colors.black54,
            ),
          ),
        ],
      ),
    );
  }

  Future<void> _onBaitMe() async {
    HapticFeedback.mediumImpact();

    setState(() {
      _isSpinning = true;
    });

    _spinController.forward(from: 0);

    // Wait for spin animation
    await Future.delayed(const Duration(milliseconds: 1200));

    if (!mounted) return;

    final baitEngine = context.read<BaitEngine>();
    final creditStore = context.read<CreditStore>();
    final sessionManager = context.read<SessionManager>();

    // Wire callbacks
    baitEngine.onAddMinutes = (minutes) {
      creditStore.addBonusMinutes(minutes);
      HapticFeedback.heavyImpact();
    };

    baitEngine.onResetSession = () {
      creditStore.resetBalances();
      sessionManager.endSession();
      HapticFeedback.heavyImpact();
    };

    baitEngine.onReduceSessionTime = (minutes) {
      // Deduct from reel credits
      for (var i = 0; i < minutes; i++) {
        creditStore.drainReelsMinute();
      }
      HapticFeedback.heavyImpact();
    };

    baitEngine.onIncreaseCooldown = (minutes) {
      // Increase cooldown by adding to the last session end time
      // Session manager handles cooldown via _lastSessionEnd
      HapticFeedback.heavyImpact();
    };

    baitEngine.onEndReelSession = () {
      sessionManager.endSession();
      HapticFeedback.heavyImpact();
    };

    baitEngine.onEndAppSession = () {
      sessionManager.endAppSession();
      HapticFeedback.heavyImpact();
    };

    baitEngine.onOpenUrl = (url) async {
      final uri = Uri.tryParse(url);
      if (uri != null) {
        await launchUrl(uri, mode: LaunchMode.externalApplication);
      }
    };

    // Activate
    final outcome = await baitEngine.activate();

    if (!mounted) return;

    setState(() {
      _isSpinning = false;
    });

    // Show result dialog
    _showOutcomeDialog(context, outcome);
  }

  void _showOutcomeDialog(BuildContext context, BaitOutcome outcome) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (ctx) => AlertDialog(
        backgroundColor: isDark ? const Color(0xFF1C1C1E) : Colors.white,
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
        content: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(
              BaitEngine.outcomeLabel(outcome),
              style: TextStyle(
                fontSize: 24,
                fontWeight: FontWeight.bold,
                color: outcome == BaitOutcome.addTenMinutes
                    ? Colors.greenAccent
                    : Colors.redAccent,
              ),
            ),
            const SizedBox(height: 12),
            Text(
              BaitEngine.outcomeSubtext(outcome),
              textAlign: TextAlign.center,
              style: TextStyle(
                fontSize: 14,
                color: isDark ? Colors.white70 : Colors.black87,
                height: 1.4,
              ),
            ),
          ],
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('OK'),
          ),
        ],
      ),
    );
  }
}
