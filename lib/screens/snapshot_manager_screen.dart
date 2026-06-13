import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../services/snapshot_service.dart';
import '../services/level_service.dart';
import 'offline_feed_viewer.dart';

/// Manages saved pages for offline viewing via WebView cache.
/// Gated behind Level 5.
class SnapshotManagerScreen extends StatelessWidget {
  const SnapshotManagerScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final levelService = context.watch<LevelService>();
    final isUnlocked = levelService.level >= 5; // offline pages at L5
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return Scaffold(
      appBar: AppBar(
        title: const Text(
          'Offline Pages',
          style: TextStyle(fontSize: 17, fontWeight: FontWeight.w600),
        ),
        centerTitle: true,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back_ios_new, size: 18),
          onPressed: () => Navigator.pop(context),
        ),
      ),
      body: isUnlocked
          ? const _SavedPageList()
          : Center(
              child: Padding(
                padding: const EdgeInsets.all(32),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    Icon(
                      Icons.lock_outline,
                      size: 64,
                      color: Colors.grey.withValues(alpha: 0.4),
                    ),
                    const SizedBox(height: 16),
                    const Text(
                      'Unlocks at Level 5',
                      style: TextStyle(
                        fontSize: 18,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                    const SizedBox(height: 8),
                    Text(
                      'Earn XP to unlock offline browsing.\n'
                      'Watch ads and reduce reel time to level up.',
                      textAlign: TextAlign.center,
                      style: TextStyle(
                        fontSize: 14,
                        color: isDark ? Colors.white54 : Colors.black54,
                        height: 1.5,
                      ),
                    ),
                  ],
                ),
              ),
            ),
    );
  }
}

class _SavedPageList extends StatelessWidget {
  const _SavedPageList();

  @override
  Widget build(BuildContext context) {
    final snapshotService = context.watch<SnapshotService>();
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return Column(
      children: [
        // Info card
        Container(
          margin: const EdgeInsets.all(16),
          padding: const EdgeInsets.all(14),
          decoration: BoxDecoration(
            color: Colors.blue.withValues(alpha: 0.07),
            borderRadius: BorderRadius.circular(12),
            border: Border.all(color: Colors.blue.withValues(alpha: 0.12)),
          ),
          child: Row(
            children: [
              const Icon(
                Icons.info_outline,
                size: 16,
                color: Colors.blueAccent,
              ),
              const SizedBox(width: 10),
              Expanded(
                child: Text(
                  'The WebView already caches pages you visit. '
                  'Save bookmarks here to easily reopen them when offline.\n'
                  'No API needed — the cache handles everything.',
                  style: TextStyle(
                    fontSize: 12,
                    color: isDark ? Colors.white60 : Colors.black54,
                    height: 1.4,
                  ),
                ),
              ),
            ],
          ),
        ),

        // Header
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
          child: Row(
            children: [
              Text(
                '${snapshotService.totalSaved} saved page${snapshotService.totalSaved == 1 ? '' : 's'}',
                style: TextStyle(
                  fontSize: 12,
                  color: isDark ? Colors.white54 : Colors.black54,
                ),
              ),
              const Spacer(),
              if (snapshotService.totalSaved > 0)
                GestureDetector(
                  onTap: () => _confirmClearAll(context, snapshotService),
                  child: Text(
                    'Clear all',
                    style: TextStyle(
                      fontSize: 12,
                      color: Colors.redAccent.withValues(alpha: 0.7),
                    ),
                  ),
                ),
            ],
          ),
        ),

        // Page list
        Expanded(
          child: snapshotService.savedPages.isEmpty
              ? Center(
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Icon(
                        Icons.bookmark_border_rounded,
                        size: 48,
                        color: Colors.grey.withValues(alpha: 0.3),
                      ),
                      const SizedBox(height: 12),
                      Text(
                        'No saved pages yet',
                        style: TextStyle(
                          color: isDark ? Colors.white38 : Colors.black38,
                        ),
                      ),
                      const SizedBox(height: 4),
                      Text(
                        'Visit Instagram pages online, then save them here\nto browse offline later.',
                        textAlign: TextAlign.center,
                        style: TextStyle(
                          fontSize: 12,
                          color: isDark ? Colors.white24 : Colors.black26,
                          height: 1.4,
                        ),
                      ),
                    ],
                  ),
                )
              : ListView.builder(
                  itemCount: snapshotService.savedPages.length,
                  itemBuilder: (context, index) {
                    final page = snapshotService.savedPages[index];
                    return ListTile(
                      leading: Container(
                        width: 44,
                        height: 44,
                        decoration: BoxDecoration(
                          color: Colors.blue.withValues(alpha: 0.1),
                          borderRadius: BorderRadius.circular(10),
                        ),
                        child: const Icon(
                          Icons.web_rounded,
                          color: Colors.blueAccent,
                          size: 22,
                        ),
                      ),
                      title: Text(
                        page.title,
                        style: const TextStyle(fontSize: 14),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                      subtitle: Text(
                        _formatDate(page.savedAt),
                        style: const TextStyle(fontSize: 12),
                      ),
                      trailing: PopupMenuButton<String>(
                        onSelected: (value) {
                          if (value == 'delete') {
                            _confirmDelete(context, snapshotService, page.id);
                          } else if (value == 'open') {
                            Navigator.push(
                              context,
                              MaterialPageRoute(
                                builder: (_) => OfflineFeedViewer(
                                  url: page.url,
                                  pageId: page.id,
                                ),
                              ),
                            );
                          }
                        },
                        itemBuilder: (context) => [
                          const PopupMenuItem(
                            value: 'open',
                            child: Row(
                              children: [
                                Icon(Icons.open_in_browser, size: 18),
                                SizedBox(width: 8),
                                Text('Open Offline'),
                              ],
                            ),
                          ),
                          const PopupMenuItem(
                            value: 'delete',
                            child: Row(
                              children: [
                                Icon(
                                  Icons.delete_outline,
                                  color: Colors.redAccent,
                                  size: 18,
                                ),
                                SizedBox(width: 8),
                                Text(
                                  'Remove',
                                  style: TextStyle(color: Colors.redAccent),
                                ),
                              ],
                            ),
                          ),
                        ],
                      ),
                      onTap: () {
                        Navigator.push(
                          context,
                          MaterialPageRoute(
                            builder: (_) => OfflineFeedViewer(url: page.url),
                          ),
                        );
                      },
                    );
                  },
                ),
        ),
      ],
    );
  }

  void _confirmDelete(
    BuildContext context,
    SnapshotService service,
    String id,
  ) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Remove page?'),
        content: const Text(
          'Removes the bookmark. Cache is preserved automatically.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Cancel'),
          ),
          TextButton(
            onPressed: () {
              service.deletePage(id);
              Navigator.pop(ctx);
            },
            child: const Text(
              'Remove',
              style: TextStyle(color: Colors.redAccent),
            ),
          ),
        ],
      ),
    );
  }

  void _confirmClearAll(BuildContext context, SnapshotService service) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Clear all saved pages?'),
        content: const Text('This removes all bookmarks.'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Cancel'),
          ),
          TextButton(
            onPressed: () {
              service.deleteAll();
              Navigator.pop(ctx);
            },
            child: const Text(
              'Clear',
              style: TextStyle(color: Colors.redAccent),
            ),
          ),
        ],
      ),
    );
  }

  String _formatDate(DateTime dt) {
    final now = DateTime.now();
    final diff = now.difference(dt);
    if (diff.inMinutes < 60) return '${diff.inMinutes}m ago';
    if (diff.inHours < 24) return '${diff.inHours}h ago';
    if (diff.inDays < 7) return '${diff.inDays}d ago';
    return '${dt.month}/${dt.day} ${dt.hour}:${dt.minute.toString().padLeft(2, '0')}';
  }
}
