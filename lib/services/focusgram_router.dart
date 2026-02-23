import 'package:flutter/foundation.dart';

/// Lightweight global router for cross-widget navigation signals.
/// Used to allow the Settings page to trigger WebView navigations without
/// requiring a BuildContext reference to MainWebViewPage.
class FocusGramRouter {
  FocusGramRouter._();

  /// When this value is non-null, [MainWebViewPage] will load the URL
  /// in the WebView and clear this value. Settings page sets this to
  /// trigger in-app navigation (e.g. Instagram Settings).
  static final pendingUrl = ValueNotifier<String?>(null);
}
