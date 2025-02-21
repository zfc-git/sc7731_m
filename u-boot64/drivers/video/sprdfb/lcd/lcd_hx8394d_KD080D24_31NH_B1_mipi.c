/* drivers/video/sc8825/lcd_hx8394d_mipi.c
 *
 * Support for lcd_hx8394d mipi LCD device
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
{LCM_SEND(6),{4,0,0xB9,0xFF,0x83,0x94}},

{LCM_SEND(5),{3,0,0xBA,0x73,0x83}},

{LCM_SEND(18),{16,0,0xB1,0x6c,0x15,0x15,0x24,0xe4,0x11,0xf1,0x80,0xe4,0xD7,0x23,0x80,0xC0,0xD2,0x58}},

{LCM_SEND(14),{12,0,0xB2,0x00,0x64,0x10,0x07,0x20,0x1C,0x08,0x08,0x1C,0x4D,0x00}},

{LCM_SEND(6),{4,0,0xB4,0x00,0xFF,0x03,0x5a,0x03,0x5a,0x03,0x5a,0x01,0x6a,0x01,0x6a}},

{LCM_SEND(5),{3,0,0xB6,0x33,0x33}},

{LCM_SEND(2),{0xCC,0x09}},

{LCM_SEND(33),{31,0,0xD3,0x00,0x06,0x00,0x40,0x1a,0x08,0x00,0x32,0x10,0x07,0x00,0x07,0x54,0x15,0x0f,0x05,0x04,0x02,0x12,0x10,0x05,0x07,0x33,0x33,0x0b,0x0b,0x37,0x10,0x07,0x07}},  

{LCM_SEND(47),{45,0,0xD5,0x19,0x19,0x18,0x18,0x1a,0x1a,0x1b,0x1b,0x04,0x05,0x06,0x07,0x00,0x01,0x02,0x03,0x20,0x21,0x18,0x18,0x18,0x18,0x18,0x18,0x18,0x18,0x18,0x18,0x18,0x18,0x22,0x23,0x18,0x18,0x18,0x18,0x18,0x18,0x18,0x18,0x18,0x18,0x18,0x18}},

{LCM_SEND(47),{45,0,0xD6,0x18,0x18,0x19,0x19,0x1a,0x1a,0x1b,0x1b,0x03,0x02,0x01,0x00,0x07,0x06,0x05,0x04,0x23,0x22,0x18,0x18,0x18,0x18,0x18,0x18,0x18,0x18,0x18,0x18,0x18,0x18,0x21,0x20,0x18,0x18,0x18,0x18,0x18,0x18,0x18,0x18,0x18,0x18,0x18,0x18}},

{LCM_SEND(45),{43,0,0xE0,0x03,0x17,0x1C,0x2D,0x30,0x3B,0x27,0x40,0x08,0x0B,0x0D,0x18,0x0F,0x12,0x15,0x13,0x14,0x07,0x12,0x14,0x17,0x03,0x17,0x1C,0x2D,0x30,0x3B,0x27,0x40,0x08,0x0B,0x0D,0x18,0x0F,0x12,0x15,0x13,0x14,0x07,0x12,0x14,0x17}},

{LCM_SEND(2),{0xd2,0x55}}, 

{LCM_SEND(5),{3,0,0xC0,0x30,0x14}},

{LCM_SEND(6),{4,0,0xbf,0x41,0x0e,0x01}}, 

{LCM_SEND(7),{5,0,0xC7,0x00,0xc0,0x40,0xc0}},

{LCM_SEND(2),{0xdf,0x8e}},  

{LCM_SEND(1),{0x11}},	//Sleep Out
{LCM_SLEEP(250),},           	
{LCM_SEND(1),{0x29}},  // Display On
{LCM_SLEEP(50),},
};


static LCM_Init_Code disp_on =  {LCM_SEND(1), {0x29}};

static LCM_Init_Code sleep_in[]=  {
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

static void lcd_code_init(struct panel_spec *self,LCM_Init_Code *init,int32_t size)
{
	LCD_PRINT("[%s][%d] Enter! size =%d\n",__FUNCTION__,__LINE__,size);
	int32_t i;
	unsigned int tag;
	mipi_gen_write_t mipi_gen_write = self->info.mipi->ops->mipi_gen_write;
	for(i = 0; i < size; i++)
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

}

static int32_t hx8394d_mipi_init(struct panel_spec *self)
{
	int32_t i;
	LCM_Init_Code *init = init_data;
	unsigned int tag;

	mipi_set_cmd_mode_t mipi_set_cmd_mode = self->info.mipi->ops->mipi_set_cmd_mode;
	mipi_gen_write_t mipi_gen_write = self->info.mipi->ops->mipi_gen_write;

	LCD_PRINT("hx8394d_init\n");

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

static uint32_t hx8394d_readid(struct panel_spec *self)
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

	printk("uboot lcd_hx8394d_mipi read id!\n");
	return 0x8394;
}

static struct panel_operations lcd_hx8394d_mipi_operations = {
	.panel_init = hx8394d_mipi_init,
	.panel_readid = hx8394d_readid,
};


static struct timing_rgb lcd_hx8394d_mipi_timing = {
	.hfp = 57,  
	.hbp = 68,
	.hsync = 36,
	.vfp = 9,
	.vbp = 16,
	.vsync = 2,
};								
		
static struct info_mipi lcd_hx8394d_mipi_info = {
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
	.timing = &lcd_hx8394d_mipi_timing,
	.ops = NULL,
};




struct panel_spec lcd_hx8394d_mipi_spec = {
	.width = 800,    
	.height = 1280,
	.fps = 60,
	.type = LCD_MODE_DSI,
	.direction = LCD_DIRECT_NORMAL,
	.info = {
		.mipi = &lcd_hx8394d_mipi_info
	},
	.ops = &lcd_hx8394d_mipi_operations,
};

