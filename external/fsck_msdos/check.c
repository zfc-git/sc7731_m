/*
 * Copyright (C) 1995, 1996, 1997 Wolfgang Solfrank
 * Copyright (c) 1995 Martin Husemann
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. All advertising materials mentioning features or use of this software
 *    must display the following acknowledgement:
 *	This product includes software developed by Martin Husemann
 *	and Wolfgang Solfrank.
 * 4. Neither the name of the University nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHORS ``AS IS'' AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 * THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */


#include <sys/cdefs.h>
#ifndef lint
__RCSID("$NetBSD: check.c,v 1.10 2000/04/25 23:02:51 jdolecek Exp $");
static const char rcsid[] =
  "$FreeBSD: src/sbin/fsck_msdosfs/check.c,v 1.10 2004/02/05 15:47:46 bde Exp $";
#endif /* not lint */

#include <stdlib.h>
#include <string.h>
#include <ctype.h>
#include <stdio.h>
#include <unistd.h>
#include <fcntl.h>

#include "ext.h"
#include "fsutil.h"

/*
 * If the FAT > this size then skip comparing, lest we risk
 * OOMing the framework. in the future we need to just re-write
 * this whole thing and optimize for less memory
 */
#define FAT_COMPARE_MAX_KB 4096

int
checkfilesys(const char *fname)
{
	int dosfs;
	struct bootblock boot;
	struct fatEntry *fat = NULL;
	int i, finish_dosdirsection=0;
	int mod = 0;
	int ret = 8;
        int quiet = 0;
        int skip_fat_compare = 0;

	rdonly = alwaysno;
	if (!quiet)
		printf("** %s", fname);

	dosfs = open(fname, rdonly ? O_RDONLY : O_RDWR, 0);
	if (dosfs < 0 && !rdonly) {
		dosfs = open(fname, O_RDONLY, 0);
		if (dosfs >= 0)
			pwarn(" (NO WRITE)\n");
		else if (!quiet)
			printf("\n");
		rdonly = 1;
	} else if (!quiet)
		printf("\n");

	if (dosfs < 0) {
		perror("Can't open");
		return 8;
	}

	if (readboot(dosfs, &boot) == FSFATAL) {
		close(dosfs);
		printf("\n");
		return 8;
	}

	if (skipclean && preen && checkdirty(dosfs, &boot)) {
		printf("%s: ", fname);
		printf("FILESYSTEM CLEAN; SKIPPING CHECKS\n");
		ret = 0;
		goto out;
	}

        if (((boot.FATsecs * boot.BytesPerSec) / 1024) > FAT_COMPARE_MAX_KB)
            skip_fat_compare = 1;

	if (!quiet)  {
                if (skip_fat_compare) 
                        printf("** Phase 1 - Read FAT (compare skipped)\n");
		else if (boot.ValidFat < 0)
			printf("** Phase 1 - Read and Compare FATs\n");
		else
			printf("** Phase 1 - Read FAT\n");
	}

	mod |= readfat(dosfs, &boot, boot.ValidFat >= 0 ? boot.ValidFat : 0, &fat);
	if (mod & FSFATAL) {
		printf("Fatal error during readfat()\n");
		close(dosfs);
		return 8;
	}

	if (rdonly != 1) {
		mod |= checkdisk(fname, &boot, fat);
		if (mod & FSFATAL) {
			printf("Fatal error during check disk\n");
			goto out;
		}
	}

	if (!skip_fat_compare && boot.ValidFat < 0)
		for (i = 1; i < (int)boot.FATs; i++) {
			struct fatEntry *currentFat;

			mod |= readfat(dosfs, &boot, i, &currentFat);

			if (mod & FSFATAL) {
				printf("Fatal error during readfat() for comparison\n");
				goto out;
			}

			mod |= comparefat(&boot, fat, currentFat, i);
			free(currentFat);
			if (mod & FSFATAL) {
				printf("Fatal error during FAT comparison\n");
				goto out;
			}
		}

	if (!quiet)
		printf("** Phase 2 - Check Cluster Chains\n");

	mod |= checkfat(&boot, fat);
	if (mod & FSFATAL) {
		printf("Fatal error during FAT check\n");
		goto out;
	}
	/* delay writing FATs */

	if (!quiet)
		printf("** Phase 3 - Checking Directories\n");

	mod |= resetDosDirSection(&boot, fat);
	finish_dosdirsection = 1;
	if (mod & FSFATAL) {
		printf("Fatal error during resetDosDirSection()\n");
		goto out;
	}
	/* delay writing FATs */

	mod |= handleDirTree(dosfs, &boot, fat);
	if (mod & FSFATAL)
		goto out;

	if (!quiet)
		printf("** Phase 4 - Checking for Lost Files\n");

	mod |= checklost(dosfs, &boot, fat);
	if (mod & FSFATAL)
		goto out;

	/* now write the FATs */
	if (mod & FSFATMOD) {
		if (ask(1, "Update FATs")) {
			mod |= writefat(dosfs, &boot, fat, mod & FSFIXFAT);
			if (mod & FSFATAL) {
				printf("Fatal error during writefat()\n");
				goto out;
			}
		} else
			mod |= FSERROR;
	}

	if (boot.NumBad)
		pwarn("%d files, %d free (%d clusters), %d bad (%d clusters)\n",
		      boot.NumFiles,
		      boot.NumFree * boot.ClusterSize / 1024, boot.NumFree,
		      boot.NumBad * boot.ClusterSize / 1024, boot.NumBad);
	else
		pwarn("%d files, %d free (%d clusters)\n",
		      boot.NumFiles,
		      boot.NumFree * boot.ClusterSize / 1024, boot.NumFree);

	if (mod && (mod & FSERROR) == 0) {
		if (mod & FSDIRTY) {
			if (ask(1, "MARK FILE SYSTEM CLEAN") == 0)
				mod &= ~FSDIRTY;

			if (mod & FSDIRTY) {
				pwarn("MARKING FILE SYSTEM CLEAN\n");
				mod |= writefat(dosfs, &boot, fat, 1);
			} else {
				pwarn("\n***** FILE SYSTEM IS LEFT MARKED AS DIRTY *****\n");
				mod |= FSERROR; /* file system not clean */
			}
		}
	}

	if (mod & (FSFATAL | FSERROR))
		goto out;

	if (sprd_check_frag && !(mod & (FSFATAL | FSERROR | FSFATMOD | FSDIRMOD))) {
		printf("** Phase 5 -  (check fragment of files)\n");
		check_filefragment(&boot, fat);

		printf("** Phase 6 -  (check fragment of free clusters)\n");
		check_freefragment(&boot, fat);
	}

	ret = 0;

    out:
	if (finish_dosdirsection)
		finishDosDirSection();
	free(fat);
	if (rdonly != 1) {
		fsync(dosfs);
	}
	close(dosfs);

	if (mod & (FSFATMOD|FSDIRMOD)) {
		pwarn("\n***** FILE SYSTEM WAS MODIFIED *****\n");
		return 4; 
	}

	return ret;
}
