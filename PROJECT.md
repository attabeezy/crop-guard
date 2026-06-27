# CropGuard 2.0 — Project Plan

**A Ghanaian crop disease diagnostic tool for the Ghana AI Innovation Challenge 2026**

---

## 1. What we are building

An **offline-first Android application** that lets a farmer photograph a crop leaf and instantly get:

1. **A diagnosis** — which disease (or "healthy") the plant has, from an on-device AI model.
2. **A confidence signal** — including an honest "I'm not sure, retake the photo / consult an extension officer" path when the model is uncertain.
3. **A visual explanation** — a heatmap (Grad-CAM) showing *where* on the leaf the model detected the problem, so the farmer can trust it isn't guessing.
4. **An action plan** — the specific, officially-sourced treatment (chemical, dosage, schedule) for that disease, drawn from Ghana's national agricultural authorities.

Everything runs **on the phone with no internet**, because the target users are farmers in rural areas with poor connectivity.

This is a from-scratch rebuild. An earlier prototype (`CropGuard-Android`) exists and informs our direction, but we are redoing the data, model, app, and documentation properly. See Section 9 for what changed and why.

---

## 2. Why this project (the competition context)

The **Ghana AI Innovation Challenge 2026** invites AI solutions to local problems, judged on five weighted criteria:

| Criterion | Weight |
|---|---|
| Relevance & impact | 30% |
| Technical soundness | 25% |
| Innovation | 20% |
| Scalability & feasibility | 15% |
| Presentation quality | 10% |

**Hard rule:** every solution must use **at least one Ghanaian dataset as a primary data source**, with all data sources clearly documented (source, ownership, licensing, restrictions).

**Key dates:**
- Application / submission deadline: **1 July 2026**
- Shortlist notification: 15 July 2026
- Summit showcase (poster + live pitch): 29–30 July 2026

**Our strategy:** maximise the two heaviest criteria (relevance/impact + technical soundness = 55%) by grounding the *entire* project in real Ghanaian data and real Ghanaian institutions, and by being rigorous and honest about model evaluation. Innovation points come from explainability (Grad-CAM) and the "knows when it's unsure" design. Impact points come from the treatment-protocol layer that tells farmers what to actually do.

---

## 3. Scope (locked)

**Crops covered: Maize, Cassava, Cashew, Tomato** — all sourced from the CCMT Ghana dataset.
- **Cocoa was considered and dropped.** CCMT has no cocoa data, and a non-Ghanaian cocoa dataset would introduce documentation and generalization caveats we don't need. Dropping it means *every* crop we ship is real Ghanaian field data with a citable source and a real Ghanaian authority behind its treatment advice — a clean story with no asterisks.

**Why these four are coherent:** they split cleanly across Ghana's two relevant authorities, so our treatment advice can be institutionally grounded for every crop:

| Crop | Data source | Treatment authority |
|---|---|---|
| Maize | CCMT (Ghana) | MOFA – PPRSD |
| Cassava | CCMT (Ghana) | MOFA – PPRSD |
| Cashew | CCMT (Ghana) | COCOBOD / CRIG (cashew is an official COCOBOD mandate crop) |
| Tomato | CCMT (Ghana) | MOFA – PPRSD |

There's also a timely economic hook: with cocoa prices falling, Ghana is actively pushing crop diversification into **cashew**, so a tool that protects cashew farmers rides a real national policy priority.

---

## 4. The dataset: CCMT

**CCMT** = "Crop Pest and Disease Detection" dataset, sourced from local farms in Ghana (collected at the University of Energy and Natural Resources, Sunyani), published with a DOI and openly available on Kaggle / Mendeley Data.

- Raw set: ~24,881 original images across cashew, cassava, maize, tomato (22 classes total).
- Images annotated by plant virology/pathology experts.
- Maize alone has 7 classes (e.g. fall armyworm, leaf blight, leaf spot, streak virus, healthy, etc.).

**Decision: we use the RAW set, not the pre-augmented ~102k version.** Reason: the pre-augmented set contains rotated/flipped copies of the same leaf. If those copies land in both the training and test sets, the model "recognises" test images it effectively already saw, inflating accuracy dishonestly. We split first, then augment only the training portion ourselves. This keeps the test set genuinely unseen.

**Citation / documentation (required by competition rule 5.3):** we will record the dataset DOI, ownership, and license in our submission. (DOI: 10.17632/bwh3zbpkpv)

---

## 5. How we guarantee the model actually works (not just looks good)

This is the make-or-break section. Crop-disease models commonly cheat by learning the *background* or *lighting* of each class instead of the disease, scoring high on their own validation set and then failing on real farm photos. Our defenses:

1. **Honest split with a held-out test set.** Split into train / validation / test so the test set is never seen during training *or* tuning. The final test number is the only one we trust and report.
2. **Realistic augmentation** (crops, brightness/contrast, rotations) applied to training data only, forcing the model to rely on lesion patterns rather than incidental cues.
3. **Grad-CAM honesty check.** We generate heatmaps to literally see where the model looks. Lesions = good. Backgrounds/corners = the model is cheating, and we retrain. This also becomes a trust feature in the app and a strong answer in the live pitch.
4. **Per-class metrics + confusion matrix**, not just overall accuracy. Overall accuracy hides disasters (e.g. a model that never correctly identifies "healthy"). We track precision/recall per class, with **healthy-class performance treated as a first-class metric**.
5. **Out-of-distribution test** — evaluate on images the model never saw (ideally a few of our own field/phone photos) to confirm generalization holds.
6. **Confidence threshold + "uncertain" path** so a low-confidence or out-of-domain image (e.g. a photo of a hand) isn't force-labeled as a disease.

---

## 6. Technical approach

- **Model:** transfer learning on an EfficientNet-class backbone, with fine-tuning of upper layers (not just a frozen backbone).
- **Output:** quantized **INT8 TensorFlow Lite** model for fast, small on-device inference. (Note: we will do quantization *properly* with a representative dataset — the old prototype claimed INT8 but actually shipped a 16MB float model. We won't repeat that.)
- **Labels:** exported as a `labels.txt` asset and read by the app, so class order is never hardcoded/desynced between training and app.
- **App:** native Android (Java), offline TFLite inference, multi-crop selection, confidence gating, Grad-CAM overlay, treatment-protocol layer.
- **Tooling:** training in **Google Colab** (free GPU); app built in **Android Studio**, tested on a physical Android phone over USB (avoids the heavy emulator).

**Current implementation note (27 June 2026):** the Android app has verified
camera/gallery input, runtime camera permission handling, crop selection,
offline float32 TFLite inference, confidence gating, and safe generic action
guidance. Debug/release compilation, lint, cold startup, and an instrumented
model inference test pass on a Samsung SM-A047F. Grad-CAM, authority-reviewed
disease-specific protocols, release signing, and field/OOD validation remain
open and must not be presented as shipped features.

---

## 7. Phase plan

| Phase | What | Where | Status |
|---|---|---|---|
| 0 | Setup & accounts (Colab, Kaggle token, GitHub) | local | ✅ essentially done |
| 1 | Data: download CCMT, inspect, clean split, augmentation setup | Colab | ✅ complete |
| 2 | Model: transfer learning + fine-tuning, deployment export, labels | Colab | ✅ complete (float32 shipped; INT8 rejected) |
| 3 | Evaluation: per-class metrics, confusion matrix, OOD test, Grad-CAM | Colab | ▶️ core test complete; field/OOD pending |
| 4 | Android app: build from scratch, integrate model, confidence gate, Grad-CAM, treatment layer | Android Studio | ▶️ core app and device checks complete; signing, explanations, and reviewed protocols pending |
| 5 | Docs & submission: README, technical report, 1–2 page challenge document | — | ⏳ |

We work **phase by phase** and only move forward when the current phase is solid.

---

## 8. Deliverables (what the competition needs)

- A **1–2 page submission document**: problem statement & objectives, dataset(s) and sources, methodology & AI/ML techniques, preliminary results & key findings, potential impact, team composition.
- A **code repository** with documentation.
- For the showcase (if shortlisted): an **A1 poster** and a **live pitch**.

---

## 9. What changed from the original prototype (and why)

The earlier `CropGuard-Android` prototype was the starting inspiration. Key issues we are fixing:

- **Dataset was not Ghanaian** (it used generic Kaggle cocoa/maize sets). → Now CCMT, real Ghana data. *This is the single most important fix for the competition.*
- **"INT8 / <4MB" claim was false** — the shipped model was actually 16MB float32. → Real quantization this time.
- **Class order was hardcoded in two places** and would silently mislabel if crops changed. → Exported `labels.txt`.
- **An FPGA/CPU "hardware accelerator" narrative** dominated the docs and the app showed a fabricated "12ms" speedup number. → Removed entirely; off-theme for this challenge and read as fake data.
- **No real evaluation** (no per-class metrics, no confusion matrix, no generalization testing). → Full evaluation phase added.
- **A pest/treatment mismatch** (pod borer vs. capsids). → Treatment layer rebuilt with each recommendation sourced to the correct authority; no invented dosages.

What we **keep** from the original: the offline-first edge approach, the COCOBOD/MOFA treatment-grounding idea (expanded and corrected), and the clean UX flow (select crop → capture → analyze → actionable result).

---

## 10. Team & ways of working

- **Team size:** up to 5 allowed by the competition; multidisciplinary mix (technical + domain knowledge) is encouraged and scored.
- **Eligibility:** participants must be Ghanaian citizens/residents or demonstrate clear collaboration with Ghana-based institutions/stakeholders.
- **Suggested split of work** (to refine together): data & model (Colab/Python), Android app (Java), evaluation & visualizations, agronomy/treatment-protocol research & sourcing, documentation & pitch.
- **Repo discipline:** never commit secrets (e.g. `kaggle.json`); document every data source per competition rule 5.3.

---

## 11. Open items / decisions still to make

- Confirm final team roster and role assignments.
- Decide whether to collect a small set of our own field photos (30–50) for an out-of-distribution / Ghanaian-validation test — high value if feasible before the deadline.
- Confirm exact disease classes per crop once we inspect the CCMT folders.
- Confirm the Kaggle mirror's tomato count discrepancy against the authoritative Mendeley archive.
- Configure protected release signing and verify the signed APK on a physical device.

---

*This document reflects the plan as of the current working session and will evolve as phases complete.*
