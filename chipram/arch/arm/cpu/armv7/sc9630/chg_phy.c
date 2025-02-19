#include <common.h>
#include <asm/io.h>

#include <asm/arch/regs_adi.h>
#include <asm/arch/adi_hal_internal.h>
#include <asm/arch/sprd_reg.h>

#ifdef DEBUG
#define debugf(fmt, args...) do { printf("%s(): ", __func__); printf(fmt, ##args); } while (0)
#else
#define debugf(fmt, args...)
#endif
#define ADC_CAL_TYPE_NO			0
#define ADC_CAL_TYPE_NV			1
#define ADC_CAL_TYPE_EFUSE		2

extern int read_adc_calibration_data(char *buffer,int size);
extern int sci_efuse_calibration_get(unsigned int * p_cal_data);
uint16_t adc_voltage_table[2][2] = {
	{3310, 4200},
	{2832, 3600},
};

uint32_t adc_cal_flag = 0;

uint16_t CHGMNG_AdcvalueToVoltage(uint16_t adcvalue)
{
	int32_t temp;
	temp = adc_voltage_table[0][1] - adc_voltage_table[1][1];
	temp = temp * (adcvalue - adc_voltage_table[0][0]);
	temp = temp / (adc_voltage_table[0][0] - adc_voltage_table[1][0]);

	debugf("uboot battery voltage:%d,adc4200:%d,adc3600:%d\n",
	       temp + adc_voltage_table[0][1], adc_voltage_table[0][0],
	       adc_voltage_table[1][0]);

	return temp + adc_voltage_table[0][1];
}

void CHG_TurnOn(void)
{
#if defined(CONFIG_SPX15)||defined(CONFIG_ARCH_SCX35L) || defined(CONFIG_SPX35L64)
	ANA_REG_MSK_OR(ANA_REG_GLB_CHGR_CTRL0, 0, BIT_CHGR_PD);
#else
	ANA_REG_MSK_OR(ANA_REG_GLB_CHGR_CTRL0, BIT_CHGR_PD_RTCCLR, (BIT_CHGR_PD_RTCCLR | BIT_CHGR_PD_RTCSET));
#endif
}

void CHG_ShutDown(void)
{
#if defined(CONFIG_SPX15)||defined(CONFIG_ARCH_SCX35L) || defined(CONFIG_SPX35L64)
	ANA_REG_MSK_OR(ANA_REG_GLB_CHGR_CTRL0, BIT_CHGR_PD, BIT_CHGR_PD);
#else
	ANA_REG_MSK_OR(ANA_REG_GLB_CHGR_CTRL0, BIT_CHGR_PD_RTCSET, (BIT_CHGR_PD_RTCCLR | BIT_CHGR_PD_RTCSET));
#endif
}

void CHG_SetRecharge(void)
{
	ANA_REG_OR(ANA_REG_GLB_CHGR_CTRL2, BIT_RECHG);
}

uint32_t CHG_GetAdcCalType(void)
{
	return adc_cal_flag;
}

#ifndef CONFIG_FDL1
/* used to get adc calibration data from nv or efuse */
void get_adc_cali_data(void)
{
	unsigned int adc_data[64];
	int ret=0;

	adc_cal_flag = ADC_CAL_TYPE_NO;

#ifndef FDL_CHG_SP8830
	/* get voltage values from nv */
	ret = read_adc_calibration_data(adc_data,48);
	if((ret > 0) &&
			((adc_data[2] & 0xFFFF) < 4500 ) && ((adc_data[2] & 0xFFFF) > 3000) &&
			((adc_data[3] & 0xFFFF) < 4500 ) && ((adc_data[3] & 0xFFFF) > 3000)){
		debugf("adc_para from nv is 0x%x 0x%x \n",adc_data[2],adc_data[3]);
		adc_voltage_table[0][1]=adc_data[2] & 0xFFFF;
		adc_voltage_table[0][0]=(adc_data[2] >> 16) & 0xFFFF;
		adc_voltage_table[1][1]=adc_data[3] & 0xFFFF;
		adc_voltage_table[1][0]=(adc_data[3] >> 16) & 0xFFFF;
		adc_cal_flag = ADC_CAL_TYPE_NV;
	}
#endif
	/* get voltage values from efuse */
	if (adc_cal_flag == ADC_CAL_TYPE_NO){
		ret = sci_efuse_calibration_get(adc_data);
		if (ret > 0) {
			debugf("adc_para from efuse is 0x%x 0x%x \n",adc_data[0],adc_data[1]);
			adc_voltage_table[0][1]=adc_data[0] & 0xFFFF;
			adc_voltage_table[0][0]=(adc_data[0]>>16) & 0xFFFF;
			adc_voltage_table[1][1]=adc_data[1] & 0xFFFF;
			adc_voltage_table[1][0]=(adc_data[1] >> 16) & 0xFFFF;
			adc_cal_flag = ADC_CAL_TYPE_EFUSE;
		}
	}
}
#endif

