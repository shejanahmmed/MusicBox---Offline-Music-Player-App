import os, re

layout_dir = r"C:\Users\ShejanAhmmed\Documents\All My Projects\Apps\MusicBox---Offline-Music-Player-App\app\src\main\res\layout"

for filename in os.listdir(layout_dir):
    if filename.startswith("activity_") and filename.endswith(".xml"):
        filepath = os.path.join(layout_dir, filename)
        with open(filepath, 'r', encoding='utf-8') as f:
            content = f.read()

        # Find the root tag: from first < that is a letter up to its closing >
        # E.g. <androidx.constraintlayout.widget.ConstraintLayout ... >
        match = re.search(r'<([a-zA-Z][a-zA-Z0-9_.-]+)([^>]*)>', content)
        if match:
            tag_name = match.group(1)
            attrs = match.group(2)
            
            # Remove all android:id attributes from attrs
            new_attrs = re.sub(r'android:id="[^"]+"', '', attrs)
            
            # Ensure it starts nicely
            new_attrs = '\n    android:id="@+id/main"' + new_attrs
            
            new_tag = f"<{tag_name}{new_attrs}>"
            
            new_content = content[:match.start()] + new_tag + content[match.end():]
            
            if new_content != content:
                with open(filepath, 'w', encoding='utf-8') as f:
                    f.write(new_content)
                print(f"Fixed {filename}")

print("Done fixing xmls.")
