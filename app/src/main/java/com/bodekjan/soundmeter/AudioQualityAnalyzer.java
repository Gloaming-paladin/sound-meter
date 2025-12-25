package com.bodekjan.soundmeter;

public class AudioQualityAnalyzer {

    // Calculate Total Harmonic Distortion (THD)
    public static double calculateTHD(double[] spectrum, double[] frequencies) {
        if (spectrum == null || spectrum.length < 2) {
            return 0;
        }

        // Find the fundamental frequency (the one with the highest peak)
        int fundamentalIndex = 0;
        double maxMagnitude = 0;
        for (int i = 1; i < spectrum.length; i++) { // Start from 1 to ignore DC component
            if (spectrum[i] > maxMagnitude) {
                maxMagnitude = spectrum[i];
                fundamentalIndex = i;
            }
        }

        if (fundamentalIndex == 0) {
            return 0; // No clear fundamental frequency found
        }

        double fundamentalFrequency = frequencies[fundamentalIndex];
        double fundamentalPower = spectrum[fundamentalIndex] * spectrum[fundamentalIndex];
        double harmonicsPower = 0;

        // Sum the power of the harmonics (2nd, 3rd, 4th, etc.)
        for (int i = 2; i * fundamentalIndex < spectrum.length; i++) {
            int harmonicIndex = i * fundamentalIndex;
            harmonicsPower += spectrum[harmonicIndex] * spectrum[harmonicIndex];
        }

        if (fundamentalPower == 0) {
            return 0;
        }

        return Math.sqrt(harmonicsPower / fundamentalPower);
    }

    // Calculate Signal-to-Noise Ratio (SNR)
    public static double calculateSNR(double[] spectrum, double[] frequencies) {
        if (spectrum == null || spectrum.length == 0) {
            return 0;
        }

        double signalPower = 0;
        double noisePower = 0;
        int signalIndex = 0;
        double maxMagnitude = 0;

        // Find the main signal component
        for (int i = 1; i < spectrum.length; i++) {
            if (spectrum[i] > maxMagnitude) {
                maxMagnitude = spectrum[i];
                signalIndex = i;
            }
        }

        if (signalIndex > 0) {
            signalPower = spectrum[signalIndex] * spectrum[signalIndex];
        }

        // Assume the rest is noise
        for (int i = 1; i < spectrum.length; i++) {
            if (i != signalIndex) {
                noisePower += spectrum[i] * spectrum[i];
            }
        }

        if (noisePower == 0) {
            return 100; // Effectively infinite SNR
        }

        return 10 * Math.log10(signalPower / noisePower);
    }

    // Calculate Spectral Centroid
    public static double calculateSpectralCentroid(double[] spectrum, double[] frequencies) {
        if (spectrum == null || frequencies == null || spectrum.length != frequencies.length) {
            return 0;
        }

        double weightedSum = 0;
        double totalMagnitude = 0;

        for (int i = 0; i < spectrum.length; i++) {
            weightedSum += frequencies[i] * spectrum[i];
            totalMagnitude += spectrum[i];
        }

        if (totalMagnitude == 0) {
            return 0;
        }

        return weightedSum / totalMagnitude;
    }

    // Calculate Spectral Bandwidth
    public static double calculateSpectralBandwidth(double[] spectrum, double[] frequencies, double centroid) {
        if (spectrum == null || frequencies == null || spectrum.length != frequencies.length) {
            return 0;
        }

        double weightedSum = 0;
        double totalMagnitude = 0;

        for (int i = 0; i < spectrum.length; i++) {
            double deviation = frequencies[i] - centroid;
            weightedSum += deviation * deviation * spectrum[i];
            totalMagnitude += spectrum[i];
        }

        if (totalMagnitude == 0) {
            return 0;
        }

        return Math.sqrt(weightedSum / totalMagnitude);
    }

    public static double calculateDecibels(short[] audioBuffer) {
        if (audioBuffer == null || audioBuffer.length == 0) {
            return 0;
        }

        double sumOfSquares = 0;
        for (short sample : audioBuffer) {
            sumOfSquares += sample * sample;
        }

        double rms = Math.sqrt(sumOfSquares / audioBuffer.length);

        if (rms == 0) {
            return 0; // Or a very small number to represent silence
        }

        // Reference pressure for dB calculation (can be adjusted)
        double reference = 32767.0; // Max amplitude for 16-bit audio
        return 20 * Math.log10(rms / reference);
    }
}
