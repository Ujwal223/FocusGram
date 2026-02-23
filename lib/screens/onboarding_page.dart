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

  final List<OnboardingData> _pages = [
    OnboardingData(
      title: 'Welcome to FocusGram',
      description:
          'The distraction-free way to use Instagram. We help you stay focused by blocking Reels and Explore content.',
      icon: Icons.auto_awesome,
      color: Colors.blue,
    ),
    OnboardingData(
      title: 'Ghost Mode',
      description:
          'Browse with total privacy. We block typing indicators and read receipts automatically.',
      icon: Icons.visibility_off,
      color: Colors.purple,
    ),
    OnboardingData(
      title: 'Session Management',
      description:
          'Plan your usage. Set daily limits and use timed sessions to stay in control of your time.',
      icon: Icons.timer,
      color: Colors.orange,
    ),
    OnboardingData(
      title: 'Open Links in FocusGram',
      description:
          'To open Instagram links directly here: Tap "Configure", then "Open by default" -> "Add link" and select all.',
      icon: Icons.link,
      color: Colors.cyan,
      isAppSettingsPage: true,
    ),
    OnboardingData(
      title: 'Upload Content',
      description:
          'We need access to your gallery if you want to upload stories or posts directly from FocusGram.',
      icon: Icons.photo_library,
      color: Colors.orange,
      isPermissionPage: true,
      permission: Permission.photos,
    ),
    OnboardingData(
      title: 'Stay Notified',
      description:
          'We need notification permissions to alert you when your session is over or a new message arrives.',
      icon: Icons.notifications_active,
      color: Colors.green,
      isPermissionPage: true,
      permission: Permission.notification,
    ),
  ];

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      body: Stack(
        children: [
          PageView.builder(
            controller: _pageController,
            onPageChanged: (index) => setState(() => _currentPage = index),
            itemCount: _pages.length,
            itemBuilder: (context, index) =>
                _OnboardingSlide(data: _pages[index]),
          ),
          Positioned(
            bottom: 50,
            left: 0,
            right: 0,
            child: Column(
              children: [
                Row(
                  mainAxisAlignment: MainAxisAlignment.center,
                  children: List.generate(
                    _pages.length,
                    (index) => Container(
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
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 32),
                  child: SizedBox(
                    width: double.infinity,
                    height: 56,
                    child: Builder(
                      builder: (context) {
                        final data = _pages[_currentPage];
                        return ElevatedButton(
                          onPressed: () async {
                            if (data.isAppSettingsPage) {
                              await AppSettings.openAppSettings(
                                type: AppSettingsType.settings,
                              );
                            } else if (data.isPermissionPage) {
                              if (data.permission != null) {
                                await data.permission!.request();
                              }
                              if (data.title == 'Stay Notified') {
                                await NotificationService().init();
                              }
                            }

                            if (_currentPage == _pages.length - 1) {
                              _finish();
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
                            _currentPage == _pages.length - 1
                                ? 'Get Started'
                                : (data.isAppSettingsPage
                                      ? 'Configure'
                                      : 'Next'),
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
              ],
            ),
          ),
        ],
      ),
    );
  }

  void _finish() {
    context.read<SettingsService>().setFirstRunCompleted();
    widget.onFinish();
  }
}

class OnboardingData {
  final String title;
  final String description;
  final IconData icon;
  final Color color;
  final bool isPermissionPage;
  final bool isAppSettingsPage;
  final Permission? permission;

  OnboardingData({
    required this.title,
    required this.description,
    required this.icon,
    required this.color,
    this.isPermissionPage = false,
    this.isAppSettingsPage = false,
    this.permission,
  });
}

class _OnboardingSlide extends StatelessWidget {
  final OnboardingData data;

  const _OnboardingSlide({required this.data});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(40),
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(data.icon, size: 120, color: data.color),
          const SizedBox(height: 48),
          Text(
            data.title,
            textAlign: TextAlign.center,
            style: const TextStyle(
              color: Colors.white,
              fontSize: 32,
              fontWeight: FontWeight.bold,
            ),
          ),
          const SizedBox(height: 16),
          Text(
            data.description,
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
