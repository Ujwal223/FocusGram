import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:flutter_inappwebview/flutter_inappwebview.dart';
import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';

class AdblockContentBlockerData {
  final List<ContentBlocker> contentBlockers;
  final Set<String> blockedHosts;
  final String sourceTag;

  const AdblockContentBlockerData({
    required this.contentBlockers,
    required this.blockedHosts,
    required this.sourceTag,
  });

  Map<String, dynamic> toJson() => {
    'sourceTag': sourceTag,
    'hosts': blockedHosts.toList(),
    // We can’t safely serialize ContentBlocker objects; rebuild from hosts.
    // contentBlockers will always be regenerated from hosts when restoring.
  };

  static AdblockContentBlockerData fromJson(Map<String, dynamic> json) {
    final hosts =
        (json['hosts'] as List?)?.whereType<String>().toSet() ?? <String>{};
    return AdblockContentBlockerData(
      contentBlockers: hosts
          .map(
            (h) => ContentBlocker(
              trigger: ContentBlockerTrigger(
                urlFilter: AdblockContentBlockerLoader._urlFilterForHost(h),
              ),
              action: ContentBlockerAction(
                type: ContentBlockerActionType.BLOCK,
              ),
            ),
          )
          .toList(growable: false),
      blockedHosts: hosts,
      sourceTag: (json['sourceTag'] as String?) ?? 'cached',
    );
  }
}

class AdblockContentBlockerLoader {
  // Cache keys
  static const _keyCache = 'adblock_cb_cache_v2';
  static const _keyCacheUpdatedAt = 'adblock_cb_cache_updated_at_v1';
  static const _keySourceCache = 'adblock_source_cache_v1';

  static const _maxContentBlockerRules = 5000;

  // Raw GitHub sources, intentionally split by repository sections so the app
  // follows upstream changes without depending on third-party packaged mirrors.
  static const _sources = <_SourceSpec>[
    // uBlock Origin built-in Annoyances family:
    // https://github.com/uBlockOrigin/uAssets/tree/master/filters
    _SourceSpec(
      tag: 'ublock_annoyances',
      url:
          'https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/annoyances.txt',
    ),
    _SourceSpec(
      tag: 'ublock_annoyances_cookies',
      url:
          'https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/annoyances-cookies.txt',
    ),
    _SourceSpec(
      tag: 'ublock_annoyances_others',
      url:
          'https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/annoyances-others.txt',
    ),

    // EasyList network-blocking sections:
    // https://github.com/easylist/easylist/tree/master/easylist
    _SourceSpec(
      tag: 'easylist_adservers',
      url:
          'https://raw.githubusercontent.com/easylist/easylist/master/easylist/easylist_adservers.txt',
    ),
    _SourceSpec(
      tag: 'easylist_general_block',
      url:
          'https://raw.githubusercontent.com/easylist/easylist/master/easylist/easylist_general_block.txt',
    ),
    _SourceSpec(
      tag: 'easylist_specific_block',
      url:
          'https://raw.githubusercontent.com/easylist/easylist/master/easylist/easylist_specific_block.txt',
    ),
    _SourceSpec(
      tag: 'easylist_thirdparty',
      url:
          'https://raw.githubusercontent.com/easylist/easylist/master/easylist/easylist_thirdparty.txt',
    ),

    // AdGuard BaseFilter network-blocking sections:
    // https://github.com/AdguardTeam/AdguardFilters/tree/master/BaseFilter/sections
    _SourceSpec(
      tag: 'adguard_base_adservers',
      url:
          'https://raw.githubusercontent.com/AdguardTeam/AdguardFilters/master/BaseFilter/sections/adservers.txt',
    ),
    _SourceSpec(
      tag: 'adguard_base_adservers_firstparty',
      url:
          'https://raw.githubusercontent.com/AdguardTeam/AdguardFilters/master/BaseFilter/sections/adservers_firstparty.txt',
    ),
    _SourceSpec(
      tag: 'adguard_base_antiadblock',
      url:
          'https://raw.githubusercontent.com/AdguardTeam/AdguardFilters/master/BaseFilter/sections/antiadblock.txt',
    ),
    _SourceSpec(
      tag: 'adguard_base_cryptominers',
      url:
          'https://raw.githubusercontent.com/AdguardTeam/AdguardFilters/master/BaseFilter/sections/cryptominers.txt',
    ),
    _SourceSpec(
      tag: 'adguard_base_general_url',
      url:
          'https://raw.githubusercontent.com/AdguardTeam/AdguardFilters/master/BaseFilter/sections/general_url.txt',
    ),
    _SourceSpec(
      tag: 'adguard_base_specific',
      url:
          'https://raw.githubusercontent.com/AdguardTeam/AdguardFilters/master/BaseFilter/sections/specific.txt',
    ),
  ];

  Future<AdblockContentBlockerData> loadOrUpdateIfNeeded({
    required bool enabled,
    required SharedPreferences prefs,
    int timeoutMs = 8000,
  }) async {
    if (!enabled) {
      return const AdblockContentBlockerData(
        contentBlockers: [],
        blockedHosts: {},
        sourceTag: 'disabled',
      );
    }

    final cachedData = _readCachedData(prefs);
    final sourceCache = _readSourceCache(prefs);

    final fetchResults = await _fetchAllSources(
      cache: sourceCache,
      timeoutMs: timeoutMs,
    );

    if (fetchResults.isEmpty && cachedData != null) {
      return cachedData;
    }

    final sourceEntries = <String, _CachedSource>{...sourceCache};
    for (final result in fetchResults) {
      sourceEntries[result.tag] = result.source;
    }

    final hosts = sourceEntries.values
        .expand((source) => source.hosts)
        .where(_isValidHostname)
        .toSet();

    if (hosts.isEmpty && cachedData != null) {
      return cachedData;
    }

    final data = _buildData(
      hosts: hosts,
      sourceTag: fetchResults.any((r) => r.changed)
          ? 'updated-github'
          : 'validated-github-cache',
    );

    await prefs.setString(_keyCache, jsonEncode(data.toJson()));
    await prefs.setString(
      _keySourceCache,
      jsonEncode({
        for (final entry in sourceEntries.entries) entry.key: entry.value,
      }),
    );
    await prefs.setInt(
      _keyCacheUpdatedAt,
      DateTime.now().millisecondsSinceEpoch,
    );

    return data;
  }

  AdblockContentBlockerData? _readCachedData(SharedPreferences prefs) {
    final cached = prefs.getString(_keyCache);
    if (cached == null) return null;
    try {
      final decoded = jsonDecode(cached) as Map<String, dynamic>;
      return AdblockContentBlockerData.fromJson(decoded);
    } catch (_) {
      return null;
    }
  }

  Map<String, _CachedSource> _readSourceCache(SharedPreferences prefs) {
    final cached = prefs.getString(_keySourceCache);
    if (cached == null) return {};
    try {
      final decoded = jsonDecode(cached) as Map<String, dynamic>;
      return decoded.map((tag, value) {
        return MapEntry(
          tag,
          _CachedSource.fromJson(value as Map<String, dynamic>),
        );
      });
    } catch (_) {
      return {};
    }
  }

  AdblockContentBlockerData _buildData({
    required Set<String> hosts,
    required String sourceTag,
  }) {
    final sortedHosts = hosts.toList(growable: false)..sort();
    final cappedHosts = sortedHosts.take(_maxContentBlockerRules).toSet();

    return AdblockContentBlockerData(
      contentBlockers: cappedHosts
          .map(
            (h) => ContentBlocker(
              trigger: ContentBlockerTrigger(urlFilter: _urlFilterForHost(h)),
              action: ContentBlockerAction(
                type: ContentBlockerActionType.BLOCK,
              ),
            ),
          )
          .toList(growable: false),
      blockedHosts: cappedHosts,
      sourceTag: sourceTag,
    );
  }

  Future<List<_FetchedSource>> _fetchAllSources({
    required Map<String, _CachedSource> cache,
    required int timeoutMs,
  }) async {
    final client = http.Client();
    try {
      final timeout = Duration(milliseconds: timeoutMs);
      return Future.wait(
        _sources.map(
          (source) => _fetchSource(
            client: client,
            source: source,
            cached: cache[source.tag],
            timeout: timeout,
          ),
        ),
      ).then((results) => results.whereType<_FetchedSource>().toList());
    } finally {
      client.close();
    }
  }

  Future<_FetchedSource?> _fetchSource({
    required http.Client client,
    required _SourceSpec source,
    required _CachedSource? cached,
    required Duration timeout,
  }) async {
    try {
      final headers = <String, String>{
        if (cached?.etag != null) 'If-None-Match': cached!.etag!,
        if (cached?.lastModified != null)
          'If-Modified-Since': cached!.lastModified!,
        'User-Agent': 'FocusGram-AdblockListUpdater',
      };

      final res = await client
          .get(Uri.parse(source.url), headers: headers)
          .timeout(timeout);

      if (res.statusCode == 304 && cached != null) {
        return _FetchedSource(tag: source.tag, source: cached, changed: false);
      }

      if (res.statusCode != 200 || res.body.isEmpty) return null;

      return _FetchedSource(
        tag: source.tag,
        source: _CachedSource(
          url: source.url,
          etag: res.headers['etag'],
          lastModified: res.headers['last-modified'],
          hosts: parseHostsFromFilterText(res.body),
        ),
        changed: true,
      );
    } catch (_) {
      return null;
    }
  }

  /// Strict/strong: we only extract domain-ish entries from common uBlock/EasyList
  /// syntax forms:
  ///  - ||example.com^
  ///  - ||example.com/
  ///  - ||example.com
  ///
  /// We ignore all element-hiding/cosmetic rules and $ options.
  @visibleForTesting
  static Set<String> parseHostsFromFilterText(String raw) {
    final hosts = <String>{};

    for (final line in raw.split('\n')) {
      final l = line.trim();
      if (l.isEmpty) continue;
      if (l.startsWith('!')) continue;
      if (l.startsWith('@@')) continue;

      // Skip comments / metadata
      if (l.startsWith('[')) continue;

      // Skip cosmetic element-hiding rules
      if (l.contains('##') || l.contains('#@#') || l.contains(r'#$#')) {
        continue;
      }

      // uBlock-style host anchors
      if (l.startsWith('||')) {
        final body = l.substring(2);

        // Drop anything after a separator like '^', '/', '?', ' ' (conservative)
        // e.g. "example.com^" -> "example.com"
        // e.g. "example.com/" -> "example.com"
        // e.g. "example.com^$third-party" -> "example.com"
        final stopChars = ['^', '/', '?', '\\', '|', '\t', ' ', r'$'];

        String host = body;
        for (final sc in stopChars) {
          final idx = host.indexOf(sc);
          if (idx >= 0) host = host.substring(0, idx);
        }

        host = host.trim();

        // Remove leading/trailing dots
        host = host
            .replaceAll(RegExp(r'^\.+'), '')
            .replaceAll(RegExp(r'\.+$'), '');

        if (host.isEmpty) continue;
        if (host.contains('*') || host.contains(',')) continue;

        final normalized = host.toLowerCase();
        if (!_isValidHostname(normalized)) continue;

        hosts.add(normalized);
      }
    }

    return hosts;
  }

  static String _urlFilterForHost(String host) {
    final escaped = RegExp.escape(host);
    return r'^https?://([^/?#]+\.)?'
        '$escaped'
        r'([/?#:].*)?$';
  }

  static bool _isValidHostname(String host) {
    if (!host.contains('.')) return false;
    if (host.length > 255) return false;
    if (host.startsWith('.') || host.endsWith('.')) return false;
    if (host.contains('..')) return false;
    return RegExp(r'^[a-z0-9][a-z0-9.-]*[a-z0-9]$').hasMatch(host);
  }
}

class _SourceSpec {
  final String tag;
  final String url;

  const _SourceSpec({required this.tag, required this.url});
}

class _FetchedSource {
  final String tag;
  final _CachedSource source;
  final bool changed;

  _FetchedSource({
    required this.tag,
    required this.source,
    required this.changed,
  });
}

class _CachedSource {
  final String url;
  final String? etag;
  final String? lastModified;
  final Set<String> hosts;

  const _CachedSource({
    required this.url,
    required this.etag,
    required this.lastModified,
    required this.hosts,
  });

  factory _CachedSource.fromJson(Map<String, dynamic> json) {
    return _CachedSource(
      url: (json['url'] as String?) ?? '',
      etag: json['etag'] as String?,
      lastModified: json['lastModified'] as String?,
      hosts: (json['hosts'] as List?)?.whereType<String>().toSet() ?? {},
    );
  }

  Map<String, dynamic> toJson() => {
    'url': url,
    'etag': etag,
    'lastModified': lastModified,
    'hosts': hosts.toList(growable: false)..sort(),
  };
}
