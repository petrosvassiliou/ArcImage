#!/usr/bin/env bash
# ==============================================================================
# ArcImage — Automated macOS Local Environment Setup
# Configures JDK 17, Android SDK (API 36), local.properties, and verifies build.
# Safe to re-run (idempotent) and contains zero secrets.
# ==============================================================================
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

echo "==> [1/4] Checking macOS environment for ArcImage..."
if [[ "$(uname -s)" != "Darwin" ]]; then
  echo "Warning: This helper targets macOS (detected $(uname -s))."
fi

# Ensure Homebrew is available on PATH (Apple Silicon & Intel Macs)
if ! command -v brew >/dev/null 2>&1; then
  if [[ -x /opt/homebrew/bin/brew ]]; then
    eval "$(/opt/homebrew/bin/brew shellenv)"
  elif [[ -x /usr/local/bin/brew ]]; then
    eval "$(/usr/local/bin/brew shellenv)"
  fi
fi

# ------------------------------------------------------------------------------
# 1. Locate or install JDK 17
# ------------------------------------------------------------------------------
echo "==> [2/4] Locating JDK 17..."
JDK17_HOME=""
if /usr/libexec/java_home -v 17 >/dev/null 2>&1; then
  JDK17_HOME="$(/usr/libexec/java_home -v 17)"
elif command -v brew >/dev/null 2>&1 && brew --prefix openjdk@17 >/dev/null 2>&1; then
  CANDIDATE="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home"
  if [[ -d "$CANDIDATE" ]]; then
    JDK17_HOME="$CANDIDATE"
  fi
fi

if [[ -z "$JDK17_HOME" ]]; then
  if ! command -v brew >/dev/null 2>&1; then
    echo "Error: JDK 17 not found and Homebrew is not installed."
    echo "Install Homebrew first:"
    echo '  /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"'
    echo "Then re-run: ./scripts/setup-macos.sh"
    exit 1
  fi
  echo "    Installing OpenJDK 17 via Homebrew..."
  brew install openjdk@17
  JDK17_HOME="$(brew --prefix openjdk@17)/libexec/openjdk.jdk/Contents/Home"
fi

export JAVA_HOME="$JDK17_HOME"
export PATH="$JAVA_HOME/bin:$PATH"
echo "    Using JAVA_HOME=$JAVA_HOME"

# Persist org.gradle.java.home in ~/.gradle/gradle.properties if not already set
mkdir -p "$HOME/.gradle"
touch "$HOME/.gradle/gradle.properties"
if ! grep -q '^org\.gradle\.java\.home=' "$HOME/.gradle/gradle.properties"; then
  printf '\norg.gradle.java.home=%s\n' "$JAVA_HOME" >> "$HOME/.gradle/gradle.properties"
  echo "    Saved org.gradle.java.home in ~/.gradle/gradle.properties"
fi

# ------------------------------------------------------------------------------
# 2. Locate or install Android SDK (API 36 + Build-Tools 36.0.0)
# ------------------------------------------------------------------------------
echo "==> [3/4] Checking Android SDK (API 36)..."
SDK_DIR="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
mkdir -p "$SDK_DIR"

find_sdkmanager() {
  local candidates=(
    "$SDK_DIR/cmdline-tools/latest/bin/sdkmanager"
  )
  if command -v brew >/dev/null 2>&1; then
    local brew_prefix
    brew_prefix="$(brew --prefix)"
    candidates+=(
      "$brew_prefix/share/android-commandlinetools/cmdline-tools/latest/bin/sdkmanager"
      "$brew_prefix/bin/sdkmanager"
    )
  fi
  for c in "${candidates[@]}"; do
    if [[ -x "$c" ]]; then
      echo "$c"
      return 0
    fi
  done
  if command -v sdkmanager >/dev/null 2>&1; then
    command -v sdkmanager
    return 0
  fi
  return 1
}

if [[ ! -d "$SDK_DIR/platforms/android-36" || ! -d "$SDK_DIR/build-tools/36.0.0" ]]; then
  SDKMANAGER_BIN="$(find_sdkmanager || true)"
  if [[ -z "$SDKMANAGER_BIN" ]]; then
    if ! command -v brew >/dev/null 2>&1; then
      echo "Error: Android SDK API 36 not found at $SDK_DIR and Homebrew is not installed."
      echo "Either install Android Studio or Homebrew, then re-run ./scripts/setup-macos.sh"
      exit 1
    fi
    echo "    Installing Android Command-Line Tools via Homebrew..."
    brew install --cask android-commandlinetools
    SDKMANAGER_BIN="$(find_sdkmanager)"
  fi

  echo "    Accepting Android SDK licenses and installing API 36 + Build-Tools 36.0.0..."
  yes | "$SDKMANAGER_BIN" --sdk_root="$SDK_DIR" --licenses >/dev/null || true
  "$SDKMANAGER_BIN" --sdk_root="$SDK_DIR" \
    "platform-tools" \
    "platforms;android-36" \
    "build-tools;36.0.0"
fi

printf 'sdk.dir=%s\n' "$SDK_DIR" > "$REPO_ROOT/local.properties"
echo "    Wrote local.properties -> sdk.dir=$SDK_DIR"

# ------------------------------------------------------------------------------
# 3. Run unit tests & assemble debug APKs via Gradle Wrapper
# ------------------------------------------------------------------------------
echo "==> [4/4] Verifying local Gradle build & unit tests..."
./gradlew testDebugUnitTest assembleDebug

echo ""
echo "=============================================================================="
echo "ArcImage local macOS setup complete!"
echo "Debug APKs built at:"
ls -lh "$REPO_ROOT"/app/build/outputs/apk/debug/*.apk
echo "=============================================================================="
