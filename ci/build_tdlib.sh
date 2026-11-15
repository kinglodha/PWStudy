#!/usr/bin/env bash
# Prototype only. Build pinned official TDLib JNI libraries into the current checkout.
set -euo pipefail

: "${ANDROID_HOME:?Install the Android SDK and set ANDROID_HOME}"
: "${GITHUB_WORKSPACE:?Set to the Android project directory}"
: "${RUNNER_TEMP:?Set to a temporary build directory}"
TDLIB_REVISION=c8828d50f5eac2fc7089ce1089d7ce9095c61b5e
NDK_VERSION=28.2.13676358
OPENSSL_VERSION=openssl-3.5.5

# Requires JDK, CMake 3.22.1, NDK, Android platform 34, gperf, php, perl and make.
# The Android SDK/NDK downloads and OpenSSL build require network access on the runner.
sdkmanager "platforms;android-34" "cmake;3.22.1" "ndk;${NDK_VERSION}"
git clone --filter=blob:none --no-checkout https://github.com/tdlib/td.git "$RUNNER_TEMP/tdlib-src"
git -C "$RUNNER_TEMP/tdlib-src" checkout --detach "$TDLIB_REVISION"
cd "$RUNNER_TEMP/tdlib-src/example/android"
./build-openssl.sh "$ANDROID_HOME" "$NDK_VERSION" "$PWD/third-party/openssl" "$OPENSSL_VERSION"
./build-tdlib.sh "$ANDROID_HOME" "$NDK_VERSION" "$PWD/third-party/openssl" c++_static JSONJava 23 "arm64-v8a armeabi-v7a x86_64 x86"
mkdir -p "$GITHUB_WORKSPACE/app/src/main/jniLibs"
cp -R tdlib/libs/. "$GITHUB_WORKSPACE/app/src/main/jniLibs/"
# The checked-in JsonClient.java must match the exact pinned TDLib source.
cmp tdlib/java/org/drinkless/tdlib/JsonClient.java "$GITHUB_WORKSPACE/app/src/main/java/org/drinkless/tdlib/JsonClient.java"
