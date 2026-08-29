package com.ecovoice.config;

import com.ecovoice.service.EcologicalInsightService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InsightAnalysisScheduler {

    private final EcologicalInsightService insightService;

    @Scheduled(fixedDelayString = "${sandbox.insight.interval-ms:60000}")
    public void analyze() {
        insightService.analyzeAll();
    }
}
