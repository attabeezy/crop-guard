package org.cropguard.app;

final class Diagnosis {
    final Label label;
    final float confidence;
    final boolean uncertain;
    final boolean demo;

    Diagnosis(Label label, float confidence, boolean uncertain, boolean demo) {
        this.label = label;
        this.confidence = confidence;
        this.uncertain = uncertain;
        this.demo = demo;
    }
}

