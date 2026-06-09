import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import '../services/app_lock_service.dart';
import 'app_lock_setup_screen.dart';

/// App Lock settings — two independent lock modes (app-wide + messages tab),
/// each with their own toggle, all backed by a single PIN.
class AppLockSettingsPage extends StatefulWidget {
  const AppLockSettingsPage({super.key});

  @override
  State<AppLockSettingsPage> createState() => _AppLockSettingsPageState();
}

class _AppLockSettingsPageState extends State<AppLockSettingsPage> {
  Future<bool> _ensurePin() async {
    final appLock = context.read<AppLockService>();
    if (appLock.hasPin) return true;
    final ok = await Navigator.push<bool>(
      context,
      MaterialPageRoute(builder: (_) => const AppLockSetupScreen()),
    );
    return ok == true;
  }

  @override
  Widget build(BuildContext context) {
    final a = context.watch<AppLockService>();
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final anythingOn = a.lockAppWide || a.lockMessages;

    return Scaffold(
      appBar: AppBar(
        title: const Text('App Lock',
            style: TextStyle(fontSize: 17, fontWeight: FontWeight.w600)),
        centerTitle: true,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back_ios_new, size: 18),
          onPressed: () => Navigator.pop(context),
        ),
      ),
      body: ListView(
        children: [
          // ── Status card ──────────────────────────────────────
          Container(
            margin: const EdgeInsets.all(16),
            padding: const EdgeInsets.all(20),
            decoration: BoxDecoration(
              gradient: LinearGradient(
                colors: anythingOn
                    ? [Colors.blueAccent.withValues(alpha: 0.15), Colors.blue.withValues(alpha: 0.05)]
                    : [Colors.grey.withValues(alpha: 0.1), Colors.grey.withValues(alpha: 0.05)],
                begin: Alignment.topLeft, end: Alignment.bottomRight,
              ),
              borderRadius: BorderRadius.circular(16),
              border: Border.all(
                color: anythingOn
                    ? Colors.blueAccent.withValues(alpha: 0.3)
                    : Colors.grey.withValues(alpha: 0.2),
              ),
            ),
            child: Column(
              children: [
                Icon(
                  anythingOn ? Icons.lock_rounded : Icons.lock_open_rounded,
                  color: anythingOn ? Colors.blueAccent : Colors.grey,
                  size: 48,
                ),
                const SizedBox(height: 12),
                Text(
                  anythingOn ? 'Lock Active' : 'No Lock',
                  style: TextStyle(
                    fontSize: 20, fontWeight: FontWeight.bold,
                    color: anythingOn ? Colors.blueAccent : Colors.grey,
                  ),
                ),
                const SizedBox(height: 6),
                Text(
                  _statusText(a),
                  textAlign: TextAlign.center,
                  style: TextStyle(
                    fontSize: 13,
                    color: isDark ? Colors.white54 : Colors.black54,
                  ),
                ),
              ],
            ),
          ),

          const _SectionHeader(title: 'LOCK MODES'),
          // ── App-wide lock ────────────────────────────────────
          SwitchListTile(
            title: const Text('Lock Entire App'),
            subtitle: const Text(
              'Require PIN when opening FocusGram.',
            ),
            value: a.lockAppWide,
            onChanged: (v) async {
              if (v && !a.hasPin) {
                if (!await _ensurePin()) return;
              }
              await a.setLockAppWide(v);
              HapticFeedback.selectionClick();
            },
          ),
          // ── Messages tab lock ────────────────────────────────
          SwitchListTile(
            title: const Text('Lock Messages Tab'),
            subtitle: const Text(
              'Require PIN to open Instagram Direct Messages',
            ),
            value: a.lockMessages,
            onChanged: (v) async {
              if (v && !a.hasPin) {
                if (!await _ensurePin()) return;
              }
              await a.setLockMessages(v);
              HapticFeedback.selectionClick();
            },
          ),

          // ─── PIN & extras ────────────────────────────────────
          if (a.hasPin) ...[
            const _SectionHeader(title: 'PIN & SECURITY'),
            ListTile(
              title: const Text('Change PIN'),
              subtitle: const Text('Set a new 4-digit code'),
              trailing: const Icon(Icons.arrow_forward_ios, size: 14),
              onTap: () async {
                final ok = await Navigator.push<bool>(
                  context,
                  MaterialPageRoute(builder: (_) => const AppLockSetupScreen()),
                );
                if (ok == true && mounted) {
                  ScaffoldMessenger.of(context).showSnackBar(
                    const SnackBar(content: Text('PIN updated')),
                  );
                }
              },
            ),
            SwitchListTile(
              title: const Text('Scrambled Keypad'),
              subtitle: const Text('Shuffle digits on the lock screen'),
              value: a.scrambleKeypad,
              onChanged: (v) async {
                await a.setScrambleKeypad(v);
                HapticFeedback.selectionClick();
              },
            ),
            // Biometrics option removed
          ],

          // ── Hint if no PIN ───────────────────────────────────
          if (!a.hasPin)
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 0),
              child: Container(
                padding: const EdgeInsets.all(14),
                decoration: BoxDecoration(
                  color: Colors.blue.withValues(alpha: 0.07),
                  borderRadius: BorderRadius.circular(10),
                ),
                child: const Row(
                  children: [
                    Icon(Icons.info_outline, size: 16, color: Colors.blueAccent),
                    SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        'Enable any lock mode above to set up your PIN.',
                        style: TextStyle(fontSize: 13),
                      ),
                    ),
                  ],
                ),
              ),
            ),

          const SizedBox(height: 40),
        ],
      ),
    );
  }

  String _statusText(AppLockService a) {
    if (!a.hasPin) return 'Set a PIN to enable any lock mode.';
    final parts = <String>[];
    if (a.lockAppWide) parts.add('App-wide');
    if (a.lockMessages) parts.add('Messages tab');
    if (parts.isEmpty) return 'Both modes are off — enable one above.';
    return '${parts.join(' + ')} lock is active.';
  }
}

class _SectionHeader extends StatelessWidget {
  final String title;
  const _SectionHeader({required this.title});
  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 24, 16, 8),
      child: Text(title,
          style: const TextStyle(
              color: Colors.grey,
              fontSize: 11,
              fontWeight: FontWeight.bold,
              letterSpacing: 1.2)),
    );
  }
}
