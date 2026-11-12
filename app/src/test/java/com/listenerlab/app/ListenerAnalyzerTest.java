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

        AnalysisResult result = ListenerAnalyzer.analyze(data);
        String report = result.report;

        assertTrue(report.contains("Confirmed listeners: 1"));
        assertTrue(report.contains("ESP32-S3-BOX / ESPHome"));
        assertTrue(report.contains("VAD sensitivity"));
        assertTrue(result.firmwareCandidate);
        assertTrue(report.contains("DESKTOP RECOVERY PATH"));
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
        assertFalse(ListenerAnalyzer.analyze(data).firmwareCandidate);
    }

    @Test public void fallsBackToStateWhenRegistryIsUnavailable() throws Exception {
        ScanData data = new ScanData();
        data.config = new JSONObject("{\"version\":\"2026.9.0\"}");
        data.states = new JSONArray("[{\"entity_id\":\"assist_satellite.office\",\"state\":\"idle\",\"attributes\":{}}]");

        String report = ListenerAnalyzer.createReport(data);

        assertTrue(report.contains("Confirmed listeners: 1"));
        assertTrue(report.contains("assist_satellite.office"));
    }

    @Test public void googleCastSpeakerDoesNotUnlockFirmware() throws Exception {
        ScanData data = new ScanData();
        data.config = new JSONObject("{\"version\":\"2026.9.0\"}");
        data.states = new JSONArray("[{\"entity_id\":\"media_player.kitchen_nest\",\"state\":\"idle\",\"attributes\":{\"friendly_name\":\"Kitchen Google Nest speaker\"}}]");

        AnalysisResult result = ListenerAnalyzer.analyze(data);

        assertFalse(result.firmwareCandidate);
        assertTrue(result.report.contains("Cast or Nest speaker is not automatically an Assist microphone"));
        assertFalse(result.report.contains("DESKTOP RECOVERY PATH"));
    }

    @Test public void staleEspHomeEntryOfAReflashedAtomIsNotAListenerAndDoesNotUnlockFirmware() throws Exception {
        // The board runs other firmware now (an LCARS room listener); its ESPHome entry stays in Home Assistant,
        // unavailable, and only the dashboard-served update entity still answers.
        ScanData data = new ScanData();
        data.config = new JSONObject("{\"version\":\"2026.9.0\"}");
        data.devices = new JSONArray("[{\"id\":\"dev1\",\"name\":\"lacars listener 1\",\"model\":\"atom-echo-voice-assistant\",\"manufacturer\":\"M5Stack\",\"identifiers\":[[\"esphome\",\"90:15:06:fd:f5:34\"]]}]");
        data.entities = new JSONArray("[" +
                "{\"entity_id\":\"assist_satellite.lacars_listener_1\",\"device_id\":\"dev1\",\"platform\":\"esphome\"}," +
                "{\"entity_id\":\"select.lacars_listener_1_wake_word\",\"device_id\":\"dev1\",\"platform\":\"esphome\"}," +
                "{\"entity_id\":\"switch.lacars_listener_1_mute\",\"device_id\":\"dev1\",\"platform\":\"esphome\"}," +
                "{\"entity_id\":\"update.lacars_listener_1_firmware\",\"device_id\":\"dev1\",\"platform\":\"esphome\"}" +
                "]");
        data.states = new JSONArray("[" +
                "{\"entity_id\":\"assist_satellite.lacars_listener_1\",\"state\":\"unavailable\",\"attributes\":{\"friendly_name\":\"lacars listener 1\"}}," +
                "{\"entity_id\":\"select.lacars_listener_1_wake_word\",\"state\":\"unavailable\",\"attributes\":{}}," +
                "{\"entity_id\":\"switch.lacars_listener_1_mute\",\"state\":\"unavailable\",\"attributes\":{}}," +
                "{\"entity_id\":\"update.lacars_listener_1_firmware\",\"state\":\"off\",\"attributes\":{}}" +
                "]");

        AnalysisResult result = ListenerAnalyzer.analyze(data);
        String report = result.report;

        assertTrue(report.contains("Confirmed listeners: 0"));
        assertTrue(report.contains("Possible voice devices: 1"));
        assertTrue(report.contains("1. lacars listener 1"));
        assertTrue(report.contains("registered but not answering"));
        assertTrue(report.contains("Listener state: unavailable"));
        assertTrue(report.contains("Not answering in Home Assistant"));
        assertTrue(report.contains("C. Identify first"));
        assertFalse(report.contains("confirmed listener"));
        assertFalse(report.contains("C. ESPHome advanced"));
        assertFalse(report.contains("Restore the unavailable device"));
        assertFalse(result.firmwareCandidate);
        assertFalse(report.contains("DESKTOP RECOVERY PATH"));
    }

    @Test public void unavailableStateOnlySatelliteIsNotConfirmed() throws Exception {
        ScanData data = new ScanData();
        data.config = new JSONObject("{\"version\":\"2026.9.0\"}");
        data.states = new JSONArray("[{\"entity_id\":\"assist_satellite.computer_2\",\"state\":\"unavailable\",\"attributes\":{}}]");

        AnalysisResult result = ListenerAnalyzer.analyze(data);

        assertTrue(result.report.contains("Confirmed listeners: 0"));
        assertTrue(result.report.contains("Possible voice devices: 1"));
        assertTrue(result.report.contains("Not answering in Home Assistant"));
        assertFalse(result.firmwareCandidate);
    }

    @Test public void offlineSatelliteOnADeviceThatStillAnswersIsReportedForRestore() throws Exception {
        ScanData data = new ScanData();
        data.config = new JSONObject("{\"version\":\"2026.9.0\"}");
        data.devices = new JSONArray("[{\"id\":\"dev1\",\"name\":\"Kitchen Voice\",\"model\":\"ESP32-S3-BOX\",\"identifiers\":[[\"esphome\",\"kitchen-voice\"]]}]");
        data.entities = new JSONArray("[" +
                "{\"entity_id\":\"assist_satellite.kitchen\",\"device_id\":\"dev1\",\"platform\":\"esphome\"}," +
                "{\"entity_id\":\"select.kitchen_vad_sensitivity\",\"device_id\":\"dev1\",\"platform\":\"esphome\"}" +
                "]");
        data.states = new JSONArray("[" +
                "{\"entity_id\":\"assist_satellite.kitchen\",\"state\":\"unavailable\",\"attributes\":{}}," +
                "{\"entity_id\":\"select.kitchen_vad_sensitivity\",\"state\":\"aggressive\",\"attributes\":{}}" +
                "]");

        AnalysisResult result = ListenerAnalyzer.analyze(data);

        assertTrue(result.report.contains("Confirmed listeners: 1"));
        assertTrue(result.report.contains("Restore the unavailable device"));
        assertTrue(result.firmwareCandidate);
    }
}
