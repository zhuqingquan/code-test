package com.example.testadrscreencapture;

import android.app.Activity;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.os.Environment;
import android.view.Surface;
import android.view.SurfaceHolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

/**
 * 摄像头工具类
 * 主要完成打开一个摄像头（因为安卓中同时只支持打开一个摄像头）
 * 并支持在前置与后摄像头之间切换
 */
public class CameraUtils {

    // 相机默认宽高，相机的宽度和高度跟屏幕坐标不一样，手机屏幕的宽度和高度是反过来的。
    public static final int DEFAULT_WIDTH = 1280;
    public static final int DEFAULT_HEIGHT = 720;
    public static final int DESIRED_PREVIEW_FPS = 30;

    private static int mCameraID = -1;
    private static Camera mCamera;
    private static int mCameraPreviewFps;
    private static int mOrientation = 0;
    private static int mPreviewWidth = 0;
    private static int mPreviewHeight = 0;
    private static int mPreviewPixFormat = 0;

    private static boolean mNeedCallbackBuffer = true;          // 是否设置回调以获取预览的数据byte[]
    private static boolean mNeedCallbackBufferDump = false;      // 是否将回调获取到的预览数据byte[]写入到文件中查看
    private static PreviewDataHandler mPreDataHandler = null;

    /**
     * 打开相机，默认打开前置相机
     * @param expectFps
     */
    public static void openFrontCamera(int expectFps) {
        if (mCamera != null) {
            throw new RuntimeException("camera already initialized!");
        }
        Camera.CameraInfo info = new Camera.CameraInfo();
        int numCameras = Camera.getNumberOfCameras();
        for (int i = 0; i < numCameras; i++) {
            Camera.getCameraInfo(i, info);
            if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                openCamera(i, expectFps);
                return;
            }
        }
        // 没有摄像头时，抛出异常
        if (mCamera == null) {
            throw new RuntimeException("Unable to open camera");
        }
    }

    /**
     * 根据ID打开相机
     * @param cameraID Camera的ID，根据Camera总数来定义，[0,count)
     * @param expectFps 帧率。预期值，实际可能因为摄像头不支持设置的值而生效的实际帧率不一样
     */
    public static void openCamera(int cameraID, int expectFps) {
        if (mCamera != null) {
            throw new RuntimeException("camera already initialized!");
        }
        mCamera = Camera.open(cameraID);
        if (mCamera == null) {
            throw new RuntimeException("Unable to open camera");
        }
        mCameraID = cameraID;
        Camera.Parameters parameters = mCamera.getParameters();
        mCameraPreviewFps = CameraUtils.chooseFixedPreviewFps(parameters, expectFps * 1000);
        parameters.setRecordingHint(true);
        mCamera.setParameters(parameters);
        setPreviewSize(CameraUtils.DEFAULT_WIDTH, CameraUtils.DEFAULT_HEIGHT);
        setPictureSize(CameraUtils.DEFAULT_WIDTH, CameraUtils.DEFAULT_HEIGHT);
        mCamera.setDisplayOrientation(mOrientation);
    }

    /**
     * 切换相机
     * @param cameraID
     */
    public static void switchCamera(int cameraID, SurfaceHolder holder) {
        if (mCameraID == cameraID) {
            return;
        }
        // 释放原来的相机
        releaseCamera();
        // 打开相机
        openCamera(cameraID, CameraUtils.DESIRED_PREVIEW_FPS);
        // 打开预览
        setPreviewDisplay(holder);
        startPreview();     //TODO 可能不需要start？或者根据当前的preview状态判断是否要start
    }

    /**
     * 释放相机
     */
    public static void releaseCamera() {
        if (mCamera != null) {
            mCamera.stopPreview();
            mCamera.release();
            mCamera = null;
            mCameraID = -1;
        }
    }

    /**
     * 设置预览摄像头画面的Surface
     * @param holder Surface的Holer对象
     */
    public static void setPreviewDisplay(SurfaceHolder holder) {
        if (mCamera == null) {
            throw new IllegalStateException("Camera must be set when start preview");
        }
        try {
            mCamera.setPreviewDisplay(holder);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 设置预览的Texture对象
     * @param texture
     */
    public static void setPreviewTexture(SurfaceTexture texture)
    {
        if (mCamera == null) {
            throw new IllegalStateException("Camera must be set when start preview");
        }
        try {
            mCamera.setPreviewTexture(texture);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    /**
     * 设置预览大小
     * @param expectWidth 期望的宽，实际预览的宽高将在设置支持的列表中选择与期望值最匹配的一项
     * @param expectHeight 期望的高，实际预览的宽高将在设置支持的列表中选择与期望值最匹配的一项
     */
    public static void setPreviewSize(int expectWidth, int expectHeight) {
        setPreviewSize(expectWidth, expectHeight, ImageFormat.NV21);
    }

    /**
     * 设置预览大小
     * @param expectWidth 期望的宽，实际预览的宽高将在设置支持的列表中选择与期望值最匹配的一项
     * @param expectHeight 期望的高，实际预览的宽高将在设置支持的列表中选择与期望值最匹配的一项
     * @param format 预览的像素格式，参考 ImageFormat
     */
    public static void setPreviewSize(int expectWidth, int expectHeight, int format) {
        Camera.Parameters parameters = mCamera.getParameters();
        Camera.Size size = calculatePerfectSize(parameters.getSupportedPreviewSizes(),
                expectWidth, expectHeight);
        parameters.setPreviewSize(size.width, size.height);
        parameters.setPreviewFormat(format);
        mCamera.setParameters(parameters);
        mPreviewWidth = size.width;
        mPreviewHeight = size.height;
        mPreviewPixFormat = format;
    }

    /**
     * 开始预览
     */
    public static void startPreview() {
        if (mCamera != null) {
            mCamera.startPreview();
            if(mNeedCallbackBuffer && mPreDataHandler==null)
            {
                mPreDataHandler = new PreviewDataHandler(mNeedCallbackBufferDump);
                mCamera.setPreviewCallback(mPreDataHandler);
            }
        }
    }

    /**
     * 停止预览
     */
    public static void stopPreview() {
        if (mCamera != null) {
            mCamera.stopPreview();
        }
        if(mPreDataHandler!=null)
        {
            mPreDataHandler.onStopPreview();
        }
    }

    /**
     * 拍照
     */
    public static void takePicture(Camera.ShutterCallback shutterCallback,
                                   Camera.PictureCallback rawCallback,
                                   Camera.PictureCallback pictureCallback) {
        if (mCamera != null) {
            mCamera.takePicture(shutterCallback, rawCallback, pictureCallback);
        }
    }

    /**
     * 获取预览大小
     * @return
     */
    public static Camera.Size getPreviewSize() {
        if (mCamera != null) {
            return mCamera.getParameters().getPreviewSize();
        }
        return null;
    }

    /**
     * 设置拍摄的照片大小
     * @param expectWidth 期望的宽，实际预览的宽高将在设置支持的列表中选择与期望值最匹配的一项
     * @param expectHeight 期望的高，实际预览的宽高将在设置支持的列表中选择与期望值最匹配的一项
     */
    public static void setPictureSize(int expectWidth, int expectHeight) {
        if(mCamera==null)
        {
            throw new RuntimeException("Camera is not open yet");
        }
        Camera.Parameters parameters = mCamera.getParameters();
        Camera.Size size = calculatePerfectSize(parameters.getSupportedPictureSizes(),
                expectWidth, expectHeight);
        parameters.setPictureSize(size.width, size.height);
        mCamera.setParameters(parameters);
    }

    /**
     * 获取照片大小
     * @return
     */
    public static Camera.Size getPictureSize() {
        if (mCamera != null) {
            return mCamera.getParameters().getPictureSize();
        }
        return null;
    }

    /**
     * 计算最完美的Size
     * @param sizes
     * @param expectWidth
     * @param expectHeight
     * @return
     */
    public static Camera.Size calculatePerfectSize(List<Camera.Size> sizes, int expectWidth,
                                                   int expectHeight) {
        sortList(sizes); // 根据宽度进行排序
        Camera.Size result = sizes.get(0);
        boolean widthOrHeight = false; // 判断存在宽或高相等的Size
        // 辗转计算宽高最接近的值
        for (Camera.Size size: sizes) {
            // 如果宽高相等，则直接返回
            if (size.width == expectWidth && size.height == expectHeight) {
                result = size;
                break;
            }
            // 仅仅是宽度相等，计算高度最接近的size
            if (size.width == expectWidth) {
                widthOrHeight = true;
                if (Math.abs(result.height - expectHeight)
                        > Math.abs(size.height - expectHeight)) {
                    result = size;
                }
            }
            // 高度相等，则计算宽度最接近的Size
            else if (size.height == expectHeight) {
                widthOrHeight = true;
                if (Math.abs(result.width - expectWidth)
                        > Math.abs(size.width - expectWidth)) {
                    result = size;
                }
            }
            // 如果之前的查找不存在宽或高相等的情况，则计算宽度和高度都最接近的期望值的Size
            else if (!widthOrHeight) {
                if (Math.abs(result.width - expectWidth)
                        > Math.abs(size.width - expectWidth)
                        && Math.abs(result.height - expectHeight)
                        > Math.abs(size.height - expectHeight)) {
                    result = size;
                }
            }
        }
        return result;
    }

    /**
     * 排序
     * @param list
     */
    private static void sortList(List<Camera.Size> list) {
        Collections.sort(list, new java.util.Comparator<Camera.Size>() {
            @Override
            public int compare(Camera.Size pre, Camera.Size after) {
                if (pre.width > after.width) {
                    return 1;
                } else if (pre.width < after.width) {
                    return -1;
                }
                return 0;
            }
        });
    }

    /**
     * 选择合适的FPS
     * @param parameters
     * @param expectedThoudandFps 期望的FPS
     * @return
     */
    private static int chooseFixedPreviewFps(Camera.Parameters parameters, int expectedThoudandFps) {
        List<int[]> supportedFps = parameters.getSupportedPreviewFpsRange();
        for (int[] entry : supportedFps) {
            if (entry[0] == entry[1] && entry[0] == expectedThoudandFps) {
                parameters.setPreviewFpsRange(entry[0], entry[1]);
                return entry[0];
            }
        }
        int[] temp = new int[2];
        int guess;
        parameters.getPreviewFpsRange(temp);
        if (temp[0] == temp[1]) {
            guess = temp[0];
        } else {
            guess = temp[1] / 2;
        }
        return guess;
    }

    /**
     * 设置预览角度，setDisplayOrientation本身只能改变预览的角度
     * previewFrameCallback以及拍摄出来的照片是不会发生改变的，拍摄出来的照片角度依旧不正常的
     * 拍摄的照片需要自行处理
     * 这里Nexus5X的相机简直没法吐槽，后置摄像头倒置了，切换摄像头之后就出现问题了。
     * @param displayRotation 当前手机屏幕的旋转角度
     */
    public static int calculateCameraPreviewOrientation(int displayRotation) {
        Camera.CameraInfo info = new Camera.CameraInfo();
        Camera.getCameraInfo(mCameraID, info);
        int degrees = 0;
        switch (displayRotation) {
            case Surface.ROTATION_0:
                degrees = 0;
                break;
            case Surface.ROTATION_90:
                degrees = 90;
                break;
            case Surface.ROTATION_180:
                degrees = 180;
                break;
            case Surface.ROTATION_270:
                degrees = 270;
                break;
        }

        int result;
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360;
            result = (360 - result) % 360;
        } else {
            result = (info.orientation - degrees + 360) % 360;
        }
        return result;
    }


    /**
     * 获取当前的Camera ID
     * @return
     */
    public static int getCameraID() {
        return mCameraID;
    }

    /**
     * 获取当前预览的角度
     */
    public static int getPreviewOrientation() {
        return mOrientation;
    }

    public static void setPreviewOrientation(int orientation)
    {
        mOrientation = orientation;
        if(mCamera!=null)
        {
            mCamera.setDisplayOrientation(orientation);
        }
    }

    /**
     * 获取FPS（千秒值）
     */
    public static int getCameraPreviewThousandFps() {
        return mCameraPreviewFps;
    }

    /**
     * 获取摄像头总数
     * @return 摄像头总数
     */
    private static int sCameraCount = -1;
    public static int GetCameraCount() {
        if(sCameraCount!=-1)
            return sCameraCount;

        try {
            Class<?> cameraClass = Class.forName("android.hardware.Camera");
            Method getCameraCount = cameraClass.getMethod("getNumberOfCameras");
            if (getCameraCount != null) {
                sCameraCount = (Integer) getCameraCount.invoke(null, (Object[]) null);
            }
        } catch (Exception e) {
            return -1;
        }
        return sCameraCount;
    }

    /**
     * 根据Camera.Parameters判断Camera是否支持torch（手电）
     * @param params
     * @return
     */
    private static boolean supportTorch(Camera.Parameters params) {
        boolean result = false;
        List<String> flashModes = params.getSupportedFlashModes();
        if (flashModes != null) {
            int count = flashModes.size();
            for (int i = 0; i < count; ++i) {
                String flashMode = flashModes.get(i);
                if (flashMode.contains("torch")) {
                    result = true;
                    break;
                }
            }
        }
        return result;
    }

    public static int getZoom() {
        if (mCamera == null) {
            return 0;
        }
        return mCamera.getParameters().getZoom();
    }

    public static int getMaxZoom() {
        if (mCamera == null) {
            return 0;
        }
        return mCamera.getParameters().getMaxZoom();
    }

    public static boolean isZoomSupported() {
        if (mCamera == null) {
            return false;
        }
        return mCamera.getParameters().isZoomSupported();
    }

    public static void setZoom(int value) {
        if (mCamera == null) {
            return;
        }
        try {
            Camera.Parameters params = mCamera.getParameters();
            params.setZoom(value);
            mCamera.setParameters(params);
        } catch (Throwable t) {
        }
    }

    static class PreviewDataHandler implements Camera.PreviewCallback {
        FileOutputStream fop = null;
        boolean mDumpToFile = false;

        public PreviewDataHandler(boolean dumpToFile)
        {
            mDumpToFile = dumpToFile;
        }

        @Override
        public void onPreviewFrame(byte[] bytes, Camera camera) {
            int bitPerPixel = ImageFormat.getBitsPerPixel(mPreviewPixFormat);
            int expLength = mPreviewHeight * mPreviewWidth * bitPerPixel / 8;

            if(mDumpToFile)
            {
                // write data to file
                try {
                    if(fop==null) {
                        String path = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + "testCame_" + mPreviewWidth + "x" + mPreviewHeight + ".yuv";
                        File file = new File(path);
                        // if file doesnt exists, then create it
                        if (!file.exists()) {
                            file.createNewFile();
                        }
                        fop = new FileOutputStream(file, true);
                    }

                    fop.write(bytes);
                    fop.flush();

                    System.out.println("Done - expLength=" + expLength + " format=" + mPreviewPixFormat);

                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        void onStopPreview()
        {
            try {
                if (fop != null) {
                    fop.close();
                    fop = null;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
