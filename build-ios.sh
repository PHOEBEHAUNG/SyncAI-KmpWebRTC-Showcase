#!/bin/bash

# ========================================
# VLMWebRTC iOS Build and Install Script
# ========================================
# This script builds iOS app with CocoaPods integration
# Usage:
#   ./build-ios.sh                      # Build for simulator (Debug)
#   ./build-ios.sh device               # Build for device (Debug)
#   ./build-ios.sh device release       # Build for device (Release)
#   ./build-ios.sh simulator --no-install  # Build only, don't install
#   ./build-ios.sh device --no-install     # Build only for device
# ========================================

set -e  # Exit on error

# Fix for Android Studio Ladybug/Otter JBR spawn helper issue
# Use system JDK instead of Android Studio bundled JBR
if [ -d "/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home" ]; then
    export JAVA_HOME="/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home"
elif [ -d "/opt/homebrew/opt/openjdk@17" ]; then
    export JAVA_HOME="/opt/homebrew/opt/openjdk@17"
elif [ -n "$(/usr/libexec/java_home -v 17 2>/dev/null)" ]; then
    export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
fi
export PATH="$JAVA_HOME/bin:$PATH"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  VLMWebRTC iOS Build Script${NC}"
echo -e "${BLUE}========================================${NC}"

# Parse arguments
TARGET=${1:-simulator}  # simulator or device
BUILD_TYPE=${2:-debug}  # debug or release
INSTALL_APP=true

# Check for --no-install flag
for arg in "$@"; do
    if [ "$arg" = "--no-install" ]; then
        INSTALL_APP=false
    fi
done

# Adjust BUILD_TYPE if --no-install is in position 2
if [ "$BUILD_TYPE" = "--no-install" ]; then
    BUILD_TYPE="debug"
fi

# Configuration
WORKSPACE="iosApp/iosApp.xcworkspace"
SCHEME="iosApp"
IOS_APP_DIR="iosApp"
BUNDLE_ID="com.codingdrama.vlmwebrtc.VLMWebRTC"

# Set build configuration
if [ "$BUILD_TYPE" = "release" ]; then
    CONFIGURATION="Release"
else
    CONFIGURATION="Debug"
fi

# Set destination based on target
if [ "$TARGET" = "device" ]; then
    echo -e "${YELLOW}Building for iOS Device ($CONFIGURATION)...${NC}"
    DESTINATION="generic/platform=iOS"
    SDK="iphoneos"
else
    echo -e "${YELLOW}Building for iOS Simulator ($CONFIGURATION)...${NC}"
    DESTINATION="platform=iOS Simulator,name=iPhone 15 Pro,OS=latest"
    SDK="iphonesimulator"
fi

# Step 1: Install/Update CocoaPods
echo -e "${YELLOW}Step 1: Checking CocoaPods...${NC}"

if ! command -v pod &> /dev/null; then
    echo -e "${RED}❌ CocoaPods not found!${NC}"
    echo -e "${YELLOW}Installing CocoaPods...${NC}"
    sudo gem install cocoapods
fi

echo -e "${BLUE}CocoaPods version: $(pod --version)${NC}"

# Step 2: Generate dummy framework for CocoaPods
echo -e "${YELLOW}Step 2: Generating dummy framework for CocoaPods...${NC}"

# Generate dummy framework first (required for CocoaPods)
echo -e "${BLUE}Generating dummy framework...${NC}"
./gradlew :composeApp:generateDummyFramework

if [ $? -ne 0 ]; then
    echo -e "${YELLOW}⚠️  Dummy framework generation had issues, continuing...${NC}"
fi

echo -e "${GREEN}✅ Dummy framework generated!${NC}"

# Step 3: Install CocoaPods dependencies via Gradle
echo -e "${YELLOW}Step 3: Installing CocoaPods dependencies via Gradle...${NC}"

# Use Gradle's podInstall task which properly sets up the project
./gradlew :composeApp:podInstall

if [ $? -ne 0 ]; then
    echo -e "${RED}❌ Gradle podInstall failed!${NC}"
    exit 1
fi

echo -e "${GREEN}✅ CocoaPods dependencies configured!${NC}"

# Step 4: Build Kotlin/Native framework (after pods are installed for cinterop)
echo -e "${YELLOW}Step 4: Building Kotlin/Native framework...${NC}"

# Build the framework for the target platform
if [ "$TARGET" = "device" ]; then
    echo -e "${BLUE}Building for iOS device (arm64)...${NC}"
    if [ "$BUILD_TYPE" = "release" ]; then
        ./gradlew :composeApp:linkPodReleaseFrameworkIosArm64
    else
        ./gradlew :composeApp:linkPodDebugFrameworkIosArm64
    fi
else
    echo -e "${BLUE}Building for iOS simulator (arm64)...${NC}"
    if [ "$BUILD_TYPE" = "release" ]; then
        ./gradlew :composeApp:linkPodReleaseFrameworkIosSimulatorArm64
    else
        ./gradlew :composeApp:linkPodDebugFrameworkIosSimulatorArm64
    fi
fi

if [ $? -ne 0 ]; then
    echo -e "${RED}❌ Kotlin framework build failed!${NC}"
    exit 1
fi

echo -e "${GREEN}✅ Kotlin framework built successfully!${NC}"

# Step 5: Install pods for iosApp workspace
echo -e "${YELLOW}Step 5: Installing pods for iosApp...${NC}"
cd $IOS_APP_DIR
pod install
if [ $? -ne 0 ]; then
    echo -e "${YELLOW}⚠️  Pod install had issues, trying --repo-update...${NC}"
    pod install --repo-update
fi
cd ..
echo -e "${GREEN}✅ iosApp pods installed!${NC}"

# Step 6: Build iOS app with Xcode
echo -e "${YELLOW}Step 6: Building iOS app...${NC}"

if [ "$TARGET" = "device" ]; then
    # Build for device (creates IPA)
    echo -e "${BLUE}Building archive for device...${NC}"
    
    xcodebuild -workspace "$WORKSPACE" \
        -scheme "$SCHEME" \
        -configuration "$CONFIGURATION" \
        -sdk "$SDK" \
        -destination "$DESTINATION" \
        -archivePath "build/iosApp.xcarchive" \
        archive \
        CODE_SIGN_IDENTITY="" \
        CODE_SIGNING_REQUIRED=NO \
        CODE_SIGNING_ALLOWED=NO
    
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✅ iOS app archived successfully!${NC}"
        echo -e "${YELLOW}Archive location: build/iosApp.xcarchive${NC}"
        
        if [ "$INSTALL_APP" = false ]; then
            echo -e "${BLUE}ℹ️  Build only mode - skipping installation${NC}"
        else
            echo -e "${YELLOW}ℹ️  To install on device, open Xcode and deploy from the archive.${NC}"
        fi
    else
        echo -e "${RED}❌ Build failed!${NC}"
        exit 1
    fi
else
    # Build for simulator
    echo -e "${BLUE}Building for simulator...${NC}"
    
    xcodebuild -workspace "$WORKSPACE" \
        -scheme "$SCHEME" \
        -configuration "$CONFIGURATION" \
        -sdk "$SDK" \
        -destination "$DESTINATION" \
        build
    
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✅ iOS app built successfully!${NC}"
        
        if [ "$INSTALL_APP" = false ]; then
            echo -e "${BLUE}ℹ️  Build only mode - skipping installation${NC}"
            echo -e "${YELLOW}Build location: ~/Library/Developer/Xcode/DerivedData/${NC}"
        else
            # Try to install on simulator
            echo -e "${YELLOW}Installing on simulator...${NC}"
            
            # Get simulator ID
            SIMULATOR_ID=$(xcrun simctl list devices | grep "iPhone 15 Pro" | grep "Booted" | awk -F'[()]' '{print $2}' | head -n 1)
            
            if [ -z "$SIMULATOR_ID" ]; then
                echo -e "${YELLOW}No booted simulator found. Booting iPhone 15 Pro...${NC}"
                xcrun simctl boot "iPhone 15 Pro" 2>/dev/null || true
                sleep 3
                SIMULATOR_ID=$(xcrun simctl list devices | grep "iPhone 15 Pro" | grep "Booted" | awk -F'[()]' '{print $2}' | head -n 1)
            fi
            
            if [ -n "$SIMULATOR_ID" ]; then
                # Find the .app bundle
                APP_PATH=$(find ~/Library/Developer/Xcode/DerivedData -name "iosApp.app" | grep "$CONFIGURATION-iphonesimulator" | head -n 1)
                
                if [ -n "$APP_PATH" ]; then
                    xcrun simctl install "$SIMULATOR_ID" "$APP_PATH"
                    echo -e "${GREEN}✅ App installed on simulator!${NC}"
                    
                    # Launch the app
                    xcrun simctl launch "$SIMULATOR_ID" "$BUNDLE_ID"
                    echo -e "${GREEN}✅ App launched!${NC}"
                else
                    echo -e "${YELLOW}⚠️  Could not find app bundle. Build successful but not installed.${NC}"
                fi
            else
                echo -e "${YELLOW}⚠️  Could not find/boot simulator. Build successful but not installed.${NC}"
            fi
        fi
    else
        echo -e "${RED}❌ Build failed!${NC}"
        exit 1
    fi
fi

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  Build Complete!${NC}"
echo -e "${GREEN}========================================${NC}"
