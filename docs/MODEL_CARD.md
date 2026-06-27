# CropGuard model card

## Intended use

Screening photographs of maize, cassava, cashew, and tomato leaves captured in Ghana.
The model is not a laboratory test, does not replace an extension officer, and
must not be used as the sole basis for pesticide application.

## Data

Planned primary source: CCMT Crop Pest and Disease Detection, DOI
`10.17632/bwh3zbpkpv`, collected in Ghana. Before release, record the exact
download version, files, ownership, license, restrictions, class counts,
deduplication results, exclusions, and split manifest hashes.

The split script groups byte-identical images to prevent exact duplicates from
crossing train, validation, and test. This does not detect near-duplicates;
perceptual-hash and manual burst inspection remain release gates.

## Evaluation protocol

The training script reserves a held-out test partition, emits per-class
precision/recall/F1 and a confusion matrix, and uses training images only as the
INT8 representative set. Report macro F1, per-class recall (especially healthy),
calibration error, abstention coverage/accuracy, model size, and device latency.
Add a separately sourced phone-photo/OOD set without tuning on it.

## Explainability

The plan calls for Grad-CAM. The included classifier currently exports only final
probabilities, so the Android app correctly says that visual explanation is
unavailable. A release may claim Grad-CAM only after exporting a validated
explanation-capable model, confirming that heatmaps correspond to the predicted
class, and reviewing representative correct and incorrect examples with a plant
pathology expert.

## Known limitations

No trained weights, verified class inventory, calibration study, field/OOD test,
near-duplicate audit, or agronomist-reviewed treatment dataset is included in
this source repository yet. Debug demo mode always abstains.
