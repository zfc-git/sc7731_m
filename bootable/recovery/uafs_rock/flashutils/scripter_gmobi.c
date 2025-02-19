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

#define GMT_SCRIPT_FILE "/tmp/updater-script"
extern int updater_main(int argc, char **argv);

int main(int argc, char* argv[]) {
	int ret = 0;
	/* run updater_main */
	LOGI("run updater_main()\n");
	if (access(GMT_SCRIPT_FILE, F_OK) == 0) {
		ret = updater_main(argc, argv);
		if (ret != 0) {
			LOGE("failed with error(4): %d\n(%s)", ret, strerror(errno));
		}
		else {
			unlink(GMT_SCRIPT_FILE);
		}
	}

	return ret;
}
