#!/bin/sh
set -e

cat << "EOF"
=======================================================
          FocusGram Release Build Utility
=======================================================
EOF

CHOICE="$1"

if [ -z "$CHOICE" ]; then
    echo "Select target package variant to build:"
    echo "  1) Standard FocusGram (com.ujwal.focusgram)"
    echo "  2) Uncloned Instagram (com.instagram.android - replaces official app)"
    echo "  3) Both Variants (Standard and Uncloned)"
    printf "Enter choice [1-3] (default 1): "
    read -r USER_INPUT
    CHOICE="${USER_INPUT:-1}"
fi

case "$CHOICE" in
    1|--standard|-s)
        echo ""
        echo "--> Building Standard FocusGram (com.ujwal.focusgram)..."
        ./gradlew :app:assembleRelease -PuseInstaPackage=false
        ;;
    2|--insta|-i)
        echo ""
        echo "--> Building Uncloned Instagram Package Edition (com.instagram.android)..."
        ./gradlew :app:assembleRelease -PuseInstaPackage=true
        ;;
    3|--both|-b)
        echo ""
        echo "--> [1/2] Building Standard FocusGram (com.ujwal.focusgram)..."
        ./gradlew :app:assembleRelease -PuseInstaPackage=false
        
        echo ""
        echo "--> [2/2] Building Uncloned Instagram Package Edition (com.instagram.android)..."
        ./gradlew :app:assembleRelease -PuseInstaPackage=true
        ;;
    *)
        echo "Invalid selection: $CHOICE. Please choose 1, 2, or 3."
        exit 1
        ;;
esac

echo ""
echo "======================================================="
echo "Build finished! Generated APK outputs:"
echo "======================================================="
ls -lh app/build/outputs/apk/release/*.apk 2>/dev/null || true
echo ""
