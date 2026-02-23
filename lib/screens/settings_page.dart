import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../services/session_manager.dart';
import '../services/settings_service.dart';
import '../services/focusgram_router.dart';
import 'guardrails_page.dart';
import 'about_page.dart';

class SettingsPage extends StatelessWidget {
  const SettingsPage({super.key});

  @override
  Widget build(BuildContext context) {
    // Watching services ensures the UI rebuilds when settings or session state change.
    final sm = context.watch<SessionManager>();
    final settings = context.watch<SettingsService>();
    final isDark = settings.isDarkMode;

    return Scaffold(
      appBar: AppBar(
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
            destination: const _DistractionSettingsPage(),
          ),
          _buildSettingsTile(
            context: context,
            title: 'Extras',
            subtitle: 'Ghost mode, text selection and experimental features',
            icon: Icons.extension_outlined,
            destination: const _ExtrasSettingsPage(),
          ),
          _buildSettingsTile(
            context: context,
            title: 'Notifications',
            subtitle: 'Manage message and activity alerts',
            icon: Icons.notifications_active_outlined,
            destination: const _NotificationSettingsPage(),
          ),
          _buildSettingsTile(
            context: context,
            title: 'About',
            subtitle: 'Developer info and GitHub',
            icon: Icons.info_outline,
            destination: const AboutPage(),
          ),

          const Divider(height: 40, indent: 16, endIndent: 16),

          ListTile(
            leading: const Icon(
              Icons.settings_outlined,
              color: Colors.purpleAccent,
            ),
            title: const Text('Instagram Settings'),
            subtitle: const Text(
              'Open native Instagram account settings',
              style: TextStyle(fontSize: 12),
            ),
            trailing: const Icon(
              Icons.open_in_new,
              color: Colors.white24,
              size: 14,
            ),
            onTap: () {
              // Bug 6 fix: navigate inside the WebView instead of external browser
              Navigator.pop(context);
              FocusGramRouter.pendingUrl.value =
                  'https://www.instagram.com/accounts/settings/?entrypoint=profile';
            },
          ),

          const SizedBox(height: 40),
          Center(
            child: Text(
              'FocusGram · Built for discipline',
              style: TextStyle(
                color: isDark ? Colors.white12 : Colors.black12,
                fontSize: 12,
              ),
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
      title: Text(title),
      subtitle: Text(subtitle, style: const TextStyle(fontSize: 13)),
      trailing: const Icon(Icons.arrow_forward_ios, size: 14),
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
        color: Colors.blue.withValues(alpha: 0.05),
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: Colors.blue.withValues(alpha: 0.1)),
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
        Text(label, style: const TextStyle(color: Colors.grey, fontSize: 11)),
      ],
    );
  }

  Widget _dividerCell() => Container(
    width: 1,
    height: 36,
    color: Colors.blue.withValues(alpha: 0.1),
  );
}

class _DistractionSettingsPage extends StatelessWidget {
  const _DistractionSettingsPage();

  @override
  Widget build(BuildContext context) {
    final settings = context.watch<SettingsService>();
    return Scaffold(
      appBar: AppBar(
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
            title: const Text('Blur Posts and Explore'),
            subtitle: const Text(
              'Blurs images and videos on the home feed and Explore page',
              style: TextStyle(fontSize: 13),
            ),
            value: settings.blurExplore,
            onChanged: (v) => settings.setBlurExplore(v),
            activeThumbColor: Colors.blue,
          ),
          SwitchListTile(
            title: const Text('Mindfulness Gate'),
            subtitle: const Text(
              'Show breathing exercise before opening',
              style: TextStyle(fontSize: 13),
            ),
            value: settings.showBreathGate,
            onChanged: (v) => settings.setShowBreathGate(v),
            activeThumbColor: Colors.blue,
          ),
          SwitchListTile(
            title: const Text('Strict Changes (Word Challenge)'),
            subtitle: const Text(
              'Requires 15-word typing challenge before lax changes',
              style: TextStyle(fontSize: 13),
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
          title: Text(widget.title),
          subtitle: Text(
            '${_draftValue.toInt()} min',
            style: const TextStyle(fontSize: 13),
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
                      child: const Text('Cancel'),
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

class _ExtrasSettingsPage extends StatelessWidget {
  const _ExtrasSettingsPage();

  @override
  Widget build(BuildContext context) {
    final settings = context.watch<SettingsService>();
    final isDark = settings.isDarkMode;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Extras', style: TextStyle(fontSize: 17)),
        leading: IconButton(
          icon: const Icon(Icons.arrow_back_ios_new, size: 18),
          onPressed: () => Navigator.pop(context),
        ),
      ),
      body: ListView(
        children: [
          const _SettingsSectionHeader(title: 'EXPERIMENT'),
          ListTile(
            onTap: () => Navigator.push(
              context,
              MaterialPageRoute(builder: (_) => const _GhostModeSettingsPage()),
            ),
            leading: Icon(
              Icons.visibility_off_outlined,
              color: isDark ? Colors.white70 : Colors.black87,
            ),
            title: const Text('Ghost Mode'),
            subtitle: Text(
              settings.anyGhostModeEnabled
                  ? 'Active — some receipts are hidden'
                  : 'Disabled',
              style: TextStyle(
                color: settings.anyGhostModeEnabled
                    ? Colors.blue
                    : (isDark ? Colors.white38 : Colors.black38),
                fontSize: 13,
              ),
            ),
            trailing: const Icon(Icons.chevron_right),
          ),
          SwitchListTile(
            title: const Text('Enable Text Selection'),
            subtitle: const Text(
              'Allows copying text from posts and captions',
              style: TextStyle(fontSize: 13),
            ),
            value: settings.enableTextSelection,
            onChanged: (v) => settings.setEnableTextSelection(v),
            activeThumbColor: Colors.blue,
          ),
          const SizedBox(height: 24),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: Text(
              'Experimental features: Some features may break if Instagram updates their website.',
              style: TextStyle(
                color: isDark ? Colors.white24 : Colors.black26,
                fontSize: 11,
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _SettingsSectionHeader extends StatelessWidget {
  final String title;
  const _SettingsSectionHeader({required this.title});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 24, 16, 8),
      child: Text(
        title,
        style: const TextStyle(
          color: Colors.blue,
          fontSize: 11,
          fontWeight: FontWeight.bold,
          letterSpacing: 1.2,
        ),
      ),
    );
  }
}

class _GhostModeSettingsPage extends StatelessWidget {
  const _GhostModeSettingsPage();

  @override
  Widget build(BuildContext context) {
    final settings = context.watch<SettingsService>();
    final isDark = settings.isDarkMode;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Ghost Mode', style: TextStyle(fontSize: 17)),
        leading: IconButton(
          icon: const Icon(Icons.arrow_back_ios_new, size: 18),
          onPressed: () => Navigator.pop(context),
        ),
      ),
      body: ListView(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 4),
            child: Text(
              'Control which activity receipts are hidden from other users. ',
              style: TextStyle(
                color: isDark ? Colors.white38 : Colors.black45,
                fontSize: 12,
                height: 1.4,
              ),
            ),
          ),
          const _SettingsSectionHeader(title: 'MESSAGING'),
          SwitchListTile(
            secondary: Icon(
              Icons.keyboard_outlined,
              color: isDark ? Colors.white54 : Colors.black54,
            ),
            title: const Text('Hide typing indicator'),
            subtitle: Text(
              "Others won't see the 'typing...' status when you write a message",
              style: TextStyle(
                color: isDark ? Colors.white38 : Colors.black45,
                fontSize: 12,
              ),
            ),
            value: settings.ghostTyping,
            onChanged: (v) => settings.setGhostTyping(v),
            activeThumbColor: Colors.blue,
          ),
          Stack(
            children: [
              AbsorbPointer(
                child: Opacity(
                  opacity: 0.5,
                  child: SwitchListTile(
                    secondary: Icon(
                      Icons.done_all_rounded,
                      color: isDark ? Colors.white54 : Colors.black54,
                    ),
                    title: const Text('Hide seen status'),
                    subtitle: Text(
                      "Others won't see when you've read their DMs",
                      style: TextStyle(
                        color: isDark ? Colors.white38 : Colors.black45,
                        fontSize: 12,
                      ),
                    ),
                    value: settings.ghostSeen,
                    onChanged: (v) => settings.setGhostSeen(v),
                    activeThumbColor: Colors.blue,
                  ),
                ),
              ),
              Positioned(
                right: 16,
                top: 0,
                bottom: 0,
                child: Center(
                  child: Container(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 8,
                      vertical: 4,
                    ),
                    decoration: BoxDecoration(
                      color: Colors.blue.withValues(alpha: 0.2),
                      borderRadius: BorderRadius.circular(4),
                      border: Border.all(color: Colors.blue, width: 0.5),
                    ),
                    child: const Text(
                      'COMING SOON',
                      style: TextStyle(
                        color: Colors.blue,
                        fontSize: 10,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                  ),
                ),
              ),
            ],
          ),
          SwitchListTile(
            secondary: Icon(
              Icons.image_outlined,
              color: isDark ? Colors.white54 : Colors.black54,
            ),
            title: const Text('Hide DM photo seen status'),
            subtitle: Text(
              'Prevents Instagram from marking photos/videos in DMs as viewed',
              style: TextStyle(
                color: isDark ? Colors.white38 : Colors.black45,
                fontSize: 12,
              ),
            ),
            value: settings.ghostDmPhotos,
            onChanged: (v) => settings.setGhostDmPhotos(v),
            activeThumbColor: Colors.blue,
          ),
          const _SettingsSectionHeader(title: 'STORIES'),
          SwitchListTile(
            secondary: Icon(
              Icons.auto_stories_outlined,
              color: isDark ? Colors.white54 : Colors.black54,
            ),
            title: const Text('Story ghost mode'),
            subtitle: Text(
              'Watch stories without appearing in the viewer list',
              style: TextStyle(
                color: isDark ? Colors.white38 : Colors.black45,
                fontSize: 12,
              ),
            ),
            value: settings.ghostStories,
            onChanged: (v) => settings.setGhostStories(v),
            activeThumbColor: Colors.blue,
          ),
          const SizedBox(height: 32),
        ],
      ),
    );
  }
}

class _NotificationSettingsPage extends StatelessWidget {
  const _NotificationSettingsPage();

  @override
  Widget build(BuildContext context) {
    final settings = context.watch<SettingsService>();
    final isDark = settings.isDarkMode;

    return Scaffold(
      appBar: AppBar(
        title: const Text('Notifications', style: TextStyle(fontSize: 17)),
        leading: IconButton(
          icon: const Icon(Icons.arrow_back_ios_new, size: 18),
          onPressed: () => Navigator.pop(context),
        ),
      ),
      body: ListView(
        children: [
          Container(
            margin: const EdgeInsets.all(16),
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: Colors.blue.withValues(alpha: 0.1),
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: Colors.blue.withValues(alpha: 0.3)),
            ),
            child: Column(
              children: [
                const Row(
                  children: [
                    Icon(
                      Icons.info_outline,
                      color: Colors.blueAccent,
                      size: 20,
                    ),
                    SizedBox(width: 12),
                    Text(
                      'Important Note',
                      style: TextStyle(
                        color: Colors.blueAccent,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 10),
                Text(
                  'FocusGram monitors your session locally. For notifications to work, the app must be running in the background (minimized). If you force-close or swipe away the app from your task switcher, notifications will stop until you reopen it.',
                  style: TextStyle(
                    color: isDark ? Colors.white70 : Colors.black87,
                    fontSize: 13,
                    height: 1.4,
                  ),
                ),
              ],
            ),
          ),
          SwitchListTile(
            secondary: const Icon(Icons.mail_outline, color: Colors.blueAccent),
            title: const Text('Direct Messages'),
            subtitle: const Text(
              'Notify when you receive a new DM',
              style: TextStyle(fontSize: 13),
            ),
            value: settings.notifyDMs,
            onChanged: (v) => settings.setNotifyDMs(v),
            activeThumbColor: Colors.blue,
          ),
          SwitchListTile(
            secondary: const Icon(
              Icons.favorite_border,
              color: Colors.blueAccent,
            ),
            title: const Text('General Activity'),
            subtitle: const Text(
              'Likes, mentions, and other interactions',
              style: TextStyle(fontSize: 13),
            ),
            value: settings.notifyActivity,
            onChanged: (v) => settings.setNotifyActivity(v),
            activeThumbColor: Colors.blue,
          ),
          const SizedBox(height: 24),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: Text(
              'Note: Push notifications are generated by the app local service by monitoring the web sessions. This does not rely on Instagram servers sending notifications to your device.',
              style: TextStyle(
                color: isDark ? Colors.white24 : Colors.black26,
                fontSize: 11,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
