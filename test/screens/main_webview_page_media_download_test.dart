import 'package:flutter_test/flutter_test.dart';
import 'package:focusgram/screens/main_webview_page.dart';

void main() {
  group('handleFocusGramMediaDownload', () {
    test('rejects non-http(s) schemes', () async {
      final launched = <Uri>[];
      final ok = await handleFocusGramMediaDownload(
        raw: '{"type":"video","url":"file:///etc/passwd","filename":"x"}',
        launch: (uri) async => launched.add(uri),
      );

      expect(ok, isFalse);
      expect(launched, isEmpty);
    });

    test('accepts http(s) instagram-like hosts and calls launcher', () async {
      final launched = <Uri>[];
      final ok = await handleFocusGramMediaDownload(
        raw:
            '{"type":"video","url":"https://cdninstagram.com/v/1.mp4","filename":"x"}',
        launch: (uri) async => launched.add(uri),
      );

      expect(ok, isTrue);
      expect(launched, hasLength(1));
      expect(launched.first.scheme, 'https');
      expect(launched.first.host.toLowerCase(), contains('cdninstagram.com'));
    });

    test('rejects non-instagram hosts even if http(s)', () async {
      final launched = <Uri>[];
      final ok = await handleFocusGramMediaDownload(
        raw:
            '{"type":"video","url":"https://example.com/video.mp4","filename":"x"}',
        launch: (uri) async => launched.add(uri),
      );

      expect(ok, isFalse);
      expect(launched, isEmpty);
    });

    test('rejects malformed JSON safely', () async {
      final launched = <Uri>[];
      final ok = await handleFocusGramMediaDownload(
        raw: '{not json',
        launch: (uri) async => launched.add(uri),
      );

      expect(ok, isFalse);
      expect(launched, isEmpty);
    });

    test('rejects missing url field', () async {
      final launched = <Uri>[];
      final ok = await handleFocusGramMediaDownload(
        raw: '{"type":"video","filename":"x"}',
        launch: (uri) async => launched.add(uri),
      );

      expect(ok, isFalse);
      expect(launched, isEmpty);
    });
  });
}
