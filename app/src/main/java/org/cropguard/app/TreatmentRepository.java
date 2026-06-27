package org.cropguard.app;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

final class TreatmentRepository {
    static final class Treatment {
        final String summary;
        final List<String> steps;
        final String authority;

        Treatment(String summary, List<String> steps, String authority) {
            this.summary = summary;
            this.steps = steps;
            this.authority = authority;
        }
    }

    private final JSONObject root;

    TreatmentRepository(Context context) throws Exception {
        StringBuilder json = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                context.getAssets().open("treatments.json")))) {
            String line;
            while ((line = reader.readLine()) != null) json.append(line);
        }
        root = new JSONObject(json.toString());
    }

    Treatment get(String key, boolean uncertain) {
        String lookup = uncertain ? "uncertain" : key;
        JSONObject value = root.optJSONObject(lookup);
        if (value == null) value = root.optJSONObject("default");
        List<String> steps = new ArrayList<>();
        JSONArray array = value.optJSONArray("steps");
        for (int i = 0; array != null && i < array.length(); i++) steps.add(array.optString(i));
        return new Treatment(value.optString("summary"), steps, value.optString("authority"));
    }
}
