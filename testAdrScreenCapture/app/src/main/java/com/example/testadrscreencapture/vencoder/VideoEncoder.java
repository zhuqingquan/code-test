package com.example.testadrscreencapture.vencoder;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Build;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.RequiresApi;

import java.io.IOException;
import java.nio.ByteBuffer;

public class VideoEncoder {

    static public abstract class Callback{
        abstract public void onEncodedDataAvailable(int index, MediaCodec.BufferInfo info, ByteBuffer buffer);
        abstract public void onEncodedDataFormatChanged(MediaFormat format);
        abstract public void onError(MediaCodec.CodecException e);
    };

    final static String TAG = "VideoEncoder";
    VideoEncodeConfig mEncConfig;   // 用户设置的编码参数
    Surface mInputSurface;          // Surface for input raw video data
    MediaCodec mEncoder;            // MediaCodec编码器对象
    MediaFormat mFormat;            // 当前设置的MediaFormat属性
    boolean mIsStarted = false;     // 是否成功start
    int width = 0;                  // 实际编码的宽
    int height = 0;                 // 实际编码高
    Callback mCallback;             // 用户设置的回调对象

    /**
     * 设置或者更新编码参数配置。
     * @param cfg 新的编码参数配置
     * @return true--设置成功，false--设置失败。在启动了编码器之后，如果设置的新参数需要重启编码器才能生效，此时将返回失败
     */
    public boolean setConfig(VideoEncodeConfig cfg)
    {
        if(mEncConfig==null)
        {
            // 首次设置Config
            mEncConfig = cfg;
            return true;
        }
        if(cfg==null && mIsStarted) return false;   // 启动后不支持将Config清空
        if(!cfg.codecName.isEmpty() && cfg.codecName!=mEncConfig.codecName) { return false; }   // 不支持更改codecName
        if(!cfg.mimeType.isEmpty() && cfg.mimeType!=mEncConfig.mimeType) { return false; }      // 不支持更改MimeType

        applyConfig(mEncoder, cfg);
        mEncConfig = cfg;
        return true;
    }

    /**
     * 获取用户设置的VideoEncodeConfig对象
     * @return VideoEncodeConfig对象
     */
    public VideoEncodeConfig getConfig() {
        return mEncConfig;
    }

    public void setCallback(Callback callback)
    {
        mCallback = callback;
    }

    // 将参数更新到MediaCodec对象内
    boolean applyConfig(MediaCodec encoder, VideoEncodeConfig cfg)
    {
        if(encoder==null)   return false;    // 未创建编码器时直接跳过
        if (Build.VERSION.SDK_INT >= 21) {
//            widthRange = getWidthRange(mEncoder.getCodecInfo());
//            heightRange = getHeightRange(mEncoder.getCodecInfo());
//            if (widthRange != null && mWidth < widthRange.getLower()) {
//                mWidth = widthRange.getLower();
//            }
//            if (heightRange != null && mHeight < heightRange.getLower()) {
//                mHeight = heightRange.getLower();
//            }
        }
        width = cfg.width;
        height = cfg.height;
        MediaFormat format = MediaCodecUtils.createMediaFormat(cfg.mimeType, width, height, cfg.bitrate, cfg.frameRate, cfg.iframeInterval, cfg.codecProfileLevel);
        if(format==null)
            return false;
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mFormat = format;
        return true;
    }

    public boolean start()
    {
        if(mIsStarted)
        {
            Log.e(TAG, "VideoEncoder started already.");
            return false;
        }
        if(mEncConfig==null)
        {
            Log.e(TAG, "Encoder Config is null.");
            return false;
        }
        MediaCodec codec = createEncoder(mEncConfig);
        if(codec==null)
        {
            Log.e(TAG, "Create MediaCodec obj failed.Config="+mEncConfig.toString());
            return false;
        }
        try {
            codec.setCallback(mCodecCallback);
            if(!applyConfig(codec, mEncConfig))
            {
                Log.e(TAG, "Configure mediacodec failed.");
            }

            makeSureCreateSurface(codec);
            codec.start();
        } catch (MediaCodec.CodecException e) {
            Log.e("Encoder", "Configure codec failure!\n  with configs " + mEncConfig.toString(), e);
            throw e;
        }
        mEncoder = codec;
        mIsStarted = true;
        return true;
    }

    public void stop()
    {
        if(!mIsStarted) return;
        mIsStarted = false;
        if(mInputSurface!=null)
        {
            // 使用InputSurface编码时，通过signalEndOfInputStream，然后在onOutputBufferAvailable判断流结束时在stop以及release
            mEncoder.signalEndOfInputStream();
            mInputSurface.release();
            mInputSurface = null;
        }
    }

    public Surface getInputSurface()
    {
        return mInputSurface;
    }

    /**
     * 设置输入源的Surface
     * 此接口必须在start之前调用
     * @param surface 提供编码源视频数据的Surface
     */
    @RequiresApi(api = Build.VERSION_CODES.M)
    public void setInputSurface(Surface surface)
    {
        mInputSurface = surface;
    }

    // 如果用户在调用start之前未使用setInputSurface接口设置输入的Surface的话
    // start接口将调用此方法确保在调用MediaCodec.start之前创建InputSurface
    private void makeSureCreateSurface(MediaCodec codec)
    {
        // 如果mInputSurface不为null，则认为用户在start之前主动调用了setInputSurface，此时当前类也会调用MediaCodec.setInputSurface
        // 否则则调用MediaCodec.createInputSurface对Surface进行初始化
        if(mInputSurface==null) {
            mInputSurface = codec.createInputSurface();
        }
        else{
            if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.M) {
                codec.setInputSurface(mInputSurface);
            }
        }
    }

    private MediaCodec createEncoder(VideoEncodeConfig cfg)
    {
        if(cfg==null) return null;
        MediaCodec encoder = null;
        try{
            if(cfg.codecName!=null && !cfg.codecName.isEmpty())
                encoder = MediaCodec.createByCodecName(cfg.codecName);
            if(encoder==null)
                encoder = MediaCodec.createEncoderByType(cfg.mimeType);
        }
        catch(IOException ex)
        {
            Log.e(TAG, "createEncoderByType failed."+ex.toString());
        }
        return encoder;
    }

    /**
     * let media codec run async mode if mCallback != null
     */
    private MediaCodec.Callback mCodecCallback = new MediaCodec.Callback() {
        @Override
        public void onInputBufferAvailable(MediaCodec codec, int index) {
            //mCallback.onInputBufferAvailable(BaseEncoder.this, index);
        }

        @Override
        public void onOutputBufferAvailable(MediaCodec codec, int index, MediaCodec.BufferInfo info) {
            Log.v(TAG, "encoded data index="+index+ " bufferLen="+info.size+ " bufferOffset="+info.offset+" PTS(us)="+info.presentationTimeUs + " flags="+info.flags);
            boolean configFrame = (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG)!=0; // sps pps for 264
            boolean isKeyFrame = (info.flags & MediaCodec.BUFFER_FLAG_KEY_FRAME)!=0; // I帧
            boolean isEndOfStream = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0; // 流结束符
            ByteBuffer encodedData = codec.getOutputBuffer(index);
            if(mCallback!=null)
                mCallback.onEncodedDataAvailable(index, info, encodedData);
            codec.releaseOutputBuffer(index, false);
            if(isEndOfStream)
            {
                //if(mEncoder!=null) mEncoder.stop();
                if(mEncoder!=null)
                {
                    mEncoder.stop();
                    mEncoder.release();
                    mEncoder = null;
                }
            }
        }

        @Override
        public void onError(MediaCodec codec, MediaCodec.CodecException e) {
            Log.i(TAG, "error "+e.toString());
            if(mCallback!=null) mCallback.onError(e);
        }

        @Override
        public void onOutputFormatChanged(MediaCodec codec, MediaFormat format) {
            Log.i(TAG, "output format changed. new format="+format.toString());
            if(mCallback!=null) mCallback.onEncodedDataFormatChanged(format);
        }
    };
}
