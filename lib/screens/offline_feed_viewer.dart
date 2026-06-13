import 'package:flutter/material.dart';
import 'package:flutter_inappwebview/flutter_inappwebview.dart';
import 'package:provider/provider.dart';
import '../services/snapshot_service.dart';

/// Opens a saved page offline. Uses saved HTML content when available,
/// falls back to WebView cache.
class OfflineFeedViewer extends StatelessWidget {
  final String url;
  final String? pageId;

  const OfflineFeedViewer({super.key, required this.url, this.pageId});

  @override
  Widget build(BuildContext context) {
    // Find the saved page with HTML content
    SavedPage? page;
    if (pageId != null) {
      final ss = context.read<SnapshotService>();
      final matches = ss.savedPages.where((p) => p.id == pageId);
      if (matches.isNotEmpty) page = matches.first;
    }

    return Scaffold(
      appBar: AppBar(
        title: const Text(
          'Offline View',
          style: TextStyle(fontSize: 17, fontWeight: FontWeight.w600),
        ),
        centerTitle: true,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back_ios_new, size: 18),
          onPressed: () => Navigator.pop(context),
        ),
      ),
      body: Column(
        children: [
          Container(
            width: double.infinity,
            padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
            color: Colors.blue.withValues(alpha: 0.1),
            child: const Row(
              children: [
                Icon(
                  Icons.wifi_off_rounded,
                  size: 14,
                  color: Colors.blueAccent,
                ),
                SizedBox(width: 6),
                Text(
                  'Offline — saved content shown',
                  style: TextStyle(fontSize: 11, color: Colors.blueAccent),
                ),
              ],
            ),
          ),
          Expanded(
            child: page?.htmlContent != null
                ? InAppWebView(
                    initialSettings: InAppWebViewSettings(
                      javaScriptEnabled: true,
                      domStorageEnabled: true,
                      transparentBackground: false,
                      useHybridComposition: true,
                    ),
                    onWebViewCreated: (c) async {
                      await c.loadData(
                        data: page!.htmlContent!,
                        mimeType: 'text/html',
                        encoding: 'utf-8',
                        baseUrl: WebUri(url),
                      );
                    },
                  )
                : InAppWebView(
                    initialUrlRequest: URLRequest(url: WebUri(url)),
                    initialSettings: InAppWebViewSettings(
                      cacheEnabled: true,
                      cacheMode: CacheMode.LOAD_CACHE_ELSE_NETWORK,
                      domStorageEnabled: true,
                      javaScriptEnabled: true,
                      transparentBackground: false,
                      useHybridComposition: true,
                    ),
                  ),
          ),
        ],
      ),
    );
  }
}
