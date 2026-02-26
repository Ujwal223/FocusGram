// Document-start script — injected before Instagram's JS loads.
const String kNativeFeelingScript = '''
(function() {
  const style = document.createElement('style');
  style.id = 'fg-native-feel';
  style.textContent = `
    /* Hide all scrollbars */
    * {
      -ms-overflow-style: none !important;
      scrollbar-width: none !important;
    }
    *::-webkit-scrollbar {
      display: none !important;
    }

    /* Remove blue tap highlight */
    * {
      -webkit-tap-highlight-color: transparent !important;
    }

    /* Disable text selection globally except inputs */
    * {
      -webkit-user-select: none !important;
      user-select: none !important;
    }
    input, textarea, [contenteditable="true"] {
      -webkit-user-select: text !important;
      user-select: text !important;
    }

    /* Momentum scrolling */
    * {
      -webkit-overflow-scrolling: touch !important;
    }

    /* Remove focus outlines */
    *:focus, *:focus-visible {
      outline: none !important;
    }

    /* Fade images in */
    img {
      animation: igFadeIn 0.15s ease-in-out;
    }
    @keyframes igFadeIn {
      from { opacity: 0; }
      to { opacity: 1; }
    }
  `;

  if (document.head) {
    document.head.appendChild(style);
  } else {
    document.addEventListener('DOMContentLoaded', () => {
      document.head.appendChild(style);
    });
  }
})();
''';

// Post-load script — call in onLoadStop only.
// IMPORTANT: Do NOT add overscroll-behavior rules here — they lock the feed scroll.
const String kNativeFeelingPostLoadScript = '''
(function() {
  // Smooth anchor scrolling only — do NOT apply to all containers.
  document.documentElement.style.scrollBehavior = 'auto';
})();
''';
