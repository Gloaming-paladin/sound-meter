package com.bodekjan.soundmeter;

import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

public class AudioEditor {
    private static final String TAG = "AudioEditor";

    // 剪辑音频文件
    public boolean trimAudio(String inputFilePath, String outputFilePath, long startTimeMs, long durationMs) {
        try {
            MediaPlayer mediaPlayer = new MediaPlayer();
            mediaPlayer.setDataSource(inputFilePath);
            mediaPlayer.prepare();

            // 获取音频信息 - 使用MediaPlayer的正确方法
            int sampleRate = 44100; // 默认采样率，实际应从音频文件解析
            int channelCount = 2; // 默认双声道，实际应从音频文件解析
            int bufferSize = AudioRecord.getMinBufferSize(sampleRate,
                    channelCount == 2 ? AudioFormat.CHANNEL_IN_STEREO : AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);

            // 创建输出文件
            File outputFile = new File(outputFilePath);
            if (outputFile.exists()) {
                outputFile.delete();
            }

            // 使用AudioTrack播放并记录指定时间段
            AudioRecord audioRecord = new AudioRecord(
                    android.media.MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelCount == 2 ? AudioFormat.CHANNEL_IN_STEREO : AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
            );

            // 由于MediaPlayer无法直接获取采样率等信息，这里使用固定值
            // 实际应用中应使用音频解析库如FFmpeg或SoundPool
            audioRecord.startRecording();

            // 创建缓冲区读取音频数据
            byte[] buffer = new byte[bufferSize];
            int bytesRead;
            long totalBytesRead = 0;
            long maxBytesToRead = (long) (sampleRate * channelCount * 2 * (durationMs / 1000.0)); // 2字节每样本

            FileOutputStream fileOutputStream = new FileOutputStream(outputFile);

            // 跳过开始部分
            long skipBytes = (long) (sampleRate * channelCount * 2 * (startTimeMs / 1000.0));
            while (skipBytes > 0) {
                int skip = audioRecord.read(buffer, 0, (int) Math.min(buffer.length, skipBytes));
                if (skip <= 0) break;
                skipBytes -= skip;
            }

            // 读取指定时长的音频数据
            while (totalBytesRead < maxBytesToRead) {
                bytesRead = audioRecord.read(buffer, 0, (int) Math.min(buffer.length, maxBytesToRead - totalBytesRead));
                if (bytesRead <= 0) break;
                fileOutputStream.write(buffer, 0, bytesRead);
                totalBytesRead += bytesRead;
            }

            fileOutputStream.close();
            audioRecord.stop();
            audioRecord.release();
            mediaPlayer.release();

            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error trimming audio: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // 合并两个音频文件
    public boolean mergeAudioFiles(String file1Path, String file2Path, String outputPath) {
        try {
            File file1 = new File(file1Path);
            File file2 = new File(file2Path);
            File outputFile = new File(outputPath);

            if (outputFile.exists()) {
                outputFile.delete();
            }

            // 读取两个音频文件并合并
            FileInputStream fis1 = new FileInputStream(file1);
            FileInputStream fis2 = new FileInputStream(file2);
            FileOutputStream fos = new FileOutputStream(outputFile);

            byte[] buffer = new byte[1024];
            int bytesRead;

            // 复制第一个文件
            while ((bytesRead = fis1.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }

            // 复制第二个文件
            while ((bytesRead = fis2.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }

            fis1.close();
            fis2.close();
            fos.close();

            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error merging audio files: " + e.getMessage());
            return false;
        }
    }

    // 实现基本噪音过滤算法
    public byte[] applyNoiseReduction(byte[] audioData, int sampleRate) {
        // 简单的噪音过滤算法实现
        // 这里使用简单的均值滤波器作为示例

        // 将字节数组转换为短整型数组（假设PCM 16位）
        short[] samples = new short[audioData.length / 2];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = (short) (((audioData[2*i+1] & 0xFF) << 8) | (audioData[2*i] & 0xFF));
        }

        // 应用简单的低通滤波器
        short[] filteredSamples = new short[samples.length];
        double alpha = 0.9f; // 滤波器系数

        for (int i = 0; i < samples.length; i++) {
            if (i == 0) {
                filteredSamples[i] = samples[i];
            } else {
                filteredSamples[i] = (short) (alpha * filteredSamples[i-1] + (1 - alpha) * samples[i]);
            }
        }

        // 转换回字节数组
        byte[] result = new byte[audioData.length];
        for (int i = 0; i < filteredSamples.length; i++) {
            result[2*i] = (byte) (filteredSamples[i] & 0xFF);
            result[2*i+1] = (byte) ((filteredSamples[i] >> 8) & 0xFF);
        }

        return result;
    }
}
