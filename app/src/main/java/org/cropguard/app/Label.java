package org.cropguard.app;

final class Label {
    final String crop;
    final String key;
    final String displayName;

    Label(String crop, String key, String displayName) {
        this.crop = crop;
        this.key = key;
        this.displayName = displayName;
    }
}

