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
{LCM_SEND(6),{4,0,0xB9,0xFF,0x83,0x94}},

//Set MIPI
{LCM_SEND(5),{3,0,0xBA,0x73,0x83}},

//Set Power HX5186 Mode and External Power Mode
{LCM_SEND(18),{16,0,0xB1,0x6C,0x0C,0x0D,0x25,
					0x04,0x11,0xF1,0x80,0xE7,
					0x5A,0x23,0x80,0xC0,0xD2,
					0x58}},

//Set Display
{LCM_SEND(15),{13,0,0xB2,0x85,0x44,0x0F,0x09,
				    0x24,0x1C,0x08,0x08,0x1C,
					0x4D,0x00,0x00}},

//Set CYC
{LCM_SEND(15),{13,0,0xB4,0x00,0xFF,0x59,0x5A,
					0x59,0x5A,0x59,0x5A,0x01,
					0x70,0x01,0x70}},

//Set Power Option HX5186 Mode
{LCM_SEND(6),{4,0,0xBF,0x41,0x0E,0x01}},

//Set D3
{LCM_SEND(33),{31,0,0xD3,0x00,0x00,0x00,0x01,
					0x07,0x0C,0x0C,0x32,0x10,
					0x07,0x00,0x05,0x00,0x20,
					0x0A,0x05,0x09,0x00,0x32,
					0x10,0x08,0x00,0x21,0x21,
					0x08,0x07,0x23,0x0D,0x07,
					0x47}},

//Se GIP
{LCM_SEND(47),{45,0,0xD5,0x01,0x00,0x01,0x00,
					0x03,0x02,0x03,0x02,0x21,
					0x20,0x21,0x20,0x18,0x02,
					0x18,0x02,0x18,0x18,0x18,
					0x18,0x18,0x18,0x18,0x18,
					0x18,0x18,0x18,0x18,0x18,
					0x18,0x18,0x18,0x18,0x18,
					0x18,0x18,0x18,0x18,0x18,
					0x18,0x18,0x18,0x18,0x18}},

//Set Gamma
{LCM_SEND(45),{43,0,0xE0,0x00,0x00,0x02,0x28,
					0x2D,0x3D,0x0F,0x32,0x06,
					0x09,0x0C,0x17,0x0E,0x12,
					0x14,0x12,0x14,0x07,0x11,
					0x12,0x18,0x00,0x00,0x03,
					0x28,0x2C,0x3D,0x0F,0x32,
					0x06,0x09,0x0B,0x16,0x0F,
					0x11,0x14,0x13,0x13,0x07,
					0x11,0x11,0x17}},

//Set Panel
{LCM_SEND(2),{0xCC,0x09}},

//Set TCON Option
{LCM_SEND(7),{5,0,0xC7,0x00,0xC0,0x40,0xC0}},

//Set C0
{LCM_SEND(5),{3,0,0xC0,0x30,0x14}},
 
//Set BC
{LCM_SEND(2),{0xBC,0x07}},

//Set VCOM
{LCM_SEND(5),{3,0,0xB6,0x46,0x46}},
//{LCM_SEND(5),{3,0,0xB0,0x80,0x01}},



//Sleep Out
{LCM_SEND(1),{0x11}},
{LCM_SLEEP(120),}, 

//Set ECO
{LCM_SEND(5),{3,0,0xC6,0x3D,0x00}},

//Display ON
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
	{LCM_SEND(2),{0xBF,0x55}},
	{LCM_SEND(2),{0xBF,0xAA}},
	{LCM_SEND(2),{0xBF,0x66}},
	{LCM_SEND(2),{0xBF,0x01}},
	{LCM_SEND(2),{0xBC,0x04}},
	{LCM_SEND(2),{0xBD,0x93}},
	{LCM_SEND(2),{0xBE,0x00}},
};

static int32_t hx8279_mipi_init(struct panel_spec *self)
{
	LCD_PRINT("[%s][%d] hx8279_mipi_init Enter!\n",__FUNCTION__,__LINE__);
	int32_t i;
	LCM_Init_Code *init = init_data;
	unsigned int tag;
	

	mipi_set_cmd_mode_t mipi_set_cmd_mode = self->info.mipi->ops->mipi_set_cmd_mode;
	mipi_dcs_write_t mipi_dcs_write = self->info.mipi->ops->mipi_dcs_write;
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
	.hfp = 20,  
	.hbp = 30,
	.hsync = 24,
	.vfp = 6,
	.vbp = 8,
	.vsync = 2,
};								
		
static struct info_mipi lcd_hx8394d_mipi_info = {
	.work_mode  = SPRDFB_MIPI_MODE_VIDEO,
	.video_bus_width = 24, 
	.lan_number = 4,
	.phy_feq = 550*1000,
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

