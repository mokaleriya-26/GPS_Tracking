package com.gps.tracking.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IntentClassifierTest {

    private IntentClassifier classifier;

    @BeforeEach
    void setUp() {
        classifier = new IntentClassifier();
    }

    @Test
    void testDriverRankingQueries() {
        assertIntent("Who is safest?", IntentClassifier.Intent.DRIVER_RANKING);
        assertIntent("Who is the safest driver?", IntentClassifier.Intent.DRIVER_RANKING);
        assertIntent("Which driver is best?", IntentClassifier.Intent.DRIVER_RANKING);
        assertIntent("Show top drivers", IntentClassifier.Intent.DRIVER_RANKING);
        assertIntent("Rank drivers", IntentClassifier.Intent.DRIVER_RANKING);
        assertIntent("Who has the highest safety score?", IntentClassifier.Intent.DRIVER_RANKING);
        assertIntent("Which driver has the lowest risk?", IntentClassifier.Intent.DRIVER_RANKING);
        assertIntent("Show me the top driver", IntentClassifier.Intent.DRIVER_RANKING);
    }

    @Test
    void testDriverRecommendationQueries() {
        assertIntent("Recommend a driver for a long-distance trip", IntentClassifier.Intent.DRIVER_RECOMMENDATION);
        assertIntent("Who should I assign for an important route?", IntentClassifier.Intent.DRIVER_RECOMMENDATION);
        assertIntent("Suggest 3 drivers", IntentClassifier.Intent.DRIVER_RECOMMENDATION);
        assertIntent("Recommend drivers", IntentClassifier.Intent.DRIVER_RECOMMENDATION);
    }

    @Test
    void testOverallReportQueries() {
        assertIntent("Today's fleet report", IntentClassifier.Intent.OVERALL_REPORT);
        assertIntent("How did the fleet perform today?", IntentClassifier.Intent.OVERALL_REPORT);
        assertIntent("Show today's overall report", IntentClassifier.Intent.OVERALL_REPORT);
        assertIntent("How was the fleet last week?", IntentClassifier.Intent.OVERALL_REPORT);
        assertIntent("Fleet performance report for September", IntentClassifier.Intent.OVERALL_REPORT);
    }

    @Test
    void testDriverReportQueries() {
        assertIntent("Rohan's September report", IntentClassifier.Intent.DRIVER_REPORT);
        assertIntent("Show DRV002 monthly report", IntentClassifier.Intent.DRIVER_REPORT);
        assertIntent("How did Rohan perform in September?", IntentClassifier.Intent.DRIVER_REPORT);
        assertIntent("Give me Rohan's report for September", IntentClassifier.Intent.DRIVER_REPORT);
    }

    @Test
    void testAlertQueries() {
        assertIntent("Show open alerts", IntentClassifier.Intent.ALERT_QUERY);
        assertIntent("Any critical alerts?", IntentClassifier.Intent.ALERT_QUERY);
        assertIntent("How many overspeed alerts occurred this week?", IntentClassifier.Intent.ALERT_QUERY);
        assertIntent("Which driver has the most violations?", IntentClassifier.Intent.ALERT_QUERY);
        assertIntent("Which driver needs training?", IntentClassifier.Intent.ALERT_QUERY);
        assertIntent("Which drivers have repeated harsh braking?", IntentClassifier.Intent.ALERT_QUERY);
        assertIntent("What is the most common alert type?", IntentClassifier.Intent.ALERT_QUERY);
        assertIntent("Show unresolved critical alerts", IntentClassifier.Intent.ALERT_QUERY);
        assertIntent("How many warnings occurred?", IntentClassifier.Intent.ALERT_QUERY);
    }

    @Test
    void testVehicleQueries() {
        assertIntent("Which vehicle has the most alerts?", IntentClassifier.Intent.VEHICLE_ALERT_ANALYSIS);
        assertIntent("What vehicle causes the most trouble?", IntentClassifier.Intent.VEHICLE_ALERT_ANALYSIS);
        assertIntent("Which vehicles had GPS disconnect issues?", IntentClassifier.Intent.VEHICLE_ALERT_ANALYSIS);
        assertIntent("Which vehicles are inactive?", IntentClassifier.Intent.VEHICLE_STATS);
        assertIntent("Which vehicle has the highest speed?", IntentClassifier.Intent.VEHICLE_STATS);
    }

    @Test
    void testTripQueries() {
        assertIntent("How many trips did Rohan complete?", IntentClassifier.Intent.TRIP_QUERY);
        assertIntent("Show today's trips", IntentClassifier.Intent.TRIP_QUERY);
        assertIntent("Who travelled the most?", IntentClassifier.Intent.TRIP_QUERY);
        assertIntent("Which driver completed the most trips?", IntentClassifier.Intent.TRIP_QUERY);
        assertIntent("What is the average trip distance?", IntentClassifier.Intent.TRIP_QUERY);
        assertIntent("What was the longest trip?", IntentClassifier.Intent.TRIP_QUERY);
        assertIntent("What is the average trip duration?", IntentClassifier.Intent.TRIP_QUERY);
    }

    @Test
    void testEntryExitQueries() {
        assertIntent("How many vehicles entered today?", IntentClassifier.Intent.ENTRY_EXIT);
        assertIntent("How many vehicles exited today?", IntentClassifier.Intent.ENTRY_EXIT);
    }

    @Test
    void testCompareQueries() {
        assertIntent("Compare Rohan and Deepak", IntentClassifier.Intent.COMPARE_DRIVERS);
        assertIntent("Compare DRV001 and DRV002", IntentClassifier.Intent.COMPARE_DRIVERS);
        assertIntent("Compare the top 5 drivers", IntentClassifier.Intent.COMPARE_DRIVERS);
    }

    @Test
    void testFleetSummaryQueries() {
        assertIntent("Give me today's fleet summary", IntentClassifier.Intent.FLEET_SUMMARY);
        assertIntent("How is the fleet doing today?", IntentClassifier.Intent.FLEET_SUMMARY);
        assertIntent("Fleet overview", IntentClassifier.Intent.FLEET_SUMMARY);
        assertIntent("Give me today's operational summary", IntentClassifier.Intent.FLEET_SUMMARY);
        assertIntent("Give me a fleet health summary", IntentClassifier.Intent.FLEET_SUMMARY);
    }

    private void assertIntent(String message, IntentClassifier.Intent expected) {
        IntentClassifier.ClassifiedIntent result = classifier.classify(message);
        assertEquals(expected, result.getIntent(),
            String.format("Expected %s for '%s' but got %s (score=%.2f)",
                expected, message, result.getIntent(), result.getScore()));
    }
}
