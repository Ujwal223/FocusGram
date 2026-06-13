import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import '../services/credit_store.dart';
import '../services/level_service.dart';
import 'adsterra_ad_screen.dart';
import 'timer_fallback_screen.dart';
import '../widgets/native_ad_banner.dart';

/// Shown before a reel or Instagram session when credits are zero
/// and Effort Friction Mode is enabled.
///
/// Fallback chain: Adsterra Social Bar (WebView) → Timer fallback.
class EffortFrictionGate extends StatefulWidget {
  final String sessionType; // 'reels' or 'insta'
  final VoidCallback onProceed;
  final VoidCallback? onCancel;

  const EffortFrictionGate({
    super.key,
    required this.sessionType,
    required this.onProceed,
    this.onCancel,
  });

  @override
  State<EffortFrictionGate> createState() => _EffortFrictionGateState();
}

class _EffortFrictionGateState extends State<EffortFrictionGate> {
  bool _isWorking = false;
  String _status = '';

  @override
  Widget build(BuildContext context) {
    final creditStore = context.watch<CreditStore>();
    final isReels = widget.sessionType == 'reels';
    final credits = isReels
        ? creditStore.reelsMinutes
        : creditStore.instaMinutes;

    return Scaffold(
      backgroundColor: Colors.black,
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 28),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.center,
            children: [
              const Spacer(flex: 2),

              // Icon
              Container(
                width: 80,
                height: 80,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  gradient: LinearGradient(
                    colors: [Colors.orange.shade800, Colors.orange.shade500],
                    begin: Alignment.topLeft,
                    end: Alignment.bottomRight,
                  ),
                  boxShadow: [
                    BoxShadow(
                      color: Colors.orange.withValues(alpha: 0.3),
                      blurRadius: 24,
                      spreadRadius: 4,
                    ),
                  ],
                ),
                child: const Icon(
                  Icons.play_circle_fill_rounded,
                  color: Colors.white,
                  size: 40,
                ),
              ),

              const SizedBox(height: 28),

              Text(
                isReels ? 'Earn Reels Time' : 'Earn Instagram Time',
                style: const TextStyle(
                  color: Colors.white,
                  fontSize: 24,
                  fontWeight: FontWeight.bold,
                ),
              ),
              const SizedBox(height: 12),
              Text(
                'Watch a short ad to earn ${CreditStore.minutesPerAd} minutes '
                'of ${isReels ? 'reel' : 'Instagram'} time.',
                textAlign: TextAlign.center,
                style: TextStyle(
                  color: Colors.white.withValues(alpha: 0.6),
                  fontSize: 15,
                  height: 1.5,
                ),
              ),

              const SizedBox(height: 32),

              // Credit balance display
              if (credits > 0)
                Container(
                  padding: const EdgeInsets.all(16),
                  decoration: BoxDecoration(
                    color: Colors.green.withValues(alpha: 0.1),
                    borderRadius: BorderRadius.circular(14),
                    border: Border.all(
                      color: Colors.green.withValues(alpha: 0.2),
                    ),
                  ),
                  child: Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      const Icon(
                        Icons.access_time,
                        color: Colors.greenAccent,
                        size: 20,
                      ),
                      const SizedBox(width: 8),
                      Text(
                        'You have $credits min remaining',
                        style: const TextStyle(
                          color: Colors.greenAccent,
                          fontSize: 16,
                          fontWeight: FontWeight.w600,
                        ),
                      ),
                    ],
                  ),
                ),

              const SizedBox(height: 20),

              // Status message
              if (_status.isNotEmpty)
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: Colors.blue.withValues(alpha: 0.1),
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: Row(
                    children: [
                      const Icon(
                        Icons.info_outline,
                        color: Colors.blueAccent,
                        size: 18,
                      ),
                      const SizedBox(width: 8),
                      Expanded(
                        child: Text(
                          _status,
                          style: const TextStyle(
                            color: Colors.blueAccent,
                            fontSize: 13,
                          ),
                        ),
                      ),
                    ],
                  ),
                ),

              const SizedBox(height: 12),

              // Watch ad button
              SizedBox(
                width: double.infinity,
                height: 54,
                child: ElevatedButton.icon(
                  onPressed: _isWorking ? null : _startFallbackChain,
                  style: ElevatedButton.styleFrom(
                    backgroundColor: Colors.orange,
                    foregroundColor: Colors.white,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(14),
                    ),
                    elevation: 0,
                  ),
                  icon: _isWorking
                      ? const SizedBox(
                          width: 20,
                          height: 20,
                          child: CircularProgressIndicator(
                            strokeWidth: 2,
                            color: Colors.white,
                          ),
                        )
                      : const Icon(Icons.play_arrow_rounded, size: 22),
                  label: Text(
                    _isWorking
                        ? 'Working…'
                        : 'Watch Ad (+${CreditStore.minutesPerAd} min)',
                    style: const TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ),
              ),

              const SizedBox(height: 12),

              // Proceed button
              if (credits > 0)
                SizedBox(
                  width: double.infinity,
                  height: 48,
                  child: OutlinedButton(
                    onPressed: widget.onProceed,
                    style: OutlinedButton.styleFrom(
                      foregroundColor: Colors.white70,
                      side: BorderSide(
                        color: Colors.white.withValues(alpha: 0.2),
                      ),
                      shape: RoundedRectangleBorder(
                        borderRadius: BorderRadius.circular(14),
                      ),
                    ),
                    child: const Text('Proceed with earned time'),
                  ),
                ),

              const SizedBox(height: 16),

              // Cancel
              TextButton(
                onPressed: widget.onCancel ?? () => Navigator.pop(context),
                child: Text(
                  credits > 0 ? 'Skip for now' : 'Not now',
                  style: TextStyle(color: Colors.white.withValues(alpha: 0.4)),
                ),
              ),

              const Spacer(flex: 1),
              Text(
                'Ads by Adsterra',
                style: TextStyle(
                  color: Colors.white.withValues(alpha: 0.15),
                  fontSize: 10,
                ),
              ),
              const SizedBox(height: 4),
              // Native banner ad at bottom
              const NativeAdBanner(height: 50),
              const SizedBox(height: 8),
            ],
          ),
        ),
      ),
    );
  }

  // ── Fallback Chain ─────────────────────────────────────────

  Future<void> _startFallbackChain() async {
    setState(() => _isWorking = true);

    // Tier 1: Adsterra ad (full-screen WebView)
    setState(() => _status = '');

    if (mounted) {
      final adsterraResult = await Navigator.push<bool>(
        context,
        MaterialPageRoute(
          builder: (_) => AdsterraAdScreen(
            sessionType: widget.sessionType,
            requiredSeconds: 15,
          ),
        ),
      );

      if (adsterraResult == true && mounted) {
        _grantReward();
        setState(() {
          _isWorking = false;
          _status = '';
        });
        return;
      }

      if (!mounted) return;
    }

    // Tier 2: Timer fallback (always works)
    setState(() => _status = 'Using timer fallback…');

    if (mounted) {
      final timerResult = await Navigator.push<bool>(
        context,
        MaterialPageRoute(
          builder: (_) => TimerFallbackScreen(
            sessionType: widget.sessionType,
            requiredSeconds: 15,
          ),
        ),
      );

      if (timerResult == true && mounted) {
        _grantReward();
      }
    }

    if (mounted) {
      setState(() {
        _isWorking = false;
        _status = '';
      });
    }
  }

  void _grantReward() {
    final creditStore = context.read<CreditStore>();
    final levelService = context.read<LevelService>();

    if (widget.sessionType == 'reels') {
      creditStore.addReelsMinutes();
    } else {
      creditStore.addInstaMinutes();
    }
    levelService.addXpForAd();
    HapticFeedback.heavyImpact();
  }
}
