#include <config.h>
#include "asm/arch/sci_types.h"

#if defined (CONFIG_SC8830) || defined(CONFIG_SC9630) || defined(CONFIG_SCX35L64)
#include "asm/arch/sprd_reg_global.h"
#include "asm/arch/sprd_reg_ahb.h"
#include "asm/arch/sprd_module_config.h"
#include "asm/arch/regs_ahb.h"
#include "asm/arch/__regs_ahb.h"
#include "asm/arch/chip_drv_common_io.h"
#include  "key_pad_reg.h"
#else
#include "asm/arch/sc8810_reg_global.h"
#include "asm/arch/sc8810_reg_ahb.h"
#include "asm/arch/sc8810_module_config.h"
#endif

/******************judge the boot mode**************/
#ifdef CONFIG_SD_BOOT

typedef enum KEY_IN{
	KEY_IN0 = 0x00,
	KEY_IN1 = 0x01,
	KEY_IN_MAX
}KEY_INT_E;

typedef enum KEY_OUT{
	KEY_OUT0 = 0x00,
	KEY_OUT1 = 0x01,
	KEY_OUT_MAX
}KEY_OUT_E;

typedef enum KEYPAD_MIN_MRX{
	KEYIN0_OUT0 = 0x00,
	KEYIN1_OUT0 = 0x01,
	KEYIN0_OUT1 = 0x10,
	KEYIN1_OUT1 = 0x11,
	KEY_MAX
}KEYPAD_2X2_MRE_E;

/******************judge the boot mode**************/
static void PinConfig()
{

uint32 reg = 0;
REG_OR(AON_GEN0,BIT_20);
SDIO_Mdelay(10);

for(reg=PIN_KEYIN0_REG;reg<=PIN_KEYIN1_REG;reg +=4)
{
REG_AND(reg,~(BIT_6));
REG_OR(reg,BIT_7|BIT_12);

}
REG_AND(PIN_KEYOUT0_REG,~(BIT_6));
REG_AND(PIN_KEYOUT1_REG,~(BIT_6));
}

static void KeypadConfig()
{
	unsigned int i = 0;
	REG_OR(AON_GEN0,BIT_8);
	REG_OR(AON_RTC_EB,BIT_1);
	REG_OR(AON_RST0,BIT_8);
	SDIO_Mdelay(10);
	REG_AND(AON_RST0,~BIT_8);
	REG_WRITE(KPD_INT_CLR,0xfff);
	REG_WRITE(KPD_POLARITY,0xffff);
	REG_WRITE(KPD_CLK_DIVIDE_CNT,0x0);
	REG_WRITE(KPD_DEBOUNCE_CNT,0xf);
	REG_AND(KPD_CTRL,~(0xffff<<8));
	i = BIT_8|BIT_9|BIT_16;
	REG_OR(KPD_CTRL,i);
	i |=BIT_0;
	REG_OR(KPD_CTRL,i);

}
static void KeypadDisable(void)
{
	REG_WRITE(KPD_INT_CLR,0xfff);
	REG_AND(AON_GEN0,~BIT_8);
	REG_AND(AON_RTC_EB,~BIT_1);
	return;
}
PUBLIC BOOLEAN isSDBoot_for_KpdDisable(void)
{
	uint32 i;
	BOOLEAN key_press[KEY_OUT_MAX][KEY_IN_MAX]={0};
	uint32 keySts = 0;
	uint32 keyRawIntSts = 0;
	PinConfig();
	KeypadConfig();
	SDIO_Mdelay(20);
	keySts = *(volatile uint32 *)(KPD_STATUS);
	keyRawIntSts=*(volatile uint32 *)(KPD_INT_RAW_STATUS);
	KeypadDisable();
	if(keyRawIntSts){
		for(i=0;i<4;i++){
			if(keyRawIntSts&(1<<i)){
				uint32 p_key=(keySts>>(i<<3))&0x77;
				switch(p_key){
					case KEYIN0_OUT0:
						key_press[KEY_OUT0][KEY_IN0]=TRUE;
					case KEYIN1_OUT0:
						key_press[KEY_OUT0][KEY_IN1]=TRUE;
					default: break;
				}

			}

		}
		if((key_press[KEY_OUT0][KEY_IN0]==TRUE)&&(key_press[KEY_OUT0][KEY_IN1]==TRUE)){
			return TRUE;
		}

	}

	return FALSE;
}

#endif

#ifndef CONFIG_NO_SD_BOOT
PUBLIC BOOLEAN isSDCardBoot(void)
{
	uint32 s_keypad_raw_status = 0;
	uint32 s_keypad_status = 0;

	SDIO_Mdelay(20);

	s_keypad_raw_status = *(volatile uint32 *)(KPD_INT_RAW_STATUS);
	s_keypad_status = *(volatile uint32 *)(KPD_STATUS);

	if (((s_keypad_raw_status & 0x03) == 0x03) && (((s_keypad_status & 0x7777) == 0x0001) || ((s_keypad_status & 0x7777) == 0x0100))) {
		return TRUE;
	} else {
		return FALSE;
	}
}
#endif
