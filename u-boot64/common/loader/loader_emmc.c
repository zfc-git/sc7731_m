#include <common.h>
#include "loader_common.h"
#include <malloc.h>
#include <mmc.h>
#include <ext_common.h>
#include <ext4fs.h>
#include <asm/sizes.h>

#include <secureboot/sprdsec_header.h>
#include <secureboot/sec_common.h>

#ifdef CONFIG_ARM7_RAM_ACTIVE
    extern void pmic_arm7_RAM_active(void);
#endif

#ifdef CONFIG_MINI_TRUSTZONE
#include "trustzone_def.h"
#endif

#ifdef CONFIG_SECURE_BOOT
#include "secure_boot.h"
#include "secure_verify.h"
#include <linux/types.h>
#endif

#ifdef CONFIG_OF_LIBFDT
#include "dev_tree.h"
#endif

#define MODEM_MAGIC		"SCI1"
#define MODEM_HDR_SIZE		12 //size of a block
#define MODEM_IMG_HDR		0x1
#define MODEM_LAST_HDR		0x100
#define MODEM_SHA1_HDR		0x400
#define MODEM_SHA1_SIZE		20

#define FREE_RAM_SPACE_ADDR		0x9d000000 /* 0x9d000000 ~9f000000  30M Bytes Free*/

typedef struct {
	uint32_t type_flags;
	uint32_t offset;
	uint32_t length;
} data_block_header_t;

#ifdef CONFIG_SUPPORT_TDLTE
static boot_image_required_t const s_boot_image_tl_table[] = {
#if !defined( CONFIG_KERNEL_BOOT_CP )
	{"tl_fixnv1", "tl_fixnv2", LTE_FIXNV_SIZE, LTE_FIXNV_ADDR},
	{"tl_runtimenv1", "tl_runtimenv2", LTE_RUNNV_SIZE, LTE_RUNNV_ADDR},
	{"tl_modem", NULL, LTE_MODEM_SIZE, LTE_MODEM_ADDR},
	{"tl_ldsp", NULL, LTE_LDSP_SIZE, LTE_LDSP_ADDR},	//ltedsp
	{"tl_tgdsp", NULL, LTE_GDSP_SIZE, LTE_GDSP_ADDR},
#endif
	{NULL, NULL, 0, 0}
};
#endif

#ifdef CONFIG_SUPPORT_WLTE
static boot_image_required_t const s_boot_image_wl_table[] = {
#if !defined( CONFIG_KERNEL_BOOT_CP )
	{"wl_fixnv1", "wl_fixnv2", LTE_FIXNV_SIZE, LTE_FIXNV_ADDR},
	{"wl_runtimenv1", "wl_runtimenv2", LTE_RUNNV_SIZE, LTE_RUNNV_ADDR},
	{"wl_modem", NULL, LTE_MODEM_SIZE, LTE_MODEM_ADDR},
	{"wl_ldsp", NULL, LTE_LDSP_SIZE, LTE_LDSP_ADDR},
	{"wl_gdsp", NULL, LTE_GDSP_SIZE, LTE_GDSP_ADDR},
	{"wl_warm", NULL, WL_WARM_SIZE, WL_WARM_ADDR},
#endif
	{NULL, NULL, 0, 0}
};
#endif

#ifdef CONFIG_SUPPORT_GSM
static boot_image_required_t const s_boot_image_gsm_table[] = {
#if !defined( CONFIG_KERNEL_BOOT_CP )
	{"g_fixnv1", "g_fixnv2", GSM_FIXNV_SIZE, GSM_FIXNV_ADDR},
	{"g_runtimenv1", "g_runtimenv2", GSM_RUNNV_SIZE, GSM_RUNNV_ADDR},
	{"g_modem", NULL, GSM_MODEM_SIZE, GSM_MODEM_ADDR},
	{"g_dsp", NULL, GSM_DSP_SIZE, GSM_DSP_ADDR},
#endif
	{NULL, NULL, 0, 0}
};
#endif

#ifdef CONFIG_SUPPORT_LTE
static boot_image_required_t const s_boot_image_lte_table[] = {
#if !defined( CONFIG_KERNEL_BOOT_CP ) && !defined( CONFIG_MEM_LAYOUT_DECOUPLING )
#ifdef  CONFIG_ADVANCED_LTE
	{"l_fixnv1","l_fixnv2",LTE_FIXNV_SIZE,LTE_FIXNV_ADDR},
	{"l_runtimenv1","l_runtimenv2",LTE_RUNNV_SIZE,LTE_RUNNV_ADDR},
	{"l_modem",NULL,LTE_MODEM_SIZE,LTE_MODEM_ADDR},
	{"l_wgdsp",NULL,LTE_WGDSP_SIZE,LTE_WGDSP_ADDR},//wgdsp
	{"l_tgdsp",NULL,LTE_TGDSP_SIZE ,LTE_TGDSP_ADDR}, //tddsp
	{"l_ldsp",NULL,LTE_LDSP_SIZE ,LTE_LDSP_ADDR},
#else
	{"l_fixnv1", "l_fixnv2", LTE_FIXNV_SIZE, LTE_FIXNV_ADDR},
	{"l_runtimenv1", "l_runtimenv2", LTE_RUNNV_SIZE, LTE_RUNNV_ADDR},
	{"l_modem", NULL, LTE_MODEM_SIZE, LTE_MODEM_ADDR},
	{"l_ldsp", NULL, LTE_LDSP_SIZE, LTE_LDSP_ADDR},
	{"l_gdsp", NULL, LTE_GDSP_SIZE, LTE_GDSP_ADDR},
	{"l_warm", NULL, WL_WARM_SIZE, WL_WARM_ADDR},
#endif
#endif
#ifdef  CONFIG_ADVANCED_LTE
	{"l_agdsp",NULL,LTE_AGDSP_SIZE ,LTE_AGDSP_ADDR}, //agdsp now is boot by uboot
#endif
	{NULL, NULL, 0, 0}
};
#endif

#ifdef CONFIG_SUPPORT_TD
static boot_image_required_t const s_boot_image_TD_table[] = {
#if !defined( CONFIG_KERNEL_BOOT_CP )
	{"tdfixnv1", "tdfixnv2", FIXNV_SIZE, TDFIXNV_ADR},
	{"tdruntimenv1", "tdruntimenv2", RUNTIMENV_SIZE, TDRUNTIMENV_ADR},
	{"tdmodem", NULL, TDMODEM_SIZE, TDMODEM_ADR},
	{"tddsp", NULL, TDDSP_SIZE, TDDSP_ADR},
#endif
	{NULL, NULL, 0, 0}
};
#endif

#ifdef CONFIG_SUPPORT_W
static boot_image_required_t const s_boot_image_W_table[] = {
#if !defined( CONFIG_KERNEL_BOOT_CP )
	{"wfixnv1", "wfixnv2", FIXNV_SIZE, WFIXNV_ADR},
	{"wruntimenv1", "wruntimenv2", RUNTIMENV_SIZE, WRUNTIMENV_ADR},
	{"wmodem", NULL, WMODEM_SIZE, WMODEM_ADR},
	{"wdsp", NULL, WDSP_SIZE, WDSP_ADR},
#endif
	{NULL, NULL, 0, 0}
};
#endif

#ifdef CONFIG_SUPPORT_WIFI
static boot_image_required_t const s_boot_image_WIFI_table[] = {
	{"wcnfixnv1", "wcnfixnv2", FIXNV_SIZE, WCNFIXNV_ADR},
	{"wcnruntimenv1", "wcnruntimenv2", RUNTIMENV_SIZE, WCNRUNTIMENV_ADR},
	{"wcnmodem", NULL, WCNMODEM_SIZE, WCNMODEM_ADR},
	{NULL, NULL, 0, 0}
};
#endif

static boot_image_required_t const s_boot_image_COMMON_table[] = {
#ifdef CONFIG_SIMLOCK_ENABLE
	{"simlock", NULL, SIMLOCK_SIZE, SIMLOCK_ADR},
#endif
#ifdef CONFIG_DFS_ENABLE
#if !defined( CONFIG_KERNEL_BOOT_CP )
	{"pm_sys", NULL, DFS_SIZE, DFS_ADDR},
#endif
#endif
	{NULL, NULL, 0, 0}

};

#ifdef CONFIG_MINI_TRUSTZONE
static boot_image_required_t const s_boot_image_TZ_table[] = {
	{"sml", NULL, TRUSTRAM_SIZE, TRUSTRAM_ADR},
	{NULL, NULL, 0, 0}

};
#endif

static boot_image_required_t *const s_boot_image_table[] = {
#ifdef CONFIG_SUPPORT_TDLTE
	s_boot_image_tl_table,
#endif

#ifdef CONFIG_SUPPORT_WLTE
	s_boot_image_wl_table,
#endif

#ifdef CONFIG_SUPPORT_LTE
	s_boot_image_lte_table,
#endif

#ifdef CONFIG_SUPPORT_GSM
	s_boot_image_gsm_table,
#endif

#ifdef CONFIG_SUPPORT_TD
	s_boot_image_TD_table,
#endif

#ifdef CONFIG_SUPPORT_W
	s_boot_image_W_table,
#endif

#ifdef CONFIG_SUPPORT_WIFI
	s_boot_image_WIFI_table,
#endif
#ifdef CONFIG_MINI_TRUSTZONE
	s_boot_image_TZ_table,
#endif
	s_boot_image_COMMON_table,

	0
};

#ifdef CONFIG_SECURE_BOOT
uint8_t header_buf[SEC_HEADER_MAX_SIZE];
#endif

static void efuse_power_on(void)
{
  unsigned int i;
  printf("Efuse_power_on\n");
  *(volatile unsigned int *)(0x402E0000) |= (1<<13);
  *(volatile unsigned int *)(0x40240010) = 0xc0000140;
  for(i=0;i<0xFFFF;i++);
  *(volatile unsigned int *)(0x40240010) = 0xd0000140;
}

static void efuse_power_off(void)
{
  unsigned int i;
  printf("Efuse_power_off\n");
  if((*(volatile unsigned int *)(0x40240010))&(1<<28))
  {
    *(volatile unsigned int *)(0x40240010) = 0xc0000140;
	for(i=0;i<0xFFFF;i++);
  }
  *(volatile unsigned int *)(0x40240010) = 0xa0000140;
  *(volatile unsigned int *)(0x402E0000) &= ~(1<<13);
}

#if 0
static void enable_cc63_module(void)
{
  printf("Enable_cc63_module\n");
  efuse_power_on();
  *(volatile unsigned int *)(0x40240040) = 0x8810;
  *(volatile unsigned int *)(0x40240008) = 0x02;
  *(volatile unsigned int *)(0x40240004) = 0x401;
  *(volatile unsigned int *)(0x4024000c) = 0x01;
  while(((*(volatile unsigned int *)(0x40240020))&0x01) != 0);
  efuse_power_off();
  printf("Enable_cc63_module end\n");
  __asm__("b .");
}

#endif
int read_logoimg(char *bmp_img, size_t size)
{
	block_dev_desc_t *p_block_dev = NULL;
	disk_partition_t info;

	p_block_dev = get_dev("mmc", 0);
	if (NULL == p_block_dev)
		return -1;

	if (!get_partition_info_by_name(p_block_dev, "logo", &info)) {
		if (FALSE == Emmc_Read(PARTITION_USER, info.start, size / EMMC_SECTOR_SIZE, bmp_img)) {
			debugf("function: %s nand read error\n");
			return -1;
		}
		debugf("read logo partition OK!\n");
	} else {
		debugf("no logo partition , no need to display\n");
		return -1;
	}
	return 0;
}

#ifdef CONFIG_SECURE_BOOT

int get_spl_hash(void *hash_data)
{
	NBLHeader *header;
	unsigned int len;
	uint8_t *spl_data;
	int ret = 0;
	int size = CONFIG_SPL_HASH_LEN;
	spl_data = malloc(size);
	if (!spl_data) {
		return ret;
	}

	if (FALSE == Emmc_Read(PARTITION_BOOT1, 0, size / EMMC_SECTOR_SIZE, (uint8_t *) spl_data)) {
		debugf("PARTITION_BOOT1 read error \n");
		return ret;
	}

	header = (NBLHeader *) ((uint8_t *) spl_data + BOOTLOADER_HEADER_OFFSET);
	len = header->mHashLen;
	/*clear header */
	memset(header, 0, sizeof(NBLHeader));
	header->mHashLen = len;
	debugf("cal spl hash len=%d\n", header->mHashLen * 4);
	ret = cal_sha1(spl_data, (header->mHashLen) << 2, hash_data);

	if (spl_data)
		free(spl_data);

	return ret;
}
#endif
void _boot_secure_check(void)
{
#ifdef SECURE_BOOT_ENABLE
	secure_check(DSP_ADR, 0, DSP_ADR + DSP_SIZE - VLR_INFO_OFF, CONFIG_SYS_NAND_U_BOOT_DST + CONFIG_SYS_NAND_U_BOOT_SIZE - KEY_INFO_SIZ - VLR_INFO_OFF);
	secure_check(MODEM_ADR, 0, MODEM_ADR + MODEM_SIZE - VLR_INFO_OFF,
		     CONFIG_SYS_NAND_U_BOOT_DST + CONFIG_SYS_NAND_U_BOOT_SIZE - KEY_INFO_SIZ - VLR_INFO_OFF);
#ifdef CONFIG_SIMLOCK
	secure_check(SIMLOCK_ADR, 0, SIMLOCK_ADR + SIMLOCK_SIZE - VLR_INFO_OFF,
		     CONFIG_SYS_NAND_U_BOOT_DST + CONFIG_SYS_NAND_U_BOOT_SIZE - KEY_INFO_SIZ - VLR_INFO_OFF);
#endif
#endif
	return;
}

/**
	Function for reading user partition.
*/
int _boot_partition_read(block_dev_desc_t * dev, uchar * partition_name, uint32_t offsetsector, uint32_t size, uint8_t * buf)
{
	int ret = 0;
	uint32_t left;
	uint32_t nsct;
	char *sctbuf = NULL;
	disk_partition_t info;

	if (NULL == buf) {
		debugf("buf is NULL!\n");
		goto end;
	}
	nsct = size / EMMC_SECTOR_SIZE;
	left = size % EMMC_SECTOR_SIZE;

	if (get_partition_info_by_name(dev, partition_name, &info)) {
		errorf("get partition %s info failed!\n", partition_name);
		goto end;
	}

	if(nsct) {
		if (FALSE == Emmc_Read(PARTITION_USER, info.start + offsetsector, nsct, buf))
			goto end;
	}

	if (left) {
		sctbuf = malloc(EMMC_SECTOR_SIZE);
		if (NULL != sctbuf) {
			if (FALSE != Emmc_Read(PARTITION_USER, info.start + offsetsector + nsct, 1, sctbuf)) {
				memcpy(buf + (nsct * EMMC_SECTOR_SIZE), sctbuf, left);
				ret = 1;
			}
			free(sctbuf);
		}
	} else {
		ret = 1;
	}

end:
	debugf("partition %s read %s!\n", partition_name, ret ? "success" : "failed");
	return ret;
}

/**
	Function for writing user partition.
*/
int _boot_partition_write(block_dev_desc_t * dev, uchar * partition_name, u32 size, u8 * buf)
{
	disk_partition_t info;

	if (NULL == buf) {
		debugf("buf is NULL!\n");
		return 0;
	}
	size = (size + (EMMC_SECTOR_SIZE - 1)) & (~(EMMC_SECTOR_SIZE - 1));
	size = size / EMMC_SECTOR_SIZE;
	if (0 == get_partition_info_by_name(dev, partition_name, &info)) {
		if (TRUE != Emmc_Write(PARTITION_USER, info.start, size, buf)) {
			debugf("partition:%s read error!\n", partition_name);
			return 0;
		}
	} else {
		debugf("partition:%s >>>get partition info failed!\n", partition_name);
		return 0;
	}
	debugf("partition:%s write success!\n", partition_name);
	return 1;
}

int splash_screen_prepare(void)
{
	int ret;
	size_t size = SZ_1M;
	u8 *addr;
	u8 *s;

	s = getenv("splashimage");
	if (!s) {
		debugf("%s: failed to get env from splashimage\n");
		return -1;
	}
	addr = (u8 *) simple_strtoul(s, NULL, 16);
	ret = read_logoimg(addr, size);
	if (ret) {
		debugf("%s: failed to read logo partition\n");
		return ret;
	}

	return 0;
}

/**
	we assume partition with backup must check ecc.
*/
int _boot_read_partition_with_backup(block_dev_desc_t * dev, boot_image_required_t info)
{
	uint8_t *bakbuf = NULL;
	uint8_t *oribuf = NULL;
	uint8_t status = 0;
	uint8_t header[EMMC_SECTOR_SIZE];
	uint32_t checksum = 0;
	nv_header_t *header_p = NULL;
	uint32_t bufsize = info.size + EMMC_SECTOR_SIZE;

	header_p = header;
	bakbuf = malloc(bufsize);
	if (NULL == bakbuf) {
		debugf("bakbuf malloc fail\n");
		return 0;
	}
	memset(bakbuf, 0xff, bufsize);
	oribuf = malloc(bufsize);
	if (NULL == oribuf) {
		debugf("oribuf malloc fail\n");
		free(bakbuf);
		return 0;
	}
	memset(oribuf, 0xff, bufsize);
	if (_boot_partition_read(dev, info.partition, 0, info.size + EMMC_SECTOR_SIZE, oribuf)) {
		memset(header, 0, EMMC_SECTOR_SIZE);
		memcpy(header, oribuf, EMMC_SECTOR_SIZE);
		checksum = header_p->checksum;
		debugf("_boot_read_partition_with_backup origin checksum 0x%x\n", checksum);
		if (_chkNVEcc(oribuf + EMMC_SECTOR_SIZE, info.size, checksum)) {
			memcpy(info.mem_addr, oribuf + EMMC_SECTOR_SIZE, info.size);
			status += 1;
		}
	}
	if (_boot_partition_read(dev, info.bak_partition, 0, info.size + EMMC_SECTOR_SIZE, bakbuf)) {
		memset(header, 0, EMMC_SECTOR_SIZE);
		memcpy(header, bakbuf, EMMC_SECTOR_SIZE);
		checksum = header_p->checksum;
		debugf("_boot_read_partition_with_backup backup checksum 0x%x\n", checksum);
		if (_chkNVEcc(bakbuf + EMMC_SECTOR_SIZE, info.size, checksum))
			status += 1 << 1;
	}

	switch (status) {
	case 0:
		debugf("(%s)both org and bak partition are damaged!\n", info.partition);
		memset(info.mem_addr, 0, info.size);
		free(bakbuf);
		free(oribuf);
		return 0;
	case 1:
		debugf("(%s)bak partition is damaged!\n", info.bak_partition);
		_boot_partition_write(dev, info.bak_partition, info.size + EMMC_SECTOR_SIZE, oribuf);
		break;
	case 2:
		debugf("(%s)org partition is damaged!\n!", info.partition);
		memcpy(info.mem_addr, bakbuf + EMMC_SECTOR_SIZE, info.size);
		_boot_partition_write(dev, info.partition, info.size + EMMC_SECTOR_SIZE, bakbuf);
		break;
	case 3:
		debugf("(%s)both org and bak partition are ok!\n", info.partition);
		break;
	default:
		debugf("status error!\n");
		free(bakbuf);
		free(oribuf);
		return 0;
	}
	free(bakbuf);
	free(oribuf);
	return 1;
}

static uint32_t get_boot_offset(block_dev_desc_t *dev, boot_image_required_t img_info, uint32_t secure_offset)
{
	uint32_t offset = 0;
	uint32_t hdr_offset = 0;
	data_block_header_t hdr_buf[8]  __attribute__ ((packed));
	uint32_t read_len = sizeof(hdr_buf);
	uint8_t result = 0; // 0:OK, 1:not find, 2:some error occur
	data_block_header_t* hdr_ptr = NULL;

	if(strstr(img_info.partition, "modem")) {
		do {
			if(!_boot_partition_read(dev, img_info.partition, hdr_offset + secure_offset, read_len, (uint8_t*)hdr_buf)) {
				result = 2;
				debugf("get modem boot hdr failed: partitionread failed !\n");
				break;
			}

			if(!hdr_offset) {
				if(memcmp((uint8_t*)hdr_buf, MODEM_MAGIC, strlen(MODEM_MAGIC))) {
					result = 2;
					debugf("old image format!\n");
					break;
				}

				hdr_ptr = hdr_buf + 1;
				hdr_offset = MODEM_HDR_SIZE;
			} else {
				hdr_ptr = hdr_buf;
			}

			while(!(hdr_ptr->type_flags & MODEM_IMG_HDR)) {
				hdr_ptr ++;
				hdr_offset += MODEM_HDR_SIZE;
				if(read_len == ((uint8_t*)hdr_ptr - (uint8_t*)hdr_buf)) {
					result = 1;
					break;
				}

				if(hdr_ptr->type_flags & MODEM_LAST_HDR) {
					result = 2;
					debugf("no modem image, error image header!!!\n");
					break;
				}
			};

			if(result != 1) {
				break;
			}

		} while(1);

		if(!result) {
			offset = hdr_ptr->offset;
			if(hdr_ptr->type_flags & MODEM_SHA1_HDR) {
				offset += MODEM_SHA1_SIZE;
			}
			offset = offset / EMMC_SECTOR_SIZE ;
		}
	}

	debugf("Modem offset Bytes: 0x%x!\n",offset);

	return offset;

}

/**
	Function for reading image which is needed when power on.
*/
int _boot_load_required_image(block_dev_desc_t * dev, boot_image_required_t img_info)
{
	uint32_t secure_boot_offset = 0;
	uint32_t normal_boot_offset = 0;

	debugf("load %s to addr 0x%08x\n", img_info.partition, img_info.mem_addr);

	if (NULL != img_info.bak_partition) {
		debugf("load %s with backup img %s\n", img_info.partition, img_info.bak_partition);
		_boot_read_partition_with_backup(dev, img_info);
	} else {
#if defined (CONFIG_SECURE_BOOT)
		if (!_boot_partition_read(dev, img_info.partition, 0, SEC_HEADER_MAX_SIZE, header_buf)) {
			debugf("%s:%s read error!\n", img_info.partition);
			return 0;
		}
		//if(header_parser(header_buf) )
		secure_boot_offset = get_code_offset(header_buf);
		normal_boot_offset = get_boot_offset(dev, img_info, secure_boot_offset);

		/*_boot_partition_read(dev, img_info.partition, normal_boot_offset + secure_boot_offset, img_info.size, (u8 *) img_info.mem_addr);*/
		_boot_partition_read(dev, img_info.partition, secure_boot_offset, img_info.size + (normal_boot_offset * EMMC_SECTOR_SIZE), (u8 *)FREE_RAM_SPACE_ADDR);

		/*secure_verify("uboot", header_buf, img_info.mem_addr);*/
		secure_verify("uboot", header_buf, (u8 *)FREE_RAM_SPACE_ADDR);
		memcpy((u8 *)img_info.mem_addr, (u8 *)FREE_RAM_SPACE_ADDR + (normal_boot_offset * EMMC_SECTOR_SIZE), img_info.size);
#elif defined (CONFIG_SECBOOT)
		_boot_partition_read(dev,img_info.partition,0,(img_info.size + CERT_SIZE),(u8*)VERIFY_ADDR);
		secboot_param_set((u8*)VERIFY_ADDR,&img_verify_info);
		uboot_verify_img(&img_verify_info,sizeof(imgToVerifyInfo));

		memcpy(img_info.mem_addr,(VERIFY_ADDR + SYS_HEADER_SIZE),img_info.size);
#else
		normal_boot_offset = get_boot_offset(dev, img_info, 0);
		_boot_partition_read(dev, img_info.partition, normal_boot_offset, img_info.size, (u8 *) img_info.mem_addr);

#endif
	}

	return 1;
}

/**
	Function for checking and loading kernel/ramdisk image.
*/
int _boot_load_kernel_ramdisk_image(block_dev_desc_t * dev, char *bootmode, boot_img_hdr * hdr)
{
	uchar *partition = NULL;
	uint32_t size, offset;
	uint32_t dt_img_adr;
	uint32_t secure_boot_offset = 0;
	char  *boot_mode_type_str;
	if (0 == memcmp(bootmode, RECOVERY_PART, strlen(RECOVERY_PART))) {
		partition = "recovery";
		debugf("enter recovery mode!\n");
	} else {
		partition = "boot";
		debugf("enter boot mode!\n");
	}
#ifdef CONFIG_SECURE_BOOT
	if (!_boot_partition_read(dev, partition, 0, 512, (u8 *) hdr)) {
		errorf("%s read error!\n", partition);
		return 0;
	}
	secure_boot_offset = get_code_offset(hdr);
#endif
#ifdef CONFIG_SECBOOT
	secure_boot_offset = 1;
#endif
	if (!_boot_partition_read(dev, partition, 0 + secure_boot_offset, 4 * EMMC_SECTOR_SIZE, (u8 *) hdr)) {
		errorf("%s read error!\n", partition);
		return 0;
	}
	/*image header check */
	if (0 != memcmp(hdr->magic, BOOT_MAGIC, BOOT_MAGIC_SIZE)) {
		errorf("bad boot image header, give up boot!!!!\n");
		return 0;
	}

	/*read kernel image */
	offset = 4;
	size = (hdr->kernel_size + (KERNL_PAGE_SIZE - 1)) & (~(KERNL_PAGE_SIZE - 1));
	if (size <= 0) {
		errorf("kernel image should not be zero!\n");
		return 0;
	}
	if (!_boot_partition_read(dev, partition, offset + secure_boot_offset, size, (u8 *) KERNEL_ADR)) {
		errorf("%s kernel read error!\n", partition);
		return 0;
	}
	debugf("%s kernel read OK,size=%u! \n", partition, size);
	/*read ramdisk image */
	offset += size / 512;
	offset = ((offset + 3) / 4) * 4;
	size = (hdr->ramdisk_size + (KERNL_PAGE_SIZE - 1)) & (~(KERNL_PAGE_SIZE - 1));
	if (size < 0) {
		debugf("ramdisk size error\n");
		return 0;
	}
	if (!_boot_partition_read(dev, partition, offset + secure_boot_offset, size, (u8 *) RAMDISK_ADR)) {
		errorf("ramdisk read error!\n");
		return 0;
	}
	debugf("%s ramdisk read OK,size=%u! \n", partition, size);

#ifdef CONFIG_OF_LIBFDT
#ifdef CONFIG_SEPARATE_DT
	if (!_boot_load_separate_dt())
		return 0;
#else
	//read dt image
	offset += size / 512;
	offset = ((offset + 3) / 4) * 4;
	size = (hdr->dt_size + (KERNL_PAGE_SIZE - 1)) & (~(KERNL_PAGE_SIZE - 1));
	dt_img_adr = RAMDISK_ADR - size - KERNL_PAGE_SIZE;
	if (size < 0) {
		errorf("dt size error\n");
		return 0;
	}
	if (!_boot_partition_read(dev, partition, offset + secure_boot_offset, size, (u8 *) dt_img_adr)) {
		errorf("dt read error!\n");
		return 0;
	}
	debugf("%s dtb read OK,size=%u! \n", partition, size);
	if (load_dtb((void*) DT_ADR, (void *)dt_img_adr)) {
		errorf("dt load error!\n");
		return 0;
	}
#endif
#endif

	boot_mode_type_str = getenv("bootmode");
	if(!strncmp(boot_mode_type_str, "sprdisk", 7)) {
		int ramdisk_size;
		ramdisk_size = boot_sprdisk();
		if (ramdisk_size > 0)
			hdr->ramdisk_size = ramdisk_size;
		else
			errorf("%s, sprdisk mode failure!\n", __FUNCTION__);
		return 1;
	}

#ifdef CONFIG_SDRAMDISK
	{
		int sd_ramdisk_size = 0;
#ifdef WDSP_ADR
		size = WDSP_ADR - RAMDISK_ADR;
#else
		size = TDDSP_ADR - RAMDISK_ADR;
#endif
		if (size > 0)
			sd_ramdisk_size = load_sd_ramdisk((void *) RAMDISK_ADR, size);
		if (sd_ramdisk_size > 0)
			hdr->ramdisk_size = sd_ramdisk_size;
	}
#endif
	return 1;
}

#ifdef CONFIG_SEPARATE_DT
int _boot_load_separate_dt(void)
{
	struct dt_table *table = malloc(EMMC_SECTOR_SIZE);
	struct dt_entry *dt_entry_ptr;
	uint32_t secure_boot_offset = 0;

#ifdef CONFIG_SECURE_BOOT
	if (do_raw_data_read("dt", EMMC_SECTOR_SIZE, 0, (u8 *) table)) {
		errorf("dt read error!\n");
		free(table);
		return 0;
	}
	secure_boot_offset = get_code_offset(table);
#endif

	if (do_raw_data_read("dt", EMMC_SECTOR_SIZE, 0 + secure_boot_offset, (u8 *) table)) {
		errorf("dt read error!\n");
		free(table);
		return 0;
	}

	/* Validate the device tree table header */
	if((table->magic != DEV_TREE_MAGIC) && (table->version != DEV_TREE_VERSION)) {
		errorf("Cannot validate Device Tree Table \n");
		free(table);
		return 0;
	}

	/* Calculate the offset of device tree within device tree table */
	if((dt_entry_ptr = dev_tree_get_entry_ptr(table)) == NULL) {
		errorf("Getting device tree address failed\n");
		free(table);
		return 0;
	}

	if (do_raw_data_read("dt", dt_entry_ptr->size,
			dt_entry_ptr->offset + secure_boot_offset, (u8 *) DT_ADR)) {
		errorf("dt read error!\n");
		free(table);
		return 0;
	}

	debugf("dtb read OK,size=%u! \n", dt_entry_ptr->size);
	free(table);
	return 1;
}
#endif

#ifdef CONFIG_SECURE_BOOT
int secure_verify_partition(block_dev_desc_t * dev, uchar * partition_name, void *ram_addr)
{
	int ret = 0;
	int size;
	disk_partition_t info;

	if (get_partition_info_by_name(dev, partition_name, &info)) {
		errorf("verify get partition %s info failed!\n", partition_name);
		ret = 1;
	}
	size = info.size * EMMC_SECTOR_SIZE;
	debugf("%s=%x  =%x\n", partition_name, info.size, size);
	_boot_partition_read(dev, partition_name, 0, size, (uint8_t *) ram_addr);
	secure_verify("uboot", (uint8_t *) ram_addr, 0);
	return ret;
}
#endif

#ifdef CONFIG_SECBOOT
int secure_load_partition(block_dev_desc_t * dev,uchar * partition_name,void *ram_addr)
{
	int ret = 0;
	int size;
	disk_partition_t info;

	if(get_partition_info_by_name(dev,partition_name,&info)){
		debugf("load partition %s info failed\n",partition_name);
		ret = 1;
	}
	size = info.size * EMMC_SECTOR_SIZE;
	debugf("%s=%x =%x\n",partition_name,info.size,size);
	_boot_partition_read(dev,partition_name,0,size,(uint8_t*)ram_addr);
	return ret;
}
#endif
void vlx_nand_boot(char *kernel_pname, int backlight_set)
{
	boot_img_hdr *hdr = (void *)raw_header;
	block_dev_desc_t *dev = NULL;
	char *mode_ptr = NULL;
	uchar *partition = NULL;
	int i = 0;
	int j = 0;
	int ret = 0;
	dev = get_dev("mmc", 0);
	if (NULL == dev) {
		errorf("Fatal Error,get_dev mmc failed!\n");
		return;
	}
#ifdef CONFIG_SPLASH_SCREEN
	set_backlight(backlight_set);
#endif

#if defined(CONFIG_SECURE_BOOT)||defined(CONFIG_SECBOOT)
	if (0 == memcmp(kernel_pname, RECOVERY_PART, strlen(RECOVERY_PART))) {
		partition = "recovery";
	} else {
		partition = "boot";
	}
#if defined (CONFIG_SECURE_BOOT)
	secure_verify_partition(dev, partition, KERNEL_ADR);
#endif
#if defined (CONFIG_SECBOOT)
	secure_load_partition(dev,partition,VERIFY_ADDR);
	secboot_param_set(VERIFY_ADDR,&img_verify_info);
	secboot_get_pubkhash(UBOOT_START,img_verify_info.pubkeyhash);
	uboot_verify_img(&img_verify_info,sizeof(imgToVerifyInfo));
#endif
#endif
#ifdef OTA_BACKUP_MISC_RECOVERY
	ret = memcmp(kernel_pname, RECOVERY_PART, strlen(RECOVERY_PART));
	if ((ret != 0) || (boot_load_recovery_in_sd(hdr) != 0))
		if (!_boot_load_kernel_ramdisk_image(dev, kernel_pname, hdr))
			return;
#else
	//loader kernel and ramdisk
	if (!_boot_load_kernel_ramdisk_image(dev, kernel_pname, hdr))
		return;
#endif


	set_vibrator(0);

#ifdef BOOT_NATIVE_LINUX_MODEM
	/*load required image which config in table */
	i = 0;
#ifdef CONFIG_ARM7_RAM_ACTIVE
			pmic_arm7_RAM_active();
#endif
	while (s_boot_image_table[i]) {
		j = 0;
		while (s_boot_image_table[i][j].partition) {
			_boot_load_required_image(dev, s_boot_image_table[i][j]);
			j++;
		}
		i++;
	}

#if !defined(CONFIG_KERNEL_BOOT_CP) && defined(CONFIG_MEM_LAYOUT_DECOUPLING)
	do {
		extern boot_image_required_t *get_cp_boot_table(void);
		boot_image_required_t *cp_boot_table = NULL;
		cp_boot_table = get_cp_boot_table();
		printf("cp_boot_table = 0x%p\n", cp_boot_table);
		if(NULL != cp_boot_table) {
			for(i = 0; cp_boot_table[i].size > 0; i++) {
				printf("cp_boot_table[%d].size = 0x\n", i, cp_boot_table[i]);
				_boot_load_required_image(dev, cp_boot_table[i]);
			}
			printf("sizeof: 0x%x, size 0x%x\n", sizeof(boot_image_required_t), cp_boot_table[i].size);
			printf("cp_boot_table[%d] = 0x%p\n", i, &(cp_boot_table[i]));
		}
	} while(0);
#endif

#endif

#if defined (CONFIG_SECBOOT)
	uint32_t flag;
#ifdef CONFIG_SPRD_SECBOOT
	flag = SPRD_FLAG;
#endif
#ifdef CONFIG_SANSA_SECBOOT
	flag = SANSA_FLAG;
#endif
	efuse_power_on();
	uboot_update_swVersion(&flag,sizeof(flag));
	efuse_power_off();
#endif

#if 0
enable_cc63_module();
#endif
	vlx_entry();
}
