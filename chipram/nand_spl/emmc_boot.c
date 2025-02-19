#include <config.h>
#include <common.h>
#include <nand.h>
#include <boot_mode.h>
#include <asm/io.h>
//#include "../drivers/mmc/card_sdio.h"
#include <card_sdio.h>
#if defined(CONFIG_SCX35L64) || defined(CONFIG_CHIP_ENV_SET) || defined (CONFIG_WHALE)
#include <asm/arch/sprd_chipram_env.h>
#endif
#include <asm/arch/secure_boot.h>
#include <part.h>
#include <part_efi.h>
//#include <security/sec_common.h>
#define SECURE_HEADER_OFF 512


#ifdef CONFIG_DUAL_SPL
/* these parameter used to get the size of uboot.bin from sec header*/
#define SEHEADER_UBOOT_SIZE_OFFSET 0x30
#define BYTE3 0x03
#define BYTE2 0x02
#define BYTE1 0x01
#define BYTE0 0x0
uint8 hash_temp[HASH_TEMP_LEN]={0};
unsigned long uboot_size_no_hash=0;
uint8 *uboot_start_addr = CONFIG_SYS_NAND_U_BOOT_DST;//0x9f00_0000
uint8 *uboot_start_hash_addr = CONFIG_SYS_NAND_U_BOOT_DST-EMMC_SECTOR_SIZE;
uint8 *uboot_size_no_hash_addr=CONFIG_SYS_NAND_U_BOOT_DST-EMMC_SECTOR_SIZE+SEHEADER_UBOOT_SIZE_OFFSET;
BOOLEAN Spl_Hash_Exist(uint8 *p_hash,uint hash_len)
{
	uint32 i;
	for(i=0;i<hash_len;i++)
		{
		if(*(p_hash+i)!=0)
			return TRUE;
		}
	return FALSE;
}

#endif

#if defined(CONFIG_ARM)
void board_init_f(ulong bootflag)
{
	relocate_code(CONFIG_SYS_TEXT_BASE - TOTAL_MALLOC_LEN, NULL, CONFIG_SYS_TEXT_BASE);
}
#endif
#ifdef CONFIG_LOAD_PARTITION
int read_common_partition(block_dev_desc_t *dev, uchar * partition_name, uint32_t offsetsector, uint32_t size, uint8_t* buf)
{
	uint32_t count,left;
	disk_partition_t info;
	unsigned char left_buf[512];

	if (NULL == buf)
		return -1;

	count = size/EMMC_SECTOR_SIZE;
	left = size %EMMC_SECTOR_SIZE;
	if (get_partition_info_by_name(dev, partition_name, &info))
		return -1;
	if(0!=count)
		if(FALSE == Emmc_Read(PARTITION_USER, info.start+offsetsector, count, buf))
			return -1;
	if (left) {
		if (0 != Emmc_Read(PARTITION_USER, info.start+offsetsector+count, 1, left_buf)){
			sprd_memcpy(buf+(count*EMMC_SECTOR_SIZE), left_buf, left);
		}else{
			return -1;
		}
	}

	return 0;
}

int  load_common_partition(uchar * partition_name,uint32_t size, uint8_t* buf)
{
	int  i, retrys = 5;
	block_dev_desc_t *dev = NULL;
	dev = get_dev("mmc", 0);

	if (NULL == dev)
		return -1;

	for(i =0; i<retrys; i++){
	if(0==read_common_partition(dev, partition_name, 0,size, buf))
		      break;
	}

	if(i == 5)
		return -1;

	return 0;
}
#endif

#ifdef CONFIG_DUAL_SPL
int Hash_Check(void)///add the hash_check
{
	uboot_size_no_hash=((*(uboot_size_no_hash_addr+BYTE3))<<24)|((*(uboot_size_no_hash_addr+BYTE2))<<16)|((*(uboot_size_no_hash_addr+BYTE1))<<8)|(*(uboot_size_no_hash_addr+BYTE0));
	sha256_csum_wd(uboot_start_addr,uboot_size_no_hash,hash_temp,NULL);
	if(0==sprd_memcmp(uboot_start_hash_addr+UBOOT_HASH_SIZE_OFFSET,hash_temp,HASH_TEMP_LEN))
	return 0;
	else
	return -1;
}
#endif//end hash_check


void nand_boot(void)
{
	int ret;
	int i, j;
	block_dev_desc_t *dev = NULL;
	disk_partition_t info;
	__attribute__ ((noreturn)) void (*uboot) (void);
#if 0
	unsigned int i = 0;
	for (i = 0xffffffff; i > 0;)
		i--;
#endif
	/*
	 * Init board specific nand support
	 */
#ifdef SPRD_EVM_TAG_ON
#if 0
	unsigned long int *ptr = (unsigned long int *)SPRD_EVM_ADDR_START - 8;
	int ijk = 0;
	for (ijk = 0; ijk < 28; ijk++) {
		*(ptr++) = 0x55555555;
	}
#endif
	SPRD_EVM_TAG(1);
#endif
#ifndef CONFIG_LOAD_PARTITION
#ifdef CONFIG_SECURE_BOOT
	if (TRUE == Emmc_Init()) {
		Emmc_Read(PARTITION_BOOT2, 2, (CONFIG_SYS_EMMC_U_BOOT_SECTOR_NUM + 1), (uint8 *) (CONFIG_SYS_NAND_U_BOOT_DST - KEY_INFO_SIZ));
		//Emmc_Read(PARTITION_BOOT2, 2, (CONFIG_SYS_EMMC_U_BOOT_SECTOR_NUM + 1), (uint8 *)(CONFIG_SYS_NAND_U_BOOT_DST));
		Emmc_Read(PARTITION_BOOT2, 1, 1, (uint8 *) VLR_INFO_OFF);
	}
#elif defined(CONFIG_NO_SD_BOOT)
	if (TRUE == Emmc_Init()) {
		Emmc_Read(PARTITION_BOOT2, 0, CONFIG_SYS_EMMC_U_BOOT_SECTOR_NUM, (uint8 *) CONFIG_SYS_NAND_U_BOOT_DST);
	}
#else
	if (TRUE == isSDCardBoot()) {
		if (TRUE == SD_Init()) {
			SD_CARD_Read(PARTITION_BOOT2, 0, CONFIG_SYS_EMMC_U_BOOT_SECTOR_NUM, (uint8 *) CONFIG_SYS_NAND_U_BOOT_DST);
		} else {
			/* if boot from SD card fail, boot from EMMC as romcode */
			if (TRUE == Emmc_Init()) {
				Emmc_Read(PARTITION_BOOT2, 0, CONFIG_SYS_EMMC_U_BOOT_SECTOR_NUM, (uint8 *) CONFIG_SYS_NAND_U_BOOT_DST);
			}
		}
	} else {
		if (TRUE == Emmc_Init()) {
			Emmc_Read(PARTITION_BOOT2, 0, CONFIG_SYS_EMMC_U_BOOT_SECTOR_NUM, (uint8 *) CONFIG_SYS_NAND_U_BOOT_DST);
		}
	}
#endif
#endif
#ifdef CONFIG_LOAD_PARTITION
		if (TRUE == isSDBoot_for_KpdDisable()) {
			if (TRUE == SD_Init()) {
				SD_CARD_Read(PARTITION_BOOT2, SDCARD_BOOT_SECTOR, CONFIG_SYS_EMMC_U_BOOT_SECTOR_NUM, (uint8 *) (uint8_t*)(CONFIG_SYS_NAND_U_BOOT_START-UBOOT_HASH_SIZE));
			} else {
				/* if boot from SD card fail, boot from EMMC as romcode */
				if (TRUE == Emmc_Init()) {
					load_common_partition("uboot",CONFIG_SYS_EMMC_U_BOOT_SECTOR_NUM*EMMC_SECTOR_SIZE,(uint8_t*)(CONFIG_SYS_NAND_U_BOOT_DST-EMMC_SECTOR_SIZE));// compatible whale tshark3
				}
			}
		}else{
		if(TRUE == Emmc_Init()){
#if CONFIG_SMLBOOT// whale
			load_common_partition("sml",SML_LOAD_MAX_SIZE,(uint8_t*)(CONFIG_SML_LDADDR_START-IMAGE_HEAD_SIZE));
			load_common_partition("trustos", TOS_LOAD_MAX_SIZE,(uint8_t*)(CONFIG_TOS_LDADDR_START-IMAGE_HEAD_SIZE));
#endif
			load_common_partition("uboot",CONFIG_SYS_EMMC_U_BOOT_SECTOR_NUM*EMMC_SECTOR_SIZE,(uint8_t*)(CONFIG_SYS_NAND_U_BOOT_DST-EMMC_SECTOR_SIZE));// compatible whale tshark3
			#ifdef CONFIG_DUAL_SPL//only for tshark3 dual spl;
			if(Spl_Hash_Exist(uboot_start_hash_addr+UBOOT_HASH_SIZE_OFFSET,HASH_TEMP_LEN))
			{
				if(0!=Hash_Check())
				{
				load_common_partition("uboot_bak",CONFIG_SYS_EMMC_U_BOOT_SECTOR_NUM*EMMC_SECTOR_SIZE,(uint8_t*)(CONFIG_SYS_NAND_U_BOOT_DST-EMMC_SECTOR_SIZE));
				if(0!=Hash_Check())
					while(1);
				}
			}
			#endif
		}
	}
#endif

#ifdef CONFIG_SECBOOT
#if CONFIG_SMLBOOT 
		secboot_verify(IRAM_BEGIN,(CONFIG_SML_LDADDR_START - IMAGE_HEAD_SIZE),NULL,NULL);
		secboot_verify(IRAM_BEGIN,(CONFIG_TOS_LDADDR_START - IMAGE_HEAD_SIZE),NULL,NULL);
#endif
		secboot_verify(IRAM_BEGIN,(CONFIG_SYS_NAND_U_BOOT_DST - SECURE_HEADER_OFF),NULL,NULL);
#endif

	/*
	 * Jump to U-Boot image
	 */
#ifdef SPRD_EVM_TAG_ON
	SPRD_EVM_TAG(3);
#endif


#if CONFIG_SMLBOOT
		uboot = (void *)CONFIG_SML_LDADDR_START;
#else
		uboot = (void *)CONFIG_SYS_NAND_U_BOOT_START;
#endif

#ifdef CONFIG_SECURE_BOOT
#if defined(CONFIG_SC8830) || defined(CONFIG_SC9630)  || defined(CONFIG_SCX35L64)
	typedef sec_callback_func_t *(*get_secure_checkfunc_t) (sec_callback_func_t *);
	sec_callback_func_t sec_callfunc;
	vlr_info_t *vlr_info = (vlr_info_t *) (VLR_INFO_OFF);
	get_secure_checkfunc_t *get_secure_func = (get_secure_checkfunc_t *) (INTER_RAM_BEGIN + 0x1C);
	(*get_secure_func) (&sec_callfunc);
	sec_callfunc.secure_check((CONFIG_SYS_NAND_U_BOOT_START - KEY_INFO_SIZ), vlr_info->length, vlr_info, KEY_INFO_OFF);
	//sec_callfunc.secure_check((CONFIG_SYS_NAND_U_BOOT_START),vlr_info->length, vlr_info, KEY_INFO_OFF);
#elif defined(CONFIG_FPGA)

#else
	secure_check(CONFIG_SYS_NAND_U_BOOT_START, 0, CONFIG_SYS_NAND_U_BOOT_START + CONFIG_SYS_NAND_U_BOOT_SIZE - VLR_INFO_OFF,
		     INTER_RAM_BEGIN + CONFIG_SPL_LOAD_LEN - KEY_INFO_SIZ - CUSTOM_DATA_SIZ);
#endif
#endif
	/* disable emmc sd_clk */
	Emmc_DisSdClk();
#if defined(CONFIG_SCX35L64)
	chipram_env_set(BOOTLOADER_MODE_LOAD);
	extern void switch64_and_set_pc(u32 addr);
	switch64_and_set_pc(CONFIG_SYS_NAND_U_BOOT_START);
#else
#if  defined(CONFIG_CHIP_ENV_SET)
	chipram_env_set(BOOTLOADER_MODE_LOAD);
#endif
	(*uboot) ();
#endif
}
