with open('C:/Users/kakal/Documents/PowerLevelling/src/main/java/com/powerlevelling/client/fx/KiFx.java', 'r') as f:
    content = f.read()

old = """// Number of particles per tick (scaled by density)
        int count = Math.max(1, Mth.floor(6f * density * (isSelf ? 1f : 0.6f)));

        Vec3 eyePos = player.getEyePosition(1f);
        float radius = 0.6f + rand.nextFloat() * 0.4f;

        for (int i = 0; i < count && KiAuraParticle.canSpawn(); i++) {
            // Orbit around player at varying heights and radii
            float orbitRadius = 0.5f + rand.nextFloat() * 0.5f;
            float orbitSpeed = (0.03f + rand.nextFloat() * 0.03f) * (rand.nextBoolean() ? 1f : -1f);
            float startAngle = rand.nextFloat() * Mth.TWO_PI;
            float verticalOffset = -0.3f + rand.nextFloat() * 1.0f; // around waist to head

            // Spawn at player position with orbit data encoded in speed params
            level.addParticle(ModParticles.KI_AURA.get(),
                    eyePos.x, eyePos.y, eyePos.z,
                    orbitRadius, verticalOffset, orbitSpeed);
        }
    }
}"""

new = """// Number of particles per tick (scaled by density)
        int count = Math.max(1, Mth.floor(6f * density * (isSelf ? 1f : 0.6f)));
        
        // SSJ: spawn more particles with golden color
        if (isSSJ) {
            count = Math.max(1, Mth.floor(12f * density * (isSelf ? 1f : 0.8f)));
        }

        Vec3 eyePos = player.getEyePosition(1f);
        float radius = 0.6f + rand.nextFloat() * 0.4f;

        for (int i = 0; i < count && KiAuraParticle.canSpawn(); i++) {
            // Orbit around player at varying heights and radii
            float orbitRadius = 0.5f + rand.nextFloat() * 0.5f;
            float orbitSpeed = (0.03f + rand.nextFloat() * 0.03f) * (rand.nextBoolean() ? 1f : -1f);
            float startAngle = rand.nextFloat() * Mth.TWO_PI;
            float verticalOffset = -0.3f + rand.nextFloat() * 1.0f; // around waist to head

            // Spawn at player position with orbit data encoded in speed params
            level.addParticle(ModParticles.KI_AURA.get(),
                    eyePos.x, eyePos.y, eyePos.z,
                    orbitRadius, verticalOffset, orbitSpeed);
        }
        
        // SSJ: additional golden spark particles
        if (isSSJ) {
            spawnSSJAura(level, eyePos, rand, isSelf);
        }
    }
    
    private static void spawnSSJAura(ClientLevel level, Vec3 eyePos, RandomSource rand, boolean isSelf) {
        // Spawn golden spark particles around the player
        int count = isSelf ? 4 : 2;
        for (int i = 0; i < count; i++) {
            double x = eyePos.x + (rand.nextDouble() - 0.5) * 1.5;
            double y = eyePos.y + (rand.nextDouble() - 0.5) * 1.5;
            double z = eyePos.z + (rand.nextDouble() - 0.5) * 1.5;
            level.addParticle(ModParticles.KI_SPARK.get(), x, y, z, 0, 0.05, 0);
        }
    }
}"""

if old in content:
    content = content.replace(old, new)
    with open('C:/Users/kakal/Documents/PowerLevelling/src/main/java/com/powerlevelling/client/fx/KiFx.java', 'w') as f:
        f.write(content)
    print('Replacement successful')
else:
    print('Old text not found')