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

#define MAX_DATA   64

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
//Page0
{LCM_SEND(6),{4,0,0xBF,0x93,0x61,0xF4}},    // SET password, 93/61/F4 can access all page, 5A/A5/FF access Page0 only, for ESD set to 00/00/00		
{LCM_SLEEP(1)},

{LCM_SEND(8),{6,0,0xB0,0x00,0x01,0x23,0x45,0x66}},    //Set Power Sequency Control
{LCM_SLEEP(1)},

{LCM_SEND(14),{12,0,0xC7,0x0F,0x01,0x69,0x0A,0x0A,0x28,0x19,0x00,0x32,0x32,0x22}},     //Set power control //DCDCM[3:0]=1111, AVEE, VCL, VGH, VGL for CP
{LCM_SLEEP(1)},

{LCM_SEND(7),{5,0,0xBB,0x00,0x08,0xD3,0x54}},
{LCM_SLEEP(1)},
 
{LCM_SEND(2),{0xBE,0x01}},   //Page 1
{LCM_SLEEP(1)},
 
{LCM_SEND(11),{9,0,0xCC,0x34,0x00,0x38,0x07,0x00,0x91,0x02,0xD0}},  //0x50
{LCM_SLEEP(1)},
 
{LCM_SEND(2),{0xC4,0x05}},    //OSC_L
{LCM_SLEEP(1)},
 
{LCM_SEND(2),{0xC2,0x38}},
{LCM_SLEEP(1)},
 
{LCM_SEND(2),{0xB4,0x01}},    //VCOM_S=01
{LCM_SLEEP(1)},
 
{LCM_SEND(2),{0xBE,0x00}},   //Page 0 
{LCM_SLEEP(1)},
 
{LCM_SEND(7),{5,0,0xBA,0x3E,0x2D,0x2D,0x0E}}, //PA[6]=0, PA[5]=0, PA[4]=0, PA[0]=0
{LCM_SLEEP(1)},
 
{LCM_SEND(2),{0xC1,0x20}},     //PA[6]=1, PA[5:4]=01
{LCM_SLEEP(1)},
 
{LCM_SEND(11),{9,0,0xC3,0x00,0x05,0x0C,0x18,0xA0,0x0C,0xA0,0x4E}},
{LCM_SLEEP(1)},
 
{LCM_SEND(15),{13,0,0xC4,0x06,0xA0,0x91,0x04,0x0C,0x00,0x00,0x00,0x00,0x00,0x00,0x00}},	//Option1[7:0]
{LCM_SLEEP(1)},
 
{LCM_SEND(41),{39,0,0xC8,0x7C,0x6B,0x5A,0x4F,0x4C,0x3D,0x3F,0x2E,0x48,0x47,0x47,0x66,0x55,0x5D,0x4F,0x4A,0x3E,0x26,0x00,0x7C,0x6B,0x5A,0x4F,0x4C,0x3D,0x3F,0x2E,0x48,0x47,0x47,0x66,0x55,0x5D,0x4F,0x4A,0x3E,0x26,0x00}},
{LCM_SLEEP(1)},

{LCM_SEND(6),{4,0,0xCC,0x13,0xB6,0x00}},     //SET DSI	//DSI_INIT0[1:0]=11 =4lane
{LCM_SLEEP(1)},
{LCM_SEND(25),{23,0,0xD5,0x05,0x05,0x04,0x04,0x07,0x07,0x06,0x06,0x00,0x1F,0x1F,0x1F,0x1F,0x1F,0x1F,0x01,0x1F,0x1F,0x1F,0x1F,0x1F,0x1F}},     //Set GIP L mapping
{LCM_SLEEP(1)},
{LCM_SEND(25),{23,0,0xD6,0x05,0x05,0x04,0x04,0x07,0x07,0x06,0x06,0x00,0x1F,0x1F,0x1F,0x1F,0x1F,0x1F,0x01,0x1F,0x1F,0x1F,0x1F,0x1F,0x1F}},    //Set GIP R mapping
{LCM_SLEEP(1)},

{LCM_SEND(46), {44,0,0xD9,0x10,0x00,0x00,0x10,0x0E,0x20,0x00,0x03,0x80,0x31,0x10,0x01,0xC5,0x00,0x00,0x00,0x06,0x86,0x00,0x00,0x00,0x00,0x00,0x03,0x86,0x07,0x00,0x55,0x00,0x00,0x05,0x05,0x00,0x03,0x86,0x00,0x08,0x08,0x08,0x08,0x08,0x08,0x04}},
{LCM_SLEEP(1)},

{LCM_SEND(7), {5,0,0xD0,0x00,0x80,0x00,0x9A}},
{LCM_SLEEP(1)},

{LCM_SEND(9), {7,0,0xB8,0x00,0xB1,0x00,0x00,0xB1,0x00}},
{LCM_SLEEP(1)},

{LCM_SEND(1), {0x35}},

//SLP OUT
{LCM_SEND(1), {0x11}},// SLPOUT
{LCM_SLEEP(100)},


//DISP ON
{LCM_SEND(1), {0x29}},// DSPON
{LCM_SLEEP(10)},
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
	mipi_dcs_write_t mipi_dcs_write = self->info.mipi->ops->mipi_dcs_write;
	for(i = 0; i < size; i++)
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

}
static int32_t s6d7aa0x02_mipi_init(struct panel_spec *self)
{
	LCD_PRINT("[%s][%d] s6d7aa0x02_mipi_init Enter!\n",__FUNCTION__,__LINE__);
	//int32_t i;
	int32_t size = 0;
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

	size=ARRAY_SIZE(init_data);

	lcd_code_init(self,init,size);

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
	.hfp = 10,  
	.hbp = 16,
	.hsync = 4,
	.vfp = 3,
	.vbp = 14,
	.vsync = 3,
};								
		
static struct info_mipi lcd_s6d7aa0x02_mipi_info = {
	.work_mode  = SPRDFB_MIPI_MODE_VIDEO,
	.video_bus_width = 24, 
	.lan_number = 4,
	.phy_feq				= 500*1000,
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

