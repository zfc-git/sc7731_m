#include <common.h>
#include <asm/io.h>
//#include <asm/arch/ldo.h>   //trunk branch
#include <sprd_regulator.h>   //prime branch
//#include <asm/arch/sprd_reg_ahb.h>
//#include <asm/arch/regs_ahb.h>
#include <asm/arch/common.h>
#include "adi_hal_internal.h"
#include <asm/u-boot.h>
#include <part.h>
#include <sdhci.h>
#include <asm/arch/mfp.h>
//#include <linux/gpio.h>
#include <asm/arch/pinmap.h>
#include "asm/arch/sprd_module_config.h"
#include <boot_mode.h>
DECLARE_GLOBAL_DATA_PTR;

extern void sprd_gpio_init(void);
extern void ADI_init (void);
extern int LDO_Init(void);
extern void ADC_Init(void);
extern int sound_init(void);

phys_size_t real_ram_size = 0x40000000;


//extern struct eic_gpio_resource sprd_gpio_resource[];
extern int sprd_gpio_direction_output(struct gpio_chip *chip, unsigned offset, int value);
extern int sprd_gpio_request(struct gpio_chip *chip, unsigned offset);

#define EXT_VDDUSB_EN_GPIO	152

int set_ext_vddusb_en(int is_enable)
{
	sprd_gpio_request(NULL, EXT_VDDUSB_EN_GPIO);
	if (is_enable) {
		sprd_gpio_direction_output(NULL, EXT_VDDUSB_EN_GPIO, 1);
	} else {
		sprd_gpio_direction_output(NULL, EXT_VDDUSB_EN_GPIO, 0);
	}

	return 0;
}

int board_init()
{
	//gd->bd->bi_arch_number = MACH_TYPE_OPENPHONE;
	/*get env transfer from chipram*/
	setup_chipram_env();
	gd->bd->bi_boot_params = PHYS_SDRAM_1 + 0x100;
	ADI_init();
	misc_init();
	LDO_Init();
	ADC_Init();
	pin_init();
	sprd_eic_init();
	sprd_gpio_init();
//	sound_init();
	init_ldo_sleep_gr();
	//TDPllRefConfig(1);

	// disable external vddusb
	set_ext_vddusb_en(0);

	return 0;
}

phys_size_t get_real_ram_size(void)
{
        return real_ram_size;
}

int dram_init(void)
{
#ifdef CONFIG_DDR_AUTO_DETECT
        ulong sdram_base = CONFIG_SYS_SDRAM_BASE;
        ulong sdram_size = 0;
        int i;

        real_ram_size = 0;
        ulong bank_cnt = CONFIG_NR_DRAM_BANKS_ADDR_IN_IRAM;

        for (i = 1; i <= *(volatile uint32 *)CONFIG_NR_DRAM_BANKS_ADDR_IN_IRAM; i++) {
                real_ram_size += *(volatile ulong *)((volatile ulong *)CONFIG_NR_DRAM_BANKS_ADDR_IN_IRAM + i);
        }

        real_ram_size = get_ram_size((volatile void *)sdram_base, real_ram_size);
#else
	real_ram_size = REAL_SDRAM_SIZE;
#endif
        gd->ram_size = get_ram_size((volatile void *)PHYS_SDRAM_1,
                        PHYS_SDRAM_1_SIZE);

        return 0;
}


CBOOT_FUNC s_boot_func_array[CHECK_BOOTMODE_FUN_NUM] = {

	get_mode_from_bat_low,
	write_sysdump_before_boot_extend,
	/* 1 get mode from file*/
	get_mode_from_file_extend,
	/* 2 get mode from watch dog*/
	get_mode_from_watchdog,
	/*3 get mode from alarm register*/
	get_mode_from_alarm_register,
	/*0 get mode from calibration detect*/
	get_mode_from_pctool,
	/*4 get mode from charger*/
	get_mode_from_charger,
	/*5 get mode from keypad*/
	get_mode_from_keypad,
	/*6 get mode from gpio*/
	get_mode_from_gpio_extend,

	/*shutdown device*/
//	get_mode_from_shutdown,

	0
};



void board_boot_mode_regist(CBOOT_MODE_ENTRY *array)
{
	MODE_REGIST(CMD_NORMAL_MODE, normal_mode);
	MODE_REGIST(CMD_RECOVERY_MODE, recovery_mode);
	MODE_REGIST(CMD_FASTBOOT_MODE, fastboot_mode);
	MODE_REGIST(CMD_WATCHDOG_REBOOT, watchdog_mode);
	MODE_REGIST(CMD_UNKNOW_REBOOT_MODE, unknow_reboot_mode);
	MODE_REGIST(CMD_PANIC_REBOOT, panic_reboot_mode);
	MODE_REGIST(CMD_AUTODLOADER_REBOOT, autodloader_mode);
	MODE_REGIST(CMD_SPECIAL_MODE, special_mode);
	MODE_REGIST(CMD_CHARGE_MODE, charge_mode);
	MODE_REGIST(CMD_ENGTEST_MODE,engtest_mode);
	MODE_REGIST(CMD_CALIBRATION_MODE, calibration_mode);
	MODE_REGIST(CMD_EXT_RSTN_REBOOT_MODE, normal_mode);
	MODE_REGIST(CMD_IQ_REBOOT_MODE, iq_mode);
	MODE_REGIST(CMD_ALARM_MODE, alarm_mode);
	MODE_REGIST(CMD_AUTOTEST_MODE, autotest_mode);

	return ;
}


int board_late_init(void)
{
	boot_pwr_check();

#if !defined(CONFIG_FPGA)
#ifdef CONFIG_NAND_BOOT
	//extern int nand_ubi_dev_init(void);
	nand_ubi_dev_init();
	debugf("nand ubi init OK!\n");
#endif

	extern void sprdbat_init(void);
//	sprdbat_init();
	debugf("CHG init OK!\n");
	if (sprdbat_get_vbatauxadc_caltype() != 0) {
		/*fix me*/
		printf("I'm coming in \"if(CHG_GetAdcCalType != 0)\"\n");
		DCDC_Cal_ArmCore();
	}
#endif
	board_keypad_init();
	return 0;
}


