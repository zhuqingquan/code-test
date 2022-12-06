package com.example.testadrscreencapture.vencoder;

import android.media.MediaCodecInfo;

import java.util.Objects;

public class VideoEncodeConfig {
    final public int width;
    final public int height;
    final public int bitrate;
    final public int frameRate;
    final public int iframeInterval;
    final public String codecName;
    final public String mimeType;
    final public MediaCodecInfo.CodecProfileLevel codecProfileLevel;

    /**
     * @param codecName         selected codec name, maybe null
     * @param mimeType          video MIME type, cannot be null
     * @param codecProfileLevel profile level for video encoder nullable
     */
    public VideoEncodeConfig(int width, int height, int bitrate,
                             int frameRate, int iframeInterval,
                             String codecName, String mimeType,
                             MediaCodecInfo.CodecProfileLevel codecProfileLevel) {
        this.width = width;
        this.height = height;
        this.bitrate = bitrate;
        this.frameRate = frameRate;
        this.iframeInterval = iframeInterval;
        this.codecName = codecName;
        this.mimeType = Objects.requireNonNull(mimeType);
        this.codecProfileLevel = codecProfileLevel;
    }

    @Override
    public String toString() {
        return "VideoEncodeConfig{" +
                "width=" + width +
                ", height=" + height +
                ", bitrate=" + bitrate +
                ", frameRate=" + frameRate +
                ", iframeInterval=" + iframeInterval +
                ", codecName='" + codecName + '\'' +
                ", mimeType='" + mimeType + '\'' +
                //", codecProfileLevel=" + (codecProfileLevel == null ? "" : Utils.avcProfileLevelToString(codecProfileLevel)) +
                '}';
    }
}
