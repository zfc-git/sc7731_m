typedef unsigned int				uint32;
typedef unsigned long int 			uint64;

//#define REG32(_x_)	(*(volatile uint32*)(_x_))
#define REG32(_x_)	(*(volatile uint32*)((uint64)(_x_)))

#define TRUE		1
#define FALSE		0

#define NULL        0

#define CLK_24MHZ    24
#define CLK_26MHZ    26
#define CLK_38_4MHZ  38
#define CLK_48MHZ    48
#define CLK_64MHZ    64
#define CLK_76_8MHZ  77
#define CLK_96MHZ    96
#define CLK_100MHZ   100    
#define CLK_153_6MHZ 154
#define CLK_150MHZ   150    
#define CLK_166MHZ   166
#define CLK_192MHZ   192
#define CLK_200MHZ   200  
#define CLK_250MHZ   250  
#define CLK_333MHZ   333
#define CLK_400MHZ   400
#define CLK_427MHZ   427
#define CLK_450MHZ   450
#define CLK_500MHZ   500
#define CLK_525MHZ   525
#define CLK_533MHZ   533    
#define CLK_537MHZ   537
#define CLK_540MHZ   540
#define CLK_550MHZ   550
#define CLK_640MHZ   640
#define CLK_667MHZ   667
#define CLK_800MHZ	 800
#define CLK_1000MHZ	 1000
#define EMC_CLK_400MHZ 400
#define EMC_CLK_450MHZ 450
#define EMC_CLK_500MHZ 500

#define DDR_DUMMY_REG 0x30000004


void reg_bit_set(uint32 addr,uint32 start_bit,uint32 bits_num,uint32 val);
void wait_us(uint32 us);
void wait_10us(uint32 us_10);
uint32 __raw_readl(uint32 addr);
void __raw_writel(uint32 addr,uint32 val);
void __ddr_print(char * log);
void __ddr_wdg_reset();

