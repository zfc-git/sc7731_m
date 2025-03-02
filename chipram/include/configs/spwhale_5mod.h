/*
 * (C) Copyright 2009 DENX Software Engineering
 * Author: John Rigby <jrigby@gmail.com>
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as
 * published by the Free Software Foundation; either version 2 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.	 See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston,
 * MA 02111-1307 USA
 */

#ifndef __CONFIG_H
#define __CONFIG_H


//only used in fdl2 .in uart download, the debug infors  from  serial will break the download process.
//#define NAND_DEBUG  
//#define DEBUG
#define CONFIG_LOAD_PARTITION
#define CONFIG_CHIP_ENV_SET


/*#define SPRD_EVM_TAG_ON 1*/
#ifdef SPRD_EVM_TAG_ON
#define SPRD_EVM_ADDR_START 0x00026000
#define SPRD_EVM_TAG(_x) (*(((unsigned long *)SPRD_EVM_ADDR_START)+_x) = *(volatile unsigned long *)0x87003004)
#endif



/*
 * SPREADTRUM BIGPHONE board - SoC Configuration
 */
//#define CONFIG_FPGA
//#define CONFIG_WHALE_FPGA
#define CONFIG_WHALE
//#define CONFIG_SCX35L64
//
//
//#define CONFIG_SUPPORT_WLTE
#define UBOOT_HASH_SIZE        0x200




#define CHIPRAM_ENV_ADDR	0x82000000
#define CHIP_ENDIAN_LITTLE
#define _LITTLE_ENDIAN 1


#define CONFIG_EMMC_BOOT
//#define CONFIG_ARCH_SCX35L
#define CONFIG_ADIE_SC2731

#ifdef  CONFIG_EMMC_BOOT
#define EMMC_SECTOR_SIZE 512


#define CONFIG_SYS_EMMC_U_BOOT_SECTOR_NUM ((CONFIG_SYS_NAND_U_BOOT_SIZE+EMMC_SECTOR_SIZE-1)/EMMC_SECTOR_SIZE)
#endif

/*
 * MMC definition
 */
#define CONFIG_CMD_MMC
#ifdef  CONFIG_CMD_MMC
#define CONFIG_EFI_PARTITION		1
#endif



//#define CONFIG_SYS_HUSH_PARSER



#define CONFIG_SPL_LOAD_LEN	(0x6000)


/*#define CMDLINE_NEED_CONV */

//#define CONFIG_SYS_TEXT_BASZE  0x80f00000

//#define	CONFIG_SYS_MONITOR_LEN		(256 << 10)	/* 256 kB for U-Boot */

/* NAND BOOT is the only boot method */
#define CONFIG_NAND_U_BOOT
/* Start copying real U-boot from the second page */
#define CONFIG_SYS_NAND_U_BOOT_OFFS	0x40000
#define CONFIG_SYS_NAND_U_BOOT_SIZE	0x8A000
//#define FPGA_TRACE_DOWNLOAD //for download image from trace

/* Load U-Boot to this address */
#define CONFIG_SYS_NAND_U_BOOT_DST	0x9f000000
#define CONFIG_SYS_NAND_U_BOOT_START	CONFIG_SYS_NAND_U_BOOT_DST
#define CONFIG_SYS_SDRAM_BASE 0x80000000

#define CONFIG_SMLBOOT          1
#define SML_LOAD_MAX_SIZE       (32*1024) //NOT be excess 32K,or ovrelap spl.
#define TOS_LOAD_MAX_SIZE       (1024*1024)
#define CONFIG_SML_LDADDR_START (0x2000) //(0x94053000)
#define CONFIG_TOS_LDADDR_START (0x96000000)
#define IMAGE_HEAD_SIZE		(512)


#define CONFIG_HW_WATCHDOG
//#define CONFIG_AUTOBOOT //used for FPGA test, auto boot other image
//#define CONFIG_DISPLAY_CPUINFO


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

/* 8MB DRAM test */

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
#define CONFIG_DUAL_DDR

#ifndef CONFIG_CLK_PARA
#define DDR_CLK 464
#else
#define MAGIC_HEADER	0x5555AAAA
#define MAGIC_END	0xAAAA5555
#define CONFIG_PARA_VERSION 1
#define CLK_CA7_CORE    ARM_CLK_750M
#define CLK_CA7_AXI     ARM_CLK_400M
#define CLK_CA7_DGB     ARM_CLK_200M
#define CLK_CA7_AHB     AHB_CLK_192M
#define CLK_CA7_APB     APB_CLK_64M
#define CLK_PUB_AHB     PUB_AHB_CLK_128M
#define CLK_AON_APB     AON_APB_CLK_96M
#define DDR_FREQ        800000000
#define DCDC_ARM0	900
#define DCDC_ARM1	900
#define DCDC_CORE	900

#define CONFIG_VOL_PARA
#endif
//---these three macro below,only one can be open
//#define DDR_LPDDR1
#define DDR_LPDDR2
//#define DDR_DDR3
#define DDR_PINMAP_TYPE_3
//#define DDR_AUTO_DETECT
#define DDR_TYPE DRAM_LPDDR2_2CS_8G_X32
#define DDR_AUTO_DETECT
//#define DDR_TYPE DRAM_LPDDR2_2CS_8G_X32
//#define DDR_TYPE DRAM_LPDDR2_1CS_4G_X32
//#define DDR_TYPE DRAM_LPDDR2_1CS_8G_X32
//#define DDR_TYPE DRAM_LPDDR2_2CS_16G_X32
//#define DDR_TYPE DRAM_LPDDR3_1CS_4G_X32
//#define DDR_TYPE DRAM_LPDDR3_2CS_8G_X32
//#define DDR_TYPE DRAM_LPDDR3_2CS_6GX2_X32
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
//#define CONFIG_JFFS2_NAND
//#define CONFIG_SPRD_NAND_HWECC
//#define CONFIG_SYS_NAND_5_ADDR_CYCLE


#define CONFIG_MTD_PARTITIONS

/* U-Boot general configuration */
#define CONFIG_SYS_PROMPT	"=> "	/* Monitor Command Prompt */
#define CONFIG_SYS_CBSIZE	1024	/* Console I/O Buffer Size  */
/* Print buffer sz */
#define CONFIG_SYS_PBSIZE	(CONFIG_SYS_CBSIZE + \
		sizeof(CONFIG_SYS_PROMPT) + 16)
/* Boot Argument Buffer Size */

/* support OS choose */
#undef CONFIG_BOOTM_NETBSD 
#undef CONFIG_BOOTM_RTEMS

/* U-Boot commands */
#include <config_cmd_default.h>
#undef CONFIG_CMD_FPGA
#undef CONFIG_CMD_LOADS
#undef CONFIG_CMD_NET
#undef CONFIG_CMD_SETGETDCR




#define xstr(s)	str(s)
#define str(s)	#s





#define CONFIG_USB_DWC3
//#define CONFIG_USB_ETHER


#include <asm/sizes.h>



//#define LOW_BAT_ADC_LEVEL 782 /*phone battery adc value low than this value will not boot up*/


//#define TD_CP_OFFSET_ADDR			0x8000000	/*128*/
//#define TD_CP_SDRAM_SIZE			0x1200000	/*18M*/
//#define WCDMA_CP_OFFSET_ADDR		0x10000000	/*256M*/
//#define WCDMA_CP_SDRAM_SIZE		0x4000000	/*64M*/





/* #define CONFIG_SPRD_AUDIO_DEBUG */


#endif /* __CONFIG_H */
