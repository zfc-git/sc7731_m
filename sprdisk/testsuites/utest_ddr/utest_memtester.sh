#!/bin/sh


#$1 - size of memory region
#$2 - loops
#$3 - number of memtester process
setup()
{
	cat /proc/meminfo | grep MemFree
	rm /media/sdcard/memtester*.log 2>/dev/null
}

umemtester()
{
	i=1
	while [ "$i" -le $3 ]
	do
		date>/media/sdcard/memtester"$i".log
		memtester $1 $2 1>/dev/null 2>>/media/sdcard/memtester"$i".log &
		eval pid$i=$!
		i=$((i+1))
	done
}

uwait()
{
	i=1
	while [ "$i" -le $1 ]
	do
		eval wait \$pid$i
		date>>/media/sdcard/memtester"$i".log
		cat /media/sdcard/memtester"$i".log
		i=$((i+1))
	done
}

# Main
#./test_mpeg4_dec.sh &
#vsppid=$!
setup
date
umemtester $1 $2 $3
uwait $3
date
#kill -1 $vsppid

