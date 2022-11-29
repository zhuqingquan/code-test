#include <xf86drm.h>
#include <libdrm/drm_fourcc.h>
#include <xf86drmMode.h>

#include <sys/types.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <sys/mman.h>
#include <stdio.h>
#include <unistd.h>
#include <fcntl.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <vector>

#define LOG_PREFIX "obs-drmsend: "

#define ERR(fmt, ...) fprintf(stderr, LOG_PREFIX fmt "\n", ##__VA_ARGS__)
#define MSG(fmt, ...) fprintf(stdout, LOG_PREFIX fmt "\n", ##__VA_ARGS__)

void printUsage(const char *name)
{
	MSG("usage: %s /dev/dri/card socket_filename", name);
}

struct DRMFBInfo
{
    uint32_t fb_id;
    int handles[4];
    int fds[4];

    DRMFBInfo()
        : fb_id(0) 
    {
        handles[0] = handles[1] = handles[2] = handles[3] = 0;
        fds[0] = fds[1] = fds[2] = fds[3] = 0;
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
                        DRMFBInfo fbInfo;
                        fbInfo.fb_id = plane->fb_id;
                        fbInfo.handles[j] = drmfb->handles[j];
                        fbInfo.fds[j] = fb_fd;
                        fbInfos.push_back(fbInfo);
                        count++;
                    }
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

// 此函数运行需要requires either setuid or setcap cap_sys_admin+ep
int main(int argc, const char *argv[])
{
	if (argc < 3) {
		printUsage(argv[0]);
		return 1;
	}

	const char *card = argv[1];

    std::vector<DRMFBInfo> fbInfoVec;
    int count = getFBInfos(card, fbInfoVec);

	MSG("Opening card %s", card);
	const int drmfd = open(card, O_RDONLY);
	if (drmfd < 0) {
		perror("Cannot open card");
		return 1;
	}

	if (0 != drmSetClientCap(drmfd, DRM_CLIENT_CAP_UNIVERSAL_PLANES, 1)) {
		perror("Cannot tell drm to expose all planes; the rest will very likely fail");
	}

	int sockfd = -1;
	int retval = 2;
	//drmsend_response_t response = {0};
	//int fb_fds[OBS_DRMSEND_MAX_FRAMEBUFFERS] = {-1};

	{
		drmModePlaneResPtr planes = drmModeGetPlaneResources(drmfd);
		if (!planes) {
			ERR("Cannot get drm planes: %s (%d)", strerror(errno),
			    errno);
			goto cleanup;
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

			int j = 0;
            drmModeFB2Ptr drmfb = nullptr;
            void* pDataBuf = nullptr;
			if (!plane->fb_id)
				goto plane_continue;

			//for (; j < response.num_framebuffers; ++j) {
			//	if (response.framebuffers[j].fb_id ==
			//	    plane->fb_id)
			//		break;
			//}

			//if (j < response.num_framebuffers)
			//	goto plane_continue;

			//if (j == OBS_DRMSEND_MAX_FRAMEBUFFERS) {
			//	ERR("Too many framebuffers, max %d",
			//	    OBS_DRMSEND_MAX_FRAMEBUFFERS);
			//	goto plane_continue;
			//}

			drmfb = drmModeGetFB2(drmfd, plane->fb_id);
			if (!drmfb) {
				ERR("Cannot get drmModeFBPtr for fd=%u fb %#x: %s (%d)",
				    drmfd, plane->fb_id, strerror(errno), errno);
			} else {
				if (!drmfb->handles[0]) {
					ERR("\t\tFB handle for fb %#x is NULL",
					    plane->fb_id);
					ERR("\t\tPossible reason: not permitted to get FB handles. Do `sudo setcap cap_sys_admin+ep %s`",
					    argv[0]);
				} else {
					int fb_fd = -1;
					const int ret = drmPrimeHandleToFD(
						drmfd, drmfb->handles[0], 0,
						&fb_fd);
					if (ret != 0 || fb_fd == -1) {
						ERR("Cannot get fd for fb %#x handle %#x: %s (%d)",
						    plane->fb_id, drmfb->handles[0],
						    strerror(errno), errno);
					} else {
                        printf("Prime handle to fd success. handle=%u, fd=%u w*h=%u*%u pitches[0]=%u pixfmt=%u\n", 
                                drmfb->handles[0], fb_fd, drmfb->width, drmfb->height, drmfb->pitches[0], drmfb->pixel_format);
						//const int fb_index =
						//	response.num_framebuffers++;
						//drmsend_framebuffer_t *fb =
						//	response.framebuffers +
						//	fb_index;
						//fb_fds[fb_index] = fb_fd;
						//fb->fb_id = plane->fb_id;
						//fb->width = drmfb->width;
						//fb->height = drmfb->height;
						//fb->pitch = drmfb->pitch;
						//fb->offset = 0;
						//fb->fourcc =
						//	DRM_FORMAT_XRGB8888; // FIXME
                        pDataBuf = mmap(nullptr, drmfb->pitches[0]*drmfb->height, PROT_READ, MAP_SHARED, fb_fd, drmfb->offsets[0]);
                        if(pDataBuf!=(void*)-1)
                        {
                            printf("mmap success. buf=%p\n", pDataBuf);
                            munmap(pDataBuf, drmfb->pitches[0]*drmfb->height);
                        }
                        else
                        {
                            printf("mmap failed. error=%s\n", strerror(errno));
                        }
					}
				}
				drmModeFreeFB2(drmfb);
			}

		plane_continue:
			drmModeFreePlane(plane);
		}

		drmModeFreePlaneResources(planes);
	}
cleanup:
	if (sockfd >= 0)
		close(sockfd);
	close(drmfd);
	return retval;
}

// create gl texture from DUMB buffer file fd.
// It works by importing KMS (DRM) display framebuffer directly as GL texture, using EGL_EXT_image_dma_buf_import extension.
/*
{
    obs_enter_graphics();

	const graphics_t *const graphics = gs_get_context();
	const EGLDisplay edisp = graphics->device->plat->edisplay;
	ctx->edisp = edisp;

	// clang-format off 
	// FIXME check for EGL_EXT_image_dma_buf_import
	EGLAttrib eimg_attrs[] = {
		EGL_WIDTH, fb->width,
		EGL_HEIGHT, fb->height,
		EGL_LINUX_DRM_FOURCC_EXT, fb->fourcc,
		EGL_DMA_BUF_PLANE0_FD_EXT, ctx->fbs.fb_fds[index],
		EGL_DMA_BUF_PLANE0_OFFSET_EXT, fb->offset,
		EGL_DMA_BUF_PLANE0_PITCH_EXT, fb->pitch,
		EGL_NONE
	};
	// clang-format on 

	ctx->eimage = eglCreateImage(edisp, EGL_NO_CONTEXT,
				     EGL_LINUX_DMA_BUF_EXT, 0, eimg_attrs);

	if (!ctx->eimage) {
		// FIXME stringify error
		blog(LOG_ERROR, "Cannot create EGLImage: %d", eglGetError());
		dmabuf_source_close(ctx);
		goto exit;
	}

	// FIXME handle fourcc?
	ctx->texture = gs_texture_create(fb->width, fb->height, GS_BGRA, 1,
					 NULL, GS_DYNAMIC);
	const GLuint gltex = *(GLuint *)gs_texture_get_obj(ctx->texture);
	blog(LOG_DEBUG, "gltex = %x", gltex);
	glBindTexture(GL_TEXTURE_2D, gltex);

	glEGLImageTargetTexture2DOES(GL_TEXTURE_2D, ctx->eimage);
	glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
	glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);

	ctx->active_fb = index;

exit:
	obs_leave_graphics();
}
*/
