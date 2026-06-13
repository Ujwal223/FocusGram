import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import '../services/app_lock_service.dart';

/// The lock screen shown when FocusGram is locked.
///
/// Supports PIN entry with optional scrambled keypad.
/// [forAppWide] controls which PIN to verify: true = app-wide, false = messages.
/// [title] lets the screen show context (e.g. "Messages Locked").
class AppLockScreen extends StatefulWidget {
  final bool forAppWide;
  final String? title;
  final String? subtitle;

  const AppLockScreen({
    super.key,
    this.forAppWide = true,
    this.title,
    this.subtitle,
  });

  @override
  State<AppLockScreen> createState() => _AppLockScreenState();
}

class _AppLockScreenState extends State<AppLockScreen> {
  String _enteredPin = '';
  bool _showError = false;
  String _errorMsg = '';
  bool _isVerifying = false;
  List<int> _scrambledDigits = [];

  @override
  void initState() {
    super.initState();
    _refreshScrambled();
  }

  void _refreshScrambled() {
    setState(() {
      _scrambledDigits = context.read<AppLockService>().getScrambledDigits();
    });
  }

  @override
  Widget build(BuildContext context) {
    final appLock = context.watch<AppLockService>();
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return Scaffold(
      backgroundColor: isDark ? Colors.black : Colors.white,
      body: SafeArea(
        child: Column(
          children: [
            const Spacer(flex: 2),
            // Icon
            Container(
              width: 64,
              height: 64,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: Colors.blue.withValues(alpha: 0.1),
              ),
              child: const Icon(
                Icons.lock_outline,
                color: Colors.blueAccent,
                size: 32,
              ),
            ),
            const SizedBox(height: 20),

            // Title
            Text(
              widget.title ?? 'FocusGram is Locked',
              style: const TextStyle(fontSize: 22, fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 8),
            Text(
              widget.subtitle ?? 'Enter your PIN to unlock',
              style: TextStyle(
                fontSize: 14,
                color: isDark ? Colors.white54 : Colors.black54,
              ),
            ),

            const SizedBox(height: 32),

            // PIN dots
            Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: List.generate(4, (i) {
                final filled = i < _enteredPin.length;
                return Container(
                  width: 16,
                  height: 16,
                  margin: const EdgeInsets.symmetric(horizontal: 8),
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    color: filled
                        ? Colors.blueAccent
                        : (isDark ? Colors.white24 : Colors.black12),
                  ),
                );
              }),
            ),

            // Error text
            if (_showError)
              Padding(
                padding: const EdgeInsets.only(top: 16),
                child: Text(
                  _errorMsg,
                  style: const TextStyle(color: Colors.redAccent, fontSize: 13),
                ),
              ),

            if (_isVerifying)
              const Padding(
                padding: EdgeInsets.only(top: 16),
                child: CircularProgressIndicator(strokeWidth: 2),
              ),

            const Spacer(),

            // Keypad
            _buildKeypad(appLock),
          ],
        ),
      ),
    );
  }

  Widget _buildKeypad(AppLockService appLock) {
    final useScrambled = appLock.scrambleKeypad;

    // Build digit labels
    final digitLabels = useScrambled
        ? _scrambledDigits.map((d) => d.toString()).toList()
        : List.generate(10, (i) => i.toString());

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 32, vertical: 16),
      child: Column(
        children: [
          // Row 1: 1 2 3
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceEvenly,
            children: [
              _KeypadButton(
                label: digitLabels[1],
                onTap: () => _onDigit(digitLabels[1]),
              ),
              _KeypadButton(
                label: digitLabels[2],
                onTap: () => _onDigit(digitLabels[2]),
              ),
              _KeypadButton(
                label: digitLabels[3],
                onTap: () => _onDigit(digitLabels[3]),
              ),
            ],
          ),
          // Row 2: 4 5 6
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceEvenly,
            children: [
              _KeypadButton(
                label: digitLabels[4],
                onTap: () => _onDigit(digitLabels[4]),
              ),
              _KeypadButton(
                label: digitLabels[5],
                onTap: () => _onDigit(digitLabels[5]),
              ),
              _KeypadButton(
                label: digitLabels[6],
                onTap: () => _onDigit(digitLabels[6]),
              ),
            ],
          ),
          // Row 3: 7 8 9
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceEvenly,
            children: [
              _KeypadButton(
                label: digitLabels[7],
                onTap: () => _onDigit(digitLabels[7]),
              ),
              _KeypadButton(
                label: digitLabels[8],
                onTap: () => _onDigit(digitLabels[8]),
              ),
              _KeypadButton(
                label: digitLabels[9],
                onTap: () => _onDigit(digitLabels[9]),
              ),
            ],
          ),
          // Row 4: delete  0  scramble-refresh
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceEvenly,
            children: [
              _KeypadButton(label: '⌫', onTap: _onDelete, isFunction: true),
              _KeypadButton(
                label: digitLabels[0],
                onTap: () => _onDigit(digitLabels[0]),
              ),
              if (useScrambled)
                _KeypadButton(
                  label: '⟳',
                  onTap: _refreshScrambled,
                  isFunction: true,
                )
              else
                const SizedBox(width: 72), // Placeholder
            ],
          ),
        ],
      ),
    );
  }

  void _onDigit(String digit) {
    if (_enteredPin.length >= 4) return;
    setState(() {
      _enteredPin += digit;
      _showError = false;
    });

    if (_enteredPin.length == 4) {
      _verifyPin();
    }
  }

  void _onDelete() {
    if (_enteredPin.isEmpty) return;
    setState(
      () => _enteredPin = _enteredPin.substring(0, _enteredPin.length - 1),
    );
  }

  Future<void> _verifyPin() async {
    setState(() => _isVerifying = true);

    final appLock = context.read<AppLockService>();
    final valid = await appLock.verifyPin(
      _enteredPin,
      forAppWide: widget.forAppWide,
    );

    if (!mounted) return;

    if (valid) {
      HapticFeedback.heavyImpact();
      appLock.onUnlocked();
      Navigator.of(context).pop(true);
    } else {
      setState(() {
        _showError = true;
        _errorMsg = 'Wrong PIN. Try again.';
        _enteredPin = '';
        _isVerifying = false;
      });
      HapticFeedback.heavyImpact();
    }
  }


}

class _KeypadButton extends StatelessWidget {
  final String label;
  final VoidCallback onTap;
  final bool isFunction;

  const _KeypadButton({
    required this.label,
    required this.onTap,
    this.isFunction = false,
  });

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return SizedBox(
      width: 72,
      height: 72,
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          borderRadius: BorderRadius.circular(36),
          onTap: onTap,
          child: Center(
            child: Text(
              label,
              style: TextStyle(
                fontSize: isFunction ? 28 : 24,
                fontWeight: FontWeight.w500,
                color: isFunction
                    ? Colors.blueAccent
                    : (isDark ? Colors.white : Colors.black87),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
