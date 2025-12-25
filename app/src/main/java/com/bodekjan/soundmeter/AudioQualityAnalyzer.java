// app/src/main/java/com/bodekjan/soundmeter/AudioQualityAnalyzer.java
package com.bodekjan.soundmeter;

public class AudioQualityAnalyzer {

    /**
     * 计算总谐波失真 (THD)
     * THD = sqrt(次谐波功率之和) / 基波功率
     */
    public static double calculateTHD(double[] spectrum, double[] frequencies) {
        if (spectrum == null || frequencies == null || spectrum.length != frequencies.length) {
            return 0;
        }

        // 找到基波频率（能量最大的频率）
        int fundamentalIndex = findPeakIndex(spectrum);
        double fundamentalFreq = frequencies[fundamentalIndex];
        double fundamentalPower = spectrum[fundamentalIndex] * spectrum[fundamentalIndex];

        // 计算谐波功率（基波频率的整数倍）
        double harmonicPower = 0;
        for (int i = 0; i < frequencies.length; i++) {
            double ratio = frequencies[i] / fundamentalFreq;
            // 检查是否为谐波（频率是基波的整数倍，允许5%误差）
            if (isHarmonic(ratio)) {
                harmonicPower += spectrum[i] * spectrum[i];
            }
        }

        // THD = sqrt(谐波功率) / sqrt(基波功率)
        if (fundamentalPower > 0) {
            return Math.sqrt(harmonicPower) / Math.sqrt(fundamentalPower);
        } else {
            return 0;
        }
    }

    private static int findPeakIndex(double[] spectrum) {
        int peakIndex = 0;
        double peakValue = 0;

        for (int i = 0; i < spectrum.length; i++) {
            if (spectrum[i] > peakValue) {
                peakValue = spectrum[i];
                peakIndex = i;
            }
        }

        return peakIndex;
    }

    private static boolean isHarmonic(double ratio) {
        // 检查比率是否接近整数（允许5%误差）
        int roundedRatio = (int) Math.round(ratio);
        return Math.abs(ratio - roundedRatio) < 0.05 && roundedRatio > 1;
    }

    /**
     * 计算信噪比 (SNR)
     * SNR = 10 * log10(signal_power / noise_power)
     */
    public static double calculateSNR(double[] spectrum, double[] frequencies) {
        if (spectrum == null || frequencies == null || spectrum.length != frequencies.length) {
            return 0;
        }

        // 找到信号频率范围（假设为20Hz-20kHz）
        double signalPower = 0;
        double noisePower = 0;
        int signalCount = 0;
        int noiseCount = 0;

        for (int i = 0; i < spectrum.length; i++) {
            double freq = frequencies[i];
            double power = spectrum[i] * spectrum[i]; // 功率 = 幅度平方

            if (freq >= 20 && freq <= 20000) {
                // 在音频范围内的认为是信号
                signalPower += power;
                signalCount++;
            } else {
                // 超出音频范围的认为是噪声
                noisePower += power;
                noiseCount++;
            }
        }

        if (signalCount > 0 && noiseCount > 0) {
            signalPower /= signalCount; // 平均信号功率
            noisePower /= noiseCount;   // 平均噪声功率

            if (noisePower > 0) {
                return 10 * Math.log10(signalPower / noisePower);
            }
        }

        return Double.POSITIVE_INFINITY; // 如果噪声为0，SNR为无穷大
    }

    /**
     * 计算THD+N (THD + Noise)
     */
    public static double calculateTHDPlusN(double[] spectrum, double[] frequencies) {
        if (spectrum == null || frequencies == null || spectrum.length != frequencies.length) {
            return 0;
        }

        int fundamentalIndex = findPeakIndex(spectrum);
        double fundamentalPower = spectrum[fundamentalIndex] * spectrum[fundamentalIndex];

        double harmonicAndNoisePower = 0;
        for (int i = 0; i < spectrum.length; i++) {
            if (i != fundamentalIndex) {
                double ratio = frequencies[i] / frequencies[fundamentalIndex];
                // 如果不是谐波，则计入噪声
                if (!isHarmonic(ratio) || i == fundamentalIndex) {
                    harmonicAndNoisePower += spectrum[i] * spectrum[i];
                }
            }
        }

        if (fundamentalPower > 0) {
            return Math.sqrt(harmonicAndNoisePower) / Math.sqrt(fundamentalPower);
        } else {
            return 0;
        }
    }
}
