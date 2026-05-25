import 'package:flutter/material.dart';

import '../services/remote_popup_service.dart';

class RemotePopupHandler {
  static Future<void> checkAndShow(BuildContext context) async {
    final popup = await RemotePopupService.fetchPopup();
    if (popup == null) return;

    final shouldShow = await RemotePopupService.shouldShow(popup);
    if (!shouldShow) return;

    await RemotePopupService.markShown(popup);

    if (!context.mounted) return;

    showDialog(
      context: context,
      barrierDismissible: true,
      builder: (_) {
        return AlertDialog(
          title: Text(popup.title),
          content: Text(popup.body),
          actions: [
            TextButton(
              onPressed: () {
                Navigator.pop(context);
              },
              child: Text(popup.buttonText),
            ),
          ],
        );
      },
    );
  }
}
