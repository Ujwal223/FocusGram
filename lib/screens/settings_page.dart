import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../services/session_manager.dart';
import '../services/settings_service.dart';

class SettingsPage extends StatelessWidget {
  const SettingsPage({super.key});

  @override
  Widget build(BuildContext context) {
    final settings = context.watch<SettingsService>();
    final sm = context.watch<SessionManager>();

    return Scaffold(
      backgroundColor: Colors.black,
      appBar: AppBar(
        backgroundColor: Colors.black,
        title: const Text(
          'FocusGram',
          style: TextStyle(fontSize: 17, fontWeight: FontWeight.w600),
        ),
        centerTitle: true,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back_ios_new, size: 18),
          onPressed: () => Navigator.pop(context),
        ),
      ),
      body: ListView(
        children: [
          // ── Stats row ───────────────────────────────────────────
          _buildStatsRow(sm),

          // ── Consumption Limits ──────────────────────────────────
          _buildSectionHeader('Reel Consumption Limits'),
          _buildFrictionSliderTile(
            context: context,
            sm: sm,
            title: 'Daily Reel Limit',
            subtitle: '${sm.dailyLimitSeconds ~/ 60} min / day',
            value: (sm.dailyLimitSeconds ~/ 60).toDouble(),
            min: 5,
            max: 120,
            divisor: 5,
            warningText:
                'Increasing your daily limit may make it easier to mindlessly scroll. Are you sure?',
            onConfirmed: (v) => sm.setDailyLimitMinutes(v.toInt()),
          ),
          _buildFrictionSliderTile(
            context: context,
            sm: sm,
            title: 'Session Cooldown',
            subtitle: '${sm.cooldownSeconds ~/ 60} min between sessions',
            value: (sm.cooldownSeconds ~/ 60).toDouble(),
            min: 5,
            max: 180,
            divisor: 5,
            warningText:
                'Reducing the cooldown makes it easier to start new reel sessions. Are you sure?',
            onConfirmed: (v) => sm.setCooldownMinutes(v.toInt()),
          ),

          // ── Distraction Management ──────────────────────────────
          _buildSectionHeader('Distraction Management'),
          SwitchListTile(
            title: const Text(
              'Blur Explore feed',
              style: TextStyle(color: Colors.white),
            ),
            subtitle: const Text(
              'Blurs posts and reels in Explore by default',
              style: TextStyle(color: Colors.white54, fontSize: 13),
            ),
            value: settings.blurExplore,
            onChanged: (v) => settings.setBlurExplore(v),
            activeThumbColor: Colors.blue,
          ),

          // ── Friction & Discipline ───────────────────────────────
          _buildSectionHeader('Friction & Discipline'),
          SwitchListTile(
            title: const Text(
              'Mindfulness Gate',
              style: TextStyle(color: Colors.white),
            ),
            subtitle: const Text(
              'Show breathing exercise before opening Instagram',
              style: TextStyle(color: Colors.white54, fontSize: 13),
            ),
            value: settings.showBreathGate,
            onChanged: (v) => settings.setShowBreathGate(v),
            activeThumbColor: Colors.blue,
          ),
          SwitchListTile(
            title: const Text(
              'Long-press to start Reel session',
              style: TextStyle(color: Colors.white),
            ),
            subtitle: const Text(
              'Requires 2s hold on the play button',
              style: TextStyle(color: Colors.white54, fontSize: 13),
            ),
            value: settings.requireLongPress,
            onChanged: (v) => settings.setRequireLongPress(v),
            activeThumbColor: Colors.blue,
          ),

          const Divider(color: Colors.white10, height: 40),

          // ── Danger zone ─────────────────────────────────────────
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16.0),
            child: ElevatedButton(
              onPressed: () => _confirmReset(context, sm),
              style: ElevatedButton.styleFrom(
                backgroundColor: Colors.red.withAlpha(
                  (255 * 0.08).round(),
                ), // Changed from withOpacity
                foregroundColor: Colors.redAccent,
                side: const BorderSide(color: Colors.redAccent, width: 0.5),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(10),
                ),
              ),
              child: const Text('Reset Daily Usage Counter'),
            ),
          ),
          const SizedBox(height: 40),
          const Center(
            child: Text(
              'FocusGram · Built for discipline',
              style: TextStyle(color: Colors.white12, fontSize: 12),
            ),
          ),
          const SizedBox(height: 24),
        ],
      ),
    );
  }

  Widget _buildStatsRow(SessionManager sm) {
    return Container(
      margin: const EdgeInsets.fromLTRB(16, 20, 16, 4),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: const Color(0xFF111111),
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: Colors.white10),
      ),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceAround,
        children: [
          _statCell('Opens Today', '${sm.dailyOpenCount}×', Colors.blue),
          _dividerCell(),
          _statCell(
            'Reels Used',
            '${sm.dailyUsedSeconds ~/ 60}m',
            Colors.orangeAccent,
          ),
          _dividerCell(),
          _statCell(
            'Remaining',
            '${sm.dailyRemainingSeconds ~/ 60}m',
            Colors.greenAccent,
          ),
        ],
      ),
    );
  }

  Widget _statCell(String label, String value, Color color) {
    return Column(
      children: [
        Text(
          value,
          style: TextStyle(
            color: color,
            fontSize: 22,
            fontWeight: FontWeight.bold,
          ),
        ),
        const SizedBox(height: 4),
        Text(
          label,
          style: const TextStyle(color: Colors.white38, fontSize: 11),
        ),
      ],
    );
  }

  Widget _dividerCell() =>
      Container(width: 1, height: 36, color: Colors.white10);

  Widget _buildSectionHeader(String title) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 28, 16, 8),
      child: Text(
        title.toUpperCase(),
        style: const TextStyle(
          color: Colors.blue,
          fontSize: 11,
          fontWeight: FontWeight.bold,
          letterSpacing: 1.3,
        ),
      ),
    );
  }

  /// A slider tile that shows a friction dialog before accepting a larger value.
  Widget _buildFrictionSliderTile({
    required BuildContext context,
    required SessionManager sm,
    required String title,
    required String subtitle,
    required double value,
    required double min,
    required double max,
    required int divisor,
    required String warningText,
    required Future<void> Function(double) onConfirmed,
  }) {
    return _FrictionSliderTile(
      title: title,
      subtitle: subtitle,
      value: value,
      min: min,
      max: max,
      divisor: divisor,
      warningText: warningText,
      onConfirmed: onConfirmed,
    );
  }

  void _confirmReset(BuildContext context, SessionManager sm) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        backgroundColor: const Color(0xFF1A1A1A),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
        title: const Text(
          'Reset Counter?',
          style: TextStyle(color: Colors.white),
        ),
        content: const Text(
          'This will reset your daily reel usage to zero minutes.',
          style: TextStyle(color: Colors.white70),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Cancel'),
          ),
          TextButton(
            onPressed: () {
              sm.resetDailyCounter();
              Navigator.pop(ctx);
            },
            child: const Text(
              'Reset',
              style: TextStyle(color: Colors.redAccent),
            ),
          ),
        ],
      ),
    );
  }
}

/// Stateful slider tile that shows a friction dialog when the user moves the
/// slider to a value greater than the current persisted value.
class _FrictionSliderTile extends StatefulWidget {
  final String title;
  final String subtitle;
  final double value;
  final double min;
  final double max;
  final int divisor;
  final String warningText;
  final Future<void> Function(double) onConfirmed;

  const _FrictionSliderTile({
    required this.title,
    required this.subtitle,
    required this.value,
    required this.min,
    required this.max,
    required this.divisor,
    required this.warningText,
    required this.onConfirmed,
  });

  @override
  State<_FrictionSliderTile> createState() => _FrictionSliderTileState();
}

class _FrictionSliderTileState extends State<_FrictionSliderTile> {
  late double _draftValue;
  bool _pendingConfirm = false;

  @override
  void initState() {
    super.initState();
    _draftValue = widget.value;
  }

  @override
  void didUpdateWidget(_FrictionSliderTile old) {
    super.didUpdateWidget(old);
    // Keep draft in sync if external value changed (e.g. after reset)
    if (!_pendingConfirm) _draftValue = widget.value;
  }

  @override
  Widget build(BuildContext context) {
    final divisions = ((widget.max - widget.min) / widget.divisor).round();

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        ListTile(
          title: Text(
            widget.title,
            style: const TextStyle(color: Colors.white),
          ),
          subtitle: Text(
            '${_draftValue.toInt()} min',
            style: const TextStyle(color: Colors.white70, fontSize: 13),
          ),
          trailing: _pendingConfirm
              ? Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    TextButton(
                      onPressed: () {
                        setState(() {
                          _draftValue = widget.value;
                          _pendingConfirm = false;
                        });
                      },
                      child: const Text(
                        'Cancel',
                        style: TextStyle(color: Colors.white38),
                      ),
                    ),
                    ElevatedButton(
                      onPressed: () async {
                        setState(() => _pendingConfirm = false);
                        await widget.onConfirmed(_draftValue);
                      },
                      style: ElevatedButton.styleFrom(
                        backgroundColor: Colors.blue,
                        padding: const EdgeInsets.symmetric(
                          horizontal: 12,
                          vertical: 6,
                        ),
                        minimumSize: Size.zero,
                        tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                      ),
                      child: const Text(
                        'Apply',
                        style: TextStyle(fontSize: 12),
                      ),
                    ),
                  ],
                )
              : null,
        ),
        if (_pendingConfirm)
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
            child: Text(
              widget.warningText,
              style: TextStyle(
                color: Colors.orangeAccent.withValues(alpha: 0.8),
                fontSize: 12,
              ),
            ),
          ),
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16.0),
          child: Slider(
            value: _draftValue,
            min: widget.min,
            max: widget.max,
            divisions: divisions,
            activeColor: _pendingConfirm ? Colors.orange : Colors.blue,
            onChanged: (v) {
              setState(() {
                _draftValue = v;
                // Show friction warning when moving to a larger (more permissive) value
                _pendingConfirm = v > widget.value;
              });
            },
            onChangeEnd: (v) {
              // If decreasing (more strict), apply immediately without dialog
              if (v <= widget.value) {
                widget.onConfirmed(v);
                setState(() => _pendingConfirm = false);
              }
            },
          ),
        ),
      ],
    );
  }
}
