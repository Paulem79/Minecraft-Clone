package ovh.paulem.mc.engine.render;

import ovh.paulem.mc.world.World;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class ParticleSystem {
    private final List<Particle> particles = new ArrayList<>();

    public void addParticle(Particle particle) {
        particles.add(particle);
    }

    public void update(float deltaTime, World world) {
        Iterator<Particle> it = particles.iterator();
        while (it.hasNext()) {
            Particle p = it.next();
            p.update(deltaTime, world);
            if (!p.isAlive()) {
                it.remove();
            }
        }
    }

    public List<Particle> getParticles() {
        return particles;
    }
}