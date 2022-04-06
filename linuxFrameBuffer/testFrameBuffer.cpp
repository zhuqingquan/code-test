/*
 * @Author: your name
 * @Date: 2022-04-06 20:52:49
 * @LastEditTime: 2022-04-06 21:06:45
 * @LastEditors: Please set LastEditors
 * @Description: 打开koroFileHeader查看配置 进行设置: https://github.com/OBKoro1/koro1FileHeader/wiki/%E9%85%8D%E7%BD%AE
 * @FilePath: /code-test/linuxFrameBuffer/testFrameBuffer.cpp
 */

#include <stdio.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <linux/fb.h>
#include <sys/ioctl.h>
#include <sys/mman.h>

#define FB_DEVICE_0 "/dev/fb0"

#define WRITE 0xFFFFFFFF
#define BLACK 0x00000000

int main(int argc, char* argv[])
{
    int fd = -1;
    unsigned int* pfb = nullptr;
    unsigned int width = 0;
    unsigned int height = 0;

    struct fb_fix_screeninfo fixScreenInfo = {0};
    struct fb_var_screeninfo varScreenInfo = {0};

    fd = open(FB_DEVICE_0, O_RDWR);
    if(fd<0)
    {
        printf("Open FrameBuffer failed.name=%s ret=%d\n", FB_DEVICE_0, fd);
        return -1;
    }

    printf("Open FrameBuffer success.Name=%s\n", FB_DEVICE_0);

    ioctl(fd, FBIOGET_FSCREENINFO, &fixScreenInfo);
    printf("FixScreenInfo: smem_start=0x%lx smem_len=%u\n", fixScreenInfo.smem_start, fixScreenInfo.smem_len);
    return 0;
}