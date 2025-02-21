/* drivers/video/sc8825/lcd_ek79029_mipi.c
 *
 * Support for lcd_ek79029 mipi LCD device
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
{LCM_SEND(2),{0xCD,0xAA}},

{LCM_SLEEP(1)},

{LCM_SEND(2),{0x30,0x00}},

{LCM_SLEEP(1)},

{LCM_SEND(2),{0x5E,0x03}},

{LCM_SLEEP(1)},

{LCM_SEND(2),{0x32,0x00}},

{LCM_SLEEP(1)},


{LCM_SEND(2),{0x33,0x25}},

{LCM_SLEEP(1)},

{LCM_SEND(2),{0x65,0x08}},

{LCM_SLEEP(1)},

{LCM_SEND(2),{0x3A,0x10}},

{LCM_SLEEP(1)},

{LCM_SEND(2),{0x36,0x49}},

{LCM_SLEEP(1)},

{LCM_SEND(2),{0x67,0x82}},

{LCM_SLEEP(1)},

{LCM_SEND(2),{0x69,0x27}},

{LCM_SLEEP(1)},


{LCM_SEND(2),{0x6C,0x01}},

{LCM_SLEEP(1)},


{LCM_SEND(2),{0x6D,0x14}},

{LCM_SLEEP(1)},

{LCM_SEND(11), {9,0,0x55,0x09,0x09,0x09,0x09,0x09,0x09,0x09,0x09}},

{LCM_SLEEP(1)},

{LCM_SEND(19), {17,0,0x56,0x09,0x09,0x09,0x09,0x09,0x09,0x09,0x09,0x09,0x09,0x09,0x09,0x09,0x09,0x09,0x09}},

{LCM_SLEEP(1)},


{LCM_SEND(2),{0x6B,0x00}},

{LCM_SLEEP(1)},

{LCM_SEND(2),{0x58,0x08}},

{LCM_SLEEP(1)},

{LCM_SEND(2),{0x73,0xF0}},

{LCM_SLEEP(1)},
{LCM_SEND(2),{0x74,0x17}},
{LCM_SLEEP(1)},
{LCM_SEND(2),{0x61,0x84}},
{LCM_SLEEP(1)},
{LCM_SEND(2),{0x77,0x00}},

{LCM_SLEEP(1)},


{LCM_SEND(2),{0x68,0x06}},

{LCM_SLEEP(1)},

{LCM_SEND(2),{0x28,0x35}},

{LCM_SLEEP(1)},


{LCM_SEND(7), {5,0,0x57,0x00,0x00,0x09,0x09}},

{LCM_SLEEP(1)},

{LCM_SEND(2),{0x41,0x70}},

{LCM_SLEEP(1)},

{LCM_SEND(2),{0x7E,0x38}},

{LCM_SLEEP(1)},

{LCM_SEND(2),{0x4E,0x50}},

{LCM_SLEEP(1)},

{LCM_SEND(2),{0x4F,0x4F}},

{LCM_SLEEP(1)},
{LCM_SEND(2),{0x63,0x44}},

{LCM_SLEEP(1)},

{LCM_SEND(22), {20,0,0x53,0x18,0x15,0x14,0x11,0x12,0x12,0x14,0x16,0x16,0x11,0x0d,0x0c,0x0b,0x0d,0x0d,0x0c,0x08,0x03,0x00}},

{LCM_SLEEP(1)},

{LCM_SEND(22), {20,0,0x54,0x18,0x15,0x14,0x11,0x12,0x12,0x14,0x16,0x16,0x11,0x0d,0x0c,0x0b,0x0d,0x0d,0x0c,0x08,0x03,0x00}},

{LCM_SLEEP(1)},

{LCM_SEND(2),{0x29,0x10}},

{LCM_SLEEP(1)},


{LCM_SEND(2),{0x2E,0x03}},
{LCM_SLEEP(1)},

{LCM_SEND(2),{0x7C,0x80}},
{LCM_SLEEP(1)},

{LCM_SEND(2),{0x78,0x67}},

{LCM_SLEEP(1)},

{LCM_SEND(2),{0x76,0x36}},

{LCM_SLEEP(1)},

{LCM_SEND(2),{0x47,0x18}},

{LCM_SLEEP(1)},

{LCM_SEND(2),{0x2D,0x31}},

{LCM_SLEEP(1)},

{LCM_SEND(2),{0x50,0xd0}},

{LCM_SLEEP(1)},

{LCM_SEND(2),{0x48,0x66}},

{LCM_SLEEP(1)},

{LCM_SEND(2),{0x3F,0x00}},

{LCM_SLEEP(1)},
{LCM_SEND(2),{0x66,0x81}},

{LCM_SLEEP(1)},
{LCM_SEND(2),{0x4D,0x00}},

{LCM_SLEEP(1)},

{LCM_SEND(1), {0x11}},
{LCM_SLEEP(100)},
};


static LCM_Init_Code disp_on =  {LCM_SEND(1), {0x29}};

static LCM_Init_Code sleep_in[]=  {
    {LCM_SEND(1), {0x10}},
    //{LCM_SLEEP(120)},
};

static LCM_Init_Code sleep_out[] =  {
    {LCM_SEND(1), {0x11}},
    //{LCM_SLEEP(120)},
};

static int32_t ek79029_mipi_init(struct panel_spec *self)
{
	LCD_PRINT("[%s][%d] ek79029_mipi_init Enter!\n",__FUNCTION__,__LINE__);
	int32_t i;
	LCM_Init_Code *init = init_data;
	unsigned int tag;
	

	mipi_set_cmd_mode_t mipi_set_cmd_mode = self->info.mipi->ops->mipi_set_cmd_mode;
	mipi_dcs_write_t mipi_dcs_write = self->info.mipi->ops->mipi_dcs_write;
	mipi_eotp_set_t mipi_eotp_set = self->info.mipi->ops->mipi_eotp_set;
	mipi_set_lp_mode_t mipi_set_lp_mode = self->info.mipi->ops->mipi_set_lp_mode;
	mipi_set_hs_mode_t mipi_set_hs_mode = self->info.mipi->ops->mipi_set_hs_mode;
	
	LCD_PRINT("[%s] Lancewu lcd_ek79029_init\n",__FUNCTION__);

	//ek79029_mipi_set_lcmvdd();
	
	udelay(2*1000);
	mipi_set_cmd_mode();
	mipi_eotp_set(1,0);

	LCD_PRINT("lcd_ek79029 data size: %d\n",ARRAY_SIZE(init_data));

	for(i = 0; i < ARRAY_SIZE(init_data); i++)
	{
		tag = (init->tag >>24);
		if(tag & LCM_TAG_SEND)
		{
			LCD_PRINT("\n init->data : 0x%x\n",init->data[i]);
			mipi_dcs_write(init->data, (init->tag & LCM_TAG_MASK));
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


static uint32_t ek79029_readid(struct panel_spec *self)
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

    	return 0x7902;

	printk("uboot lcd_ek79029_mipi read id!\n");
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
		//printk("lcd_ek79029_mipi read id 0x04 value is 0x%x!\n", read_data[0]);
		printk("lcd_ek79029_mipi read id 0x04 value is 0x%x, 0x%x, 0x%x!\n", read_data[0], read_data[1], read_data[2]);

		if((0x93 == read_data[0])){
			printk("lcd_ek79029_mipi read id success!\n");
			return 0x7902;
		}
	}

	mdelay(5);
	mipi_set_hs_mode();
	return 0;
	#endif
}

static struct panel_operations lcd_ek79029_mipi_operations = {
	.panel_init = ek79029_mipi_init,
	.panel_readid = ek79029_readid,
};


static struct timing_rgb lcd_ek79029_mipi_timing = {
	.hfp = 125,
	.hbp = 25,
	.hsync = 25,
	.vfp = 12,
	.vbp = 10,
	.vsync = 2,
};								
		
static struct info_mipi lcd_ek79029_mipi_info = {
	.work_mode  = SPRDFB_MIPI_MODE_VIDEO,
	.video_bus_width = 24, 
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

