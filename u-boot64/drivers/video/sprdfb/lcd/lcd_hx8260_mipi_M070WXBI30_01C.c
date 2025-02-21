/* drivers/video/sc8825/lcd_hx8260_mipi.c
 *
 * Support for lcd_hx8260 mipi LCD device
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
 {LCM_SEND(2),{0xB0,0x00}},
 {LCM_SEND(2),{0xB1,0x5b}},
 {LCM_SEND(2),{0xb3,0x18}},
 {LCM_SEND(2),{0xbb,0xeA}},//E8-4LANE  EA--3LANE
 {LCM_SEND(2),{0xb0,0x03}}, 
 {LCM_SEND(2),{0xb2,0x00}},		 		
 {LCM_SLEEP(100)},

 {LCM_SEND(2),{0xc3,0x02}},
 {LCM_SEND(2),{0xc4,0x03}},
 {LCM_SEND(2),{0xc5,0x02}},
 {LCM_SEND(2),{0xc6,0x01}},
 {LCM_SEND(2),{0xca,0x00}},
 		
 {LCM_SEND(2),{0xb0,0x02}},
 {LCM_SEND(2),{0xb1,0x06}},
 {LCM_SEND(2),{0xb2,0x06}},
 {LCM_SEND(2),{0xb3,0x05}},
 {LCM_SEND(2),{0xb4,0x05}},
 {LCM_SEND(2),{0xb5,0x08}},
 {LCM_SEND(2),{0xb6,0x08}},
 {LCM_SEND(2),{0xb7,0x07}},
 {LCM_SEND(2),{0xb8,0x07}},
 {LCM_SEND(2),{0xb9,0x01}},
 {LCM_SEND(2),{0xba,0x00}},
 {LCM_SEND(2),{0xbb,0x00}},
 {LCM_SEND(2),{0xbc,0x00}},
 {LCM_SEND(2),{0xbd,0x00}},
 {LCM_SEND(2),{0xbe,0x00}},
 {LCM_SEND(2),{0xbf,0x00}},
 		
 {LCM_SEND(2),{0xc0,0x02}},
 {LCM_SEND(2),{0xc1,0x00}},
 {LCM_SEND(2),{0xc2,0x00}},
 {LCM_SEND(2),{0xc3,0x00}},
 {LCM_SEND(2),{0xc4,0x00}},
 {LCM_SEND(2),{0xc5,0x00}},
 {LCM_SEND(2),{0xc6,0x00}},
 {LCM_SEND(2),{0xc7,0x06}},
 {LCM_SEND(2),{0xc8,0x06}},
 {LCM_SEND(2),{0xc9,0x05}},
 {LCM_SEND(2),{0xca,0x05}},
 {LCM_SEND(2),{0xcb,0x08}},
 {LCM_SEND(2),{0xcc,0x08}},
 {LCM_SEND(2),{0xcd,0x07}},
 {LCM_SEND(2),{0xce,0x07}},
 {LCM_SEND(2),{0xcf,0x01}},
 		
 {LCM_SEND(2),{0xd0,0x00}},
 {LCM_SEND(2),{0xd1,0x00}},
 {LCM_SEND(2),{0xd2,0x00}},
 {LCM_SEND(2),{0xd3,0x00}},
 {LCM_SEND(2),{0xd4,0x00}},
 {LCM_SEND(2),{0xd5,0x00}},
 {LCM_SEND(2),{0xd6,0x02}},
 {LCM_SEND(2),{0xd7,0x00}},
 {LCM_SEND(2),{0xd8,0x00}},
 {LCM_SEND(2),{0xd9,0x00}},
 {LCM_SEND(2),{0xda,0x00}},
 {LCM_SEND(2),{0xdb,0x00}},
 {LCM_SEND(2),{0xdc,0x00}},

 {LCM_SEND(1), {0x11}},//SLP OUT
 {LCM_SLEEP(120)},
 {LCM_SEND(1), {0x29}},//DISP ON  
 {LCM_SLEEP(100)},
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

static int32_t hx8260_mipi_init(struct panel_spec *self)
{
	LCD_PRINT("[%s][%d] hx8260_mipi_init Enter!\n",__FUNCTION__,__LINE__);
	int32_t i;
	LCM_Init_Code *init = init_data;
	unsigned int tag;
	

	mipi_set_cmd_mode_t mipi_set_cmd_mode = self->info.mipi->ops->mipi_set_cmd_mode;
	mipi_gen_write_t mipi_gen_write = self->info.mipi->ops->mipi_gen_write;
	mipi_eotp_set_t mipi_eotp_set = self->info.mipi->ops->mipi_eotp_set;
	mipi_set_lp_mode_t mipi_set_lp_mode = self->info.mipi->ops->mipi_set_lp_mode;
	mipi_set_hs_mode_t mipi_set_hs_mode = self->info.mipi->ops->mipi_set_hs_mode;
	
	LCD_PRINT("[%s] Lancewu lcd_hx8260_init\n",__FUNCTION__);

	//hx8260_mipi_set_lcmvdd();
	
	udelay(2*1000);
	mipi_set_cmd_mode();
	mipi_eotp_set(1,0);

	LCD_PRINT("lcd_hx8260 data size: %d\n",ARRAY_SIZE(init_data));

	for(i = 0; i < ARRAY_SIZE(init_data); i++)
	{
		tag = (init->tag >>24);
		if(tag & LCM_TAG_SEND)
		{
			LCD_PRINT("\n init->data :,0x%x\n",init->data[i]);
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


static uint32_t hx8260_readid(struct panel_spec *self)
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

    	return 0x8260;

	printk("uboot lcd_hx8260_mipi read id!\n");
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
		//printk("lcd_hx8260_mipi read id 0x04 value is,0x%x!\n", read_data[0]);
		printk("lcd_hx8260_mipi read id 0x04 value is,0x%x,,0x%x,,0x%x!\n", read_data[0], read_data[1], read_data[2]);

		if((0x93 == read_data[0])){
			printk("lcd_hx8260_mipi read id success!\n");
			return 0x8260;
		}
	}

	mdelay(5);
	mipi_set_hs_mode();
	return 0;
	#endif
}

static struct panel_operations lcd_hx8260_mipi_operations = {
	.panel_init = hx8260_mipi_init,
	.panel_readid = hx8260_readid,
};


static struct timing_rgb lcd_hx8260_mipi_timing = {
	.hfp = 140,  
	.hbp = 140,
	.hsync = 4,
	.vfp = 4,
	.vbp = 6,
	.vsync = 2,
};								
		
static struct info_mipi lcd_hx8260_mipi_info = {
	.work_mode  = SPRDFB_MIPI_MODE_VIDEO,
	.video_bus_width = 24, 
	.lan_number = 3,
	.phy_feq = 304*1000,
	.h_sync_pol = SPRDFB_POLARITY_POS,
	.v_sync_pol = SPRDFB_POLARITY_POS,
	.de_pol = SPRDFB_POLARITY_POS,	
	.te_pol = SPRDFB_POLARITY_POS,
	.color_mode_pol = SPRDFB_POLARITY_NEG,
	.shut_down_pol = SPRDFB_POLARITY_NEG,
	.timing = &lcd_hx8260_mipi_timing,
	.ops = NULL,
};




struct panel_spec lcd_hx8260_mipi_spec = {


	.width = 800,    
	.height = 1280,
	.fps = 60,
	.type = LCD_MODE_DSI,
	.direction = LCD_DIRECT_NORMAL,
	.info = {
		.mipi = &lcd_hx8260_mipi_info
	},
	.ops = &lcd_hx8260_mipi_operations,
};

