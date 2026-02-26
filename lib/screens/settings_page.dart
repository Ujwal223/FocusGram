import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:url_launcher/url_launcher.dart';
import '../services/session_manager.dart';
import '../services/settings_service.dart';
import '../services/focusgram_router.dart';
import '../features/screen_time/screen_time_screen.dart';
import 'guardrails_page.dart';

// ─── Main Settings Page ───────────────────────────────────────────────────────

class SettingsPage extends StatelessWidget {
  const SettingsPage({super.key});

  @override
  Widget build(BuildContext context) {
    final sm = context.watch<SessionManager>();
    final settings = context.watch<SettingsService>();
    final isDark = settings.isDarkMode;

    return Scaffold(
      appBar: AppBar(
        title: const Text(
          'Settings',
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
          _buildStatsRow(sm),

          const _SectionHeader(title: 'FOCUS & BLOCKING'),
          _SubmoduleTile(
            icon: Icons.block_rounded,
            iconColor: Colors.redAccent,
            title: 'Focus Mode',
            subtitle: settings.minimalModeEnabled
                ? 'Minimal mode on'
                : settings.disableReelsEntirely
                ? 'Reels fully disabled'
                : 'Blocking, friction, media',
            onTap: () => Navigator.push(
              context,
              MaterialPageRoute(builder: (_) => const FocusSettingsPage()),
            ),
          ),
          _SubmoduleTile(
            icon: Icons.timer_outlined,
            iconColor: Colors.blueAccent,
            title: 'Time Control & Guardrails',
            subtitle: 'Daily limit, cooldown, scheduled blocking',
            enabled:
                !(settings.disableReelsEntirely || settings.minimalModeEnabled),
            disabledSubtitle: 'Reels are fully disabled',
            onTap: () => Navigator.push(
              context,
              MaterialPageRoute(builder: (_) => const GuardrailsPage()),
            ),
          ),

          const _SectionHeader(title: 'APPEARANCE'),
          _SubmoduleTile(
            icon: Icons.palette_outlined,
            iconColor: Colors.purpleAccent,
            title: 'Appearance',
            subtitle: settings.grayscaleEnabled
                ? 'Grayscale on'
                : settings.grayscaleScheduleEnabled
                ? 'Grayscale scheduled at ${settings.grayscaleScheduleTime}'
                : 'Theme, grayscale',
            onTap: () => Navigator.push(
              context,
              MaterialPageRoute(builder: (_) => const AppearancePage()),
            ),
          ),

          const _SectionHeader(title: 'PRIVACY & NOTIFICATIONS'),
          _SubmoduleTile(
            icon: Icons.lock_outline,
            iconColor: Colors.tealAccent,
            title: 'Privacy & Notifications',
            subtitle: 'Link sanitization, session end alerts',
            onTap: () => Navigator.push(
              context,
              MaterialPageRoute(
                builder: (_) => const PrivacyNotificationsPage(),
              ),
            ),
          ),

          const _SectionHeader(title: 'STATS & HISTORY'),
          _SubmoduleTile(
            icon: Icons.bar_chart_rounded,
            iconColor: Colors.greenAccent,
            title: 'Screen Time Dashboard',
            subtitle: 'Daily & weekly usage',
            onTap: () => Navigator.push(
              context,
              MaterialPageRoute(builder: (_) => const ScreenTimeScreen()),
            ),
          ),

          const _SectionHeader(title: 'ABOUT'),
          FutureBuilder<PackageInfo>(
            future: PackageInfo.fromPlatform(),
            builder: (context, snapshot) => ListTile(
              title: const Text('Version'),
              trailing: Text(
                snapshot.data?.version ?? '…',
                style: const TextStyle(color: Colors.grey),
              ),
            ),
          ),
          ListTile(
            title: const Text('GitHub'),
            trailing: const Icon(Icons.open_in_new, size: 14),
            onTap: () => launchUrl(
              Uri.parse('https://github.com/Ujwal223/FocusGram'),
              mode: LaunchMode.externalApplication,
            ),
          ),
          ListTile(
            title: const Text('Legal Disclaimer'),
            trailing: const Icon(Icons.info_outline, size: 14),
            onTap: () => _showLegalDisclaimer(context),
          ),
          ListTile(
            title: const Text('Open Source Licenses'),
            trailing: const Icon(Icons.arrow_forward_ios, size: 14),
            onTap: () => showLicensePage(context: context),
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
              Navigator.pop(context);
              FocusGramRouter.pendingUrl.value =
                  'https://www.instagram.com/accounts/settings/?entrypoint=profile';
            },
          ),
          const SizedBox(height: 40),
          Center(
            child: Text(
              'FocusGram · Built with 💖 by Ujwal Chapagain',
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

  Widget _statCell(String label, String value, Color color) => Column(
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

  Widget _dividerCell() => Container(
    width: 1,
    height: 36,
    color: Colors.blue.withValues(alpha: 0.1),
  );

  void _showLegalDisclaimer(BuildContext context) {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Legal Disclaimer'),
        content: const SingleChildScrollView(
          child: Text(
            'FocusGram is an independent, free, and open-source productivity tool '
            'licensed under AGPL-3.0. Not affiliated with Meta or Instagram.\n\n'
            'How it works: FocusGram embeds a standard Android System WebView that loads instagram.com. \n'
            'All user-facing features are implemented exclusively via client-side modifications and are never transmitted to or processed by Meta servers.\n\n'
            'All features are client-side only. We do not use private APIs, '
            'intercept credentials, scrape, harvest or collect any user data.',
            style: TextStyle(fontSize: 13, height: 1.4),
          ),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context),
            child: const Text('Close'),
          ),
        ],
      ),
    );
  }
}

// ─── Focus Settings ───────────────────────────────────────────────────────────

class FocusSettingsPage extends StatelessWidget {
  const FocusSettingsPage({super.key});

  @override
  Widget build(BuildContext context) {
    final settings = context.watch<SettingsService>();
    return Scaffold(
      appBar: _subAppBar(context, 'Focus Mode'),
      body: ListView(
        children: [
          const _SectionHeader(title: 'BLOCKING'),
          Container(
            margin: const EdgeInsets.fromLTRB(16, 0, 16, 8),
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
            decoration: BoxDecoration(
              color: Colors.blue.withValues(alpha: 0.07),
              borderRadius: BorderRadius.circular(10),
              border: Border.all(color: Colors.blue.withValues(alpha: 0.12)),
            ),
            child: const Row(
              children: [
                Icon(Icons.info_outline, size: 14, color: Colors.blueAccent),
                SizedBox(width: 8),
                Expanded(
                  child: Text(
                    'Blocking changes apply immediately. The page reloads automatically in the background.',
                    style: TextStyle(fontSize: 11, color: Colors.blueAccent),
                  ),
                ),
              ],
            ),
          ),
          _SwitchTile(
            title: 'Minimal Mode',
            subtitle:
                'Feed and DMs only — blocks Reels, Explore, Stories, Suggested',
            value: settings.minimalModeEnabled,
            onChanged: (v) async {
              await settings.setMinimalModeEnabled(v);
              HapticFeedback.selectionClick();
            },
          ),
          _SwitchTile(
            title: 'Disable Reels Entirely',
            subtitle: 'Block all Reels with no session option',
            value: settings.disableReelsEntirely,
            enabled: !settings.minimalModeEnabled,
            disabledSubtitle: 'Included in Minimal Mode',
            onChanged: (v) => settings.setDisableReelsEntirely(v),
          ),

          const _SectionHeader(title: 'FRICTION'),
          _SwitchTile(
            title: 'Mindfulness Gate',
            subtitle: 'Breath / intention screen before opening Instagram',
            value: settings.showBreathGate,
            onChanged: (v) => settings.setShowBreathGate(v),
          ),
          _SwitchTile(
            title: 'Strict Mode (Word Challenge)',
            subtitle: 'Must type a phrase before starting a Reel session',
            value: settings.requireWordChallenge,
            onChanged: (v) => settings.setRequireWordChallenge(v),
          ),
          const _SectionHeader(title: 'MEDIA'),
          _SwitchTile(
            title: 'Block Autoplay Videos',
            subtitle: 'Videos won\'t play until you tap them',
            value: settings.blockAutoplay,
            onChanged: (v) => settings.setBlockAutoplay(v),
          ),
          _SwitchTile(
            title: 'Blur Feed & Explore',
            subtitle: 'Blurs post thumbnails until tapped',
            value: settings.blurExplore,
            onChanged: (v) => settings.setBlurExplore(v),
          ),

          const SizedBox(height: 40),
        ],
      ),
    );
  }
}

// ─── Appearance ───────────────────────────────────────────────────────────────

class AppearancePage extends StatelessWidget {
  const AppearancePage({super.key});

  @override
  Widget build(BuildContext context) {
    final settings = context.watch<SettingsService>();
    return Scaffold(
      appBar: _subAppBar(context, 'Appearance'),
      body: ListView(
        children: [
          const _SectionHeader(title: 'DISPLAY'),
          _SwitchTile(
            title: 'Grayscale Mode',
            subtitle:
                'Makes Instagram black & white — reduces dopamine response',
            value: settings.grayscaleEnabled,
            onChanged: (v) => settings.setGrayscaleEnabled(v),
          ),
          const _SectionHeader(title: 'GRAYSCALE SCHEDULE'),
          _SwitchTile(
            title: 'Schedule Grayscale',
            subtitle: 'Auto-enable grayscale at a set time each day',
            value: settings.grayscaleScheduleEnabled,
            onChanged: (v) => settings.setGrayscaleScheduleEnabled(v),
          ),
          if (settings.grayscaleScheduleEnabled)
            ListTile(
              leading: const Icon(
                Icons.access_time,
                color: Colors.blueAccent,
                size: 20,
              ),
              title: const Text('Start Time'),
              subtitle: const Text(
                'Grayscale activates at this time and stays on until midnight',
                style: TextStyle(fontSize: 12),
              ),
              trailing: Text(
                settings.grayscaleScheduleTime,
                style: const TextStyle(
                  color: Colors.blueAccent,
                  fontSize: 16,
                  fontWeight: FontWeight.w600,
                ),
              ),
              onTap: () async {
                final parts = settings.grayscaleScheduleTime.split(':');
                final time = await showTimePicker(
                  context: context,
                  initialTime: TimeOfDay(
                    hour: int.parse(parts[0]),
                    minute: int.parse(parts[1]),
                  ),
                );
                if (time != null) {
                  settings.setGrayscaleScheduleTime(
                    '${time.hour.toString().padLeft(2, '0')}:${time.minute.toString().padLeft(2, '0')}',
                  );
                }
              },
            ),
          if (settings.grayscaleScheduleEnabled)
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
              child: Text(
                settings.isGrayscaleActiveNow
                    ? '● Grayscale is active now'
                    : '○ Grayscale will activate at ${settings.grayscaleScheduleTime}',
                style: TextStyle(
                  fontSize: 12,
                  color: settings.isGrayscaleActiveNow
                      ? Colors.greenAccent
                      : Colors.grey,
                ),
              ),
            ),
          const SizedBox(height: 40),
        ],
      ),
    );
  }
}

// ─── Privacy & Notifications ──────────────────────────────────────────────────

class PrivacyNotificationsPage extends StatelessWidget {
  const PrivacyNotificationsPage({super.key});

  @override
  Widget build(BuildContext context) {
    final settings = context.watch<SettingsService>();
    return Scaffold(
      appBar: _subAppBar(context, 'Privacy & Notifications'),
      body: ListView(
        children: [
          const _SectionHeader(title: 'NOTIFICATIONS'),
          Container(
            margin: const EdgeInsets.fromLTRB(16, 0, 16, 12),
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
            decoration: BoxDecoration(
              color: Colors.blue.withValues(alpha: 0.07),
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: Colors.blue.withValues(alpha: 0.15)),
            ),
            child: const Text(
              'FocusGram can show notifications when your Focus session ends. '
              'Instagram\'s own notification system handles background alerts.',
              style: TextStyle(fontSize: 12, height: 1.5),
            ),
          ),
          _SwitchTile(
            title: 'Session End Notification',
            subtitle: 'Notify when Focus session time is up',
            value: settings.notifySessionEnd,
            onChanged: (v) => settings.setNotifySessionEnd(v),
          ),

          const _SectionHeader(title: 'INSTA NOTIFICATIONS'),
          _SwitchTile(
            title: 'DM Notifications',
            subtitle: 'Show notification when someone messages you',
            value: settings.notifyDMs,
            onChanged: (v) => settings.setNotifyDMs(v),
          ),
          _SwitchTile(
            title: 'Activity Notifications',
            subtitle: 'Likes, comments, follows and other activity',
            value: settings.notifyActivity,
            onChanged: (v) => settings.setNotifyActivity(v),
          ),

          const SizedBox(height: 40),
        ],
      ),
    );
  }
}

// ─── Shared widgets ───────────────────────────────────────────────────────────

PreferredSizeWidget _subAppBar(BuildContext context, String title) => AppBar(
  title: Text(
    title,
    style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w600),
  ),
  centerTitle: true,
  leading: IconButton(
    icon: const Icon(Icons.arrow_back_ios_new, size: 18),
    onPressed: () => Navigator.pop(context),
  ),
);

class _SubmoduleTile extends StatelessWidget {
  final IconData icon;
  final Color iconColor;
  final String title;
  final String subtitle;
  final String? disabledSubtitle;
  final bool enabled;
  final VoidCallback onTap;

  const _SubmoduleTile({
    required this.icon,
    required this.iconColor,
    required this.title,
    required this.subtitle,
    this.disabledSubtitle,
    this.enabled = true,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return ListTile(
      leading: Container(
        width: 36,
        height: 36,
        decoration: BoxDecoration(
          color: (enabled ? iconColor : Colors.grey).withValues(alpha: 0.12),
          borderRadius: BorderRadius.circular(10),
        ),
        child: Icon(icon, color: enabled ? iconColor : Colors.grey, size: 20),
      ),
      title: Text(
        title,
        style: TextStyle(
          fontSize: 15,
          fontWeight: FontWeight.w500,
          color: enabled ? null : Colors.grey,
        ),
      ),
      subtitle: Text(
        enabled ? subtitle : (disabledSubtitle ?? subtitle),
        style: const TextStyle(fontSize: 12),
      ),
      trailing: const Icon(
        Icons.arrow_forward_ios,
        size: 14,
        color: Colors.grey,
      ),
      onTap: enabled ? onTap : null,
    );
  }
}

class _SwitchTile extends StatelessWidget {
  final String title;
  final String? subtitle;
  final String? disabledSubtitle;
  final bool value;
  final bool enabled;
  final ValueChanged<bool> onChanged;

  const _SwitchTile({
    required this.title,
    this.subtitle,
    this.disabledSubtitle,
    required this.value,
    this.enabled = true,
    required this.onChanged,
  });

  @override
  Widget build(BuildContext context) {
    return SwitchListTile(
      title: Text(
        title,
        style: TextStyle(fontSize: 15, color: enabled ? null : Colors.grey),
      ),
      subtitle: (subtitle != null || (!enabled && disabledSubtitle != null))
          ? Text(
              enabled ? (subtitle ?? '') : (disabledSubtitle ?? subtitle ?? ''),
              style: const TextStyle(fontSize: 12),
            )
          : null,
      value: value,
      onChanged: enabled ? onChanged : null,
    );
  }
}

class _SectionHeader extends StatelessWidget {
  final String title;
  const _SectionHeader({required this.title});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 24, 16, 8),
      child: Text(
        title,
        style: const TextStyle(
          color: Colors.grey,
          fontSize: 11,
          fontWeight: FontWeight.bold,
          letterSpacing: 1.2,
        ),
      ),
    );
  }
}
