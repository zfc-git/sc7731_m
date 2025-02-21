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
{LCM_SEND(6), {4,0,0xFF,0x61,0x36,0x07 }}, 
{LCM_SEND(2), {0x03,0x20}}, 
{LCM_SEND(2), {0x04,0x04}}, 
{LCM_SEND(2), {0x05,0x00}}, 
{LCM_SEND(2), {0x06,0x00}}, 
{LCM_SEND(2), {0x07,0x00}}, 
{LCM_SEND(2), {0x08,0x00}}, 
{LCM_SEND(2), {0x09,0x00}}, 
{LCM_SEND(2), {0x0a,0x01}}, 
{LCM_SEND(2), {0x0b,0x01}}, 
{LCM_SEND(2), {0x0c,0x01}},
{LCM_SEND(2), {0x0d,0x1e}}, 
{LCM_SEND(2), {0x0e,0x01}}, 
{LCM_SEND(2), {0x0f,0x01}}, 
{LCM_SEND(2), {0x10,0x40}}, 
{LCM_SEND(2), {0x11,0x02}}, 
{LCM_SEND(2), {0x12,0x05}}, 
{LCM_SEND(2), {0x13,0x00}}, 
{LCM_SEND(2), {0x14,0x00}}, 
{LCM_SEND(2), {0x15,0x00}}, 
{LCM_SEND(2), {0x16,0x01}}, 
{LCM_SEND(2), {0x17,0x01}}, 
{LCM_SEND(2), {0x18,0x00}}, 
{LCM_SEND(2), {0x19,0x00}}, 
{LCM_SEND(2), {0x1a,0x00}}, 
{LCM_SEND(2), {0x1b,0xc0}}, 
{LCM_SEND(2), {0x1c,0xbb}}, 
{LCM_SEND(2), {0x1d,0x0b}}, 
{LCM_SEND(2), {0x1e,0x00}}, 
{LCM_SEND(2), {0x1f,0x00}}, 
{LCM_SEND(2), {0x20,0x00}}, 
{LCM_SEND(2), {0x21,0x00}}, 
{LCM_SEND(2), {0x22,0x00}}, 
{LCM_SEND(2), {0x23,0xc0}}, 
{LCM_SEND(2), {0x24,0x30}}, 
{LCM_SEND(2), {0x25,0x00}}, 
{LCM_SEND(2), {0x26,0x00}}, 
{LCM_SEND(2), {0x27,0x03}}, 
{LCM_SEND(2), {0x30,0x01}}, 
{LCM_SEND(2), {0x31,0x23}}, 
{LCM_SEND(2), {0x32,0x55}}, 
{LCM_SEND(2), {0x33,0x67}}, 
{LCM_SEND(2), {0x34,0x89}}, 
{LCM_SEND(2), {0x35,0xab}}, 
{LCM_SEND(2), {0x36,0x01}}, 
{LCM_SEND(2), {0x37,0x23}}, 
{LCM_SEND(2), {0x38,0x45}}, 
{LCM_SEND(2), {0x39,0x67}}, 
{LCM_SEND(2), {0x3a,0x44}}, 
{LCM_SEND(2), {0x3b,0x55}}, 
{LCM_SEND(2), {0x3c,0x66}},
{LCM_SEND(2), {0x3d,0x77}}, 
{LCM_SEND(2), {0x50,0x00}}, 
{LCM_SEND(2), {0x51,0x0d}}, 
{LCM_SEND(2), {0x52,0x0d}}, 
{LCM_SEND(2), {0x53,0x0c}}, 
{LCM_SEND(2), {0x54,0x0c}}, 
{LCM_SEND(2), {0x55,0x0f}}, 
{LCM_SEND(2), {0x56,0x0f}}, 
{LCM_SEND(2), {0x57,0x0e}}, 
{LCM_SEND(2), {0x58,0x0e}}, 
{LCM_SEND(2), {0x59,0x06}}, 
{LCM_SEND(2), {0x5a,0x07}}, 
{LCM_SEND(2), {0x5b,0x1f}}, 
{LCM_SEND(2), {0x5c,0x1f}}, 
{LCM_SEND(2), {0x5d,0x1f}}, 
{LCM_SEND(2), {0x5e,0x1f}}, 
{LCM_SEND(2), {0x5f,0x1f}}, 
{LCM_SEND(2), {0x60,0x1f}}, 
{LCM_SEND(2), {0x67,0x06}}, 
{LCM_SEND(2), {0x68,0x13}}, 
{LCM_SEND(2), {0x69,0x0f}}, 
{LCM_SEND(2), {0x6a,0x12}}, 
{LCM_SEND(2), {0x6b,0x0e}}, 
{LCM_SEND(2), {0x6c,0x11}}, 
{LCM_SEND(2), {0x6d,0x0d}}, 
{LCM_SEND(2), {0x6e,0x10}}, 
{LCM_SEND(2), {0x6f,0x0c}}, 
{LCM_SEND(2), {0x70,0x14}}, 
{LCM_SEND(2), {0x71,0x15}}, 
{LCM_SEND(2), {0x72,0x08}}, 
{LCM_SEND(2), {0x73,0x01}}, 
{LCM_SEND(2), {0x74,0x00}}, 
{LCM_SEND(2), {0x75,0x02}}, 
{LCM_SEND(2), {0x76,0x02}}, 
{LCM_SEND(2), {0x83,0x01}}, 
{LCM_SLEEP(10),},
{LCM_SEND(6), {4,0,0xFF,0x61,0x36,0x08}}, 
{LCM_SEND(2), {0x8B,0x42}}, 
{LCM_SEND(6), {4,0,0xFF,0x61,0x36,0x01}}, 
{LCM_SEND(2), {0x63,0x10}}, 
{LCM_SEND(6), {4,0,0xFF,0x61,0x36,0x01}}, 
{LCM_SEND(2), {0x31,0x19}}, 
{LCM_SEND(2), {0x36,0x00}}, 
{LCM_SEND(6), {4,0,0xFF,0x61,0x36,0x01}}, 
{LCM_SEND(2), {0x50,0xA9}}, 
{LCM_SEND(6), {4,0,0xFF,0x61,0x36,0x01}}, 
{LCM_SEND(2), {0x51,0xA4}},
{LCM_SEND(6), {4,0,0xFF,0x61,0x36,0x01}}, 
{LCM_SEND(2), {0x53,0xA0}}, 
{LCM_SEND(6), {4,0,0xFF,0x61,0x36,0x01}}, 
{LCM_SEND(2), {0xA0,0x2D}}, 
{LCM_SEND(2), {0xA1,0x3B }}, 
{LCM_SEND(2), {0xA2,0x45 }}, 
{LCM_SEND(2), {0xA3,0x13 }}, 
{LCM_SEND(2), {0xA4,0x14 }}, 
{LCM_SEND(2), {0xA5,0x2A }}, 
{LCM_SEND(2), {0xA6,0x1A }}, 
{LCM_SEND(2), {0xA7,0x1D }}, 
{LCM_SEND(2), {0xA8,0x9E }}, 
{LCM_SEND(2), {0xA9,0x1E }}, 
{LCM_SEND(2), {0xAA,0x29 }}, 
{LCM_SEND(2), {0xAB,0x81 }}, 
{LCM_SEND(2), {0xAC,0x1F }}, 
{LCM_SEND(2), {0xAD,0x20 }}, 
{LCM_SEND(2), {0xAE,0x55 }}, 
{LCM_SEND(2), {0xAF,0x2B }}, 
{LCM_SEND(2), {0xB0,0x30 }}, 
{LCM_SEND(2), {0xB1,0x48 }}, 
{LCM_SEND(2), {0xB2,0x4F }}, 
{LCM_SEND(2), {0xB3,0x0D}}, 
{LCM_SEND(6), {4,0,0xFF,0x61,0x36,0x01}}, 
{LCM_SEND(2), {0xC0,0x30}}, 
{LCM_SEND(2), {0xC1,0x3B}},
{LCM_SEND(2), {0xC2,0x45}},
{LCM_SEND(2), {0xC3,0x13}},
{LCM_SEND(2), {0xC4,0x14}},
{LCM_SEND(2), {0xC5,0x2A}},
{LCM_SEND(2), {0xC6,0x1A}},
{LCM_SEND(2), {0xC7,0x1D}},
{LCM_SEND(2), {0xC8,0x9E}}, 
{LCM_SEND(2), {0xC9,0x1E}}, 
{LCM_SEND(2), {0xCA,0x29}},
{LCM_SEND(2), {0xCB,0x81}},
{LCM_SEND(2), {0xCC,0x1F}},
{LCM_SEND(2), {0xCD,0x1F}}, 
{LCM_SEND(2), {0xCE,0x55}}, 
{LCM_SEND(2), {0xCF,0x2B}},
{LCM_SEND(2), {0xD0,0x30}}, 
{LCM_SEND(2), {0xD1,0x48}},
{LCM_SEND(2), {0xD2,0x4F}},
{LCM_SEND(2), {0xD3,0x15}}, 
{LCM_SEND(6), {4,0,0xFF,0x61,0x36,0x08}}, 
{LCM_SEND(2), {0xAB,0x24}}, 
{LCM_SEND(6), {4,0,0xFF,0x61,0x36,0x00}}, 
//{LCM_SEND(2), {0x35,0x00}},
{LCM_SEND(2),{0x11,0x00}},
{LCM_SLEEP(120),},
{LCM_SEND(2),{0x29,0x00}},
};

static LCM_Init_Code disp_on =  {LCM_SEND(1), {0x29}};

static LCM_Init_Code sleep_in =  {LCM_SEND(1), {0x10}};

static LCM_Init_Code sleep_out =  {LCM_SEND(1), {0x11}};



static int32_t jd9365_mipi_init(struct panel_spec *self)
{
	LCD_PRINT("[%s][%d] jd9365_mipi_init Enter!\n",__FUNCTION__,__LINE__);
	int32_t i;
	LCM_Init_Code *init = init_data;
	unsigned int tag;

	mipi_set_cmd_mode_t mipi_set_cmd_mode = self->info.mipi->ops->mipi_set_cmd_mode;
	mipi_dcs_write_t mipi_dcs_write = self->info.mipi->ops->mipi_dcs_write;
	mipi_eotp_set_t mipi_eotp_set = self->info.mipi->ops->mipi_eotp_set;
	mipi_set_lp_mode_t mipi_set_lp_mode = self->info.mipi->ops->mipi_set_lp_mode;
	mipi_set_hs_mode_t mipi_set_hs_mode = self->info.mipi->ops->mipi_set_hs_mode;
	
	LCD_PRINT("[%s] Lancewu lcd_jd9365_init\n",__FUNCTION__);

	//jd9365_mipi_set_lcmvdd();
	
	udelay(2*1000);
	mipi_set_cmd_mode();
	mipi_eotp_set(1,0);

	for(i = 0; i < ARRAY_SIZE(init_data); i++){
		tag = (init->tag >>24);
		if(tag & LCM_TAG_SEND){
			mipi_dcs_write(init->data, (init->tag & LCM_TAG_MASK));
			udelay(20);
		}else if(tag & LCM_TAG_SLEEP){
			mdelay((init->tag & LCM_TAG_MASK));
		}
		init++;
	}

	mipi_eotp_set(1,1);

	return 0;
}


static uint32_t jd9365_readid(struct panel_spec *self)
{
	/*Jessica TODO: need read id*/
	int32_t i = 0;
	uint32 j =0;
	//LCM_Force_Cmd_Code * rd_prepare = rd_prep_code;
	uint8_t read_data[3] = {0};
	int32_t read_rtn = 0;
	unsigned int tag = 0;
	mipi_set_cmd_mode_t mipi_set_cmd_mode = self->info.mipi->ops->mipi_set_cmd_mode;
	mipi_force_write_t mipi_force_write = self->info.mipi->ops->mipi_force_write;
	mipi_force_read_t mipi_force_read = self->info.mipi->ops->mipi_force_read;
        mipi_set_lp_mode_t mipi_set_lp_mode = self->info.mipi->ops->mipi_set_lp_mode;
        mipi_set_hs_mode_t mipi_set_hs_mode = self->info.mipi->ops->mipi_set_hs_mode;
	mipi_eotp_set_t mipi_eotp_set = self->info.mipi->ops->mipi_eotp_set;

    	return 0x6136;

	printk("uboot lcd_jd9365_mipi read id!\n");
	#if 0
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

		if((0x93 == read_data[0])){
			printk("lcd_jd9365_mipi read id success!\n");
			mipi_eotp_set(1,1);
			return 0x6136;
		}
	}
	mipi_eotp_set(1,1);
	return 0x0;
	#endif
}

static struct panel_operations lcd_jd9365_mipi_operations = {
	.panel_init = jd9365_mipi_init,
	.panel_readid = jd9365_readid,
};

static struct timing_rgb lcd_jd9365_mipi_timing = {
	.hfp = 100,  /* unit: pixel */
	.hbp = 64,
	.hsync = 16,
	.vfp = 16, /*unit: line*/
	.vbp = 15,
	.vsync = 2,
};

static struct info_mipi lcd_jd9365_mipi_info = {
	.work_mode  = SPRDFB_MIPI_MODE_VIDEO,
	.video_bus_width = 24, /*18,16*/
	.lan_number = 4,
	.phy_feq = 560*1000,
	.h_sync_pol = SPRDFB_POLARITY_POS,
	.v_sync_pol = SPRDFB_POLARITY_POS,
	.de_pol = SPRDFB_POLARITY_POS,
	.te_pol = SPRDFB_POLARITY_POS,
	.color_mode_pol = SPRDFB_POLARITY_NEG,
	.shut_down_pol = SPRDFB_POLARITY_NEG,
	.timing = &lcd_jd9365_mipi_timing,
	.ops = NULL,
};

struct panel_spec lcd_bi32ta8001_21h_mipi_spec = {
	.width = 800,
	.height = 1280,
//	.fps = 60,
	.type = LCD_MODE_DSI,
	.direction = LCD_DIRECT_NORMAL,
	.info = {
		.mipi = &lcd_jd9365_mipi_info
	},
	.ops = &lcd_jd9365_mipi_operations,
};
