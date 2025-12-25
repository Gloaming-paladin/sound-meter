package com.bodekjan.soundmeter;

import android.content.Context;
import android.media.MediaRecorder;
import android.os.Build;

import java.io.File;
import java.io.IOException;

/**
 * Created by bodekjan on 2016/8/8.
 */
public class MyMediaRecorder {
    public File myRecAudioFile;
    private MediaRecorder mMediaRecorder;
    public boolean isRecording = false;
    private AudioFormat currentFormat = AudioFormat.THREE_GPP;
    private Context context;

    public MyMediaRecorder(Context context) {
        this.context = context.getApplicationContext();
    }

    public float getMaxAmplitude() {
        if (mMediaRecorder != null) {
            try {
                return mMediaRecorder.getMaxAmplitude();
            } catch (IllegalStateException e) {
                e.printStackTrace();
                return 0;
            }
        }
        return 5;
    }

    public File getMyRecAudioFile() {
        return myRecAudioFile;
    }

    public void setMyRecAudioFile(File myRecAudioFile) {
        this.myRecAudioFile = myRecAudioFile;
    }

    public boolean startRecorder() {
        if (myRecAudioFile == null) {
            return false;
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                mMediaRecorder = new MediaRecorder(context);
            } else {
                mMediaRecorder = new MediaRecorder();
            }

            mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mMediaRecorder.setOutputFormat(currentFormat.getOutputFormat());
            mMediaRecorder.setAudioEncoder(currentFormat.getAudioEncoder());
            mMediaRecorder.setOutputFile(myRecAudioFile.getAbsolutePath());

            mMediaRecorder.prepare();
            mMediaRecorder.start();
            isRecording = true;
            return true;
        } catch (IOException | IllegalStateException e) {
            if (mMediaRecorder != null) {
                mMediaRecorder.reset();
                mMediaRecorder.release();
                mMediaRecorder = null;
            }
            isRecording = false;
            e.printStackTrace();
        }
        return false;
    }

    public void stopRecording() {
        if (mMediaRecorder != null) {
            try {
                if (isRecording) {
                    mMediaRecorder.stop();
                }
            } catch (RuntimeException e) {
                android.util.Log.e("MyMediaRecorder", "stop() failed", e);
            } finally {
                mMediaRecorder.release();
                mMediaRecorder = null;
                isRecording = false;
            }
        }
    }

    public void delete() {
        stopRecording();
        if (myRecAudioFile != null) {
            myRecAudioFile.delete();
            myRecAudioFile = null;
        }
    }

    public void setAudioFormat(AudioFormat format) {
        this.currentFormat = format;
    }

    public enum AudioFormat {
        THREE_GPP("3gp", MediaRecorder.OutputFormat.THREE_GPP, MediaRecorder.AudioEncoder.AMR_NB),
        MPEG_4("mp4", MediaRecorder.OutputFormat.MPEG_4, MediaRecorder.AudioEncoder.AAC),
        WAV("wav", MediaRecorder.OutputFormat.RAW_AMR, MediaRecorder.AudioEncoder.AMR_NB);

        private final String extension;
        private final int outputFormat;
        private final int audioEncoder;

        AudioFormat(String extension, int outputFormat, int audioEncoder) {
            this.extension = extension;
            this.outputFormat = outputFormat;
            this.audioEncoder = audioEncoder;
        }

        public String getExtension() { return extension; }
        public int getOutputFormat() { return outputFormat; }
        public int getAudioEncoder() { return audioEncoder; }
    }
}
