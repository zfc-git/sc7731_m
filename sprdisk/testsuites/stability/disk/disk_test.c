#include <stdio.h>
#include <stdlib.h>
#include <fcntl.h>
#include <unistd.h>
#include <string.h>
#include <signal.h>
#include <errno.h>
#include <time.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <sys/statfs.h>
#include <sys/reboot.h>

#define M (1<<20)
#define LOG_LEN 256
char log_buf[LOG_LEN] = {0};
int log_len = 0;
#ifdef DEBUG
#define TRACE(format, args...) 								\
do{															\
 	log_len = sprintf(log_buf, "[%s]:%s():line:%d, "format 	\
 					,__FILE__, __func__, __LINE__, ##args);	\
 	write(1, log_buf, log_len);								\
}while(0)
#else
#define TRACE(format, args...)
#endif

#define LOCAL_LOG(format, args...) 									\
do{																	\
	time_t t = time(NULL);											\
	struct tm *lt = localtime(&t);									\
 	log_len = sprintf(log_buf, "[%02d:%02d:%02d]:"format, 			\
 		lt->tm_hour+8 >= 24 ? lt->tm_hour -16 : lt->tm_hour+8, 		\
 		lt->tm_min, lt->tm_sec, ##args);							\
 	write(2, log_buf, log_len);										\
}while(0)

long long total_size = 0;
char *file_name = NULL;
int loop = 1;
int loop_num = 0;
int loop_time = 60;//24 * 60 * 60;//24H
//info for log
int loop_count = 0;
int tmp_loop_num = 0;//store the loop_num
char *m_info = NULL;//msg info
int u_info = 0;//use time msg
int l_info = 0;//left time msg
int skip_num = 0;//skip test number
static off_t pre_pos = 0;
void usage(void);
void rm_file(void);
void time_out(int signo)
{
	signo = 0;
	loop_num = signo;
	loop = signo;
}

static void random_data(char *buf, size_t len)
{
	TRACE("trace\n");
	char i = 0;
	int data = 0;
	while(len - data > 0) {
		buf[data++] = 0x7f & i++;
	}
}

static void get_path(const char *f_name, char *path, int path_size)
{
	if(f_name == NULL) {
		LOCAL_LOG("ERROR:output file is null!\n");
		usage();
		exit(1);
	}
	if(f_name[0] == '/') {
		char *p = strrchr(f_name, '/');
		int n = 0;
		if((n = p - f_name) != 0)
			strncpy(path, f_name, n);
	} else {
		getcwd(path, path_size);
	}

	TRACE("trace:%s\n", path);
}

long long get_disk_size(const char *f_name)
{
	TRACE("trace\n");
	struct statfs st = {0};
	char path[128] = {0};
	get_path(f_name, path, sizeof(path));
	if(strlen(path) == 0) {
		LOCAL_LOG("ERROR:disk device is null!\n");
		exit(1);
	}
	if(access(path, F_OK) == -1)
	   if(mkdir(path, 0777)) {
	   		LOCAL_LOG("ERROR:creat dir %s failed:%s!\n", path, strerror(errno));
	   		exit(1);
	   	}

	if(statfs(path, &st) < 0) {
		LOCAL_LOG("ERROR:%s:%s!\n", path, strerror(errno));
		exit(1);
	}
	return (long long)st.f_bavail * (long long)st.f_bsize;
}

int read_from_file(char *buf, int size)
{
	TRACE("trace\n");
	int r_size = 0;
	int fd = open(file_name, O_RDONLY);
	if (fd < 0) {
		LOCAL_LOG("ERROR:open %s to read failed:%s!\n", file_name, strerror(errno));
		r_size = fd;
		goto err;
	}

	lseek(fd, pre_pos, SEEK_SET);

	if((r_size = read(fd, buf, size)) < 0) {
		LOCAL_LOG("ERROR:read %s failed:%s!\n", file_name, strerror(errno));
	} else {
		pre_pos += r_size;
	}

	close(fd);
err:
	return r_size;
}

int write_to_file(char *buf, off_t size)
{
	TRACE("trace\n");
	int w_size = 0;
	int fd = open(file_name, O_WRONLY | O_APPEND);
	if (fd < 0) {
		LOCAL_LOG("ERROR:open %s to write failed:%s!\n", file_name, strerror(errno));
		w_size = fd;
		goto err2;
	}
	if((w_size = write(fd, buf, size)) < 0) {
		LOCAL_LOG("ERROR:write %s failed:%s!\n", file_name, strerror(errno));
		goto err1;
	}
	fsync(fd);
err1:
	close(fd);
err2:
	return w_size;
}

int check_data(char *origin, char *modify, int size)
{
	TRACE("trace\n");
	return memcmp(origin, modify, size);
}

void init_buf(char **w_buf, char **r_buf, int size)
{
	TRACE("trace\n");
	*w_buf = (char *)malloc(size);
	if(*w_buf == NULL) {
		LOCAL_LOG("ERROR:malloc w_buf failed:%s!\n", strerror(errno));
		exit(1);
	}
	*r_buf = (char *)malloc(size);
	if(*r_buf == NULL) {
		LOCAL_LOG("ERROR:malloc r_buf failed:%s!\n", strerror(errno));
		exit(1);
	}
}

void free_buf(char **w_buf, char **r_buf)
{
	TRACE("trace\n");
	if(*w_buf != NULL) {
		free(*w_buf);
		*w_buf = NULL;
	}
	if(r_buf != NULL) {
		free(*r_buf);
		*r_buf = NULL;
	}
}

void disk_rw_test(char *ori_buf, char *mod_buf, int size)
{
	TRACE("trace\n");
	long long left_size = total_size;
	int count = 0;

	while(size) {
		count++;
		random_data(ori_buf, size);
		if((write_to_file(ori_buf, size) < 0) || (read_from_file(mod_buf, size) < 0))
			break;
		if(check_data(ori_buf, mod_buf, size)) {
			if(skip_num)
				LOCAL_LOG("INFO:skip test %d times!\n", skip_num);
			LOCAL_LOG("ERROR:we want to test %lld MB file in %d %s;\n"					\
				"\tbut data broken occured when test in the %d time;\n"					\
				"\twe've checked %lld Bytes data with %d write/read this time!\n",		\
				total_size>>20, u_info, m_info,	loop_count, total_size - left_size, count);
			l_info = (l_info = alarm(0)) > 3600 ? (m_info = "hours", l_info/3600) : 	\
				(l_info ? (m_info = "seconds", l_info) : (m_info = "times", tmp_loop_num - loop_count));
			LOCAL_LOG("ERROR:there're still %d %s' test is not finished!\n"				\
				"\tclean up file %s to panic ......\n", l_info, m_info, file_name);
			free_buf(&ori_buf, &mod_buf);
			rm_file();
			exit(2);
		}
		left_size -= size;
		size = left_size > size ? size : left_size;
	}
	if(left_size) {
		LOCAL_LOG("INFO:test comes across a mistake, skip this time's test!\n");
		skip_num++;
	} else {
		LOCAL_LOG("INFO:test %d time completely!\n", loop_count);
	}
}

void usage(void)
{
	printf("Usage:\n");
	printf("flashtest [ -h ] [ -s size ] [ -o file ] [-n num ] [ -t minutes]\n");
	printf("           -h: show this help menu\n");
	printf("           -s: file size to read/write with MBytes\n");
	printf("           -o: output file which to test\n");
	printf("           -n: how many times to test\n");
	printf("           -t: how long time to test(minutes)\n");
}

int get_opt(int argc, char *argv[])
{
	TRACE("trace\n");
	int ch = 0;
	while((ch = getopt(argc, argv, "hs:o:t:n:")) != -1) {
		switch(ch){
		case 's':
			total_size = (long long)atoi(optarg) * (M);
//			LOCAL_LOG("INFO:total_size is %lld MB.\n", total_size);
			break;
		case 'o':
			file_name = optarg;
			break;
		case 't':
			loop_time = atoi(optarg) * 3600;//1h = 3600s
			loop_num = 0;
			loop = loop_time != 0;
			break;
		case 'n':
			loop_num = atoi(optarg);
			loop_time = 0;
			loop = loop_num != 0;
			break;
		case 'h':
			usage();
			exit(1);
		default:
			printf("Unknown option: %c\n", (char)optopt);
			usage();
			exit(1);
		}
	}
	return 0;
}

void mk_test_file(void)
{
	TRACE("------------\n");
	pre_pos = 0;
	int fd = creat(file_name, 0755);
	if(fd < 0) {
		LOCAL_LOG("ERROR:creat %s failed:%s!\n", file_name, strerror(errno));
	} else {
		close(fd);
	}

}

void rm_file(void)
{
	TRACE("------------\n");
	if(remove(file_name) != 0) {
		LOCAL_LOG("ERROR:delete %s failed:%s!\n", file_name, strerror(errno));
	}
}

int loop_test(char **buf1, char **buf2)
{
	signal(SIGALRM, time_out);
	alarm(loop_time);
	LOCAL_LOG("INFO:start to test %s %lld MB in %d %s ....\n", file_name, total_size>>20, u_info, m_info);
	init_buf(buf1, buf2, M);
	do{
		mk_test_file();
		loop_count++;
		disk_rw_test(*buf1, *buf2, M);
		rm_file();
	}while(loop_num -= loop);
	free_buf(buf1, buf2);
	if(skip_num) {
		LOCAL_LOG("INFO:skip test %d times!\n", skip_num);
		if(u_info == tmp_loop_num)
			u_info -= skip_num;
	}
	LOCAL_LOG("INFO:test %lld MB in %d %s successful!\n", total_size>>20, u_info, m_info);
	return 0;
}

int main(int argc, char *argv[])
{
	TRACE("trace\n");
	if(argc == 1){
		usage();
		return 1;
	}
	char *ori_buf = NULL;
	char *mod_buf = NULL;
	get_opt(argc, argv);
	tmp_loop_num = loop_num ? loop_num : 1;
	u_info = loop_time > 3600 ? (m_info = "hours", loop_time/3600 ): 					\
		(loop_time ? (m_info = "seconds", loop_time) : (m_info = "times", tmp_loop_num));
	long long avail_size = 0;
	if((avail_size = get_disk_size(file_name)) == 0) {
		LOCAL_LOG("ERROR:disk avail size is zero!\n");
		exit(1);
	}
	if(total_size == 0 || total_size > avail_size) {
		LOCAL_LOG("WARNING:size is %lld MB; change it to avail_size %lld MB!\n", 		\
			total_size>>20, avail_size>>20);
		total_size = avail_size>>20<<20;
	}

	return loop_test(&ori_buf, &mod_buf);
}