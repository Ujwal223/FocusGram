import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:app_settings/app_settings.dart';
import '../services/settings_service.dart';
import '../services/notification_service.dart';

class OnboardingPage extends StatefulWidget {
  final VoidCallback onFinish;

  const OnboardingPage({super.key, required this.onFinish});

  @override
  State<OnboardingPage> createState() => _OnboardingPageState();
}

class _OnboardingPageState extends State<OnboardingPage> {
  final PageController _pageController = PageController();
  int _currentPage = 0;

  // Pages: Welcome, Session Management, Link Handling, Blur Settings, Notifications
  static const int _kTotalPages = 5;

  static const int _kBlurPage = 3;
  static const int _kLinkPage = 2;
  static const int _kNotifPage = 4;

  @override
  Widget build(BuildContext context) {
    final settings = context.watch<SettingsService>();

    final List<Widget> slides = [
      // ── Page 0: Welcome ─────────────────────────────────────────────────
      _StaticSlide(
        icon: Icons.auto_awesome,
        color: Colors.blue,
        title: 'Welcome to FocusGram',
        description:
            'The distraction-free way to use Instagram. We help you stay focused by blocking Reels and Explore content.',
      ),

      // ── Page 1: Session Management ───────────────────────────────────────
      _StaticSlide(
        icon: Icons.timer,
        color: Colors.orange,
        title: 'Session Management',
        description:
            'Plan your usage. Set daily limits and use timed sessions to stay in control of your time.',
      ),

      // ── Page 2: Open links ───────────────────────────────────────────────
      _StaticSlide(
        icon: Icons.link,
        color: Colors.cyan,
        title: 'Open Links in FocusGram',
        description:
            'To open Instagram links directly here: Tap "Configure", then "Open by default" → "Add link" and select all.',
        isAppSettingsPage: true,
      ),

      // ── Page 3: Blur Settings ────────────────────────────────────────────
      _BlurSettingsSlide(settings: settings),

      // ── Page 4: Notifications ────────────────────────────────────────────
      _StaticSlide(
        icon: Icons.notifications_active,
        color: Colors.green,
        title: 'Stay Notified',
        description:
            'We need notification permissions to alert you when your session is over or a new message arrives.',
        isPermissionPage: true,
        permission: Permission.notification,
      ),
    ];

    return Scaffold(
      backgroundColor: Colors.black,
      body: Stack(
        children: [
          PageView.builder(
            controller: _pageController,
            onPageChanged: (index) => setState(() => _currentPage = index),
            itemCount: _kTotalPages,
            itemBuilder: (context, index) => slides[index],
          ),
          Positioned(
            bottom: 50,
            left: 0,
            right: 0,
            child: Column(
              children: [
                // Dot indicators
                Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: List.generate(
                    _kTotalPages,
                    (index) => AnimatedContainer(
                      duration: const Duration(milliseconds: 250),
                      margin: const EdgeInsets.symmetric(horizontal: 4),
                      width: _currentPage == index ? 12 : 8,
                      height: 8,
                      decoration: BoxDecoration(
                        color: _currentPage == index
                            ? Colors.blue
                            : Colors.white24,
                        borderRadius: BorderRadius.circular(4),
                      ),
                    ),
                  ),
                ),
                const SizedBox(height: 32),
                // CTA button
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 32),
                  child: SizedBox(
                    width: double.infinity,
                    height: 56,
                    child: Builder(
                      builder: (context) {
                        final isLast = _currentPage == _kTotalPages - 1;
                        final isLink = _currentPage == _kLinkPage;
                        final isNotif = _currentPage == _kNotifPage;
                        final isBlur = _currentPage == _kBlurPage;

                        String label;
                        if (isLast) {
                          label = 'Get Started';
                        } else if (isLink) {
                          label = 'Configure';
                        } else if (isNotif) {
                          label = 'Allow Notifications';
                        } else if (isBlur) {
                          label = 'Save & Continue';
                        } else {
                          label = 'Next';
                        }

                        return ElevatedButton(
                          onPressed: () async {
                            if (isLink) {
                              await AppSettings.openAppSettings(
                                type: AppSettingsType.settings,
                              );
                            } else if (isNotif) {
                              await Permission.notification.request();
                              await NotificationService().init();
                            }

                            if (!context.mounted) return;
                            if (isLast) {
                              _finish(context);
                            } else {
                              _pageController.nextPage(
                                duration: const Duration(milliseconds: 300),
                                curve: Curves.easeInOut,
                              );
                            }
                          },
                          style: ElevatedButton.styleFrom(
                            backgroundColor: Colors.blue,
                            foregroundColor: Colors.white,
                            shape: RoundedRectangleBorder(
                              borderRadius: BorderRadius.circular(16),
                            ),
                          ),
                          child: Text(
                            label,
                            style: const TextStyle(
                              fontSize: 18,
                              fontWeight: FontWeight.bold,
                            ),
                          ),
                        );
                      },
                    ),
                  ),
                ),
                // Skip button (available on all pages except last)
                if (_currentPage < _kTotalPages - 1)
                  TextButton(
                    onPressed: () => _finish(context),
                    child: const Text(
                      'Skip',
                      style: TextStyle(color: Colors.white38, fontSize: 14),
                    ),
                  ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  void _finish(BuildContext context) {
    context.read<SettingsService>().setFirstRunCompleted();
    widget.onFinish();
  }
}

// ── Static info slide ──────────────────────────────────────────────────────────

class _StaticSlide extends StatelessWidget {
  final IconData icon;
  final Color color;
  final String title;
  final String description;
  final bool isPermissionPage;
  final bool isAppSettingsPage;
  final Permission? permission;

  const _StaticSlide({
    required this.icon,
    required this.color,
    required this.title,
    required this.description,
    this.isPermissionPage = false,
    this.isAppSettingsPage = false,
    this.permission,
  });

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(40, 40, 40, 160),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(icon, size: 120, color: color),
          const SizedBox(height: 48),
          Text(
            title,
            textAlign: TextAlign.center,
            style: const TextStyle(
              color: Colors.white,
              fontSize: 32,
              fontWeight: FontWeight.bold,
            ),
          ),
          const SizedBox(height: 16),
          Text(
            description,
            textAlign: TextAlign.center,
            style: const TextStyle(
              color: Colors.white70,
              fontSize: 18,
              height: 1.5,
            ),
          ),
        ],
      ),
    );
  }
}

// ── Blur settings slide ────────────────────────────────────────────────────────

class _BlurSettingsSlide extends StatelessWidget {
  final SettingsService settings;

  const _BlurSettingsSlide({required this.settings});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(32, 40, 32, 160),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Header
          Center(
            child: Icon(
              Icons.blur_on_rounded,
              size: 90,
              color: Colors.purpleAccent,
            ),
          ),
          const SizedBox(height: 36),
          const Center(
            child: Text(
              'Distraction Shield',
              textAlign: TextAlign.center,
              style: TextStyle(
                color: Colors.white,
                fontSize: 30,
                fontWeight: FontWeight.bold,
              ),
            ),
          ),
          const SizedBox(height: 12),
          const Center(
            child: Text(
              'Blur feeds you don\'t want to be tempted by. You can change these anytime in Settings.',
              textAlign: TextAlign.center,
              style: TextStyle(
                color: Colors.white60,
                fontSize: 16,
                height: 1.5,
              ),
            ),
          ),
          const SizedBox(height: 40),

          // Blur Home Feed toggle
          _BlurToggleTile(
            icon: Icons.home_rounded,
            label: 'Blur Home Feed',
            subtitle: 'Posts in your feed will be blurred until tapped',
            value: settings.blurReels,
            onChanged: (v) => settings.setBlurReels(v),
          ),
          const SizedBox(height: 16),

          // Blur Explore toggle
          _BlurToggleTile(
            icon: Icons.explore_rounded,
            label: 'Blur Explore Feed',
            subtitle: 'Explore thumbnails stay blurred until you tap',
            value: settings.blurExplore,
            onChanged: (v) => settings.setBlurExplore(v),
          ),
        ],
      ),
    );
  }
}

class _BlurToggleTile extends StatelessWidget {
  final IconData icon;
  final String label;
  final String subtitle;
  final bool value;
  final ValueChanged<bool> onChanged;

  const _BlurToggleTile({
    required this.icon,
    required this.label,
    required this.subtitle,
    required this.value,
    required this.onChanged,
  });

  @override
  Widget build(BuildContext context) {
    return AnimatedContainer(
      duration: const Duration(milliseconds: 200),
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
      decoration: BoxDecoration(
        color: value
            ? Colors.purpleAccent.withValues(alpha: 0.12)
            : Colors.white.withValues(alpha: 0.06),
        borderRadius: BorderRadius.circular(16),
        border: Border.all(
          color: value
              ? Colors.purpleAccent.withValues(alpha: 0.5)
              : Colors.white.withValues(alpha: 0.1),
          width: 1,
        ),
      ),
      child: Row(
        children: [
          Icon(
            icon,
            color: value ? Colors.purpleAccent : Colors.white38,
            size: 28,
          ),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  label,
                  style: TextStyle(
                    color: value ? Colors.white : Colors.white70,
                    fontSize: 16,
                    fontWeight: FontWeight.w600,
                  ),
                ),
                const SizedBox(height: 2),
                Text(
                  subtitle,
                  style: const TextStyle(color: Colors.white38, fontSize: 12),
                ),
              ],
            ),
          ),
          Switch(
            value: value,
            onChanged: onChanged,
            activeThumbColor: Colors.purpleAccent,
          ),
        ],
      ),
    );
  }
}
