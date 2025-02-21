#!/bin/sh

rm -rf dev
mkdir -p dev
mkdir -p dev/pts
mkdir -p dev/shm

mknod -m 0600 dev/tty c 5 0
mknod -m 0600 dev/console c 5 1
mknod -m 0600 dev/tty0 c 4 0
mknod -m 0600 dev/tty1 c 4 1
mknod -m 0600 dev/ttyS0 c 4 64
mknod -m 0600 dev/ttyS1 c 4 65

mknod -m 0600 dev/ram0 c 1 0
mknod -m 0600 dev/ram c 1 1
mknod -m 0600 dev/null c 1 3
mknod -m 0600 dev/zero c 1 5

mknod -m 0600 dev/mmcblk1p1 b 179 129

find * | cpio --quiet -o -H newc | gzip > $1

