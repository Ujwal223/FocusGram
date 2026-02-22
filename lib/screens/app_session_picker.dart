import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../services/session_manager.dart';

/// Shown on every cold app open. Asks the user how long they plan to use
/// Instagram today. Uses an iOS-style scroll picker (ListWheelScrollView).
class AppSessionPickerScreen extends StatefulWidget {
  final VoidCallback onSessionStarted;
  const AppSessionPickerScreen({super.key, required this.onSessionStarted});

  @override
  State<AppSessionPickerScreen> createState() => _AppSessionPickerScreenState();
}

class _AppSessionPickerScreenState extends State<AppSessionPickerScreen> {
  static final List<int> _minuteOptions = [
    5,
    10,
    15,
    20,
    25,
    30,
    35,
    40,
    45,
    50,
    55,
    60,
  ];
  int _selectedIndex = 2; // default: 15 min

  @override
  Widget build(BuildContext context) {
    final selectedMinutes = _minuteOptions[_selectedIndex];

    return Scaffold(
      backgroundColor: Colors.black,
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 32),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.center,
            children: [
              const Spacer(flex: 2),

              // Icon
              Container(
                width: 72,
                height: 72,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  gradient: LinearGradient(
                    colors: [Colors.blue.shade700, Colors.blue.shade400],
                    begin: Alignment.topLeft,
                    end: Alignment.bottomRight,
                  ),
                  boxShadow: [
                    BoxShadow(
                      color: Colors.blue.withValues(alpha: 0.4),
                      blurRadius: 24,
                      spreadRadius: 4,
                    ),
                  ],
                ),
                child: const Icon(
                  Icons.timer_outlined,
                  color: Colors.white,
                  size: 36,
                ),
              ),

              const SizedBox(height: 28),

              const Text(
                'Set Your Intention',
                style: TextStyle(
                  color: Colors.white,
                  fontSize: 26,
                  fontWeight: FontWeight.bold,
                  letterSpacing: -0.5,
                ),
              ),
              const SizedBox(height: 10),
              const Text(
                'How long do you plan to use\nInstagram right now?',
                textAlign: TextAlign.center,
                style: TextStyle(
                  color: Colors.white54,
                  fontSize: 15,
                  height: 1.5,
                ),
              ),

              const Spacer(flex: 1),

              // iOS-style scroll picker
              SizedBox(
                height: 220,
                child: Stack(
                  alignment: Alignment.center,
                  children: [
                    // Selection highlight
                    Container(
                      height: 50,
                      margin: const EdgeInsets.symmetric(horizontal: 0),
                      decoration: BoxDecoration(
                        color: Colors.blue.withValues(alpha: 0.12),
                        borderRadius: BorderRadius.circular(12),
                        border: Border.all(
                          color: Colors.blue.withValues(alpha: 0.3),
                          width: 1,
                        ),
                      ),
                    ),
                    ListWheelScrollView.useDelegate(
                      itemExtent: 50,
                      physics: const FixedExtentScrollPhysics(),
                      perspective: 0.003,
                      squeeze: 1.1,
                      diameterRatio: 2.5,
                      onSelectedItemChanged: (i) {
                        setState(() => _selectedIndex = i);
                      },
                      controller: FixedExtentScrollController(
                        initialItem: _selectedIndex,
                      ),
                      childDelegate: ListWheelChildListDelegate(
                        children: _minuteOptions.asMap().entries.map((entry) {
                          final isSelected = entry.key == _selectedIndex;
                          return Center(
                            child: RichText(
                              text: TextSpan(
                                children: [
                                  TextSpan(
                                    text: '${entry.value}',
                                    style: TextStyle(
                                      fontSize: isSelected ? 28 : 22,
                                      fontWeight: isSelected
                                          ? FontWeight.bold
                                          : FontWeight.w300,
                                      color: isSelected
                                          ? Colors.white
                                          : Colors.white38,
                                    ),
                                  ),
                                  TextSpan(
                                    text: ' min',
                                    style: TextStyle(
                                      fontSize: isSelected ? 16 : 14,
                                      color: isSelected
                                          ? Colors.white70
                                          : Colors.white24,
                                    ),
                                  ),
                                ],
                              ),
                            ),
                          );
                        }).toList(),
                      ),
                    ),
                  ],
                ),
              ),

              const Spacer(flex: 1),

              // Confirm button
              SizedBox(
                width: double.infinity,
                height: 54,
                child: ElevatedButton(
                  onPressed: () => _confirm(context, selectedMinutes),
                  style: ElevatedButton.styleFrom(
                    backgroundColor: Colors.blue,
                    foregroundColor: Colors.white,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(14),
                    ),
                    elevation: 0,
                  ),
                  child: Text(
                    'Start $selectedMinutes-Minute Session',
                    style: const TextStyle(
                      fontSize: 16,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ),
              ),

              const SizedBox(height: 16),
              const Text(
                'You\'ll be prompted to close the app when your time is up.',
                textAlign: TextAlign.center,
                style: TextStyle(color: Colors.white24, fontSize: 12),
              ),
              const Spacer(flex: 1),
            ],
          ),
        ),
      ),
    );
  }

  void _confirm(BuildContext context, int minutes) {
    context.read<SessionManager>().startAppSession(minutes);
    widget.onSessionStarted();
  }
}
