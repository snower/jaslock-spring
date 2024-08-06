package io.github.snower.jaslock.spring.boot.actuate;

import io.github.snower.jaslock.exceptions.SlockException;
import io.github.snower.jaslock.spring.boot.SlockTemplate;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;

public class SlockHealthIndicator implements HealthIndicator {
    private final SlockTemplate slockTemplate;

    public SlockHealthIndicator(SlockTemplate slockTemplate) {
        this.slockTemplate = slockTemplate;
    }

    @Override
    public Health health() {
        try {
            if (slockTemplate.getClient().ping()) {
                return Health.up().build();
            }
            return Health.down().build();
        } catch (SlockException e) {
            return Health.down()
                    .status(new Status(e.getClass().getName(), e.toString()))
                    .build();
        }
    }
}
