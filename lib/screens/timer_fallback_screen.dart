import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

/// A 15-second timer that acts as the last-resort fallback
/// when both AdMob and Adsterra fail to serve an ad.
///
/// Shows a digital wellness quote while the user waits.
/// After the timer, they earn the same reward.
class TimerFallbackScreen extends StatefulWidget {
  final String sessionType;
  final int requiredSeconds;

  const TimerFallbackScreen({
    super.key,
    required this.sessionType,
    this.requiredSeconds = 15,
  });

  @override
  State<TimerFallbackScreen> createState() => _TimerFallbackScreenState();
}

class _TimerFallbackScreenState extends State<TimerFallbackScreen> {
  int _remaining = 0;
  Timer? _timer;
  int _quoteIndex = 0;

  static const _quotes = [
    '"The secret of getting ahead is getting started." — Mark Twain',
    '"Focus on being productive instead of busy." — Tim Ferriss',
    '"Almost everything will work if you unplug it for a few minutes." — Ann Lamott',
    '"The key is not to prioritize what\'s on your schedule, but to schedule your priorities." — Stephen Covey',
    '"Your mind is for having ideas, not holding them." — David Allen',
    '"Simplicity is the ultimate sophistication." — Leonardo da Vinci',
    '"The ability to simplify means to eliminate the unnecessary." — Hans Hofmann',
    '"In the midst of chaos, there is also opportunity." — Sun Tzu',
  ];

  @override
  void initState() {
    super.initState();
    _remaining = widget.requiredSeconds;
    _quoteIndex = DateTime.now().millisecondsSinceEpoch % _quotes.length;
    _startTimer();
  }

  @override
  void dispose() {
    _timer?.cancel();
    super.dispose();
  }

  void _startTimer() {
    _timer = Timer.periodic(const Duration(seconds: 1), (_) {
      if (!mounted) return;
      setState(() {
        if (_remaining > 0) {
          _remaining--;
        } else {
          _timer?.cancel();
          HapticFeedback.heavyImpact();
        }
      });
    });
  }

  @override
  Widget build(BuildContext context) {
    final done = _remaining <= 0;

    return Scaffold(
      backgroundColor: Colors.black,
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 32),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.center,
            children: [
              const Spacer(flex: 2),

              // Icon
              Container(
                width: 72,
                height: 72,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  color: Colors.green.withValues(alpha: 0.1),
                  border: Border.all(
                    color: Colors.green.withValues(alpha: 0.3),
                    width: 2,
                  ),
                ),
                child: Icon(
                  done ? Icons.check_circle : Icons.timer_outlined,
                  color: done ? Colors.greenAccent : Colors.green,
                  size: 36,
                ),
              ),

              const SizedBox(height: 28),

              // Timer
              Text(
                done ? 'Done!' : '$_remaining',
                style: TextStyle(
                  color: done ? Colors.greenAccent : Colors.white,
                  fontSize: 56,
                  fontWeight: FontWeight.bold,
                  fontFeatures: const [FontFeature.tabularFigures()],
                ),
              ),

              const SizedBox(height: 8),
              Text(
                done
                    ? 'You earned ${widget.sessionType == 'reels' ? 'reel' : 'Instagram'} time'
                    : 'Please wait while we prepare your reward',
                style: TextStyle(
                  color: Colors.white.withValues(alpha: 0.5),
                  fontSize: 14,
                ),
              ),

              const SizedBox(height: 40),

              // Quote
              Container(
                padding: const EdgeInsets.all(20),
                decoration: BoxDecoration(
                  color: Colors.white.withValues(alpha: 0.05),
                  borderRadius: BorderRadius.circular(16),
                  border: Border.all(
                    color: Colors.white.withValues(alpha: 0.08),
                  ),
                ),
                child: Text(
                  _quotes[_quoteIndex],
                  textAlign: TextAlign.center,
                  style: TextStyle(
                    color: Colors.white.withValues(alpha: 0.6),
                    fontSize: 15,
                    height: 1.5,
                    fontStyle: FontStyle.italic,
                  ),
                ),
              ),

              const Spacer(flex: 1),

              // Continue button
              SizedBox(
                width: double.infinity,
                height: 54,
                child: ElevatedButton.icon(
                  onPressed: done ? () => Navigator.pop(context, true) : null,
                  style: ElevatedButton.styleFrom(
                    backgroundColor: done ? Colors.greenAccent : Colors.grey,
                    foregroundColor: done ? Colors.black : Colors.white38,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(14),
                    ),
                    elevation: 0,
                  ),
                  icon: Icon(
                    done ? Icons.check_circle : Icons.hourglass_empty,
                    size: 22,
                  ),
                  label: Text(
                    done
                        ? 'Continue & Earn Reward'
                        : 'Wait $_remaining seconds',
                    style: const TextStyle(
                      fontWeight: FontWeight.w600,
                      fontSize: 16,
                    ),
                  ),
                ),
              ),

              const SizedBox(height: 16),
              Text(
                'No ad available — timer reward instead',
                style: TextStyle(
                  color: Colors.white.withValues(alpha: 0.2),
                  fontSize: 11,
                ),
              ),

              const Spacer(flex: 1),
            ],
          ),
        ),
      ),
    );
  }
}
