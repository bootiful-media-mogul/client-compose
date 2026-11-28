#!/usr/bin/env bash
cd `dirname $0`
C=`pwd`
cd $C
cd ..
./gradlew :desktopApp:hotRunJvm --auto