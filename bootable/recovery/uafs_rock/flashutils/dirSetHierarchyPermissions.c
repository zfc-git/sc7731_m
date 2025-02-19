/*
 * Copyright (C) 2007 The Android Open Source Project
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

#include <stdlib.h>
#include <string.h>
#include <stdio.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <unistd.h>
#include <errno.h>
#include <dirent.h>
#include <limits.h>


int
dirSetHierarchyPermissions(const char *path,
        int uid, int gid, int dirMode, int fileMode)
{
    struct stat st;
    if (lstat(path, &st)) {
        return -1;
    }

    /* ignore symlinks */
    if (S_ISLNK(st.st_mode)) {
        return 0;
    }

    /* directories and files get different permissions */
    if (chown(path, uid, gid) ||
        chmod(path, S_ISDIR(st.st_mode) ? dirMode : fileMode)) {
        return -1;
    }

    /* recurse over directory components */
    if (S_ISDIR(st.st_mode)) {
        DIR *dir = opendir(path);
        if (dir == NULL) {
            return -1;
        }

        errno = 0;
        const struct dirent *de;
        while (errno == 0 && (de = readdir(dir)) != NULL) {
            if (!strcmp(de->d_name, "..") || !strcmp(de->d_name, ".")) {
                continue;
            }

            char dn[PATH_MAX];
            snprintf(dn, sizeof(dn), "%s/%s", path, de->d_name);
            if (!dirSetHierarchyPermissions(dn, uid, gid, dirMode, fileMode)) {
                errno = 0;
            } else if (errno == 0) {
                errno = -1;
            }
        }

        if (errno != 0) {
            int save = errno;
            closedir(dir);
            errno = save;
            return -1;
        }

        if (closedir(dir)) {
            return -1;
        }
    }

    return 0;
}
