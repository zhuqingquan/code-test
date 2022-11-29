#include <X11/Xlib.h>
#include "glad/egl.h"
#include "glad/gl.h"

#include <xf86drm.h>
#include <libdrm/drm_fourcc.h>
#include <xf86drmMode.h>
#include <fcntl.h>
#include <sys/un.h>
#include <unistd.h>
//#include <sys/mman.h>

#include <stdlib.h>
#include <vector>
#include <stdio.h>
#include <errno.h>

#define LOG_PREFIX "eglCapture"

#define ERR(fmt, ...) fprintf(stderr, LOG_PREFIX fmt "\n", ##__VA_ARGS__)
#define MSG(fmt, ...) fprintf(stdout, LOG_PREFIX fmt "\n", ##__VA_ARGS__)

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

struct DRMFBInfo
{
    uint32_t fb_id;
    uint32_t width;
    uint32_t height;
    uint32_t fourcc;
    uint32_t offset[4];
    uint32_t pitches[4];
    int handles[4];
    int fds[4];

    DRMFBInfo()
        : fb_id(0), width(0), height(0), fourcc(0)
    {
        handles[0] = handles[1] = handles[2] = handles[3] = 0;
        fds[0] = fds[1] = fds[2] = fds[3] = 0;
        offset[0] = offset[1] = offset[2] = offset[3] = 0;
        pitches[0] = pitches[1] = pitches[2] = pitches[3] = 0;
    }
};

int getFBInfos(const char* graphicCard, std::vector<DRMFBInfo>& fbInfos)
{
	MSG("Opening card %s", graphicCard);
	const int drmfd = open(graphicCard, O_RDONLY);
	if (drmfd < 0) {
		perror("Cannot open card");
		return 0;
	}

	if (0 != drmSetClientCap(drmfd, DRM_CLIENT_CAP_UNIVERSAL_PLANES, 1)) {
		perror("Cannot tell drm to expose all planes; the rest will very likely fail");
	}

    int count = 0;
    drmModePlaneResPtr planes = drmModeGetPlaneResources(drmfd);
    if (!planes) {
        ERR("Cannot get drm planes: %s (%d)", strerror(errno),
            errno);
        close(drmfd);
        return count;
    }

    MSG("DRM planes %d:", planes->count_planes);
    for (uint32_t i = 0; i < planes->count_planes; ++i) {
        drmModePlanePtr plane =
            drmModeGetPlane(drmfd, planes->planes[i]);
        if (!plane) {
            ERR("Cannot get drmModePlanePtr for plane %#x: %s (%d)",
                planes->planes[i], strerror(errno), errno);
            continue;
        }

        MSG("\t%d: fb_id=%#x", i, plane->fb_id);
        if (!plane->fb_id)
        {
            drmModeFreePlane(plane);
            continue;
        }

        drmModeFB2Ptr drmfb = drmModeGetFB2(drmfd, plane->fb_id);
        if (!drmfb) {
            ERR("Cannot get drmModeFBPtr for fd=%u fb %#x: %s (%d)", drmfd, plane->fb_id, strerror(errno), errno);
        } else {
            if (!drmfb->handles[0]) {
                ERR("\t\tFB handle for fb %#x is NULL", plane->fb_id);
                ERR("\t\tPossible reason: not permitted to get FB handles. Do `sudo setcap cap_sys_admin+ep %s`", graphicCard);
            } else {
                DRMFBInfo fbInfo;
                for(int j=0; j<4; j++)
                {
                    if(drmfb->handles[j]==0)
                        continue;
                    
                    int fb_fd = -1;
                    const int ret = drmPrimeHandleToFD(drmfd, drmfb->handles[j], 0, &fb_fd);
                    if (ret != 0 || fb_fd == -1) {
                        ERR("Cannot get fd for fb %#x handle %#x: %s (%d)",
                            plane->fb_id, drmfb->handles[j],
                            strerror(errno), errno);
                    }
                    else{
                        printf("Prime handle to fd success. handle=%u, fd=%u w*h=%u*%u offset[%d]=%u pitches[%d]=%u pixfmt=%u\n", 
                                drmfb->handles[j], fb_fd, drmfb->width, drmfb->height, j, drmfb->offsets[j], j, drmfb->pitches[j], drmfb->pixel_format);
                        fbInfo.fb_id = plane->fb_id;
                        fbInfo.width = drmfb->width;
                        fbInfo.height = drmfb->height;
                        fbInfo.fourcc = drmfb->pixel_format;
                        fbInfo.offset[j] = drmfb->offsets[j];
                        fbInfo.pitches[j] = drmfb->pitches[j];
                        fbInfo.handles[j] = drmfb->handles[j];
                        fbInfo.fds[j] = fb_fd;
                    }
                }
                if(fbInfo.fb_id!=0)
                {
                    fbInfos.push_back(fbInfo);
                    count++;
                }
            }
            drmModeFreeFB2(drmfb);
        }
        drmModeFreePlane(plane);
    }

    drmModeFreePlaneResources(planes);
    close(drmfd);
    return count;
}

EGLImage createImageFromFB(EGLDisplay edisp, const DRMFBInfo& fbInfo)
{
    for(int i=0; i<4; i++)
    {
        if(fbInfo.fds[i]==0)
            continue;
        // FIXME check for EGL_EXT_image_dma_buf_import
        EGLAttrib eimg_attrs[] = {
            EGL_WIDTH, fbInfo.width,
            EGL_HEIGHT, fbInfo.height,
            EGL_LINUX_DRM_FOURCC_EXT, fbInfo.fourcc,
            EGL_DMA_BUF_PLANE0_FD_EXT, fbInfo.fds[i],
            EGL_DMA_BUF_PLANE0_OFFSET_EXT, fbInfo.offset[i],
            EGL_DMA_BUF_PLANE0_PITCH_EXT, fbInfo.pitches[i],
            EGL_NONE
        };
        // clang-format on 

        EGLImage eimage = eglCreateImage(edisp, EGL_NO_CONTEXT,
                         EGL_LINUX_DMA_BUF_EXT, 0, eimg_attrs);
        if(eimage==EGL_NO_IMAGE)
        {
            printf("Create EGL Image failed.err=%d\n", eglGetError());
            continue;
        }
        return eimage;
    }
    return EGL_NO_IMAGE;
}

GLuint createTexture(uint32_t width, uint32_t height, GLint pixfmt, int usage)
{
    GLuint tex = 0;
    glGenTextures(1, &tex);
    if(tex==0)
    {
        return tex;
    }
    glBindTexture(GL_TEXTURE_2D, tex);
    glTexImage2D(GL_TEXTURE_2D, 0, pixfmt, width, height, 0, pixfmt, GL_UNSIGNED_BYTE, nullptr);
    glBindTexture(GL_TEXTURE_2D, 0);
    return tex;
}

extern "C"{

// FIXME integrate into glad
typedef void(* PFNGLEGLIMAGETARGETTEXTURE2DOESPROC)(
	GLenum target, GLeglImageOES image);
//GLAPI PFNGLEGLIMAGETARGETTEXTURE2DOESPROC glad_glEGLImageTargetTexture2DOES;
}
static PFNGLEGLIMAGETARGETTEXTURE2DOESPROC glEGLImageTargetTexture2DOES;

GLuint createTextureFromEGLImage(EGLImage eglImg, uint32_t width, uint32_t height)
{
	GLuint gltex = createTexture(width, height, GL_RGBA, GL_DYNAMIC_COPY); //gs_texture_create(fb->width, fb->height, GS_BGRA, 1, NULL, GS_DYNAMIC);
	printf("create Texture from EGLImage %s gltex = %x\n", gltex!=0 ? "success" : "failed", gltex);
	glBindTexture(GL_TEXTURE_2D, gltex);

    	// FIXME move to glad
	if (!glEGLImageTargetTexture2DOES) {
		glEGLImageTargetTexture2DOES =
			(PFNGLEGLIMAGETARGETTEXTURE2DOESPROC)eglGetProcAddress("glEGLImageTargetTexture2DOES");
	}

	if (!glEGLImageTargetTexture2DOES) {
		fprintf(stderr, "GL_OES_EGL_image extension is required");
		return 0;
	}

	glEGLImageTargetTexture2DOES(GL_TEXTURE_2D, eglImg);
	glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
	glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
    return gltex;
}

int main(int argc, char* argv[])
{
    std::vector<DRMFBInfo> fbInfoVec;
    int fbCount = getFBInfos("/dev/dri/card0", fbInfoVec);
    printf("Get FrameBuffer FD count=%lu\n", fbInfoVec.size());

    int result = 0;
    int verMaj = 0, verMin = 0;
    int eglVersion = 0;
    const char* eglExtersions = nullptr;
    bool ret;
    EGLConfig eglCfg = nullptr;
    EGLContext eglCtx = EGL_NO_CONTEXT;
    EGLSurface pbuffer = EGL_NO_SURFACE;
    
    int cfgCount = 0;
    std::vector<EGLImage> eglImages;

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

    gladLoaderLoadGL();

    for(auto iter : fbInfoVec)
    {
        EGLImage eglImg = createImageFromFB(edisp, iter);
        if(eglImg!=EGL_NO_IMAGE)
        {
            eglImages.push_back(eglImg);
            printf("Create EGL image success. FB_ID=%u FB_FD=%u width=%u height=%u fourcc=[%c %c %c %c]\n", iter.fb_id, iter.fds[0], iter.width, iter.height, *(char*)(&iter.fourcc)+0, *(char*)(&iter.fourcc)+1, *(char*)(&iter.fourcc)+2, *(char*)(&iter.fourcc)+3);
        }
        GLuint gltex = createTextureFromEGLImage(eglImg, iter.width, iter.height);
    }

    printf("press a key to exit...");
    getchar();

    for(auto iter : eglImages)
    {
        eglDestroyImage(edisp, iter);
    }
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
