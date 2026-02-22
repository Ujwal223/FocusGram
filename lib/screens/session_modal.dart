import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../services/session_manager.dart';

class SessionModal extends StatefulWidget {
  const SessionModal({super.key});

  @override
  State<SessionModal> createState() => _SessionModalState();
}

class _SessionModalState extends State<SessionModal> {
  double _customMinutes = 5.0;

  @override
  Widget build(BuildContext context) {
    final sm = context.watch<SessionManager>();

    return Container(
      padding: const EdgeInsets.all(24),
      decoration: const BoxDecoration(
        color: Color(0xFF121212),
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              const Text(
                'Start Reel Session',
                style: TextStyle(
                  color: Colors.white,
                  fontSize: 20,
                  fontWeight: FontWeight.bold,
                ),
              ),
              IconButton(
                onPressed: () => Navigator.pop(context),
                icon: const Icon(Icons.close, color: Colors.white54),
              ),
            ],
          ),
          const SizedBox(height: 8),
          Text(
            'Remaining Daily: ${sm.dailyRemainingSeconds ~/ 60}m',
            style: const TextStyle(color: Colors.white70),
          ),
          if (sm.isCooldownActive)
            Padding(
              padding: const EdgeInsets.only(top: 8.0),
              child: Text(
                'Cooldown active: ${sm.cooldownRemainingSeconds ~/ 60}m ${sm.cooldownRemainingSeconds % 60}s left',
                style: const TextStyle(color: Colors.orangeAccent),
              ),
            ),
          const SizedBox(height: 24),
          const Text(
            'Presets',
            style: TextStyle(color: Colors.white, fontWeight: FontWeight.w600),
          ),
          const SizedBox(height: 12),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [1, 5, 10, 15].map((m) {
              return Expanded(
                child: Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 4.0),
                  child: ElevatedButton(
                    onPressed: (sm.isCooldownActive || sm.isDailyLimitExhausted)
                        ? null
                        : () => _start(m),
                    style: ElevatedButton.styleFrom(
                      backgroundColor: Colors.white12,
                      foregroundColor: Colors.white,
                      padding: const EdgeInsets.symmetric(vertical: 12),
                    ),
                    child: Text('${m}m'),
                  ),
                ),
              );
            }).toList(),
          ),
          const SizedBox(height: 32),
          const Text(
            'Custom Duration',
            style: TextStyle(color: Colors.white, fontWeight: FontWeight.w600),
          ),
          Slider(
            value: _customMinutes,
            min: 1,
            max: 30,
            divisions: 29,
            label: '${_customMinutes.toInt()}m',
            onChanged: (v) => setState(() => _customMinutes = v),
          ),
          const SizedBox(height: 24),
          SizedBox(
            width: double.infinity,
            height: 50,
            child: ElevatedButton(
              onPressed: (sm.isCooldownActive || sm.isDailyLimitExhausted)
                  ? null
                  : () => _start(_customMinutes.toInt()),
              style: ElevatedButton.styleFrom(
                backgroundColor: Colors.blue,
                foregroundColor: Colors.white,
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(12),
                ),
              ),
              child: const Text(
                'Start Session',
                style: TextStyle(fontSize: 16),
              ),
            ),
          ),
          const SizedBox(height: 16),
        ],
      ),
    );
  }

  void _start(int minutes) {
    final sm = context.read<SessionManager>();
    if (sm.startSession(minutes)) {
      Navigator.pop(context);
    }
  }
}
