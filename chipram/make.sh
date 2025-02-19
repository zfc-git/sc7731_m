export PATH=$PATH:$(pwd)/../prebuilts/gcc/linux-x86/aarch64/aarch64-linux-android-4.9/bin
export make ARM_EABI_TOOLCHAIN=$(pwd)/../prebuilts/gcc/linux-x86/aarch64/aarch64-linux-android-4.9/bin
export BUILD_DIR=./out
export make CROSS_COMPILE=aarch64-linux-android-
make distclean
make spwhale_fpga_config
make
