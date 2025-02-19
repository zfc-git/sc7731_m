

#include <linux/types.h>

/*use this instead of memset ,they are the same*/
void * sprd_memset(void * string,int fill,size_t cnt)
{
	unsigned long *stl = (unsigned long *) string;
	unsigned long fl = 0;
	int i = 0;
	char *st8;

	/* do one word  a time while possible */
	if ( ((ulong)string & (sizeof(*stl) - 1)) == 0)
	{
		for (i = 0; i < sizeof(*stl); i++)
		{
			fl <<= 8;
			fl |= fill & 0xff;
		}
		while (cnt >= sizeof(*stl))
		{
			*stl++ = fl;
			cnt -= sizeof(*stl);
		}
	}
	/* fill 8 bits*/
	st8 = (char *)fl;
	while (cnt--)
	{
		*st8++ = fill;
	}

	return string;
}
void * sprd_memcpy(void *dst, const void *src, size_t cnt)
{
	unsigned long *dlt = (unsigned long *)dst, *slt = (unsigned long *)src;
	char *d8, *st8;

	/* while all data is aligned (common case), copy a word at a time */
	if ( (((ulong)dst | (ulong)src) & (sizeof(*dlt) - 1)) == 0) {
		while (cnt >= sizeof(*dlt)) {
			*dlt++ = *slt++;
			cnt -= sizeof(*dlt);
		}
	}
	/* copy the reset one byte at a time */
	d8 = (char *)dlt;
	st8 = (char *)slt;
	while (cnt--)
		*d8++ = *st8++;

	return dst;
}

