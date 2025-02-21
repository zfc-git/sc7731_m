#!/bin/sh
#defaut use, show detail info
#add error select
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
	echo "error:$1, exit ..."
	freq_cleanup $2
}
cpu_online()
{
	local re=`cat $path$2"/online"`
	[ $re == 1 ] || echo $1 >$path$2"/online"
	[ $? == 0 ] || echo "cpu$2 online failed"
}
cpu_gov()
{
	echo $1 >$path$2"/cpufreq/scaling_governor"
	[ $? == 0 ] || err_exit "echo $1 >"$path$2"/cpufreq/scaling_governor" 3
}
freq_setup()
{
	echo 123 > /sys/power/wake_lock
	echo 1 > $hotplug
	[ $? == 0 ] || err_exit "echo 1 >$hotplug" 2
	set_cpus 0 cpu_gov userspace
	n_cps=$?
	set_cpus 1 cpu_online 1
	echo "start to test cpu dvfs ..."
}
freq_cleanup()
{
#	echo sprdemand > /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor
	[ $1 -ge 1 ] && {
		echo $default_freq > $path"0/cpufreq/scaling_setspeed"
		echo $default_govr > $path"0/cpufreq/scaling_governor"
	}
	[ $1 -ge 3 ] && echo 0 > $hotplug
	[ $1 -ge 2 ] && echo 123 > /sys/power/wake_unlock
	[ -f $bright ] && echo 2 >$bright >/dev/null 2>&1
	exit 0
}
check_freq()
{
	eval local lfreq=`cat $path$2"/cpufreq/scaling_cur_freq"`
	[ $lfreq -eq $1 ] || echo "check:cpu$2's freq is still $lfreq !"
}
freq_result()
{
	eval local lf=`cat $path$3"/cpufreq/scaling_cur_freq"`
	[ $1 -eq 0 ] && result="success" || result="failed, cpu$3's freq is $lf !"
	echo "change cpu$3 freq to $2 $result !"
	[ $1 -eq 0 ] || let cpu_f+=1
	let cpu_c+=1
	set_cpus 1 check_freq $2
}
freq_state()
{
	echo "change cpu freq $cpu_c times, failed $cpu_f times."
	[ $count -le $cpu_c ] && freq_cleanup 4
}

random()
{
	local v=$1
	local r=$RANDOM
	let rv=$r%$v
	return $rv
}
set_freq()
{
	local cpu=$2
	[ $cpu == 0 ] || cpu=$3
	echo $1 > $path$cpu"/cpufreq/scaling_setspeed"
	freq_result $? $1 $cpu
	sleep $interval
	freq_state > $log"cpufreq.state"
}
freq_test()
{
	local n=0
	local new_freq
	while [ 1 ]
	do
		n=0
		new_freq=
		for freq in $available_freq
		do
			random $n_cps
			set_freq $freq $n $?
			let n+=1
			new_freq=`echo "$freq $new_freq"`
		done
		n=0
		for freq in $new_freq
		do
			random $n_cps
			set_freq $freq $n $?
			let n+=1
		done
	done
}
#main function
bright="/sys/class/leds/keyboard-backlight/brightness"
path="/sys/devices/system/cpu/cpu"
path1="/sys/devices/system/cpu/cpuhotplug/cpu_hotplug_disable"
[ -f $path1 ] && hotplug=$path1 || hotplug=$path"freq/sprdemand/cpu_hotplug_disable"
log="/data/"
n_cps=0
cpu_c=0
cpu_f=0
default_freq=`cat $path"0/cpufreq/scaling_cur_freq"`
default_govr=`cat $path"0/cpufreq/scaling_governor"`
available_freq=`cat $path"0/cpufreq/scaling_available_frequencies"`
[ -z "$available_freq" ] && err_exit "cat "$path"0/cpufreq/scaling_available_frequencies" 0
[ -z $1 ] && count=10 && interval=1 || count=$1
[ -z $2 ] && interval=1 || interval=$2
freq_setup
freq_test > $log"cpufreq.log"
