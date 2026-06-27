# CropGuard 2.0

[![Open data setup in Colab](https://colab.research.google.com/assets/colab-badge.svg)](https://colab.research.google.com/github/attabeezy/crop-guard/blob/main/notebooks/01_data_setup.ipynb)

[![Open dataset preparation in Colab](https://colab.research.google.com/assets/colab-badge.svg)](https://colab.research.google.com/github/attabeezy/crop-guard/blob/main/notebooks/02_prepare_dataset.ipynb)

[![Open model training in Colab](https://colab.research.google.com/assets/colab-badge.svg)](https://colab.research.google.com/github/attabeezy/crop-guard/blob/main/notebooks/03_train_model.ipynb)

[![Open model evaluation in Colab](https://colab.research.google.com/assets/colab-badge.svg)](https://colab.research.google.com/github/attabeezy/crop-guard/blob/main/notebooks/04_evaluate_model.ipynb)

[![Open INT8 export in Colab](https://colab.research.google.com/assets/colab-badge.svg)](https://colab.research.google.com/github/attabeezy/crop-guard/blob/main/notebooks/05_export_int8.ipynb)

[![Open float32 export in Colab](https://colab.research.google.com/assets/colab-badge.svg)](https://colab.research.google.com/github/attabeezy/crop-guard/blob/main/notebooks/06_export_float32.ipynb)

CropGuard is an offline-first Android screening tool for maize, cassava, cashew,
and tomato leaf conditions. The application is deliberately fail-safe: if its model
is missing or confidence is below 53%, it does not issue a disease diagnosis or
chemical recommendation.

## Current build status

- Native Java Android application: camera/gallery input, crop selection,
  runtime camera permission handling, on-device TFLite inference, confidence
  gate, and offline action-plan store.
- Leakage-resistant dataset manifest generation.
- EfficientNetV2 transfer learning, fine-tuning, calibration, and held-out evaluation.
- Release-eligible float32 TFLite model with zero conversion accuracy loss.
- Failed INT8 parity results preserved rather than shipped.
- Reproducible Gradle 8.9 wrapper with passing debug/release compilation and
  Android lint.

The CCMT images are not committed. Trained models are stored with Git LFS, and
the Android application includes the validated float32 TFLite artifact.
Grad-CAM is also not represented as complete: the current Android model contract
does not expose gradients or activation maps. See [docs/MODEL_CARD.md](docs/MODEL_CARD.md).

## Android

Prerequisites: Android Studio Ladybug or newer, JDK 17, Android SDK 35.

1. Ensure Git LFS has downloaded `app/src/main/assets/model.tflite`.
2. Build from Android Studio, or run:

   ```powershell
   .\gradlew.bat lintDebug assembleDebug assembleRelease
   ```

3. Run the `app` configuration on Android 8.0 or newer.

The debug build, cold startup, runtime camera permission prompt, and on-device
model inference were verified on a Samsung SM-A047F running Android 14 on
27 June 2026. Camera capture, gallery selection, all four crop flows, and the
low-confidence fallback also passed a manual device check. The generated
release APK is unsigned; configure a protected release signing key before
distribution.

Debug builds allow demo mode. Release builds fail at startup if the model is
missing or incompatible. The output count must exactly match `labels.txt`.

Held-out float32 TFLite results: 84.97% accuracy, 84.49% macro-F1, 90.06%
coverage, and 89.48% accepted-case accuracy. The conversion had 100% prediction
agreement with the selected Keras checkpoint across 4,811 test images.

## Model pipeline

Download the raw, non-augmented CCMT data separately and arrange it as:

```text
data/raw/
  maize/<class>/*.jpg
  cassava/<class>/*.jpg
  cashew/<class>/*.jpg
  tomato/<class>/*.jpg
```

Then run in Colab or a Python 3.11 environment:

```bash
pip install -r ml/requirements.txt
python ml/prepare_data.py
python ml/train.py
```

Do not publish a competition metric until `metrics.json`, the confusion matrix,
class mapping, field/OOD results, and dataset provenance have been reviewed.

## Safety and release gates

- Reconcile the Kaggle mirror counts with the authoritative Mendeley archive.
- Add only authority-reviewed, dated treatment protocols. No pesticide dosage is
  currently shipped.
- Implement and validate an explanation-capable export before claiming Grad-CAM.
- Test phone photos outside the training distribution and calibrate the threshold.
- Configure release signing and perform a final signed-APK installation check.
