/*
 * Copyright (C) 2012 Cyril Hrubis chrubis@suse.cz
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of version 2 of the GNU General Public License as
 * published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it would be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 * Further, this software is distributed without any warranty that it is
 * free of the rightful claim of any third person regarding infringement
 * or the like.  Any license provided herein, whether implied or
 * otherwise, applies only to this software file.  Patent licenses, if
 * any, provided herein do not apply to combinations of this program with
 * other software, or any other product whatsoever.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */

 /*

   This code helps with file reading/writing files providing scanf/printf like
   interface that opens and closes the file automatically.

   This kind of interface is especially useful for reading/writing values
   from/to pseudo filesystems like procfs or sysfs.

  */

#ifndef SAFE_FILE_OPS
#define SAFE_FILE_OPS

#include <sys/stat.h>

#include "lapi/utime.h"
#include "test.h"

/*
 * All-in-one function to scanf value(s) from a file.
 */
int file_scanf(const char *file, const int lineno,
		const char *path, const char *fmt, ...)
		__attribute__ ((format (scanf, 4, 5)));

#define FILE_SCANF(path, fmt, ...) \
	file_scanf(__FILE__, __LINE__, \
	           (path), (fmt), ## __VA_ARGS__)

void safe_file_scanf(const char *file, const int lineno,
                     void (*cleanup_fn)(void),
		     const char *path, const char *fmt, ...)
		     __attribute__ ((format (scanf, 5, 6)));

#define SAFE_FILE_SCANF(cleanup_fn, path, fmt, ...) \
	safe_file_scanf(__FILE__, __LINE__, (cleanup_fn), \
	                (path), (fmt), ## __VA_ARGS__)

/*
 * All-in-one function that lets you printf directly into a file.
 */
int file_printf(const char *file, const int lineno,
                      const char *path, const char *fmt, ...)
                      __attribute__ ((format (printf, 4, 5)));

#define FILE_PRINTF(path, fmt, ...) \
	file_printf(__FILE__, __LINE__, \
	            (path), (fmt), ## __VA_ARGS__)

void safe_file_printf(const char *file, const int lineno,
                      void (*cleanup_fn)(void),
                      const char *path, const char *fmt, ...)
                      __attribute__ ((format (printf, 5, 6)));

#define SAFE_FILE_PRINTF(cleanup_fn, path, fmt, ...) \
	safe_file_printf(__FILE__, __LINE__, (cleanup_fn), \
	                 (path), (fmt), ## __VA_ARGS__)

/*
 * Safe function to copy files, no more system("cp ...") please.
 */
void safe_cp(const char *file, const int lineno,
             void (*cleanup_fn)(void),
	     const char *src, const char *dst);

#define SAFE_CP(cleanup_fn, src, dst) \
	safe_cp(__FILE__, __LINE__, (cleanup_fn), (src), (dst))

/*
 * Safe function to touch a file.
 *
 * If the file (pathname) does not exist It will be created with
 * the specified permission (mode) and the access/modification times (times).
 *
 * If mode is 0 then the file is created with (0666 & ~umask)
 * permission or (if the file exists) the permission is not changed.
 *
 * times is a timespec[2] (as for utimensat(2)). If times is NULL then
 * the access/modification times of the file is set to the current time.
 */
void safe_touch(const char *file, const int lineno,
		void (*cleanup_fn)(void),
		const char *pathname,
		mode_t mode, const struct timespec times[2]);

#define SAFE_TOUCH(cleanup_fn, pathname, mode, times) \
	safe_touch(__FILE__, __LINE__, (cleanup_fn), \
			(pathname), (mode), (times))

#endif /* SAFE_FILE_OPS */
