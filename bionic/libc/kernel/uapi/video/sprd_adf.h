/****************************************************************************
 ****************************************************************************
 ***
 ***   This header was automatically generated from a Linux kernel header
 ***   of the same name, to make information necessary for userspace to
 ***   call into the kernel available to libc.  It contains only constants,
 ***   structures, and macros generated from the original header, and thus,
 ***   contains no copyrightable information.
 ***
 ***   To edit the content of this header, modify the corresponding
 ***   source file (e.g. under external/kernel-headers/original/) then
 ***   run bionic/libc/kernel/tools/update_all.py
 ***
 ***   Any manual change here will be lost the next time this script will
 ***   be run. You've been warned!
 ***
 ****************************************************************************
 ****************************************************************************/
#ifndef _UAPI_VIDEO_SPRD_ADF_H_
#define _UAPI_VIDEO_SPRD_ADF_H_
#include <linux/types.h>
#include <video/adf.h>
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
#define SPRD_ADF_MAX_PLANE 4
enum {
 ADF_TRANSFORM_NONE = 0x00,
 ADF_TRANSFORM_FLIP_H = 0x01,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
 ADF_TRANSFORM_FLIP_V = 0x02,
 ADF_TRANSFORM_ROT_90 = 0x04,
 ADF_TRANSFORM_ROT_180 = 0x03,
 ADF_TRANSFORM_ROT_270 = 0x07,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
 ADF_TRANSFORM_RESERVED = 0x08,
};
enum {
 ADF_SCALE_NONE = 0,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
};
enum {
 ADF_UNCOMPRESSED,
 ADF_COMPRESSED,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
};
enum {
 ADF_BLENDING_NONE = 0x0100,
 ADF_BLENDING_PREMULT = 0x0105,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
 ADF_BLENDING_COVERAGE = 0x0405
};
enum {
 ADF_PIXEL_FORMAT_RGBA_8888 = 1,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
 ADF_PIXEL_FORMAT_RGBX_8888 = 2,
 ADF_PIXEL_FORMAT_RGB_888 = 3,
 ADF_PIXEL_FORMAT_RGB_565 = 4,
 ADF_PIXEL_FORMAT_BGRA_8888 = 5,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
 ADF_PIXEL_FORMAT_sRGB_A_8888 = 0xC,
 ADF_PIXEL_FORMAT_sRGB_X_8888 = 0xD,
 ADF_PIXEL_FORMAT_YV12 = 0x32315659,
 ADF_PIXEL_FORMAT_Y8 = 0x20203859,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
 ADF_PIXEL_FORMAT_Y16 = 0x20363159,
 ADF_PIXEL_FORMAT_RAW16 = 0x20,
 ADF_PIXEL_FORMAT_RAW_SENSOR = 0x20,
 ADF_PIXEL_FORMAT_RAW10 = 0x25,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
 ADF_PIXEL_FORMAT_RAW_OPAQUE = 0x24,
 ADF_PIXEL_FORMAT_BLOB = 0x21,
 ADF_PIXEL_FORMAT_IMPLEMENTATION_DEFINED = 0x22,
 ADF_PIXEL_FORMAT_YCbCr_420_888 = 0x23,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
 ADF_PIXEL_FORMAT_YCbCr_422_SP = 0x10,
 ADF_PIXEL_FORMAT_YCrCb_420_SP = 0x11,
 ADF_PIXEL_FORMAT_YCbCr_422_P = 0x12,
 ADF_PIXEL_FORMAT_YCbCr_420_P = 0x13,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
 ADF_PIXEL_FORMAT_YCbCr_422_I = 0x14,
 ADF_PIXEL_FORMAT_YCbCr_420_I = 0x15,
 ADF_PIXEL_FORMAT_CbYCrY_422_I = 0x16,
 ADF_PIXEL_FORMAT_CbYCrY_420_I = 0x17,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
 ADF_PIXEL_FORMAT_YCbCr_420_SP_TILED = 0x18,
 ADF_PIXEL_FORMAT_YCbCr_420_SP = 0x19,
 ADF_PIXEL_FORMAT_YCrCb_420_SP_TILED = 0x1A,
 ADF_PIXEL_FORMAT_YCrCb_422_SP = 0x1B,
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
 ADF_PIXEL_FORMAT_YCrCb_420_P = 0x1C,
};
struct sprd_adf_device_capability {
 __u32 device_id;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
};
struct sprd_adf_interface_capability {
 __u32 interface_id;
 __u32 fb_count;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
 __u32 fb_format;
};
struct sprd_adf_hwlayer_capability {
 __u32 hwlayer_id;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
 __u32 format;
 __u32 rotation;
 __u32 scale;
 __u32 blending;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
};
struct sprd_adf_overlayengine_capability {
 __u32 number_hwlayer;
 __u32 format;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
 __u32 rotation;
 __u32 scale;
 __u32 blending;
 union {
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
 struct sprd_adf_hwlayer_capability hwlayers[0];
 const struct sprd_adf_hwlayer_capability
 *hwlayer_ptr[SPRD_ADF_MAX_PLANE];
 };
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
};
struct sprd_adf_hwlayer_custom_data {
 __u32 interface_id;
 __u32 hwlayer_id;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
 __u32 buffer_id;
 __u32 alpha;
 __s16 dst_x;
 __s16 dst_y;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
 __u16 dst_w;
 __u16 dst_h;
 __u32 blending;
 __u32 rotation;
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
 __u32 scale;
 __u32 compression;
};
struct sprd_adf_post_custom_data {
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
 __u32 version;
 __u32 num_interfaces;
 __s32 retire_fence;
 struct sprd_adf_hwlayer_custom_data hwlayers[0];
/* WARNING: DO NOT EDIT, AUTO-GENERATED CODE - SEE TOP FOR INSTRUCTIONS */
};
#endif
