class FocusSettings {
  final bool ghostMode; // DM ghost — blocks seen/DM signals comprehensively
  final bool noAds; // strip ads and sponsored posts
  final bool noStories; // hide story tray
  final bool noReels; // hide reels tab
  final bool noAutoplay; // stop videos autoplaying
  final bool noDMs; // block direct messages

  const FocusSettings({
    this.ghostMode = false,
    this.noAds = true,
    this.noStories = false,
    this.noReels = false,
    this.noAutoplay = false,
    this.noDMs = false,
  });
}
