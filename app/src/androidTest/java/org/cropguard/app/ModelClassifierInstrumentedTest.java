package org.cropguard.app;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public final class ModelClassifierInstrumentedTest {
    @Test
    public void releaseModelLoadsAndRunsInference() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        Bitmap image = Bitmap.createBitmap(224, 224, Bitmap.Config.ARGB_8888);
        image.eraseColor(Color.rgb(55, 135, 62));

        try (ModelClassifier classifier = new ModelClassifier(context)) {
            assertTrue(classifier.isModelAvailable());
            Diagnosis diagnosis = classifier.classify(image, "cashew");

            assertFalse(diagnosis.demo);
            assertNotNull(diagnosis.label);
            assertTrue(diagnosis.label.crop.equals("cashew"));
            assertTrue(Float.isFinite(diagnosis.confidence));
            assertTrue(diagnosis.confidence >= 0f);
            assertTrue(diagnosis.confidence <= 1f);
        } finally {
            image.recycle();
        }
    }
}
