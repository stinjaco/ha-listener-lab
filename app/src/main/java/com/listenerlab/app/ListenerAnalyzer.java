package com.listenerlab.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class ListenerAnalyzer {
    private static final String[] VOICE_WORDS = {
            "assist", "voice", "satellite", "wake", "microphone", "listener",
            "atom echo", "s3-box", "s3 box", "respeaker", "voice pe", "okay nabu",
            "vad", "pipeline", "noise suppression", "volume multiplier", "mic gain"
    };

    private ListenerAnalyzer() {}

    static String createReport(ScanData data) {
        Map<String, JSONObject> states = indexBy(data.states, "entity_id");
        Map<String, JSONObject> devices = indexBy(data.devices, "id");
        Map<String, Candidate> candidates = new LinkedHashMap<>();
        List<String> relatedAudio = new ArrayList<>();

        for (int i = 0; i < data.devices.length(); i++) {
            JSONObject device = data.devices.optJSONObject(i);
            if (device == null) continue;
            String text = searchable(device);
            if (containsVoiceWord(text)) {
                String id = device.optString("id", "device-" + i);
                Candidate candidate = candidates.computeIfAbsent(id, key -> new Candidate(key));
                candidate.device = device;
                candidate.score += 20;
                candidate.evidence.add("Device name or model contains a voice-hardware clue");
            }
        }

        for (int i = 0; i < data.entities.length(); i++) {
            JSONObject entity = data.entities.optJSONObject(i);
            if (entity == null) continue;
            String entityId = entity.optString("entity_id");
            String deviceId = entity.optString("device_id");
            String domain = domain(entityId);
            String platform = entity.optString("platform").toLowerCase(Locale.US);
            String text = searchable(entity);
            boolean confirmed = "assist_satellite".equals(domain);
            boolean voiceClue = containsVoiceWord(text);
            if (confirmed || voiceClue) {
                String key = deviceId.isEmpty() ? entityId : deviceId;
                Candidate candidate = candidates.computeIfAbsent(key, Candidate::new);
                candidate.entities.add(entity);
                if (candidate.device == null) candidate.device = devices.get(deviceId);
                if (confirmed) {
                    candidate.score += 100;
                    candidate.confirmed = true;
                    candidate.satelliteEntityId = entityId;
                    candidate.evidence.add("Home Assistant exposes a standard assist_satellite entity");
                } else {
                    candidate.score += 6;
                }
                if ("esphome".equals(platform)) candidate.platform = "ESPHome";
                if ("wyoming".equals(platform)) candidate.platform = "Wyoming";
                if ("voip".equals(platform)) candidate.platform = "VoIP";
            }
        }

        for (Map.Entry<String, JSONObject> entry : states.entrySet()) {
            String entityId = entry.getKey();
            JSONObject state = entry.getValue();
            String text = (entityId + " " + state.optJSONObject("attributes")).toLowerCase(Locale.US);
            if (entityId.startsWith("assist_satellite.") && !containsSatellite(candidates, entityId)) {
                Candidate candidate = new Candidate(entityId);
                candidate.confirmed = true;
                candidate.score = 100;
                candidate.satelliteEntityId = entityId;
                candidate.evidence.add("Home Assistant state confirms an Assist satellite");
                candidates.put(entityId, candidate);
            }
            if (entityId.startsWith("media_player.") &&
                    (text.contains("cast") || text.contains("nest") || text.contains("speaker"))) {
                relatedAudio.add(friendlyEntityName(state, entityId));
            }
        }

        StringBuilder out = new StringBuilder();
        out.append("LISTENER LAB — READ-ONLY SYSTEM REPORT\n");
        out.append("Scanned: ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(new Date())).append("\n");
        out.append("Home Assistant: ").append(data.config.optString("version", "version not reported")).append("\n");
        out.append("Inventory: ").append(data.devices.length()).append(" devices, ")
                .append(data.entities.length()).append(" registered entities, ")
                .append(data.states.length()).append(" active states\n\n");

        List<Candidate> useful = new ArrayList<>();
        for (Candidate candidate : candidates.values()) {
            enrich(candidate, states, data.satelliteConfigurations);
            if (candidate.confirmed || candidate.score >= 15) useful.add(candidate);
        }
        int confirmedCount = 0;
        for (Candidate candidate : useful) if (candidate.confirmed) confirmedCount++;
        out.append("RESULT\n");
        out.append("Confirmed listeners: ").append(confirmedCount).append("\n");
        out.append("Possible voice devices: ").append(useful.size() - confirmedCount).append("\n\n");

        if (useful.isEmpty()) {
            out.append("No standardized listener was visible. This does not prove none exist. The setup may use an older integration, custom firmware, a separate Wyoming host, or a user account without registry access.\n\n");
        } else {
            int number = 1;
            for (Candidate candidate : useful) {
                out.append(number++).append(". ").append(candidate.name()).append("\n");
                out.append("   Classification: ").append(candidate.type()).append(" — ")
                        .append(candidate.confirmed ? "confirmed listener" : "possible voice hardware").append("\n");
                if (candidate.satelliteEntityId != null) {
                    JSONObject state = states.get(candidate.satelliteEntityId);
                    out.append("   Listener state: ").append(state == null ? "not reported" : state.optString("state", "unknown")).append("\n");
                }
                out.append("   Controls found: ").append(candidate.controls.isEmpty() ? "none exposed" : join(candidate.controls)).append("\n");
                if (candidate.unavailable > 0) out.append("   Warning: ").append(candidate.unavailable).append(" related entities are unavailable\n");
                out.append("   Next check: ").append(candidate.nextCheck()).append("\n\n");
                appendOptions(out, candidate);
            }
        }

        if (!relatedAudio.isEmpty()) {
            out.append("RELATED SPEAKERS\n");
            out.append(join(relatedAudio)).append("\n");
            out.append("These may only play responses; a Cast or Nest speaker is not automatically an Assist microphone.\n\n");
        }

        out.append("SAFE NEXT STEPS\n");
        if (confirmedCount > 0) {
            out.append("1. In Home Assistant, open Settings > Voice assistants > Debug. Speak at normal volume and inspect which stage fails.\n");
            out.append("2. Test the same wake phrase from roughly 3, 6, and 10 feet away, five times at each distance.\n");
            out.append("3. If wake detection fails, check placement, microphone mute, wake word, and VAD. If the transcript is quiet or wrong, inspect recorded input before increasing gain.\n");
            out.append("4. Change only one setting at a time and keep the original value for rollback.\n");
        } else {
            out.append("1. Confirm the scanning user is an administrator, then scan again for full registry access.\n");
            out.append("2. In Home Assistant, check Settings > Voice assistants and Settings > Devices & services for ESPHome or Wyoming.\n");
            out.append("3. Copy this report to the person helping; do not share the access token.\n");
        }

        if (data.warnings.length() > 0) {
            out.append("\nSCAN NOTES\n");
            for (int i = 0; i < data.warnings.length(); i++) out.append("• ").append(data.warnings.optString(i)).append("\n");
        }
        out.append("\nPrivacy: the access token is not included in this report and is not saved by Listener Lab.");
        return out.toString();
    }

    private static void appendOptions(StringBuilder out, Candidate candidate) {
        out.append("   Options after confirming this device:\n");
        out.append("   A. Diagnose only — run repeatable distance tests and compare Assist debug runs without changing anything.\n");
        if (!candidate.controls.isEmpty()) {
            out.append("   B. Home Assistant tuning — use the exposed ").append(join(candidate.controls))
                    .append(" controls, changing one value at a time with rollback notes.\n");
        }
        if (candidate.platform.contains("ESPHome")) {
            out.append("   C. ESPHome advanced — back up its YAML, expose gain/noise settings as number or select controls where supported, validate the configuration, and only then offer an explicit OTA or cable flash. Never flash automatically.\n");
        } else if (candidate.platform.contains("Wyoming") || candidate.platform.contains("ReSpeaker")) {
            out.append("   C. Wyoming advanced — inspect the satellite host's microphone level, ALSA/PipeWire device, wake-word service, logs, and network latency before changing models.\n");
        } else if (candidate.platform.contains("Voice Preview")) {
            out.append("   C. Voice hardware advanced — check the physical mute switch, placement, official firmware, captured input, echo cancellation, and supported audio controls before any firmware work.\n");
        } else {
            out.append("   C. Identify first — open the device and integration in Home Assistant. Listener Lab should not offer firmware or low-level audio changes until the platform is known.\n");
        }
        out.append("\n");
    }

    private static void enrich(Candidate candidate, Map<String, JSONObject> states,
                               Map<String, JSONObject> satelliteConfigurations) {
        if (candidate.device != null) {
            String identifiers = candidate.device.optString("identifiers").toLowerCase(Locale.US);
            String text = searchable(candidate.device);
            if (identifiers.contains("esphome") || text.contains("esphome")) candidate.platform = "ESPHome";
            if (identifiers.contains("wyoming") || text.contains("wyoming")) candidate.platform = "Wyoming";
            if (text.contains("voice preview") || text.contains("voice pe")) candidate.platform = "Home Assistant Voice Preview Edition";
            if (text.contains("atom echo")) candidate.platform = "M5Stack Atom Echo / ESPHome";
            if (text.contains("s3-box") || text.contains("s3 box")) candidate.platform = "ESP32-S3-BOX / ESPHome";
            if (text.contains("respeaker")) candidate.platform = "ReSpeaker-based satellite";
        }
        for (JSONObject entity : candidate.entities) {
            String id = entity.optString("entity_id");
            String text = searchable(entity) + " " + id.toLowerCase(Locale.US);
            if (text.contains("vad") || text.contains("voice activity")) addOnce(candidate.controls, "VAD sensitivity");
            if (text.contains("pipeline")) addOnce(candidate.controls, "Assist pipeline");
            if (text.contains("wake word")) addOnce(candidate.controls, "wake word");
            if (text.contains("gain") || text.contains("volume multiplier")) addOnce(candidate.controls, "microphone gain");
            if (text.contains("noise suppression")) addOnce(candidate.controls, "noise suppression");
            if ((text.contains("microphone") || text.contains("mic")) && text.contains("mute")) addOnce(candidate.controls, "microphone mute");
            JSONObject state = states.get(id);
            if (state != null && "unavailable".equals(state.optString("state"))) candidate.unavailable++;
        }
        JSONObject satelliteConfig = satelliteConfigurations.get(candidate.satelliteEntityId);
        if (satelliteConfig != null) {
            if (!satelliteConfig.optString("pipeline_entity_id").isEmpty()) addOnce(candidate.controls, "Assist pipeline");
            if (!satelliteConfig.optString("vad_entity_id").isEmpty()) addOnce(candidate.controls, "VAD sensitivity");
            JSONArray wakeWords = satelliteConfig.optJSONArray("available_wake_words");
            if (wakeWords != null && wakeWords.length() > 0) addOnce(candidate.controls, "wake word");
        }
    }

    private static Map<String, JSONObject> indexBy(JSONArray array, String key) {
        Map<String, JSONObject> result = new HashMap<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item != null && !item.optString(key).isEmpty()) result.put(item.optString(key), item);
        }
        return result;
    }

    private static boolean containsSatellite(Map<String, Candidate> candidates, String entityId) {
        for (Candidate candidate : candidates.values()) if (entityId.equals(candidate.satelliteEntityId)) return true;
        return false;
    }

    private static String searchable(JSONObject value) {
        return value == null ? "" : value.toString().toLowerCase(Locale.US);
    }

    private static boolean containsVoiceWord(String text) {
        for (String word : VOICE_WORDS) if (text.contains(word)) return true;
        return false;
    }

    private static String domain(String entityId) {
        int dot = entityId.indexOf('.');
        return dot < 0 ? "" : entityId.substring(0, dot);
    }

    private static String friendlyEntityName(JSONObject state, String fallback) {
        JSONObject attributes = state.optJSONObject("attributes");
        return attributes == null ? fallback : attributes.optString("friendly_name", fallback);
    }

    private static void addOnce(List<String> values, String value) {
        if (!values.contains(value)) values.add(value);
    }

    private static String join(List<String> values) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) out.append(", ");
            out.append(values.get(i));
        }
        return out.toString();
    }

    private static final class Candidate {
        final String key;
        JSONObject device;
        final List<JSONObject> entities = new ArrayList<>();
        final List<String> evidence = new ArrayList<>();
        final List<String> controls = new ArrayList<>();
        String platform = "Unknown voice hardware";
        String satelliteEntityId;
        int score;
        int unavailable;
        boolean confirmed;

        Candidate(String key) { this.key = key; }

        String name() {
            if (device != null) {
                String value = device.optString("name_by_user");
                if (value.isEmpty()) value = device.optString("name");
                if (!value.isEmpty()) return value;
            }
            if (satelliteEntityId != null) return satelliteEntityId;
            return key;
        }

        String type() { return platform; }

        String nextCheck() {
            if (unavailable > 0) return "Restore the unavailable device or Wi-Fi connection before tuning audio.";
            if (!confirmed) return "Open this device in Home Assistant and confirm whether it contains the microphone used for Assist.";
            if (controls.contains("VAD sensitivity")) return "Run normal-voice distance tests, then compare the exposed VAD setting without changing gain yet.";
            if (platform.contains("ESPHome")) return "Use Assist debug audio first; gain and noise controls may be compiled into ESPHome YAML rather than exposed here.";
            return "Use the Assist debug run to separate wake-word failure from speech-to-text failure.";
        }
    }
}
