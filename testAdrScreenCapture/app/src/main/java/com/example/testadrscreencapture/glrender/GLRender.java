package com.example.testadrscreencapture.glrender;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLContext;
import android.opengl.EGLSurface;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import android.view.Surface;

import java.io.File;
import java.io.FileOutputStream;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import static android.opengl.GLES20.GL_UNSIGNED_BYTE;

/**
 * 使用OpenGL ES进行视频或者图片的渲染
 */
public class GLRender {
    static private final String TAG = "GLRender";
    private Object mTargetSurface = null;      // 显示渲染结果的Surface
    private int mTargetTexture = -1;            // 作为RenderTarget的Texture的ID
    private TextureHolder mTargetTextureHolder; // RenderTarget Texture的Holder对象
    private int mFBO = -1;                      // FrameBuffer Object will bind to mTargetTexture
    private EglCore mEGL = null;                // EGL context
    private EGLContext mSharedContext = null;   // 共享的EGLContext
    private EGLSurface mWindowSurface;          // EGLSurface for window render
    private EGLSurface mOffscreenSurface;       // EGLSurface for offscreen render
    private int mTargetWidth;                   // width of render target
    private int mTargetHeight;                   // height of render target
    private int mTargetFormat = 0;              // Pixel format of render target

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

        /**
         * 当RenderTarget对象创建时通过此回调接口通知调用者
         * @param eglSurface 创建的作为RenderTarget的Surface
         * @param FBO 创建的作为RenderTarget的FBO
         * @param textureId 与创建的FBO绑定的Texture的ID
         */
        public void onRenderTargetCreated(Object eglSurface, int FBO, int textureId) {
        }

        /**
         * 当RenderTarget对象被释放时回调通知调用者
         */
        public void onRenderTargetDestroyed() {
        }

        /**
         * 当RenderTarget是FBO，完成一帧的渲染时将触发此回调
         * @param FBO FBO ID
         * @param textureId 与FBO绑定的texture的ID
         */
        public void onRenderTargetFrameAvailable(int FBO, TextureHolder textureId) {

        }
    }

    /**
     * 构造函数，此构造方法构造的GLRender内部将创建独立的EGL context以及渲染线程
     */
    public GLRender()
    {
        Log.i(TAG, "GLRender construct");
    }

    /**
     * 创建一个与当前GLRender共享EGLContext的对象
     * 此GLRender有独立的渲染线程。
     * @return GLRender对象。如果当前GLRender未初始化或者初始化失败，此调用将返回null
     */
    public GLRender createSharedGLRender() {
        if(mRenderState.get()==RENDER_STATE_UNINIT)
            return null;
        if(mEGL==null || mEGL.getContext()==null)
            return  null;
        GLRender render = new GLRender(mEGL.getContext());
        return render;
    }

    public GLRender(EglCore egl, Handler renderThreadHandler)
    {
        Log.i(TAG, "GLRender construct2");
        if(renderThreadHandler.getClass()!=RenderThreadHandler.class)
            throw new IllegalArgumentException("handler must be typeof RenderThreadHandler. can get from GLRender.getHandler");
        mEGL = egl;
        mRenderThreadHandler = (RenderThreadHandler)renderThreadHandler;
    }

    /**
     * 构造函数，使用共享的EGLContext
     * @param sharedContext
     */
    private GLRender(EGLContext sharedContext) {
        Log.i(TAG, "GLRender construct with shared context."+sharedContext.toString());
        mSharedContext = sharedContext;
    }

    public EglCore getEGL() { return mEGL; }
    public Handler getHandler() { return mRenderThreadHandler; }
    public void setCallback(Callback glRenderCallback) { mCallback = glRenderCallback; }

    long mDrawCountMax = Long.MAX_VALUE;
    public void setDrawCountMax(long max) {
        mDrawCountMax = max;
    }

    static final int DRAWFRAMERATE_MAX = 240;
    static final int DRAWFRAMERATE_MIN = 1;
    int mDrawFramerate = -1;        // 渲染的帧率，控制渲染的频率与时间，一般如果渲染到Window则设置为显示器刷新帧率。而如果渲染到FBO，之后再用于编码等，则可以设置为编码帧率等。
    public void setDrawFramerate(int framerate) {
        mDrawFramerate = framerate > DRAWFRAMERATE_MAX ? DRAWFRAMERATE_MAX : framerate;
        mDrawFramerate = framerate < DRAWFRAMERATE_MIN ? DRAWFRAMERATE_MIN : framerate;
    }

    /**
     * 开始初始化EGL context。初始化的结果将通过GLRender.Callback的onInitResult()接口回调通知用户
     * 初始化如果成功则EGL context将创建以及渲染的线程将成功创建。但是RenderTarget还未设置。需要再调用resizeTarget进行RenderTarget的设置。
     * @return true--开始初始化，false--未开始，可能是创建渲染线程失败
     */
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

    void initEGL()
    {
        if(mEGL!=null)
            return;
        Log.i(TAG, "initEGL ");
        mEGL = new EglCore(mSharedContext, EglCore.FLAG_TRY_GLES3);
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
        if(mCallback!=null) {
            mCallback.onRenderTargetCreated(eglSurface, 0, 0);
        }
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
        if(eglSurface!=null)
        {
            mEGL.makeCurrent(mOffscreenSurface);
        }
        Log.i(TAG, "createTargetSurface width="+width+" height"+height+" EGLSurface="+eglSurface);
    }

    void releaseTargetSurface()
    {
        Log.i(TAG, "releaseTargetSurface ");
        mEGL.makeNothingCurrent();
        if(mWindowSurface!=null) {
            mEGL.releaseSurface(mWindowSurface);
            mWindowSurface = null;
        }

        mTargetSurface = null;
        mTargetWidth = 0;
        mTargetHeight = 0;

        if(mCallback!=null) {
            mCallback.onRenderTargetDestroyed();
        }
    }

    void releaseTargetSurfaceOffscreen()
    {
        if(mOffscreenSurface!=null) {
            mEGL.releaseSurface(mOffscreenSurface);
            mOffscreenSurface = null;
            mTargetWidth = 0;
            mTargetHeight = 0;
        }
    }

    void createTargetTextureAndFBO(int width, int height, int pixfmt) {
        //mEGL.makeCurrent(EGL14.EGL_NO_SURFACE);
        GlUtil.checkGlError("makeCurrent");
        int[] fbs = new int[1];
        GLES20.glGenFramebuffers(1, fbs, 0);
        GlUtil.checkGlError("glGenFrameBuffers");
        int texture = GlUtil.createTextureObject(GLES20.GL_TEXTURE_2D);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
        ByteBuffer pixels = null;
//        if(false) {
//            int pitch = width * 4;
//            int len = pitch * height;
//            byte[] pixelDatas = new byte[len];
//            byte val = 0;
//            for(int i=0; i<height; i++) {
//                Arrays.fill(pixelDatas, i*pitch, (i+1)*pitch-1, val);
//                val += 1;
//            }
//            pixels = ByteBuffer.wrap(pixelDatas);
//        }

        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, pixfmt, width, height, 0, pixfmt, GL_UNSIGNED_BYTE, pixels);

        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbs[0]);
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, texture, 0);

        // See if GLES is happy with all this.
        int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            throw new RuntimeException("Framebuffer not complete, status=" + status);
        }

        //GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        //GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);

        mTargetWidth = width;
        mTargetHeight = height;
        mTargetFormat = pixfmt;
        mTargetTexture = texture;
        mTargetTextureHolder = new TextureHolder(mEGL.getContext(), texture);
        mFBO = fbs[0];
        if(mCallback!=null) {
            mCallback.onRenderTargetCreated(null, mFBO, mTargetTexture);
        }
    }

    void releaseTargetTextureAndFBO() {
        if(mFBO!=-1) {
            int[] fbs = new int[]{mFBO};
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
            GLES20.glDeleteFramebuffers(1, fbs, 0);
            mFBO = -1;
        }
        if(mTargetTexture!=-1 && mTargetTextureHolder!=null) {
            mTargetTextureHolder.lock();
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
            int[] textures = new int[] { mTargetTexture };
            GLES20.glDeleteTextures(1, textures, 0);
            mTargetTexture = -1;
            mTargetTextureHolder.onTextureReleased();
            mTargetTextureHolder.unlock();
            mTargetTextureHolder = null;
        }
        mTargetWidth = 0;
        mTargetHeight = 0;
        mTargetFormat = 0;

        if(mCallback!=null) {
            mCallback.onRenderTargetDestroyed();
        }
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
                releaseTargetSurfaceOffscreen();
                createTargetSurface(width, height);
                releaseTargetTextureAndFBO();
                createTargetTextureAndFBO(width, height, GLES20.GL_RGBA);
            }
        }
        else if(surface==null && width==0 && height==0) {
            releaseTargetSurface();
            releaseTargetSurfaceOffscreen();
            releaseTargetTextureAndFBO();
            Log.i(TAG, "releaseRenderTareget when resize rendertarget surface==null width==0 height==0");
        }
        else{
            Log.e(TAG, "doResizeTarget invalid param. Surface="+surface.toString()+" width="+width+" height"+height);
        }
        // 此处触发重复绘制的Timer
        drawFrame();
    }

    /**
     * 当调用resizeTarget时传入的时有效的surface，则此接口获取传入的Surface对象。
     * @return 返回作为RenderTarget的Surface对象
     */
    public Object getRenderTargetSurface() {
        return mTargetSurface;
    }

    /**
     * 当调用resizeTarget创建的是离屏的RenderTarget，创建成功时将创建一个Texture作为RenderTarget
     * 通过此接口可获取创建的RenderTarget的Texture Id。
     * @return Texture Id
     */
    public TextureHolder getRenderTargetTexture() {
        return  mTargetTextureHolder;
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

    static public abstract class CreateTextureResultCallback {
        public abstract void onCreateResult(boolean isSuccess, TextureHolder textureId);
    };

    static public class TextureParams {
        public int width;
        public int height;
        public int format;
        public CreateTextureResultCallback resultCallback;

        public TextureParams(int w, int h, int fmt, CreateTextureResultCallback callback) {
            width = w;
            height = h;
            format = fmt;
            resultCallback = callback;
        }
    }
    /**
     * 创建一个可用于外部使用的Texture，比如可用于摄像头采集画面等。
     * 创建的结果通过callback回调给调用者
     * @param params texture的属性以及回调创建的结果的Callback
     * @return true--调用成功  false--调用失败，此GLRender未初始化
     */
    public boolean createTexture(TextureParams params) {
        if(mRenderState.get()==RENDER_STATE_UNINIT) {
            Log.e(TAG, "createTexture failed. RENDER_STATE==UNINIT");
            return false;
        }

        mRenderThreadHandler.sendMessage(mRenderThreadHandler.obtainMessage(RenderThreadHandler.MSG_CREATE_TEXTURE, params));
        return true;
    }

    public void releaseTexture(int textureId) {
        mRenderThreadHandler.sendMessage(mRenderThreadHandler.obtainMessage(RenderThreadHandler.MSG_RELEASE_TEXTURE, textureId, -1));
    }

    public void onTextureFrameAvailable(SurfaceTexture sf) {
        mRenderThreadHandler.sendMessage(mRenderThreadHandler.obtainMessage(RenderThreadHandler.MSG_TEXTURE_FRAME_AVAILABLE, sf));
    }

    private void doTextureOnFrameAvailable(SurfaceTexture sf) {
        if(sf!=null) {
            sf.updateTexImage();
        }
    }

    void doCreateTexture(int width, int height, int format, CreateTextureResultCallback callback) {
        int texId = GlUtil.createTextureObject(GLES11Ext.GL_TEXTURE_EXTERNAL_OES);
        if(callback!=null) {
            callback.onCreateResult(texId>0, texId>0 ? new TextureHolder(mEGL.getContext(), texId) : null);
        }
    }

    void doReleaseTexture(int textureId) {
        int[] texs = new int[1];
        texs[0] = textureId;
        GLES20.glDeleteTextures(1, texs, 0);
    }

    public void setBackground(float r, float g, float b, float a) {
        backcolor_r = r;
        backcolor_g = g;
        backcolor_b = b;
        backcolor_a = a;
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
    float backcolor_a = 1.0f;

    long frameCount = 0;
    long mLastDrawTime = 0;

    void draw(int backcolor)
    {
        if(mDrawFramerate==-1) {
            mDrawFramerate = 50;
        }
        int frameInterval = 1000 / mDrawFramerate;
        long now = System.currentTimeMillis();
        if(mLastDrawTime!=0 && now < (mLastDrawTime + frameInterval - 2)) {
            long delay = (mLastDrawTime + frameInterval - now) / 2;
            //Log.v(TAG, "Not rech draw time. delay="+delay);
            mRenderThreadHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mRenderThreadHandler.sendMessage(mRenderThreadHandler.obtainMessage(RenderThreadHandler.MSG_DRAW));
                }
            }, delay);
            return;
        }
        mLastDrawTime = now;
        long delay = frameInterval / 2;
        //Log.v(TAG, "It's time to draw. delayToNextTimeDraw="+delay);
        mRenderThreadHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                mRenderThreadHandler.sendMessage(mRenderThreadHandler.obtainMessage(RenderThreadHandler.MSG_DRAW));
            }
        }, delay);

        // EGL context未初始化或者RenderTarget未创建时进行渲染操作
        if(mEGL==null || (mWindowSurface==null && mTargetTexture==-1/* && mOffscreenSurface==null*/))
            return;
        // 如果pause，也不再渲染
        if(mRenderState.get()!=RENDER_STATE_RUNNING)
            return;
        if(frameCount>=mDrawCountMax)
            return;

        // 所有Element中，只要有任何一个Element更新了需要重绘，此时所有的Element都将重绘
        boolean isNeedRedraw = false;
        for (DrawableElem elem : mAllElems) {
            if (elem.isNeedRedraw()) {
                isNeedRedraw = true;
                break;
            }
        }
        if(!isNeedRedraw)
            return;

        EGLSurface target = mWindowSurface!=null ? mWindowSurface : /*EGL14.EGL_NO_SURFACE*/mOffscreenSurface;
        //GlUtil.checkGlError("draw start");
        if(!mEGL.isCurrent(target))
            mEGL.makeCurrent(target);
        GlUtil.checkGlError("makeCurrent");

        int width = 0;
        int height = 0;
        if(target==mWindowSurface)
        {
            width = mEGL.querySurface(target, EGL14.EGL_WIDTH);
            height = mEGL.querySurface(target, EGL14.EGL_HEIGHT);
        }
        else if(mFBO!=-1) {
            mTargetTextureHolder.lock();
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, mFBO);
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, mTargetTexture, 0);
            GlUtil.checkGlError("glBindFramebuffer in draw");
            // See if GLES is happy with all this.
            int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
            if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
                mTargetTextureHolder.unlock();
                throw new RuntimeException("Framebuffer not complete, status=" + status);
            }
            width = mTargetWidth;
            height = mTargetHeight;
        }
        // 可能出现宽或者高不合法的情况，导致看渲染结果为无内容，此时添加日志打印
        if(width==0 || height==0) {
            Log.e(TAG, "invalid width or height in draw. skip. width="+width+" height="+height);
            if(mFBO!=-1) {
                mTargetTextureHolder.unlock();
            }
            return;
        }

        GLES20.glViewport(0, 0, width, height);
        GlUtil.checkGlError("glViewport");
        GLES20.glClearColor(backcolor_r, backcolor_g, backcolor_b, backcolor_a);
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
        frameCount++;

        // dump texture content to jpeg image
        if(mFBO!=-1 && frameCount%100==0 && false) {
            File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                    "GLRenderTest");
            if (dir.exists() || dir.mkdirs()) {
                SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US);
                String fileName = "GLRenderTest-" + format.format(new Date())
                        + "-" + width + "x" + height + ".jpg";
                GlUtil.dumpCurrentFBOToJPEG(dir, fileName, width, height);
            }
        }

        if(mFBO!=-1) {
            GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, 0, 0);
            GlUtil.checkGlError("draw glFramebufferTexture2D");
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
            GlUtil.checkGlError("draw glBindTexture");
            GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
            GlUtil.checkGlError("draw glBindFramebuffer");
            mTargetTextureHolder.unlock();

            if(mCallback!=null) {
                mCallback.onRenderTargetFrameAvailable(mFBO, mTargetTextureHolder);
            }
        }
        mEGL.makeNothingCurrent();

//        backcolor_g += 0.1;
//        if(backcolor_g>1.0) backcolor_g = 0.0f;
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
        public static final int MSG_CREATE_TEXTURE = 7;
        public static final int MSG_RELEASE_TEXTURE = 8;
        public static final int MSG_TEXTURE_FRAME_AVAILABLE = 9;

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
                    glRender.releaseTargetTextureAndFBO();
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
                case MSG_CREATE_TEXTURE:
                    TextureParams params = (TextureParams) msg.obj;
                    glRender.doCreateTexture(params.width, params.height, params.format, params.resultCallback);
                    break;
                case MSG_RELEASE_TEXTURE:
                    glRender.doReleaseTexture(msg.arg1);
                    break;
                case MSG_TEXTURE_FRAME_AVAILABLE:
                    glRender.doTextureOnFrameAvailable((SurfaceTexture)msg.obj);
                    break;
                default:
                    Log.i(TAG, "handleMessage no surport msg="+msg.what);
                    break;
            }
            super.handleMessage(msg);
            //Log.i(TAG, "handleMessage msg="+msg.what);
        }
    }
}
