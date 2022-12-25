package com.example.testadrscreencapture.ui.home;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PixelFormat;
import android.graphics.SurfaceTexture;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.opengl.GLES20;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import com.example.testadrscreencapture.CameraUtils;
import com.example.testadrscreencapture.R;
import com.example.testadrscreencapture.ScreenCapService;
import com.example.testadrscreencapture.databinding.FragmentHomeBinding;
import com.example.testadrscreencapture.glrender.GLRender;
import com.example.testadrscreencapture.glrender.RectSpirit2d;
import com.example.testadrscreencapture.ui.ScreenCapPreviewer;
import com.example.testadrscreencapture.ui.dashboard.EGLSurfaceView;
import com.example.testadrscreencapture.vencoder.VideoEncodeConfig;
import com.example.testadrscreencapture.vencoder.VideoEncoder;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import static android.Manifest.permission.RECORD_AUDIO;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;
import static android.app.Activity.RESULT_OK;
import static android.content.Context.MEDIA_PROJECTION_SERVICE;
import static android.os.Build.VERSION_CODES.M;

public class HomeFragment extends Fragment {

    private static final String TAG = "HomeFragment";
    private HomeViewModel homeViewModel;
    private FragmentHomeBinding binding;
    private Button mBtStartCap;
    private ScreenCapPreviewer mEGLSurfaceView = null;

    private Surface mPreviewSurface;                    // 从预览的UI SurfaceView中获取的Surface对象
    private int mPreviewSurfaceWidth = 0;
    private int mPreviewSurfaceHeight = 0;
    private boolean mIsRecordeAudio = false;
    boolean mRecorderStarted = false;
    boolean mIsPreview = false;

    RectSpirit2d mRectForScreenCapPreview = null;

    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        homeViewModel =
                new ViewModelProvider(this).get(HomeViewModel.class);

        binding = FragmentHomeBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        //final TextView textView = binding.textHome;
        homeViewModel.getText().observe(getViewLifecycleOwner(), new Observer<String>() {
            @Override
            public void onChanged(@Nullable String s) {
                //textView.setText(s);
            }
        });

        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
        deinitVideoRender();
        Log.i(TAG, "onDestroyView");
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        if(checkCameraHardware(getContext())) {
            // Android 6.0相机动态权限检查
            if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED) {
                //initView();
            } else {
                ActivityCompat.requestPermissions(getActivity(),
                        new String[]{
                                Manifest.permission.CAMERA,
                                Manifest.permission.WRITE_EXTERNAL_STORAGE
                        }, 0);
            }
        }
        // 实时预览屏幕的SurfaceView
        mEGLSurfaceView = binding.surfaceViewScreenCapPreview;//(ScreenCapPreviewer)getActivity().findViewById(R.id.surfaceView_screenCapPreview);
        if(binding.surfaceViewScreenCapPreview!=null) {
            SurfaceHolder surfaceHolder = mEGLSurfaceView.getHolder();
            surfaceHolder.addCallback(mPreviewSurfaceCallback);
            //surfaceHolder.setFormat(PixelFormat.TRANSPARENT);
            //mEGLSurfaceView.setZOrderOnTop(true);
            //mEGLSurfaceView.setZOrderMediaOverlay(true);
        }

        // 开始录制按键
        mBtStartCap = binding.btStartCap;//(Button)getActivity().findViewById(R.id.btStartCap);
        if(mBtStartCap!=null) {
            mBtStartCap.setOnClickListener(v -> {
                if (!mRecorderStarted) {
                    if (hasExternalStoragePermissions()) {
                        startCapture();
                    } else {
                        requestPermissions();
                    }
                } else {
                    Log.i("ScreenCap:", "click button to stop recorder");
                    doStopRecordeCapture();
                }
            });
        }

        // 是否勾选预览CheckBox
        CheckBox cbIsPreview = binding.cbPreview;//getActivity().findViewById(R.id.cbPreview);
        if(cbIsPreview!=null) {
            cbIsPreview.setOnCheckedChangeListener((buttonView, isChecked) -> {
                mIsPreview = isChecked;
                if (mIsPreview) {
                    doStopRecordeCapture();
                    startCapture();
                } else {
                    stopCapture();
                }
            });
        }

        binding.cbPreviewCam.setOnCheckedChangeListener((buttonView, isChecked) -> {
            mIsPreview = isChecked;
            if (mIsPreview) {
                CameraUtils.openFrontCamera(30);
                if(mCameraPreviewSurfaceTexture!=null) {
                    CameraUtils.setPreviewTexture(mCameraPreviewSurfaceTexture);
                    startPreview(mPreviewSurfaceWidth, mPreviewSurfaceHeight);
                }
            } else {
                CameraUtils.setPreviewTexture(null);
                CameraUtils.stopPreview();
                CameraUtils.releaseCamera();
            }
        });

        Log.i(TAG, "onViewCreated");
    }

    @Override
    public void onPause() {
        super.onPause();
        if(mRender!=null)
            mRender.pause();
        Log.i(TAG, "onPause");
    }

    @Override
    public void onStop() {
        super.onStop();
        Log.i(TAG, "onStop");
    }

    @Override
    public void onResume() {
        super.onResume();
        if(mRender!=null)
            mRender.resume();
        Log.i(TAG, "onResume");
    }

    private SurfaceHolder.Callback mPreviewSurfaceCallback = new SurfaceHolder.Callback() {
        @Override
        public void surfaceCreated(@NonNull SurfaceHolder holder) {
            mPreviewSurface = holder.getSurface();
            initVideoRender(mPreviewSurface);
        }

        @Override
        public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
            mPreviewSurface = holder.getSurface();
            mPreviewSurfaceWidth = width;
            mPreviewSurfaceHeight = height;
            Log.i(TAG, "surfaceChanged surface="+holder.getSurface().toString());
            if(mRender!=null) {
                mRender.resizeTarget(holder.getSurface(), width, height);
                //mRender.drawFrame();
            }
        }

        @Override
        public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
            Log.i(TAG, "surfaceDestroyed surface="+holder.getSurface().toString());
            if(mRender!=null) {
                mRender.resizeTarget(null, 0, 0);
            }
        }

//        @Override
//        public void surfaceRedrawNeeded(SurfaceHolder surfaceHolder) {
//            //mRender.draw();
//            Log.i(TAG, "surfaceRedrawNeeded surface="+surfaceHolder.toString());
//        }
//
//        @Override
//        public void surfaceRedrawNeededAsync(SurfaceHolder holder, Runnable drawingFinished) {
//            Log.i(TAG, "surfaceRedrawNeededAsync surface="+holder.getSurface().toString());
//            if(mRender!=null)
//                mRender.drawFrame();
//        }

    };

    void doStopRecordeCapture()
    {
        stopCapture();
        releaseEncoder();
        //releaseMuxer();
        mBtStartCap.setText("开始录制");
    }

    ///////////////////////////摄像头预览///////////////////////////////////////////////////////
    private boolean checkCameraHardware(Context context)
    {
        if(context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA)){
            return true;
        } else {
            return false;
        }
    }

    private void startPreview(int width,int height){
        //根据设置的宽高 和手机支持的分辨率对比计算出合适的宽高算法
        CameraUtils.setPreviewSize(width, height);
        WindowManager wm = (WindowManager)this.getContext().getSystemService(Context.WINDOW_SERVICE);
        if(wm==null)
            return;
        int displayRotation = wm.getDefaultDisplay().getRotation();
        int cameraRotation = CameraUtils.calculateCameraPreviewOrientation(displayRotation);
        CameraUtils.setPreviewOrientation(cameraRotation);

//        Camera.CameraInfo info = new Camera.CameraInfo();
//        android.hardware.Camera.getCameraInfo(mCamerId, info);
//        int rotation = getDisplayRotationDegree();
//
//        int result;
//        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
//            result = (info.orientation + rotation) % 360;
//            result = (360 - result) % 360;  // compensate the mirror
//        } else {  // back-facing
//            result = (info.orientation - rotation + 360) % 360;
//        }
//        mCamera.setDisplayOrientation(result);



//        Camera.Parameters parameters = mCamera.getParameters();
//        parameters.setPreviewFormat(ImageFormat.NV21);
//        //根据设置的宽高 和手机支持的分辨率对比计算出合适的宽高算法
//        Camera.Size size = CameraUtils.calculatePerfectSize(parameters.getSupportedPreviewSizes(),
//                width, height);
//
//        parameters.setPreviewSize(size.width, size.height);
        //设置照片尺寸
        CameraUtils.setPictureSize(1920, 1080);
        //设置实时对焦 部分手机不支持会crash

        //parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
//        mCamera.setParameters(parameters);
        //开启预览
        CameraUtils.startPreview();
    }
    //////////////////////////////////gl render////////////////////////////////////////////////
    private GLRender mRender = null;
    private GLRender.Callback mRenderCallback = new GLRender.Callback() {
        @Override
        public void onInitResult(boolean isSuccess) {
            Log.i(TAG, "onInitResult isSuccess="+isSuccess);
            createRectForScreenCap();
            createSurfaceTextureAndRectForCamera();
        }

        @Override
        public void onDeinit() {
            Log.i(TAG, "onDeinit");
        }

    };

    private void initVideoRender(Surface surface)
    {
        if(mRender==null)
        {
            Log.i(TAG, "initVideoRender");
            mRender = new GLRender();
            mRender.setCallback(mRenderCallback);
            mRender.init();
        }
    }

    private void deinitVideoRender()
    {
        if(mRender!=null)
        {
            Log.i(TAG, "deinitVideoRender");
            mRender.deinit();
            mRender = null;
        }
    }

    // 一段写死的测试代码，在画面中显示一张图片
    void addRectTestShowPicture()
    {
        if(mRender==null)
            return;
        try {
            String state = Environment.getExternalStorageState();
            if(state.equals(Environment.MEDIA_MOUNTED))
            {
                String pathName =  Environment.getExternalStorageDirectory().getAbsolutePath() + "/" + Environment.DIRECTORY_DCIM + "/" + "Camera/tmp.png";
                FileInputStream inputStream = new FileInputStream(pathName);
                Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                RectSpirit2d rect = new RectSpirit2d(RectSpirit2d.ProgramType.TEXTURE_2D);
                rect.setTextRotation(0, false, true);
                rect.setSourceImage(bitmap);
                mRender.addElem(rect);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    SurfaceTexture mScreenCapPreviewSurfaceTexture;
    RectSpirit2d mRectScreenCapPreview = null;
    void createRectForScreenCap()
    {
        if(mRender==null)
            return;
        mRender.createTexture(new GLRender.TextureParams(720, 1280, GLES20.GL_RGBA, new GLRender.CreateTextureResultCallback() {
            @Override
            public void onCreateResult(boolean isSuccess, int textureId) {
                Log.i(TAG, "Texture for preview screen capture is created.isSucess="+ isSuccess+" TextureId="+textureId);
                if(textureId!=0 && isSuccess) {
                    SurfaceTexture st = new SurfaceTexture(textureId);
                    st.setDefaultBufferSize(720, 1280);
                    st.setOnFrameAvailableListener(new SurfaceTexture.OnFrameAvailableListener() {
                        @Override
                        public void onFrameAvailable(SurfaceTexture surfaceTexture) {
                            mRectScreenCapPreview.notifyTextureFrameAvailable(surfaceTexture);
                            mRender.drawFrame();
                        }
                    });
                    Surface sf = new Surface(st);
                    mScreenCapPreviewSurfaceTexture = st;
                    mPreviewSurface = sf;
                    mPreviewSurfaceWidth = 720;
                    mPreviewSurfaceHeight = 1280;

                    //RectSpirit2d rect = new RectSpirit2d(RectSpirit2d.ProgramType.TEXTURE_2D);
                    RectSpirit2d rect = new RectSpirit2d(RectSpirit2d.ProgramType.TEXTURE_EXT);
                    rect.setTextRotation(0, false, true);
                    rect.setSourceTexture(textureId);
                    rect.move(-1.0f, 0.5f, 1.0f, 1.0f);
                    mRender.addElem(rect);
                    mRectScreenCapPreview = rect;
                }
            }
        }));
    }

    SurfaceTexture mCameraPreviewSurfaceTexture;
    RectSpirit2d mRectCameraPreview = null;
    Surface mPreviewSurfaceCamera = null;
    int mPreviewSurfaceCameraWidth = 0;
    int mPreviewSurfaceCameraHeight = 0;
    void createSurfaceTextureAndRectForCamera()
    {
        if(mRender==null)
            return;
        mRender.createTexture(new GLRender.TextureParams(720, 1280, GLES20.GL_RGBA, new GLRender.CreateTextureResultCallback() {
            @Override
            public void onCreateResult(boolean isSuccess, int textureId) {
                Log.i(TAG, "Texture for preview screen capture is created.isSucess="+ isSuccess+" TextureId="+textureId);
                if(textureId!=0 && isSuccess) {
                    SurfaceTexture st = new SurfaceTexture(textureId);
                    st.setDefaultBufferSize(720, 1280);
                    st.setOnFrameAvailableListener(new SurfaceTexture.OnFrameAvailableListener() {
                        @Override
                        public void onFrameAvailable(SurfaceTexture surfaceTexture) {
                            mRectCameraPreview.notifyTextureFrameAvailable(surfaceTexture);
                            mRender.drawFrame();
                        }
                    });
                    Surface sf = new Surface(st);
                    mCameraPreviewSurfaceTexture = st;
                    mPreviewSurfaceCamera = sf;
                    mPreviewSurfaceCameraWidth = 720;
                    mPreviewSurfaceCameraHeight = 1280;

                    //RectSpirit2d rect = new RectSpirit2d(RectSpirit2d.ProgramType.TEXTURE_2D);
                    RectSpirit2d rect = new RectSpirit2d(RectSpirit2d.ProgramType.TEXTURE_EXT);
                    rect.setTextRotation(0, false, true);
                    rect.setSourceTexture(textureId);
                    rect.move(-0.0f, 0.5f, 1.0f, 1.0f);
                    mRender.addElem(rect);
                    mRectCameraPreview = rect;
                }
            }
        }));
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
        new AlertDialog.Builder(this.getContext())
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
        mediaProjectionManager = (MediaProjectionManager)getActivity().getSystemService(MEDIA_PROJECTION_SERVICE);
        Intent intent = mediaProjectionManager.createScreenCaptureIntent();
        startActivityForResult(intent, RECORD_REQUEST_CODE);
        Log.i("ScreenCap:", "start request MediaProjection permission.");
    }

    void stopCapture()
    {
        Intent svc = new Intent(this.getContext(), ScreenCapService.class);
        getActivity().stopService(svc);
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

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
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
                Intent svc = new Intent(this.getContext(), ScreenCapService.class);
                svc.putExtra("data", data);
                svc.putExtra("resultCode", resultCode);
                svc.putExtra("surface", mCapSurface);
                svc.putExtra("width", mCapSurfaceWidth);
                svc.putExtra("height", mCapSurfaceHeight);
                getActivity().startForegroundService(svc);
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
        PackageManager pm = getActivity().getPackageManager();
        String packageName = getActivity().getPackageName();
        int granted = pm.checkPermission(WRITE_EXTERNAL_STORAGE, packageName);
        return granted == PackageManager.PERMISSION_GRANTED;
    }



}