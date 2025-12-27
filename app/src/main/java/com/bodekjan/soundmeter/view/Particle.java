package com.bodekjan.soundmeter.view;

import java.util.Random;

public class Particle {
    float x, y;
    float radius;
    float speed;
    float angle;
    int alpha;

    private static final Random random = new Random();

    public Particle(int viewWidth, int viewHeight) {
        this.x = random.nextFloat() * viewWidth;
        this.y = random.nextFloat() * viewHeight;
        this.radius = random.nextFloat() * 3 + 1; // Radius between 1 and 4
        this.speed = random.nextFloat() * 1.5f + 0.5f; // Speed between 0.5 and 2
        this.angle = (float) (random.nextFloat() * 2 * Math.PI);
        this.alpha = random.nextInt(150) + 50; // Alpha between 50 and 200
    }

    public void move(int viewWidth, int viewHeight) {
        x += speed * Math.cos(angle);
        y += speed * Math.sin(angle);

        if (x < 0) x = viewWidth;
        if (x > viewWidth) x = 0;
        if (y < 0) y = viewHeight;
        if (y > viewHeight) y = 0;
    }
}
