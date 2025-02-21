#!/bin/sh
log="/data/dtest_err.log"
path="/data/test.txt"
sdpath=`busybox mount | grep media | busybox awk '{if(NR==1) print $3}'`
tpath=$sdpath"/test.txt"
size=1024
ttime=24
date >$log
echo 123 >/sys/power/wake_lock
[ -z $1 ] || [ $1 == "-T" ] && path=$tpath
[ -z $2 ] || size=$2
[ -z $3 ] || ttime=$3
echo "running ......"
disk_test -o $path -s $size -t $ttime 2>>$log
if [ $? == 2 ] ; then
	echo c >/proc/sysrq-trigger
fi
date >>$log