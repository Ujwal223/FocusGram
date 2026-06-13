import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../services/level_service.dart';
import '../services/settings_service.dart';
import '../services/credit_store.dart';
import 'adsterra_ad_screen.dart';

/// Displays current level, XP progress, and locked/preview features.
class LevelPanelScreen extends StatelessWidget {
  const LevelPanelScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final levelService = context.watch<LevelService>();
    final settings = context.watch<SettingsService>();
    final isDark = settings.isDarkMode;

    return Scaffold(
      appBar: AppBar(
        title: const Text(
          'Your Journey',
          style: TextStyle(fontSize: 17, fontWeight: FontWeight.w600),
        ),
        centerTitle: true,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back_ios_new, size: 18),
          onPressed: () => Navigator.pop(context),
        ),
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          // ── Level Header Card ──────────────────────────────
          Container(
            padding: const EdgeInsets.all(24),
            decoration: BoxDecoration(
              gradient: LinearGradient(
                colors: _levelColors(levelService.level, isDark),
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
              ),
              borderRadius: BorderRadius.circular(20),
              boxShadow: [
                BoxShadow(
                  color: _levelColors(
                    levelService.level,
                    isDark,
                  )[0].withValues(alpha: 0.3),
                  blurRadius: 20,
                  offset: const Offset(0, 8),
                ),
              ],
            ),
            child: Column(
              children: [
                // Level badge
                Container(
                  width: 72,
                  height: 72,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    color: Colors.white.withValues(alpha: 0.2),
                    border: Border.all(
                      color: Colors.white.withValues(alpha: 0.4),
                      width: 3,
                    ),
                  ),
                  child: Center(
                    child: Text(
                      '${levelService.level}',
                      style: const TextStyle(
                        color: Colors.white,
                        fontSize: 32,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                  ),
                ),
                const SizedBox(height: 16),
                Text(
                  _levelTitle(levelService.level),
                  style: const TextStyle(
                    color: Colors.white,
                    fontSize: 22,
                    fontWeight: FontWeight.bold,
                  ),
                ),
                const SizedBox(height: 8),
                // XP progress bar
                ClipRRect(
                  borderRadius: BorderRadius.circular(999),
                  child: LinearProgressIndicator(
                    value: levelService.levelProgress,
                    minHeight: 8,
                    backgroundColor: Colors.white.withValues(alpha: 0.2),
                    valueColor: const AlwaysStoppedAnimation<Color>(
                      Colors.white,
                    ),
                  ),
                ),
                const SizedBox(height: 8),
                Text(
                  '${levelService.xp} / ${levelService.xpForNextLevel} XP',
                  style: TextStyle(
                    color: Colors.white.withValues(alpha: 0.8),
                    fontSize: 14,
                  ),
                ),
              ],
            ),
          ),

          const SizedBox(height: 24),

          // ── Next Unlock ────────────────────────────────────
          if (levelService.nextLockedFeature != null) ...[
            Container(
              padding: const EdgeInsets.all(16),
              decoration: BoxDecoration(
                color: (isDark ? Colors.white : Colors.black).withValues(
                  alpha: 0.05,
                ),
                borderRadius: BorderRadius.circular(14),
                border: Border.all(
                  color: (isDark ? Colors.white : Colors.black).withValues(
                    alpha: 0.1,
                  ),
                ),
              ),
              child: Row(
                children: [
                  Container(
                    width: 44,
                    height: 44,
                    decoration: BoxDecoration(
                      color: Colors.amber.withValues(alpha: 0.15),
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: const Icon(
                      Icons.lock_outline,
                      color: Colors.amber,
                      size: 22,
                    ),
                  ),
                  const SizedBox(width: 14),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          'Next at Level ${levelService.nextLockedFeature!.requiredLevel}',
                          style: const TextStyle(
                            fontSize: 12,
                            color: Colors.grey,
                          ),
                        ),
                        const SizedBox(height: 2),
                        Text(
                          'Unlock ${levelService.nextLockedFeature!.name}',
                          style: const TextStyle(
                            fontSize: 16,
                            fontWeight: FontWeight.w600,
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 24),
          ],

          // ── Feature Unlock Table ───────────────────────────
          const Text(
            'FEATURES',
            style: TextStyle(
              fontSize: 11,
              fontWeight: FontWeight.bold,
              letterSpacing: 1.2,
              color: Colors.grey,
            ),
          ),
          const SizedBox(height: 8),
          ...AppFeature.all.map((feature) {
            final unlocked = levelService.isFeatureUnlocked(feature);
            return Container(
              margin: const EdgeInsets.only(bottom: 6),
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
              decoration: BoxDecoration(
                color: (isDark ? Colors.white : Colors.black).withValues(
                  alpha: unlocked ? 0.04 : 0.02,
                ),
                borderRadius: BorderRadius.circular(12),
                border: Border.all(
                  color: unlocked
                      ? Colors.greenAccent.withValues(alpha: 0.2)
                      : (isDark ? Colors.white : Colors.black).withValues(
                          alpha: 0.08,
                        ),
                ),
              ),
              child: Row(
                children: [
                  Icon(
                    unlocked ? Icons.check_circle : Icons.lock_outline,
                    color: unlocked ? Colors.greenAccent : Colors.grey,
                    size: 20,
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Text(
                      feature.name,
                      style: TextStyle(
                        fontSize: 14,
                        fontWeight: unlocked
                            ? FontWeight.w600
                            : FontWeight.normal,
                        color: unlocked ? null : Colors.grey,
                      ),
                    ),
                  ),
                  Text(
                    unlocked ? 'Unlocked' : 'Level ${feature.requiredLevel}',
                    style: TextStyle(
                      fontSize: 12,
                      color: unlocked ? Colors.greenAccent : Colors.grey,
                    ),
                  ),
                ],
              ),
            );
          }),

          const SizedBox(height: 24),

          // ── XP Rules ────────────────────────────────────────
          const Text(
            'HOW TO EARN XP',
            style: TextStyle(
              fontSize: 11,
              fontWeight: FontWeight.bold,
              letterSpacing: 1.2,
              color: Colors.grey,
            ),
          ),
          const SizedBox(height: 8),
          _XpRuleTile(
            icon: Icons.play_circle_outline,
            label: 'Watch a rewarded ad',
            value: '+2 XP (up to 20/day)',
            isDark: isDark,
          ),
          _XpRuleTile(
            icon: Icons.trending_down,
            label: 'Watch fewer reels than your weekly average',
            value: '+10 XP per reel saved',
            isDark: isDark,
          ),
          _XpRuleTile(
            icon: Icons.check_circle_outline,
            label: 'Stay under your daily reel limit',
            value: '+15 XP per day',
            isDark: isDark,
          ),
          _XpRuleTile(
            icon: Icons.login,
            label: 'Open the app and check in',
            value: '+1 XP per day',
            isDark: isDark,
          ),
          const SizedBox(height: 16),

          // ── Watch Ad to earn XP ─────────────────────────────
          SizedBox(
            width: double.infinity,
            height: 48,
            child: ElevatedButton.icon(
              onPressed: () => _watchAdForXp(context),
              style: ElevatedButton.styleFrom(
                backgroundColor: Colors.orange,
                foregroundColor: Colors.white,
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(12),
                ),
              ),
              icon: const Icon(Icons.play_circle_fill_rounded, size: 20),
              label: const Text(
                'Watch Ad to Earn +2 XP',
                style: TextStyle(fontWeight: FontWeight.w600),
              ),
            ),
          ),
          const SizedBox(height: 16),

          // ── XP History ──────────────────────────────────────
          const Text(
            'RECENT XP',
            style: TextStyle(
              fontSize: 11,
              fontWeight: FontWeight.bold,
              letterSpacing: 1.2,
              color: Colors.grey,
            ),
          ),
          const SizedBox(height: 8),
          ...levelService.recentXpLog.take(10).map((entry) {
            final dt = DateTime.tryParse(entry['time'] as String? ?? '');
            final timeStr = dt != null
                ? '${dt.hour}:${dt.minute.toString().padLeft(2, '0')}'
                : '';
            final amount = entry['amount'] as int;
            return Container(
              margin: const EdgeInsets.only(bottom: 4),
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
              decoration: BoxDecoration(
                color: (isDark ? Colors.white : Colors.black).withValues(
                  alpha: 0.04,
                ),
                borderRadius: BorderRadius.circular(8),
              ),
              child: Row(
                children: [
                  Icon(
                    amount > 0 ? Icons.add_circle : Icons.remove_circle,
                    color: amount > 0 ? Colors.greenAccent : Colors.redAccent,
                    size: 16,
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      entry['reason'] as String? ?? '',
                      style: const TextStyle(fontSize: 13),
                    ),
                  ),
                  Text(
                    amount > 0 ? '+$amount XP' : '$amount XP',
                    style: TextStyle(
                      fontSize: 12,
                      fontWeight: FontWeight.bold,
                      color: amount > 0 ? Colors.greenAccent : Colors.redAccent,
                    ),
                  ),
                  const SizedBox(width: 8),
                  Text(
                    timeStr,
                    style: const TextStyle(fontSize: 10, color: Colors.grey),
                  ),
                ],
              ),
            );
          }),
          if (levelService.recentXpLog.isEmpty)
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 12),
              child: Text(
                'No XP earned yet — watch an ad above or reduce reel time!',
                style: TextStyle(
                  fontSize: 13,
                  color: isDark ? Colors.white38 : Colors.black38,
                ),
              ),
            ),
          const SizedBox(height: 20),

          const Text(
            'DEGRADATION',
            style: TextStyle(
              fontSize: 11,
              fontWeight: FontWeight.bold,
              letterSpacing: 1.2,
              color: Colors.grey,
            ),
          ),
          const SizedBox(height: 8),
          Container(
            padding: const EdgeInsets.all(14),
            decoration: BoxDecoration(
              color: Colors.redAccent.withValues(alpha: 0.06),
              borderRadius: BorderRadius.circular(12),
              border: Border.all(
                color: Colors.redAccent.withValues(alpha: 0.15),
              ),
            ),
            child: const Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Icon(
                      Icons.warning_amber_rounded,
                      color: Colors.redAccent,
                      size: 18,
                    ),
                    SizedBox(width: 8),
                    Text(
                      'XP decays if you backslide',
                      style: TextStyle(
                        color: Colors.redAccent,
                        fontWeight: FontWeight.w600,
                        fontSize: 13,
                      ),
                    ),
                  ],
                ),
                SizedBox(height: 6),
                Text(
                  '• Watching more reels than your weekly average deducts XP\n'
                  '• Exceeding limits for 3 consecutive days drops a level\n'
                  '• Levels are preserved on monthly reset, but XP resets',
                  style: TextStyle(
                    fontSize: 12,
                    color: Colors.grey,
                    height: 1.5,
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 40),
        ],
      ),
    );
  }

  Color _levelColor(int level) {
    switch (level) {
      case 1:
        return Colors.grey;
      case 2:
        return Colors.blue;
      case 3:
        return Colors.purple;
      case 4:
        return Colors.orange;
      case 5:
        return Colors.amber;
      default:
        return Colors.grey;
    }
  }

  List<Color> _levelColors(int level, bool isDark) {
    final base = _levelColor(level);
    // MaterialColor supports .shadeXXX; plain Color doesn't.
    if (base is MaterialColor) {
      return isDark
          ? [base.shade800, base.shade900]
          : [base.shade400, base.shade700];
    }
    return [base, base];
  }

  /// Navigate to Adsterra ad -> grant XP on completion.
  Future<void> _watchAdForXp(BuildContext context) async {
    // Try Adsterra Social Bar first
    final adResult = await Navigator.push<bool>(
      context,
      MaterialPageRoute(
        builder: (_) => const AdsterraAdScreen(sessionType: 'reels'),
      ),
    );

    if (adResult == true && context.mounted) {
      context.read<LevelService>().addXpForAd();
      context.read<CreditStore>().addReelsMinutes();
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('+10 XP earned!'),
          duration: Duration(seconds: 2),
        ),
      );
    }
  }

  String _levelTitle(int level) {
    switch (level) {
      case 1:
        return 'Beginner';
      case 2:
        return 'Mindful Scroller';
      case 3:
        return 'Disciplined';
      case 4:
        return 'Focus Master';
      case 5:
        return 'Digital Monk';
      default:
        return 'Level $level';
    }
  }
}

class _XpRuleTile extends StatelessWidget {
  final IconData icon;
  final String label;
  final String value;
  final bool isDark;

  const _XpRuleTile({
    required this.icon,
    required this.label,
    required this.value,
    required this.isDark,
  });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 6),
      child: Row(
        children: [
          Icon(icon, size: 18, color: Colors.greenAccent),
          const SizedBox(width: 10),
          Expanded(
            child: Text(
              label,
              style: TextStyle(
                fontSize: 13,
                color: isDark ? Colors.white70 : Colors.black87,
              ),
            ),
          ),
          Text(
            value,
            style: const TextStyle(
              fontSize: 12,
              color: Colors.greenAccent,
              fontWeight: FontWeight.w600,
            ),
          ),
        ],
      ),
    );
  }
}
