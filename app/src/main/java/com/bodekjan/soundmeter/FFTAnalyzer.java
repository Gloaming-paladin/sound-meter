// app/src/main/java/com/bodekjan/soundmeter/FFTAnalyzer.java
package com.bodekjan.soundmeter;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import java.util.Arrays;

public class FFTAnalyzer {
    private static final int SAMPLE_RATE = 44100;
    private static final int BUFFER_SIZE = 1024;
    private static final int FFT_SIZE = 512; // 2的幂次方，用于FFT

    private AudioRecord audioRecord;
    private short[] audioBuffer;
    private double[] realPart;
    private double[] imaginaryPart;
    private boolean isRecording = false;

    public FFTAnalyzer() {
        // 初始化音频录制
        int minBufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
        );

        // 确保缓冲区大小至少为FFT_SIZE的两倍
        int bufferSize = Math.max(minBufferSize, FFT_SIZE * 2);
        audioBuffer = new short[bufferSize];

        // 初始化FFT数组
        realPart = new double[FFT_SIZE];
        imaginaryPart = new double[FFT_SIZE];

        audioRecord = new AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
        );
    }

    public void startRecording() {
        if (audioRecord.getState() == AudioRecord.STATE_INITIALIZED) {
            audioRecord.startRecording();
            isRecording = true;
        }
    }

    public void stopRecording() {
        if (isRecording) {
            audioRecord.stop();
            isRecording = false;
        }
    }

    public double[] getFrequencySpectrum() {
        if (!isRecording) {
            return null;
        }

        // 读取音频数据
        int bytesRead = audioRecord.read(audioBuffer, 0, FFT_SIZE);
        if (bytesRead <= 0) {
            return null;
        }

        // 填充实部和虚部数组
        for (int i = 0; i < FFT_SIZE; i++) {
            if (i < bytesRead) {
                realPart[i] = audioBuffer[i] / 32768.0; // 归一化到[-1, 1]
            } else {
                realPart[i] = 0;
            }
            imaginaryPart[i] = 0;
        }

        // 执行FFT
        performFFT(realPart, imaginaryPart);

        // 计算幅度谱
        double[] magnitudeSpectrum = new double[FFT_SIZE / 2];
        for (int i = 0; i < FFT_SIZE / 2; i++) {
            magnitudeSpectrum[i] = Math.sqrt(realPart[i] * realPart[i] +
                    imaginaryPart[i] * imaginaryPart[i]);
        }

        return magnitudeSpectrum;
    }

    // 快速傅里叶变换算法
    private void performFFT(double[] real, double[] imag) {
        int n = real.length;
        if (Integer.bitCount(n) != 1) {
            throw new IllegalArgumentException("数组长度必须是2的幂");
        }

        // 位反转置换
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

        // FFT计算
        for (int length = 2; length <= n; length <<= 1) {
            double angle = -2 * Math.PI / length;
            double wLenReal = Math.cos(angle);
            double wLenImag = Math.sin(angle);

            for (int i = 0; i < n; i += length) {
                double wReal = 1;
                double wImag = 0;

                // 修改：将内层循环的j改为k2以避免变量名冲突
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
    }

    public static double[] getFrequencyBins(int sampleRate, int fftSize) {
        double[] frequencies = new double[fftSize / 2];
        double frequencyStep = (double) sampleRate / fftSize;
        for (int i = 0; i < fftSize / 2; i++) {
            frequencies[i] = i * frequencyStep;
        }
        return frequencies;
    }
}
