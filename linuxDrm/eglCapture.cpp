#include <X11/Xlib.h>
#include "glad/egl.h"
#include <stdio.h>

static const EGLint cfgAttribs[] = {
    EGL_STENCIL_SIZE, 0,
    EGL_DEPTH_SIZE, 0,
    EGL_BUFFER_SIZE, 32,
    EGL_ALPHA_SIZE, 8,
    EGL_RENDERABLE_TYPE, EGL_OPENGL_BIT,
    EGL_SURFACE_TYPE, EGL_WINDOW_BIT | EGL_PBUFFER_BIT,
    EGL_NONE
};

static const EGLint ctxAttribs[] = {
    EGL_CONTEXT_OPENGL_PROFILE_MASK, EGL_CONTEXT_OPENGL_CORE_PROFILE_BIT,
    EGL_CONTEXT_MAJOR_VERSION, 3, EGL_CONTEXT_MINOR_VERSION, 3,
    EGL_NONE
};

static const EGLint pbufferAttribs[] = {
    EGL_WIDTH, 256,
    EGL_HEIGHT, 256,
    EGL_NONE
};

int main(int argc, char* argv[])
{
    int result = 0;
    int verMaj = 0, verMin = 0;
    int eglVersion = 0;
    const char* eglExtersions = nullptr;
    bool ret;
    EGLConfig eglCfg = nullptr;
    EGLContext eglCtx = EGL_NO_CONTEXT;
    EGLSurface pbuffer = EGL_NO_SURFACE;
    int cfgCount = 0;
    printf("Start...\n");
    Display* display = XOpenDisplay(nullptr);
    if(nullptr==display)
    {
        printf("Open Display failed.\n");
        return -1;
    }

    // 第一次loadEGL会因为传入nullptr的缘故而可能只能加载到1.0版本的API
    eglVersion = gladLoaderLoadEGL(nullptr);
    printf("EGL version from loadEGL ver=%u\n", eglVersion);
    eglExtersions = eglQueryString(EGL_NO_DISPLAY, EGL_EXTENSIONS);
    printf("EGL extensions: %s\n", eglExtersions);
    EGLDisplay edisp = eglGetDisplay(display);
    if(nullptr==edisp)
    {
        printf("get egl Display failed.\n");
        return -1;
    }
    if(!eglInitialize(edisp, &verMaj, &verMin))
    {
        printf("elgInitialize failed. err=%x\n", eglGetError());
        result = -2;
        goto END;
    }
    printf("elgInitialize success. maj=%d min=%d\n", verMaj, verMin);

    // 使用初始化后的EGLDisplay再load一次，这次能够获取到1.5版本EGL的api
    eglVersion = gladLoaderLoadEGL(edisp);
    printf("EGL version from loadEGL ver=%u EGLDisplay=%p\n", eglVersion, edisp);
    // 初始化EGLDisplay对象后再查询一次支持的EXTENSION，此时结果对于对应EGLDisplay更准确
    eglExtersions = eglQueryString(edisp, EGL_EXTENSIONS);
    printf("EGL extensions(EGLDisplay=%p): %s\n", edisp, eglExtersions);

    ret = eglBindAPI(EGL_OPENGL_API);
    printf("eglBindAPI %d\n", ret);

    ret = eglChooseConfig(edisp, cfgAttribs, &eglCfg, 1, &cfgCount);
    printf("choose config ret=%d config=%p\n", ret, eglCfg);

    if(eglCfg==nullptr)
    {
        EGLint err = eglGetError();
        printf("EGL choose config failed.Err=%x\n", err);
        goto END;
    }
    eglCtx = eglCreateContext(edisp, eglCfg, EGL_NO_CONTEXT, ctxAttribs);
    if(eglCtx==EGL_NO_CONTEXT)
    {
        EGLint err = eglGetError();
        printf("EGL create context failed.Err=%x\n", err);
        goto END;
    }
    pbuffer = eglCreatePbufferSurface(edisp, eglCfg, pbufferAttribs);
    if(pbuffer == EGL_NO_SURFACE)
    {
        EGLint err = eglGetError();
        printf("EGL create PBuffer surface failed.err=%x\n", err);
        goto END;
    }

    ret = eglMakeCurrent(edisp, pbuffer, pbuffer, eglCtx);
    if(!ret)
    {
        EGLint err = eglGetError();
        printf("EGL make current falied. err=%x Display=%p buffer_write=%p buffer_read=%p context=%p\n",
            err, edisp, pbuffer, pbuffer, eglCtx);
        goto END;
    }

    printf("press a key to exit...");
    getchar();
END:
    eglMakeCurrent(edisp, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
    eglDestroySurface(edisp, pbuffer);
    eglDestroyContext(edisp, eglCtx);
    eglTerminate(edisp);
    XCloseDisplay(display);
    gladLoaderUnloadEGL();
    printf("end %d\n", result);
    return 0;
}