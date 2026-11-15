import os
from PIL import Image

# Create a 64x64 aura texture with gradient rings
img = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
pixels = img.load()

center_x, center_y = 32, 32

for y in range(64):
    for x in range(64):
        dx = x - center_x
        dy = y - center_y
        dist = (dx*dx + dy*dy)**0.5
        
        # Multiple rings with fading
        if dist < 30:
            # Outer glow
            alpha = int(255 * (1 - dist/30) * 0.3)
            # Add pulsing rings
            ring = int(dist * 0.5) % 4
            if ring == 0:
                alpha = min(255, alpha + 100)
            
            # Color: golden/yellow for aura
            r = 255
            g = int(200 * (1 - dist/30))
            b = 0
            pixels[x, y] = (r, g, b, alpha)

# Save
os.makedirs('C:/Users/kakal/Documents/PowerLevelling/src/main/resources/assets/powerlevelling/textures/entity/aura', exist_ok=True)
img.save('C:/Users/kakal/Documents/PowerLevelling/src/main/resources/assets/powerlevelling/textures/entity/aura/aura.png')
print("Aura texture created")