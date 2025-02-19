#include <part.h>
#include <common.h>
extern block_dev_desc_t sprd_mmc_dev;
block_dev_desc_t *get_dev(char* ifname, int dev)
{
	return mmc_get_dev();
}

int get_partition_info_by_name (block_dev_desc_t *dev_desc, uchar * partition_name,
						disk_partition_t *info)
{
	switch(dev_desc->part_type){
#ifdef CONFIG_EFI_PARTITION
	case PART_TYPE_EFI:
		if (get_partition_info_by_name_efi(dev_desc, partition_name, info)== 0) {
			//PRINTF ("## Valid EFI partition found ##\n");
			return (0);
		}
		break;
#endif
	default:
		break;
	}
	return (-1);
}
