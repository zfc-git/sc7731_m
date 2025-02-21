/* drivers/video/sc8825/lcd_ek79029_mipi.c
 *
 * Support for ek79029 mipi LCD device
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
#include "../sprdfb_panel.h"
#include "../sprdfb.h"

#define printk printf

//#define  LCD_DEBUG
#ifdef LCD_DEBUG
#define LCD_PRINT printk
#else
#define LCD_PRINT(...)
#endif

#define MAX_DATA   56

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
//#define ARRAY_SIZE(array) ( sizeof(array) / sizeof(array[0]))

#define LCM_TAG_SEND  (1<< 0)
#define LCM_TAG_SLEEP (1 << 1)

static LCM_Init_Code init_data[] = {
{LCM_SEND(2),{0xCD,0xAA}},
{LCM_SLEEP(1),},
{LCM_SEND(2),{0x30,0x00}},
{LCM_SLEEP(1),},
{LCM_SEND(2),{0x32,0x00}},
{LCM_SLEEP(1),},
{LCM_SEND(2),{0x33,0x00}},
{LCM_SLEEP(1),},
{LCM_SEND(2),{0x65,0x08}},
{LCM_SLEEP(1),},
{LCM_SEND(2),{0x3A,0x04}},
{LCM_SLEEP(1),},
{LCM_SEND(2),{0x36,0x00}},
{LCM_SLEEP(1),},
{LCM_SEND(2),{0x67,0x82}},
{LCM_SLEEP(1),},
{LCM_SEND(2),{0x69,0x23}},
{LCM_SLEEP(1),},
{LCM_SEND(2),{0x6C,0xC1}},
{LCM_SLEEP(1),},
{LCM_SEND(2),{0x6d,0x00}},
{LCM_SLEEP(1),},
{LCM_SEND(11),{9,0,0x55,0x00,0x0F,0x00,0x0F,0x00,0x0F,0x00,0x0F}},
{LCM_SLEEP(1),},
{LCM_SEND(19),{17,0,0x56,0x00,0x0F,0x00,0x0F,0x00,0x0F,0x00,0x0F,0x00,0x0F,0x00,0x0F,0x00,0x0F,0x00,0x0F}},
{LCM_SLEEP(1),},
{LCM_SEND(2),{0x6B,0x00}},
{LCM_SLEEP(1),},
{LCM_SEND(2),{0x58,0x01}},
{LCM_SLEEP(1),},
{LCM_SEND(2),{0x73,0xF0}},
{LCM_SLEEP(1),},
{LCM_SEND(2),{0x74,0x17}},
{LCM_SLEEP(1),},
{LCM_SEND(2),{0x5E,0x03}},
{LCM_SLEEP(1),},
{LCM_SEND(2),{0x68,0x02}},
{LCM_SLEEP(1),},
{LCM_SEND(2),{0x6A,0x00}},
{LCM_SLEEP(1),},
{LCM_SEND(2),{0x28,0x31}},
{LCM_SLEEP(1),},
{LCM_SEND(2),{0x29,0x08}},
{LCM_SLEEP(1),},
{LCM_SEND(2),{0x63,0x04}},
{LCM_SLEEP(1),},
{LCM_SEND(11),{9,0,0x57,0x00,0x0F,0x00,0x0F,0x00,0x0F,0x00,0x0F}},
{LCM_SLEEP(1),},
{LCM_SEND(2),{0x7E,0x38}},
{LCM_SLEEP(1),},
{LCM_SEND(2),{0x4E,0x46}},
{LCM_SLEEP(1),},
{LCM_SEND(2),{0x4F,0x4A}},
{LCM_SLEEP(1),},
{LCM_SEND(2),{0x41,0x22}},
{LCM_SLEEP(1),},
{LCM_SEND(22),{20,0,0x53,0x1F,0x1C,0x1B,0x17,0x18,0x18,0x1A,0x1B,0x1B,0x15,0x10,0x0E,0x0E,0x0F,0x0E,0x0C,0x08,0x06,0x00}},
{LCM_SLEEP(1),},
{LCM_SEND(22),{20,0,0x54,0x1F,0x1C,0x1B,0x17,0x18,0x18,0x19,0x1B,0x1B,0x15,0x10,0x0E,0x0D,0x0E,0x0D,0x0C,0x08,0x06,0x00}},
{LCM_SLEEP(1),},
{LCM_SEND(2),{0x50,0xD0}},
{LCM_SLEEP(1),},
{LCM_SEND(2),{0x76,0x34}},
{LCM_SLEEP(1),},
{LCM_SEND(2),{0x77,0x00}},
{LCM_SLEEP(1),},
{LCM_SEND(2),{0x47,0x1F}},
{LCM_SLEEP(1),},
{LCM_SEND(2),{0x78,0x67}},
{LCM_SLEEP(1),},
{LCM_SEND(2),{0x48,0x67}},
{LCM_SLEEP(1),},
{LCM_SEND(2),{0x2D,0x31}},
{LCM_SLEEP(1),},
{LCM_SEND(2),{0x4D,0x00}},
{LCM_SLEEP(1),},
					 
{LCM_SEND(1),{0x11}},
// SLPOUT                                                                   
{LCM_SLEEP(120),},
//DISP ON                                                                                           
{LCM_SEND(1),{0x29}},
// DSPON                                                                    
{LCM_SLEEP(5),},
//--- TE----//                                                                                      
{LCM_SEND(2),{0x35,0x00}},
};

static LCM_Init_Code disp_on =  {LCM_SEND(1), {0x29}};

static LCM_Init_Code sleep_in =  {LCM_SEND(1), {0x10}};

static LCM_Init_Code sleep_out =  {LCM_SEND(1), {0x11}};

static LCM_Force_Cmd_Code rd_prep_code[]={
	{0x39, {LCM_SEND(8), {0x6, 0, 0xF0, 0x55, 0xAA, 0x52, 0x08, 0x01}}},
	{0x37, {LCM_SEND(2), {0x3, 0}}},
};

static int32_t ek79029_mipi_init(struct panel_spec *self)
{
	int32_t i;
	LCM_Init_Code *init = init_data;
	unsigned int tag;

	mipi_set_cmd_mode_t mipi_set_cmd_mode = self->info.mipi->ops->mipi_set_cmd_mode;
	mipi_gen_write_t mipi_gen_write = self->info.mipi->ops->mipi_gen_write;

	LCD_PRINT("ek79029_init\n");

	mipi_set_cmd_mode();

	for(i = 0; i < ARRAY_SIZE(init_data); i++){
		tag = (init->tag >>24);
		if(tag & LCM_TAG_SEND){
			mipi_gen_write(init->data, (init->tag & LCM_TAG_MASK));
			udelay(20);
		}else if(tag & LCM_TAG_SLEEP){
			mdelay((init->tag & LCM_TAG_MASK));
		}
		init++;
	}
	return 0;
}

static uint32_t ek79029_readid(struct panel_spec *self)
{
	/*Jessica TODO: need read id*/
	int32_t i = 0;
	uint32 j =0;
	uint8_t read_data[3] = {0};
	int32_t read_rtn = 0;
	unsigned int tag = 0;
	mipi_set_cmd_mode_t mipi_set_cmd_mode = self->info.mipi->ops->mipi_set_cmd_mode;
	mipi_force_write_t mipi_force_write = self->info.mipi->ops->mipi_force_write;
	mipi_force_read_t mipi_force_read = self->info.mipi->ops->mipi_force_read;
	mipi_eotp_set_t mipi_eotp_set = self->info.mipi->ops->mipi_eotp_set;

    	return 0x7902;
}

static struct panel_operations lcd_ek79029_mipi_operations = {
	.panel_init = ek79029_mipi_init,
	.panel_readid = ek79029_readid,
};

static struct timing_rgb lcd_ek79029_mipi_timing = {
	.hfp = 125,  /* unit: pixel */
	.hbp = 120,
	.hsync = 8,
	.vfp = 25, /*unit: line*/
	.vbp = 10,
	.vsync = 4,
};

static struct info_mipi lcd_ek79029_mipi_info = {
	.work_mode  = SPRDFB_MIPI_MODE_VIDEO,
	.video_bus_width = 24, /*18,16*/
	.lan_number = 4,
	.phy_feq = 500*1000,
	.h_sync_pol = SPRDFB_POLARITY_POS,
	.v_sync_pol = SPRDFB_POLARITY_POS,
	.de_pol = SPRDFB_POLARITY_POS,
	.te_pol = SPRDFB_POLARITY_POS,
	.color_mode_pol = SPRDFB_POLARITY_NEG,
	.shut_down_pol = SPRDFB_POLARITY_NEG,
	.timing = &lcd_ek79029_mipi_timing,
	.ops = NULL,
};

struct panel_spec lcd_ek79029_mipi_spec = {
	.width = 800,
	.height = 1280,
	.fps = 60,
	.type = LCD_MODE_DSI,
	.direction = LCD_DIRECT_NORMAL,
	.info = {
		.mipi = &lcd_ek79029_mipi_info
	},
	.ops = &lcd_ek79029_mipi_operations,
};
