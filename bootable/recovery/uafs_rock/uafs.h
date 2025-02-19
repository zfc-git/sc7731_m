#ifndef _UAFS_H_
#define _UAFS_H_

/* common const string and defines */
#define UAFS_VERSION	"3.1.4"

#define MTK_IMAGE_UPDATE 0
#define BRCM_BP_IMAGE_UPDATE 0
#define QCOM_IMAGE_UPDATE 0
#define SPRD_IMAGE_UPDATE 1
#define RK_IMAGE_UPDATE 0
#define INET_IMAGE_UPDATE 0
#define INTEL_OSIP_IMAGE_UPDATE 0
#define SCRIPTER_SUPPORT 1

#define GMT_DEFAULT_UA_LOCATION 	"/system/bin/uafs"
#define GMT_DEFAULT_UA_LOCATION_B 	"/system/bin/uafs.backup"

#define TEMPORARY_LOG_FILE 	"/cache/uafs.log"
#define LOG_FILE 		"/cache/rock/uafs.log"
#define LAST_LOG_FILE		"/cache/rock/uafs.last_log"

#define DELTA_FILE 		"/cache/rock/update"
#define RESULT_FILE 		"/cache/rock/result"

#define DELTA_FILE_SDCARD 		"/sdcard/rock/update"


#include <selinux/selinux.h>
#include <selinux/label.h>

/* image update */


struct partition {
	const char* path;
	const char* img;
};

#if INTEL_OSIP_IMAGE_UPDATE

static struct partition intel_osip_img_partitions[] = {
	{"boot", "boot.img",}, 
	{"recovery", "recovery.img",}, 
	{"fastboot", "fastboot.img",}, 
	{"fastboot", "droidboot.img",}, 
	{"uefi", "uefi.img",}, 
	{NULL,NULL},
};
#endif


#if MTK_IMAGE_UPDATE
static struct partition mtk_partitions[] = {
	{"/boot", "boot.img",}, 
	{"/recovery", "recovery.img",}, 
	{"/uboot", "lk.bin",}, 
	{"/logo", "logo.bin",},  
	{NULL,NULL},
};
#endif

#if BRCM_BP_IMAGE_UPDATE

static struct partition bcm_bp_partitions[] = {
	{"/boot", "boot.img",}, 
	{"/recovery", "recovery.img",}, 
	{"/cp-boot", "cp_boot.img",}, 
	{"/cp-image", "COMMS.img",}, 
	{"/dsp-dram", "Dsp_EXT.img",}, 
	{"/dsp-pram", "Dsp_PTCM.img",}, 
	{"/dt-blob", "dt-blob",}, 
	{"/kernel-dtb", "java_c_lc2.dtb",}, 
	{"/parm-spml-ind", "sysparm2_ind_blob.img",}, 
	{"/sys-parm-ind", "sysparm_ind.img",}, 
	{"/u-boot", "u-boot.bin",}, 
	{NULL,NULL},
};
#endif

#if RK_IMAGE_UPDATE
static struct partition rk_partitions[] = {
	{"/uboot", "uboot.img",}, 
	{"/boot", "boot.img",}, 
	{"/recovery", "recovery.img",}, 
	{NULL,NULL},
};
#endif

#if QCOM_IMAGE_UPDATE
static struct partition qcom_partitions[] = {
	{"/boot", "boot.img",}, 
	{"/recovery", "recovery.img",}, 
	{"/splash", "splash.img",}, 	
	{NULL,NULL},
};
#endif


#if INET_IMAGE_UPDATE
static struct partition inet_partitions[] = {
	{"/boot", "boot.img",}, 
	{"/recovery", "recovery.img",}, 
	{"/bootloader", "bootloader.fex",}, 	
	{"/env", "env.fex",}, 	
	{NULL,NULL},
};
#endif

#if SPRD_IMAGE_UPDATE
static struct partition sprd_partitions[] = {
	{"/boot", "boot.img",}, 
	{"/recovery", "recovery.img",}, 
	{"/wfixnv1", "nvitem.bin",},
	{"/wcnfixnv1", "nvitem_wcn.bin",},
	{"/wmodem", "SC7702_sc7731g_band128.bin",},
	{"/wdsp", "DSP_DM_G2.bin",},
	{"/wcnmodem", "SC8800G_x30g_wcn_dts_modem.bin",}, 	
	{"/uboot", "u-boot.bin",}, 
	//L9061A
	{"/l_fixnv1", "nvitem.bin",},
	{"/l_modem", "SC9600_sharkl_5modcsfb.bin",},
	{"/l_ldsp", "LTE_DSP.bin",},
	{"/l_gdsp", "SHARKL_DM_DSP.bin",},
	{"/l_warm", "SC9600_sharkl_wphy_5mod.bin",},
	{"/wcnmodem", "EXEC_KERNEL_IMAGE0.bin",},
	{"/wcnfdl", "fdl1_wcn.bin",},
	{"/pm_sys", "PM_sharkl_arm7.bin",},
    //
	{"/wcnfdl", "fdl1_wcn.bin",},
	{"/tdmodem", "tdmodem.bin",},
	{"/tddsp", "tddsp.bin",},
	{"/tdfixnv1", "tdfixnv1.bin",},
	{"/tdfixnv2", "tdfixnv2.bin",},
	{"/tdruntimenv1", "tdruntimenv1.bin",},
	{"/tdruntimenv2", "tdruntimenv2.bin",},
	{"/ltemodem", "ltemodem.bin",},
	{"/ltedsp", "ltedsp.bin",},
	{"/ltefixnv1", "ltefixnv1.bin",},
	{"/ltefixnv2", "ltefixnv2.bin",},
	{"/lteruntimenv1", "lteruntimenv1.bin",},
	{"/lteruntimenv2", "lteruntimenv2.bin",},
	{"/lfmodem", "lfmodem.bin",},
	{"/lfwarm", "lfwarm.bin",},
	{"/lfgdsp", "lfgdsp.bin",},
	{"/lfldsp", "lfldsp.bin",},
	{"/lffixnv1", "lffixnv1.bin",},
	{"/lffixnv2", "lffixnv2.bin",},
	{"/lfruntimenv1", "lfruntimenv1.bin",},
	{"/lfruntimenv2", "lfruntimenv2.bin",},
	{"/tlmodem", "tlmodem.bin",},
	{"/tltdsp", "tltdsp.bin",},
	{"/tlldsp", "tlldsp.bin",},
	{"/tlfixnv1", "tlfixnv1.bin",},
	{"/tlfixnv2", "tlfixnv2.bin",},
	{"/tlruntimenv1", "tlruntimenv1.bin",},
	{"/tlruntimenv2", "tlruntimenv2.bin",},
	{"/wfixnv2", "wfixnv2.bin",},
	{"/wruntimenv1", "wruntimenv1.bin",},
	{"/wruntimenv2", "wruntimenv2.bin",},
	{"/wcnfixnv2", "wcnfixnv2.bin",},
	{"/wcnruntimenv1", "wcnruntimenv1.bin",},
	{"/wcnruntimenv2", "wcnruntimenv2.bin",},
	{"/logo", "logo.bin",},
	{"/fbootlogo", "fbootlogo.bin",},
	{"/spl", "spl.bin",},


	{NULL,NULL},
};
#endif

extern struct selabel_handle *sehandle;


// for android 4.1.x
#include "../ui.h"
extern RecoveryUI* ui;
void ui_init();
void ui_show_progress(float portion, float seconds);
void ui_set_progress(float portion);


#endif
