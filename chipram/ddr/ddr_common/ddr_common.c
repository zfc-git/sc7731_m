#include "ddr_common.h"


uint32 __raw_readl(uint32 addr)
{
     volatile uint32 val;
	 
     //asm volatile("ldr %w0, [%1]" : "=r" (val) : "r" (addr));
     val = REG32(addr);	 
     return val;
}
 
void __raw_writel(uint32 addr,uint32 val)
{
     //asm volatile("str %w0, [%1]" : : "r" (val), "r" (addr));
     REG32(addr) = val;
}

void reg_bit_set(uint32 addr,uint32 start_bit,uint32 bits_num,uint32 val)
{
	uint32 tmp_val,bit_msk = ((1 << bits_num) - 1);
	tmp_val = __raw_readl(addr);
	tmp_val &= ~(bit_msk << start_bit);
	tmp_val |= ( (val & bit_msk) << start_bit );
	__raw_writel(addr,tmp_val);
}

void wait_us(uint32 us)
{
	volatile uint32 i = 0;
	volatile uint32 j = 0;
	volatile uint32 reg = 0;

	for(i = 0; i < us; i++)
	{
		for(j = 0; j < 153; j++)
		{
			reg = __raw_readl(DDR_DUMMY_REG);
			reg = reg;
		}
	}
}

void wait_10us(uint32 us_10)
{
	volatile uint32 i = 0;

	for(i = 0; i < us_10; i++)
	{
	    wait_us(10);
	}
}

void __ddr_print(char * log)
{
	;    
}

void __ddr_wdg_reset(void)
{
	;
}

