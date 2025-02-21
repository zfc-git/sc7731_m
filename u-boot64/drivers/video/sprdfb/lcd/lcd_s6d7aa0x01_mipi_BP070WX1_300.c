/* drivers/video/sc8825/lcd_s6d7aa0x01_mipi.c
 *
 * Support for lcd_s6d7aa0x01 mipi LCD device
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
{LCM_SEND(1),{0x01}},//soft reset
{LCM_SLEEP(500)},
{LCM_SEND(5),{3,0,0xF0,0x5A,0x5A}},
{LCM_SEND(5),{3,0,0xF1,0x5A,0x5A}},
{LCM_SEND(5),{3,0,0xFC,0xA5,0xA5}},
{LCM_SEND(2),{0xB1,0x10}},
{LCM_SEND(7),{5,0,0xB2,0x14,0x22,0x2F,0x04}},
{LCM_SEND(5),{3,0,0xD0,0x00,0x10}},
{LCM_SEND(8),{6,0,0xF2,0x02,0x08,0x08,0x40,0x10}},
{LCM_SEND(2),{0xB0,0x03}},
{LCM_SEND(5),{3,0,0xFD,0x23,0x09}},
{LCM_SEND(13),{11,0,0xF3,0x01,0xD7,0xE2,0x62,0xF4,0xF7,0x77,0x3C,0x26,0x00}},
{LCM_SEND(48),{46,0,0xF4,0x00,0x02,0x03,0x26,0x03,0x02,0x09,0x00,0x07,0x16,0x16,0x03,0x00,0x08,0x08,0x03,0x0E,0x0F,0x12,0x1C,0x1D,0x1E,0x0C,0x09,0x01,0x04,0x02,0x61,0x74,0x75,0x72,0x83,0x80,0x80,0xB0,0x00,0x01,0x01,0x28,0x04,0x03,0x28,0x01,0xD1,0x32}},
{LCM_SEND(29),{27,0,0xF5,0x8C,0x2F,0x2F,0x3A,0xAB,0x98,0x52,0x0F,0x33,0x43,0x04,0x59,0x54,0x52,0x05,0x40,0x60,0x4E,0x60,0x40,0x27,0x26,0x52,0x25,0x6D,0x18}},
{LCM_SEND(11),{9,0,0xEE,0x22,0x00,0x22,0x00,0x22,0x00,0x22,0x00}},
{LCM_SEND(11),{9,0,0xEF,0x12,0x12,0x43,0x43,0xA0,0x04,0x24,0x81}},
{LCM_SEND(35),{33,0,0xF7,0x0A,0x0A,0x08,0x08,0x0B,0x0B,0x09,0x09,0x04,0x05,0x01,0x01,0x01,0x01,0x01,0x01,0x0A,0x0A,0x08,0x08,0x0B,0x0B,0x09,0x09,0x04,0x05,0x01,0x01,0x01,0x01,0x01,0x01}},
{LCM_SEND(6),{4,0,0xBC,0x01,0x4E,0x0A}},
{LCM_SEND(8),{6,0,0xE1,0x03,0x10,0x1C,0xA0,0x10}},
{LCM_SEND(9),{7,0,0xF6,0x60,0x25,0xA6,0x00,0x00,0x00}},
{LCM_SEND(9),{7,0,0xFE,0x00,0x0D,0x03,0x21,0x00,0x78}},

{LCM_SEND(20),{18,0,0xFA,0x00,0x35,0x0A,0x12,0x0B,0x11,0x17,0x16,0x19,0x21,0x24,0x24,0x25,0x25,0x24,0x23,0x2B}},
{LCM_SEND(20),{18,0,0xFB,0x00,0x35,0x0A,0x12,0x0A,0x11,0x16,0x16,0x19,0x21,0x24,0x24,0x25,0x25,0x24,0x23,0x2B}},

{LCM_SLEEP(200)}, 
{LCM_SEND(6),{4,0,0xC3,0x40,0x00,0x28}},
{LCM_SLEEP(200)}, 
{LCM_SEND(2),{0x35,0x00}}, 
{LCM_SEND(1),{0x11}}, 
{LCM_SLEEP(200)},                                                                                                                                                                
{LCM_SEND(1),{0x29}},

};


static LCM_Init_Code disp_on =  {LCM_SEND(1), {0x29}};

static LCM_Init_Code sleep_in[]=  {
	{LCM_SEND(1), {0x11}},
	{LCM_SLEEP(170)},
};

static LCM_Init_Code sleep_out[] =  {
	{LCM_SEND(1), {0x10}},
	{LCM_SLEEP(170)},

};

static int32_t s6d7aa0x01_mipi_init(struct panel_spec *self)
{
	LCD_PRINT("[%s][%d] s6d7aa0x01_mipi_init Enter!\n",__FUNCTION__,__LINE__);
	int32_t i;
	LCM_Init_Code *init = init_data;
	unsigned int tag;
	

	mipi_set_cmd_mode_t mipi_set_cmd_mode = self->info.mipi->ops->mipi_set_cmd_mode;
	mipi_gen_write_t mipi_gen_write = self->info.mipi->ops->mipi_gen_write;
	mipi_eotp_set_t mipi_eotp_set = self->info.mipi->ops->mipi_eotp_set;
	mipi_set_lp_mode_t mipi_set_lp_mode = self->info.mipi->ops->mipi_set_lp_mode;
	mipi_set_hs_mode_t mipi_set_hs_mode = self->info.mipi->ops->mipi_set_hs_mode;
	
	LCD_PRINT("[%s] Lancewu lcd_s6d7aa0x01_init\n",__FUNCTION__);

	//s6d7aa0x01_mipi_set_lcmvdd();
	
	udelay(2*1000);
	mipi_set_cmd_mode();
	mipi_eotp_set(1,0);

	LCD_PRINT("lcd_s6d7aa0x01 data size: %d\n",ARRAY_SIZE(init_data));

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


static uint32_t s6d7aa0x01_readid(struct panel_spec *self)
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

    	return 0xaa01;

	printk("uboot lcd_s6d7aa0x01_mipi read id!\n");
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
		//printk("lcd_s6d7aa0x01_mipi read id 0x04 value is 0x%x!\n", read_data[0]);
		printk("lcd_s6d7aa0x01_mipi read id 0x04 value is 0x%x, 0x%x, 0x%x!\n", read_data[0], read_data[1], read_data[2]);

		if((0x93 == read_data[0])){
			printk("lcd_s6d7aa0x01_mipi read id success!\n");
			return 0xaa01;
		}
	}

	mdelay(5);
	mipi_set_hs_mode();
	return 0;
	#endif
}

static struct panel_operations lcd_s6d7aa0x01_mipi_operations = {
	.panel_init = s6d7aa0x01_mipi_init,
	.panel_readid = s6d7aa0x01_readid,
};


static struct timing_rgb lcd_s6d7aa0x01_mipi_timing = {
	.hfp = 16,
	.hbp = 64,
	.hsync = 4,
	.vfp = 8,
	.vbp = 8,
	.vsync = 4,
};								
		
static struct info_mipi lcd_s6d7aa0x01_mipi_info = {
	.work_mode  = SPRDFB_MIPI_MODE_VIDEO,
	.video_bus_width = 24, 
	.lan_number = 4,
	.phy_feq = 460*1000,
	.h_sync_pol = SPRDFB_POLARITY_POS,
	.v_sync_pol = SPRDFB_POLARITY_POS,
	.de_pol = SPRDFB_POLARITY_POS,	
	.te_pol = SPRDFB_POLARITY_POS,
	.color_mode_pol = SPRDFB_POLARITY_NEG,
	.shut_down_pol = SPRDFB_POLARITY_NEG,
	.timing = &lcd_s6d7aa0x01_mipi_timing,
	.ops = NULL,
};




struct panel_spec lcd_s6d7aa0x01_mipi_spec = {


	.width = 800,    
	.height = 1280,
	.fps = 60,
	.type = LCD_MODE_DSI,
	.direction = LCD_DIRECT_NORMAL,
	.info = {
		.mipi = &lcd_s6d7aa0x01_mipi_info
	},
	.ops = &lcd_s6d7aa0x01_mipi_operations,
};

