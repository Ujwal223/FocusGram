import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../services/screen_time_service.dart';

class ScreenTimeScreen extends StatelessWidget {
  const ScreenTimeScreen({super.key});

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text(
          'Screen Time',
          style: TextStyle(fontSize: 17, fontWeight: FontWeight.w600),
        ),
        leading: IconButton(
          icon: const Icon(Icons.arrow_back_ios_new, size: 18),
          onPressed: () => Navigator.pop(context),
        ),
      ),
      body: Consumer<ScreenTimeService>(
        builder: (context, service, _) {
          final data = service.secondsByDate;
          final todayKey = _todayKey();
          final todaySeconds = data[todayKey] ?? 0;

          final last7 = _lastNDays(7);
          final barSpots = <BarChartGroupData>[];
          int totalSeconds = 0;
          for (var i = 0; i < last7.length; i++) {
            final key = last7[i];
            final sec = data[key] ?? 0;
            totalSeconds += sec;
            barSpots.add(
              BarChartGroupData(
                x: i,
                barRods: [
                  BarChartRodData(
                    toY: sec / 60.0,
                    width: 10,
                    borderRadius: BorderRadius.circular(4),
                    color: Colors.blueAccent,
                  ),
                ],
              ),
            );
          }

          final daysWithData = data.values.isEmpty ? 0 : data.length;
          final weeklyAvgMinutes = last7.isEmpty
              ? 0.0
              : totalSeconds / 60.0 / last7.length;
          final allTimeMinutes = totalSeconds / 60.0;

          return ListView(
            padding: const EdgeInsets.all(16),
            children: [
              _buildStatCard(
                title: 'Today',
                value: _formatDuration(todaySeconds),
              ),
              const SizedBox(height: 16),
              _buildChartCard(barSpots, last7),
              const SizedBox(height: 16),
              _buildInlineStats(
                weeklyAvgMinutes: weeklyAvgMinutes,
                allTimeMinutes: allTimeMinutes,
                daysWithData: daysWithData,
              ),
              const SizedBox(height: 24),
              const Text(
                'All data stored locally on your device only',
                textAlign: TextAlign.center,
                style: TextStyle(fontSize: 12, color: Colors.grey),
              ),
              const SizedBox(height: 24),
              Center(
                child: OutlinedButton.icon(
                  onPressed: () => _confirmReset(context, service),
                  icon: const Icon(Icons.delete_outline, size: 18),
                  label: const Text('Reset all data'),
                  style: OutlinedButton.styleFrom(
                    foregroundColor: Colors.redAccent,
                    side: const BorderSide(color: Colors.redAccent),
                  ),
                ),
              ),
            ],
          );
        },
      ),
    );
  }

  static String _todayKey() {
    final now = DateTime.now();
    return '${now.year.toString().padLeft(4, '0')}-'
        '${now.month.toString().padLeft(2, '0')}-'
        '${now.day.toString().padLeft(2, '0')}';
  }

  static List<String> _lastNDays(int n) {
    final now = DateTime.now();
    return List.generate(n, (i) {
      final d = now.subtract(Duration(days: n - 1 - i));
      return '${d.year.toString().padLeft(4, '0')}-'
          '${d.month.toString().padLeft(2, '0')}-'
          '${d.day.toString().padLeft(2, '0')}';
    });
  }

  Widget _buildStatCard({required String title, required String value}) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.blue.withValues(alpha: 0.08),
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: Colors.blue.withValues(alpha: 0.2)),
      ),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(title, style: const TextStyle(fontSize: 14, color: Colors.grey)),
          Text(
            value,
            style: const TextStyle(fontSize: 22, fontWeight: FontWeight.bold),
          ),
        ],
      ),
    );
  }

  Widget _buildChartCard(List<BarChartGroupData> bars, List<String> last7Keys) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.black.withValues(alpha: 0.15),
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: Colors.white12),
      ),
      height: 220,
      child: BarChart(
        BarChartData(
          barGroups: bars,
          gridData: FlGridData(show: false),
          borderData: FlBorderData(show: false),
          titlesData: FlTitlesData(
            leftTitles: const AxisTitles(
              sideTitles: SideTitles(showTitles: false),
            ),
            topTitles: const AxisTitles(
              sideTitles: SideTitles(showTitles: false),
            ),
            rightTitles: const AxisTitles(
              sideTitles: SideTitles(showTitles: false),
            ),
            bottomTitles: AxisTitles(
              sideTitles: SideTitles(
                showTitles: true,
                getTitlesWidget: (value, meta) {
                  final index = value.toInt();
                  if (index < 0 || index >= last7Keys.length) {
                    return const SizedBox.shrink();
                  }
                  final label = last7Keys[index].substring(
                    last7Keys[index].length - 2,
                  );
                  return Padding(
                    padding: const EdgeInsets.only(top: 4),
                    child: Text(
                      label,
                      style: const TextStyle(fontSize: 10, color: Colors.grey),
                    ),
                  );
                },
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildInlineStats({
    required double weeklyAvgMinutes,
    required double allTimeMinutes,
    required int daysWithData,
  }) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: Colors.white.withValues(alpha: 0.02),
        borderRadius: BorderRadius.circular(14),
        border: Border.all(color: Colors.white10),
      ),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceAround,
        children: [
          _inlineStat(
            label: '7-day avg',
            value: '${weeklyAvgMinutes.toStringAsFixed(1)} min',
          ),
          _inlineDivider(),
          _inlineStat(
            label: 'All-time total',
            value: '${allTimeMinutes.toStringAsFixed(0)} min',
          ),
          _inlineDivider(),
          _inlineStat(label: 'Tracked days', value: '$daysWithData'),
        ],
      ),
    );
  }

  Widget _inlineStat({required String label, required String value}) {
    return Column(
      children: [
        Text(
          value,
          style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
        ),
        const SizedBox(height: 4),
        Text(label, style: const TextStyle(fontSize: 11, color: Colors.grey)),
      ],
    );
  }

  Widget _inlineDivider() {
    return Container(
      width: 1,
      height: 40,
      color: Colors.white.withValues(alpha: 0.08),
    );
  }

  static String _formatDuration(int seconds) {
    if (seconds < 60) {
      return '0:${seconds.toString().padLeft(2, '0')}';
    }
    final h = seconds ~/ 3600;
    final m = (seconds % 3600) ~/ 60;
    if (h > 0) {
      return '${h}h ${m.toString().padLeft(2, '0')}m';
    }
    final s = seconds % 60;
    return '${m.toString().padLeft(2, '0')}:${s.toString().padLeft(2, '0')}';
  }

  Future<void> _confirmReset(
    BuildContext context,
    ScreenTimeService service,
  ) async {
    final first = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Reset screen time?'),
        content: const Text(
          'This will clear all locally stored screen time data for the last 30 days.',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Cancel'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text(
              'Continue',
              style: TextStyle(color: Colors.redAccent),
            ),
          ),
        ],
      ),
    );
    if (first != true) return;
    if (!context.mounted) return;

    final second = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Confirm reset'),
        content: const Text(
          'Are you sure you want to permanently delete all screen time data?',
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('No'),
          ),
          TextButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text(
              'Yes, delete',
              style: TextStyle(color: Colors.redAccent),
            ),
          ),
        ],
      ),
    );
    if (!context.mounted) return;
    if (second == true) {
      await service.resetAll();
    }
  }
}
