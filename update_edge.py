import os
import re

app_dir = r"C:\Users\ShejanAhmmed\Documents\All My Projects\Apps\MusicBox---Offline-Music-Player-App\app\src\main"
layout_dir = os.path.join(app_dir, "res", "layout")
java_dir = os.path.join(app_dir, "java", "com", "shejan", "musicbox")

# Update XML Layouts
for filename in os.listdir(layout_dir):
    if filename.startswith("activity_") and filename.endswith(".xml"):
        filepath = os.path.join(layout_dir, filename)
        with open(filepath, 'r', encoding='utf-8') as f:
            content = f.read()

        if 'android:id="@+id/main"' not in content:
            new_content = re.sub(
                r'(xmlns:android="http://schemas.android.com/apk/res/android")',
                r'\1\n    android:id="@+id/main"',
                content,
                count=1
            )
            with open(filepath, 'w', encoding='utf-8') as f:
                f.write(new_content)
        print(f"Updated XML layout: {filename}")

# Update Kotlin Activities
for filename in os.listdir(java_dir):
    if filename.endswith("Activity.kt"):
        filepath = os.path.join(java_dir, filename)
        with open(filepath, 'r', encoding='utf-8') as f:
            content = f.read()
        
        modified = False
        
        # 1. Add enableEdgeToEdge import if missing
        if "androidx.activity.enableEdgeToEdge" not in content:
            content = re.sub(
                r'(import android.os.Bundle)',
                r'import androidx.activity.enableEdgeToEdge\n\1',
                content,
                count=1
            )
            modified = True

        # 2. Replace WindowCompat block with enableEdgeToEdge()
        window_block = re.compile(
            r'WindowCompat\.setDecorFitsSystemWindows\(window,\s*false\)\s*window\.statusBarColor\s*=\s*android\.graphics\.Color\.TRANSPARENT\s*window\.navigationBarColor\s*=\s*android\.graphics\.Color\.TRANSPARENT',
            re.MULTILINE | re.DOTALL
        )
        if window_block.search(content):
            content = window_block.sub('enableEdgeToEdge()', content)
            modified = True

        # 3. Handle WindowCompat + Status Color
        window_block2 = re.compile(
            r'WindowCompat\.setDecorFitsSystemWindows\(window,\s*false\)\s*window\.statusBarColor\s*=\s*android\.graphics\.Color\.TRANSPARENT',
            re.MULTILINE | re.DOTALL
        )
        if window_block2.search(content):
            content = window_block2.sub('enableEdgeToEdge()', content)
            modified = True
            
        # 4. Handle WindowCompat alone
        window_block3 = re.compile(
            r'WindowCompat\.setDecorFitsSystemWindows\(window,\s*false\)',
            re.MULTILINE | re.DOTALL
        )
        if window_block3.search(content):
            content = window_block3.sub('enableEdgeToEdge()', content)
            modified = True
        
        # 5. Clean up duplicate enableEdgeToEdge() if both 3 and 4 match or user had it already
        content = re.sub(r'(enableEdgeToEdge\(\)\s*){2,}', 'enableEdgeToEdge()\n        ', content)

        # 6. Replace findViewById(android.R.id.content)
        if "findViewById(android.R.id.content)" in content:
            content = content.replace("findViewById(android.R.id.content)", "findViewById(R.id.main)")
            modified = True
            
        if modified:
            with open(filepath, 'w', encoding='utf-8') as f:
                f.write(content)
        print(f"Updated Activity code: {filename}")

print("Done.")
