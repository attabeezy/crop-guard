# CropGuard 2.0

[![Open data setup in Colab](https://colab.research.google.com/assets/colab-badge.svg)](https://colab.research.google.com/github/attabeezy/crop-guard/blob/main/notebooks/01_data_setup.ipynb)

[![Open dataset preparation in Colab](https://colab.research.google.com/assets/colab-badge.svg)](https://colab.research.google.com/github/attabeezy/crop-guard/blob/main/notebooks/02_prepare_dataset.ipynb)

[![Open model training in Colab](https://colab.research.google.com/assets/colab-badge.svg)](https://colab.research.google.com/github/attabeezy/crop-guard/blob/main/notebooks/03_train_model.ipynb)

CropGuard is an offline-first Android screening tool for maize, cassava, cashew,
and tomato leaf conditions. The application is deliberately fail-safe: if its model
is missing or confidence is below 72%, it does not issue a disease diagnosis or
chemical recommendation.

## Current build status

- Native Java Android application: camera/gallery input, crop selection,
  on-device TFLite inference, confidence gate, and offline action-plan store.
- Leakage-resistant dataset manifest generation.
- EfficientNetV2 transfer-learning, fine-tuning, held-out evaluation, and true
  representative-dataset INT8 export.
- Safe demo build when no model is installed.

The CCMT images and trained model are not committed. The repository therefore
builds as a transparent demo until the pipeline creates `model.tflite`.
Grad-CAM is also not represented as complete: the current Android model contract
does not expose gradients or activation maps. See [docs/MODEL_CARD.md](docs/MODEL_CARD.md).

## Android

Prerequisites: Android Studio Ladybug or newer, JDK 17, Android SDK 35.

1. Open this directory in Android Studio.
2. For inference, copy `artifacts/model.tflite` and `artifacts/labels.txt` into
   `app/src/main/assets/`.
3. Run the `app` configuration on Android 8.0 or newer.

Debug builds allow demo mode. Release builds fail at startup if the model is
missing or incompatible. The output count must exactly match `labels.txt`.

## Model pipeline

Download the raw, non-augmented CCMT data separately and arrange it as:

```text
data/raw/
  maize/<class>/*.jpg
  cassava/<class>/*.jpg
  cashew/<class>/*.jpg
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

- Replace the placeholder class list with the exact inspected CCMT folders.
- Obtain and record CCMT license terms; the DOI alone is not a license.
- Add only authority-reviewed, dated treatment protocols. No pesticide dosage is
  currently shipped.
- Implement and validate an explanation-capable export before claiming Grad-CAM.
- Test phone photos outside the training distribution and calibrate the threshold.
