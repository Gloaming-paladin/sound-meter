// app/src/main/java/com/bodekjan/soundmeter/FrequencyAnalyzer.java
package com.bodekjan.soundmeter;

public class FrequencyAnalyzer {

    public static class FrequencyBand {
        public double lowFreq;
        public double highFreq;
        public double intensity;

        public FrequencyBand(double lowFreq, double highFreq, double intensity) {
            this.lowFreq = lowFreq;
            this.highFreq = highFreq;
            this.intensity = intensity;
        }
    }

    /**
     * 分析音频频段强度
     * @param spectrum 频谱数据
     * @param frequencies 对应频率数组
     * @return 频段强度分析结果
     */
    public static FrequencyBand[] analyzeFrequencyBands(double[] spectrum, double[] frequencies) {
        if (spectrum == null || frequencies == null || spectrum.length != frequencies.length) {
            return new FrequencyBand[0];
        }

        // 定义几个主要频段：低频(20-250Hz), 中频(250-2000Hz), 高频(2000-8000Hz)
        FrequencyBand[] bands = new FrequencyBand[3];

        // 低频段 (20-250Hz)
        bands[0] = calculateBandIntensity(spectrum, frequencies, 20, 250);

        // 中频段 (250-2000Hz)
        bands[1] = calculateBandIntensity(spectrum, frequencies, 250, 2000);

        // 高频段 (2000-8000Hz)
        bands[2] = calculateBandIntensity(spectrum, frequencies, 2000, 8000);

        return bands;
    }

    private static FrequencyBand calculateBandIntensity(double[] spectrum, double[] frequencies,
                                                        double lowFreq, double highFreq) {
        double sum = 0;
        int count = 0;

        for (int i = 0; i < frequencies.length; i++) {
            if (frequencies[i] >= lowFreq && frequencies[i] <= highFreq) {
                sum += spectrum[i];
                count++;
            }
        }

        double intensity = count > 0 ? sum / count : 0;
        return new FrequencyBand(lowFreq, highFreq, intensity);
    }

    /**
     * 找到能量最强的频率
     */
    public static double findPeakFrequency(double[] spectrum, double[] frequencies) {
        if (spectrum == null || frequencies == null || spectrum.length != frequencies.length) {
            return 0;
        }

        int peakIndex = 0;
        double peakValue = 0;

        for (int i = 0; i < spectrum.length; i++) {
            if (spectrum[i] > peakValue) {
                peakValue = spectrum[i];
                peakIndex = i;
            }
        }

        return frequencies[peakIndex];
    }
}
