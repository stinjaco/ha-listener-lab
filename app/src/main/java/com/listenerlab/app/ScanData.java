package com.listenerlab.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.LinkedHashMap;
import java.util.Map;

final class ScanData {
    JSONObject config = new JSONObject();
    JSONArray states = new JSONArray();
    JSONArray devices = new JSONArray();
    JSONArray entities = new JSONArray();
    Object pipelines = null;
    final Map<String, JSONObject> satelliteConfigurations = new LinkedHashMap<>();
    final JSONArray warnings = new JSONArray();
}

