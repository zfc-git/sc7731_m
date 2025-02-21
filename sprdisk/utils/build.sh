#!/bin/sh

usage()
{
cat <<EOM

Usage:
    build [-a] [-j <N>]
    build -m <module> [-j <N>]
    build -p
    build -q
    build -c
    build -h

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
        Pack the ramdisk.img into the boot.img to be sprdiskboot.img.

    -c
        Clean all the build objects and output

    -h
        Show this help

EOM
}

# check return error
check_err()
{
	if [ $? -ne 0 ]; then
		echo Error: $* >&2
		exit 2
	fi
}

check_usage()
{
	if [ $? -ne 0 ]; then
		echo
		echo Error: $* >&2
		usage
		exit 2
	fi
}

build_rootfs()
{
	echo "==== build_rootfs ===="

	cd $SRC_ROOTFS
	check_err "$SRC_ROOTFS is not found!"

	if [ ! -e .config ]; then
		$MAKE O=$OUTPUTDIR buildroot_sprd_${SPRD_ARCH}_defconfig
		check_err "Kernel .config is missing!"
	fi

	$MAKE O=$OUTPUTDIR -j $JOBS
	check_err "Failed to build rootfs!"

	echo "==== build_rootfs done! ===="
}

build_ltp()
{
	echo "==== build_ltp ===="

	cd $SRC_LTP
	check_err "$SRC_LTP is not found!"

	if [ ! -e ./configure ]; then
		$MAKE O=$OUTPUTDIR autotools
	fi

	if [ ! -e include/mk/config.mk ]; then
		platform=$(echo ${CROSS_COMPILE%%-*})-linux
		./configure \
			CC=${CROSS_COMPILE}gcc \
			AR=${CROSS_COMPILE}ar \
			STRIP=${CROSS_COMPILE}strip \
			RANLIB=${CROSS_COMPILE}ranlib \
			--build=i686-pc-linux-gnu \
			--target=$platform --host=$platform \
			--prefix=$INSTDIR/opt/ltp
		check_err "Failed to configure ltp!"
	fi

	$MAKE O=$OUTPUTDIR -j $JOBS
	check_err "Failed to build ltp!"

	$MAKE O=$OUTPUTDIR install
	check_err "Failed to install ltp!"

	echo "==== build_ltp done! ===="
}

build_utest()
{
	echo "==== build_utest ===="

	cd $SRC_UTEST
	check_err "$SRC_UTEST is not found!"

	if [ ! -d "$INSTDIR/../build" ]; then
		mkdir -p $INSTDIR/../build
	fi
	echo "CROSS_COMPILE    := $CROSS_COMPILE" > $INSTDIR/../build/utest.config

	$MAKE O=$OUTPUTDIR  install
	check_err "Failed to install utest!"

	echo "==== build_utest done! ===="
}

pack_ramdisk()
{
	echo "==== pack_ramdisk ===="

	cp $TOPDIR/utils/adbd $INSTDIR/bin

	cd $INSTDIR
	check_err "$INSTDIR is not found!"

	rm -rf THIS_IS_NOT_YOUR_ROOT_FILESYSTEM

	# strip all binaries
	bins=`find * -type f -perm /111`
	for exe in $bins; do
		${CROSS_COMPILE}strip $exe 2>/dev/null
	done

	fakeroot -- $TOPDIR/utils/mkrootfs.sh $TOPDIR/$IMG
	check_err "Failed to pack the ramdisk !"

	echo "$TOPDIR/$IMG created!"

	echo "==== pack_ramdisk done ===="
}

pack__bootimage()
{
	echo "==== pack_bootimage ===="
	rm -f $ANDROID_PRODUCT_OUT/ramdisk.img
	$ANDROID_HOST_OUT/bin/mkbootfs $ANDROID_PRODUCT_OUT/root | $ANDROID_HOST_OUT/bin/minigzip > $ANDROID_PRODUCT_OUT/ramdisk.img
	DT_SUPPORT=$(get_build_var BOARD_KERNEL_SEPARATED_DT)
	if [ "$DT_SUPPORT" = "true" ]; then
	O_ARGS="--dt $ANDROID_PRODUCT_OUT/dt.img"
	fi
	$ANDROID_HOST_OUT/bin/mkbootimg  --kernel $ANDROID_PRODUCT_OUT/kernel --ramdisk $ANDROID_PRODUCT_OUT/ramdisk.img --cmdline "console=ttyS1,115200n8" --base 0x00000000  $O_ARGS --output $ANDROID_PRODUCT_OUT/boot.img
	echo "==== pack_bootimage done ===="
}

pack_sprdisk_2_bootimage()
{
	if [ -f "$TOPDIR/$IMG" -a -f "$TOPDIR/boot.img" ]; then
		mkdir $TOPDIR/out
		cp -rf $TOPDIR/$IMG  $TOPDIR/out
		$TOPDIR/utils/unpackbootimg -i $TOPDIR/boot.img -o $TOPDIR/out
		check_err "Failed to unpack the bootimage !"

		mv $TOPDIR/out/$IMG  $TOPDIR/out/boot.img-ramdisk.gz

		if [ -f "$TOPDIR/out/boot.img-dt" ]; then
			withdtimg="--dt $TOPDIR/out/boot.img-dt"
		else
			withdtimg=
		fi
		$TOPDIR/utils/mkbootimg  --kernel $TOPDIR/out/boot.img-zImage --ramdisk $TOPDIR/out/boot.img-ramdisk.gz --cmdline "console=ttyS1,115200n8" --base 0x00000000  $withdtimg --output $TOPDIR/sprdiskboot.img
		check_err "Failed to pack the bootimage !"

		rm -rf $TOPDIR/out
		echo "$TOPDIR/sprdiskboot.img created!"
	else
		echo "Be lack of img file."
	fi
}

pack_sprdiskboot()
{
	echo "==== pack_sprdiskboot ===="

	cp -rf $ANDROID_PRODUCT_OUT/boot.img $TOPDIR

	pack_sprdisk_2_bootimage

	echo "==== pack_sprdisk done ===="
}

repack_userdata_img()
{
	userdatainfo=$ANDROID_PRODUCT_OUT/obj/PACKAGING/userdata_intermediates/userdata_image_info.txt
	userdatasize=2000000000
	ramdisksize=0
	while read line
	do
		item=$(echo $line | (awk -F "=" '{print $1}') | tr -d ' ')
		if [ $item = "userdata_size" ] ; then
			userdatasize=$(echo $line | (awk -F "=" '{print $2}') | tr -d ' ')
			userdatasize=$(expr $userdatasize + 0)
			break
		fi
	done < $userdatainfo
	mkfs=$ANDROID_HOST_OUT/bin/make_ext4fs
	if [ -f $mkfs -a -d $sprdir -a -f "$ANDROID_PRODUCT_OUT/userdata.img" ]; then
		make_ext4fs -s -T -1 -S $ANDROID_PRODUCT_OUT/root/file_contexts -l $userdatasize -a data $ANDROID_PRODUCT_OUT/userdata.img $ANDROID_PRODUCT_OUT/data
		echo "==== repack_userdata done ===="
	fi
}

move_img_2_android()
{
	sprdir=$ANDROID_PRODUCT_OUT/data/sprdisk
	if [ ! -d  $sprdir ]; then
		 mkdir -p $sprdir
	fi
	if [ -f  $TOPDIR/$IMG ]; then
		 cp -rf $TOPDIR/$IMG  $sprdir
	else
		 echo "==== no ramdisk.img generated, not move to android. ===="
	fi
	if [ -f  $TOPDIR/sprdiskboot.img ]; then
		 cp -rf $TOPDIR/sprdiskboot.img  $ANDROID_PRODUCT_OUT
	else
		 echo "==== no sprdiskboot.img generated, not move to android. ===="
	fi

	repack_userdata_img
}

judge_sprdiskboot()
{
	if [ -n "${ANDROID_PRODUCT_OUT}" ]; then

		if [ ! -f "$ANDROID_PRODUCT_OUT/boot.img" ]; then
			if [ ! -f "$ANDROID_PRODUCT_OUT/kernel" ]; then
				echo "==== no kernel generated, no boot.img, not pack sprdiskboot, very serious!!! ===="
			else
				pack__bootimage
				pack_sprdiskboot
				move_img_2_android
			fi
		else
			pack_sprdiskboot
			move_img_2_android
		fi
	else
		echo "==== no product generated, not pack sprdiskboot. ===="
	fi
}

build_all()
{
	build_rootfs
	build_ltp
	build_utest
	pack_ramdisk
	judge_sprdiskboot
}

clean_objs()
{
	echo "==== clean_objs ===="
	cd $SRC_UTEST
	$MAKE O=$OUTPUTDIR -s clean
	cd $SRC_ROOTFS
	$MAKE O=$OUTPUTDIR -s distclean
	cd $SRC_LTP
	$MAKE O=$OUTPUTDIR -s distclean
	if [ -d "$OUTPUTDIR" ]; then
		rm -rf $OUTPUTDIR
	fi
	if [ -f "$SRC_ROOTFS/.config" ]; then
		rm -rf $SRC_ROOTFS/.config
	fi
	if [ -f "$SRC_ROOTFS/..config.tmp" ]; then
		rm -rf $SRC_ROOTFS/..config.tmp
	fi
	echo "==== clean_objs done ===="
}


MAKE=make
JOBS=4
MODULE=all
IMG=ramdisk.img

TOPDIR=$(dirname `readlink -f $0`)/..

SRC_ROOTFS=$TOPDIR/buildroot
SRC_LTP=$TOPDIR/ltp
SRC_UTEST=$TOPDIR/testsuites

case $1 in
	x86|x86_64)
		TOOLCHAIN=$TOPDIR/toolchain/x86_64-linux-android-4.9
		CROSS_COMPILE=x86_64-linux-android-
		exit 0
		;;
	arm)
		TOOLCHAIN=$TOPDIR/toolchain/linaro-arm-linux-gcc
		CROSS_COMPILE=arm-linux-gnueabihf-
		;;
	arm64)
		TOOLCHAIN=$TOPDIR/toolchain/aarch64-linux-gnu
		CROSS_COMPILE=aarch64-linux-gnu-
		;;
	mips)
		TOOLCHAIN=$TOPDIR/toolchain/mipsel-linux-android-4.8
		CROSS_COMPILE=mipsel-linux-android-
		;;
	mips64)
		TOOLCHAIN=$TOPDIR/toolchain/mips64el-linux-android-4.9
		CROSS_COMPILE=mips64el-linux-android-
		exit 0
		;;
	*)
	    echo "Can't find toolchain for unknown architecture: $1"
	    exit 0
	    ;;
esac

SPRD_ARCH=$1
export PATH=$PATH:$TOOLCHAIN/bin:$TOPDIR/utils

if [ -d "$ANDROID_PRODUCT_OUT" ]; then
	INSTDIR=$ANDROID_PRODUCT_OUT/sprdisk/target
	export PATH=$PATH:$ANDROID_PRODUCT_OUT/sprdisk/host/usr/bin
	OUTPUTDIR=$ANDROID_PRODUCT_OUT/sprdisk
else
	INSTDIR=$SRC_ROOTFS/output/target
	export PATH=$PATH:$SRC_ROOTFS/output/host/usr/bin
	OUTPUTDIR=$SRC_ROOTFS/output
fi

while [ -n "$2" ]; do
	case "$2" in
	-a)
		MODULE=all
		;;
	-m)
		test -n "$3"
		check_usage "No module is specified!"
		MODULE=$3
		shift
		;;
	-j)
		test -n "$3"
		check_usage "No job number is specified!"
		JOBS=$3
		shift
		;;
	-p)
		pack_ramdisk
		judge_sprdiskboot
		exit 0
		;;
	-q)
		pack_sprdisk_2_bootimage
		exit 0
		;;
	-c)
		clean_objs
		exit 0
		;;
	-h)
		usage
		exit 0
		;;
	*)
		echo
		echo "Unknown options: $2"
		usage
		exit 1
		;;
	esac
	shift
done

if [ ! -d "$ANDROID_PRODUCT_OUT" ]; then
	mkdir -p $INSTDIR
	check_err "Failed to create $INSTDIR!"
fi

build_$MODULE

