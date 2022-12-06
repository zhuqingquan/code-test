package com.example.testadrscreencapture;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;

import com.example.testadrscreencapture.ui.ScreenCapPreviewer;
import com.example.testadrscreencapture.vencoder.VideoEncodeConfig;
import com.example.testadrscreencapture.vencoder.VideoEncoder;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.example.testadrscreencapture.databinding.ActivityMainBinding;

import android.content.Intent;
import android.media.projection.MediaProjectionManager;
import android.os.Environment;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.RadioGroup;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import static android.Manifest.permission.RECORD_AUDIO;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static android.content.Context.MEDIA_PROJECTION_SERVICE;
import static android.os.Build.VERSION_CODES.M;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private Button mBtStartCap;
    private Surface mPreviewSurface;
    private int mPreviewSurfaceWidth = 0;
    private int mPreviewSurfaceHeight = 0;
    private boolean mIsRecordeAudio = false;
    boolean mRecorderStarted = false;
    boolean mIsPreview = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        BottomNavigationView navView = findViewById(R.id.nav_view);
        // Passing each menu ID as a set of Ids because each
        // menu should be considered as top level destinations.
        AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.navigation_home, R.id.navigation_dashboard, R.id.navigation_notifications)
                .build();
        NavController navController = Navigation.findNavController(this, R.id.nav_host_fragment_activity_main);
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
        NavigationUI.setupWithNavController(binding.navView, navController);

        // 实时预览屏幕的SurfaceView
        ScreenCapPreviewer capPreviewer = (ScreenCapPreviewer)findViewById(R.id.surfaceView_screenCapPreview);
        SurfaceHolder surfaceHolder = capPreviewer.getHolder();
        surfaceHolder.addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(@NonNull SurfaceHolder holder) {

            }

            @Override
            public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
                mPreviewSurface = holder.getSurface();
                mPreviewSurfaceWidth = width;
                mPreviewSurfaceHeight = height;
            }

            @Override
            public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
            }
        });

        // 开始录制按键
        mBtStartCap = (Button)findViewById(R.id.btStartCap);
        mBtStartCap.setOnClickListener(v -> {
            if(!mRecorderStarted)
            {
                if(hasExternalStoragePermissions()) {
                    startCapture();
                }
                else {
                    requestPermissions();
                }
            }
            else
            {
                Log.i("ScreenCap:", "click button to stop recorder");
                doStopRecordeCapture();
            }
        });


        // 是否勾选预览CheckBox
        CheckBox cbIsPreview = findViewById(R.id.cbPreview);
        cbIsPreview.setOnCheckedChangeListener((buttonView, isChecked) -> {
            mIsPreview = isChecked;
            if(mIsPreview)
            {
                doStopRecordeCapture();
                startCapture();
            }
            else
            {
                stopCapture();
            }
        });
    }

    void doStopRecordeCapture()
    {
        stopCapture();
        releaseEncoder();
        //releaseMuxer();
        mBtStartCap.setText("开始录制");
    }
    ///////////////////// 请求授权录制写入文件以及音频录制 ///////////////////////////////////////////
    private static final int REQUEST_PERMISSIONS = 2;
    private static final String rec_audio_request_msg = "using_your_mic_to_record_audio";
    @TargetApi(M)
    private void requestPermissions() {
        String[] permissions = mIsRecordeAudio
                ? new String[]{WRITE_EXTERNAL_STORAGE, RECORD_AUDIO}
                : new String[]{WRITE_EXTERNAL_STORAGE};
        boolean showRationale = false;
        for (String perm : permissions) {
            showRationale |= shouldShowRequestPermissionRationale(perm);
        }
        if (!showRationale) {
            requestPermissions(permissions, REQUEST_PERMISSIONS);
            return;
        }
        new AlertDialog.Builder(this)
                .setMessage(rec_audio_request_msg)
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok, (dialog, which) ->
                        requestPermissions(permissions, REQUEST_PERMISSIONS))
                .setNegativeButton(android.R.string.cancel, null)
                .create()
                .show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_PERMISSIONS) {
            int granted = PackageManager.PERMISSION_GRANTED;
            for (int r : grantResults) {
                granted |= r;
            }
            if (granted == PackageManager.PERMISSION_GRANTED) {
                startCapture();
            } else {
                //toast(getString(R.string.no_permission));
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    ///////////////////////////////MediaProjection///////////////////////////////////////////////
    MediaProjectionManager mediaProjectionManager;
    MediaProjection mediaProjection;
    VirtualDisplay virDisplay;
    Surface mCapSurface;
    int mCapSurfaceWidth;
    int mCapSurfaceHeight;
    VirtualDisplay.Callback virDisplayCallback = new VirtualDisplay.Callback() {
        @Override
        public void onPaused() {
            super.onPaused();
        }

        @Override
        public void onResumed() {
            super.onResumed();
        }

        @Override
        public void onStopped() {
            super.onStopped();
        }
    };

    static final int RECORD_REQUEST_CODE = 201;
    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    void startCapture()
    {
        // 申请录制屏幕的权限，此权限每次都要请求授权而不是只需一次授权
        mediaProjectionManager = (MediaProjectionManager)getSystemService(MEDIA_PROJECTION_SERVICE);
        Intent intent = mediaProjectionManager.createScreenCaptureIntent();
        startActivityForResult(intent, RECORD_REQUEST_CODE);
        Log.i("ScreenCap:", "start request MediaProjection permission.");
    }

    void stopCapture()
    {
        Intent svc = new Intent(this, ScreenCapService.class);
        stopService(svc);
        Log.i("ScreenCap:", "request to Stop MediaProjection service.");
    }

    private void initCapSurface(){
        if(mIsPreview)
        {
            mCapSurface = mPreviewSurface;
            mCapSurfaceWidth = mPreviewSurfaceWidth;
            mCapSurfaceHeight = mPreviewSurfaceHeight;
        }
        else
        {
            if(mEncoder!=null) {
                mCapSurface = mEncoder.getInputSurface();// 此处获取MediaCodec.createInputSurface返回的Surface，并传给ScreenCapService用于创建VirtualDisplay
                mCapSurfaceWidth = mEncoder.getConfig().width;
                mCapSurfaceHeight = mEncoder.getConfig().height;
            }
        }
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode==RECORD_REQUEST_CODE && resultCode==RESULT_OK)
        {
            if(!mIsPreview)
            {
                // 在需要使用编码器MediaCodec对象中的Surface设置到VirtualDisplay时，需要先创建编码器
                createVideoEncoder();
            }
            initCapSurface();
            if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O)
            {
                Log.i("ScreenCap:", "start ScreenCapService.");
                Intent svc = new Intent(this, ScreenCapService.class);
                svc.putExtra("data", data);
                svc.putExtra("resultCode", resultCode);
                svc.putExtra("surface", mCapSurface);
                svc.putExtra("width", mCapSurfaceWidth);
                svc.putExtra("height", mCapSurfaceHeight);
                startForegroundService(svc);
            }
            else
            {
                Log.i("ScreenCap:", "start getMediaProjection.");
                mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data);
                int w = mCapSurfaceWidth;
                int h = mCapSurfaceHeight;
                Log.i("ScreenCap:", "createVirtualDisplay.w="+w+" h="+h);
                virDisplay = mediaProjection.createVirtualDisplay("RECORDER_VIR_DISPLAY_0", w, h, 1,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC, mCapSurface, virDisplayCallback, null);
            }
            if(!mIsPreview)
            {
                mRecorderStarted = true;
                mBtStartCap.setText("停止录屏");
            }
        }
    }


    //////////////////////////////////////////VideoEncoder///////////////////////////////////////
    VideoEncoder mEncoder;
    VideoEncoder.Callback mEncoderCallback = new VideoEncoder.Callback() {
        public void onEncodedDataAvailable(int index, MediaCodec.BufferInfo info, ByteBuffer buffer)
        {
            if(mMuxer!=null && mVideoTrackIndex!=INVALID_TRACK_INDEX)
            {
                boolean isEndOfStream = (info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0; // 流结束符
                boolean configFrame = (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG)!=0; // sps pps for 264
                if(isEndOfStream) {
                    releaseMuxer();
                    return;
                }
                if(configFrame) {
                    // 使用Mutex时无需保存PPS SPS
                    return;
                }
                if (buffer != null && mMuxer!=null) {
                    buffer.position(info.offset);
                    buffer.limit(info.offset + info.size);
                    mMuxer.writeSampleData(mVideoTrackIndex, buffer, info);
                }
            }
        }

        public void onEncodedDataFormatChanged(MediaFormat format)
        {
            releaseMuxer();
            createMuxer(format, null);
        }

        public void onError(MediaCodec.CodecException e)
        {

        }
    };

    void createVideoEncoder()
    {
        if(mEncoder==null)
        {
            mEncoder = new VideoEncoder();
            MediaCodecInfo.CodecProfileLevel profileLevel = new MediaCodecInfo.CodecProfileLevel();
            profileLevel.profile = MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline;
            profileLevel.level = MediaCodecInfo.CodecProfileLevel.AVCLevel31;
            mEncoder.setConfig(new VideoEncodeConfig(720, 1280, 3000*1024, 30, 3, "", MediaFormat.MIMETYPE_VIDEO_AVC, profileLevel));
            mEncoder.setCallback(mEncoderCallback);
            //mEncoder.setInputSurface(mSurface);       // setInputSurface需要传入PersistentSurface，SurfaceView中的Surface无法满足要求
            mEncoder.start();
            Log.i("ScreenCap:", "create Encoder and start");
        }
    }

    void releaseEncoder()
    {
        if(mEncoder!=null)
        {
            Log.i("ScreenCap:", "stop and release VideoEncoder");
            mEncoder.stop();
            mEncoder = null;
        }
    }

    ///////////////////MediaMuxer///////////////////////////////
    MediaMuxer mMuxer;
    final static int INVALID_TRACK_INDEX = -1;
    int mVideoTrackIndex = INVALID_TRACK_INDEX;
    int mAudioTrackIndex = INVALID_TRACK_INDEX;
    void createMuxer(MediaFormat videoFmt, MediaFormat audioFmt)
    {
        if(!hasExternalStoragePermissions())
        {
            return;
        }
        int width = videoFmt.getInteger(MediaFormat.KEY_WIDTH, -1);
        int height = videoFmt.getInteger(MediaFormat.KEY_HEIGHT, -1);
        if(width==-1 || height==-1)
            return;
        try{
            File filePathName = getSavingFilePathName(width, height);
            mMuxer = new MediaMuxer(filePathName.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            if(videoFmt!=null) {
                int trackIndex = mMuxer.addTrack(videoFmt);
                if(trackIndex==INVALID_TRACK_INDEX)
                {
                    Log.e("MediaMuxer", "create video track failed.format="+videoFmt.toString());
                }
                else
                {
                    mVideoTrackIndex = trackIndex;
                    Log.i("MediaMuxer", "add video track.TrackIndex=" + trackIndex + " format="+videoFmt.toString());
                }
            }
            if(audioFmt!=null) {
                int trackIndex = mMuxer.addTrack(audioFmt);
                if(trackIndex==INVALID_TRACK_INDEX)
                {
                    Log.e("MediaMuxer", "create audio track failed.format="+audioFmt.toString());
                }
                else
                {
                    mVideoTrackIndex = trackIndex;
                    Log.i("MediaMuxer", "add audio track.TrackIndex=" + trackIndex + " format="+audioFmt.toString());
                }
            }
            mMuxer.start();
        }
        catch(IOException ex)
        {
            Log.e("MediaMuxer", "create MediaMuxer failed.Ex="+ex.toString());
            releaseMuxer();
        }
    }

    void releaseMuxer()
    {
        if(mMuxer!=null)
        {
            mMuxer.stop();
            mMuxer.release();
            mMuxer = null;
        }
    }

    private static File getSavingFilePathName(int width, int height) {
        File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                "Screenshots");
        if (!dir.exists() && !dir.mkdirs()) {
            return null;
        }
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US);
        final File file = new File(dir, "Screenshots-" + format.format(new Date())
                + "-" + width + "x" + height + ".mp4");
        return file;
    }

    private boolean hasExternalStoragePermissions() {
        PackageManager pm = getPackageManager();
        String packageName = getPackageName();
        int granted = pm.checkPermission(WRITE_EXTERNAL_STORAGE, packageName);
        return granted == PackageManager.PERMISSION_GRANTED;
    }
}