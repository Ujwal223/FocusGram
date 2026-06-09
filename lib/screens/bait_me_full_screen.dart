import 'dart:async';
import 'dart:math';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import 'package:url_launcher/url_launcher.dart';
import '../services/bait_engine.dart';
import '../services/credit_store.dart';
// import '../services/level_service.dart';  // unused
import '../services/session_manager.dart';

/// Full-screen Bait Me page with big spin animation.
class BaitMeFullScreen extends StatefulWidget {
  const BaitMeFullScreen({super.key});

  @override
  State<BaitMeFullScreen> createState() => _BaitMeFullScreenState();
}

class _BaitMeFullScreenState extends State<BaitMeFullScreen>
    with SingleTickerProviderStateMixin {
  bool _isSpinning = false;
  bool _done = false;
  BaitOutcome? _lastOutcome;
  late AnimationController _spinController;
  late Animation<double> _spinAnimation;

  @override
  void initState() {
    super.initState();
    _spinController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 1800),
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
    return Scaffold(
      backgroundColor: Colors.black,
      body: SafeArea(
        child: Center(
          child: Padding(
            padding: const EdgeInsets.all(32),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                const Spacer(),
                // Title
                Text(
                  _done ? '🎲 Result!' : '🎲 Bait Me',
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 28,
                    fontWeight: FontWeight.bold,
                  ),
                ),
                const SizedBox(height: 8),
                Text(
                  _done
                      ? BaitEngine.outcomeSubtext(_lastOutcome ?? BaitOutcome.addTenMinutes)
                      : 'Tap the button to test your luck!',
                  textAlign: TextAlign.center,
                  style: TextStyle(
                    color: Colors.white.withValues(alpha: 0.5),
                    fontSize: 15,
                  ),
                ),
                const Spacer(),

                // Spinning icon
                AnimatedBuilder(
                  animation: _spinAnimation,
                  builder: (context, child) {
                    return Transform.rotate(
                      angle: _isSpinning ? _spinAnimation.value * 2 * pi * 5 : 0,
                      child: child,
                    );
                  },
                  child: Container(
                    width: 120,
                    height: 120,
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      color: _done
                          ? Colors.green.withValues(alpha: 0.15)
                          : Colors.purpleAccent.withValues(alpha: 0.15),
                      border: Border.all(
                        color: _done ? Colors.greenAccent : Colors.purpleAccent,
                        width: 3,
                      ),
                    ),
                    child: Center(
                      child: Icon(
                        _done ? Icons.check_circle : Icons.casino_rounded,
                        color: _done ? Colors.greenAccent : Colors.purpleAccent,
                        size: 56,
                      ),
                    ),
                  ),
                ),

                const Spacer(),

                // Outcome description
                if (_done && _lastOutcome != null)
                  Container(
                    padding: const EdgeInsets.all(20),
                    decoration: BoxDecoration(
                      color: Colors.white.withValues(alpha: 0.05),
                      borderRadius: BorderRadius.circular(16),
                    ),
                    child: Column(
                      children: [
                        Text(
                          BaitEngine.outcomeLabel(_lastOutcome!),
                          style: TextStyle(
                            fontSize: 22,
                            fontWeight: FontWeight.bold,
                            color: _lastOutcome == BaitOutcome.addTenMinutes
                                ? Colors.greenAccent
                                : Colors.redAccent,
                          ),
                        ),
                        const SizedBox(height: 8),
                        Text(
                          BaitEngine.outcomeSubtext(_lastOutcome!),
                          textAlign: TextAlign.center,
                          style: TextStyle(
                            color: Colors.white.withValues(alpha: 0.6),
                            fontSize: 14,
                            height: 1.4,
                          ),
                        ),
                      ],
                    ),
                  ),

                const Spacer(flex: 2),

                // Big button
                SizedBox(
                  width: double.infinity,
                  height: 56,
                  child: ElevatedButton.icon(
                    onPressed: _isSpinning ? null : _onBaitMe,
                    style: ElevatedButton.styleFrom(
                      backgroundColor:
                          _done ? Colors.greenAccent : Colors.purpleAccent,
                      foregroundColor: Colors.black,
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(16),
                      ),
                      elevation: 4,
                    ),
                    icon: Icon(
                      _isSpinning
                          ? Icons.hourglass_top
                          : _done
                              ? Icons.check_circle
                              : Icons.casino_rounded,
                      size: 24,
                    ),
                    label: Text(
                      _isSpinning
                          ? 'Rolling…'
                          : _done
                              ? 'Done — Close'
                              : '🎲 Spin the Wheel!',
                      style: const TextStyle(
                        fontWeight: FontWeight.bold,
                        fontSize: 18,
                      ),
                    ),
                  ),
                ),

                if (!_done)
                  Padding(
                    padding: const EdgeInsets.only(top: 12),
                    child: TextButton(
                      onPressed: () => Navigator.pop(context),
                      child: Text('Not now',
                          style: TextStyle(
                              color: Colors.white.withValues(alpha: 0.3))),
                    ),
                  ),

                const Spacer(),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Future<void> _onBaitMe() async {
    HapticFeedback.mediumImpact();
    setState(() => _isSpinning = true);

    _spinController.forward(from: 0);
    await Future.delayed(const Duration(milliseconds: 1800));
    if (!mounted) return;

    final baitEngine = context.read<BaitEngine>();
    final creditStore = context.read<CreditStore>();
    final sessionManager = context.read<SessionManager>();

    baitEngine.onAddMinutes = (m) => creditStore.addBonusMinutes(m);
    baitEngine.onResetSession = () => creditStore.resetBalances();
    baitEngine.onReduceSessionTime = (m) {
      for (var i = 0; i < m; i++) creditStore.drainReelsMinute();
    };
    baitEngine.onEndReelSession = () => sessionManager.endSession();
    baitEngine.onEndAppSession = () => sessionManager.endAppSession();
    baitEngine.onOpenUrl = (url) async {
      final uri = Uri.tryParse(url);
      if (uri != null) await launchUrl(uri, mode: LaunchMode.externalApplication);
    };

    final outcome = await baitEngine.activate();
    if (!mounted) return;

    setState(() {
      _isSpinning = false;
      _done = true;
      _lastOutcome = outcome;
    });
    HapticFeedback.heavyImpact();
  }
}
