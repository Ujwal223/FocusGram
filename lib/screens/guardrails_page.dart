import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import '../services/session_manager.dart';
import '../utils/discipline_challenge.dart';

class GuardrailsPage extends StatelessWidget {
  const GuardrailsPage({super.key});

  @override
  Widget build(BuildContext context) {
    final sm = context.watch<SessionManager>();

    return Scaffold(
      backgroundColor: Colors.black,
      appBar: AppBar(
        backgroundColor: Colors.black,
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
          const Padding(
            padding: EdgeInsets.all(16.0),
            child: Text(
              'Set your limits to stay focused. Changes to these settings require a challenge.',
              style: TextStyle(color: Colors.white54, fontSize: 13),
            ),
          ),
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
            onConfirmed: (v) => sm.setDailyLimitMinutes(v.toInt()),
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
          const Divider(color: Colors.white10, height: 32),
          const Padding(
            padding: EdgeInsets.symmetric(horizontal: 16.0),
            child: Text(
              'Scheduled Blocking',
              style: TextStyle(
                color: Colors.blue,
                fontWeight: FontWeight.bold,
                fontSize: 13,
              ),
            ),
          ),
          SwitchListTile(
            title: const Text(
              'Enable Blocking Schedule',
              style: TextStyle(color: Colors.white),
            ),
            subtitle: const Text(
              'Block Instagram during specific hours',
              style: TextStyle(color: Colors.white54, fontSize: 13),
            ),
            value: sm.scheduleEnabled,
            onChanged: (v) => sm.setScheduleEnabled(v),
          ),
          if (sm.scheduleEnabled) ...[
            ListTile(
              title: const Text(
                'Start Time',
                style: TextStyle(color: Colors.white),
              ),
              trailing: Text(
                '${sm.schedStartHour.toString().padLeft(2, '0')}:${sm.schedStartMin.toString().padLeft(2, '0')}',
                style: const TextStyle(color: Colors.blue),
              ),
              onTap: () async {
                final time = await showTimePicker(
                  context: context,
                  initialTime: TimeOfDay(
                    hour: sm.schedStartHour,
                    minute: sm.schedStartMin,
                  ),
                );
                if (time != null) {
                  sm.setScheduleTime(
                    startH: time.hour,
                    startM: time.minute,
                    endH: sm.schedEndHour,
                    endM: sm.schedEndMin,
                  );
                }
              },
            ),
            ListTile(
              title: const Text(
                'End Time',
                style: TextStyle(color: Colors.white),
              ),
              trailing: Text(
                '${sm.schedEndHour.toString().padLeft(2, '0')}:${sm.schedEndMin.toString().padLeft(2, '0')}',
                style: const TextStyle(color: Colors.blue),
              ),
              onTap: () async {
                final time = await showTimePicker(
                  context: context,
                  initialTime: TimeOfDay(
                    hour: sm.schedEndHour,
                    minute: sm.schedEndMin,
                  ),
                );
                if (time != null) {
                  sm.setScheduleTime(
                    startH: sm.schedStartHour,
                    startM: sm.schedStartMin,
                    endH: time.hour,
                    endM: time.minute,
                  );
                }
              },
            ),
          ],
        ],
      ),
    );
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
  Widget build(BuildContext context) {
    final divisions = ((widget.max - widget.min) / widget.divisor).round();

    return Column(
      children: [
        ListTile(
          title: Text(
            widget.title,
            style: const TextStyle(color: Colors.white),
          ),
          subtitle: Text(
            '${_draftValue.toInt()} min',
            style: const TextStyle(color: Colors.white70),
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
                      child: const Text(
                        'Cancel',
                        style: TextStyle(color: Colors.white38),
                      ),
                    ),
                    ElevatedButton(
                      onPressed: () async {
                        final success = await DisciplineChallenge.show(context);
                        if (!success) return;
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
