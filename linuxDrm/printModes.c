#define _GNU_SOURCE
#include <errno.h>
#include <fcntl.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>
#include <time.h>
#include <unistd.h>
#include <xf86drm.h>
#include <xf86drmMode.h>

static int modeset_open(int *out, const char *node)
{
	int fd, ret;
	uint64_t has_dumb;

	fd = open(node, O_RDWR | O_CLOEXEC);
	if (fd < 0) {
		ret = -errno;
		fprintf(stderr, "cannot open '%s': %m\n", node);
		return ret;
	}

	if (drmGetCap(fd, DRM_CAP_DUMB_BUFFER, &has_dumb) < 0 ||
	    !has_dumb) {
		fprintf(stderr, "drm device '%s' does not support dumb buffers\n",
			node);
		close(fd);
		return -EOPNOTSUPP;
	}

	*out = fd;
	return 0;
}

int main(int argc, char* argv[])
{
	int ret, fd;
	const char *card;

    //ret = modeset_open(&fd, "/dev/dri/card0");
    ret = modeset_open(&fd, "/dev/dri/renderD128");

	drmModeRes *res;
	drmModeConnector *conn;
	//int i;
	struct modeset_dev *dev;

	/* retrieve resources */
	res = drmModeGetResources(fd);
	if (!res) {
		fprintf(stderr, "cannot retrieve DRM resources (%d): %m\n",
			errno);
		return -errno;
	}
    printf("drmModeRes conector count=%d encoder count=%d crtc count=%d fb count=%d\n", 
            res->count_connectors, res->count_encoders, res->count_crtcs, res->count_fbs);
    char logBuf[512] = {0};
    // print connector all id
    snprintf(logBuf, 512, "connector id [");
	for (int i = 0; i < res->count_connectors; ++i) {
        snprintf(logBuf+strlen(logBuf), 512, " %d", res->connectors[i]);
    }
    snprintf(logBuf+strlen(logBuf), 512, "%s\n", "]");
    printf("%s", logBuf);

    // print encoder all id
    snprintf(logBuf, 512, "encoder id [");
	for (int i = 0; i < res->count_encoders; ++i) {
        snprintf(logBuf+strlen(logBuf), 512, " %d", res->encoders[i]);
    }
    snprintf(logBuf+strlen(logBuf), 512, "%s\n", "]");
    printf("%s", logBuf);

    // print crtc all id
    snprintf(logBuf, 512, "crtc id [");
	for (int i = 0; i < res->count_crtcs; ++i) {
        snprintf(logBuf+strlen(logBuf), 512, " %d", res->crtcs[i]);
    }
    snprintf(logBuf+strlen(logBuf), 512, "%s\n", "]");
    printf("%s", logBuf);

    // print crtc all id
    snprintf(logBuf, 512, "fb id [");
	for (int i = 0; i < res->count_fbs; ++i) {
        snprintf(logBuf+strlen(logBuf), 512, " %d", res->fbs[i]);
    }
    snprintf(logBuf+strlen(logBuf), 512, "%s\n", "]");
    printf("%s", logBuf);

    // get Plane list
    drmModePlaneResPtr planes = drmModeGetPlaneResources(fd);
    for(uint32_t i=0; planes != NULL && i < planes->count_planes; i++)
    {
        drmModePlanePtr plane = drmModeGetPlane(fd, planes->planes[i]);
        if(plane!=NULL)
        {
            printf("Plane %u--------------------------\n", plane->plane_id);
            printf("\tcrtc_id %u fb_id %u x=%u y=%u crtc_x=%u crtc_y=%u\n", 
                    plane->crtc_id, plane->fb_id, plane->x, plane->y, plane->crtc_x, plane->crtc_y);
            drmModeFreePlane(plane);
        }
    }
    drmModeFreePlaneResources(planes);

	/* iterate all connectors */
	for (int i = 0; i < res->count_connectors; ++i) {
        printf("Connector %u ------------------------------------- \n", res->connectors[i]);
		/* get information for each connector */
		conn = drmModeGetConnector(fd, res->connectors[i]);
		if (!conn) {
			printf("cannot retrieve DRM connector %u:%u (%d): %m\n",
				i, res->connectors[i], errno);
			continue;
		}
        if(conn->connection != DRM_MODE_CONNECTED)
        {
            printf("Connector %u is NOT CONNECTED\n", res->connectors[i]);
            continue;
        }
        printf("connector %u encoder connected %u modes count=%d subpixel=%d\n", 
                conn->connector_id, conn->encoder_id, conn->count_modes, (int)conn->subpixel);

        // print encoder ids connected to current connector
        snprintf(logBuf, 512, "\tencoder id list [");
        for (int i = 0; i < conn->count_encoders; ++i) {
            snprintf(logBuf+strlen(logBuf), 512, " %d", conn->encoders[i]);
        }
        snprintf(logBuf+strlen(logBuf), 512, " ]\n");
        printf("%s", logBuf);

        drmModeEncoder* enc = conn->encoder_id != 0 ? drmModeGetEncoder(fd, conn->encoder_id) : NULL;
        if(enc!=NULL)
        {
            printf("\tcurrent encoder %d crtc connected=%u possible_crtcs=%04x\n", 
                    conn->encoder_id, enc->crtc_id, enc->possible_crtcs);
            if(enc->crtc_id!=0)
            {
                drmModeCrtc* crtc = drmModeGetCrtc(fd, enc->crtc_id);
                printf("\t\tcurrent crtc %d buffer_id=%u w*h=%u*%u x=%u y=%u\n", 
                        crtc->crtc_id, crtc->buffer_id, crtc->width, crtc->height, crtc->x, crtc->y);
                if(crtc->buffer_id!=0)
                {
                    drmModeFBPtr fb = drmModeGetFB(fd, crtc->buffer_id);
                    if(fb==NULL)
                    {
                        drmModeFB2Ptr fb2 = drmModeGetFB2(fd, crtc->buffer_id);
                        if(fb2!=NULL)
                        {
                            printf("\t\tFrameBuffer %u handle=[%u %u %u %u] w*h=%u*%u pitch=%u offset=%u\n", 
                                    fb2->fb_id, fb2->handles[0], fb2->handles[1], fb2->handles[2], fb2->handles[3],
                                    fb2->width, fb2->height, fb2->pitches[0], fb2->offsets[0]);
                            struct drm_mode_map_dumb mreq;
                            uint64_t offset = 0;
                            memset(&mreq, 0, sizeof(mreq));
                            mreq.handle = fb2->handles[0];
                            ret = drmIoctl(fd, DRM_IOCTL_MODE_MAP_DUMB, &mreq);
                            if (ret) {
                                printf("Map_DUMB failed\n");
                            }
                            else
                            {
                                printf("Map_DUMB offset=%llu\n", mreq.offset);
                                offset = mreq.offset;
                            }
                            void* mappedAddr = mmap(0, fb2->pitches[0]*fb2->height, PROT_READ | PROT_WRITE, MAP_SHARED,
                                        fd, mreq.offset);
                            if (mappedAddr == MAP_FAILED) {
                                printf("map buffer failed.\n");
                            }
                            else{
                                printf("map buffer success. addr=%p\n", mappedAddr);
                            }
                            drmModeFreeFB2(fb2);
                        }
                        else 
                            printf("\t\tdrmModeGetFB2 failed.[fb_id=%u]\n", crtc->buffer_id);
                    }
                    else
                    {
                            printf("\t\tFrameBuffer %u handle=%u w*h=%u*%u pitch=%u depth=%u\n", 
                                    fb->fb_id, fb->handle, fb->width, fb->height, fb->pitch, fb->depth);
                            drmModeFreeFB(fb);
                    }
                }
                drmModeFreeCrtc(crtc);
            }
            drmModeFreeEncoder(enc);
        }

        for( int i=0; i< conn->count_modes; i++)
        {
            drmModeModeInfoPtr mode = conn->modes + i;
            printf("\tmode %s [w=%d h=%d] %s\n", mode->name, mode->hdisplay, mode->vdisplay, i==0?"***":"");
        }
    }
    return 0;
}
