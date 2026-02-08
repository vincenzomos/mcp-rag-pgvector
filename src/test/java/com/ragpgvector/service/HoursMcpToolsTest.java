package com.ragpgvector.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HoursMcpToolsTest {

    private final HoursMcpTools tools = new HoursMcpTools();

    @Test
    void confirmUseOfUncertainData_noRecords_returnsNoConfirmationNeeded() {
        String result = tools.confirmUseOfUncertainData(null);
        assertTrue(result.contains("No records") || result.contains("No confirmation needed"));
    }

    @Test
    void confirmUseOfUncertainData_allComplete_returnsNoConfirmationNeeded() {
        List<Map<String, Object>> records = List.of(
                Map.of("projectCode", "Devops", "month", "January", "year", 2024, "hours", 8, "complete", "Y"),
                Map.of("projectCode", "Standby", "month", "February", "year", 2024, "hours", 4, "complete", "Y")
        );

        String result = tools.confirmUseOfUncertainData(records);
        assertTrue(result.contains("All used records are marked as complete") || result.contains("No additional confirmation"));
    }

    @Test
    void confirmUseOfUncertainData_hasIncomplete_returnsQuestion() {
        List<Map<String, Object>> records = List.of(
                Map.of("projectCode", "Devops", "month", "January", "year", 2024, "hours", 8, "complete", "N"),
                Map.of("projectCode", "Standby", "month", "February", "year", 2024, "hours", 4, "complete", "Y")
        );

        String result = tools.confirmUseOfUncertainData(records);
        assertTrue(result.toLowerCase().contains("would you like") || result.contains("complete='N'"));
    }
}
