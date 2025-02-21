#!/bin/sh


sdb shell mkdir /data/gsp
sdb shell rm /data/gsp/* -rf
sdb shell mkdir /data/gsp/out

sdb push ./utest_gsp /data/gsp/
sdb push ./640x480_YUV420SP.raw /data/gsp/

sdb shell rm /data/gsp/out/*


#phy buffer: YUV420 2P 640x480-> YUV4202P 320x480 
sdb shell /data/gsp/utest_gsp \
-f0 /data/gsp/640x480_YUV420SP.raw -cf0 4 -pw0 640 -ph0 480 -ix0 0 -iy0 0 -iw0 640 -ih0 480 -rot0 1 -ox0 0 -oy0 0 -ow0 320 -oh0 480 \
-fd /data/gsp/out/320x480_YUV420SP.raw -cfd 4 -pwd 320 -phd 480

#virt buffer: YUV420 2P 640x480-> YUV4202P 320x480 
 sdb shell /data/gsp/utest_gsp \
-f0 /data/gsp/640x480_YUV420SP.raw -cf0 4 -bt0 1 -pw0 640 -ph0 480 -ix0 0 -iy0 0 -iw0 640 -ih0 480 -rot0 1 -ox0 0 -oy0 0 -ow0 320 -oh0 480 \
-fd /data/gsp/out/320x480_YUV420SP.raw -cfd 4 -btd 1 -pwd 320 -phd 480

# virt YUV420 2P 640x480 --cpy--> phy YUV420 2P 640x480 --GSP--> phy YUV4202P 320x480 
sdb shell /data/gsp/utest_gsp \
-f0 /data/gsp/640x480_YUV420SP.raw -cf0 4 -bt0 1 -pw0 640 -ph0 480 -ix0 0 -iy0 0 -iw0 640 -ih0 480 -rot0 1 -ox0 0 -oy0 0 -ow0 320 -oh0 480 -cpy0 1 -cbt0 0 \
-fd /data/gsp/out/320x480_YUV420SP.raw -cfd 4 -btd 0 -pwd 320 -phd 480





adb shell /data/gsp/utest_gsp \
-f0 /data/gsp/640x480_YUV420SP.raw -cf0 4 -pw0 640 -ph0 480 -ix0 0 -iy0 0 -iw0 640 -ih0 480 -rot0 1 -ox0 0 -oy0 0 -ow0 540 -oh0 960 \
-fd /data/gsp/out/540x960_AGRB888.raw -cfd 0 -pwd 540 -phd 960


adb shell /data/gsp/utest_gsp \
-f0 /data/gsp/640x480_YUV420SP.raw -cf0 4 -pw0 640 -ph0 480 -ix0 0 -iy0 0 -iw0 640 -ih0 480 -rot0 1 -ox0 0 -oy0 0 -ow0 540 -oh0 960 \
-fd /data/gsp/out/540x960_YUV4202P.raw -cfd 0 -pwd 540 -phd 960


adb shell /media/sdcard/gsp/utest_gsp \
-f0 /data/gsp/1280x720_YUV420SP.raw -cf0 4 -pw0 1280 -ph0 720 -ix0 0 -iy0 0 -iw0 1280 -ih0 720 -rot0 3 -ox0 0 -oy0 0 -ow0 452 -oh0 800 \
-fd /data/gsp/out/480x800_YUV4202P.raw -cfd 4 -pwd 480 -phd 800














