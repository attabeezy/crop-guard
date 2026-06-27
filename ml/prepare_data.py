"""Create leakage-resistant CCMT train/validation/test manifests.

Expected input: data/raw/<crop>/<class>/*.{jpg,jpeg,png}
Outputs CSV manifests to data/splits. Exact duplicate files are kept in one split.
"""
from __future__ import annotations

import argparse
import hashlib
from pathlib import Path

import pandas as pd
from sklearn.model_selection import GroupShuffleSplit

EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp"}


def digest(path: Path) -> str:
    hasher = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            hasher.update(chunk)
    return hasher.hexdigest()


def inventory(root: Path) -> pd.DataFrame:
    rows = []
    for crop_dir in sorted(p for p in root.iterdir() if p.is_dir()):
        for class_dir in sorted(p for p in crop_dir.iterdir() if p.is_dir()):
            for image in sorted(class_dir.rglob("*")):
                if image.suffix.lower() in EXTENSIONS:
                    rows.append({
                        "path": str(image.resolve()),
                        "crop": crop_dir.name.lower().strip().replace(" ", "_"),
                        "class": class_dir.name.lower().strip().replace(" ", "_"),
                        "sha256": digest(image),
                    })
    frame = pd.DataFrame(rows)
    if frame.empty:
        raise SystemExit(f"No images found under {root}/<crop>/<class>")
    frame["label"] = frame["crop"] + "|" + frame["class"]
    conflicts = frame.groupby("sha256")["label"].nunique()
    if (conflicts > 1).any():
        examples = conflicts[conflicts > 1].index[:5].tolist()
        raise ValueError(f"Identical files occur under different labels: {examples}")
    return frame


def split(frame: pd.DataFrame, seed: int) -> dict[str, pd.DataFrame]:
    # Stratification is approximated by splitting independently within each label.
    output = {"train": [], "validation": [], "test": []}
    for _, subset in frame.groupby("label"):
        groups = subset["sha256"]
        if groups.nunique() < 5:
            raise ValueError(f"Class {subset.iloc[0]['label']} needs at least 5 unique images")
        outer = GroupShuffleSplit(n_splits=1, test_size=.20, random_state=seed)
        train_val_idx, test_idx = next(outer.split(subset, groups=groups))
        train_val = subset.iloc[train_val_idx]
        inner = GroupShuffleSplit(n_splits=1, test_size=.125, random_state=seed + 1)
        train_idx, validation_idx = next(inner.split(train_val, groups=train_val["sha256"]))
        output["train"].append(train_val.iloc[train_idx])
        output["validation"].append(train_val.iloc[validation_idx])
        output["test"].append(subset.iloc[test_idx])
    return {name: pd.concat(parts).sample(frac=1, random_state=seed).reset_index(drop=True)
            for name, parts in output.items()}


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--input", type=Path, default=Path("data/raw"))
    parser.add_argument("--output", type=Path, default=Path("data/splits"))
    parser.add_argument("--seed", type=int, default=2026)
    args = parser.parse_args()
    parts = split(inventory(args.input), args.seed)
    args.output.mkdir(parents=True, exist_ok=True)
    for name, frame in parts.items():
        frame.to_csv(args.output / f"{name}.csv", index=False)
        print(f"{name}: {len(frame)} images")


if __name__ == "__main__":
    main()
