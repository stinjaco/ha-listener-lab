package com.listenerlab.app;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ListenerAnalyzerTest {
    @Test public void identifiesConfirmedEspHomeSatelliteAndControls() throws Exception {
        ScanData data = new ScanData();
        data.config = new JSONObject("{\"version\":\"2026.9.0\"}");
        data.devices = new JSONArray("[{\"id\":\"dev1\",\"name\":\"Kitchen Voice\",\"model\":\"ESP32-S3-BOX\",\"identifiers\":[[\"esphome\",\"kitchen-voice\"]]}]");
        data.entities = new JSONArray("[" +
                "{\"entity_id\":\"assist_satellite.kitchen\",\"device_id\":\"dev1\",\"platform\":\"esphome\"}," +
                "{\"entity_id\":\"select.kitchen_vad_sensitivity\",\"device_id\":\"dev1\",\"platform\":\"esphome\"}" +
                "]");
        data.states = new JSONArray("[{\"entity_id\":\"assist_satellite.kitchen\",\"state\":\"idle\",\"attributes\":{\"friendly_name\":\"Kitchen Voice\"}}]");

        String report = ListenerAnalyzer.createReport(data);

        assertTrue(report.contains("Confirmed listeners: 1"));
        assertTrue(report.contains("ESP32-S3-BOX / ESPHome"));
        assertTrue(report.contains("VAD sensitivity"));
    }

    @Test public void doesNotClaimAnOrdinaryDeviceIsAListener() throws Exception {
        ScanData data = new ScanData();
        data.config = new JSONObject("{\"version\":\"2026.9.0\"}");
        data.devices = new JSONArray("[{\"id\":\"dev1\",\"name\":\"Kitchen Light\",\"model\":\"Bulb\"}]");
        data.entities = new JSONArray("[{\"entity_id\":\"light.kitchen\",\"device_id\":\"dev1\",\"platform\":\"hue\"}]");
        data.states = new JSONArray("[{\"entity_id\":\"light.kitchen\",\"state\":\"on\",\"attributes\":{}}]");

        String report = ListenerAnalyzer.createReport(data);

        assertTrue(report.contains("Confirmed listeners: 0"));
        assertFalse(report.contains("1. Kitchen Light"));
    }

    @Test public void fallsBackToStateWhenRegistryIsUnavailable() throws Exception {
        ScanData data = new ScanData();
        data.config = new JSONObject("{\"version\":\"2026.9.0\"}");
        data.states = new JSONArray("[{\"entity_id\":\"assist_satellite.office\",\"state\":\"idle\",\"attributes\":{}}]");

        String report = ListenerAnalyzer.createReport(data);

        assertTrue(report.contains("Confirmed listeners: 1"));
        assertTrue(report.contains("assist_satellite.office"));
    }
}

