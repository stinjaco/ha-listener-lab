package com.listenerlab.app;

final class AnalysisResult {
    final String report;
    final boolean firmwareCandidate;
    final int confirmedListeners;
    final int possibleListeners;

    AnalysisResult(String report, boolean firmwareCandidate, int confirmedListeners, int possibleListeners) {
        this.report = report;
        this.firmwareCandidate = firmwareCandidate;
        this.confirmedListeners = confirmedListeners;
        this.possibleListeners = possibleListeners;
    }
}

