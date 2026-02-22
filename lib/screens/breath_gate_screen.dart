import 'package:flutter/material.dart';
import 'dart:async';

/// A mindfulness screen shown before the app opens.
/// Forces the user to take a deep 8-second breath.
class BreathGateScreen extends StatefulWidget {
  final VoidCallback onFinish;

  const BreathGateScreen({super.key, required this.onFinish});

  @override
  State<BreathGateScreen> createState() => _BreathGateScreenState();
}

class _BreathGateScreenState extends State<BreathGateScreen>
    with TickerProviderStateMixin {
  late AnimationController _controller;
  late Animation<double> _scaleAnimation;
  int _secondsRemaining = 8;
  Timer? _timer;
  bool _canContinue = false;

  @override
  void initState() {
    super.initState();

    // 8-second breathing animation: 4s in, 4s out
    _controller = AnimationController(
      vsync: this,
      duration: const Duration(seconds: 4),
    );

    _scaleAnimation = Tween<double>(
      begin: 1.0,
      end: 1.5,
    ).animate(CurvedAnimation(parent: _controller, curve: Curves.easeInOut));

    _controller.repeat(reverse: true);

    _startCountdown();
  }

  void _startCountdown() {
    _timer = Timer.periodic(const Duration(seconds: 1), (timer) {
      if (_secondsRemaining > 0) {
        setState(() => _secondsRemaining--);
      } else {
        setState(() {
          _canContinue = true;
          _timer?.cancel();
        });
      }
    });
  }

  @override
  void dispose() {
    _controller.dispose();
    _timer?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      body: Center(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 40.0),
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const Text(
                'Are you sure you want to open Instagram?',
                textAlign: TextAlign.center,
                style: TextStyle(
                  color: Colors.white,
                  fontSize: 20,
                  fontWeight: FontWeight.w300,
                ),
              ),
              const SizedBox(height: 80),

              // Animated Breath Circle
              ScaleTransition(
                scale: _scaleAnimation,
                child: Container(
                  width: 100,
                  height: 100,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    boxShadow: [
                      BoxShadow(
                        color: Colors.blue.withValues(alpha: 0.3),
                        blurRadius: 30,
                        spreadRadius: 10,
                      ),
                    ],
                    gradient: const RadialGradient(
                      colors: [Colors.blue, Colors.black],
                    ),
                  ),
                ),
              ),

              const SizedBox(height: 80),

              Text(
                _canContinue
                    ? 'Breathed.'
                    : 'Take a deep breath for $_secondsRemaining seconds...',
                style: const TextStyle(
                  color: Colors.white70,
                  fontSize: 16,
                  fontStyle: FontStyle.italic,
                ),
              ),

              const SizedBox(height: 40),

              SizedBox(
                width: double.infinity,
                height: 50,
                child: ElevatedButton(
                  onPressed: _canContinue ? widget.onFinish : null,
                  style: ElevatedButton.styleFrom(
                    backgroundColor: Colors.white,
                    foregroundColor: Colors.black,
                    disabledBackgroundColor: Colors.white10,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(25),
                    ),
                  ),
                  child: const Text('Continue to Instagram'),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
