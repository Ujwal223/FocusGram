import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';

import '../services/settings_service.dart';

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
          _SwitchTile(
            title: 'GHOST MODE',
            subtitle: 'Hide seen indicator / read receipts',
            value: settings.ghostMode,
            onChanged: (v) async {
              await settings.setGhostMode(v);
              HapticFeedback.selectionClick();
            },
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 16),
            child: Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: Colors.amber.withValues(alpha: 0.07),
                borderRadius: BorderRadius.circular(8),
                border: Border.all(color: Colors.amber.withValues(alpha: 0.12)),
              ),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Padding(
                    padding: const EdgeInsets.only(right: 8, top: 2),
                    child: Icon(
                      Icons.info_outline,
                      size: 14,
                      color: Colors.amber,
                    ),
                  ),
                  const Expanded(
                    child: Text(
                      'NOTE: The seen indicator is not sent to the sender while you are active in the chat, but as soon as you close and reopen the chat, the seen indicator is sent.',
                      style: TextStyle(fontSize: 11, color: Colors.amber),
                    ),
                  ),
                ],
              ),
            ),
          ),

          /* TRIED BUT IT DIDNT WORK 98% oF THE TIME) 
          
          const _SectionHeader(title: 'FOCUSGRAM V2'),
          _SwitchTile(
            title: 'Ad Blocker',
            subtitle: 'Removes ads and sponsored posts',
            value: settings.v2AdBlockerDomEnabled,
            onChanged: (v) async {
              await settings.setV2AdBlockerDomEnabled(v);
              HapticFeedback.selectionClick();
            },
          ),
          _SwitchTile(
            title: 'Block Suggested Posts',
            subtitle: 'Removes Suggested for you and recommendation units',
            value: settings.contentSuggested,
            onChanged: (v) async {
              await settings.setContentSuggestedEnabled(v);
              HapticFeedback.selectionClick();
            },
          ),
*/
          const SizedBox(height: 40),
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
          ? Text(subtitle ?? '', style: const TextStyle(fontSize: 12))
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
