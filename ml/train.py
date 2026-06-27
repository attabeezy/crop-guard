"""Train, evaluate, and export a fully-quantized CropGuard classifier."""
from __future__ import annotations

import argparse
import json
from pathlib import Path

import numpy as np
import pandas as pd
import tensorflow as tf
from sklearn.metrics import classification_report, confusion_matrix

SIZE = 224
BATCH = 32


def load_frame(path: Path, classes: list[str], training: bool) -> tf.data.Dataset:
    frame = pd.read_csv(path)
    table = tf.lookup.StaticHashTable(
        tf.lookup.KeyValueTensorInitializer(classes, range(len(classes))),
        default_value=-1,
    )
    paths = frame["path"].astype(str).values
    labels = (frame["crop"] + "|" + frame["class"]).values
    dataset = tf.data.Dataset.from_tensor_slices((paths, labels))

    def decode(path: tf.Tensor, label: tf.Tensor):
        image = tf.io.decode_image(tf.io.read_file(path), channels=3, expand_animations=False)
        image.set_shape([None, None, 3])
        image = tf.image.resize(image, [SIZE, SIZE])
        return image, table.lookup(label)

    dataset = dataset.map(decode, num_parallel_calls=tf.data.AUTOTUNE)
    if training:
        dataset = dataset.shuffle(min(len(frame), 4096), seed=2026)
    return dataset.batch(BATCH).prefetch(tf.data.AUTOTUNE)


def make_model(class_count: int) -> tf.keras.Model:
    augmentation = tf.keras.Sequential([
        tf.keras.layers.RandomFlip("horizontal"),
        tf.keras.layers.RandomRotation(.08),
        tf.keras.layers.RandomZoom(.12),
        tf.keras.layers.RandomContrast(.15),
    ])
    base = tf.keras.applications.EfficientNetV2B0(
        include_top=False, weights="imagenet", input_shape=(SIZE, SIZE, 3))
    base.trainable = False
    inputs = tf.keras.Input((SIZE, SIZE, 3), name="image")
    x = augmentation(inputs)
    x = tf.keras.applications.efficientnet_v2.preprocess_input(x)
    x = base(x, training=False)
    x = tf.keras.layers.GlobalAveragePooling2D()(x)
    x = tf.keras.layers.Dropout(.25)(x)
    outputs = tf.keras.layers.Dense(class_count, activation="softmax", name="probabilities")(x)
    return tf.keras.Model(inputs, outputs)


def representative(dataset: tf.data.Dataset):
    for images, _ in dataset.unbatch().batch(1).take(200):
        yield [tf.cast(images, tf.float32)]


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--splits", type=Path, default=Path("data/splits"))
    parser.add_argument("--output", type=Path, default=Path("artifacts"))
    parser.add_argument("--epochs", type=int, default=15)
    parser.add_argument("--fine-tune-epochs", type=int, default=8)
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)

    train_frame = pd.read_csv(args.splits / "train.csv")
    classes = sorted((train_frame["crop"] + "|" + train_frame["class"]).unique())
    train = load_frame(args.splits / "train.csv", classes, True)
    validation = load_frame(args.splits / "validation.csv", classes, False)
    test = load_frame(args.splits / "test.csv", classes, False)

    model = make_model(len(classes))
    model.compile("adam", "sparse_categorical_crossentropy", metrics=["accuracy"])
    callbacks = [
        tf.keras.callbacks.EarlyStopping(patience=4, restore_best_weights=True),
        tf.keras.callbacks.ModelCheckpoint(args.output / "best.keras", save_best_only=True),
    ]
    model.fit(train, validation_data=validation, epochs=args.epochs, callbacks=callbacks)

    backbone = next(layer for layer in model.layers if layer.name.startswith("efficientnet"))
    backbone.trainable = True
    for layer in backbone.layers[:-35]:
        layer.trainable = False
    model.compile(tf.keras.optimizers.Adam(1e-5), "sparse_categorical_crossentropy",
                  metrics=["accuracy"])
    model.fit(train, validation_data=validation, epochs=args.fine_tune_epochs,
              callbacks=callbacks)

    truth, predicted = [], []
    for images, labels in test:
        truth.extend(labels.numpy().tolist())
        predicted.extend(np.argmax(model.predict(images, verbose=0), axis=1).tolist())
    report = classification_report(truth, predicted, target_names=classes, output_dict=True)
    (args.output / "metrics.json").write_text(json.dumps(report, indent=2))
    np.savetxt(args.output / "confusion_matrix.csv",
               confusion_matrix(truth, predicted), delimiter=",", fmt="%d")

    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    converter.representative_dataset = lambda: representative(train)
    converter.target_spec.supported_ops = [tf.lite.OpsSet.TFLITE_BUILTINS_INT8]
    converter.inference_input_type = tf.uint8
    converter.inference_output_type = tf.uint8
    (args.output / "model.tflite").write_bytes(converter.convert())
    with (args.output / "labels.txt").open("w", encoding="utf-8") as stream:
        for label in classes:
            crop, key = label.split("|", 1)
            stream.write(f"{crop}|{key}|{key.replace('_', ' ').title()}\n")


if __name__ == "__main__":
    main()
