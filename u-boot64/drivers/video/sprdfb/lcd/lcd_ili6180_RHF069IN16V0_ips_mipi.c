/* drivers/video/sc8825/lcd_ili6180_mipi.c
 *
 * Support for lcd_ili6180 mipi LCD device
 *
 * Copyright (C) 2010 Spreadtrum
 *
 * This software is licensed under the terms of the GNU General Public
 * License version 2, as published by the Free Software Foundation, and
 * may be copied, distributed, and modified under those terms.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */

#include "../sprdfb_chip_common.h"
#include "../sprdfb.h"
#include "../sprdfb_panel.h"


#define printk printf

#define  LCD_DEBUG
#ifdef LCD_DEBUG
#define LCD_PRINT printk
#else
#define LCD_PRINT(...)
#endif


#define MAX_DATA   48

typedef struct LCM_Init_Code_tag {
	unsigned int tag;
	unsigned char data[MAX_DATA];
}LCM_Init_Code;

typedef struct LCM_force_cmd_code_tag{
	unsigned int datatype;
	LCM_Init_Code real_cmd_code;
}LCM_Force_Cmd_Code;


#define LCM_TAG_SHIFT 24
#define LCM_TAG_MASK  ((1 << 24) -1)
#define LCM_SEND(len) ((1 << LCM_TAG_SHIFT)| len)
#define LCM_SLEEP(ms) ((2 << LCM_TAG_SHIFT)| ms)

#define LCM_TAG_SEND  (1<< 0)
#define LCM_TAG_SLEEP (1 << 1)

static LCM_Init_Code init_data[] ={
{LCM_SEND(2),{0xFA,0x13}},
{LCM_SLEEP(10)},
{LCM_SEND(2),{0xBF,0x55}},//A4
{LCM_SLEEP(10)},
{LCM_SEND(2),{0xBF,0xAA}},
{LCM_SLEEP(10)},
{LCM_SEND(2),{0xBF,0x66}},//22
{LCM_SLEEP(10)},
{LCM_SEND(2),{0xBF,0x01}},
{LCM_SLEEP(10)},
{LCM_SEND(2),{0xBC,0x04}},
{LCM_SLEEP(10)},
{LCM_SEND(2),{0xBD,0x93}},
{LCM_SLEEP(10)},
{LCM_SEND(2),{0xBE,0x00}},
{LCM_SLEEP(100)},
{LCM_SEND(2),{0xBD,0x91}},
{LCM_SLEEP(10)},
{LCM_SEND(2),{0xBE,0x41}},
{LCM_SLEEP(10)},
{LCM_SEND(2),{0xBD,0x01}},
{LCM_SLEEP(10)},
{LCM_SEND(2),{0xBE,0x08}},//
{LCM_SLEEP(10)},
{LCM_SEND(2),{0xBD,0x13}},//
{LCM_SLEEP(10)},
{LCM_SEND(2),{0xBE,0x00}},//
{LCM_SLEEP(10)},
{LCM_SEND(2),{0xBD,0x02}},//
{LCM_SLEEP(10)},
{LCM_SEND(2),{0xBE,0x06}},//
{LCM_SLEEP(10)},
{LCM_SEND(2),{0xBD,0x14}},//
{LCM_SLEEP(10)},
{LCM_SEND(2),{0xBE,0x00}},//
{LCM_SLEEP(10)},
{LCM_SEND(2),{0xBD,0x03}},//
{LCM_SLEEP(10)},
{LCM_SEND(2),{0xBE,0x00}},//
{LCM_SLEEP(10)},
{LCM_SEND(2),{0xBD,0x15}},//
{LCM_SLEEP(10)},
{LCM_SEND(2),{0xBE,0x00}},//9C
{LCM_SLEEP(10)},
{LCM_SEND(2),{0xBD,0x04}},//CC
{LCM_SLEEP(10)},
{LCM_SEND(2),{0xBE,0x00}},//
{LCM_SLEEP(10)},
{LCM_SEND(2),{0xBD,0x16}},//2C
{LCM_SLEEP(10)},
{LCM_SEND(2),{0xBE,0x00}},//
{LCM_SLEEP(10)},
{LCM_SEND(2),{0xBD,0x05}},
{LCM_SLEEP(10)},
{LCM_SEND(2),{0xBE,0x00}},
{LCM_SLEEP(10)},
{LCM_SEND(2),{0xBD,0x17}},
{LCM_SLEEP(10)},
{LCM_SEND(2),{0xBE,0x00}},
{LCM_SLEEP(10)},
{LCM_SEND(2),{0xBD,0x06}},
{LCM_SLEEP(10)},
{LCM_SEND(2),{0xBE,0x04}},
{LCM_SLEEP(10)},
{LCM_SEND(2),{0xBD,0x18}},
{LCM_SLEEP(10)},
{LCM_SEND(2),{0xBE,0x00}},//
{LCM_SLEEP(10)},
{LCM_SEND(2),{0xBD,0x07}},//
{LCM_SLEEP(10)},
{LCM_SEND(2),{0xBE,0x08}},//
{LCM_SLEEP(10)},
{LCM_SEND(2),{0xBD,0x19}},//
{LCM_SLEEP(10)},
{LCM_SEND(2),{0xBE,0x04}},//
{LCM_SLEEP(10)},
{LCM_SEND(2),{0xBD,0x08}},//
{LCM_SLEEP(10)},
{LCM_SEND(2),{0xBE,0x0A}},//
{LCM_SLEEP(10)},
{LCM_SEND(2),{0xBD,0x1A}},//
{LCM_SLEEP(10)},
{LCM_SEND(2),{0xBE,0x08}},//
{LCM_SLEEP(10)},
{LCM_SEND(2),{0xBD,0x09}},//
{LCM_SLEEP(10)},
{LCM_SEND(2),{0xBE,0x0E}},//
{LCM_SLEEP(10)},
{LCM_SEND(2),{0xBD,0x1B}},//
{LCM_SLEEP(10)},
{LCM_SEND(2),{0xBE,0x0E}},//
{LCM_SLEEP(10)},
{LCM_SEND(2),{0xBD,0x0A}},//
{LCM_SLEEP(10)},
{LCM_SEND(2),{0xBE,0x0F}},//
{LCM_SLEEP(10)},
{LCM_SEND(2),{0xBD,0x1C}},//
{LCM_SLEEP(10)},
{LCM_SEND(2),{0xBE,0x14}},
{LCM_SLEEP(10)},
{LCM_SEND(2),{0xBD,0x0B}},
{LCM_SLEEP(10)},
{LCM_SEND(2),{0xBE,0x11}},
{LCM_SLEEP(10)},
{LCM_SEND(2),{0xBD,0x1D}},
{LCM_SLEEP(10)},
{LCM_SEND(2),{0xBE,0x18}},
{LCM_SLEEP(10)},
{LCM_SEND(2),{0xBD,0x0C}},
{LCM_SLEEP(10)},
{LCM_SEND(2),{0xBE,0x15}},
{LCM_SLEEP(10)},
{LCM_SEND(2),{0xBD,0x1E}},//
{LCM_SLEEP(10)},
{LCM_SEND(2),{0xBE,0x1E}},//
{LCM_SLEEP(10)},
{LCM_SEND(2),{0xBD,0x0D}},//
{LCM_SLEEP(10)},
{LCM_SEND(2),{0xBE,0x17}},//
{LCM_SLEEP(10)},
{LCM_SEND(2),{0xBD,0x1F}},//
{LCM_SLEEP(10)},
{LCM_SEND(2),{0xBE,0x1F}},//
{LCM_SLEEP(10)},
{LCM_SEND(2),{0xBD,0x0E}},//
{LCM_SLEEP(10)},
{LCM_SEND(2),{0xBE,0x1F}},//
{LCM_SLEEP(10)},
{LCM_SEND(2),{0xBD,0x20}},//
{LCM_SLEEP(10)},
{LCM_SEND(2),{0xBE,0x1F}},//
{LCM_SLEEP(10)},
{LCM_SEND(2),{0xBD,0x0F}},//
{LCM_SLEEP(10)},
{LCM_SEND(2),{0xBE,0x1F}},//
{LCM_SLEEP(10)},
{LCM_SEND(2),{0xBD,0x21}},//
{LCM_SLEEP(10)},
{LCM_SEND(2),{0xBE,0x1F}},//
{LCM_SLEEP(10)},
{LCM_SEND(2),{0xBD,0x10}},//
{LCM_SLEEP(10)},
{LCM_SEND(2),{0xBE,0x1F}},
{LCM_SLEEP(10)},
{LCM_SEND(2),{0xBD,0x22}},
{LCM_SLEEP(10)},
{LCM_SEND(2),{0xBE,0x1F}},//
{LCM_SLEEP(10)},
{LCM_SEND(2),{0xBD,0x11}},
{LCM_SLEEP(10)},
{LCM_SEND(2),{0xBE,0x1F}},
{LCM_SLEEP(10)},
{LCM_SEND(2),{0xBD,0x23}},
{LCM_SLEEP(10)},
{LCM_SEND(2),{0xBE,0x1F}},//02
{LCM_SLEEP(10)},
{LCM_SEND(2),{0xBD,0x12}},
{LCM_SLEEP(10)},
{LCM_SEND(2),{0xBE,0x1A}},//2c
{LCM_SLEEP(10)},
{LCM_SEND(2),{0xBD,0x24}},//5c
{LCM_SLEEP(10)},
{LCM_SEND(2),{0xBE,0x1A}},
{LCM_SLEEP(10)},
};


static LCM_Init_Code sleep_in[] =  {

    {LCM_SEND(2), {0x28,0x00}},
    {LCM_SLEEP(10)},
    {LCM_SEND(2), {0x10,0x00}},
    {LCM_SLEEP(120)},
};

static LCM_Init_Code sleep_out[] =  {
    {LCM_SEND(1), {0x11}},
    {LCM_SLEEP(120)},
    {LCM_SEND(1), {0x29}},
    {LCM_SLEEP(20)},
};

/*
static void ili6180_mipi_set_lcmvdd(void)
{
	regulator_set_voltage("vddsim2",1800);
	regulator_enable("vddsim2");
	return;
}*/
/*static void lcd_power_en(struct panel_spec *self,unsigned char enabled)
{
    if(enabled)
    {
		sprd_gpio_request(232, "lcm_pwr_en");
		sprd_gpio_direction_output(232, 1);
    }
    else
    {	
		sprd_gpio_request(232, "lcm_pwr_en");
		sprd_gpio_direction_output(232, 0);
    }
}*/
static int32_t ili6180_mipi_init(struct panel_spec *self)
{
	LCD_PRINT("[%s][%d] ili6180_mipi_init Enter!\n",__FUNCTION__,__LINE__);
	int32_t i;
	LCM_Init_Code *init = init_data;
	unsigned int tag;
	

	mipi_set_cmd_mode_t mipi_set_cmd_mode = self->info.mipi->ops->mipi_set_cmd_mode;
	mipi_gen_write_t mipi_gen_write = self->info.mipi->ops->mipi_gen_write;
	mipi_eotp_set_t mipi_eotp_set = self->info.mipi->ops->mipi_eotp_set;
	mipi_set_lp_mode_t mipi_set_lp_mode = self->info.mipi->ops->mipi_set_lp_mode;
	mipi_set_hs_mode_t mipi_set_hs_mode = self->info.mipi->ops->mipi_set_hs_mode;
	
	LCD_PRINT("[%s] Lancewu lcd_ili6180_init\n",__FUNCTION__);

	//ili6180_mipi_set_lcmvdd();
	
	udelay(2*1000);
	mipi_set_cmd_mode();
	mipi_eotp_set(1,0);

	LCD_PRINT("lcd_ili6180 data size: %d\n",ARRAY_SIZE(init_data));

	for(i = 0; i < ARRAY_SIZE(init_data); i++)
	{
		tag = (init->tag >>24);
		if(tag & LCM_TAG_SEND)
		{
			LCD_PRINT("\n init->data : 0x%x\n",init->data[i]);
			mipi_gen_write(init->data, (init->tag & LCM_TAG_MASK));
			udelay(20);
		}
		else if(tag & LCM_TAG_SLEEP)
		{
			udelay((init->tag & LCM_TAG_MASK) * 1000);
		}
		
		init++;
	}

	mipi_eotp_set(1,1);

	return 0;
	
}


static uint32_t ili6180_readid(struct panel_spec *self)
{
	LCD_PRINT("[%s] ili6180_readid Enter!\n",__FUNCTION__);
	return 0x6180;
}




static uint32_t ili6180_panel_reset(struct panel_spec *self)
{
//	lcd_power_en(self,1);
//	mdelay(20);
//	lcd_reset(self,1);
//	mdelay(20);
//	lcd_reset(self,0);
//	mdelay(30);
//	lcd_reset(self,1);
//	mdelay(30);
	//lcd_vdd_en(1);
//	mdelay(20);

	return 1;
}
static struct panel_operations lcd_ili6180_mipi_operations = {
	.panel_init = ili6180_mipi_init,
	.panel_readid = ili6180_readid,
	//.panel_reset = ili6180_panel_reset,
};


static struct timing_rgb lcd_ili6180_mipi_timing = {
	.hfp = 80,  
	.hbp = 60,
	.hsync = 1,
	.vfp = 35,
	.vbp = 25,
	.vsync = 1,
};



static struct info_mipi lcd_ili6180_mipi_info = {
	.work_mode  = SPRDFB_MIPI_MODE_VIDEO,
	.video_bus_width = 24, 
	.lan_number = 4,
	.phy_feq = 304*1000,
	.h_sync_pol = SPRDFB_POLARITY_POS,
	.v_sync_pol = SPRDFB_POLARITY_POS,
	.de_pol = SPRDFB_POLARITY_POS,	
	.te_pol = SPRDFB_POLARITY_POS,
	.color_mode_pol = SPRDFB_POLARITY_NEG,
	.shut_down_pol = SPRDFB_POLARITY_NEG,
	.timing = &lcd_ili6180_mipi_timing,
	.ops = NULL,
};




struct panel_spec lcd_ili6180_mipi_spec = {


	.width = 600,    
	.height = 1024,
	.fps = 60,
	.type = LCD_MODE_DSI,
	.direction = LCD_DIRECT_NORMAL,
	.info = {
		.mipi = &lcd_ili6180_mipi_info
	},
	.ops = &lcd_ili6180_mipi_operations,
};

