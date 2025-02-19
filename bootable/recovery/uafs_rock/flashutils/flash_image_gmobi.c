/*
 * Copyright (C) 2008 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
 /*
 - 这里使用了CM7的代码.
 - 目前支持MTD,eMMC,BML
 - notmmao@gmail.com > 2013-02-01
 */

// 2014-03-06, support script;
// 2014-03-11, support restore flash_image_gmobi 

#include <errno.h>
#include <fcntl.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>

#include <sys/stat.h>

#define LOGE(...) fprintf(stderr, "E:" __VA_ARGS__)
#define LOGW(...) fprintf(stdout, "W:" __VA_ARGS__)
#define LOGI(...) fprintf(stdout, "I:" __VA_ARGS__)

extern int 
restore_raw_partition(const char* partitionType, const char* partition, const char* filename);

static void
check_and_fclose(FILE *fp, const char *name) {
	fflush(fp);
	if (ferror(fp)) LOGI("Error in %s\n(%s)\n", name, strerror(errno));
	fclose(fp);
}

static int copy_file_to(const char* src, const char* dest) {
	struct stat statbuf;
	stat(src, &statbuf);	
	
	FILE *src_file = fopen(src, "r");
	FILE *dest_file = fopen(dest, "w");
	if (src_file != NULL) {
		char buf[4096];
		int readn;
		
		while ((readn = fread(buf, 1, sizeof(buf), src_file))>0) {
			fwrite(buf, 1, readn, dest_file);
		}
		check_and_fclose(src_file, src);
	}
	else {
		LOGE("Open %s error.\n", src);
		return 1;
	}
	check_and_fclose(dest_file, dest);
	
	LOGI("copy %s to %s success.\n", src, dest);
	
	chmod(dest, statbuf.st_mode);
	LOGI("Chmod %s to %04o success.\n", dest, statbuf.st_mode);
	sync();
	return 0;
}

static int write_file(const char *path, const char *value, int len) {
	int fd, ret;
	fd = open(path, O_WRONLY|O_CREAT, 0622);

	if (fd < 0)
	return -errno;

	do {
		ret = write(fd, value, len);
	} while (ret < 0 && errno == EINTR);

	close(fd);
	if (ret < 0) {
		return -errno;
	} else {
		return 0;
	}
}

int main(int argc, char **argv) {
	char* type = "emmc";
	if (argc < 3) {
		LOGE( "usage: %s partition file.img\n", argv[0]);
		return 2;
	}
	if (argc == 4) {
		type = argv[3];
	}

	LOGI("run: %s %s %s %s\n", argv[0], argv[1], argv[2], argv[3]);

	/* unlock uboot writeable, for MTK MT6572 */
#if 0
	char buf[3] = {0};
	int ret = write_file("/proc/driver/mtd_writeable", buf, 3);
	if (ret != 0) {
		LOGE( "failed with error(1): %d\n(%s)\n", ret, strerror(errno));
	}
#else
	int ret = 0;
#endif

	ret = restore_raw_partition(type, argv[1], argv[2]);
	if (ret != 0) {
		LOGE( "failed with error(2): %d\n(%s)\n", ret, strerror(errno));
		if(strcmp(argv[1], "/dev/uboot")==0) {
			ret = restore_raw_partition(type, "uboot", argv[2]);
			if (ret != 0) {
				LOGE( "failed with error(3): %d\n(%s)\n", ret, strerror(errno));
			}
		}
		if(strcmp(argv[1], "boot")==0) {
			ret = restore_raw_partition(type, "/dev/bootimg", argv[2]);
			if (ret != 0) {
				LOGE( "failed with error(3.2): %d\n(%s)\n", ret, strerror(errno));
			}
		}
	}
	/* remove img file after flashing, for resever*/
	if(ret != 0) {
		return ret;
	}

	unlink(argv[2]);
	sync();

	return 0;
}
