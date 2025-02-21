/* drivers/video/sc8825/lcd_nt35521s_mipi.c
 *
 * Support for nt35521s mipi LCD device
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
{LCM_SEND(7),{5,0,0xFF,0xAA,0x55,0x25,0x01}},
{LCM_SEND(2),{0xFC,0x20}},
{LCM_SLEEP(20),},
{LCM_SEND(2),{0xFC,0x00}},
{LCM_SLEEP(20),},
{LCM_SEND(2),{0xFC,0x20}},
{LCM_SLEEP(20),},
{LCM_SEND(7),{5,0,0xFF,0xAA,0x55,0x25,0x00}},
////---------------Page 0 Enable-----------------------------------------------------------------------
{LCM_SEND(8),{6,0,0xF0,0x55,0xAA,0x52,0x08,0x00}},
{LCM_SEND(5),{3,0,0xB1,0x68,0x01}},
{LCM_SEND(2),{0xB6,0x08}},
{LCM_SEND(6),{4,0,0xB8,0x01,0x02,0x08}},
{LCM_SEND(5),{3,0,0xBB,0x44,0x44}},
{LCM_SEND(5),{3,0,0xBC,0x00,0x00}},
{LCM_SEND(8),{6,0,0xBD,0x02,0x68,0x10,0x10,0x00}},
{LCM_SEND(2),{0xC8,0x80}},
////---------------Page 1 Enable-----------------------------------------------------------------------
{LCM_SEND(8),{6,0,0xF0,0x55,0xAA,0x52,0x08,0x01}},
{LCM_SEND(5),{3,0,0xB3,0x4F,0x29}}, 
{LCM_SEND(5),{3,0,0xB4,0x10,0x10}},
{LCM_SEND(5),{3,0,0xB5,0x05,0x05}},
{LCM_SEND(5),{3,0,0xB9,0x35,0x35}},
{LCM_SEND(5),{3,0,0xBA,0x25,0x25}},
{LCM_SEND(5),{3,0,0xBC,0x68,0x00}},
{LCM_SEND(5),{3,0,0xBD,0x68,0x00}},
{LCM_SEND(2),{0xBE,0x34}},    
{LCM_SEND(2),{0xC0,0x0C}},
{LCM_SEND(2),{0xCA,0x00}},
////---------------Page 2 Enable-----------------------------------------------------------------------
{LCM_SEND(8),{6,0,0xF0,0x55,0xAA,0x52,0x08,0x02}},
{LCM_SEND(2),{0xEE,0x01}},					
{LCM_SEND(19),{17,0,0xB0,0x00,0x00,0x00,0x0F,0x00,0x2A,0x00,0x40,0x00,0x54,0x00,0x76,0x00,0x93,0x00,0xC5}},						
{LCM_SEND(19),{17,0,0xB1,0x00,0xF0,0x01,0x32,0x01,0x66,0x01,0xBB,0x01,0xFF,0x02,0x01,0x02,0x42,0x02,0x85}},							
{LCM_SEND(19),{17,0,0xB2,0x02,0xAF,0x02,0xE0,0x03,0x05,0x03,0x35,0x03,0x54,0x03,0x84,0x03,0xA0,0x03,0xC4}},					
{LCM_SEND(7),{5,0,0xB3,0x03,0xF2,0x03,0xFF}},							
////---------------Page 3 Enable-----------------------------------------------------------------------
{LCM_SEND(8),{6,0,0xF0,0x55,0xAA,0x52,0x08,0x03 }}, 
{LCM_SEND(5),{3,0,0xB0,0x00,0x00}},
{LCM_SEND(5),{3,0,0xB1,0x00,0x00}},
{LCM_SEND(8),{6,0,0xB2,0x08,0x00,0x17,0x00,0x00}},
{LCM_SEND(8),{6,0,0xB6,0x05,0x00,0x00,0x00,0x00}}, 
{LCM_SEND(8),{6,0,0xBA,0x53,0x00,0xA0,0x00,0x00}},
{LCM_SEND(8),{6,0,0xBB,0x53,0x00,0xA0,0x00,0x00}},
{LCM_SEND(7),{5,0,0xC0,0x00,0x00,0x00,0x00}},
{LCM_SEND(7),{5,0,0xC1,0x00,0x00,0x00,0x00}},
{LCM_SEND(2),{0xC4,0x60}},
{LCM_SEND(2),{0xC5,0xC0}},
////---------------Page 5 Enable-----------------------------------------------------------------------
{LCM_SEND(8),{6,0,0xF0,0x55,0xAA,0x52,0x08,0x05}},
{LCM_SEND(5),{3,0,0xB0,0x17,0x06}},
{LCM_SEND(5),{3,0,0xB1,0x17,0x06}},
{LCM_SEND(5),{3,0,0xB2,0x17,0x06}},
{LCM_SEND(5),{3,0,0xB3,0x17,0x06}},
{LCM_SEND(5),{3,0,0xB4,0x17,0x06}},
{LCM_SEND(5),{3,0,0xB5,0x17,0x06}},
{LCM_SEND(2),{0xB8,0x0C}},
{LCM_SEND(2),{0xB9,0x00}},
{LCM_SEND(2),{0xBA,0x00}},
{LCM_SEND(2),{0xBB,0x0A}},
{LCM_SEND(2),{0xBC,0x02}},
{LCM_SEND(8),{6,0,0xBD,0x03,0x01,0x01,0x03,0x03}},
{LCM_SEND(2),{0xC0,0x07}},
{LCM_SEND(2),{0xC4,0xA2}},
{LCM_SEND(5),{3,0,0xC8,0x03,0x20}},
{LCM_SEND(5),{3,0,0xC9,0x01,0x21}},
{LCM_SEND(6),{4,0,0xCC,0x00,0x00,0x01}},
{LCM_SEND(6),{4,0,0xCD,0x00,0x00,0x01}},
{LCM_SEND(8),{6,0,0xD1,0x00,0x04,0xFC,0x07,0x14}},
{LCM_SEND(8),{6,0,0xD2,0x10,0x05,0x00,0x03,0x16}},
{LCM_SEND(2),{0xE5,0x06}},
{LCM_SEND(2),{0xE6,0x06}},
{LCM_SEND(2),{0xE7,0x06}},
{LCM_SEND(2),{0xE8,0x06}},
{LCM_SEND(2),{0xE9,0x06}},
{LCM_SEND(2),{0xEA,0x06}},
{LCM_SEND(2),{0xED,0x30}},
////---------------Page 6 Enable-----------------------------------------------------------------------
{LCM_SEND(8),{6,0,0xF0,0x55,0xAA,0x52,0x08,0x06}},
{LCM_SEND(5),{3,0,0xB0,0x17,0x11}},
{LCM_SEND(5),{3,0,0xB1,0x16,0x10}},
{LCM_SEND(5),{3,0,0xB2,0x12,0x18}},
{LCM_SEND(5),{3,0,0xB3,0x13,0x19}},
{LCM_SEND(5),{3,0,0xB4,0x00,0x31}},
{LCM_SEND(5),{3,0,0xB5,0x31,0x34}},
{LCM_SEND(5),{3,0,0xB6,0x34,0x29}},
{LCM_SEND(5),{3,0,0xB7,0x2A,0x33}},
{LCM_SEND(5),{3,0,0xB8,0x2E,0x2D}},
{LCM_SEND(5),{3,0,0xB9,0x08,0x34}},
{LCM_SEND(5),{3,0,0xBA,0x34,0x08}},
{LCM_SEND(5),{3,0,0xBB,0x2D,0x2E}},
{LCM_SEND(5),{3,0,0xBC,0x34,0x2A}},
{LCM_SEND(5),{3,0,0xBD,0x29,0x34}},
{LCM_SEND(5),{3,0,0xBE,0x34,0x31}},
{LCM_SEND(5),{3,0,0xBF,0x31,0x00}},
{LCM_SEND(5),{3,0,0xC0,0x19,0x13}},
{LCM_SEND(5),{3,0,0xC1,0x18,0x12}},
{LCM_SEND(5),{3,0,0xC2,0x10,0x16}},
{LCM_SEND(5),{3,0,0xC3,0x11,0x17}},
{LCM_SEND(5),{3,0,0xE5,0x34,0x34}},
{LCM_SEND(5),{3,0,0xC4,0x12,0x18}},
{LCM_SEND(5),{3,0,0xC5,0x13,0x19}},
{LCM_SEND(5),{3,0,0xC6,0x17,0x11}},
{LCM_SEND(5),{3,0,0xC7,0x16,0x10}},
{LCM_SEND(5),{3,0,0xC8,0x08,0x31}},
{LCM_SEND(5),{3,0,0xC9,0x31,0x34}},
{LCM_SEND(5),{3,0,0xCA,0x34,0x29}},
{LCM_SEND(5),{3,0,0xCB,0x2A,0x33}},
{LCM_SEND(5),{3,0,0xCC,0x2D,0x2E}},
{LCM_SEND(5),{3,0,0xCD,0x00,0x34}},
{LCM_SEND(5),{3,0,0xCE,0x34,0x00}},
{LCM_SEND(5),{3,0,0xCF,0x2E,0x2D}},
{LCM_SEND(5),{3,0,0xD0,0x34,0x2A}},
{LCM_SEND(5),{3,0,0xD1,0x29,0x34}},
{LCM_SEND(5),{3,0,0xD2,0x34,0x31}},
{LCM_SEND(5),{3,0,0xD3,0x31,0x08}},
{LCM_SEND(5),{3,0,0xD4,0x10,0x16}},
{LCM_SEND(5),{3,0,0xD5,0x11,0x17}},
{LCM_SEND(5),{3,0,0xD6,0x19,0x13}},
{LCM_SEND(5),{3,0,0xD7,0x18,0x12}},
{LCM_SEND(5),{3,0,0xE6,0x34,0x34}},
{LCM_SEND(8),{6,0,0xD8,0x00,0x00,0x00,0x00,0x00}},
{LCM_SEND(8),{6,0,0xD9,0x00,0x00,0x00,0x00,0x00}},
{LCM_SEND(2),{0xE7,0x00}},

{LCM_SEND(2),{0x11,0x00}},
{LCM_SLEEP(120),},
{LCM_SEND(2),{0x29,0x00}},
};

static LCM_Init_Code disp_on =  {LCM_SEND(1), {0x29}};

static LCM_Init_Code sleep_in =  {LCM_SEND(1), {0x10}};

static LCM_Init_Code sleep_out =  {LCM_SEND(1), {0x11}};

static LCM_Force_Cmd_Code rd_prep_code[]={
	{0x39, {LCM_SEND(8), {0x6, 0, 0xF0, 0x55, 0xAA, 0x52, 0x08, 0x01}}},
	{0x37, {LCM_SEND(2), {0x3, 0}}},
};

static int32_t nt35521s_mipi_init(struct panel_spec *self)
{
	int32_t i;
	LCM_Init_Code *init = init_data;
	unsigned int tag;

	mipi_set_cmd_mode_t mipi_set_cmd_mode = self->info.mipi->ops->mipi_set_cmd_mode;
	mipi_gen_write_t mipi_gen_write = self->info.mipi->ops->mipi_gen_write;

	LCD_PRINT("nt35521s_init\n");

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

static uint32_t nt35521s_readid(struct panel_spec *self)
{
	/*Jessica TODO: need read id*/
	int32_t i = 0;
	uint32 j =0;
	LCM_Force_Cmd_Code * rd_prepare = rd_prep_code;
	uint8_t read_data[3] = {0};
	int32_t read_rtn = 0;
	unsigned int tag = 0;
	mipi_set_cmd_mode_t mipi_set_cmd_mode = self->info.mipi->ops->mipi_set_cmd_mode;
	mipi_force_write_t mipi_force_write = self->info.mipi->ops->mipi_force_write;
	mipi_force_read_t mipi_force_read = self->info.mipi->ops->mipi_force_read;
	mipi_eotp_set_t mipi_eotp_set = self->info.mipi->ops->mipi_eotp_set;

	LCD_PRINT("lcd_nt35521s_mipi read id!\n");
	//		return 0x21;	//debug
//#ifdef CONFIG_SC8830
//	return 0x12;	//debug
//#endif
	mipi_set_cmd_mode();
	mipi_eotp_set(0,1);
	for(j = 0; j < 4; j++){
		rd_prepare = rd_prep_code;
		for(i = 0; i < ARRAY_SIZE(rd_prep_code); i++){
			tag = (rd_prepare->real_cmd_code.tag >> 24);
			if(tag & LCM_TAG_SEND){
				mipi_force_write(rd_prepare->datatype, rd_prepare->real_cmd_code.data, (rd_prepare->real_cmd_code.tag & LCM_TAG_MASK));
			}else if(tag & LCM_TAG_SLEEP){
				mdelay((rd_prepare->real_cmd_code.tag & LCM_TAG_MASK));
			}
			rd_prepare++;
		}
		read_rtn = mipi_force_read(0xC5, 3,(uint8_t *)read_data);
		LCD_PRINT("lcd_nt35521s_mipi read id 0xc5 value is 0x%x, 0x%x, 0x%x!\n", read_data[0], read_data[1], read_data[2]);

		if((0x55 == read_data[0])&&(0x21 == read_data[1])){
			LCD_PRINT("lcd_nt35521s_mipi read id success!\n");
			mipi_eotp_set(1,1);
			return 0x21;
		}
	}
	mipi_eotp_set(1,1);
	return 0x0;
}

static struct panel_operations lcd_nt35521s_mipi_operations = {
	.panel_init = nt35521s_mipi_init,
	.panel_readid = nt35521s_readid,
};

static struct timing_rgb lcd_nt35521s_mipi_timing = {
	.hfp = 50,  /* unit: pixel */
	.hbp = 64,
	.hsync = 16,
	.vfp = 16, /*unit: line*/
	.vbp = 15,
	.vsync = 2,
};

static struct info_mipi lcd_nt35521s_mipi_info = {
	.work_mode  = SPRDFB_MIPI_MODE_VIDEO,
	.video_bus_width = 24, /*18,16*/
	.lan_number = 4,
	.phy_feq = 450*1000,
	.h_sync_pol = SPRDFB_POLARITY_POS,
	.v_sync_pol = SPRDFB_POLARITY_POS,
	.de_pol = SPRDFB_POLARITY_POS,
	.te_pol = SPRDFB_POLARITY_POS,
	.color_mode_pol = SPRDFB_POLARITY_NEG,
	.shut_down_pol = SPRDFB_POLARITY_NEG,
	.timing = &lcd_nt35521s_mipi_timing,
	.ops = NULL,
};

struct panel_spec lcd_nt35521s_mipi_spec = {
	.width = 800,
	.height = 1280,
	.type = LCD_MODE_DSI,
	.direction = LCD_DIRECT_NORMAL,
	.info = {
		.mipi = &lcd_nt35521s_mipi_info
	},
	.ops = &lcd_nt35521s_mipi_operations,
};
