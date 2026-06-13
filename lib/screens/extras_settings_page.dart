import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';

import '../services/settings_service.dart';
import 'ghost_mode_submenu_page.dart';

class ExtrasSettingsPage extends StatelessWidget {
  const ExtrasSettingsPage({super.key});

  @override
  Widget build(BuildContext context) {
    final settings = context.watch<SettingsService>();
    return Scaffold(
      appBar: AppBar(
        title: const Text(
          'Extras',
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
          const _SectionHeader(title: 'STARTUP'),
          _LaunchPagePicker(settings: settings),
          const SizedBox(height: 8),

          const _SectionHeader(title: 'MEDIA'),
          _SwitchTile(
            title: 'Download Media (Feed + Reels)',
            subtitle: 'Adds a download icon on posts and reels',
            value: settings.videoDownloadEnabled,
            onChanged: (v) async {
              await settings.setVideoDownloadEnabled(v);
              HapticFeedback.selectionClick();
            },
          ),

          const _SectionHeader(title: 'FOCUS'),
          ListTile(
            leading: Container(
              width: 36,
              height: 36,
              decoration: BoxDecoration(
                color: settings.ghostMode
                    ? Colors.purple.withValues(alpha: 0.15)
                    : Colors.grey.withValues(alpha: 0.1),
                borderRadius: BorderRadius.circular(10),
              ),
              child: Icon(
                Icons.visibility_off_rounded,
                color: settings.ghostMode ? Colors.purpleAccent : Colors.grey,
                size: 20,
              ),
            ),
            title: const Text('Ghost Mode', style: TextStyle(fontSize: 15)),
            subtitle: Text(
              _ghostSubtitle(settings),
              style: const TextStyle(fontSize: 12),
            ),
            trailing: const Icon(Icons.chevron_right, size: 20),
            onTap: () => Navigator.push(
              context,
              MaterialPageRoute(builder: (_) => const GhostModeSubmenuPage()),
            ),
          ),

          const SizedBox(height: 40),
        ],
      ),
    );
  }
}

String _ghostSubtitle(SettingsService s) {
  if (s.ghostMode) return 'DM Ghost active — works inside chat only';
  return 'Tap to configure ghost modes';
}

class _LaunchPagePicker extends StatelessWidget {
  final SettingsService settings;
  const _LaunchPagePicker({required this.settings});

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final options = ['home', 'following', 'favorites', 'direct'];
    final labels = {
      'home': 'Home Feed',
      'following': 'Following',
      'favorites': 'Favorites',
      'direct': 'Direct Messages',
    };

    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          DropdownButtonFormField<String>(
            initialValue: settings.startupPage,
            decoration: const InputDecoration(
              labelText: 'Launch Page',
              border: OutlineInputBorder(),
              isDense: true,
              contentPadding: EdgeInsets.symmetric(
                horizontal: 12,
                vertical: 10,
              ),
            ),
            items: options
                .map(
                  (p) => DropdownMenuItem(
                    value: p,
                    child: Text(
                      labels[p] ?? p,
                      style: const TextStyle(fontSize: 14),
                    ),
                  ),
                )
                .toList(),
            onChanged: (v) {
              if (v != null) settings.setStartupPage(v);
              HapticFeedback.selectionClick();
            },
          ),
          const SizedBox(height: 6),
          Text(
            'Choose which page opens when you launch Focusgram.',
            style: TextStyle(
              fontSize: 12,
              color: isDark ? Colors.white38 : Colors.black38,
            ),
          ),
        ],
      ),
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
      title: Text(title, style: const TextStyle(fontSize: 15)),
      subtitle: subtitle != null
          ? Text(subtitle!, style: const TextStyle(fontSize: 12))
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
