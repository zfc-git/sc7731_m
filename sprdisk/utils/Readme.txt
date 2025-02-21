Sprdisk 1.x <http://wikiserver.spreadtrum.com/Projects/SoftwareSystem/wiki/Sprdisk%E7%9B%B8%E5%85%B3%E6%96%87%E6%A1%A3#no1>
---------------------------------------------------------------------------------------------------------------------------
sprdisk is a ramdisk system that contains the following functions,

sprdisk/
├── buildroot      # buildroot project for basic rootfs
├── ltp            # linux test project for kernel test
├── testsuites     # sprd-customized test cases
├── toolchain      # cross toolchain
└── utils          # utils to build sprdisk

Usage:(In sprdisk root directory)

    make	target=arm(arm64)	arg="[-a] [-j <N>]"
    make	target=arm(arm64)	arg="-m <module> [-j <N>]"
    make	target=arm(arm64)	arg="-p"
    make	target=arm(arm64)	arg="-q"
    make	target=arm(arm64)	arg="-c"
    make	target=arm(arm64)	arg="-h"

    -a
        Build all components and output the ramdisk.img. (default)

    -m <module>
        Build the specified module and output to target dir
        in <top>/buildroot/output/target. The module may be
        the following names,

            rootfs     -- rootfs created from buildroot
            ltp        -- linux test project
            utest      -- customized testsuites

    -j
        Assign the paralleled build jobs number. (4 by default)

    -p
        Pack the ramdisk.img from the target dir.

    -q
        Pack the ramdisk.img into the boot.img to be sprdiskboot.img, boot.img should exist in sprdisk root directory.

    -c
        Clean all the build objects and output

    -h
        Show this help