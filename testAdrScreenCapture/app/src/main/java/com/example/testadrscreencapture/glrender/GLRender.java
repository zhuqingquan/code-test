package com.example.testadrscreencapture.glrender;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import android.view.Surface;
import java.lang.ref.WeakReference;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 使用OpenGL ES进行视频或者图片的渲染
 */
public class GLRender {
    static private final String TAG = "GLRender";
    private Object mTargetSurface = null;      // 显示渲染结果的Surface
    private EglCore mEGL = null;                // EGL context
    private EGLSurface mWindowSurface;          // EGLSurface for window render
    private EGLSurface mOffscreenSurface;       // EGLSurface for offscreen render
    private int mTargetWidth;                   // width of render target
    private int mTargetHeight;                   // height of render target
    private RenderThread mRenderThread = null;  // 渲染线程
    private RenderThreadHandler mRenderThreadHandler = null;    // RenderThreadHandler对象
    private Callback mCallback = null;

    static final int RENDER_STATE_UNINIT = 0;       // 未初始化，未调用init或者调用了init但是创建EGL context失败了
    //static final int RENDER_STATE_INITED = 1;
    static final int RENDER_STATE_RUNNING = 1;      // 渲染中，init成功后将进入此状态，即使RenderTarget未创建也是Running状态
    static final int RENDER_STATE_PAUSED = 2;       // 暂停状态，调用pause()之后进入此状态，调用resume()之后退出此状态进入RUNNING状态
    private AtomicInteger mRenderState = new AtomicInteger(RENDER_STATE_UNINIT);     // 当前Render的状态

    private List<DrawableElem> mAllElems = new LinkedList<>();

    /**
     * GLRender的渲染状态或者事件回调
     */
    static public abstract class Callback{
        /**
         * GLRender的真实初始化结果。
         * 用户调用init接口后将会异步进行初始化，通过此接口回调初始化的结果
         * @param isSuccess 是否成功初始化
         */
        public abstract void onInitResult(boolean isSuccess);

        /**
         * GLRender已经反初始化完成，不再可用
         */
        public abstract void onDeinit();
    }

    /**
     * 构造函数，此构造方法构造的GLRender内部将创建独立的EGL context以及渲染线程
     */
    public GLRender()
    {
        Log.i(TAG, "GLRender construct");
    }

    public GLRender(EglCore egl, Handler renderThreadHandler)
    {
        Log.i(TAG, "GLRender construct2");
        if(renderThreadHandler.getClass()!=RenderThreadHandler.class)
            throw new IllegalArgumentException("handler must be typeof RenderThreadHandler. can get from GLRender.getHandler");
        mEGL = egl;
        mRenderThreadHandler = (RenderThreadHandler)renderThreadHandler;
    }

    public EglCore getEGL() { return mEGL; }
    public Handler getHandler() { return mRenderThreadHandler; }
    public void setCallback(Callback glRenderCallback) { mCallback = glRenderCallback; }

    public boolean init() {
        if(mRenderState.get()!=RENDER_STATE_UNINIT) {
            Log.w(TAG, "init do nothing. current Render state="+mRenderState.get());
            return true;
        }
        startRenderThread();
        if(mRenderThreadHandler==null)
            return false;
        // sendMessage to init
        mRenderThreadHandler.sendMessage(mRenderThreadHandler.obtainMessage(RenderThreadHandler.MSG_INIT_CONTEXT));
        return true;
    }

    /**
     * 传入RenderTarget的Surface初始化当前对象渲染环境
     * 使用此对象渲染的内容将呈现在传入的Surface对象中
     * @param targetSurface 作为RenderTarget的Surface
     * @return true--成功 false--初始化失败
     */
    public boolean setRenderTarget(Surface targetSurface) {
        if(targetSurface==null)
            return false;
        Log.i(TAG, "init targetSurface"+targetSurface.toString());
        startRenderThread();
        if(mRenderThreadHandler==null)
            return false;
        // sendMessage to init
        mRenderThreadHandler.sendMessage(mRenderThreadHandler.obtainMessage(RenderThreadHandler.MSG_RESIZE_TARGET, 0, 0, targetSurface));

        mRenderThreadHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                mRenderThreadHandler.sendMessage(mRenderThreadHandler.obtainMessage(RenderThreadHandler.MSG_DRAW));
            }
        }, 1000);
        return true;
    }

    /**
     * 创建离屏Texture作为RenderTarget并初始化渲染环境
     * @param width 离屏Texture的宽
     * @param height 离屏Texture的高
     * @return true--成功 false--初始化失败
     */
    public boolean setRenderTarget(int width, int height) {
        Log.i(TAG, "init width="+width+" height="+height);
        startRenderThread();
        if(mRenderThreadHandler==null)
            return false;
        // sendMessage to init
        mRenderThreadHandler.sendMessage(mRenderThreadHandler.obtainMessage(RenderThreadHandler.MSG_RESIZE_TARGET, width, height));
        return true;
    }

    void initEGL()
    {
        if(mEGL!=null)
            return;
        Log.i(TAG, "initEGL ");
        mEGL = new EglCore(null, EglCore.FLAG_TRY_GLES3);
        mRenderState.set(RENDER_STATE_RUNNING);
    }

    void createTargetSurface(Object surface)
    {
        EGLSurface eglSurface = mEGL.createWindowSurface(surface);
        mEGL.makeCurrent(eglSurface);
        GlUtil.checkGlError("makeCurrent");
        mWindowSurface = eglSurface;
        mTargetSurface = surface;
        mTargetWidth = mEGL.querySurface(eglSurface, EGL14.EGL_WIDTH);
        mTargetHeight = mEGL.querySurface(eglSurface, EGL14.EGL_HEIGHT);
        Log.i(TAG, "createTargetSurface width="+mTargetWidth+" height="+mTargetHeight+" surface="+mTargetSurface.toString()+" EGLSurface="+mWindowSurface);
    }

//    void initTargetSurface(SurfaceTexture surface)
//    {
//        EGLSurface eglSurface = mEGL.createWindowSurface(surface);
//        mWindowSurface = eglSurface;
//    }

    void createTargetSurface(int width, int height)
    {
        EGLSurface eglSurface = mEGL.createOffscreenSurface(width, height);
        mOffscreenSurface = eglSurface;
        mTargetWidth = width;
        mTargetHeight = height;
        Log.i(TAG, "createTargetSurface width="+width+" height"+height+" EGLSurface="+eglSurface);
    }

    void callbackInitResult()
    {
        if(mCallback!=null)
        {
            boolean isSuc = mEGL!=null;
            mCallback.onInitResult(isSuc);
        }
    }

    /**
     * 停止渲染，释放资源
     */
    public void deinit() {
        Log.i(TAG, "deinit ");
        mRenderThreadHandler.sendMessage(mRenderThreadHandler.obtainMessage(RenderThreadHandler.MSG_DEINIT_CONTEXT));
        stopRenderThread();
    }


    void releaseEGL()
    {
        if(mEGL!=null)
        {
            Log.i(TAG, "releaseEGL ");
            mRenderState.set(RENDER_STATE_UNINIT);
            mEGL.release();
            mEGL = null;
            if(mCallback!=null)
                mCallback.onDeinit();
        }
    }

    void releaseTargetSurface()
    {
        Log.i(TAG, "releaseTargetSurface ");
        mEGL.makeNothingCurrent();
        if(mWindowSurface!=null) {
            mEGL.releaseSurface(mWindowSurface);
            mWindowSurface = null;
        }
        if(mOffscreenSurface!=null) {
            mEGL.releaseSurface(mOffscreenSurface);
            mOffscreenSurface = null;
        }
        mTargetSurface = null;
        mTargetWidth = 0;
        mTargetHeight = 0;
    }

    /**
     * 当作为RenderTarget的Surface/SurfaceTexture，或者离屏的Surface发生改变时需要调用此方法重新创建对应的EGLSurface
     * 当SurfaceView的大小发生改变，或者手机横竖屏切换时可能需要执行此操作
     * @param surface 用于创建EGLSurface的Surface或者SurfaceTexture对象
     * @param width 宽，当surface参数不为null，应传入surface的宽。否则为离屏EGLSurface的宽
     * @param height 高，当surface参数不为null，应传入surface的高。否则为离屏EGLSurface的高
     */
    public void resizeTarget(Object surface, int width, int height)
    {
        Log.i(TAG, "resizeTarget surface="+(surface==null ? "null" : surface.toString())+" width="+width+" height"+height);
        mRenderThreadHandler.sendMessage(mRenderThreadHandler.obtainMessage(RenderThreadHandler.MSG_RESIZE_TARGET, width, height, surface));
    }

    private void doResizeTarget(Object surface, int width, int height){
        if(surface!=null)
        {
            if(surface!=mTargetSurface || (width!=0 && width!=mTargetWidth) || (height!=0 && height!=mTargetHeight)){
                releaseTargetSurface();
                createTargetSurface(surface);
            }
        }
        else if(width!=0 && height!=0)
        {
            if(width!=mTargetWidth || height!=mTargetHeight) {
                releaseTargetSurface();
                createTargetSurface(width, height);
            }
        }
        else if(surface==null && width==0 && height==0) {
            releaseTargetSurface();
            Log.i(TAG, "releaseRenderTareget when resize rendertarget surface==null width==0 height==0");
        }
        else{
            Log.e(TAG, "doResizeTarget invalid param. Surface="+surface.toString()+" width="+width+" height"+height);
        }
    }

    private void startRenderThread() {
        // 如果构造GLRender对象时没有传入Handler，则在当前对象中启动线程，创建Handler
        if(mRenderThreadHandler!=null)
            return;
        if(mRenderThread==null) {
            mRenderThread = new RenderThread("GLRender Thread");
            mRenderThread.start();
            mRenderThreadHandler = new RenderThreadHandler(mRenderThread, this);
        }
    }

    private void stopRenderThread() {
        if(mRenderThread!=null)
        {
            mRenderThread.shutdown();
            mRenderThread = null;
        }
        mRenderThreadHandler = null;
    }

    /**
     * 暂停渲染，之前添加的渲染内容不会进行渲染
     * 但是渲染线程处于运行状态，且已创建的资源Context都不会释放
     */
    public void pause()
    {
        mRenderState.set(RENDER_STATE_PAUSED);
    }

    /**
     * 恢复渲染
     */
    public void resume()
    {
        mRenderState.set(RENDER_STATE_RUNNING);
    }

    /**
     * 添加需要渲染绘制的元素
     * @param elem 绘制的元素对象
     */
    public void addElem(DrawableElem elem)
    {
        mRenderThreadHandler.sendMessage(mRenderThreadHandler.obtainMessage(RenderThreadHandler.MSG_ADD_ELEM, elem));
    }

    /**
     * 移除已经添加的绘制元素
     * @param elem 绘制的元素对象
     */
    public void removeElem(DrawableElem elem) {
        mRenderThreadHandler.sendMessage(mRenderThreadHandler.obtainMessage(RenderThreadHandler.MSG_REMOVE_ELEM, elem));
    }

    private void doAddElem(DrawableElem elem)
    {
        if (elem != null) {
            mAllElems.add(elem);
        }
    }

    private void doRemoveElem(DrawableElem elem) {
        if (elem != null && mAllElems.contains(elem)) {
            mAllElems.remove(elem);
        }
    }

    // TODO 改成invalidate可能准确点？？？
    public void drawFrame()
    {
        mRenderThreadHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                mRenderThreadHandler.sendMessage(mRenderThreadHandler.obtainMessage(RenderThreadHandler.MSG_DRAW));
            }
        }, 1);
    }

    float backcolor_r = 0.0f;
    float backcolor_g = 0.0f;
    float backcolor_b = 0.0f;

    void draw(int backcolor)
    {
        mRenderThreadHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                mRenderThreadHandler.sendMessage(mRenderThreadHandler.obtainMessage(RenderThreadHandler.MSG_DRAW));
            }
        }, 1000);

        // EGL context未初始化或者RenderTarget未创建时进行渲染操作
        if(mEGL==null || (mWindowSurface==null && mOffscreenSurface==null))
            return;
        // 如果pause，也不再渲染
        if(mRenderState.get()!=RENDER_STATE_RUNNING)
            return;
        EGLSurface target = mWindowSurface!=null ? mWindowSurface : mOffscreenSurface;
//        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GlUtil.checkGlError("draw start");
        if(!mEGL.isCurrent(target))
        {
            mEGL.makeCurrent(target);
            GlUtil.checkGlError("makeCurrent");
        }

        int width = mEGL.querySurface(target, EGL14.EGL_WIDTH);
        int height = mEGL.querySurface(target, EGL14.EGL_HEIGHT);
        GLES20.glViewport(0, 0, width, height);
        GlUtil.checkGlError("glViewport");
        GLES20.glClearColor(1.0f, backcolor_g, 0.0f, 1.0f);
        GlUtil.checkGlError("glClearColor");
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GlUtil.checkGlError("glClear");

        // draw element in list
        for (DrawableElem elem : mAllElems) {
            if (elem != null)
                elem.draw();
        }

        boolean result = mEGL.swapBuffers(target);
        if (!result) {
            Log.d(TAG, "WARNING: swapBuffers() failed");
        }

        backcolor_g += 0.1;
        if(backcolor_g>1.0) backcolor_g = 0.0f;
    }

    private  class RenderThread extends HandlerThread
    {
        private boolean mShutdown = true;

        RenderThread(String threadName)
        {
            super(threadName);
        }

        /**
         * Shuts everything down.
         */
        private void shutdown() {
            Log.i(TAG, "shutdown");
            mShutdown = true;
            if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.JELLY_BEAN_MR2) {
                super.quitSafely();
            }
            else {
                super.quit();
            }
        }
    }

    /**
     * RenderThread线程的Handler
     */
    private static class RenderThreadHandler extends Handler {
        final static String TAG = "RenderThreadHandler";
        public static final int MSG_INIT_CONTEXT = 1;
        public static final int MSG_DEINIT_CONTEXT = 2;
        public static final int MSG_DRAW = 3;
        public static final int MSG_RESIZE_TARGET = 4;
        public static final int MSG_ADD_ELEM = 5;
        public static final int MSG_REMOVE_ELEM = 6;
        public static final int MSG_SET_PARM = 7;

        private WeakReference<RenderThread> mWeakRenderThread;
        private WeakReference<GLRender> mWeakRefGLRender;

        /**
         * Call from render thread.
         */
        public RenderThreadHandler(RenderThread rt, GLRender render) {
            super(rt.getLooper());
            mWeakRenderThread = new WeakReference<>(rt);
            mWeakRefGLRender = new WeakReference<>(render);
        }

        @Override
        public void handleMessage(Message msg) {
            RenderThread renderThread = mWeakRenderThread.get();
            GLRender glRender = mWeakRefGLRender.get();
            if (renderThread == null || glRender==null) {
                Log.w(TAG, "RenderThreadHandler.handleMessage: weak ref is null");
                return;
            }

            int what = msg.what;
            switch(what)
            {
                case MSG_INIT_CONTEXT:
                    glRender.initEGL();
                    glRender.callbackInitResult();
                    break;
                case MSG_DEINIT_CONTEXT:
                    glRender.releaseTargetSurface();
                    glRender.releaseEGL();
                    break;
                case MSG_RESIZE_TARGET:
                    glRender.doResizeTarget(msg.obj, msg.arg1, msg.arg2);
                    break;
                case MSG_DRAW:
                    glRender.draw(0xFF0000FF);
                    break;
                case MSG_ADD_ELEM:
                    glRender.doAddElem((DrawableElem) msg.obj);
                    break;
                case MSG_REMOVE_ELEM:
                    glRender.doRemoveElem((DrawableElem) msg.obj);
                    break;
                default:
                    break;
            }
            super.handleMessage(msg);
            Log.i(TAG, "handleMessage msg="+msg.what);
        }
    }
}
