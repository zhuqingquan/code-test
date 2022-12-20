package com.example.testadrscreencapture.ui.dashboard;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.example.testadrscreencapture.glrender.GLRender;
import com.example.testadrscreencapture.glrender.RectSpirit2d;

import java.io.FileInputStream;
import java.io.IOException;


public class EGLSurfaceView extends SurfaceView implements SurfaceHolder.Callback2{
    static final String TAG = "EGLSurfaceView";
    //private EGLSurfaceView mEGLSurfaceView = null;
    private Surface mTargetSurfaceWin = null;
    private GLRender mRender = null;

    private GLRender.Callback mRenderCallback = new GLRender.Callback() {
        @Override
        public void onInitResult(boolean isSuccess) {
            Log.i(TAG, "onInitResult isSuccess="+isSuccess);
        }

        @Override
        public void onDeinit() {
            Log.i(TAG, "onDeinit");
        }
    };

    public EGLSurfaceView(Context context) {
        super(context);
        getHolder().setType(SurfaceHolder.SURFACE_TYPE_GPU);
        setZOrderOnTop(true);
        //setZOrderMediaOverlay(true);
        this.getHolder().addCallback(this);
    }

    public EGLSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        getHolder().setType(SurfaceHolder.SURFACE_TYPE_GPU);
        setZOrderOnTop(true);
        //setZOrderMediaOverlay(true);
        this.getHolder().addCallback(this);
    }

    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        Log.i(TAG, "surfaceCreated surface="+surfaceHolder.getSurface().toString());
        mTargetSurfaceWin = surfaceHolder.getSurface();
        initVideoRender(mTargetSurfaceWin);
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int format, int width, int height) {
        Log.i(TAG, "surfaceChanged surface="+surfaceHolder.getSurface().toString());
        if(mRender!=null) {
            mTargetSurfaceWin = surfaceHolder.getSurface();
            mRender.resizeTarget(surfaceHolder.getSurface(), width, height);

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
                    //mRender.addRect(bitmap);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {
        Log.i(TAG, "surfaceDestroyed surface="+surfaceHolder.getSurface().toString());
        if(mRender!=null) {
            mRender.resizeTarget(null, 0, 0);
        }
    }

    @Override
    public void surfaceRedrawNeeded(SurfaceHolder surfaceHolder) {
        //mRender.draw();
        Log.i(TAG, "surfaceRedrawNeeded surface="+surfaceHolder.toString());
    }

    @Override
    public void surfaceRedrawNeededAsync(SurfaceHolder holder, Runnable drawingFinished) {
        Log.i(TAG, "surfaceRedrawNeededAsync surface="+holder.getSurface().toString());
        if(mRender!=null)
            mRender.drawFrame();
    }

    public void pauseRender() {
        if(mRender!=null)
            mRender.pause();
    }

    public void resumeRender() {
        if(mRender!=null)
            mRender.resume();
    }

    public void stopRender()
    {
        deinitVideoRender();
    }

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
}
