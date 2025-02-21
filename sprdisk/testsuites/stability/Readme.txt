
The "stability" includes the unit test cases about stability such as reboot sleep-wakeup etc.

The top Makefile.config file may get and export the following environments for source build,

	CROSS_COMPILE
	ARCH
	CC
	CPP
	LD
	AR
	STRIP
	RANLIB
	CFLAGS
	CPPFLAGS
	LDFLAGS

$(VROOT) is a target rootfs that the program will be installed.

# to build
	make build

# to install
	make install

# to clean
	make clean

