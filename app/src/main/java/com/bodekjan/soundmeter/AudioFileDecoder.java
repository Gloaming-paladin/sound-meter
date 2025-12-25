package com.bodekjan.soundmeter;

import android.content.Context;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;

import java.io.IOException;
import java.nio.ByteBuffer;

public class AudioFileDecoder {
    private MediaExtractor extractor;
    private MediaCodec codec;
    private ByteBuffer[] inputBuffers;
    private ByteBuffer[] outputBuffers;
    private MediaCodec.BufferInfo bufferInfo;
    private boolean isEOS = false;

    public AudioFileDecoder(Context context, Uri uri) throws IOException {
        extractor = new MediaExtractor();
        extractor.setDataSource(context, uri, null);

        int trackIndex = selectAudioTrack(extractor);
        if (trackIndex < 0) {
            throw new IOException("No audio track found in the file.");
        }
        extractor.selectTrack(trackIndex);

        MediaFormat format = extractor.getTrackFormat(trackIndex);
        String mime = format.getString(MediaFormat.KEY_MIME);

        codec = MediaCodec.createDecoderByType(mime);
        codec.configure(format, null, null, 0);
        codec.start();

        inputBuffers = codec.getInputBuffers();
        outputBuffers = codec.getOutputBuffers();
        bufferInfo = new MediaCodec.BufferInfo();
    }

    private int selectAudioTrack(MediaExtractor extractor) {
        for (int i = 0; i < extractor.getTrackCount(); i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (mime.startsWith("audio/")) {
                return i;
            }
        }
        return -1;
    }

    public int read(short[] buffer) throws IOException {
        int shortsCount = 0;

        while (shortsCount == 0 && !isEOS) {
            int inputBufferIndex = codec.dequeueInputBuffer(10000);
            if (inputBufferIndex >= 0) {
                ByteBuffer inputBuffer = inputBuffers[inputBufferIndex];
                int sampleSize = extractor.readSampleData(inputBuffer, 0);

                if (sampleSize < 0) {
                    codec.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                    isEOS = true;
                } else {
                    codec.queueInputBuffer(inputBufferIndex, 0, sampleSize, extractor.getSampleTime(), 0);
                    extractor.advance();
                }
            }

            int outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 10000);
            if (outputBufferIndex >= 0) {
                if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    isEOS = true;
                    break;
                }

                ByteBuffer outputBuffer = outputBuffers[outputBufferIndex];
                int size = bufferInfo.size;
                if (size > 0) {
                    shortsCount = size / 2;
                    if (buffer.length < shortsCount) {
                        shortsCount = buffer.length;
                    }
                    outputBuffer.asShortBuffer().get(buffer, 0, shortsCount);
                }
                codec.releaseOutputBuffer(outputBufferIndex, false);

            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                outputBuffers = codec.getOutputBuffers();
            } else if (outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                // The format is changed
            }
        }

        return isEOS && shortsCount == 0 ? -1 : shortsCount;
    }

    public void release() throws IOException {
        if (codec != null) {
            codec.stop();
            codec.release();
            codec = null;
        }
        if (extractor != null) {
            extractor.release();
            extractor = null;
        }
    }
}
