package com.example.testadrscreencapture.vencoder;

import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.os.AsyncTask;
import android.os.Build;
import android.util.Log;
import android.util.Range;

import java.util.ArrayList;
import java.util.List;

public class MediaCodecUtils {
    interface MediaCodecInfoCallback {
        void onResult(MediaCodecInfo[] infos);
    }

    static final class EncoderFinder extends AsyncTask<String, Void, MediaCodecInfo[]> {
        private MediaCodecInfoCallback func;

        EncoderFinder(MediaCodecInfoCallback func) {
            this.func = func;
        }

        @Override
        protected MediaCodecInfo[] doInBackground(String... mimeTypes) {
            return findEncodersByType(mimeTypes[0]);
        }

        @Override
        protected void onPostExecute(MediaCodecInfo[] mediaCodecInfos) {
            func.onResult(mediaCodecInfos);
        }
    }

    static void findEncodersByTypeAsync(String mimeType, MediaCodecInfoCallback callback) {
        new EncoderFinder(callback).execute(mimeType);
    }

    /**
     * Find an encoder supported specified MIME type
     *
     * @return Returns empty array if not found any encoder supported specified MIME type
     */
    static MediaCodecInfo[] findEncodersByType(String mimeType) {
        MediaCodecList codecList = new MediaCodecList(MediaCodecList.ALL_CODECS);
        List<MediaCodecInfo> infos = new ArrayList<>();
        for (MediaCodecInfo info : codecList.getCodecInfos()) {
            if (!info.isEncoder()) {
                continue;
            }
            try {
                MediaCodecInfo.CodecCapabilities cap = info.getCapabilitiesForType(mimeType);
                if (cap == null) continue;
            } catch (IllegalArgumentException e) {
                // unsupported
                continue;
            }
            infos.add(info);
        }

        return infos.toArray(new MediaCodecInfo[infos.size()]);
    }

    /**
     * 根据参数参数创建MediaFormat对象
     * @param mime MIME type
     * @param width 宽
     * @param height 高
     * @param bitrate 码率。单位为 bps
     * @param frameRate 帧率
     * @param iframeInterval I帧间隔，GOP
     * @param codecProfileLevel profile与level
     * @return
     */
    static MediaFormat createMediaFormat(String mime, int width, int height, int bitrate, int frameRate, int iframeInterval, MediaCodecInfo.CodecProfileLevel codecProfileLevel)
    {
        MediaFormat format = MediaFormat.createVideoFormat(mime, width, height);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, iframeInterval);
        if (codecProfileLevel != null && codecProfileLevel.profile != 0 && codecProfileLevel.level != 0) {
            format.setInteger(MediaFormat.KEY_PROFILE, codecProfileLevel.profile);
            format.setInteger(MediaFormat.KEY_LEVEL, codecProfileLevel.level);
        }
        // maybe useful
        // format.setInteger(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 10_000_000);
        return format;
    }

    static MediaCodecInfo.CodecProfileLevel adapterProfileAndLevel(String mime, MediaCodecInfo codecInfo,  int profile, int level)
    {
        // H264中自适应选择Profile与level的逻辑。可能不需要，但是当用户不指定profile与level时可能需要
        MediaCodecInfo.CodecProfileLevel result = new MediaCodecInfo.CodecProfileLevel();
        result.profile = profile;
        result.level = level;
        if (mime.equals("video/avc")) {
            try {
                MediaCodecInfo.CodecProfileLevel[] pr = codecInfo.getCapabilitiesForType(mime).profileLevels;
                int tmpLevel = 0;
                int tmpProfile = 0;
                for (MediaCodecInfo.CodecProfileLevel aPr : pr) {
                    if (aPr.profile <= MediaCodecInfo.CodecProfileLevel.AVCProfileHigh444) {
                        if (tmpProfile < aPr.profile) {
                            tmpProfile = aPr.profile;
                            tmpLevel = aPr.level;
                        } else if (tmpProfile == aPr.profile && tmpLevel < aPr.level) {
                            tmpProfile = aPr.profile;
                            tmpLevel = aPr.level;
                        }
                    }
                }
                if (tmpProfile > 0) {
                    tmpLevel = tmpLevel > MediaCodecInfo.CodecProfileLevel.AVCLevel42 ?
                            MediaCodecInfo.CodecProfileLevel.AVCLevel42 : tmpLevel;     // avoid crash
                    result.profile = tmpProfile;
                    result.level = tmpLevel;
                }
            } catch (Throwable t) {
                Log.e("", "getCodecInfo error:" + t.toString());
            }
        }
        return result;
    }

    /////////////////////////////////////////
    static public Range<Integer> getHeightRange(MediaCodecInfo codecInfo) {
        if (!codecInfo.isEncoder()) {
            return null;
        }

        String[] types = codecInfo.getSupportedTypes();
        for (int j = 0; j < types.length; j++) {
            MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType(types[j]);
            if (Build.VERSION.SDK_INT >= 21) {
                MediaCodecInfo.VideoCapabilities videoCapabilities = capabilities.getVideoCapabilities();
                if (videoCapabilities != null) {
                    Range<Integer> heightRange = videoCapabilities.getSupportedHeights();
                    return heightRange;
                }
            }
        }
        return null;
    }

    static public Range<Integer> getWidthRange(MediaCodecInfo codecInfo) {
        if (!codecInfo.isEncoder()) {
            return null;
        }

        String[] types = codecInfo.getSupportedTypes();
        for (int j = 0; j < types.length; j++) {
            MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType(types[j]);
            if (Build.VERSION.SDK_INT >= 21) {
                MediaCodecInfo.VideoCapabilities videoCapabilities = capabilities.getVideoCapabilities();
                if (videoCapabilities != null) {
                    Range<Integer> widthRange = videoCapabilities.getSupportedWidths();
                    return widthRange;
                }
            }
        }
        return null;
    }

    static public boolean isSupportedCBRMode(MediaCodecInfo codecInfo) {
        if (!codecInfo.isEncoder()) {
            return false;
        }
        String[] types = codecInfo.getSupportedTypes();
        for (int j = 0; j < types.length; j++) {
            MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType(types[j]);
            if (Build.VERSION.SDK_INT >= 21) {
                MediaCodecInfo.EncoderCapabilities encCap = capabilities.getEncoderCapabilities();
                if (encCap != null) {
                    if (encCap.isBitrateModeSupported(2)) {// 0:BITRATE_MODE_CQ, 1:BITRATE_MODE_VBR, 2:BITRATE_MODE_CBR
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
