package com.example.testadrscreencapture.glrender;

import java.util.concurrent.locks.ReentrantLock;

/**
 * 持有Texture资源对应的ID
 * 提供lock与unlock接口保证多个线程对同一个Texture操作时的线程安全
 */
public class TextureHolder {
    Object mContext;
    int mTextureID = -1;
    ReentrantLock mMutex;

    /**
     * 构造函数
     * @param context 创建此Texture资源的渲染上下文，比如EGLContext
     * @param textureID Texture资源的ID
     */
    public TextureHolder(Object context, int textureID) {
        mContext = context;
        mTextureID = textureID;
        mMutex = new ReentrantLock();
    }

    /**
     * 获取创建此Texture资源的渲染上下文
     * @return 渲染上下文
     */
    public Object getContext() { return mContext; }

    /**
     * 获取Texture资源的ID
     * @return Texture资源的ID
     */
    public int getTexture() { return mTextureID; }

    /**
     * 获取Texture的ID并加锁
     * @return Texture的ID
     */
    public int lock() {
        mMutex.lock();
        return mTextureID;
    }

    /**
     * 释放Texture的锁
     */
    public void unlock() {
        mMutex.unlock();
    }

    /**
     * 当Texture被释放时需要调用此方法，这样通过lock方法获取TextureID使用Texture的渲染线程才会正常处理Texture被释放的情况
     */
    public void onTextureReleased() {
        mTextureID = -1;
    }
}
