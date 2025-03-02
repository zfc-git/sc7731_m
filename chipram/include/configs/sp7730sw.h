

#ifndef __CONFIG_H
#define __CONFIG_H

//#define DEBUG

/*#define SPRD_EVM_TAG_ON 1*/
#ifdef SPRD_EVM_TAG_ON
#define SPRD_EVM_ADDR_START 0x00026000
#define SPRD_EVM_TAG(_x) (*(((unsigned long *)SPRD_EVM_ADDR_START)+_x) = *(volatile unsigned long *)0x87003004)
#endif


/*no sec bin;use sec_header*/
#define CONFIG_DUAL_SPL
#ifdef CONFIG_DUAL_SPL
#define HASH_TEMP_LEN 32
#define UBOOT_HASH_SIZE        0x200
#define UBOOT_HASH_SIZE_OFFSET 0x08
#endif
/*
 * SPREADTRUM BIGPHONE board - SoC Configuration
 */
//#define CONFIG_FPGA
#define CONFIG_SP8830
#define CONFIG_SC8830
#define CONFIG_SPX30G   //tshark chip
#define CONFIG_CHIP_ENV_SET
#define CHIPRAM_ENV_ADDR       0x5000b000

#define CONFIG_SPX30G2	//tshark2 chip
#define CONFIG_SPX30G3	//tshark3 chip
#define CONFIG_ADIE_SC2723S


#define CONFIG_AUTODLOADER

#define CHIP_ENDIAN_LITTLE
#define _LITTLE_ENDIAN 1

#define CONFIG_EMMC_BOOT
#define CONFIG_LOAD_PARTITION
#define CONFIG_SD_BOOT
#define SDCARD_BOOT_SECTOR  128

//#define CONFIG_SECURE_BOOT

#ifdef  CONFIG_EMMC_BOOT
#define EMMC_SECTOR_SIZE 512


#define CONFIG_EFI_PARTITION
#define CONFIG_SYS_EMMC_U_BOOT_SECTOR_NUM ((CONFIG_SYS_NAND_U_BOOT_SIZE+EMMC_SECTOR_SIZE-1)/EMMC_SECTOR_SIZE)

#endif

#ifdef CONFIG_SECURE_BOOT
#define CONFIG_SPL_LOAD_LEN    (0x8000)
#else
#define CONFIG_SPL_LOAD_LEN     (0x6000)
#endif





//#define	CONFIG_SYS_MONITOR_LEN		(256 << 10)	/* 256 kB for U-Boot */

/* NAND BOOT is the only boot method */
#define CONFIG_NAND_U_BOOT

/* Start copying real U-boot from the second page */
#define CONFIG_SYS_NAND_U_BOOT_OFFS	0x40000
#define CONFIG_SYS_NAND_U_BOOT_SIZE	0xa0000

//#define FPGA_TRACE_DOWNLOAD //for download image from trace

/* Load U-Boot to this address */
#define CONFIG_SYS_NAND_U_BOOT_DST	0x9f000000	//u-boot64: 0x9f000000, old: 0x8f700000
#define CONFIG_SYS_NAND_U_BOOT_START	CONFIG_SYS_NAND_U_BOOT_DST
#define CONFIG_SYS_SDRAM_BASE 0x80000000




/*
 * Memory Info
 */
/* malloc() len */
#define CONFIG_SYS_MALLOC_LEN		(2 << 20)	/* 1 MiB */
/*
 * Board has 2 32MB banks of DRAM but there is a bug when using
 * both so only the first is configured
 */
#define CONFIG_NR_DRAM_BANKS	1



/*
 * Serial Info
 */

#define CONFIG_SYS_SC8800X_UART1	1

/*
 * Flash & Environment
 */
/* No NOR flash present */
#define CONFIG_SYS_MONITOR_LEN ((CONFIG_SYS_NAND_U_BOOT_OFFS)+(CONFIG_SYS_NAND_U_BOOT_SIZE))
#define CONFIG_SYS_NO_FLASH	1
#define CONFIG_ENV_SIZE		(128 * 1024)
/*
#define	CONFIG_ENV_OFFSET	CONFIG_SYS_MONITOR_LEN
#define CONFIG_ENV_OFFSET_REDUND	(CONFIG_ENV_OFFSET + CONFIG_ENV_SIZE)
*/

/* DDR */
#define CONFIG_CLK_PARA

#ifndef CONFIG_CLK_PARA
#define DDR_CLK 464
#else
#define MAGIC_HEADER	0x5555AAAA
#define MAGIC_END	0xAAAA5555
#define CONFIG_PARA_VERSION 1
#define CLK_CA7_CORE    ARM_CLK_1300M
#define CONFIG_AP_AXI_DIV
#if defined(CONFIG_AP_AXI_DIV)
#define AP_AXI_DIV	2
#else
#define CLK_CA7_AXI     ARM_CLK_400M
#endif
#define CLK_CA7_DGB     ARM_CLK_200M
#define CLK_CA7_AHB     AHB_CLK_192M
#define CLK_CA7_APB     APB_CLK_64M
#define CLK_PUB_AHB     PUB_AHB_CLK_153_6M
#define CLK_AON_APB     AON_APB_CLK_128M
#define DDR_FREQ        533000000
#define DCDC_ARM	1000
#define DCDC_CORE	900
#define DCDC_GEN       2400
#define CONFIG_VOL_PARA
#endif
#define TDPLL_SELECT_SWITCH
#define WPLL_SELECT_SWITCH

//---these four macro below,only one can be open
//#define DDR_LPDDR1
//#define DDR_LPDDR2
//#define DDR_DDR3
#define DDR_LPDDR3

#define DDR_PINMAP_TYPE_4

#define DDR_DEVICE_TYPE_TYPI

#define DDR_AUTO_DETECT
//#define DDR_TYPE DRAM_LPDDR2_1CS_4G_X32
//#define DDR_TYPE DRAM_LPDDR2_1CS_8G_X32
//#define DDR_TYPE DRAM_LPDDR2_2CS_8G_X32
//#define DDR_TYPE DRAM_LPDDR2_2CS_16G_X32
//#define DDR_TYPE DRAM_LPDDR3_2CS_8G_X32
//#define DDR_TYPE DRAM_DDR3_1CS_2G_X8_4P
//#define DDR_TYPE DRAM_DDR3_1CS_4G_X16_2P

#define DDR3_DLL_ON TRUE
//#define DLL_BYPASS
#define DDR_APB_CLK 128
#define DDR_DFS_SUPPORT
#define DDR_DFS_VAL_BASE 0X1c00

//#define DDR_SCAN_SUPPORT
#define MEM_IO_DS LPDDR2_DS_40R

#define PUBL_LPDDR1_DS PUBL_LPDDR1_DS_48OHM
#define PUBL_LPDDR2_DS PUBL_LPDDR2_DS_40OHM
#define PUBL_DDR3_DS   PUBL_DDR3_DS_34OHM


/* NAND */

//#define CONFIG_SYS_NAND_5_ADDR_CYCLE


#define CONFIG_MTD_PARTITIONS

/* U-Boot general configuration */
#define CONFIG_SYS_PROMPT	"=> "	/* Monitor Command Prompt */
#define CONFIG_SYS_CBSIZE	1024	/* Console I/O Buffer Size  */
/* Print buffer sz */
#define CONFIG_SYS_PBSIZE	(CONFIG_SYS_CBSIZE + \
		sizeof(CONFIG_SYS_PROMPT) + 16)




/* U-Boot commands */
#include <config_cmd_default.h>

#undef CONFIG_CMD_FPGA
#undef CONFIG_CMD_LOADS
#undef CONFIG_CMD_NET
#undef CONFIG_CMD_SETGETDCR



#define xstr(s)	str(s)
#define str(s)	#s



#endif /* __CONFIG_H */
