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
//Set EXTC
{LCM_SEND(2),{0x83,0x00}},

{LCM_SEND(2),{0x84,0x00}},

{LCM_SEND(2),{0x85,0x05}},

{LCM_SEND(2),{0x86,0x08}},
 
{LCM_SEND(2),{0x8C,0x8E}},

{LCM_SEND(2),{0xC5,0x2B}},

{LCM_SEND(2),{0xC7,0x2B}},

{LCM_SEND(2),{0xFD,0x5B}},

{LCM_SEND(2),{0xFA,0x14}},

{LCM_SEND(2),{0x03,0x0F}},

{LCM_SEND(2),{0x33,0xAA}},

{LCM_SEND(2),{0x84,0x11}},
 
{LCM_SEND(2),{0xA9,0x4B}},

{LCM_SEND(1),{0x11}},
{LCM_SLEEP(120),}, 
{LCM_SEND(1),{0x29}},
{LCM_SLEEP(20),}, 

};


static LCM_Init_Code sleep_in[]=  {
    {LCM_SEND(2), {0x28,0x00}},
    {LCM_SLEEP(10)},
    {LCM_SEND(2), {0x10,0x00}},
    {LCM_SLEEP(120)},
};

static LCM_Init_Code sleep_out[] =  {                                                                       
{LCM_SLEEP(1)},
{LCM_SEND(1), {0x11}},//SLP OUT
{LCM_SLEEP(120)},
{LCM_SEND(1), {0x29}},//DISP ON
{LCM_SLEEP(50)},                                                                                                                                                                                          
//--- TE----//                                                                                      
//{LCM_SEND(2),{0x35,0x00}},
 
};

static int32_t hx8279_mipi_init(struct panel_spec *self)
{
	LCD_PRINT("[%s][%d] hx8279_mipi_init Enter!\n",__FUNCTION__,__LINE__);
	int32_t i;
	LCM_Init_Code *init = init_data;
	unsigned int tag;
	

	mipi_set_cmd_mode_t mipi_set_cmd_mode = self->info.mipi->ops->mipi_set_cmd_mode;
	mipi_gen_write_t mipi_gen_write = self->info.mipi->ops->mipi_gen_write;
	mipi_eotp_set_t mipi_eotp_set = self->info.mipi->ops->mipi_eotp_set;
	mipi_set_lp_mode_t mipi_set_lp_mode = self->info.mipi->ops->mipi_set_lp_mode;
	mipi_set_hs_mode_t mipi_set_hs_mode = self->info.mipi->ops->mipi_set_hs_mode;
	
	LCD_PRINT("[%s] Lancewu lcd_hx8279_init\n",__FUNCTION__);

	//hx8279_mipi_set_lcmvdd();
	
	udelay(2*1000);
	mipi_set_cmd_mode();
	mipi_eotp_set(1,0);

	LCD_PRINT("lcd_hx8279 data size: %d\n",ARRAY_SIZE(init_data));

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


static uint32_t hx8279_readid(struct panel_spec *self)
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
	.panel_init = hx8279_mipi_init,
	.panel_readid = hx8279_readid,
};


static struct timing_rgb lcd_hx8279_mipi_timing = {
	.hfp = 60,  
	.hbp = 60,
	.hsync = 1,
	.vfp = 20,
	.vbp = 40,
	.vsync = 1,
};								
		
static struct info_mipi lcd_hx8394d_mipi_info = {
	.work_mode  = SPRDFB_MIPI_MODE_VIDEO,
	.video_bus_width = 24, 
	.lan_number = 4,
	.phy_feq = 400*1000,
	.h_sync_pol = SPRDFB_POLARITY_POS,
	.v_sync_pol = SPRDFB_POLARITY_POS,
	.de_pol = SPRDFB_POLARITY_POS,	
	.te_pol = SPRDFB_POLARITY_POS,
	.color_mode_pol = SPRDFB_POLARITY_NEG,
	.shut_down_pol = SPRDFB_POLARITY_NEG,
	.timing = &lcd_hx8279_mipi_timing,
	.ops = NULL,
};




struct panel_spec lcd_hx8394d_mipi_spec = {
	.width = 600,    
	.height = 1024,
	.fps = 60,
	.type = LCD_MODE_DSI,
	.direction = LCD_DIRECT_NORMAL,
	.info = {
		.mipi = &lcd_hx8394d_mipi_info
	},
	.ops = &lcd_hx8394d_mipi_operations,
};

