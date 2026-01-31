
import math

def get_circle_points(cx, cy, r, angle_deg):
    rad = math.radians(angle_deg)
    x = cx + r * math.cos(rad)
    y = cy + r * math.sin(rad)
    return round(x, 2), round(y, 2)

# Generate Sunburst Path
# 60 ticks (every 6 degrees)
# Full Circle was 0 to 60.
# HALF CIRCLE (Top Half):
# 0 deg = East (Right)
# 90 deg = South (Down)
# 180 deg = West (Left)
# 270 deg = North (Top)
# Top Half is 180 to 360 (West -> North -> East).
# Corresponds to indices 30 to 60 (30*6=180, 60*6=360).
# Inner Radius 16, Outer Radius 21.

path_commands = []
# Full Circle 0 to 60 (360 degrees)
for i in range(60):
    angle = i * 6
    x1, y1 = get_circle_points(50, 50, 16, angle)
    # Ensure precision match
    x2, y2 = get_circle_points(50, 50, 21, angle)
    path_commands.append(f"M {x1},{y1} L {x2},{y2}")

sunburst_path = " ".join(path_commands)
print("SUNBURST_PATH:")
print(sunburst_path)
