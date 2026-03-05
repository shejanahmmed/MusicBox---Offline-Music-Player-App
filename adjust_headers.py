import os, re

layout_dir = r"C:\Users\ShejanAhmmed\Documents\All My Projects\Apps\MusicBox---Offline-Music-Player-App\app\src\main\res\layout"

for filename in os.listdir(layout_dir):
    if filename.startswith("activity_") and filename.endswith(".xml"):
        filepath = os.path.join(layout_dir, filename)
        with open(filepath, 'r', encoding='utf-8') as f:
            content = f.read()

        new_content = content
        
        # Replace paddingTop="40dp" with padding="24dp"
        if 'android:paddingTop="40dp"' in new_content:
            new_content = new_content.replace('android:paddingTop="40dp"', 'android:paddingTop="24dp"')
        
        # Replace layout_marginTop="40dp" with layout_marginTop="24dp"
        if 'android:layout_marginTop="40dp"' in new_content:
            new_content = new_content.replace('android:layout_marginTop="40dp"', 'android:layout_marginTop="24dp"')
            
        if new_content != content:
            with open(filepath, 'w', encoding='utf-8') as f:
                f.write(new_content)
            print(f"Shifted header up in {filename}")

print("Done shifting headers.")
