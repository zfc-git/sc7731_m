#!/bin/bash

CC=$1
if [ ! -d "$ANDROID_PRODUCT_OUT/sprdisk" ]; then
	targetdir=$PWD/../../buildroot/output/target
else
	targetdir=$ANDROID_PRODUCT_OUT/sprdisk/target
fi
bindir=$targetdir/system/bin
if [ $CC = arm-linux-gnueabihf-gcc ]; then
	libdir=$targetdir/system/lib
else
	libdir=$targetdir/system/lib64
fi
xbindir=$targetdir/system/xbin

check_dir()
{
	if [ ! -d "$targetdir" ]; then
		mkdir -p $targetdir
		mkdir -p $bindir
		mkdir -p $libdir
		mkdir -p $xbindir
	else
		if [ ! -d "$bindir" ]; then
			mkdir -p $bindir
		fi
		if [ ! -d "$libdir" ]; then
			mkdir -p $libdir
		fi
		if [ ! -d "$xbindir" ]; then
			mkdir -p $xbindir
		fi
	fi
}

check_files_and_dirs()
{
	dirname=$1
	filename=$2
	if [ $CC = arm-linux-gnueabihf-gcc ]; then
		androidlibdir=$ANDROID_PRODUCT_OUT/system/lib
	else
		androidlibdir=$ANDROID_PRODUCT_OUT/system/lib64
	fi

	libfile=$androidlibdir/$filename
	if [ ! -f $libfile ]; then
		echo "Error: $libfile not exist."
		return 1
	else
		curlocal=$PWD/$dirname/output
		if [ ! -f $curlocal/$filename ]; then
			cp -rf $libfile $curlocal
		fi
		if [ ! -f $libdir/$filename ]; then
			cp -rf $libfile $libdir
		fi
		return 0
	fi
}


collect_needfiles()
{
	curtestdir=$1
	flag=0
	curlocal=$PWD/$curtestdir/output
	if [ ! -d "$curlocal" ]; then
		mkdir $curlocal
	fi

	linkerfile=$ANDROID_PRODUCT_OUT/system/bin/linker
	curlinkerfile=$curlocal/linker
	if [ $CC = aarch64-linux-gnu-gcc ]; then
		linkerfile=$ANDROID_PRODUCT_OUT/system/bin/linker64
		curlinkerfile=$curlocal/linker64
	fi

	if [ ! -f $curlinkerfile ]; then
		cp -rf $linkerfile $curlocal
	fi

	while read line
	do
		item=$(echo $line | (awk '{print $1}') | tr -d ' ')
		if [ $item = '' ]; then
			continue
		fi
		check_files_and_dirs $curtestdir $item
		if [ $? -eq 1 ]; then
			flag=1
			break
		fi
	done < $PWD/$curtestdir/libconfig

	if [ ! -f $ANDROID_PRODUCT_OUT/system/xbin/$curtestdir ]; then
		echo "Error: ${curtestdir} file not exist."
		return
	fi

	if [ $flag -eq 0 ]; then
		cp -rf $ANDROID_PRODUCT_OUT/system/xbin/$curtestdir	$curlocal
		cp -rf $ANDROID_PRODUCT_OUT/system/xbin/$curtestdir $xbindir
	fi
}

build_target_file()
{
	UTEST_GSPDIR=../../../vendor/sprd/open-source/tools/utest/utest_gsp
	mv $UTEST_GSPDIR/Android.mk  $UTEST_GSPDIR/Android.mk.bak
	sed -e 's/$(TARGET_BOARD_PLATFORM)/scx30gxxx/g' $UTEST_GSPDIR/Android.mk.bak > $UTEST_GSPDIR/Android.mk
	source ${PWD}/../../../build/envsetup.sh
	mmm $UTEST_GSPDIR
	mv $UTEST_GSPDIR/Android.mk.bak $UTEST_GSPDIR/Android.mk
	check_dir
	linkerfile=$ANDROID_PRODUCT_OUT/system/bin/linker
	if [ $CC = aarch64-linux-gnu-gcc ]; then
		linkerfile=$ANDROID_PRODUCT_OUT/system/bin/linker64
	fi

	if [ ! -f $linkerfile ]; then
		echo "Error: ${linkerfile} not exist."
		exit 0
	fi

	cp -rf $linkerfile $bindir

	for  mydir in  ` ls . `  ; do
		if [ -d "$mydir" -a "$mydir" != "testcase" ]; then
			collect_needfiles $mydir
		fi
	done
}


clean_target()
{
	for  mydir in  ` ls . ` ; do
		if [ -d "$mydir" ]; then
			rm -rf ${mydir}/output
		fi
	done
}

if [ $CC = -c ] ; then
	 clean_target
else
	 if [ -d "$ANDROID_PRODUCT_OUT" ]; then
		 build_target_file
		 if [ -d "$PWD/testcase" ]; then
				if [ ! -d "$targetdir/usr/bin" ]; then
					mkdir -p $targetdir/usr/bin
				fi
		 		cp -rf $PWD/testcase/*.sh	 $targetdir/usr/bin
		 fi
	 fi
fi
