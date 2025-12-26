package com.bodekjan.soundmeter;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VideoView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

public class PreviewFragment extends Fragment {

    private static final String TAG = "PreviewFragment";
    private static final int TIMEOUT_US = 10000;

    private Uri fileUri;
    private boolean isVideo;
    private String tempFilePath;

    public static PreviewFragment newInstance(Uri fileUri) {
        PreviewFragment fragment = new PreviewFragment();
        Bundle args = new Bundle();
        args.putParcelable("file_uri", fileUri);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            fileUri = getArguments().getParcelable("file_uri");
            isVideo = getArguments().getBoolean("is_video");
            tempFilePath = getArguments().getString("temp_file_path");
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_preview, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        String decibelText = getArguments().getString("decibel_text");
        String avgDecibelText = getArguments().getString("avg_decibel_text");
        String minDecibelText = getArguments().getString("min_decibel_text");
        String maxDecibelText = getArguments().getString("max_decibel_text");
        String locationText = getArguments().getString("location_text");

        TextView decibelTextView = view.findViewById(R.id.decibel_text_preview);
        TextView avgDecibelTextView = view.findViewById(R.id.avg_decibel_text_preview);
        TextView minDecibelTextView = view.findViewById(R.id.min_decibel_text_preview);
        TextView maxDecibelTextView = view.findViewById(R.id.max_decibel_text_preview);
        TextView locationTextView = view.findViewById(R.id.location_text_preview);

        if (decibelTextView != null) decibelTextView.setText(decibelText);
        if (avgDecibelTextView != null) avgDecibelTextView.setText(avgDecibelText);
        if (minDecibelTextView != null) minDecibelTextView.setText(minDecibelText);
        if (maxDecibelTextView != null) maxDecibelTextView.setText(maxDecibelText);
        if (locationTextView != null) locationTextView.setText(locationText);

        ImageView imageView = view.findViewById(R.id.preview_image);
        VideoView videoView = view.findViewById(R.id.preview_video);

        if (!isVideo) {
            imageView.setVisibility(View.VISIBLE);
            imageView.setImageURI(fileUri);
        } else {
            videoView.setVisibility(View.VISIBLE);
            videoView.setVideoURI(fileUri);
            videoView.start();
        }

        Button saveButton = view.findViewById(R.id.button_save);
        saveButton.setOnClickListener(v -> {
            Log.d(TAG, "Save button clicked.");
            Log.d(TAG, "tempFilePath: " + tempFilePath);
            if (tempFilePath != null) {
                if (isVideo) {
                    saveVideoToGallery();
                } else {
                    saveImageToGallery();
                }
            } else {
                Log.e(TAG, "tempFilePath is null, cannot save.");
                Toast.makeText(getContext(), "Error: File path not found.", Toast.LENGTH_SHORT).show();
            }
        });

        Button dontSaveButton = view.findViewById(R.id.button_dont_save);
        dontSaveButton.setOnClickListener(v -> {
            if (tempFilePath != null) {
                new File(tempFilePath).delete();
            }
            getParentFragmentManager().popBackStack();
        });
    }

    private void saveImageToGallery() {
        try {
            Bitmap originalBitmap = MediaStore.Images.Media.getBitmap(requireContext().getContentResolver(), fileUri);
            Bitmap mutableBitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true);
            Canvas canvas = new Canvas(mutableBitmap);
            Paint paint = new Paint();
            paint.setColor(Color.WHITE);
            paint.setTextSize(36);
            paint.setAntiAlias(true);

            String decibelText = getArguments().getString("decibel_text");
            String avgDecibelText = getArguments().getString("avg_decibel_text");
            String minDecibelText = getArguments().getString("min_decibel_text");
            String maxDecibelText = getArguments().getString("max_decibel_text");
            String locationText = getArguments().getString("location_text");

            canvas.drawText(decibelText, 50, 100, paint);
            canvas.drawText(avgDecibelText, 50, 150, paint);
            canvas.drawText(minDecibelText, 50, 200, paint);
            canvas.drawText(maxDecibelText, 50, 250, paint);
            canvas.drawText(locationText, 50, 300, paint);

            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, System.currentTimeMillis() + ".jpg");
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES);
            } else {
                values.put(MediaStore.Images.Media.DATA, Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getPath());
            }

            ContentResolver resolver = requireContext().getContentResolver();
            Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

            if (uri != null) {
                try (OutputStream outputStream = resolver.openOutputStream(uri)) {
                    mutableBitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
                    Toast.makeText(getContext(), getString(R.string.saved_successfully), Toast.LENGTH_SHORT).show();
                    getParentFragmentManager().popBackStack();
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Error saving image to gallery", e);
            Toast.makeText(getContext(), getString(R.string.save_failed), Toast.LENGTH_SHORT).show();
        }
    }

    private void saveVideoToGallery() {
        // TODO: Fix video processing
        // File outputFile = new File(requireContext().getCacheDir(), "video_with_text.mp4");
        try {
            // addTextToVideo(tempFilePath, outputFile.getAbsolutePath());

            ContentValues contentValues = new ContentValues();
            contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, System.currentTimeMillis() + ".mp4");
            contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/SoundMeter");
            }

            ContentResolver resolver = requireContext().getContentResolver();
            Uri uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues);

            if (uri != null) {
                try (OutputStream outputStream = resolver.openOutputStream(uri);
                     FileInputStream fileInputStream = new FileInputStream(tempFilePath)) {
                    byte[] buf = new byte[1024];
                    int len;
                    while ((len = fileInputStream.read(buf)) > 0) {
                        outputStream.write(buf, 0, len);
                    }
                }
                // outputFile.delete();
                Toast.makeText(getContext(), getString(R.string.saved_successfully), Toast.LENGTH_SHORT).show();
                getParentFragmentManager().popBackStack();
            }

        } catch (IOException e) {
            Log.e(TAG, "Error saving video to gallery", e);
            Toast.makeText(getContext(), getString(R.string.save_failed), Toast.LENGTH_SHORT).show();
        }
    }

    private void addTextToVideo(String inputPath, String outputPath) throws IOException {
        MediaExtractor videoExtractor = null;
        MediaMuxer muxer = null;
        MediaCodec videoDecoder = null;
        MediaCodec videoEncoder = null;
        Surface inputSurface = null;

        try {
            videoExtractor = new MediaExtractor();
            videoExtractor.setDataSource(inputPath);
            muxer = new MediaMuxer(outputPath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

            int videoTrackIndex = -1;
            MediaFormat videoFormat = null;
            for (int i = 0; i < videoExtractor.getTrackCount(); i++) {
                MediaFormat format = videoExtractor.getTrackFormat(i);
                String mime = format.getString(MediaFormat.KEY_MIME);
                if (mime.startsWith("video/")) {
                    videoTrackIndex = i;
                    videoFormat = format;
                    break;
                }
            }

            if (videoTrackIndex == -1) {
                throw new IOException("No video track found in " + inputPath);
            }

            videoExtractor.selectTrack(videoTrackIndex);

            int width = videoFormat.getInteger(MediaFormat.KEY_WIDTH);
            int height = videoFormat.getInteger(MediaFormat.KEY_HEIGHT);

            MediaFormat encoderFormat = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height);
            encoderFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            encoderFormat.setInteger(MediaFormat.KEY_BIT_RATE, videoFormat.getInteger(MediaFormat.KEY_BIT_RATE));
            encoderFormat.setInteger(MediaFormat.KEY_FRAME_RATE, videoFormat.getInteger(MediaFormat.KEY_FRAME_RATE));
            encoderFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);

            videoEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC);
            videoEncoder.configure(encoderFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            inputSurface = videoEncoder.createInputSurface();
            videoEncoder.start();

            videoDecoder = MediaCodec.createDecoderByType(videoFormat.getString(MediaFormat.KEY_MIME));
            videoDecoder.configure(videoFormat, inputSurface, null, 0);
            videoDecoder.start();

            int newVideoTrackIndex = muxer.addTrack(videoEncoder.getOutputFormat());

            // Audio track
            int audioTrackIndex = -1;
            MediaFormat audioFormat = null;
            for (int i = 0; i < videoExtractor.getTrackCount(); i++) {
                MediaFormat format = videoExtractor.getTrackFormat(i);
                if (format.getString(MediaFormat.KEY_MIME).startsWith("audio/")) {
                    audioTrackIndex = i;
                    audioFormat = format;
                    break;
                }
            }

            int newAudioTrackIndex = -1;
            if (audioTrackIndex != -1) {
                newAudioTrackIndex = muxer.addTrack(audioFormat);
            }

            muxer.start();

            // Copy audio track
            if (audioTrackIndex != -1) {
                MediaExtractor audioExtractor = new MediaExtractor();
                audioExtractor.setDataSource(inputPath);
                audioExtractor.selectTrack(audioTrackIndex);
                ByteBuffer buffer = ByteBuffer.allocate(1024 * 1024);
                MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                while (true) {
                    int sampleSize = audioExtractor.readSampleData(buffer, 0);
                    if (sampleSize < 0) break;
                    bufferInfo.offset = 0;
                    bufferInfo.size = sampleSize;
                    bufferInfo.presentationTimeUs = audioExtractor.getSampleTime();
                    bufferInfo.flags = audioExtractor.getSampleFlags();
                    muxer.writeSampleData(newAudioTrackIndex, buffer, bufferInfo);
                    audioExtractor.advance();
                }
                audioExtractor.release();
            }

            // Process video track
            MediaCodec.BufferInfo videoBufferInfo = new MediaCodec.BufferInfo();
            boolean isInputEOS = false;
            boolean isOutputEOS = false;

            while (!isOutputEOS) {
                if (!isInputEOS) {
                    int inputBufferIndex = videoDecoder.dequeueInputBuffer(TIMEOUT_US);
                    if (inputBufferIndex >= 0) {
                        ByteBuffer inputBuffer = videoDecoder.getInputBuffer(inputBufferIndex);
                        int sampleSize = videoExtractor.readSampleData(inputBuffer, 0);
                        if (sampleSize < 0) {
                            videoDecoder.queueInputBuffer(inputBufferIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            isInputEOS = true;
                        } else {
                            videoDecoder.queueInputBuffer(inputBufferIndex, 0, sampleSize, videoExtractor.getSampleTime(), 0);
                            videoExtractor.advance();
                        }
                    }
                }

                int decoderStatus = videoDecoder.dequeueOutputBuffer(videoBufferInfo, TIMEOUT_US);
                if (decoderStatus >= 0) {
                    boolean isEndOfStream = (videoBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    if (videoBufferInfo.size > 0) {
                        drawTextOnSurface(inputSurface);
                    }
                    videoDecoder.releaseOutputBuffer(decoderStatus, videoBufferInfo.size > 0);
                    if (isEndOfStream) {
                        videoEncoder.signalEndOfInputStream();
                    }
                }

                int encoderStatus = videoEncoder.dequeueOutputBuffer(videoBufferInfo, TIMEOUT_US);
                if (encoderStatus >= 0) {
                    ByteBuffer encodedData = videoEncoder.getOutputBuffer(encoderStatus);
                    if ((videoBufferInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                        if (videoBufferInfo.size != 0) {
                            muxer.writeSampleData(newVideoTrackIndex, encodedData, videoBufferInfo);
                        }
                    }
                    isOutputEOS = (videoBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0;
                    videoEncoder.releaseOutputBuffer(encoderStatus, false);
                }
            }

        } finally {
            if (videoDecoder != null) {
                videoDecoder.stop();
                videoDecoder.release();
            }
            if (videoEncoder != null) {
                videoEncoder.stop();
                videoEncoder.release();
            }
            if (inputSurface != null) {
                inputSurface.release();
            }
            if (videoExtractor != null) {
                videoExtractor.release();
            }
            if (muxer != null) {
                try {
                    muxer.stop();
                    muxer.release();
                } catch (IllegalStateException e) {
                    Log.e(TAG, "Muxer already stopped or released", e);
                }
            }
        }
    }

    private void drawTextOnSurface(Surface surface) {
        Canvas canvas = surface.lockCanvas(null);
        if (canvas == null) {
            return;
        }
        try {
            Paint paint = new Paint();
            paint.setColor(Color.WHITE);
            paint.setTextSize(40);
            paint.setAntiAlias(true);

            String[] textToDraw = {
                    getArguments().getString("decibel_text"),
                    getArguments().getString("avg_decibel_text"),
                    getArguments().getString("min_decibel_text"),
                    getArguments().getString("max_decibel_text"),
                    getArguments().getString("location_text")
            };

            int y = 50;
            for (String text : textToDraw) {
                if (text != null) {
                    canvas.drawText(text, 50, y, paint);
                    y += 50;
                }
            }
        } finally {
            surface.unlockCanvasAndPost(canvas);
        }
    }
}
