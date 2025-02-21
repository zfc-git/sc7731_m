/* drivers/video/sc8825/lcd_s6d7aa0x02_mipi.c
 *
 * Support for lcd_s6d7aa0x02 mipi LCD device
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
//{LCM_SEND(7),{5,0,0xFF,0xAA,0x55,0xA5,0x80}},
//{LCM_SEND(5),{3,0,0x6F,0x07,0x00}},
//{LCM_SEND(5),{3,0,0xF7,0x50,0x00}},

{LCM_SEND(7),{5,0,0xFF,0xAA,0x55,0xA5,0x80}},
{LCM_SEND(5),{3,0,0x6F,0x11,0x00}},
{LCM_SEND(5),{3,0,0xF7,0x20,0x00}},

{LCM_SEND(2),{0x6F,0x06}},
{LCM_SEND(2),{0xF7,0xA0}},
{LCM_SEND(2),{0x6F,0x19}},
{LCM_SEND(2),{0xF7,0x12}},
{LCM_SEND(8),{6, 0,0xF0,0x55,0xAA,0x52,0x08,0x00}},
{LCM_SEND(2),{0xC8,0x80}},
{LCM_SEND(5),{3, 0,0xB1,0x6C,0x01}},
{LCM_SEND(2),{0xB6,0x08}},
{LCM_SEND(2),{0x6F,0x02}},
{LCM_SEND(2),{0xB8,0x08}},
{LCM_SEND(5),{3, 0,0xBB,0x54,0x54}},
{LCM_SEND(5),{3, 0,0xBC,0x05,0x05}},
{LCM_SEND(2),{0xC7,0x01}},
{LCM_SEND(8),{6, 0,0xBD,0x02,0xB0,0x0C,0x0A,0x00}},
{LCM_SEND(8),{6, 0,0xF0,0x55,0xAA,0x52,0x08,0x01}},
{LCM_SEND(5),{3, 0,0xB0,0x05,0x05}},
{LCM_SEND(5),{3, 0,0xB1,0x05,0x05}},
{LCM_SEND(5),{3, 0,0xBC,0x8E,0x00}},
{LCM_SEND(5),{3, 0,0xBD,0x92,0x00}},
{LCM_SEND(2),{0xCA,0x00}},
{LCM_SEND(2),{0xC0,0x04}},
{LCM_SEND(5),{3, 0,0xB3,0x19,0x19}},
{LCM_SEND(5),{3, 0,0xB4,0x12,0x12}},
{LCM_SEND(5),{3, 0,0xB9,0x24,0x24}},
{LCM_SEND(5),{3, 0,0xBA,0x14,0x14}},
{LCM_SEND(2),{0xBE,0x40}},//VCOM  SET
{LCM_SEND(8),{6, 0,0xF0,0x55,0xAA,0x52,0x08,0x02}},
{LCM_SEND(2),{0xEE,0x02}},
{LCM_SEND(7),{5, 0,0xEF,0x09,0x06,0x15,0x18}},
{LCM_SEND(9),{7, 0,0xB0,0x00,0x00,0x00,0x11,0x00,0x27}},
{LCM_SEND(2),{0x6F,0x06}},
{LCM_SEND(9),{7, 0,0xB0,0x00,0x36,0x00,0x45,0x00,0x5F}},
{LCM_SEND(2),{0x6F,0x0C}},
{LCM_SEND(7),{5, 0,0xB0,0x00,0x74,0x00,0xA5}},
{LCM_SEND(9),{7, 0,0xB1,0x00,0xCF,0x01,0x13,0x01,0x47}},
{LCM_SEND(2),{0x6F,0x06}},
{LCM_SEND(9),{7, 0,0xB1,0x01,0x9B,0x01,0xDF,0x01,0xE1}},
{LCM_SEND(2),{0x6F,0x0C}},
{LCM_SEND(7),{5, 0,0xB1,0x02,0x23,0x02,0x6C}},
{LCM_SEND(9),{7, 0,0xB2,0x02,0x9A,0x02,0xD7,0x03,0x05}},
{LCM_SEND(2),{0x6F,0x06}},
{LCM_SEND(9),{7, 0,0xB2,0x03,0x42,0x03,0x68,0x03,0x91}},
{LCM_SEND(2),{0x6F,0x0C}},
{LCM_SEND(7),{5, 0,0xB2,0x03,0xA5,0x03,0xBD}},
{LCM_SEND(7),{5, 0,0xB3,0x03,0xD7,0x03,0xFF}},
{LCM_SEND(9),{7, 0,0xBC,0x00,0x00,0x00,0x11,0x00,0x27}},
{LCM_SEND(2),{0x6F,0x06}},
{LCM_SEND(9),{7, 0,0xBC,0x00,0x38,0x00,0x47,0x00,0x61}},
{LCM_SEND(2),{0x6F,0x0C}},
{LCM_SEND(7),{5, 0,0xBC,0x00,0x78,0x00,0xAB}},
{LCM_SEND(9),{7, 0,0xBD,0x00,0xD7,0x01,0x1B,0x01,0x4F}},
{LCM_SEND(2),{0x6F,0x06}},
{LCM_SEND(9),{7, 0,0xBD,0x01,0xA1,0x01,0xE5,0x01,0xE7}},
{LCM_SEND(2),{0x6F,0x0C}},
{LCM_SEND(7),{5, 0,0xBD,0x02,0x27,0x02,0x70}},
{LCM_SEND(9),{7, 0,0xBE,0x02,0x9E,0x02,0xDB,0x03,0x07}},
{LCM_SEND(2),{0x6F,0x06}},
{LCM_SEND(9),{7, 0,0xBE,0x03,0x44,0x03,0x6A,0x03,0x93}},
{LCM_SEND(2),{0x6F,0x0C}},
{LCM_SEND(7),{5, 0,0xBE,0x03,0xA5,0x03,0xBD}},
{LCM_SEND(7),{5, 0,0xBF,0x03,0xD7,0x03,0xFF}},
{LCM_SEND(8),{6, 0,0xF0,0x55,0xAA,0x52,0x08,0x06}},
{LCM_SEND(5),{3, 0,0xB0,0x00,0x17}},
{LCM_SEND(5),{3, 0,0xB1,0x16,0x15}},
{LCM_SEND(5),{3, 0,0xB2,0x14,0x13}},
{LCM_SEND(5),{3, 0,0xB3,0x12,0x11}},
{LCM_SEND(5),{3, 0,0xB4,0x10,0x2D}},
{LCM_SEND(5),{3, 0,0xB5,0x01,0x08}},
{LCM_SEND(5),{3, 0,0xB6,0x09,0x31}},
{LCM_SEND(5),{3, 0,0xB7,0x31,0x31}},
{LCM_SEND(5),{3, 0,0xB8,0x31,0x31}},
{LCM_SEND(5),{3, 0,0xB9,0x31,0x31}},
{LCM_SEND(5),{3, 0,0xBA,0x31,0x31}},
{LCM_SEND(5),{3, 0,0xBB,0x31,0x31}},
{LCM_SEND(5),{3, 0,0xBC,0x31,0x31}},
{LCM_SEND(5),{3, 0,0xBD,0x31,0x09}},
{LCM_SEND(5),{3, 0,0xBE,0x08,0x01}},
{LCM_SEND(5),{3, 0,0xBF,0x2D,0x10}},
{LCM_SEND(5),{3, 0,0xC0,0x11,0x12}},
{LCM_SEND(5),{3, 0,0xC1,0x13,0x14}},
{LCM_SEND(5),{3, 0,0xC2,0x15,0x16}},
{LCM_SEND(5),{3, 0,0xC3,0x17,0x00}},
{LCM_SEND(5),{3, 0,0xE5,0x31,0x31}},
{LCM_SEND(5),{3, 0,0xC4,0x00,0x17}},
{LCM_SEND(5),{3, 0,0xC5,0x16,0x15}},
{LCM_SEND(5),{3, 0,0xC6,0x14,0x13}},
{LCM_SEND(5),{3, 0,0xC7,0x12,0x11}},
{LCM_SEND(5),{3, 0,0xC8,0x10,0x2D}},
{LCM_SEND(5),{3, 0,0xC9,0x01,0x08}},
{LCM_SEND(5),{3, 0,0xCA,0x09,0x31}},
{LCM_SEND(5),{3, 0,0xCB,0x31,0x31}},
{LCM_SEND(5),{3, 0,0xCD,0x31,0x09}},
{LCM_SEND(5),{3, 0,0xCE,0x31,0x31}},
{LCM_SEND(5),{3, 0,0xCF,0x31,0x31}},
{LCM_SEND(5),{3, 0,0xD0,0x31,0x31}},
{LCM_SEND(5),{3, 0,0xD1,0x31,0x09}},
{LCM_SEND(5),{3, 0,0xD2,0x08,0x01}},
{LCM_SEND(5),{3, 0,0xD3,0x2D,0x10}},
{LCM_SEND(5),{3, 0,0xD4,0x11,0x12}},
{LCM_SEND(5),{3, 0,0xD5,0x13,0x14}},
{LCM_SEND(5),{3, 0,0xD6,0x15,0x16}},
{LCM_SEND(5),{3, 0,0xD7,0x17,0x00}},
{LCM_SEND(5),{3, 0,0xE6,0x31,0x31}},
{LCM_SEND(8),{6, 0,0xD8,0x00,0x00,0x00,0x00,0x00}},
{LCM_SEND(8),{6, 0,0xD9,0x00,0x00,0x00,0x00,0x00}},
{LCM_SEND(2),{0xE7,0x00}},
{LCM_SEND(8),{6, 0,0xF0,0x55,0xAA,0x52,0x08,0x03}},
{LCM_SEND(5),{3, 0,0xB0,0x20,0x00}},
{LCM_SEND(5),{3, 0,0xB1,0x20,0x00}},
{LCM_SEND(8),{6, 0,0xB2,0x05,0x00,0x42,0x00,0x00}},
{LCM_SEND(8),{6, 0,0xB6,0x05,0x00,0x42,0x00,0x00}},
{LCM_SEND(8),{6, 0,0xBA,0x53,0x00,0x42,0x00,0x00}},
{LCM_SEND(8),{6, 0,0xBB,0x53,0x00,0x42,0x00,0x00}},
{LCM_SEND(2),{0xC4,0x40}},
{LCM_SEND(8),{6, 0,0xF0,0x55,0xAA,0x52,0x08,0x05}},
{LCM_SEND(5),{3, 0,0xB0,0x17,0x06}},
{LCM_SEND(2),{0xB8,0x00}},
{LCM_SEND(8),{6, 0,0xBD,0x03,0x01,0x01,0x00,0x01}},
{LCM_SEND(5),{3, 0,0xB1,0x17,0x06}},
{LCM_SEND(5),{3, 0,0xB9,0x00,0x01}},
{LCM_SEND(5),{3, 0,0xB2,0x17,0x06}},
{LCM_SEND(5),{3, 0,0xBA,0x00,0x01}},
{LCM_SEND(5),{3, 0,0xB3,0x17,0x06}},
{LCM_SEND(5),{3, 0,0xBB,0x0A,0x00}},
{LCM_SEND(5),{3, 0,0xB4,0x17,0x06}},
{LCM_SEND(5),{3, 0,0xB5,0x17,0x06}},
{LCM_SEND(5),{3, 0,0xB6,0x14,0x03}},
{LCM_SEND(5),{3, 0,0xB7,0x00,0x00}},
{LCM_SEND(5),{3, 0,0xBC,0x02,0x01}},
{LCM_SEND(2),{0xC0,0x05}},
{LCM_SEND(2),{0xC4,0xA5}},
{LCM_SEND(5),{3, 0,0xC8,0x03,0x30}},
{LCM_SEND(5),{3, 0,0xC9,0x03,0x51}},
{LCM_SEND(8),{6, 0,0xD1,0x00,0x05,0x03,0x00,0x00}},
{LCM_SEND(8),{6, 0,0xD2,0x00,0x05,0x09,0x00,0x00}},
{LCM_SEND(2),{0xE5,0x02}},
{LCM_SEND(2),{0xE6,0x02}},
{LCM_SEND(2),{0xE7,0x02}},
{LCM_SEND(2),{0xE9,0x02}},
{LCM_SEND(2),{0xED,0x33}},
{LCM_SEND(1),{0x11}}, 
{LCM_SLEEP(200)},                                                                                                                                                                
{LCM_SEND(1),{0x29}},
};


static LCM_Init_Code disp_on =  {LCM_SEND(1), {0x29}};

static LCM_Init_Code sleep_in[]=  {
	{LCM_SEND(1), {0x28}},
	{LCM_SLEEP(150)}, 	//>150ms
	{LCM_SEND(1), {0x10}},
	{LCM_SLEEP(150)},	//>150ms
};

static LCM_Init_Code sleep_out[] =  {
    {LCM_SEND(1), {0x11}},
    {LCM_SLEEP(120)},
    {LCM_SEND(1), {0x29}},
    {LCM_SLEEP(20)},
};

static int32_t s6d7aa0x02_mipi_init(struct panel_spec *self)
{
	LCD_PRINT("[%s][%d] s6d7aa0x02_mipi_init Enter!\n",__FUNCTION__,__LINE__);
	int32_t i;
	LCM_Init_Code *init = init_data;
	unsigned int tag;
	

	mipi_set_cmd_mode_t mipi_set_cmd_mode = self->info.mipi->ops->mipi_set_cmd_mode;
	mipi_gen_write_t mipi_gen_write = self->info.mipi->ops->mipi_gen_write;
	mipi_eotp_set_t mipi_eotp_set = self->info.mipi->ops->mipi_eotp_set;
	mipi_set_lp_mode_t mipi_set_lp_mode = self->info.mipi->ops->mipi_set_lp_mode;
	mipi_set_hs_mode_t mipi_set_hs_mode = self->info.mipi->ops->mipi_set_hs_mode;
	
	LCD_PRINT("[%s] Lancewu lcd_s6d7aa0x02_init\n",__FUNCTION__);

	//s6d7aa0x02_mipi_set_lcmvdd();
	
	udelay(2*1000);
	mipi_set_cmd_mode();
	mipi_eotp_set(1,0);

	LCD_PRINT("lcd_s6d7aa0x02 data size: %d\n",ARRAY_SIZE(init_data));

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


static uint32_t s6d7aa0x02_readid(struct panel_spec *self)
{
        /*Jessica TODO: need read id*/
        int32_t i = 0;
        uint32 j =0;
       // LCM_Force_Cmd_Code * rd_prepare = rd_prep_code;
        uint8_t read_data[3] = {0};
        int32_t read_rtn = 0;
        unsigned int tag = 0;
        mipi_set_cmd_mode_t mipi_set_cmd_mode = self->info.mipi->ops->mipi_set_cmd_mode;
        mipi_force_write_t mipi_force_write = self->info.mipi->ops->mipi_force_write;
        mipi_force_read_t mipi_force_read = self->info.mipi->ops->mipi_force_read;
        mipi_set_lp_mode_t mipi_set_lp_mode = self->info.mipi->ops->mipi_set_lp_mode;
        mipi_set_hs_mode_t mipi_set_hs_mode = self->info.mipi->ops->mipi_set_hs_mode;
        mipi_eotp_set_t mipi_eotp_set = self->info.mipi->ops->mipi_eotp_set;

    	return 0xaa02;

	printk("uboot lcd_s6d7aa0x02_mipi read id!\n");
	#if 0
	mipi_set_lp_mode();

	//mipi_set_cmd_mode();
	for(j = 0; j < 4; j++){
		rd_prepare = rd_prep_code;
		for(i = 0; i < ARRAY_SIZE(rd_prep_code); i++){
			tag = (rd_prepare->real_cmd_code.tag >> 24);
			if(tag & LCM_TAG_SEND){
				mipi_force_write(rd_prepare->datatype, rd_prepare->real_cmd_code.data, (rd_prepare->real_cmd_code.tag & LCM_TAG_MASK));
				udelay(20);
			}else if(tag & LCM_TAG_SLEEP){
				mdelay((rd_prepare->real_cmd_code.tag & LCM_TAG_MASK));
			}
			rd_prepare++;
		}
		mdelay(50);
		read_rtn = mipi_force_read(0x04, 3,(uint8_t *)read_data);
		//printk("lcd_s6d7aa0x02_mipi read id 0x04 value is 0x%x!\n", read_data[0]);
		printk("lcd_s6d7aa0x02_mipi read id 0x04 value is 0x%x, 0x%x, 0x%x!\n", read_data[0], read_data[1], read_data[2]);

		if((0x93 == read_data[0])){
			printk("lcd_s6d7aa0x02_mipi read id success!\n");
			return 0xaa02;
		}
	}

	mdelay(5);
	mipi_set_hs_mode();
	return 0;
	#endif
}

static struct panel_operations lcd_s6d7aa0x02_mipi_operations = {
	.panel_init = s6d7aa0x02_mipi_init,
	.panel_readid = s6d7aa0x02_readid,
};


static struct timing_rgb lcd_s6d7aa0x02_mipi_timing = {
	.hfp = 65,  /* unit: pixel */
	.hbp = 60,
	.hsync = 6,
	.vfp = 20, /*unit: line*/
	.vbp = 16,
	.vsync = 6,
};								
		
static struct info_mipi lcd_s6d7aa0x02_mipi_info = {
	.work_mode  = SPRDFB_MIPI_MODE_VIDEO,
	.video_bus_width = 24, 
	.lan_number = 4,
	.phy_feq = 490*1000,
	.h_sync_pol = SPRDFB_POLARITY_POS,
	.v_sync_pol = SPRDFB_POLARITY_POS,
	.de_pol = SPRDFB_POLARITY_POS,	
	.te_pol = SPRDFB_POLARITY_POS,
	.color_mode_pol = SPRDFB_POLARITY_NEG,
	.shut_down_pol = SPRDFB_POLARITY_NEG,
	.timing = &lcd_s6d7aa0x02_mipi_timing,
	.ops = NULL,
};




struct panel_spec lcd_s6d7aa0x02_mipi_spec = {


	.width = 800,    
	.height = 1280,
	.fps = 60,
	.type = LCD_MODE_DSI,
	.direction = LCD_DIRECT_NORMAL,
	.info = {
		.mipi = &lcd_s6d7aa0x02_mipi_info
	},
	.ops = &lcd_s6d7aa0x02_mipi_operations,
};

