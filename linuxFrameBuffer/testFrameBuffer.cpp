/*
 * @Author: your name
 * @Date: 2022-04-06 20:52:49
 * @LastEditTime: 2022-04-24 20:17:27
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
#include <unistd.h>
#include <fstream>

// #define FB_DEVICE_0 "/dev/fb0"
#define FB_DEVICE_0 "/dev/graphics/fb0"

#define WRITE 0xFFFFFFFF
#define BLACK 0x00000000

int main(int argc, char* argv[])
{
    char* fileName = FB_DEVICE_0;
    if(argc==2)
    {
        fileName = argv[1];
    }
    int fd = -1;
    unsigned int* pfb = nullptr;
    unsigned int width = 0;
    unsigned int height = 0;

    struct fb_fix_screeninfo fixScreenInfo = {0};
    struct fb_var_screeninfo varScreenInfo = {0};

    fd = open(fileName, O_RDWR);
    if(fd<0)
    {
        printf("Open FrameBuffer failed.name=%s ret=%d\n", fileName, fd);
        return -1;
    }

    printf("Open FrameBuffer success.Name=%s\n", fileName);

    ioctl(fd, FBIOGET_FSCREENINFO, &fixScreenInfo);
    printf("FixScreenInfo: smem_start=0x%lx smem_len=%u line_length=%u\n", 
        fixScreenInfo.smem_start, fixScreenInfo.smem_len, fixScreenInfo.line_length);

    ioctl(fd, FBIOGET_VSCREENINFO, &varScreenInfo);
    printf("Resolution width=%u height=%u Virtual_res w=%u h=%u bitsPerPixel=%d grayScale=%u VMode=%u offset_red=%u offset_green=%u offset_blue=%u\n", 
        varScreenInfo.xres, varScreenInfo.yres, varScreenInfo.xres_virtual, varScreenInfo.yres_virtual, varScreenInfo.bits_per_pixel,
        varScreenInfo.grayscale, varScreenInfo.vmode, varScreenInfo.red.offset, varScreenInfo.green.offset, varScreenInfo.blue.offset);

    width = varScreenInfo.xres;
    height = varScreenInfo.yres;

    // uint32_t length = fixScreenInfo.smem_len;
    uint32_t length = varScreenInfo.yres_virtual * fixScreenInfo.line_length;
    pfb = (unsigned int*)mmap(nullptr, length, PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0);
    printf("Maped addr = %p\n", pfb);

    std::ofstream outfile("/sdcard/sc.rgb32", std::ios::out | std::ios::binary);
    if(outfile)
    {
        outfile.write((char*)pfb, length);
        outfile.close();
    }

    munmap(pfb, length);
    close(fd);
    return 0;
}