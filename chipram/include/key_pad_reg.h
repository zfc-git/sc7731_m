#ifndef   __KEY_PAD_REG_H_
#define  __KEY_PAD_REG_H_


#define PINMAP_REG_BASE  (0x402A0000)
#define PIN_REG_ADDR(_x_)   (PINMAP_REG_BASE+_x_)
#define PIN_KEYOUT0_REG  PIN_REG_ADDR(0x0354)
#define PIN_KEYOUT1_REG  PIN_REG_ADDR(0x0358)
#define PIN_KEYIN0_REG       PIN_REG_ADDR(0x0360)
#define PIN_KEYIN1_REG       PIN_REG_ADDR(0x0364)

#define AON_BASE (0x402E0000)
#define AON_GEN0 (AON_BASE+0x00)
#define AON_RST0  (AON_BASE+0x08)
#define AON_RTC_EB (AON_BASE+0x10)



#define KPD_BASE                              0x40250000
#define KPD_CTRL                         	 (KPD_BASE + 0x00)
#define KPD_INT_RAW_STATUS                    (KPD_BASE + 0x08)
#define KPD_INT_CLR                    (KPD_BASE + 0x10)
#define KPD_POLARITY                 (KPD_BASE + 0x18)
#define KPD_DEBOUNCE_CNT                   (KPD_BASE + 0x1C)
#define KPD_CLK_DIVIDE_CNT                    (KPD_BASE + 0x28)
#define KPD_STATUS                         	 (KPD_BASE + 0x2C)


#define  PIN_SD0_D3_REG          PIN_REG_ADDR(0x0184)
#define  PIN_SD0_D2_REG          PIN_REG_ADDR(0x0188)
#define  PIN_SD0_CMD_REG      PIN_REG_ADDR(0x018C)
#define  PIN_SD0_D0_REG          PIN_REG_ADDR(0x0190)
#define  PIN_SD0_D1_REG          PIN_REG_ADDR(0x0194)
#define  PIN_SD0_CLK0_REG     PIN_REG_ADDR(0x019C)
#define  LDO_PDCTL2_REG         0x40038814
#define  REG_CLK_SDIO0_CFG   0x71200048
#define  SD_SLOT_SEL_REG 0x402d300c





#define REG_WRITE(reg_addr,value) \
do{\
	*(volatile uint32 *)(reg_addr) = value;\
}while(0)

#define REG_AND(reg_addr,and_mask) \
do{\
	*(volatile uint32 *)(reg_addr) &= and_mask;\
}while(0)

#define REG_OR(reg_addr,and_mask) \
do{\
	*(volatile uint32 *)(reg_addr) |= and_mask;\
}while(0)


#endif
