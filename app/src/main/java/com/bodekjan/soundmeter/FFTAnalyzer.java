package com.bodekjan.soundmeter;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.Uri;

import java.io.IOException;

public class FFTAnalyzer {
    private static final int SAMPLE_RATE = 44100;
    private static final int BUFFER_SIZE = 1024;
    public static final int FFT_SIZE = 512;

    private AudioRecord audioRecord;
    private AudioFileDecoder audioFileDecoder; // 用于解码本地文件
    private short[] audioBuffer;
    private double[] realPart;
    private double[] imaginaryPart;
    private boolean isRecording = false;

    // 用于实时录音分析
    public FFTAnalyzer() {
        // Constructor for real-time analysis
        audioBuffer = new short[FFT_SIZE];
        realPart = new double[FFT_SIZE];
        imaginaryPart = new double[FFT_SIZE];
    }

    // 用于本地文件分析
    public FFTAnalyzer(Context context, Uri audioFileUri) throws IOException {
        audioFileDecoder = new AudioFileDecoder(context, audioFileUri);
        audioBuffer = new short[FFT_SIZE];
        realPart = new double[FFT_SIZE];
        imaginaryPart = new double[FFT_SIZE];
        isRecording = true; // 标记为“正在处理”
    }

    public boolean startRecording() {
        if (isRecording) {
            return true;
        }

        int minBufferSizeInBytes = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
        );
        if (minBufferSizeInBytes == AudioRecord.ERROR_BAD_VALUE) {
            return false;
        }

        int bufferSizeInBytes = minBufferSizeInBytes * 2;

        try {
            audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSizeInBytes
            );
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
            return false;
        }

        if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release();
            audioRecord = null;
            return false;
        }

        try {
            audioRecord.startRecording();
        } catch (IllegalStateException e) {
            e.printStackTrace();
            audioRecord.release();
            audioRecord = null;
            return false;
        }

        if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
            isRecording = true;
            return true;
        }

        audioRecord.release();
        audioRecord = null;
        return false;
    }

    public void stopRecording() {
        if (!isRecording) {
            return;
        }
        isRecording = false;
        if (audioRecord != null) {
            if (audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                try {
                    audioRecord.stop();
                } catch (IllegalStateException e) {
                    e.printStackTrace();
                }
            }
            if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
                audioRecord.release();
            }
            audioRecord = null;
        }
    }

    public double[] getFrequencySpectrum() {
        if (!isRecording) {
            return null;
        }

        int bytesRead;
        if (audioRecord != null && audioRecord.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
            bytesRead = audioRecord.read(audioBuffer, 0, audioBuffer.length);
        } else if (audioFileDecoder != null) {
            try {
                bytesRead = audioFileDecoder.read(audioBuffer);
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            }
        } else {
            return null;
        }

        if (bytesRead <= 0) {
            return null;
        }

        for (int i = 0; i < FFT_SIZE; i++) {
            if (i < bytesRead) {
                realPart[i] = audioBuffer[i] / 32768.0;
            } else {
                realPart[i] = 0;
            }
            imaginaryPart[i] = 0;
        }

        performFFT(realPart, imaginaryPart);

        double[] magnitudeSpectrum = new double[FFT_SIZE / 2];
        for (int i = 0; i < FFT_SIZE / 2; i++) {
            magnitudeSpectrum[i] = Math.sqrt(realPart[i] * realPart[i] + imaginaryPart[i] * imaginaryPart[i]);
        }

        return magnitudeSpectrum;
    }

    private void performFFT(double[] real, double[] imag) {
        int n = real.length;
        if (Integer.bitCount(n) != 1) {
            throw new IllegalArgumentException("The length of the array must be a power of 2.");
        }

        int j = 0;
        for (int i = 0; i < n; i++) {
            if (i < j) {
                double tempReal = real[i];
                double tempImag = imag[i];
                real[i] = real[j];
                imag[i] = imag[j];
                real[j] = tempReal;
                imag[j] = tempImag;
            }

            int k = n >> 1;
            while (k <= j) {
                j -= k;
                k >>= 1;
            }
            j += k;
        }

        for (int length = 2; length <= n; length <<= 1) {
            double angle = -2 * Math.PI / length;
            double wLenReal = Math.cos(angle);
            double wLenImag = Math.sin(angle);

            for (int i = 0; i < n; i += length) {
                double wReal = 1;
                double wImag = 0;

                for (int k2 = i; k2 < i + length / 2; k2++) {
                    int k = k2 + length / 2;
                    double tReal = wReal * real[k] - wImag * imag[k];
                    double tImag = wReal * imag[k] + wImag * real[k];
                    real[k] = real[k2] - tReal;
                    imag[k] = imag[k2] - tImag;
                    real[k2] += tReal;
                    imag[k2] += tImag;

                    double nextWReal = wReal * wLenReal - wImag * wLenImag;
                    double nextWImag = wReal * wLenImag + wImag * wLenReal;
                    wReal = nextWReal;
                    wImag = nextWImag;
                }
            }
        }
    }

    public void release() {
        stopRecording();
        if (audioRecord != null) {
            audioRecord.release();
            audioRecord = null;
        }
        if (audioFileDecoder != null) {
            try {
                audioFileDecoder.release();
            } catch (IOException e) {
                e.printStackTrace();
            }
            audioFileDecoder = null;
        }
    }

    public static double[] getFrequencyBins(int sampleRate, int fftSize) {
        if (fftSize <= 0) {
            throw new IllegalArgumentException("FFT size must be a positive integer.");
        }
        if (Integer.bitCount(fftSize) != 1) {
            throw new IllegalArgumentException("FFT size must be a power of 2.");
        }
        double[] frequencies = new double[fftSize / 2];
        double frequencyStep = (double) sampleRate / fftSize;
        for (int i = 0; i < fftSize / 2; i++) {
            frequencies[i] = i * frequencyStep;
        }
        return frequencies;
    }
}
