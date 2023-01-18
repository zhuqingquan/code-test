package com.example.testadrscreencapture.glrender;

import java.util.concurrent.atomic.AtomicBoolean;

public abstract class DrawableElem {
    public abstract void draw();

    protected AtomicBoolean mNeedRedraw = new AtomicBoolean(false);

    /**
     * 设置是否需要重新绘制的标志位
     * @param isNeed
     */
    public void setNeedRedraw(boolean isNeed) { mNeedRedraw.set(isNeed); }

    /**
     * 返回当前Element是否需要重新绘制。
     * 当Element对应的顶点、MVP坐标、Texture等发生改变后此方法返回true，此时draw()方法将在渲染线程中被调用
     * @return 当前Element是否需要重新绘制
     */
    public boolean isNeedRedraw() {
            return mNeedRedraw.get();
    }
}
