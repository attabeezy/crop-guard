package org.cropguard.app;

import android.graphics.Bitmap;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.google.android.material.card.MaterialCardView;

import java.io.File;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends AppCompatActivity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private ModelClassifier classifier;
    private TreatmentRepository treatments;
    private Spinner cropSpinner;
    private ImageView preview;
    private TextView status;
    private LinearLayout resultContent;
    private Bitmap selectedImage;
    private Uri captureUri;

    private final ActivityResultLauncher<Uri> camera =
            registerForActivityResult(new ActivityResultContracts.TakePicture(), success -> {
                if (success) loadImage(captureUri);
            });
    private final ActivityResultLauncher<String> gallery =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) loadImage(uri);
            });

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            classifier = new ModelClassifier(this);
            treatments = new TreatmentRepository(this);
        } catch (Exception error) {
            Toast.makeText(this, "CropGuard could not start: " + error.getMessage(), Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        setContentView(buildScreen());
        if (!classifier.isModelAvailable()) {
            status.setText("DEMO BUILD · No model installed · Analysis returns “uncertain”");
        }
    }

    private View buildScreen() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = column(20);
        root.setPadding(dp(20), dp(24), dp(20), dp(32));
        root.setBackgroundColor(getColor(R.color.cream));
        scroll.addView(root);

        TextView title = text("CropGuard", 32, true);
        title.setTextColor(getColor(R.color.forest));
        root.addView(title);
        root.addView(text("Offline crop health guidance for Ghanaian farms", 16, false));

        status = text("ON-DEVICE · No internet required", 12, true);
        status.setTextColor(getColor(R.color.forest));
        status.setPadding(0, dp(12), 0, dp(16));
        root.addView(status);

        root.addView(text("1  Select crop", 18, true));
        cropSpinner = new Spinner(this);
        String[] crops = {"Maize", "Cassava", "Cashew", "Tomato"};
        cropSpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, crops));
        root.addView(cropSpinner, matchWrap());

        root.addView(spacer(16));
        root.addView(text("2  Add a clear leaf photo", 18, true));
        preview = new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        preview.setBackgroundColor(0xffdfe7dc);
        root.addView(preview, new LinearLayout.LayoutParams(-1, dp(260)));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button take = button("Take photo");
        Button choose = button("Choose photo");
        actions.addView(take, weighted());
        actions.addView(choose, weighted());
        root.addView(actions, matchWrap());
        take.setOnClickListener(v -> takePhoto());
        choose.setOnClickListener(v -> gallery.launch("image/*"));

        Button analyze = button("Analyze on this phone");
        analyze.setTextSize(17);
        analyze.setOnClickListener(v -> analyze());
        root.addView(analyze, matchWrap());

        resultContent = column(10);
        resultContent.setVisibility(View.GONE);
        MaterialCardView card = new MaterialCardView(this);
        card.setCardBackgroundColor(0xffffffff);
        card.setRadius(dp(18));
        card.setContentPadding(dp(18), dp(18), dp(18), dp(18));
        card.addView(resultContent);
        LinearLayout.LayoutParams cardParams = matchWrap();
        cardParams.topMargin = dp(18);
        root.addView(card, cardParams);

        TextView disclaimer = text("CropGuard is a screening aid, not a laboratory diagnosis. "
                + "Confirm serious symptoms and all chemical controls with an authorized extension officer.", 12, false);
        disclaimer.setPadding(0, dp(20), 0, 0);
        root.addView(disclaimer);
        return scroll;
    }

    private void takePhoto() {
        try {
            File dir = new File(getCacheDir(), "captures");
            if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("Cannot create capture folder");
            File file = new File(dir, "leaf-" + System.currentTimeMillis() + ".jpg");
            captureUri = FileProvider.getUriForFile(this, getPackageName() + ".files", file);
            camera.launch(captureUri);
        } catch (Exception error) {
            Toast.makeText(this, "Camera unavailable: " + error.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void loadImage(Uri uri) {
        try {
            ImageDecoder.Source source = ImageDecoder.createSource(getContentResolver(), uri);
            selectedImage = ImageDecoder.decodeBitmap(source, (decoder, info, src) -> {
                decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                int max = Math.max(info.getSize().getWidth(), info.getSize().getHeight());
                if (max > 1600) decoder.setTargetSampleSize((int) Math.ceil(max / 1600.0));
            });
            preview.setImageBitmap(selectedImage);
            resultContent.setVisibility(View.GONE);
        } catch (Exception error) {
            Toast.makeText(this, "Could not read that image.", Toast.LENGTH_LONG).show();
        }
    }

    private void analyze() {
        if (selectedImage == null) {
            Toast.makeText(this, "Take or choose a leaf photo first.", Toast.LENGTH_SHORT).show();
            return;
        }
        status.setText("Analyzing locally…");
        String crop = cropSpinner.getSelectedItem().toString().toLowerCase(Locale.ROOT);
        executor.execute(() -> {
            try {
                Diagnosis diagnosis = classifier.classify(selectedImage, crop);
                runOnUiThread(() -> showDiagnosis(diagnosis));
            } catch (Exception error) {
                runOnUiThread(() -> {
                    status.setText("Analysis failed");
                    Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void showDiagnosis(Diagnosis diagnosis) {
        resultContent.removeAllViews();
        status.setText(classifier.isModelAvailable() ? "ANALYZED ON DEVICE" : "DEMO BUILD · MODEL NOT INSTALLED");
        String heading = diagnosis.uncertain ? "Not confident enough" : diagnosis.label.displayName;
        resultContent.addView(text(heading, 24, true));
        String confidence = diagnosis.demo ? "No diagnosis generated"
                : String.format(Locale.getDefault(), "%.0f%% confidence", diagnosis.confidence * 100);
        resultContent.addView(text(confidence, 16, true));
        if (diagnosis.uncertain) {
            resultContent.addView(text("CropGuard will not force a disease label when confidence is below "
                    + Math.round(ModelClassifier.CONFIDENCE_THRESHOLD * 100) + "%.", 14, false));
        } else {
            resultContent.addView(text("Visual explanation: unavailable in this model contract. "
                    + "Do not treat this result as explainable until an explanation-capable model is exported.", 13, false));
        }
        TreatmentRepository.Treatment treatment = treatments.get(diagnosis.label.key, diagnosis.uncertain);
        resultContent.addView(text("Action plan", 18, true));
        resultContent.addView(text(treatment.summary, 14, false));
        for (String step : treatment.steps) resultContent.addView(text("•  " + step, 14, false));
        TextView source = text("Source note: " + treatment.authority, 12, false);
        source.setTextColor(0xff4b6352);
        resultContent.addView(source);
        resultContent.setVisibility(View.VISIBLE);
    }

    private LinearLayout column(int gap) {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        layout.setShowDividers(LinearLayout.SHOW_DIVIDER_MIDDLE);
        layout.setDividerDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        layout.setDividerPadding(dp(gap));
        return layout;
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(getColor(R.color.ink));
        if (bold) view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        return view;
    }

    private Button button(String value) {
        Button button = new Button(this);
        button.setText(value);
        button.setAllCaps(false);
        return button;
    }

    private View spacer(int size) {
        View view = new View(this);
        view.setLayoutParams(new LinearLayout.LayoutParams(1, dp(size)));
        return view;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(-1, -2);
    }

    private LinearLayout.LayoutParams weighted() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
        params.setMargins(dp(2), dp(4), dp(2), dp(4));
        return params;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override protected void onDestroy() {
        executor.shutdownNow();
        if (classifier != null) classifier.close();
        super.onDestroy();
    }
}
