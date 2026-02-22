import 'dart:async';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../services/session_manager.dart';

/// Blocking screen shown when the user tries to reopen the app too soon
/// after their last session ended. Shows a countdown and a motivational quote.
class CooldownGateScreen extends StatefulWidget {
  const CooldownGateScreen({super.key});

  @override
  State<CooldownGateScreen> createState() => _CooldownGateScreenState();
}

class _CooldownGateScreenState extends State<CooldownGateScreen> {
  Timer? _timer;
  static const List<String> _quotes = [
    '"The discipline you show offline\nshapes the clarity you experience online."',
    '"Every moment away from the screen\nis a moment given back to yourself."',
    '"Boredom is the birthplace of creativity.\nLet it breathe."',
    '"Your attention is your most valuable asset.\nSpend it wisely."',
    '"Presence is a gift you give yourself first."',
    '"Rest is not wasted time.\nIt is the foundation of focused action."',
  ];
  late final String _quote;

  @override
  void initState() {
    super.initState();
    _quote = _quotes[DateTime.now().second % _quotes.length];
    _timer = Timer.periodic(const Duration(seconds: 1), (_) {
      if (mounted) setState(() {});
    });
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final sm = context.watch<SessionManager>();
    final remaining = sm.appOpenCooldownRemainingSeconds;
    final minutes = remaining ~/ 60;
    final seconds = remaining % 60;

    // If cooldown expired, pop this gate
    if (!sm.isAppOpenCooldownActive) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) Navigator.of(context).maybePop();
      });
    }

    return Scaffold(
      backgroundColor: Colors.black,
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 32, vertical: 40),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                crossAxisAlignment: CrossAxisAlignment.center,
                children: [
                  // Icon
                  Container(
                    width: 80,
                    height: 80,
                    decoration: BoxDecoration(
                      shape: BoxShape.circle,
                      color: Colors.orange.withValues(alpha: 0.12),
                      border: Border.all(
                        color: Colors.orangeAccent.withValues(alpha: 0.4),
                        width: 1.5,
                      ),
                    ),
                    child: const Icon(
                      Icons.hourglass_top_rounded,
                      color: Colors.orangeAccent,
                      size: 38,
                    ),
                  ),

                  const SizedBox(height: 32),
                  const Text(
                    'Take a Break',
                    textAlign: TextAlign.center,
                    style: TextStyle(
                      color: Colors.white,
                      fontSize: 28,
                      fontWeight: FontWeight.bold,
                      letterSpacing: -0.5,
                    ),
                  ),

                  const SizedBox(height: 12),
                  const Text(
                    'Your session has ended.\nCome back when the timer expires.',
                    textAlign: TextAlign.center,
                    style: TextStyle(
                      color: Colors.white54,
                      fontSize: 15,
                      height: 1.5,
                    ),
                  ),

                  const SizedBox(height: 48),

                  // Countdown
                  Container(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 32,
                      vertical: 20,
                    ),
                    decoration: BoxDecoration(
                      color: Colors.orange.withValues(alpha: 0.08),
                      borderRadius: BorderRadius.circular(20),
                      border: Border.all(
                        color: Colors.orangeAccent.withValues(alpha: 0.25),
                        width: 1,
                      ),
                    ),
                    child: Column(
                      children: [
                        const Text(
                          'Return in',
                          style: TextStyle(
                            color: Colors.white38,
                            fontSize: 13,
                            letterSpacing: 1.2,
                          ),
                        ),
                        const SizedBox(height: 8),
                        Text(
                          '${minutes.toString().padLeft(2, '0')}:${seconds.toString().padLeft(2, '0')}',
                          style: const TextStyle(
                            color: Colors.orangeAccent,
                            fontSize: 52,
                            fontWeight: FontWeight.w200,
                            letterSpacing: 4,
                          ),
                        ),
                      ],
                    ),
                  ),

                  const SizedBox(height: 60),

                  // Quote
                  Text(
                    _quote,
                    textAlign: TextAlign.center,
                    style: const TextStyle(
                      color: Colors.white30,
                      fontSize: 13,
                      height: 1.7,
                      fontStyle: FontStyle.italic,
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}
