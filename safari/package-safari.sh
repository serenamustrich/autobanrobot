#!/bin/zsh
set -euo pipefail

SCRIPT_DIR=${0:A:h}
PROJECT_DIR="$SCRIPT_DIR/Xcode"

xcrun safari-web-extension-packager \
  --copy-resources \
  --macos-only \
  --swift \
  --app-name "AutoBanRobot Safari" \
  --bundle-identifier "com.serenamustrich.autobanrobot.safari" \
  --project-location "$PROJECT_DIR" \
  --no-open \
  --no-prompt \
  --force \
  "$SCRIPT_DIR/Resources"

# Keep the extension identifier under the containing app identifier generated
# by Apple's packager so unsigned local builds also pass embedded validation.
PROJECT_FILE="$PROJECT_DIR/AutoBanRobot Safari/AutoBanRobot Safari.xcodeproj/project.pbxproj"
/usr/bin/sed -i '' \
  's/com\.serenamustrich\.autobanrobot\.AutoBanRobot-Safari/com.serenamustrich.autobanrobot.safari/g' \
  "$PROJECT_FILE"

echo "Generated Safari Xcode project at: $PROJECT_DIR"
