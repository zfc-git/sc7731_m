/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include <fcntl.h>
#include <stdio.h>

#include <sys/ioctl.h>
#include <sys/mman.h>
#include <sys/types.h>

#include <linux/fb.h>
#include <linux/kd.h>

#include <png.h>

#include "minui.h"

extern char* locale;

#define SURFACE_DATA_ALIGNMENT 8

static GRSurface* malloc_surface(size_t data_size) {
    size_t size = sizeof(GRSurface) + data_size + SURFACE_DATA_ALIGNMENT;
    unsigned char* temp = reinterpret_cast<unsigned char*>(malloc(size));
    if (temp == NULL) return NULL;
    GRSurface* surface = reinterpret_cast<GRSurface*>(temp);
    surface->data = temp + sizeof(GRSurface) +
        (SURFACE_DATA_ALIGNMENT - (sizeof(GRSurface) % SURFACE_DATA_ALIGNMENT));
    return surface;
}

static int open_png(const char* name, png_structp* png_ptr, png_infop* info_ptr,
                    png_uint_32* width, png_uint_32* height, png_byte* channels) {
    char resPath[256];
    unsigned char header[8];
    int result = 0;
    int color_type, bit_depth;
    size_t bytesRead;

    snprintf(resPath, sizeof(resPath)-1, "/res/images/%s.png", name);
    resPath[sizeof(resPath)-1] = '\0';
    FILE* fp = fopen(resPath, "rb");
    if (fp == NULL) {
        result = -1;
        goto exit;
    }

    bytesRead = fread(header, 1, sizeof(header), fp);
    if (bytesRead != sizeof(header)) {
        result = -2;
        goto exit;
    }

    if (png_sig_cmp(header, 0, sizeof(header))) {
        result = -3;
        goto exit;
    }

    *png_ptr = png_create_read_struct(PNG_LIBPNG_VER_STRING, NULL, NULL, NULL);
    if (!*png_ptr) {
        result = -4;
        goto exit;
    }

    *info_ptr = png_create_info_struct(*png_ptr);
    if (!*info_ptr) {
        result = -5;
        goto exit;
    }

    if (setjmp(png_jmpbuf(*png_ptr))) {
        result = -6;
        goto exit;
    }

    png_init_io(*png_ptr, fp);
    png_set_sig_bytes(*png_ptr, sizeof(header));
    png_read_info(*png_ptr, *info_ptr);

    png_get_IHDR(*png_ptr, *info_ptr, width, height, &bit_depth,
            &color_type, NULL, NULL, NULL);

    *channels = png_get_channels(*png_ptr, *info_ptr);

    printf("bit_depth:%d,*channels:%d,color_type:%d\n",bit_depth,*channels,color_type);
    if (bit_depth == 8 && *channels == 3 && color_type == PNG_COLOR_TYPE_RGB) {
        // 8-bit RGB images: great, nothing to do.
    } else if (bit_depth <= 8 && *channels == 1 && color_type == PNG_COLOR_TYPE_GRAY) {
        // 1-, 2-, 4-, or 8-bit gray images: expand to 8-bit gray.
        png_set_expand_gray_1_2_4_to_8(*png_ptr);
    } else if (bit_depth <= 8 && *channels == 1 && color_type == PNG_COLOR_TYPE_PALETTE) {
        // paletted images: expand to 8-bit RGB.  Note that we DON'T
        // currently expand the tRNS chunk (if any) to an alpha
        // channel, because minui doesn't support alpha channels in
        // general.
        png_set_palette_to_rgb(*png_ptr);
        *channels = 3;
    } else {
        fprintf(stderr, "minui doesn't support PNG depth %d channels %d color_type %d\n",
                bit_depth, *channels, color_type);
        result = -7;
        goto exit;
    }

    return result;

  exit:
    if (result < 0) {
        png_destroy_read_struct(png_ptr, info_ptr, NULL);
    }
    if (fp != NULL) {
        fclose(fp);
    }

    return result;
}

// "display" surfaces are transformed into the framebuffer's required
// pixel format (currently only RGBX is supported) at load time, so
// gr_blit() can be nothing more than a memcpy() for each row.  The
// next two functions are the only ones that know anything about the
// framebuffer pixel format; they need to be modified if the
// framebuffer format changes (but nothing else should).

// Allocate and return a GRSurface* sufficient for storing an image of
// the indicated size in the framebuffer pixel format.
static GRSurface* init_display_surface(png_uint_32 width, png_uint_32 height) {
    GRSurface* surface = malloc_surface(width * height * 4);
    if (surface == NULL) return NULL;

    surface->width = width;
    surface->height = height;
    surface->row_bytes = width * 4;
    surface->pixel_bytes = 4;

    return surface;
}

// Copy 'input_row' to 'output_row', transforming it to the
// framebuffer pixel format.  The input format depends on the value of
// 'channels':
//
//   1 - input is 8-bit grayscale
//   3 - input is 24-bit RGB
//   4 - input is 32-bit RGBA/RGBX
//
// 'width' is the number of pixels in the row.
static void transform_rgb_to_draw(unsigned char* input_row,
                                  unsigned char* output_row,
                                  int channels, int width) {
    int x;
    unsigned char* ip = input_row;
    unsigned char* op = output_row;

    switch (channels) {
        case 1:
            // expand gray level to RGBX
            for (x = 0; x < width; ++x) {
                *op++ = *ip;
                *op++ = *ip;
                *op++ = *ip;
                *op++ = 0xff;
                ip++;
            }
            break;

        case 3:
            // expand RGBA to RGBX
            for (x = 0; x < width; ++x) {
                *op++ = *ip++;
                *op++ = *ip++;
                *op++ = *ip++;
                *op++ = 0xff;
            }
            break;

        case 4:
            // copy RGBA to RGBX
            memcpy(output_row, input_row, width*4);
            break;
    }
}

int res_create_display_surface(const char* name, GRSurface** pSurface) {
    GRSurface* surface = NULL;
    int result = 0;
    png_structp png_ptr = NULL;
    png_infop info_ptr = NULL;
    png_uint_32 width, height;
    png_byte channels;
    unsigned char* p_row;
    unsigned int y;

    *pSurface = NULL;

    result = open_png(name, &png_ptr, &info_ptr, &width, &height, &channels);
    if (result < 0) return result;

    surface = init_display_surface(width, height);
    if (surface == NULL) {
        result = -8;
        goto exit;
    }

#if defined(RECOVERY_ABGR) || defined(RECOVERY_BGRA)
    png_set_bgr(png_ptr);
#endif

    p_row = reinterpret_cast<unsigned char*>(malloc(width * 4));
    for (y = 0; y < height; ++y) {
        png_read_row(png_ptr, p_row, NULL);
        transform_rgb_to_draw(p_row, surface->data + y * surface->row_bytes, channels, width);
    }
    free(p_row);

    *pSurface = surface;

  exit:
    png_destroy_read_struct(&png_ptr, &info_ptr, NULL);
    if (result < 0 && surface != NULL) free(surface);
    return result;
}

int res_create_multi_display_surface(const char* name, int* frames, GRSurface*** pSurface) {
    GRSurface** surface = NULL;
    int result = 0;
    png_structp png_ptr = NULL;
    png_infop info_ptr = NULL;
    png_uint_32 width, height;
    png_byte channels;
    int i;
    png_textp text;
    int num_text;
    unsigned char* p_row;
    unsigned int y;

    *pSurface = NULL;
    *frames = -1;

    result = open_png(name, &png_ptr, &info_ptr, &width, &height, &channels);
    if (result < 0) return result;

    *frames = 1;
    if (png_get_text(png_ptr, info_ptr, &text, &num_text)) {
        for (i = 0; i < num_text; ++i) {
            if (text[i].key && strcmp(text[i].key, "Frames") == 0 && text[i].text) {
                *frames = atoi(text[i].text);
                break;
            }
        }
        printf("  found frames = %d\n", *frames);
    }

    if (height % *frames != 0) {
        printf("bad height (%d) for frame count (%d)\n", height, *frames);
        result = -9;
        goto exit;
    }

    surface = reinterpret_cast<GRSurface**>(malloc(*frames * sizeof(GRSurface*)));
    if (surface == NULL) {
        result = -8;
        goto exit;
    }
    for (i = 0; i < *frames; ++i) {
        surface[i] = init_display_surface(width, height / *frames);
        if (surface[i] == NULL) {
            result = -8;
            goto exit;
        }
    }

#if defined(RECOVERY_ABGR) || defined(RECOVERY_BGRA)
    png_set_bgr(png_ptr);
#endif

    p_row = reinterpret_cast<unsigned char*>(malloc(width * 4));
    for (y = 0; y < height; ++y) {
        png_read_row(png_ptr, p_row, NULL);
        int frame = y % *frames;
        unsigned char* out_row = surface[frame]->data +
            (y / *frames) * surface[frame]->row_bytes;
        transform_rgb_to_draw(p_row, out_row, channels, width);
    }
    free(p_row);

    *pSurface = reinterpret_cast<GRSurface**>(surface);

exit:
    png_destroy_read_struct(&png_ptr, &info_ptr, NULL);

    if (result < 0) {
        if (surface) {
            for (i = 0; i < *frames; ++i) {
                if (surface[i]) free(surface[i]);
            }
            free(surface);
        }
    }
    return result;
}

int res_create_alpha_surface(const char* name, GRSurface** pSurface) {
    GRSurface* surface = NULL;
    int result = 0;
    png_structp png_ptr = NULL;
    png_infop info_ptr = NULL;
    png_uint_32 width, height;
    png_byte channels;

    *pSurface = NULL;

    result = open_png(name, &png_ptr, &info_ptr, &width, &height, &channels);
    if (result < 0) return result;

    if (channels != 1) {
        result = -7;
        goto exit;
    }

    surface = malloc_surface(width * height);
    if (surface == NULL) {
        result = -8;
        goto exit;
    }
    surface->width = width;
    surface->height = height;
    surface->row_bytes = width;
    surface->pixel_bytes = 1;

#if defined(RECOVERY_ABGR) || defined(RECOVERY_BGRA)
    png_set_bgr(png_ptr);
#endif

    unsigned char* p_row;
    unsigned int y;
    for (y = 0; y < height; ++y) {
        p_row = surface->data + y * surface->row_bytes;
        png_read_row(png_ptr, p_row, NULL);
    }

    *pSurface = surface;

  exit:
    png_destroy_read_struct(&png_ptr, &info_ptr, NULL);
    if (result < 0 && surface != NULL) free(surface);
    return result;
}

static int matches_locale(const char* loc, const char* locale) {
    if (locale == NULL) return 0;

    if (strcmp(loc, locale) == 0) return 1;

    // if loc does *not* have an underscore, and it matches the start
    // of locale, and the next character in locale *is* an underscore,
    // that's a match.  For instance, loc == "en" matches locale ==
    // "en_US".

    int i;
    for (i = 0; loc[i] != 0 && loc[i] != '_'; ++i);
    if (loc[i] == '_') return 0;

    return (strncmp(locale, loc, i) == 0 && locale[i] == '_');
}

int res_create_localized_alpha_surface(const char* name,
                                       const char* locale,
                                       GRSurface** pSurface) {
    GRSurface* surface = NULL;
    int result = 0;
    png_structp png_ptr = NULL;
    png_infop info_ptr = NULL;
    png_uint_32 width, height;
    png_byte channels;
    unsigned char* row;
    png_uint_32 y;

    *pSurface = NULL;

    if (locale == NULL) {
        surface = malloc_surface(0);
        surface->width = 0;
        surface->height = 0;
        surface->row_bytes = 0;
        surface->pixel_bytes = 1;
        goto exit;
    }

    result = open_png(name, &png_ptr, &info_ptr, &width, &height, &channels);
    if (result < 0) return result;

    if (channels != 1) {
        result = -7;
        goto exit;
    }

    row = reinterpret_cast<unsigned char*>(malloc(width));
    for (y = 0; y < height; ++y) {
        png_read_row(png_ptr, row, NULL);
        int w = (row[1] << 8) | row[0];
        int h = (row[3] << 8) | row[2];
        int len = row[4];
        char* loc = (char*)row+5;

        if (y+1+h >= height || matches_locale(loc, locale)) {
            printf("  %20s: %s (%d x %d @ %d)\n", name, loc, w, h, y);

            surface = malloc_surface(w*h);
            if (surface == NULL) {
                result = -8;
                goto exit;
            }
            surface->width = w;
            surface->height = h;
            surface->row_bytes = w;
            surface->pixel_bytes = 1;

            int i;
            for (i = 0; i < h; ++i, ++y) {
                png_read_row(png_ptr, row, NULL);
                memcpy(surface->data + i*w, row, w);
            }

            *pSurface = reinterpret_cast<GRSurface*>(surface);
            break;
        } else {
            int i;
            for (i = 0; i < h; ++i, ++y) {
                png_read_row(png_ptr, row, NULL);
            }
        }
    }

exit:
    png_destroy_read_struct(&png_ptr, &info_ptr, NULL);
    if (result < 0 && surface != NULL) free(surface);
    return result;
}

void res_free_surface(GRSurface* surface) {
    free(surface);
}
