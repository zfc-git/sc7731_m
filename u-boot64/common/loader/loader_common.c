#include "loader_common.h"
#include <mmc.h>
#include <fat.h>
#if defined(CONFIG_OF_LIBFDT)
#include <libfdt.h>
#include <fdt_support.h>
#endif

#ifdef CONFIG_MINI_TRUSTZONE
#include "trustzone_def.h"
#endif

#include "sprd_cpcmdline.h"

unsigned char raw_header[8192];

unsigned int g_charger_mode = 0;
char serial_number_to_transfer[SP15_MAX_SN_LEN];

extern int charger_connected(void);
extern void modem_entry(void);
extern void fixup_pmic_items(void);

unsigned short calc_checksum(unsigned char *dat, unsigned long len)
{
	unsigned short num = 0;
	unsigned long chkSum = 0;
	while (len > 1) {
		num = (unsigned short)(*dat);
		dat++;
		num |= (((unsigned short)(*dat)) << 8);
		dat++;
		chkSum += (unsigned long)num;
		len -= 2;
	}
	if (len) {
		chkSum += *dat;
	}
	chkSum = (chkSum >> 16) + (chkSum & 0xffff);
	chkSum += (chkSum >> 16);
	return (~chkSum);
}

unsigned char _chkNVEcc(uint8_t * buf, uint32_t size, uint32_t checksum)
{
	uint16_t crc;

	crc = calc_checksum(buf, size);
	debugf("_chkNVEcc calcout 0x%lx, org 0x%llx\n", crc, checksum);
	return (crc == (uint16_t) checksum);
}

/*modif to support the sp15 64 bit sn NO */
char *get_product_sn(void)
{
	SP09_PHASE_CHECK_T phase_check_sp09;
	SP15_PHASE_CHECK_T phase_check_sp15;

	uint32_t magic;
	memset(serial_number_to_transfer, 0x0, SP15_MAX_SN_LEN);

	strcpy(serial_number_to_transfer, "0123456789ABCDEF");
	if (do_raw_data_read(PRODUCTINFO_FILE_PATITION, sizeof(magic), 0, (char *)&magic)) {
		debugf("read miscdata error.\n");
		return serial_number_to_transfer;
	}
	if (magic == SP09_SPPH_MAGIC_NUMBER){
		if (do_raw_data_read(PRODUCTINFO_FILE_PATITION, sizeof(phase_check_sp09), 0, (char *)&phase_check_sp09)) {
			debugf("sp09 read miscdata error.\n");
			return serial_number_to_transfer;
		}
		if (strlen(phase_check_sp09.SN1)) {
			memcpy(serial_number_to_transfer, phase_check_sp09.SN1, SP09_MAX_SN_LEN);
		}
	} else if (magic == SP15_SPPH_MAGIC_NUMBER ){
		if (do_raw_data_read(PRODUCTINFO_FILE_PATITION, sizeof(phase_check_sp15), 0, (char *)&phase_check_sp15)) {
			debugf("sp15 read miscdata error.\n");
			return serial_number_to_transfer;
		}
		if (strlen(phase_check_sp15.SN1)) {
			memcpy(serial_number_to_transfer, phase_check_sp15.SN1, SP15_MAX_SN_LEN);
		}
	}
	return serial_number_to_transfer;
}

void fdt_fixup_all(void)
{
	u8 *fdt_blob = (u8 *) DT_ADR;
	uint32_t fdt_size;
	boot_img_hdr *hdr = raw_header;
	int err;

	if (fdt_check_header(fdt_blob) != 0) {
		printf("image is not a fdt\n");
	}
	fdt_size = fdt_totalsize(fdt_blob);

	err = fdt_open_into(fdt_blob, fdt_blob, fdt_size + FDT_ADD_SIZE);
	if (err != 0) {
		printf("libfdt fdt_open_into(): %s\n", fdt_strerror(err));
	}

	fdt_initrd_norsvmem(fdt_blob, RAMDISK_ADR, RAMDISK_ADR + hdr->ramdisk_size, 1);
#ifdef CONFIG_SPLASH_SCREEN
	fdt_fixup_lcdid(fdt_blob);
	fdt_fixup_lcdbase(fdt_blob);
#endif
	fdt_fixup_adc_calibration_data(fdt_blob);
	fdt_fixup_dram_training(fdt_blob);
	fdt_fixup_ddr_size(fdt_blob);
	fdt_fixup_sysdump_magic(fdt_blob);
#ifdef CONFIG_SECURE_BOOT
	fdt_fixup_secureboot_param(fdt_blob);
#endif
#ifdef CONFIG_NAND_BOOT
	fdt_fixup_mtd(fdt_blob);
#endif

	/*max let cp_cmdline_fixup befor fdt_fixup_cp_boot*/
	cp_cmdline_fixup();

#if defined( CONFIG_KERNEL_BOOT_CP )
	fdt_fixup_cp_boot(fdt_blob);
#else
	fdt_fixup_calibration_parameter(fdt_blob);
	fdt_fixup_boot_mode(fdt_blob);
	fdt_fixup_rf_hardware_info(fdt_blob);
#endif

	fdt_fixup_serialno(fdt_blob);

	fdt_fixup_chosen_bootargs_board_private(fdt_blob);

#ifdef CONFIG_MEM_LAYOUT_DECOUPLING
extern int fdt_fixup_cp_coupling_info(void *blob);
	fdt_fixup_cp_coupling_info(fdt_blob);
#endif

#ifdef CONFIG_SANSA_SECBOOT
	//fdt_fixup_socid(fdt_blob);
#endif

	return;
}

static int start_linux()
{
#ifdef CONFIG_OF_LIBFDT
	void (*theKernel) (int zero, int arch, u32 params);
	theKernel = (void (*)(void *, int, int, int))KERNEL_ADR;

	fdt_fixup_all();

	/*start modem CP */
	modem_entry();

	/*disable all caches */
	cleanup_before_linux();
#ifdef CONFIG_MINI_TRUSTZONE
	trustzone_entry(TRUSTZONE_ADR + 0x200);
#endif
	/* jump to kernel with register set */
	theKernel(0, machine_arch_type, (u32)DT_ADR);
#else
	void (*theKernel) (int zero, int arch, u32 params);
	u32 machine_type = 0;

	machine_type = machine_arch_type;	/* get machine type */
	theKernel = (void (*)(int, int, u32))KERNEL_ADR;	/* set the kernel address */

	/*start modem CP */
	cp_cmdline_fixup();
	modem_entry();

	/* jump to kernel with register set */
	theKernel(0, machine_type, VLX_TAG_ADDR);
#endif /*CONFIG_OF_LIBFDT */
	while (1) ;
	return 0;
}

static int start_linux_armv8()
{
	void (*theKernel) (void *dtb_addr, int zero, int arch, int reserved);
	theKernel = (void (*)(void *, int, int, int))KERNEL_ADR;

	fdt_fixup_all();

	/*start modem CP */
	modem_entry();

	/*before switch to el2,flush all cache */
	/*FIXME: cleanup_before_linux() will cause panic here, we need to find the solution*/
	flush_dcache_range(CONFIG_SYS_SDRAM_BASE, CONFIG_SYS_SDRAM_END);
#ifdef CONFIG_DUAL_DDR
		flush_dcache_range(CONFIG_SYS_SDRAM_BASE2, CONFIG_SYS_SDRAM_END2);
#endif

#ifdef CONFIG_MINI_TRUSTZONE
	trustzone_entry(TRUSTZONE_ADR + 0x200);
#endif

	/*kernel must run in el2, so here switch to el2 */
	armv8_switch_to_el2();
	theKernel(DT_ADR, 0, 0, 0);

	/*never enter here*/
	while (1) ;
	return 0;
}


void vlx_entry(void)
{
#if 0
	/*down the device if charger disconnect during calibration detect. */
	if (g_charger_mode && !charger_connected()) {
		g_charger_mode = 0;
		power_down_devices(0);
		while (1) ;
	}

	MMU_DisableIDCM();
#endif

#ifdef DFS_ON_ARM7
	cp_dfs_param_for_arm7();
#endif
	/*shutdown usb ldo, can't shutdown it in the ldo_sleep.c because download mode must use usb */
	fixup_pmic_items();

#ifdef CONFIG_ARM64
	smp_kick_all_cpus();
	start_linux_armv8();
#else
	start_linux();
#endif

}
