package org.cropguard.app;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;

import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.Tensor;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;

final class ModelClassifier implements Closeable {
    static final float CONFIDENCE_THRESHOLD = 0.53f;
    private static final float CONFIDENCE_TEMPERATURE = 1.0042804f;

    private final List<Label> labels;
    private Interpreter interpreter;
    private int width = 224;
    private int height = 224;
    private DataType inputType = DataType.UINT8;

    ModelClassifier(Context context) throws Exception {
        labels = loadLabels(context);
        try {
            interpreter = new Interpreter(loadModel(context),
                    new Interpreter.Options().setNumThreads(Math.max(2, Runtime.getRuntime().availableProcessors() / 2)));
            Tensor input = interpreter.getInputTensor(0);
            int[] shape = input.shape();
            if (shape.length != 4 || shape[0] != 1 || shape[3] != 3) {
                throw new IllegalStateException("Expected model input [1,height,width,3]");
            }
            height = shape[1];
            width = shape[2];
            inputType = input.dataType();
            int outputClasses = interpreter.getOutputTensor(0).shape()[1];
            if (outputClasses != labels.size()) {
                throw new IllegalStateException("Model has " + outputClasses
                        + " outputs but labels.txt has " + labels.size() + " labels");
            }
        } catch (Exception missingModel) {
            interpreter = null;
            if (!BuildConfig.ALLOW_DEMO_MODE) throw missingModel;
        }
    }

    boolean isModelAvailable() {
        return interpreter != null;
    }

    Diagnosis classify(Bitmap source, String selectedCrop) {
        if (interpreter == null) {
            Label placeholder = firstForCrop(selectedCrop);
            return new Diagnosis(placeholder, 0f, true, true);
        }
        float[] probabilities = calibrate(run(source));
        int best = -1;
        float confidence = -1f;
        for (int i = 0; i < probabilities.length; i++) {
            if (labels.get(i).crop.equals(selectedCrop) && probabilities[i] > confidence) {
                best = i;
                confidence = probabilities[i];
            }
        }
        if (best < 0) throw new IllegalStateException("No labels found for crop: " + selectedCrop);
        Label label = labels.get(best);
        return new Diagnosis(label, confidence, confidence < CONFIDENCE_THRESHOLD, false);
    }

    private float[] run(Bitmap source) {
        Bitmap bitmap = Bitmap.createScaledBitmap(source, width, height, true);
        ByteBuffer input = ByteBuffer.allocateDirect(width * height * 3 * inputType.byteSize())
                .order(ByteOrder.nativeOrder());
        int[] pixels = new int[width * height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        Tensor tensor = interpreter.getInputTensor(0);
        float scale = tensor.quantizationParams().getScale();
        int zero = tensor.quantizationParams().getZeroPoint();
        for (int pixel : pixels) {
            float[] channels = {
                    (pixel >> 16) & 0xff,
                    (pixel >> 8) & 0xff,
                    pixel & 0xff
            };
            for (float channel : channels) {
                if (inputType == DataType.FLOAT32) input.putFloat(channel);
                else {
                    int quantized = Math.round(channel / scale + zero);
                    if (inputType == DataType.INT8) {
                        quantized = Math.max(-128, Math.min(127, quantized));
                    } else {
                        quantized = Math.max(0, Math.min(255, quantized));
                    }
                    input.put((byte) quantized);
                }
            }
        }
        Tensor outputTensor = interpreter.getOutputTensor(0);
        int count = labels.size();
        float[] probabilities = new float[count];
        if (outputTensor.dataType() == DataType.FLOAT32) {
            float[][] output = new float[1][count];
            interpreter.run(input, output);
            return output[0];
        }
        ByteBuffer output = ByteBuffer.allocateDirect(count).order(ByteOrder.nativeOrder());
        interpreter.run(input, output);
        output.rewind();
        float outputScale = outputTensor.quantizationParams().getScale();
        int outputZero = outputTensor.quantizationParams().getZeroPoint();
        for (int i = 0; i < count; i++) {
            int raw = outputTensor.dataType() == DataType.INT8 ? output.get() : output.get() & 0xff;
            probabilities[i] = (raw - outputZero) * outputScale;
        }
        return probabilities;
    }

    private static float[] calibrate(float[] probabilities) {
        float sum = 0f;
        for (int i = 0; i < probabilities.length; i++) {
            float clipped = Math.max(1e-8f, Math.min(1f, probabilities[i]));
            probabilities[i] = (float) Math.exp(Math.log(clipped) / CONFIDENCE_TEMPERATURE);
            sum += probabilities[i];
        }
        if (sum <= 0f || !Float.isFinite(sum)) {
            throw new IllegalStateException("Model returned invalid probabilities");
        }
        for (int i = 0; i < probabilities.length; i++) probabilities[i] /= sum;
        return probabilities;
    }

    private Label firstForCrop(String crop) {
        for (Label label : labels) if (label.crop.equals(crop)) return label;
        return new Label(crop, "unknown", "Unknown");
    }

    private static List<Label> loadLabels(Context context) throws Exception {
        List<Label> result = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                context.getAssets().open("labels.txt")))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split("\\|", 3);
                if (parts.length != 3) throw new IllegalStateException("Invalid label: " + line);
                result.add(new Label(parts[0], parts[1], parts[2]));
            }
        }
        if (result.isEmpty()) throw new IllegalStateException("labels.txt is empty");
        return result;
    }

    private static MappedByteBuffer loadModel(Context context) throws Exception {
        AssetFileDescriptor fd = context.getAssets().openFd("model.tflite");
        try (FileInputStream stream = new FileInputStream(fd.getFileDescriptor())) {
            return stream.getChannel().map(FileChannel.MapMode.READ_ONLY, fd.getStartOffset(), fd.getDeclaredLength());
        }
    }

    @Override public void close() {
        if (interpreter != null) interpreter.close();
    }
}
