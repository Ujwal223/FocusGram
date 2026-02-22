import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../services/session_manager.dart';
import '../services/settings_service.dart';
import 'guardrails_page.dart';
import 'about_page.dart';

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

          // ── Settings Subsections ──────────────────────────────
          _buildSettingsTile(
            context: context,
            title: 'Guardrails',
            subtitle: 'Daily limit, cooldown, and scheduled blocking',
            icon: Icons.shield_outlined,
            destination: const GuardrailsPage(),
          ),
          _buildSettingsTile(
            context: context,
            title: 'Distraction Management',
            subtitle: 'Blur explore and reel controls',
            icon: Icons.visibility_off_outlined,
            destination: _DistractionSettingsPage(settings: settings),
          ),
          _buildSettingsTile(
            context: context,
            title: 'About',
            subtitle: 'Developer info and GitHub',
            icon: Icons.info_outline,
            destination: const AboutPage(),
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

  Widget _buildSettingsTile({
    required BuildContext context,
    required String title,
    required String subtitle,
    required IconData icon,
    required Widget destination,
  }) {
    return ListTile(
      leading: Icon(icon, color: Colors.blue),
      title: Text(title, style: const TextStyle(color: Colors.white)),
      subtitle: Text(
        subtitle,
        style: const TextStyle(color: Colors.white54, fontSize: 13),
      ),
      trailing: const Icon(
        Icons.arrow_forward_ios,
        color: Colors.white24,
        size: 14,
      ),
      onTap: () => Navigator.push(
        context,
        MaterialPageRoute(builder: (_) => destination),
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
}

class _DistractionSettingsPage extends StatelessWidget {
  final SettingsService settings;
  const _DistractionSettingsPage({required this.settings});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      appBar: AppBar(
        backgroundColor: Colors.black,
        title: const Text(
          'Distraction Management',
          style: TextStyle(fontSize: 17),
        ),
        leading: IconButton(
          icon: const Icon(Icons.arrow_back_ios_new, size: 18),
          onPressed: () => Navigator.pop(context),
        ),
      ),
      body: ListView(
        children: [
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
          SwitchListTile(
            title: const Text(
              'Mindfulness Gate',
              style: TextStyle(color: Colors.white),
            ),
            subtitle: const Text(
              'Show breathing exercise before opening',
              style: TextStyle(color: Colors.white54, fontSize: 13),
            ),
            value: settings.showBreathGate,
            onChanged: (v) => settings.setShowBreathGate(v),
            activeThumbColor: Colors.blue,
          ),
          SwitchListTile(
            title: const Text(
              'Long-press for Session',
              style: TextStyle(color: Colors.white),
            ),
            subtitle: const Text(
              'Requires 2s hold to start a Reel session',
              style: TextStyle(color: Colors.white54, fontSize: 13),
            ),
            value: settings.requireLongPress,
            onChanged: (v) => settings.setRequireLongPress(v),
            activeThumbColor: Colors.blue,
          ),
          SwitchListTile(
            title: const Text(
              'Strict Changes (Word Challenge)',
              style: TextStyle(color: Colors.white),
            ),
            subtitle: const Text(
              'Requires 15-word typing challenge before lax changes',
              style: TextStyle(color: Colors.white54, fontSize: 13),
            ),
            value: settings.requireWordChallenge,
            onChanged: (v) => settings.setRequireWordChallenge(v),
            activeThumbColor: Colors.blue,
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
