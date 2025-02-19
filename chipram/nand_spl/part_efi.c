#include <part.h>
#include <part_efi.h>
#include <common.h>
#include <compiler.h>
#include <malloc.h>
#include <common.h>
#include<linux/types.h>

#define tole(x)	cpu_to_le32(x)

unsigned char gpt_h[512];
gpt_header *gpt_head =gpt_h;
unsigned char gpt_entry_data[512];

static const uint32_t crc32_tab[256] = {
tole(0x00000000L), tole(0x77073096L), tole(0xee0e612cL), tole(0x990951baL),
tole(0x076dc419L), tole(0x706af48fL), tole(0xe963a535L), tole(0x9e6495a3L),
tole(0x0edb8832L), tole(0x79dcb8a4L), tole(0xe0d5e91eL), tole(0x97d2d988L),
tole(0x09b64c2bL), tole(0x7eb17cbdL), tole(0xe7b82d07L), tole(0x90bf1d91L),
tole(0x1db71064L), tole(0x6ab020f2L), tole(0xf3b97148L), tole(0x84be41deL),
tole(0x1adad47dL), tole(0x6ddde4ebL), tole(0xf4d4b551L), tole(0x83d385c7L),
tole(0x136c9856L), tole(0x646ba8c0L), tole(0xfd62f97aL), tole(0x8a65c9ecL),
tole(0x14015c4fL), tole(0x63066cd9L), tole(0xfa0f3d63L), tole(0x8d080df5L),
tole(0x3b6e20c8L), tole(0x4c69105eL), tole(0xd56041e4L), tole(0xa2677172L),
tole(0x3c03e4d1L), tole(0x4b04d447L), tole(0xd20d85fdL), tole(0xa50ab56bL),
tole(0x35b5a8faL), tole(0x42b2986cL), tole(0xdbbbc9d6L), tole(0xacbcf940L),
tole(0x32d86ce3L), tole(0x45df5c75L), tole(0xdcd60dcfL), tole(0xabd13d59L),
tole(0x26d930acL), tole(0x51de003aL), tole(0xc8d75180L), tole(0xbfd06116L),
tole(0x21b4f4b5L), tole(0x56b3c423L), tole(0xcfba9599L), tole(0xb8bda50fL),
tole(0x2802b89eL), tole(0x5f058808L), tole(0xc60cd9b2L), tole(0xb10be924L),
tole(0x2f6f7c87L), tole(0x58684c11L), tole(0xc1611dabL), tole(0xb6662d3dL),
tole(0x76dc4190L), tole(0x01db7106L), tole(0x98d220bcL), tole(0xefd5102aL),
tole(0x71b18589L), tole(0x06b6b51fL), tole(0x9fbfe4a5L), tole(0xe8b8d433L),
tole(0x7807c9a2L), tole(0x0f00f934L), tole(0x9609a88eL), tole(0xe10e9818L),
tole(0x7f6a0dbbL), tole(0x086d3d2dL), tole(0x91646c97L), tole(0xe6635c01L),
tole(0x6b6b51f4L), tole(0x1c6c6162L), tole(0x856530d8L), tole(0xf262004eL),
tole(0x6c0695edL), tole(0x1b01a57bL), tole(0x8208f4c1L), tole(0xf50fc457L),
tole(0x65b0d9c6L), tole(0x12b7e950L), tole(0x8bbeb8eaL), tole(0xfcb9887cL),
tole(0x62dd1ddfL), tole(0x15da2d49L), tole(0x8cd37cf3L), tole(0xfbd44c65L),
tole(0x4db26158L), tole(0x3ab551ceL), tole(0xa3bc0074L), tole(0xd4bb30e2L),
tole(0x4adfa541L), tole(0x3dd895d7L), tole(0xa4d1c46dL), tole(0xd3d6f4fbL),
tole(0x4369e96aL), tole(0x346ed9fcL), tole(0xad678846L), tole(0xda60b8d0L),
tole(0x44042d73L), tole(0x33031de5L), tole(0xaa0a4c5fL), tole(0xdd0d7cc9L),
tole(0x5005713cL), tole(0x270241aaL), tole(0xbe0b1010L), tole(0xc90c2086L),
tole(0x5768b525L), tole(0x206f85b3L), tole(0xb966d409L), tole(0xce61e49fL),
tole(0x5edef90eL), tole(0x29d9c998L), tole(0xb0d09822L), tole(0xc7d7a8b4L),
tole(0x59b33d17L), tole(0x2eb40d81L), tole(0xb7bd5c3bL), tole(0xc0ba6cadL),
tole(0xedb88320L), tole(0x9abfb3b6L), tole(0x03b6e20cL), tole(0x74b1d29aL),
tole(0xead54739L), tole(0x9dd277afL), tole(0x04db2615L), tole(0x73dc1683L),
tole(0xe3630b12L), tole(0x94643b84L), tole(0x0d6d6a3eL), tole(0x7a6a5aa8L),
tole(0xe40ecf0bL), tole(0x9309ff9dL), tole(0x0a00ae27L), tole(0x7d079eb1L),
tole(0xf00f9344L), tole(0x8708a3d2L), tole(0x1e01f268L), tole(0x6906c2feL),
tole(0xf762575dL), tole(0x806567cbL), tole(0x196c3671L), tole(0x6e6b06e7L),
tole(0xfed41b76L), tole(0x89d32be0L), tole(0x10da7a5aL), tole(0x67dd4accL),
tole(0xf9b9df6fL), tole(0x8ebeeff9L), tole(0x17b7be43L), tole(0x60b08ed5L),
tole(0xd6d6a3e8L), tole(0xa1d1937eL), tole(0x38d8c2c4L), tole(0x4fdff252L),
tole(0xd1bb67f1L), tole(0xa6bc5767L), tole(0x3fb506ddL), tole(0x48b2364bL),
tole(0xd80d2bdaL), tole(0xaf0a1b4cL), tole(0x36034af6L), tole(0x41047a60L),
tole(0xdf60efc3L), tole(0xa867df55L), tole(0x316e8eefL), tole(0x4669be79L),
tole(0xcb61b38cL), tole(0xbc66831aL), tole(0x256fd2a0L), tole(0x5268e236L),
tole(0xcc0c7795L), tole(0xbb0b4703L), tole(0x220216b9L), tole(0x5505262fL),
tole(0xc5ba3bbeL), tole(0xb2bd0b28L), tole(0x2bb45a92L), tole(0x5cb36a04L),
tole(0xc2d7ffa7L), tole(0xb5d0cf31L), tole(0x2cd99e8bL), tole(0x5bdeae1dL),
tole(0x9b64c2b0L), tole(0xec63f226L), tole(0x756aa39cL), tole(0x026d930aL),
tole(0x9c0906a9L), tole(0xeb0e363fL), tole(0x72076785L), tole(0x05005713L),
tole(0x95bf4a82L), tole(0xe2b87a14L), tole(0x7bb12baeL), tole(0x0cb61b38L),
tole(0x92d28e9bL), tole(0xe5d5be0dL), tole(0x7cdcefb7L), tole(0x0bdbdf21L),
tole(0x86d3d2d4L), tole(0xf1d4e242L), tole(0x68ddb3f8L), tole(0x1fda836eL),
tole(0x81be16cdL), tole(0xf6b9265bL), tole(0x6fb077e1L), tole(0x18b74777L),
tole(0x88085ae6L), tole(0xff0f6a70L), tole(0x66063bcaL), tole(0x11010b5cL),
tole(0x8f659effL), tole(0xf862ae69L), tole(0x616bffd3L), tole(0x166ccf45L),
tole(0xa00ae278L), tole(0xd70dd2eeL), tole(0x4e048354L), tole(0x3903b3c2L),
tole(0xa7672661L), tole(0xd06016f7L), tole(0x4969474dL), tole(0x3e6e77dbL),
tole(0xaed16a4aL), tole(0xd9d65adcL), tole(0x40df0b66L), tole(0x37d83bf0L),
tole(0xa9bcae53L), tole(0xdebb9ec5L), tole(0x47b2cf7fL), tole(0x30b5ffe9L),
tole(0xbdbdf21cL), tole(0xcabac28aL), tole(0x53b39330L), tole(0x24b4a3a6L),
tole(0xbad03605L), tole(0xcdd70693L), tole(0x54de5729L), tole(0x23d967bfL),
tole(0xb3667a2eL), tole(0xc4614ab8L), tole(0x5d681b02L), tole(0x2a6f2b94L),
tole(0xb40bbe37L), tole(0xc30c8ea1L), tole(0x5a05df1bL), tole(0x2d02ef8dL)
};
static uint32_t __efi_crc32(const void *buf, int len, uint32_t seed)
{
	int i;
	register uint32_t crc32val;
	const unsigned char *s = buf;

	crc32val = seed;
	for (i = 0; i < len; i++) {
		crc32val = crc32_tab[(crc32val ^ s[i]) & 0xff] ^ (crc32val >> 8);
	}

	return crc32val;
}

uint32_t crc32(const void *buf, int len)
{
    return (__efi_crc32(buf, len, ~0L) ^ ~0L);
}
 gpt_entry *alloc_read_gpt_entries(block_dev_desc_t * dev_desc,
					 gpt_header * pgpt_head,int count)
{
	int i;
	gpt_entry *pte = NULL;
	pte=gpt_entry_data;
	if (!dev_desc || !pgpt_head) {
		return NULL;
	}
	if (dev_desc->block_read (dev_desc->dev,
		le64_to_cpu(pgpt_head->partition_entry_lba+count),
		1, pte)!= 1) {
		return NULL;
	}
	return pte;
}
 int is_gpt_valid(block_dev_desc_t * dev_desc, unsigned long long lba,
			gpt_header * pgpt_head, gpt_entry ** pgpt_pte)
{
	u32 crc32_backup = 0;
	u32 calc_crc32;
	if (!dev_desc || !pgpt_head) {
		return 0;
	}
	/* Read GPT Header from device */
	if (dev_desc->block_read(dev_desc->dev, lba, 1, pgpt_head) != 1) {
		return 0;
	}
	/* Check the GPT header signature */
	if (le64_to_cpu(pgpt_head->signature) != GPT_HEADER_SIGNATURE) {
		return 0;
	}
	/* Check the GUID Partition Table CRC */
	sprd_memcpy(&crc32_backup, &pgpt_head->header_crc32, sizeof(crc32_backup));
	memset(&pgpt_head->header_crc32, 0, sizeof(pgpt_head->header_crc32));

	calc_crc32 = crc32((const unsigned char *)pgpt_head,
		le32_to_cpu(pgpt_head->header_size));

	sprd_memcpy(&pgpt_head->header_crc32, &crc32_backup, sizeof(crc32_backup));

	if (calc_crc32 != le32_to_cpu(crc32_backup)) {
		return 0;
	}

	/* Check that the my_lba entry points to the LBA that contains the GPT */
	if (le64_to_cpu(pgpt_head->my_lba) != lba) {
		return 0;
	}

	/* Read and allocate Partition Table Entries */
	return 1;
}


int get_partition_info_by_name_efi(block_dev_desc_t * dev_desc, uchar * partition_name,
				disk_partition_t * info)
{
	gpt_entry *pgpt_pte = NULL;
	int ret = -1;
	unsigned int i,j,z;
	uchar disk_parition[PARTNAME_SZ];

	if (!dev_desc || !info || !partition_name) {
		return -1;
	}

	/* This function validates AND fills in the GPT header and PTE */
	if (is_gpt_valid(dev_desc, GPT_PRIMARY_PARTITION_TABLE_LBA,
			gpt_head, &pgpt_pte) != 1) {
			return -1;
	}

	for(i = 0; i<GPT_BLKSIZE; i++) {
		if(NULL == alloc_read_gpt_entries(dev_desc, gpt_head,i))
			return -1;

		for(z = 0; z<ENTRY_SIZE; z++) {
			pgpt_pte=&gpt_entry_data[128*z];
			for(j = 0; j<PARTNAME_SZ; j++) {
				disk_parition[j] =(* pgpt_pte).partition_name[j] & 0xFF;
			}
			if(0 == sprd_strcmp(disk_parition, partition_name)) {
				/* The ulong casting limits the maximum disk size to 2 TB */
				info->start = (ulong) le64_to_cpu((*pgpt_pte).starting_lba);
				/* The ending LBA is inclusive, to calculate size, add 1 to it */
				info->size = ((ulong)le64_to_cpu((*pgpt_pte).ending_lba) + 1) - info->start;
				info->blksz = dev_desc->blksz;
				ret = 0;
				break;
			}
		}
		if(ret==0)
			break;
	}

	return ret;
}

