import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:url_launcher/url_launcher.dart';

import 'update_checker_service.dart';

class UpdateBanner extends StatefulWidget {
  final UpdateInfo updateInfo;
  final VoidCallback onDismiss;

  const UpdateBanner({
    super.key,
    required this.updateInfo,
    required this.onDismiss,
  });

  @override
  State<UpdateBanner> createState() => _UpdateBannerState();
}

class _UpdateBannerState extends State<UpdateBanner> {
  bool _isExpanded = false;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final colorScheme = theme.colorScheme;

    return AnimatedContainer(
      duration: const Duration(milliseconds: 200),
      width: double.infinity,
      decoration: BoxDecoration(
        color: colorScheme.secondaryContainer,
        border: Border(
          bottom: BorderSide(
            color: colorScheme.outlineVariant,
            width: 0.5,
          ),
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
            child: Row(
              children: [
                const Text('🎉', style: TextStyle(fontSize: 16)),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    'FocusGram ${widget.updateInfo.latestVersion} available',
                    style: theme.textTheme.bodyMedium?.copyWith(
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ),
                IconButton(
                  icon: Icon(
                    _isExpanded ? Icons.expand_less : Icons.expand_more,
                    size: 18,
                  ),
                  onPressed: () {
                    HapticFeedback.lightImpact();
                    setState(() => _isExpanded = !_isExpanded);
                  },
                ),
                IconButton(
                  icon: const Icon(Icons.close, size: 18),
                  onPressed: () {
                    HapticFeedback.lightImpact();
                    widget.onDismiss();
                  },
                ),
              ],
            ),
          ),
          if (_isExpanded) ...[
            const Divider(height: 1),
            Padding(
              padding: const EdgeInsets.fromLTRB(12, 8, 12, 12),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    "What's new",
                    style: theme.textTheme.bodyMedium?.copyWith(
                      fontWeight: FontWeight.bold,
                    ),
                  ),
                  const SizedBox(height: 8),
                  Text(
                    _formatReleaseNotes(widget.updateInfo.whatsNew),
                    style: theme.textTheme.bodySmall,
                  ),
                  const SizedBox(height: 12),
                  SizedBox(
                    width: double.infinity,
                    child: OutlinedButton(
                      onPressed: () async {
                        final uri = Uri.parse(widget.updateInfo.releaseUrl);
                        await launchUrl(
                          uri,
                          mode: LaunchMode.externalApplication,
                        );
                      },
                      child: const Text('Download on GitHub'),
                    ),
                  ),
                ],
              ),
            ),
          ],
        ],
      ),
    );
  }

  String _formatReleaseNotes(String raw) {
    var text = raw;
    text = text.replaceAll(RegExp(r'#{1,6}\s'), '');
    text = text.replaceAll(RegExp(r'\*\*(.*?)\*\*'), r'\1');
    text = text.replaceAll(RegExp(r'\*(.*?)\*'), r'\1');
    text =
        text.replaceAll(RegExp(r'\[([^\]]+)\]\([^)]+\)'), r'\1'); // links -> text
    text = text.replaceAll(RegExp(r'`([^`]+)`'), r'\1');
    return text.trim();
  }
}

