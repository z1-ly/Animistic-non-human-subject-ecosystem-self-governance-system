package com.ecovoice.config;

import com.ecovoice.service.SandboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SimulationScheduler {

    private final SandboxService sandboxService;

    @Value("${sandbox.simulation.enabled:true}")
    private boolean enabled;

    @Scheduled(fixedDelayString = "${sandbox.simulation.interval-ms:3000}")
    public void simulate() {
        if (enabled) {
            sandboxService.simulateRandomReading();
        }
    }
}
