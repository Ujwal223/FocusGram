/*import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import '../services/level_service.dart';

/// A hidden debug menu for development & testing.
///
/// Access: tap the app version in settings 7 times.
/// Allows manually setting XP/level to test feature gating.
class DebugMenuScreen extends StatefulWidget {
  const DebugMenuScreen({super.key});

  @override
  State<DebugMenuScreen> createState() => _DebugMenuScreenState();
}

class _DebugMenuScreenState extends State<DebugMenuScreen> {
  int _customLevel = 1;
  int _customXp = 0;

  @override
  void initState() {
    super.initState();
    final levelService = context.read<LevelService>();
    _customLevel = levelService.level;
    _customXp = levelService.xp;
  }

  @override
  Widget build(BuildContext context) {
    final levelService = context.watch<LevelService>();
    final isDark = Theme.of(context).brightness == Brightness.dark;

    return Scaffold(
      appBar: AppBar(
        title: const Text(
          'Debug Menu',
          style: TextStyle(fontSize: 17, fontWeight: FontWeight.w600),
        ),
        centerTitle: true,
        leading: IconButton(
          icon: const Icon(Icons.arrow_back_ios_new, size: 18),
          onPressed: () => Navigator.pop(context),
        ),
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          // Current state
          Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: Colors.amber.withValues(alpha: 0.1),
              borderRadius: BorderRadius.circular(12),
              border: Border.all(
                color: Colors.amber.withValues(alpha: 0.3),
              ),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    const Icon(Icons.bug_report, color: Colors.amber, size: 20),
                    const SizedBox(width: 8),
                    const Text(
                      'Developer Tools',
                      style: TextStyle(
                        fontWeight: FontWeight.bold,
                        fontSize: 16,
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 12),
                Text(
                  'Current: Level ${levelService.level} · ${levelService.xp} XP',
                  style: const TextStyle(fontSize: 14),
                ),
                const SizedBox(height: 4),
                Text(
                  'Progress: ${(levelService.levelProgress * 100).toStringAsFixed(0)}% to next level',
                  style: TextStyle(
                    fontSize: 12,
                    color: isDark ? Colors.white54 : Colors.black54,
                  ),
                ),
              ],
            ),
          ),

          const SizedBox(height: 24),

          // Manual level setter
          const Text(
            'SET LEVEL',
            style: TextStyle(
              fontSize: 11,
              fontWeight: FontWeight.bold,
              letterSpacing: 1.2,
              color: Colors.grey,
            ),
          ),
          const SizedBox(height: 12),

          // Quick level buttons
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: List.generate(5, (i) {
              final lvl = i + 1;
              final selected = _customLevel == lvl;
              return ElevatedButton(
                onPressed: () => setState(() => _customLevel = lvl),
                style: ElevatedButton.styleFrom(
                  backgroundColor: selected ? Colors.blueAccent : null,
                  foregroundColor: selected ? Colors.white : null,
                  padding: const EdgeInsets.symmetric(
                    horizontal: 20,
                    vertical: 12,
                  ),
                ),
                child: Text('Level $lvl'),
              );
            }),
          ),

          const SizedBox(height: 16),

          // Set XP field
          const Text(
            'SET XP',
            style: TextStyle(
              fontSize: 11,
              fontWeight: FontWeight.bold,
              letterSpacing: 1.2,
              color: Colors.grey,
            ),
          ),
          const SizedBox(height: 8),
          TextField(
            decoration: const InputDecoration(
              labelText: 'XP Amount',
              border: OutlineInputBorder(),
              isDense: true,
            ),
            keyboardType: TextInputType.number,
            controller: TextEditingController(text: '$_customXp'),
            onChanged: (v) {
              _customXp = int.tryParse(v) ?? 0;
            },
          ),

          const SizedBox(height: 20),

          // Apply button
          SizedBox(
            width: double.infinity,
            height: 48,
            child: ElevatedButton.icon(
              onPressed: () => _applyDebugSettings(levelService),
              style: ElevatedButton.styleFrom(
                backgroundColor: Colors.amber,
                foregroundColor: Colors.black,
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(12),
                ),
              ),
              icon: const Icon(Icons.warning_amber_rounded, size: 20),
              label: const Text(
                'Apply Debug Settings',
                style: TextStyle(fontWeight: FontWeight.bold),
              ),
            ),
          ),

          const SizedBox(height: 32),

          // Feature unlock preview
          const Text(
            'FEATURE UNLOCK STATUS',
            style: TextStyle(
              fontSize: 11,
              fontWeight: FontWeight.bold,
              letterSpacing: 1.2,
              color: Colors.grey,
            ),
          ),
          const SizedBox(height: 8),
          ...AppFeature.all.map((feature) {
            final unlocked = _customLevel >= feature.requiredLevel;
            return Container(
              margin: const EdgeInsets.only(bottom: 4),
              padding: const EdgeInsets.symmetric(
                horizontal: 12,
                vertical: 8,
              ),
              decoration: BoxDecoration(
                color: (isDark ? Colors.white : Colors.black)
                    .withValues(alpha: 0.04),
                borderRadius: BorderRadius.circular(8),
              ),
              child: Row(
                children: [
                  Icon(
                    unlocked ? Icons.check_circle : Icons.lock_outline,
                    color: unlocked ? Colors.greenAccent : Colors.grey,
                    size: 16,
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      feature.name,
                      style: TextStyle(
                        fontSize: 13,
                        color: unlocked ? null : Colors.grey,
                      ),
                    ),
                  ),
                  Text(
                    'Lv ${feature.requiredLevel}',
                    style: TextStyle(
                      fontSize: 12,
                      color: Colors.grey,
                    ),
                  ),
                ],
              ),
            );
          }),

          const SizedBox(height: 32),

          const SizedBox(height: 40),

          // Danger zone
          Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: Colors.red.withValues(alpha: 0.05),
              borderRadius: BorderRadius.circular(12),
              border: Border.all(color: Colors.red.withValues(alpha: 0.2)),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Row(
                  children: [
                    Icon(Icons.dangerous_outlined, color: Colors.redAccent, size: 18),
                    SizedBox(width: 8),
                    Text(
                      'Danger Zone',
                      style: TextStyle(
                        color: Colors.redAccent,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                  ],
                ),
                const SizedBox(height: 12),
                SizedBox(
                  width: double.infinity,
                  child: OutlinedButton.icon(
                    onPressed: () => _resetAllData(levelService),
                    style: OutlinedButton.styleFrom(
                      foregroundColor: Colors.redAccent,
                      side: const BorderSide(color: Colors.redAccent),
                    ),
                    icon: const Icon(Icons.delete_forever, size: 18),
                    label: const Text('Reset All Level Data'),
                  ),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Future<void> _applyDebugSettings(LevelService levelService) async {
    HapticFeedback.heavyImpact();
    // Use reflection-like approach: set the private fields via a method
    // Since LevelService doesn't expose a raw setter, we provide one here.
    await _forceSetLevel(levelService, _customLevel, _customXp);
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(
          content: Text('Set to Level $_customLevel with $_customXp XP'),
          backgroundColor: Colors.amber.shade800,
        ),
      );
    }
  }

  Future<void> _forceSetLevel(LevelService levelService, int level, int xp) async {
    // The LevelService stores data in Hive (local only).
    // We bypass the normal XP system by writing directly to cache.
    await levelService.debugSetLevel(level, xp);
    await Future.delayed(const Duration(milliseconds: 100));
    if (mounted) setState(() {});
  }

  Future<void> _resetAllData(LevelService levelService) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Reset All Level Data?'),
        content: const Text(
          'This will reset your level, XP, and all history to defaults. '
          'This cannot be undone.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Cancel'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(ctx, true),
            style: TextButton.styleFrom(foregroundColor: Colors.redAccent),
            child: const Text('Reset'),
          ),
        ],
      ),
    );
    if (confirmed == true && mounted) {
      await levelService.debugReset();
      if (mounted) {
        setState(() {
          _customLevel = 1;
          _customXp = 0;
        });
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Level data reset')),
        );
      }
    }
  }
}
*/
