import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../services/session_manager.dart';
import '../services/settings_service.dart';
import '../services/level_service.dart';
import 'adsterra_ad_screen.dart';
import '../utils/discipline_challenge.dart';

class GuardrailsPage extends StatefulWidget {
  const GuardrailsPage({super.key});

  @override
  State<GuardrailsPage> createState() => _GuardrailsPageState();
}

class _GuardrailsPageState extends State<GuardrailsPage> {
  Future<void> _handleScheduleAction(
    BuildContext context,
    SessionManager sm,
    Future<void> Function() action,
  ) async {
    if (sm.isScheduledBlockActive) {
      final settings = context.read<SettingsService>();
      final ok = await DisciplineChallenge.show(
        context,
        count: settings.resolvedWordChallengeCount(),
      );
      if (!context.mounted || !ok) return;
    }
    await action();
  }

  Future<void> _pickNewSchedule(BuildContext context, SessionManager sm) async {
    final start = await showTimePicker(
      context: context,
      initialTime: const TimeOfDay(hour: 22, minute: 0),
      helpText: 'Select Start Time',
    );
    if (!context.mounted || start == null) return;

    final end = await showTimePicker(
      context: context,
      initialTime: const TimeOfDay(hour: 7, minute: 0),
      helpText: 'Select End Time',
    );
    if (!context.mounted || end == null) return;

    await sm.addSchedule(
      FocusSchedule(
        startHour: start.hour,
        startMinute: start.minute,
        endHour: end.hour,
        endMinute: end.minute,
      ),
    );
  }

  Future<void> _editExistingSchedule(
    BuildContext context,
    SessionManager sm,
    int index,
    FocusSchedule s,
  ) async {
    final start = await showTimePicker(
      context: context,
      initialTime: TimeOfDay(hour: s.startHour, minute: s.startMinute),
      helpText: 'Edit Start Time',
    );
    if (!context.mounted || start == null) return;

    final end = await showTimePicker(
      context: context,
      initialTime: TimeOfDay(hour: s.endHour, minute: s.endMinute),
      helpText: 'Edit End Time',
    );
    if (!context.mounted || end == null) return;

    await sm.updateScheduleAt(
      index,
      FocusSchedule(
        startHour: start.hour,
        startMinute: start.minute,
        endHour: end.hour,
        endMinute: end.minute,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final sm = context.watch<SessionManager>();
    final settings = context.watch<SettingsService>();
    final isDark = settings.isDarkMode;

    return Scaffold(
      appBar: AppBar(
        title: const Text(
          'Guardrails',
          style: TextStyle(fontSize: 17, fontWeight: FontWeight.bold),
        ),
        leading: IconButton(
          icon: const Icon(Icons.arrow_back_ios_new, size: 18),
          onPressed: () => Navigator.pop(context),
        ),
      ),
      body: ListView(
        children: [
          Padding(
            padding: const EdgeInsets.all(16.0),
            child: Text(
              'Set your limits to stay focused. Changes to these settings require a challenge.',
              style: TextStyle(
                color: isDark ? Colors.white54 : Colors.black54,
                fontSize: 13,
              ),
            ),
          ),
          // If quota used up, show earn page instead of slider
          if (sm.dailyRemainingSeconds <= 0)
            _buildQuotaExhaustedTile(context, sm)
          else
            _buildFrictionSliderTile(
              context: context,
              sm: sm,
              title: 'Daily Reel Limit',
              subtitle: '${sm.dailyLimitSeconds ~/ 60} min / day',
              value: (sm.dailyLimitSeconds ~/ 60).toDouble(),
              min: 5,
              max: 120,
              divisor: 5,
              isMorePermissive: (v) => v > (sm.dailyLimitSeconds ~/ 60),
              warningText:
                  'Increasing your limit makes it easier to scroll. Are you sure?',
              onConfirmed: (v) async {
                // XP penalty for increasing limit
                final increase = (v.toInt() - (sm.dailyLimitSeconds ~/ 60));
                if (increase > 0) {
                  // context.read<LevelService>().grantDebugXp(
                  //   -increase * 5, 'Penalty: increased reel limit',
                  // );
                }
                await sm.setDailyLimitMinutes(v.toInt());
              },
            ),
          _buildFrictionSliderTile(
            context: context,
            sm: sm,
            title: 'Session Cooldown',
            subtitle: '${sm.cooldownSeconds ~/ 60} min between sessions',
            value: (sm.cooldownSeconds ~/ 60).toDouble(),
            min: 5,
            max: 180,
            divisor: 5,
            isMorePermissive: (v) => v < (sm.cooldownSeconds ~/ 60),
            warningText:
                'Reducing cooldown makes it easier to start new sessions. Are you sure?',
            onConfirmed: (v) => sm.setCooldownMinutes(v.toInt()),
          ),
          Divider(color: isDark ? Colors.white10 : Colors.black12, height: 32),
          SwitchListTile(
            title: const Text('Scheduled Blocking'),
            subtitle: Text(
              'Block Instagram during specific hours',
              style: TextStyle(
                color: isDark ? Colors.white54 : Colors.black54,
                fontSize: 13,
              ),
            ),
            value: sm.scheduleEnabled,
            onChanged: (v) => sm.setScheduleEnabled(v),
          ),
          if (sm.scheduleEnabled) ...[
            ...sm.schedules.asMap().entries.map((entry) {
              final idx = entry.key;
              final s = entry.value;
              return ListTile(
                title: Text(
                  'Schedule ${idx + 1}',
                  style: const TextStyle(fontSize: 14),
                ),
                subtitle: Text(
                  '${sm.formatTime12h(s.startHour, s.startMinute)} - ${sm.formatTime12h(s.endHour, s.endMinute)}',
                  style: TextStyle(
                    color: isDark ? Colors.white54 : Colors.black54,
                    fontSize: 13,
                  ),
                ),
                trailing: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    IconButton(
                      icon: const Icon(
                        Icons.edit,
                        color: Colors.blue,
                        size: 20,
                      ),
                      onPressed: () => _handleScheduleAction(
                        context,
                        sm,
                        () => _editExistingSchedule(context, sm, idx, s),
                      ),
                    ),
                    IconButton(
                      icon: const Icon(
                        Icons.delete_outline,
                        color: Colors.redAccent,
                        size: 20,
                      ),
                      onPressed: () => _handleScheduleAction(
                        context,
                        sm,
                        () => sm.removeScheduleAt(idx),
                      ),
                    ),
                  ],
                ),
              );
            }),
            ListTile(
              leading: const Icon(
                Icons.add_circle_outline,
                color: Colors.blueAccent,
              ),
              title: const Text(
                'Add Focus Hours',
                style: TextStyle(
                  color: Colors.blueAccent,
                  fontWeight: FontWeight.w600,
                ),
              ),
              onTap: () => _handleScheduleAction(
                context,
                sm,
                () => _pickNewSchedule(context, sm),
              ),
            ),
          ],
        ],
      ),
    );
  }

  Widget _buildQuotaExhaustedTile(BuildContext context, SessionManager sm) {
    return Container(
      margin: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.orange.withValues(alpha: 0.08),
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: Colors.orange.withValues(alpha: 0.2)),
      ),
      child: Column(
        children: [
          const Icon(
            Icons.hourglass_empty,
            color: Colors.orangeAccent,
            size: 36,
          ),
          const SizedBox(height: 8),
          const Text(
            'Daily Reel Quota Used Up',
            style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
          ),
          const SizedBox(height: 4),
          const Text(
            'Watch an ad to earn 3 more minutes of reel time.',
            textAlign: TextAlign.center,
            style: TextStyle(fontSize: 13, color: Colors.grey),
          ),
          const SizedBox(height: 16),
          SizedBox(
            width: double.infinity,
            child: ElevatedButton.icon(
              onPressed: () => _earnQuota(context, sm),
              icon: const Icon(Icons.play_circle_fill_rounded, size: 20),
              label: const Text('Watch Ad (+3 min reels)'),
              style: ElevatedButton.styleFrom(
                backgroundColor: Colors.orange,
                foregroundColor: Colors.white,
                padding: const EdgeInsets.symmetric(vertical: 14),
                shape: RoundedRectangleBorder(
                  borderRadius: BorderRadius.circular(12),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Future<void> _earnQuota(BuildContext context, SessionManager sm) async {
    final result = await Navigator.push<bool>(
      context,
      MaterialPageRoute(
        builder: (_) => const AdsterraAdScreen(sessionType: 'reels'),
      ),
    );
    if (result == true && context.mounted) {
      sm.increaseDailyLimit(3);
      context.read<LevelService>().addXpForAd();
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('+3 min reel quota earned!')),
      );
    }
  }

  Widget _buildFrictionSliderTile({
    required BuildContext context,
    required SessionManager sm,
    required String title,
    required String subtitle,
    required double value,
    required double min,
    required double max,
    required int divisor,
    required bool Function(double) isMorePermissive,
    required String warningText,
    required Future<void> Function(double) onConfirmed,
  }) {
    return _FrictionSliderTile(
      title: title,
      subtitle: subtitle,
      value: value,
      min: min,
      max: max,
      divisor: divisor,
      isMorePermissive: isMorePermissive,
      warningText: warningText,
      onConfirmed: onConfirmed,
    );
  }
}

class _FrictionSliderTile extends StatefulWidget {
  final String title;
  final String subtitle;
  final double value;
  final double min;
  final double max;
  final int divisor;
  final bool Function(double) isMorePermissive;
  final String warningText;
  final Future<void> Function(double) onConfirmed;

  const _FrictionSliderTile({
    required this.title,
    required this.subtitle,
    required this.value,
    required this.min,
    required this.max,
    required this.divisor,
    required this.isMorePermissive,
    required this.warningText,
    required this.onConfirmed,
  });

  @override
  State<_FrictionSliderTile> createState() => _FrictionSliderTileState();
}

class _FrictionSliderTileState extends State<_FrictionSliderTile> {
  late double _draftValue;
  bool _pendingConfirm = false;

  @override
  void initState() {
    super.initState();
    _draftValue = widget.value;
  }

  @override
  void didUpdateWidget(_FrictionSliderTile old) {
    super.didUpdateWidget(old);
    if (!_pendingConfirm) _draftValue = widget.value;
  }

  @override
  Widget build(BuildContext context) {
    final settings = context.watch<SettingsService>();
    final isDark = settings.isDarkMode;
    final divisions = ((widget.max - widget.min) / widget.divisor).round();

    return Column(
      children: [
        ListTile(
          title: Text(widget.title),
          subtitle: Text(
            '${_draftValue.toInt()} min',
            style: TextStyle(color: isDark ? Colors.white70 : Colors.black54),
          ),
          trailing: _pendingConfirm
              ? Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    TextButton(
                      onPressed: () {
                        setState(() {
                          _draftValue = widget.value;
                          _pendingConfirm = false;
                        });
                      },
                      child: const Text('Cancel'),
                    ),
                    ElevatedButton(
                      onPressed: () async {
                        final sm = context.read<SessionManager>();
                        final settings = context.read<SettingsService>();
                        int wordCount = settings.resolvedWordChallengeCount();
                        // If we are at 0 quota, increase difficulty to 35 words
                        if (widget.title.contains('Daily Reel Limit') &&
                            sm.dailyRemainingSeconds <= 0) {
                          wordCount = 35;
                        }
                        final success = await DisciplineChallenge.show(
                          context,
                          count: wordCount,
                        );
                        if (!context.mounted || !success) return;
                        await widget.onConfirmed(_draftValue);
                        setState(() => _pendingConfirm = false);
                      },
                      child: const Text('Apply'),
                    ),
                  ],
                )
              : null,
        ),
        if (_pendingConfirm)
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16.0),
            child: Text(
              widget.warningText,
              style: const TextStyle(color: Colors.orangeAccent, fontSize: 12),
            ),
          ),
        Slider(
          value: _draftValue,
          min: widget.min,
          max: widget.max,
          divisions: divisions,
          onChanged: (v) {
            setState(() {
              _draftValue = v;
              _pendingConfirm = widget.isMorePermissive(v);
            });
          },
          onChangeEnd: (v) {
            if (!widget.isMorePermissive(v)) {
              widget.onConfirmed(v);
              setState(() => _pendingConfirm = false);
            }
          },
        ),
      ],
    );
  }
}
