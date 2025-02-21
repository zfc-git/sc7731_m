#!/bin/sh
set_cpus()
{
	local n=$1
	while [ 1 ]
	do
		[ -d $path$n ] || break
		$2 $3 $n
		let n+=1
	done
	return $n
}
err_exit()
{
	echo "error:$1 "
}
cpu_online()
{
	local re=`cat $path$2"/online"`
	[ $re == $1 ] || echo $1 >$path$2"/online"
}
cpu_gov()
{
	echo $1 >$path$2"/cpufreq/scaling_governor"
	[ $? == 0 ] || err_exit "echo $1 >"$path$2"/cpufreq/scaling_governor"
}
hot_setup()
{
	echo 123 > /sys/power/wake_lock
	echo 1 > $hotplug
	[ $? == 0 ] || err_exit "echo 1 >$hotplug"
	set_cpus 0 cpu_gov userspace
	set_cpus 1 cpu_online 1
	echo -e "set all cpus online.\nstart to test cpu hotplug ..."
}

#$1=1,2,3,$2=$?
hotplug_reslut()
{
	eval cpu_c=\$cpu$1_c
	eval cpu_f=\$cpu$1_f
	eval state=\$state$1
	[ $2 -eq 0 ] && result="success" || result="failed"
	[ $2 -eq 0 ] || let cpu_f+=1
	[ $state -eq 0 ] && line="offline" || line="online"
	[ $state -eq 0 ] && state=1 || state=0
	let cpu_c+=1
	echo "set cpu$1 $line $result"
	eval cpu$1_c=$cpu_c
	eval cpu$1_f=$cpu_f
	eval state$1=$state
}
random()
{
	let rv=$RANDOM%$1
	echo $rv
}
hotplug_state()
{
	eval echo "set cpu$2 on/off line \$cpu$2_c times,failed \$cpu$2_f times"
}
hotplug_test()
{
	let n_cpu-=1
	local ret=0
	while [ 1 ]
	do
		ret=`random $n_cpu`
		let ret+=1
		eval cm=\$state$ret
		echo $cm > $path$ret"/online"
		hotplug_reslut $ret $?
		sleep $interval
		set_cpus 1 hotplug_state 0 > $log"cpuhotplug.state"
		[ $count -gt 0 ] && let count-=1
		[ $count -eq 0 ] && break
	done
}

hot_cleanup()
{
	set_cpus 1 cpu_online 1
	set_cpus 0 cpu_gov $default_govr
	echo 0 > $hotplug
	echo 123 > /sys/power/wake_unlock
	[ -f $bright ] && echo 2 >$bright >/dev/null 2>&1
	exit 0
}

cpu_param()
{
	eval cpu$2_c=$1
	eval cpu$2_f=$1
	eval state$2=$1
}
#main function
[ -z $1 ] && count=10 && interval=1 || count=$1
[ -z $2 ] && interval=1 || interval=$2
echo "we will test cpu hotplug $count times every $interval second!"
bright="/sys/class/leds/keyboard-backlight/brightness"
path="/sys/devices/system/cpu/cpu"
path1="/sys/devices/system/cpu/cpuhotplug/cpu_hotplug_disable"
[ -f $path1 ] && hotplug=$path1 || hotplug=$path"freq/sprdemand/cpu_hotplug_disable"
default_govr=`cat $path"0/cpufreq/scaling_governor"`
log="/data/"
set_cpus 1 cpu_param 0
n_cpu=$?
hot_setup
hotplug_test > $log"cpuhotplug.log"
hot_cleanup
