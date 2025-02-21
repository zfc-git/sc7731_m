#!/bin/sh
out=$1
log="/data/sleep.log"
deep_sleep()
{
	rtc="/opt/ltp/testcases/bin/sprd_rtc_test1"
	bright="/sys/class/leds/keyboard-backlight/brightness"
	[ -x $rtc ] || return 0
#	echo 8 >/proc/sys/kernel/printk
	local i=1
	[ -f $bright ] && echo 2 >$bright >/dev/null 2>&1
	echo "start to test sleep/wakeup $out times..."
	while [ $i -le $out ]
	do
		echo "test deep sleep $i times:"
		[ -f $bright ] && echo 0 >$bright >/dev/null 2>&1
		echo mem >/sys/power/state
		$rtc >/dev/null
		echo on >/sys/power/state
		[ -f $bright ] && echo 2 >$bright >/dev/null 2>&1
		sleep 9
		res="success"
		dmesg -c | grep "deep sleep.* times"
		[ $? -eq 0 ] || res="failed!!"
		echo "[$i]:wake up from deep sleep $res!!"
		let i+=1
	done
}

deep_sleep $1 > $log