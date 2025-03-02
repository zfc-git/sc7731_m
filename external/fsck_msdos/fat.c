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
__RCSID("$NetBSD: fat.c,v 1.12 2000/10/10 20:24:52 is Exp $");
static const char rcsid[] =
  "$FreeBSD: src/sbin/fsck_msdosfs/fat.c,v 1.9 2008/01/31 13:22:13 yar Exp $";
#endif /* not lint */

#include <stdlib.h>
#include <string.h>
#include <ctype.h>
#include <stdio.h>
#include <unistd.h>
#include <fcntl.h>

#include "ext.h"
#include "fsutil.h"

static int checkclnum(struct bootblock *, int, cl_t, cl_t *);
static int clustdiffer(cl_t, cl_t *, cl_t *, int);
static int tryclear(struct bootblock *, struct fatEntry *, cl_t, cl_t *);
static int _readfat(int, struct bootblock *, int, u_char **);

/*-
 * The first 2 FAT entries contain pseudo-cluster numbers with the following
 * layout:
 *
 * 31...... ........ ........ .......0
 * rrrr1111 11111111 11111111 mmmmmmmm         FAT32 entry 0
 * rrrrsh11 11111111 11111111 11111xxx         FAT32 entry 1
 * 
 *                   11111111 mmmmmmmm         FAT16 entry 0
 *                   sh111111 11111xxx         FAT16 entry 1
 * 
 * r = reserved
 * m = BPB media ID byte
 * s = clean flag (1 = dismounted; 0 = still mounted)
 * h = hard error flag (1 = ok; 0 = I/O error)
 * x = any value ok
 */

int
checkdirty(int fs, struct bootblock *boot)
{
	off_t off;
	u_char *buffer;
	int ret = 0;

	if (boot->ClustMask != CLUST16_MASK && boot->ClustMask != CLUST32_MASK)
		return 0;

	off = boot->ResSectors;
	off *= boot->BytesPerSec;

	buffer = malloc(boot->BytesPerSec);
	if (buffer == NULL) {
		perror("No space for FAT");
		return 1;
	}

	if (lseek(fs, off, SEEK_SET) != off) {
		perror("Unable to read FAT");
		goto err;
	}

	if (read(fs, buffer, boot->BytesPerSec) != boot->BytesPerSec) {
		perror("Unable to read FAT");
		goto err;
	}

	/*
	 * If we don't understand the FAT, then the file system must be
	 * assumed to be unclean.
	 */
	if (buffer[0] != boot->Media || buffer[1] != 0xff)
		goto err;
	if (boot->ClustMask == CLUST16_MASK) {
		if ((buffer[2] & 0xf8) != 0xf8 || (buffer[3] & 0x3f) != 0x3f)
			goto err;
	} else {
		if (buffer[2] != 0xff || (buffer[3] & 0x0f) != 0x0f
		    || (buffer[4] & 0xf8) != 0xf8 || buffer[5] != 0xff
		    || buffer[6] != 0xff || (buffer[7] & 0x03) != 0x03)
			goto err;
	}

	/*
	 * Now check the actual clean flag (and the no-error flag).
	 */
	if (boot->ClustMask == CLUST16_MASK) {
		if ((buffer[3] & 0xc0) == 0xc0)
			ret = 1;
	} else {
		if ((buffer[7] & 0x0c) == 0x0c)
			ret = 1;
	}

err:
	free(buffer);
	return ret;
}

/*
 * Check a cluster number for valid value
 */
static int
checkclnum(struct bootblock *boot, int fat, cl_t cl, cl_t *next)
{
	if (*next >= (CLUST_RSRVD&boot->ClustMask))
		*next |= ~boot->ClustMask;
	if (*next == CLUST_FREE) {
		boot->NumFree++;
		return FSOK;
	}
	if (*next == CLUST_BAD) {
		boot->NumBad++;
		return FSOK;
	}
	if (*next < CLUST_FIRST
	    || (*next >= boot->NumClusters && *next < CLUST_EOFS)) {
		pwarn("Cluster %u in FAT %d continues with %s cluster number %u\n",
		      cl, fat,
		      *next < CLUST_RSRVD ? "out of range" : "reserved",
		      *next&boot->ClustMask);
		if (ask(1, "Truncate")) {
			*next = CLUST_EOF;
			return FSFATMOD;
		}
		return FSERROR;
	}
	return FSOK;
}

/*
 * Read a FAT from disk. Returns 1 if successful, 0 otherwise.
 */
static int
_readfat(int fs, struct bootblock *boot, int no, u_char **buffer)
{
	off_t off;

        printf("Attempting to allocate %u KB for FAT\n",
                (boot->FATsecs * boot->BytesPerSec) / 1024);

	*buffer = malloc(boot->FATsecs * boot->BytesPerSec);
	if (*buffer == NULL) {
		perror("No space for FAT");
		return 0;
	}

	off = boot->ResSectors + no * boot->FATsecs;
	off *= boot->BytesPerSec;

	if (lseek(fs, off, SEEK_SET) != off) {
		perror("Unable to read FAT");
		goto err;
	}

	if (read(fs, *buffer, boot->FATsecs * boot->BytesPerSec)
	    != boot->FATsecs * boot->BytesPerSec) {
		perror("Unable to read FAT");
		goto err;
	}

	return 1;

    err:
	free(*buffer);
	return 0;
}

/*
 * Read a FAT and decode it into internal format
 */
int
readfat(int fs, struct bootblock *boot, int no, struct fatEntry **fp)
{
	struct fatEntry *fat;
	u_char *buffer, *p;
	cl_t cl;
	int ret = FSOK;

	boot->NumFree = boot->NumBad = 0;

	if (!_readfat(fs, boot, no, &buffer))
		return FSFATAL;
		
	fat = calloc(boot->NumClusters, sizeof(struct fatEntry));
	if (fat == NULL) {
		perror("No space for FAT");
		free(buffer);
		return FSFATAL;
	}

	if (buffer[0] != boot->Media
	    || buffer[1] != 0xff || buffer[2] != 0xff
	    || (boot->ClustMask == CLUST16_MASK && buffer[3] != 0xff)
	    || (boot->ClustMask == CLUST32_MASK
		&& ((buffer[3]&0x0f) != 0x0f
		    || buffer[4] != 0xff || buffer[5] != 0xff
		    || buffer[6] != 0xff || (buffer[7]&0x0f) != 0x0f))) {

		/* Windows 95 OSR2 (and possibly any later) changes
		 * the FAT signature to 0xXXffff7f for FAT16 and to
		 * 0xXXffff0fffffff07 for FAT32 upon boot, to know that the
		 * file system is dirty if it doesn't reboot cleanly.
		 * Check this special condition before errorring out.
		 */
		if (buffer[0] == boot->Media && buffer[1] == 0xff
		    && buffer[2] == 0xff
		    && ((boot->ClustMask == CLUST16_MASK && buffer[3] == 0x7f)
			|| (boot->ClustMask == CLUST32_MASK
			    && buffer[3] == 0x0f && buffer[4] == 0xff
			    && buffer[5] == 0xff && buffer[6] == 0xff
			    && buffer[7] == 0x07)))
			ret |= FSDIRTY;
		else {
			/* just some odd byte sequence in FAT */
				
			switch (boot->ClustMask) {
			case CLUST32_MASK:
				pwarn("%s (%02x%02x%02x%02x%02x%02x%02x%02x)\n",
				      "FAT starts with odd byte sequence",
				      buffer[0], buffer[1], buffer[2], buffer[3],
				      buffer[4], buffer[5], buffer[6], buffer[7]);
				break;
			case CLUST16_MASK:
				pwarn("%s (%02x%02x%02x%02x)\n",
				    "FAT starts with odd byte sequence",
				    buffer[0], buffer[1], buffer[2], buffer[3]);
				break;
			default:
				pwarn("%s (%02x%02x%02x)\n",
				    "FAT starts with odd byte sequence",
				    buffer[0], buffer[1], buffer[2]);
				break;
			}

	
			if (ask(1, "Correct"))
				ret |= FSFIXFAT;
		}
	}
	switch (boot->ClustMask) {
	case CLUST32_MASK:
		p = buffer + 8;
		break;
	case CLUST16_MASK:
		p = buffer + 4;
		break;
	default:
		p = buffer + 3;
		break;
	}
	for (cl = CLUST_FIRST; cl < boot->NumClusters;) {
		switch (boot->ClustMask) {
		case CLUST32_MASK:
			fat[cl].next = p[0] + (p[1] << 8)
				       + (p[2] << 16) + (p[3] << 24);
			fat[cl].next &= boot->ClustMask;
			ret |= checkclnum(boot, no, cl, &fat[cl].next);
			cl++;
			p += 4;
			break;
		case CLUST16_MASK:
			fat[cl].next = p[0] + (p[1] << 8);
			ret |= checkclnum(boot, no, cl, &fat[cl].next);
			cl++;
			p += 2;
			break;
		default:
			fat[cl].next = (p[0] + (p[1] << 8)) & 0x0fff;
			ret |= checkclnum(boot, no, cl, &fat[cl].next);
			cl++;
			if (cl >= boot->NumClusters)
				break;
			fat[cl].next = ((p[1] >> 4) + (p[2] << 4)) & 0x0fff;
			ret |= checkclnum(boot, no, cl, &fat[cl].next);
			cl++;
			p += 3;
			break;
		}
	}

	free(buffer);
	*fp = fat;
	return ret;
}

/*
 * Get type of reserved cluster
 */
char *
rsrvdcltype(cl_t cl)
{
	if (cl == CLUST_FREE)
		return "free";
	if (cl < CLUST_BAD)
		return "reserved";
	if (cl > CLUST_BAD)
		return "as EOF";
	return "bad";
}

static int
clustdiffer(cl_t cl, cl_t *cp1, cl_t *cp2, int fatnum)
{
	if (*cp1 == CLUST_FREE || *cp1 >= CLUST_RSRVD) {
		if (*cp2 == CLUST_FREE || *cp2 >= CLUST_RSRVD) {
			if ((*cp1 != CLUST_FREE && *cp1 < CLUST_BAD
			     && *cp2 != CLUST_FREE && *cp2 < CLUST_BAD)
			    || (*cp1 > CLUST_BAD && *cp2 > CLUST_BAD)) {
				pwarn("Cluster %u is marked %s with different indicators\n",
				      cl, rsrvdcltype(*cp1));
				if (ask(1, "Fix")) {
					*cp2 = *cp1;
					return FSFATMOD;
				}
				return FSFATAL;
			}
			pwarn("Cluster %u is marked %s in FAT 0, %s in FAT %d\n",
			      cl, rsrvdcltype(*cp1), rsrvdcltype(*cp2), fatnum);
			if (ask(1, "Use FAT 0's entry")) {
				*cp2 = *cp1;
				return FSFATMOD;
			}
			if (ask(1, "Use FAT %d's entry", fatnum)) {
				*cp1 = *cp2;
				return FSFATMOD;
			}
			return FSFATAL;
		}
		pwarn("Cluster %u is marked %s in FAT 0, but continues with cluster %u in FAT %d\n",
		      cl, rsrvdcltype(*cp1), *cp2, fatnum);
		if (ask(1, "Use continuation from FAT %d", fatnum)) {
			*cp1 = *cp2;
			return FSFATMOD;
		}
		if (ask(1, "Use mark from FAT 0")) {
			*cp2 = *cp1;
			return FSFATMOD;
		}
		return FSFATAL;
	}
	if (*cp2 == CLUST_FREE || *cp2 >= CLUST_RSRVD) {
		pwarn("Cluster %u continues with cluster %u in FAT 0, but is marked %s in FAT %d\n",
		      cl, *cp1, rsrvdcltype(*cp2), fatnum);
		if (ask(1, "Use continuation from FAT 0")) {
			*cp2 = *cp1;
			return FSFATMOD;
		}
		if (ask(1, "Use mark from FAT %d", fatnum)) {
			*cp1 = *cp2;
			return FSFATMOD;
		}
		return FSERROR;
	}
	pwarn("Cluster %u continues with cluster %u in FAT 0, but with cluster %u in FAT %d\n",
	      cl, *cp1, *cp2, fatnum);
	if (ask(1, "Use continuation from FAT 0")) {
		*cp2 = *cp1;
		return FSFATMOD;
	}
	if (ask(1, "Use continuation from FAT %d", fatnum)) {
		*cp1 = *cp2;
		return FSFATMOD;
	}
	return FSERROR;
}

/*
 * check that whether disk is readonly
 */

int
checkdisk(const char *fname, struct bootblock *boot, struct fatEntry *fat)
{
	unsigned int cl;
	loff_t off;
	size_t last, pgsize;
	int mod, dosfs;
	void *buffer;
	int retry = 0;
	int wr_val;

	dosfs = open(fname, O_RDWR | O_DIRECT, 0);
	if (dosfs < 0) {
		perror("Open fail for check disk");
		return FSFATAL;
	}

	mod = FSOK;
	for (cl = CLUST_FIRST; cl < boot->NumClusters; cl++)
		if (fat[cl].next == CLUST_FREE)
				break;

	if (cl >= boot->NumClusters) {
			close(dosfs);
			return FSNOSPACE;
	}

	off = (loff_t)cl * boot->SecPerClust + boot->ClusterOffset;
	off *= boot->BytesPerSec;
	last = boot->BytesPerSec * boot->SecPerClust;

	/* if one cluster address is not align with pgsize, all is not*/
	pgsize = getpagesize();
	if (off & (pgsize - 1)) {
		perror("Start address of cluster isn't aligned with pgsize. Skip this step.");
		close(dosfs);
		return FSNOSPACE;
	}

	if (posix_memalign(&buffer, pgsize, last)) {
		perror("No memory for checking disk. Skip this step.");
		close(dosfs);
		return FSNOSPACE;
	}

	memset(buffer, 0, last);

retry:
	if (lseek64(dosfs, off, SEEK_SET) != off) {
		printf("off = %llu", off);
		perror("Unable to lseek64");
		mod |= FSFATAL;
		goto err;
	}
	if (read(dosfs, buffer, last) != last) {
		if ((errno == EIO) && (retry < 3)) {
			retry++;
			goto retry;
		}
		perror("Unable to read");
		mod |= FSFATAL;
		goto err;
	}

	wr_val = ~(*(int *)buffer);
	*(int *)buffer = wr_val;

	retry = 0;
retry1:
	if (lseek64(dosfs, off, SEEK_SET) != off) {
		printf("off = %llu", off);
		perror("Unable to lseek64");
		mod |= FSFATAL;
		goto err;
	}
	if (write(dosfs, buffer, last) != last) {
		if ((errno == EIO) && (retry < 3)) {
			retry++;
			goto retry1;
		}
		perror("Unable to write");
		mod |= FSFATAL;
		goto err;
	}

	retry = 0;
retry2:
	if (lseek64(dosfs, off, SEEK_SET) != off) {
		printf("off = %llu", off);
		perror("Unable to lseek64");
		mod |= FSFATAL;
		goto err;
	}
	if (read(dosfs, buffer, last) != last) {
		if ((errno == EIO) && (retry < 3)) {
			retry++;
			goto retry2;
		}
		perror("Unable to read");
		mod |= FSFATAL;
		goto err;
	}

	if (*(int *)buffer != wr_val) {
		printf("Read data 0x%08x doesn't match written data 0x%08x during check disk\n", *(int *)buffer, wr_val);
		mod |= FSFATAL;
		goto err;
	}

err:
	close(dosfs);
	free(buffer);
	return mod;
}

/*
 * Compare two FAT copies in memory. Resolve any conflicts and merge them
 * into the first one.
 */
int
comparefat(struct bootblock *boot, struct fatEntry *first, 
    struct fatEntry *second, int fatnum)
{
	cl_t cl;
	int ret = FSOK;

	for (cl = CLUST_FIRST; cl < boot->NumClusters; cl++)
		if (first[cl].next != second[cl].next)
			ret |= clustdiffer(cl, &first[cl].next, &second[cl].next, fatnum);
	return ret;
}

void
clearchain(struct bootblock *boot, struct fatEntry *fat, cl_t head)
{
	cl_t p, q;

	for (p = head; p >= CLUST_FIRST && p < boot->NumClusters; p = q) {
		if (fat[p].head != head)
			break;
		q = fat[p].next;
		fat[p].next = fat[p].head = CLUST_FREE;
		fat[p].length = 0;
	}
}

int
tryclear(struct bootblock *boot, struct fatEntry *fat, cl_t head, cl_t *trunc)
{
	if (ask(1, "Clear chain starting at %u", head)) {
		clearchain(boot, fat, head);
		return FSFATMOD;
	} else if (ask(1, "Truncate")) {
		*trunc = CLUST_EOF;
		return FSFATMOD;
	} else
		return FSERROR;
}

/*
 * Check a complete FAT in-memory for crosslinks
 */
int
checkfat(struct bootblock *boot, struct fatEntry *fat)
{
	cl_t head, p, h, n, wdk;
	u_int len;
	int ret = 0;
	int conf;

	/*
	 * pass 1: figure out the cluster chains.
	 */
	for (head = CLUST_FIRST; head < boot->NumClusters; head++) {
		/* find next untravelled chain */
		if (fat[head].head != 0		/* cluster already belongs to some chain */
		    || fat[head].next == CLUST_FREE
		    || fat[head].next == CLUST_BAD)
			continue;		/* skip it. */

		/* follow the chain and mark all clusters on the way */
		for (len = 0, p = head;
			 p >= CLUST_FIRST && p < boot->NumClusters;
			 p = fat[p].next) {
				/* we have to check the len, to avoid infinite loop */
				if (len > boot->NumClusters) {
					printf("detect cluster chain loop: head %u for p %u\n", head, p);
					break;
			}

			fat[p].head = head;
			len++;
		}

		/* the head record gets the length */
		fat[head].length = fat[head].next == CLUST_FREE ? 0 : len;
	}

	/*
	 * pass 2: check for crosslinked chains (we couldn't do this in pass 1 because
	 * we didn't know the real start of the chain then - would have treated partial
	 * chains as interlinked with their main chain)
	 */
	for (head = CLUST_FIRST; head < boot->NumClusters; head++) {
		/* find next untravelled chain */
		if (fat[head].head != head)
			continue;

		/* follow the chain to its end (hopefully) */
		/* also possible infinite loop, that's why I insert wdk counter */
		for (p = head,wdk=boot->NumClusters;
		     (n = fat[p].next) >= CLUST_FIRST && n < boot->NumClusters && wdk;
				 p = n,wdk--) {
			if (fat[n].head != head)
				break;
		}

		if (n >= CLUST_EOFS)
			continue;

		if (n == CLUST_FREE || n >= CLUST_RSRVD) {
			pwarn("Cluster chain starting at %u ends with cluster marked %s\n",
			      head, rsrvdcltype(n));
			ret |= tryclear(boot, fat, head, &fat[p].next);
			continue;
		}
		if (n < CLUST_FIRST || n >= boot->NumClusters) {
			pwarn("Cluster chain starting at %u ends with cluster out of range (%u)\n",
			      head, n);
			ret |= tryclear(boot, fat, head, &fat[p].next);
			continue;
		}
		pwarn("Cluster chains starting at %u and %u are linked at cluster %u\n",
		      head, fat[n].head, n);
		conf = tryclear(boot, fat, head, &fat[p].next);
		if (ask(1, "Clear chain starting at %u", h = fat[n].head)) {
			if (conf == FSERROR) {
				/*
				 * Transfer the common chain to the one not cleared above.
				 */
				for (p = n;
				     p >= CLUST_FIRST && p < boot->NumClusters;
				     p = fat[p].next) {
					if (h != fat[p].head) {
						/*
						 * Have to reexamine this chain.
						 */
						head--;
						break;
					}
					fat[p].head = head;
				}
			}
			clearchain(boot, fat, h);
			conf |= FSFATMOD;
		}
		ret |= conf;
	}

	return ret;
}

/*
 * Write out FATs encoding them from the internal format
 */
int
writefat(int fs, struct bootblock *boot, struct fatEntry *fat, int correct_fat)
{
	u_char *buffer, *p;
	cl_t cl;
	int i;
	u_int32_t fatsz;
	off_t off;
	int ret = FSOK;

	buffer = malloc(fatsz = boot->FATsecs * boot->BytesPerSec);
	if (buffer == NULL) {
		perror("No space for FAT");
		return FSFATAL;
	}
	memset(buffer, 0, fatsz);
	boot->NumFree = 0;
	p = buffer;
	if (correct_fat) {
		*p++ = (u_char)boot->Media;
		*p++ = 0xff;
		*p++ = 0xff;
		switch (boot->ClustMask) {
		case CLUST16_MASK:
			*p++ = 0xff;
			break;
		case CLUST32_MASK:
			*p++ = 0x0f;
			*p++ = 0xff;
			*p++ = 0xff;
			*p++ = 0xff;
			*p++ = 0x0f;
			break;
		}
	} else {
		/* use same FAT signature as the old FAT has */
		int count;
		u_char *old_fat;

		switch (boot->ClustMask) {
		case CLUST32_MASK:
			count = 8;
			break;
		case CLUST16_MASK:
			count = 4;
			break;
		default:
			count = 3;
			break;
		}

		if (!_readfat(fs, boot, boot->ValidFat >= 0 ? boot->ValidFat :0,
					 &old_fat)) {
			free(buffer);
			return FSFATAL;
		}

		memcpy(p, old_fat, count);
		free(old_fat);
		p += count;
	}
			
	for (cl = CLUST_FIRST; cl < boot->NumClusters; cl++) {
		switch (boot->ClustMask) {
		case CLUST32_MASK:
			if (fat[cl].next == CLUST_FREE)
				boot->NumFree++;
			*p++ = (u_char)fat[cl].next;
			*p++ = (u_char)(fat[cl].next >> 8);
			*p++ = (u_char)(fat[cl].next >> 16);
			*p &= 0xf0;
			*p++ |= (fat[cl].next >> 24)&0x0f;
			break;
		case CLUST16_MASK:
			if (fat[cl].next == CLUST_FREE)
				boot->NumFree++;
			*p++ = (u_char)fat[cl].next;
			*p++ = (u_char)(fat[cl].next >> 8);
			break;
		default:
			if (fat[cl].next == CLUST_FREE)
				boot->NumFree++;
			if (cl + 1 < boot->NumClusters
			    && fat[cl + 1].next == CLUST_FREE)
				boot->NumFree++;
			*p++ = (u_char)fat[cl].next;
			*p++ = (u_char)((fat[cl].next >> 8) & 0xf)
			       |(u_char)(fat[cl+1].next << 4);
			*p++ = (u_char)(fat[++cl].next >> 4);
			break;
		}
	}
	for (i = 0; i < boot->FATs; i++) {
		off = boot->ResSectors + i * boot->FATsecs;
		off *= boot->BytesPerSec;
		if (lseek(fs, off, SEEK_SET) != off
		    || write(fs, buffer, fatsz) != fatsz) {
			perror("Unable to write FAT");
			ret = FSFATAL; /* Return immediately?		XXX */
		}
	}
	free(buffer);
	return ret;
}


struct  stat_t{
	int  size;
	int  count;
	struct stat_t * next;
};

struct  stat_tab{
	int  num;
	struct stat_t * header;
};

int insert_stat_tab(struct stat_tab *stat_table, int size) {
	struct stat_t * next, *prev, *cur;
	prev = NULL;
	next = stat_table->header;
	while (next !=NULL) {
		if (next->size==size) {
			next->count++;
			return 0;
		}
		else if (next->size > size)
			break;

		prev = next;
		next = prev->next;
	}

	cur = malloc(sizeof(struct stat_t));
	if (cur != NULL) {
		cur->size = size;
		cur->count = 1;
		cur->next = next;
		if (prev)
			prev->next = cur;
		stat_table->num++;
	}
	else
		return -1;

	if (stat_table->header==NULL)
		stat_table->header = cur;
	else if (stat_table->header->size > cur->size)
		stat_table->header = cur;

	return 0;
}

int show_and_free_stat_tab(struct stat_tab *stat_table) {
	struct stat_t * next, *prev;

	prev = next = stat_table->header;
	printf("Stat_table item count %d\n", stat_table->num );

	printf("Clusters, Size\n");
	while (next !=NULL) {
		printf("%d, %d\n", next->size, next->count);
		prev = next;
		next = prev->next;
		free(prev);
		stat_table->num--;
	}

	stat_table->header = NULL;
	printf("Stat_table item count %d\n", stat_table->num );

	return 0;
}

float
check_freefragment(struct bootblock *boot, struct fatEntry *fat)
{
	cl_t head, old, p, h, n, wdk;
	int NumClusters = 0;
	int NumFragments = 0;
	float ratio = 0;
	struct stat_tab stat_table;
	int  start_cl, size;

	stat_table.num = 0;
	stat_table.header = NULL;

	printf("check free fragement begin!\n");
	for (head = CLUST_FIRST, old = head - 1; head < boot->NumClusters; head++) {

		if (fat[head].next==CLUST_FREE && fat[head-1].next!=CLUST_FREE ) {
			start_cl = head;
		}
		if (fat[head-1].next==CLUST_FREE && fat[head].next!=CLUST_FREE ) {
			size=head-start_cl;
			printf("From %d,  %d clusters free.\n", start_cl, size);
			if(insert_stat_tab(&stat_table, size)  <  0)
				printf("Insert stat table failed!!");
		}

		if (fat[head].next != CLUST_FREE)
			continue;		/* skip it. */

		NumClusters++;

		if (1 != head - old)
			NumFragments++;

		old = head;
	}

	if (fat[head-1].next==CLUST_FREE) {
		size=head-start_cl;
		printf("From %d  to end %d,  %d clusters free.\n", start_cl, head-1,  size);
		if(insert_stat_tab(&stat_table, size)  <  0)
			printf("Insert stat table failed!!");
	}
	show_and_free_stat_tab(&stat_table);

	if (NumClusters > 1)
		ratio = 1.0 / (NumClusters - 1) * NumFragments;
	printf("check free fragement end!\n");

	printf("%d free clusters, %d fragments, ratio: %f%%\n", NumClusters, NumFragments, ratio * 100);

	return ratio;
}

float
check_filefragment(struct bootblock *boot, struct fatEntry *fat)
{
	cl_t head, p, wdk;
	int NumClains = 0;
	int NumClusters = 0;
	int NumFragments = 0;
	float ratio = 0;

	printf("check file fragement begin!\n");
	for (head = CLUST_FIRST; head < boot->NumClusters; head++) {

		if (fat[head].head != head)
			continue;

		NumClains++;
		NumClusters = 0;
		NumFragments = 0;

		for (p = head,wdk=boot->NumClusters;
		      fat[p].next >= CLUST_FIRST && wdk;
				 p = fat[p].next,wdk--) {
			if (fat[p].head != head)
				break;

			if ((1 != fat[p].next - p) && (fat[p].next < boot->NumClusters))
				NumFragments++;

			NumClusters++;

			if ((fat[p].next < CLUST_FIRST) || (fat[p].next > boot->NumClusters))
				break;
		}

		if (NumClusters > 1)
			ratio += 1.0 / (NumClusters - 1) * NumFragments;

	}

	if (0 != NumClains)
		ratio = ratio / NumClains;
	printf("check file fragement end!\n");

	printf("%d clains, file fragement ratio: %f%%\n", NumClains, ratio * 100);
	return ratio;
}

/*
 * Check a complete in-memory FAT for lost cluster chains
 */
int
checklost(int dosfs, struct bootblock *boot, struct fatEntry *fat)
{
	cl_t head;
	int mod = FSOK;
	int ret;
	
	for (head = CLUST_FIRST; head < boot->NumClusters; head++) {
		/* find next untravelled chain */
		if (fat[head].head != head
		    || fat[head].next == CLUST_FREE
		    || (fat[head].next >= CLUST_RSRVD
			&& fat[head].next < CLUST_EOFS)
		    || (fat[head].flags & FAT_USED))
			continue;

		pwarn("Lost cluster chain at cluster %u\n%d Cluster(s) lost\n",
		      head, fat[head].length);
		mod |= ret = reconnect(dosfs, boot, fat, head);
		if (mod & FSFATAL) {
			/* If the reconnect failed, then just clear the chain */
			pwarn("Error reconnecting chain - clearing\n");
			mod &= ~FSFATAL;
			clearchain(boot, fat, head);
			mod |= FSFATMOD;
			continue;
		}
		if (ret == FSERROR && ask(1, "Clear")) {
			clearchain(boot, fat, head);
			mod |= FSFATMOD;
		}
	}
	finishlf();

	if (boot->FSInfo) {
		ret = 0;
		if (boot->FSFree != boot->NumFree) {
			pwarn("Free space in FSInfo block (%d) not correct (%d)\n",
			      boot->FSFree, boot->NumFree);
			if (ask(1, "Fix")) {
				boot->FSFree = boot->NumFree;
				ret = 1;
			}
		}

		if (boot->NumFree) {
			if ((boot->FSNext >= boot->NumClusters) || (fat[boot->FSNext].next != CLUST_FREE)) {
				pwarn("Next free cluster in FSInfo block (%u) not free\n",
				      boot->FSNext);
				if (ask(1, "Fix"))
					for (head = CLUST_FIRST; head < boot->NumClusters; head++)
						if (fat[head].next == CLUST_FREE) {
							boot->FSNext = head;
							ret = 1;
							break;
						}
			}
        }

		if (boot->FSNext > boot->NumClusters  ) {
			pwarn("FSNext block (%d) not correct NumClusters (%d)\n",
					boot->FSNext, boot->NumClusters);
			boot->FSNext=CLUST_FIRST; // boot->FSNext can have -1 value.
	    }

		if (boot->NumFree && fat[boot->FSNext].next != CLUST_FREE) {
			pwarn("Next free cluster in FSInfo block (%u) not free\n",
					boot->FSNext);
			if (ask(1, "Fix"))
				for (head = CLUST_FIRST; head < boot->NumClusters; head++)
					if (fat[head].next == CLUST_FREE) {
						boot->FSNext = head;
						ret = 1;
						break;
					}
	    }

		if (ret)
			mod |= writefsinfo(dosfs, boot);
	}

	return mod;
}
