import 'package:flutter/material.dart';

class NativeBottomNav extends StatelessWidget {
  final String currentUrl;
  final bool reelsEnabled;
  final bool exploreEnabled;
  final bool minimalMode;
  final Function(String path) onNavigate;

  const NativeBottomNav({
    super.key,
    required this.currentUrl,
    required this.reelsEnabled,
    required this.exploreEnabled,
    required this.minimalMode,
    required this.onNavigate,
  });

  String get _path {
    final parsed = Uri.tryParse(currentUrl);
    if (parsed != null && parsed.path.isNotEmpty) return parsed.path;
    return currentUrl; // may already be a path from SPA callbacks
  }

  bool get _onHome => _path == '/' || _path.isEmpty;
  bool get _onExplore => _path.startsWith('/explore');
  bool get _onReels => _path.startsWith('/reels') || _path.startsWith('/reel/');
  bool get _onProfile =>
      _path.startsWith('/accounts') ||
      _path.contains('/profile') ||
      _path.split('/').where((p) => p.isNotEmpty).length == 1;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;

    final bgColor =
        theme.colorScheme.surface.withValues(alpha: isDark ? 0.95 : 0.98);
    final iconColorInactive =
        isDark ? Colors.white70 : Colors.black54;
    final iconColorActive =
        theme.colorScheme.primary;

    final tabs = <_NavItem>[
      _NavItem(
        icon: Icons.home_outlined,
        activeIcon: Icons.home,
        label: 'Home',
        path: '/',
        active: _onHome,
        enabled: true,
      ),
      if (!minimalMode)
        _NavItem(
          icon: Icons.search_outlined,
          activeIcon: Icons.search,
          label: 'Search',
          path: '/explore/',
          active: _onExplore,
          enabled: exploreEnabled,
        ),
      _NavItem(
        icon: Icons.add_box_outlined,
        activeIcon: Icons.add_box,
        label: 'New',
        path: '/create/select/',
        active: false,
        enabled: true,
      ),
      if (!minimalMode)
        _NavItem(
          icon: Icons.play_circle_outline,
          activeIcon: Icons.play_circle,
          label: 'Reels',
          path: '/reels/',
          active: _onReels,
          enabled: reelsEnabled,
        ),
      _NavItem(
        icon: Icons.person_outline,
        activeIcon: Icons.person,
        label: 'Profile',
        path: '/accounts/edit/',
        active: _onProfile,
        enabled: true,
      ),
    ];

    return SafeArea(
      top: false,
      child: Container(
        decoration: BoxDecoration(
          color: bgColor,
          border: Border(
            top: BorderSide(
              color: isDark ? Colors.white10 : Colors.black12,
              width: 0.5,
            ),
          ),
        ),
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.spaceAround,
          children: tabs.map((item) {
            final color =
                item.active ? iconColorActive : iconColorInactive;
            final opacity = item.enabled ? 1.0 : 0.35;

            return Expanded(
              child: Opacity(
                opacity: opacity,
                child: InkWell(
                  onTap: item.enabled ? () => onNavigate(item.path) : null,
                  borderRadius: BorderRadius.circular(24),
                  child: Padding(
                    padding: const EdgeInsets.symmetric(
                      horizontal: 4,
                      vertical: 6,
                    ),
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Icon(
                          item.active ? item.activeIcon : item.icon,
                          size: 24,
                          color: color,
                        ),
                        const SizedBox(height: 2),
                        Text(
                          item.label,
                          style: TextStyle(
                            fontSize: 10,
                            color: color,
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            );
          }).toList(),
        ),
      ),
    );
  }
}

class _NavItem {
  final IconData icon;
  final IconData activeIcon;
  final String label;
  final String path;
  final bool active;
  final bool enabled;

  _NavItem({
    required this.icon,
    required this.activeIcon,
    required this.label,
    required this.path,
    required this.active,
    required this.enabled,
  });
}

