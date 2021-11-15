#!/bin/bash

cmake -Bbuild -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM=android-16 -DCMAKE_TOOLCHAIN_FILE=$ANDROID_HOME/ndk-bundle/build/cmake/android.toolchain.cmake

cd build

echo "===========================start make============================="
make
echo "===========================make done=============================="

cd ..