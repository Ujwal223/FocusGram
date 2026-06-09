import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import '../services/settings_service.dart';

/// Ghost Mode submenu — tap "Ghost Mode" in Extras to open this.
/// Single mode: DM Ghost (comprehensive seen-signal blocking).
class GhostModeSubmenuPage extends StatelessWidget {
  const GhostModeSubmenuPage({super.key});

  @override
  Widget build(BuildContext context) {
    final s = context.watch<SettingsService>();
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return Scaffold(
      appBar: AppBar(
        title: const Text(
          'Ghost Mode',
          style: TextStyle(fontSize: 17, fontWeight: FontWeight.w600),
        ),
        centerTitle: true,
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          // ── DM Ghost ──────────────────────────────────────
          _GhostCard(
            icon: Icons.visibility_off_rounded,
            title: 'DM Ghost',
            subtitle: 'Read messages without the person knowing',
            value: s.ghostMode,
            warning:
                'When DM Ghost is enabled, you can\'t send messages or react to any, you can just receive messages. You can turn ghost mode off anytime from topbar button.',
            onChanged: (v) => s.setGhostMode(v),
            isDark: isDark,
            danger: true,
          ),
          const SizedBox(height: 24),
          const SizedBox(height: 40),
        ],
      ),
    );
  }
}

class _GhostCard extends StatelessWidget {
  final IconData icon;
  final String title;
  final String subtitle;
  final bool value;
  final String warning;
  final ValueChanged<bool> onChanged;
  final bool isDark;
  final bool danger;

  const _GhostCard({
    required this.icon,
    required this.title,
    required this.subtitle,
    required this.value,
    required this.warning,
    required this.onChanged,
    required this.isDark,
    this.danger = false,
  });

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: (value ? (danger ? Colors.red : Colors.blue) : Colors.grey)
            .withValues(alpha: value ? 0.08 : 0.03),
        borderRadius: BorderRadius.circular(14),
        border: Border.all(
          color: (value ? (danger ? Colors.red : Colors.blue) : Colors.grey)
              .withValues(alpha: value ? 0.25 : 0.1),
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(
                icon,
                color: value
                    ? (danger ? Colors.redAccent : Colors.blueAccent)
                    : Colors.grey,
                size: 22,
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      title,
                      style: TextStyle(
                        fontWeight: FontWeight.w600,
                        color: value
                            ? (danger ? Colors.redAccent : null)
                            : Colors.grey,
                      ),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      subtitle,
                      style: TextStyle(
                        fontSize: 12,
                        color: isDark ? Colors.white54 : Colors.black54,
                      ),
                    ),
                  ],
                ),
              ),
              Switch(
                value: value,
                activeColor: danger ? Colors.redAccent : null,
                onChanged: onChanged,
              ),
            ],
          ),
          if (value)
            Padding(
              padding: const EdgeInsets.only(top: 10),
              child: Container(
                padding: const EdgeInsets.all(10),
                decoration: BoxDecoration(
                  color: (danger ? Colors.red : Colors.amber).withValues(
                    alpha: 0.1,
                  ),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Icon(
                      danger ? Icons.warning_amber_rounded : Icons.info_outline,
                      size: 14,
                      color: danger ? Colors.redAccent : Colors.amber,
                    ),
                    const SizedBox(width: 6),
                    Expanded(
                      child: Text(
                        warning,
                        style: TextStyle(
                          fontSize: 11,
                          color: danger
                              ? Colors.redAccent
                              : Colors.amber.shade800,
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ),
        ],
      ),
    );
  }
}
