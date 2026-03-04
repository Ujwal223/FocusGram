import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import 'reels_history_service.dart';

class ReelsHistoryScreen extends StatefulWidget {
  const ReelsHistoryScreen({super.key});

  @override
  State<ReelsHistoryScreen> createState() => _ReelsHistoryScreenState();
}

class _ReelsHistoryScreenState extends State<ReelsHistoryScreen> {
  final _service = ReelsHistoryService();
  late Future<List<ReelsHistoryEntry>> _future;

  @override
  void initState() {
    super.initState();
    _future = _service.getEntries();
  }

  Future<void> _refresh() async {
    setState(() => _future = _service.getEntries());
  }

  String _formatTimestamp(DateTime dt) =>
      DateFormat('EEE, MMM d • h:mm a').format(dt.toLocal());

  String _relativeTime(DateTime dt) {
    final diff = DateTime.now().difference(dt.toLocal());
    if (diff.inMinutes < 60) return '${diff.inMinutes}m ago';
    if (diff.inHours < 24) return '${diff.inHours}h ago';
    if (diff.inDays < 7) return '${diff.inDays}d ago';
    return _formatTimestamp(dt);
  }

  Future<void> _confirmClearAll() async {
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Clear Reels History?'),
        content: const Text(
          'This removes all history entries stored locally on this device.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Cancel'),
          ),
          ElevatedButton(
            style: ElevatedButton.styleFrom(backgroundColor: Colors.redAccent),
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('Clear All'),
          ),
        ],
      ),
    );
    if (ok != true || !mounted) return;
    await _service.clearAll();
    await _refresh();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text(
          'Reels History',
          style: TextStyle(fontSize: 17, fontWeight: FontWeight.w600),
        ),
        centerTitle: true,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back_ios_new, size: 18),
          onPressed: () => Navigator.pop(context),
        ),
        actions: [
          IconButton(
            tooltip: 'Clear All',
            onPressed: _confirmClearAll,
            icon: const Icon(Icons.delete_outline),
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: _refresh,
        child: FutureBuilder<List<ReelsHistoryEntry>>(
          future: _future,
          builder: (context, snapshot) {
            if (snapshot.connectionState == ConnectionState.waiting) {
              return const Center(child: CircularProgressIndicator());
            }

            final entries = snapshot.data ?? const <ReelsHistoryEntry>[];

            return ListView(
              physics: const AlwaysScrollableScrollPhysics(),
              children: [
                Padding(
                  padding: const EdgeInsets.fromLTRB(16, 12, 16, 4),
                  child: Row(
                    children: [
                      const Icon(
                        Icons.lock_outline,
                        size: 12,
                        color: Colors.grey,
                      ),
                      const SizedBox(width: 6),
                      Text(
                        '${entries.length} reels stored locally on device only',
                        style: const TextStyle(
                          fontSize: 12,
                          color: Colors.grey,
                        ),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 4),
                if (entries.isEmpty)
                  const Padding(
                    padding: EdgeInsets.all(48),
                    child: Center(
                      child: Column(
                        children: [
                          Icon(
                            Icons.play_circle_outline,
                            size: 48,
                            color: Colors.grey,
                          ),
                          SizedBox(height: 12),
                          Text(
                            'No Reels history yet.\nWatch a Reel and it will appear here.',
                            textAlign: TextAlign.center,
                            style: TextStyle(color: Colors.grey),
                          ),
                        ],
                      ),
                    ),
                  )
                else
                  ...entries.map((entry) {
                    return Dismissible(
                      key: ValueKey(entry.id),
                      direction: DismissDirection.endToStart,
                      background: Container(
                        color: Colors.redAccent.withValues(alpha: 0.15),
                        alignment: Alignment.centerRight,
                        padding: const EdgeInsets.only(right: 20),
                        child: const Icon(
                          Icons.delete_outline,
                          color: Colors.redAccent,
                        ),
                      ),
                      onDismissed: (_) async {
                        await _service.deleteEntry(entry.id);
                        // Don't call _refresh() on dismiss — removes the entry from
                        // the live list already via Dismissible, avoids double setState
                      },
                      child: ListTile(
                        contentPadding: const EdgeInsets.symmetric(
                          horizontal: 16,
                          vertical: 4,
                        ),
                        leading: _ReelThumbnail(url: entry.thumbnailUrl),
                        title: Text(
                          entry.title,
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(fontSize: 14),
                        ),
                        subtitle: Text(
                          _relativeTime(entry.visitedAt),
                          style: const TextStyle(
                            fontSize: 12,
                            color: Colors.grey,
                          ),
                        ),
                        trailing: const Icon(
                          Icons.play_circle_outline,
                          color: Colors.blue,
                          size: 20,
                        ),
                        onTap: () => Navigator.pop(context, entry.url),
                      ),
                    );
                  }),
                const SizedBox(height: 40),
              ],
            );
          },
        ),
      ),
    );
  }
}

/// Thumbnail widget that correctly sends Referer + User-Agent headers
/// required by Instagram's CDN. Without these the CDN returns 403.
class _ReelThumbnail extends StatelessWidget {
  final String url;
  const _ReelThumbnail({required this.url});

  @override
  Widget build(BuildContext context) {
    return ClipRRect(
      borderRadius: BorderRadius.circular(8),
      child: SizedBox(
        width: 60,
        height: 60,
        child: url.isEmpty
            ? _placeholder()
            : Image.network(
                url,
                width: 60,
                height: 60,
                fit: BoxFit.cover,
                headers: const {
                  // Instagram CDN requires a valid Referer header
                  'Referer': 'https://www.instagram.com/',
                  'User-Agent':
                      'Mozilla/5.0 (iPhone; CPU iPhone OS 18_6 like Mac OS X) '
                      'AppleWebKit/605.1.15 (KHTML, like Gecko) Mobile/22G86',
                },
                errorBuilder: (_, _, _) => _placeholder(),
                loadingBuilder: (_, child, progress) {
                  if (progress == null) return child;
                  return Container(
                    color: Colors.white10,
                    child: const Center(
                      child: SizedBox(
                        width: 16,
                        height: 16,
                        child: CircularProgressIndicator(strokeWidth: 1.5),
                      ),
                    ),
                  );
                },
              ),
      ),
    );
  }

  Widget _placeholder() => Container(
    color: Colors.white10,
    child: const Icon(
      Icons.play_circle_outline,
      color: Colors.white30,
      size: 28,
    ),
  );
}
