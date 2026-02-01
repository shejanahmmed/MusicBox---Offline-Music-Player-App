
import math

def get_circle_points(cx, cy, r, angle_deg):
    rad = math.radians(angle_deg)
    x = cx + r * math.cos(rad)
    y = cy + r * math.sin(rad)
    return round(x, 2), round(y, 2)

# Generate Sunburst Path
# Full Circle 0 to 360
path_commands = []
for i in range(60):
    angle = i * 6
    x1, y1 = get_circle_points(50, 50, 16, angle)
    x2, y2 = get_circle_points(50, 50, 21, angle)
    path_commands.append(f"M {x1},{y1} L {x2},{y2}")

sunburst_path = " ".join(path_commands)
print("SUNBURST_PATH:")
print(sunburst_path)

import random

# --- NOISE GENERATION LOGIC ---
# Generate random dust and scratches
noise_cmds = []

def get_random_point_in_circle(max_r):
    # Random radius (sqroot for uniform distribution area-wise)
    r = max_r * math.sqrt(random.uniform(0, 1))
    theta = random.uniform(0, 360)
    return get_circle_points(50, 50, r, theta)

# 1. Dust Specks (Tiny dots/lines)
num_dust = 60
for _ in range(num_dust):
    cx, cy = get_random_point_in_circle(48.0) # Stay inside R=48
    # Tiny line length 0.2 to 0.4
    length = random.uniform(0.2, 0.4)
    angle = random.uniform(0, 360)
    x2, y2 = get_circle_points(cx, cy, length, angle)
    noise_cmds.append(f"M {round(cx,2)},{round(cy,2)} L {round(x2,2)},{round(y2,2)}")

# 2. Scratches (Shorter thin lines)
num_scratches = 15 
for _ in range(num_scratches):
    cx, cy = get_random_point_in_circle(46.0) # Stay inside R=46 (margin for line length)
    # Reduced Length 1.0 to 3.0 (was 3 to 8)
    length = random.uniform(1.0, 3.0)
    # Random angle
    angle = random.uniform(0, 360)
    
    # Calculate start and end centered on cx, cy
    # Start
    x1, y1 = get_circle_points(cx, cy, length/2, angle)
    # End (Opposite direction)
    x2, y2 = get_circle_points(cx, cy, length/2, angle + 180)
    
    noise_cmds.append(f"M {round(x1,2)},{round(y1,2)} L {round(x2,2)},{round(y2,2)}")

noise_path = " ".join(noise_cmds)
print("NOISE_PATH:")
print(noise_path)


