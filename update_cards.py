import re
import os

file_path = 'demo/src/main/res/layout/activity_main.xml'

with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# Find all MaterialCardView closing tags that have focusable="true"
pattern = r'(android:focusable="true")>'
replacement = 'android:focusable="true"\n                app:cardForegroundColor="@color/card_foreground"\n                app:rippleColor="@color/ripple_color">'

new_content = re.sub(pattern, replacement, content)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(new_content)

print('Updated all MaterialCardView with click effects')
