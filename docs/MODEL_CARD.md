# CropGuard model card

## Intended use

Screening photographs of maize, cassava, cashew, and tomato leaves captured in Ghana.
The model is not a laboratory test, does not replace an extension officer, and
must not be used as the sole basis for pesticide application.

## Data

Primary source: CCMT Crop Pest and Disease Detection, DOI
`10.17632/bwh3zbpkpv.1`, collected in Ghana and licensed CC BY 4.0. Training
used version 1 of the Kaggle mirror `nirmalsankalana/crop-pest-and-disease-detection`.
The mirror's counts differ from the published Mendeley totals; this discrepancy
is preserved in the audit rather than silently ignored.

The audit removed corrupt images and exact duplicates, excluded exact files
assigned conflicting labels, and grouped likely perceptual near-duplicates so
their groups cannot cross train, validation, and test.

## Evaluation protocol

The selected fine-tuned EfficientNetV2B0 achieved 84.97% accuracy and 84.49%
macro-F1 on 4,811 held-out images. At the validation-locked 0.53 confidence
threshold, test coverage was 90.06% and accepted-case accuracy was 89.48%.
Float32 TFLite conversion produced identical predicted classes on all test
images. Full INT8 conversion was rejected after accuracy fell to 83.43% and
prediction agreement fell to 93.49%.

## Explainability

The plan calls for Grad-CAM. The included classifier currently exports only final
probabilities, so the Android app correctly says that visual explanation is
unavailable. A release may claim Grad-CAM only after exporting a validated
explanation-capable model, confirming that heatmaps correspond to the predicted
class, and reviewing representative correct and incorrect examples with a plant
pathology expert.

## Known limitations

Tomato disease separation is the weakest area, with disease-class F1 scores from
56.1% to 68.7%. Maize healthy precision is 60.3% on only 41 test examples.
No separately collected phone/OOD evaluation or agronomist-reviewed treatment
dataset is included yet. Grad-CAM review was mixed and does not yet justify a
lesion-level explainability claim.
