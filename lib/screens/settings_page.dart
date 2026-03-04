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
                : settings.grayscaleSchedules.isNotEmpty
                ? 'Grayscale scheduled (${settings.grayscaleSchedules.length} schedules)'
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
            subtitle: 'Session end alerts',
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
          ListTile(
            leading: Container(
              width: 36,
              height: 36,
              decoration: BoxDecoration(
                color: Colors.redAccent.withValues(alpha: 0.12),
                borderRadius: BorderRadius.circular(10),
              ),
              child: const Icon(Icons.shield_rounded, color: Colors.redAccent, size: 20),
            ),
            title: const Text('Minimal Mode', style: TextStyle(fontSize: 15)),
            subtitle: Text(
              settings.minimalModeEnabled ? 'Enabled - tap to customize' : 'Disabled - tap to configure',
              style: TextStyle(fontSize: 12, color: settings.minimalModeEnabled ? Colors.greenAccent : Colors.grey),
            ),
            trailing: Switch(
              value: settings.minimalModeEnabled,
              onChanged: (v) async {
                await settings.setMinimalModeEnabled(v);
                HapticFeedback.selectionClick();
              },
            ),
            onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const MinimalModeSubmenuPage())),
          ),

          const _SectionHeader(title: 'FRICTION'),
          _SwitchTile(
            title: 'Mindfulness Gate',
            subtitle: 'Breath screen before opening Instagram',
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
          // Tap to Unblur as child toggle (shown directly under Blur Feed when enabled)
          if (settings.blurExplore)
            Padding(
              padding: const EdgeInsets.only(left: 32),
              child: _SwitchTile(
                title: 'Tap to Unblur',
                subtitle: 'First tap reveals the post (doesn\'t open it)',
                value: settings.tapToUnblur,
                onChanged: (v) => settings.setTapToUnblur(v),
              ),
            ),

          const SizedBox(height: 40),
        ],
      ),
    );
  }
}

// ─── Minimal Mode Submenu ─────────────────────────────────────────────────────

class MinimalModeSubmenuPage extends StatefulWidget {
  const MinimalModeSubmenuPage({super.key});

  @override
  State<MinimalModeSubmenuPage> createState() => _MinimalModeSubmenuPageState();
}

class _MinimalModeSubmenuPageState extends State<MinimalModeSubmenuPage> {
  late bool _blurExplore;
  late bool _disableReelsEntirely;
  late bool _disableExploreEntirely;

  @override
  void initState() {
    super.initState();
    final settings = context.read<SettingsService>();
    _blurExplore = settings.blurExplore;
    _disableReelsEntirely = settings.disableReelsEntirely;
    _disableExploreEntirely = settings.disableExploreEntirely;
  }

  void _updateSetting(String key, bool value) {
    final settings = context.read<SettingsService>();
    setState(() {
      switch (key) {
        case 'blurExplore':
          _blurExplore = value;
          settings.setBlurExplore(value);
          break;
        case 'disableReelsEntirely':
          _disableReelsEntirely = value;
          settings.setDisableReelsEntirelyInternal(value);
          break;
        case 'disableExploreEntirely':
          _disableExploreEntirely = value;
          settings.setDisableExploreEntirelyInternal(value);
          break;
      }
    });
    HapticFeedback.selectionClick();
  }

  Future<void> _turnOnMinimalMode() async {
    final settings = context.read<SettingsService>();
    await settings.setMinimalModeEnabled(true);
    setState(() {
      _blurExplore = true;
      _disableReelsEntirely = true;
      _disableExploreEntirely = true;
    });
    HapticFeedback.mediumImpact();
  }

  Future<void> _turnOffMinimalMode() async {
    final settings = context.read<SettingsService>();
    await settings.setMinimalModeEnabled(false);
    // Refresh local state after turning off
    setState(() {
      _blurExplore = settings.blurExplore;
      _disableReelsEntirely = settings.disableReelsEntirely;
      _disableExploreEntirely = settings.disableExploreEntirely;
    });
    HapticFeedback.mediumImpact();
  }

  @override
  Widget build(BuildContext context) {
    final settings = context.watch<SettingsService>();
    final isDark = settings.isDarkMode;
    final isMinimalModeEnabled = settings.minimalModeEnabled;

    return Scaffold(
      appBar: _subAppBar(context, 'Minimal Mode'),
      body: ListView(
        children: [
          Container(
            margin: const EdgeInsets.all(16),
            padding: const EdgeInsets.all(20),
            decoration: BoxDecoration(
              gradient: LinearGradient(
                colors: isMinimalModeEnabled 
                    ? [Colors.redAccent.withValues(alpha: 0.2), Colors.red.withValues(alpha: 0.1)]
                    : [Colors.grey.withValues(alpha: 0.1), Colors.grey.withValues(alpha: 0.05)],
                begin: Alignment.topLeft,
                end: Alignment.bottomRight,
              ),
              borderRadius: BorderRadius.circular(16),
              border: Border.all(
                color: isMinimalModeEnabled ? Colors.redAccent.withValues(alpha: 0.3) : Colors.grey.withValues(alpha: 0.2),
              ),
            ),
            child: Column(
              children: [
                Icon(
                  isMinimalModeEnabled ? Icons.shield_rounded : Icons.shield_outlined,
                  color: isMinimalModeEnabled ? Colors.redAccent : Colors.grey,
                  size: 48,
                ),
                const SizedBox(height: 12),
                Text(
                  isMinimalModeEnabled ? 'Minimal Mode Active' : 'Minimal Mode Disabled',
                  style: TextStyle(
                    fontSize: 20,
                    fontWeight: FontWeight.bold,
                    color: isMinimalModeEnabled ? Colors.redAccent : Colors.grey,
                  ),
                ),
                const SizedBox(height: 8),
                Text(
                  isMinimalModeEnabled 
                      ? 'Distractions are blocked. Customize which features stay enabled below.'
                      : 'Turn on to block all distractions at once, or customize individual settings below.',
                  textAlign: TextAlign.center,
                  style: TextStyle(fontSize: 13, color: isDark ? Colors.white54 : Colors.black54),
                ),
                const SizedBox(height: 16),
                SizedBox(
                  width: double.infinity,
                  child: ElevatedButton(
                    onPressed: isMinimalModeEnabled ? _turnOffMinimalMode : _turnOnMinimalMode,
                    style: ElevatedButton.styleFrom(
                      backgroundColor: isMinimalModeEnabled ? Colors.grey : Colors.redAccent,
                      foregroundColor: Colors.white,
                      padding: const EdgeInsets.symmetric(vertical: 14),
                      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                    ),
                    child: Text(isMinimalModeEnabled ? 'Turn Off Minimal Mode' : 'Turn On Minimal Mode'),
                  ),
                ),
              ],
            ),
          ),
          
          const _SectionHeader(title: 'CUSTOMIZE SETTINGS'),
          
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
                Icon(Icons.touch_app_rounded, size: 14, color: Colors.blueAccent),
                SizedBox(width: 8),
                Expanded(
                  child: Text(
                    'Toggle settings below to customize what gets enabled when Minimal Mode is turned ON.',
                    style: TextStyle(fontSize: 11, color: Colors.blueAccent),
                  ),
                ),
              ],
            ),
          ),
          
          _SwitchTile(
            title: 'Blur Feed & Explore',
            subtitle: 'Blurs post thumbnails until tapped',
            value: _blurExplore,
            onChanged: (v) => _updateSetting('blurExplore', v),
          ),
          _SwitchTile(
            title: 'Disable Reels Entirely',
            subtitle: 'Block all Reels with no session option',
            value: _disableReelsEntirely,
            onChanged: (v) => _updateSetting('disableReelsEntirely', v),
          ),
          _SwitchTile(
            title: 'Disable Explore Entirely',
            subtitle: 'Block Explore completely (not just blur)',
            value: _disableExploreEntirely,
            onChanged: (v) => _updateSetting('disableExploreEntirely', v),
          ),
          
          const SizedBox(height: 40),
        ],
      ),
    );
  }
}

// ─── Appearance ───────────────────────────────────────────────────────────────

class AppearancePage extends StatefulWidget {
  const AppearancePage({super.key});

  @override
  State<AppearancePage> createState() => _AppearancePageState();
}

class _AppearancePageState extends State<AppearancePage> {
  Future<void> _addSchedule(BuildContext context, SettingsService settings) async {
    TimeOfDay? startTime = await showTimePicker(
      context: context,
      initialTime: const TimeOfDay(hour: 21, minute: 0),
      helpText: 'Select start time',
    );
    
    if (startTime == null || !context.mounted) return;
    
    TimeOfDay? endTime = await showTimePicker(
      context: context,
      initialTime: const TimeOfDay(hour: 6, minute: 0),
      helpText: 'Select end time',
    );
    
    if (endTime == null || !context.mounted) return;
    
    final newSchedule = {
      'enabled': true,
      'startTime': '${startTime.hour.toString().padLeft(2, '0')}:${startTime.minute.toString().padLeft(2, '0')}',
      'endTime': '${endTime.hour.toString().padLeft(2, '0')}:${endTime.minute.toString().padLeft(2, '0')}',
    };
    
    await settings.addGrayscaleSchedule(newSchedule);
  }

  Future<void> _editSchedule(BuildContext context, SettingsService settings, int index) async {
    final schedules = settings.grayscaleSchedules;
    if (index >= schedules.length) return;
    
    final current = schedules[index];
    final startParts = (current['startTime'] as String).split(':');
    final endParts = (current['endTime'] as String).split(':');
    
    // Capture context before async gap
    final capturedContext = context;
    
    TimeOfDay? startTime = await showTimePicker(
      context: capturedContext,
      initialTime: TimeOfDay(
        hour: int.parse(startParts[0]),
        minute: int.parse(startParts[1]),
      ),
      helpText: 'Select start time',
    );
    
    if (startTime == null || !capturedContext.mounted) return;
    
    TimeOfDay? endTime = await showTimePicker(
      context: capturedContext,
      initialTime: TimeOfDay(
        hour: int.parse(endParts[0]),
        minute: int.parse(endParts[1]),
      ),
      helpText: 'Select end time',
    );
    
    if (endTime == null || !capturedContext.mounted) return;
    
    final updatedSchedule = {
      ...current,
      'startTime': '${startTime.hour.toString().padLeft(2, '0')}:${startTime.minute.toString().padLeft(2, '0')}',
      'endTime': '${endTime.hour.toString().padLeft(2, '0')}:${endTime.minute.toString().padLeft(2, '0')}',
    };
    
    await settings.updateGrayscaleSchedule(index, updatedSchedule);
  }

  Future<void> _toggleSchedule(SettingsService settings, int index) async {
    final schedules = List<Map<String, dynamic>>.from(settings.grayscaleSchedules);
    if (index >= schedules.length) return;
    
    schedules[index] = {
      ...schedules[index],
      'enabled': !(schedules[index]['enabled'] as bool),
    };
    
    await settings.setGrayscaleSchedules(schedules);
  }

  Future<void> _deleteSchedule(SettingsService settings, int index) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: const Text('Delete Schedule'),
        content: const Text('Are you sure you want to delete this schedule?'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: const Text('Cancel'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(context, true),
            child: const Text('Delete'),
          ),
        ],
      ),
    );
    
    if (confirmed == true) {
      await settings.removeGrayscaleSchedule(index);
    }
  }

  String _formatTimeRange(Map<String, dynamic> schedule) {
    return '${schedule['startTime']} - ${schedule['endTime']}';
  }

  @override
  Widget build(BuildContext context) {
    final settings = context.watch<SettingsService>();
    final isDark = settings.isDarkMode;
    return Scaffold(
      appBar: _subAppBar(context, 'Appearance'),
      body: ListView(
        children: [
          const _SectionHeader(title: 'DISPLAY'),
          _SwitchTile(
            title: 'Grayscale Mode',
            subtitle: 'Makes Instagram black & white — reduces dopamine response',
            value: settings.grayscaleEnabled,
            onChanged: (v) => settings.setGrayscaleEnabled(v),
          ),
          const _SectionHeader(title: 'GRAYSCALE SCHEDULES'),
          Container(
            margin: const EdgeInsets.fromLTRB(16, 0, 16, 12),
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
            decoration: BoxDecoration(
              color: Colors.blue.withValues(alpha: 0.07),
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: Colors.blue.withValues(alpha: 0.15)),
            ),
            child: const Text(
              'Auto-enable grayscale during specific hours. Similar to Scheduled Blocking in Guardrails. You can add multiple schedules.',
              style: TextStyle(fontSize: 12, height: 1.5),
            ),
          ),
          
          // Status indicator
          if (settings.grayscaleSchedules.isNotEmpty)
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
              child: Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: settings.isGrayscaleActiveNow ? Colors.green.withValues(alpha: 0.1) : Colors.orange.withValues(alpha: 0.1),
                  borderRadius: BorderRadius.circular(10),
                  border: Border.all(color: settings.isGrayscaleActiveNow ? Colors.green.withValues(alpha: 0.3) : Colors.orange.withValues(alpha: 0.3)),
                ),
                child: Row(
                  children: [
                    Icon(
                      settings.isGrayscaleActiveNow ? Icons.check_circle : Icons.schedule, 
                      color: settings.isGrayscaleActiveNow ? Colors.greenAccent : Colors.orangeAccent, 
                      size: 20
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Text(
                        settings.isGrayscaleActiveNow 
                            ? 'Grayscale is active now' 
                            : 'Grayscale is currently inactive',
                        style: TextStyle(
                          fontSize: 13, 
                          color: settings.isGrayscaleActiveNow ? Colors.greenAccent : Colors.orangeAccent
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ),
          
          // Schedule list
          ...List.generate(settings.grayscaleSchedules.length, (index) {
            final schedule = settings.grayscaleSchedules[index];
            final isEnabled = schedule['enabled'] as bool;
            return ListTile(
              leading: Container(
                width: 36,
                height: 36,
                decoration: BoxDecoration(
                  color: (isEnabled ? Colors.purpleAccent : Colors.grey).withValues(alpha: 0.12),
                  borderRadius: BorderRadius.circular(10),
                ),
                child: Icon(
                  isEnabled ? Icons.play_circle_outline : Icons.pause_circle_outline,
                  color: isEnabled ? Colors.purpleAccent : Colors.grey,
                  size: 20,
                ),
              ),
              title: Text(
                _formatTimeRange(schedule),
                style: TextStyle(
                  color: isEnabled ? Colors.purpleAccent : Colors.grey,
                  fontWeight: FontWeight.w500,
                ),
              ),
              subtitle: Text(
                isEnabled ? 'Active' : 'Disabled',
                style: TextStyle(fontSize: 12, color: isDark ? Colors.white54 : Colors.black45),
              ),
              trailing: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Switch(
                    value: isEnabled,
                    onChanged: (v) => _toggleSchedule(settings, index),
                  ),
                  PopupMenuButton<String>(
                    onSelected: (value) {
                      if (value == 'edit') {
                        _editSchedule(context, settings, index);
                      } else if (value == 'delete') {
                        _deleteSchedule(settings, index);
                      }
                    },
                    itemBuilder: (context) => [
                      const PopupMenuItem(
                        value: 'edit',
                        child: Row(
                          children: [
                            Icon(Icons.edit, size: 18),
                            SizedBox(width: 8),
                            Text('Edit'),
                          ],
                        ),
                      ),
                      const PopupMenuItem(
                        value: 'delete',
                        child: Row(
                          children: [
                            Icon(Icons.delete, size: 18, color: Colors.red),
                            SizedBox(width: 8),
                            Text('Delete', style: TextStyle(color: Colors.red)),
                          ],
                        ),
                      ),
                    ],
                  ),
                ],
              ),
              onTap: () => _editSchedule(context, settings, index),
            );
          }),
          
          // Add schedule button
          ListTile(
            leading: Container(
              width: 36,
              height: 36,
              decoration: BoxDecoration(
                color: Colors.green.withValues(alpha: 0.12),
                borderRadius: BorderRadius.circular(10),
              ),
              child: const Icon(Icons.add_circle_outline, color: Colors.green, size: 20),
            ),
            title: const Text('Add Schedule', style: TextStyle(color: Colors.green)),
            subtitle: Text(
              'Add a new grayscale schedule',
              style: TextStyle(fontSize: 12, color: isDark ? Colors.white54 : Colors.black45),
            ),
            onTap: () => _addSchedule(context, settings),
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
          _SwitchTile(
            title: 'Persistent Notification',
            subtitle: 'Show ongoing notification while using Instagram',
            value: settings.notifyPersistent,
            onChanged: (v) => settings.setNotifyPersistent(v),
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
  final bool value;
  final ValueChanged<bool> onChanged;

  const _SwitchTile({
    required this.title,
    this.subtitle,
    required this.value,
    required this.onChanged,
  });

  @override
  Widget build(BuildContext context) {
    return SwitchListTile(
      title: Text(
        title,
        style: const TextStyle(fontSize: 15),
      ),
      subtitle: subtitle != null
          ? Text(
              subtitle ?? '',
              style: const TextStyle(fontSize: 12),
            )
          : null,
      value: value,
      onChanged: onChanged,
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
