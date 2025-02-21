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
{LCM_SEND(6), {4,0,0xFF,0x98,0x81,0x03}},
{LCM_SEND(2), {0X01,0X00}}, 
{LCM_SEND(2), {0X02,0X00}}, 
{LCM_SEND(2), {0X03,0X53}}, 
{LCM_SEND(2), {0X04,0XD3}}, 
{LCM_SEND(2), {0X05,0X00}}, 
{LCM_SEND(2), {0X06,0X0D}}, 
{LCM_SEND(2), {0X07,0X08}}, 
{LCM_SEND(2), {0X08,0X00}}, 
{LCM_SEND(2), {0X09,0X00}}, 
{LCM_SEND(2), {0X0a,0X00}}, 
{LCM_SEND(2), {0X0b,0X00}}, 
{LCM_SEND(2), {0X0c,0X00}},
{LCM_SEND(2), {0X0d,0X00}}, 
{LCM_SEND(2), {0X0e,0X00}}, 
{LCM_SEND(2), {0X0f,0X28}}, 
{LCM_SEND(2), {0X10,0X28}}, 
{LCM_SEND(2), {0X11,0X00}}, 
{LCM_SEND(2), {0X12,0X00}}, 
{LCM_SEND(2), {0X13,0X00}}, 
{LCM_SEND(2), {0X14,0X00}}, 
{LCM_SEND(2), {0X15,0X00}}, 
{LCM_SEND(2), {0X16,0X00}}, 
{LCM_SEND(2), {0X17,0X00}}, 
{LCM_SEND(2), {0X18,0X00}}, 
{LCM_SEND(2), {0X19,0X00}}, 
{LCM_SEND(2), {0X1a,0X00}}, 
{LCM_SEND(2), {0X1b,0X00}}, 
{LCM_SEND(2), {0X1c,0X00}}, 
{LCM_SEND(2), {0X1d,0X00}}, 
{LCM_SEND(2), {0X1e,0X40}}, 
{LCM_SEND(2), {0X1f,0X80}}, 
{LCM_SEND(2), {0X20,0X06}}, 
{LCM_SEND(2), {0X21,0X01}}, 
{LCM_SEND(2), {0X22,0X00}}, 
{LCM_SEND(2), {0X23,0X00}}, 
{LCM_SEND(2), {0X24,0X00}}, 
{LCM_SEND(2), {0X25,0X00}}, 
{LCM_SEND(2), {0X26,0X00}}, 
{LCM_SEND(2), {0X27,0X00}}, 
{LCM_SEND(2), {0X28,0X33}}, 
{LCM_SEND(2), {0X29,0X33}}, 
{LCM_SEND(2), {0X2a,0X00}}, 
{LCM_SEND(2), {0X2b,0X00}}, 
{LCM_SEND(2), {0X2c,0X00}}, 
{LCM_SEND(2), {0X2d,0X00}}, 
{LCM_SEND(2), {0X2e,0X00}}, 
{LCM_SEND(2), {0X2f,0X00}}, 
{LCM_SEND(2), {0X30,0X00}}, 
{LCM_SEND(2), {0X31,0X00}}, 
{LCM_SEND(2), {0X32,0X00}}, 
{LCM_SEND(2), {0X33,0X00}}, 
{LCM_SEND(2), {0X34,0X03}},
{LCM_SEND(2), {0X35,0X00}}, 
{LCM_SEND(2), {0X36,0X00}}, 
{LCM_SEND(2), {0X37,0X00}}, 
{LCM_SEND(2), {0X38,0X96}}, 
{LCM_SEND(2), {0X39,0X00}}, 
{LCM_SEND(2), {0X3a,0X00}}, 
{LCM_SEND(2), {0X3b,0X00}}, 
{LCM_SEND(2), {0X3c,0X00}}, 
{LCM_SEND(2), {0X3d,0X00}}, 
{LCM_SEND(2), {0X3e,0X00}}, 
{LCM_SEND(2), {0X3f,0X00}}, 
{LCM_SEND(2), {0X40,0X00}}, 
{LCM_SEND(2), {0X41,0X00}}, 
{LCM_SEND(2), {0X42,0X00}}, 
{LCM_SEND(2), {0X43,0X00}}, 
{LCM_SEND(2), {0X44,0X00}}, 
{LCM_SEND(2), {0X50,0X00}}, 
{LCM_SEND(2), {0X51,0X23}}, 
{LCM_SEND(2), {0X52,0X45}}, 
{LCM_SEND(2), {0X53,0X67}}, 
{LCM_SEND(2), {0X54,0X89}}, 
{LCM_SEND(2), {0X55,0XAB}}, 
{LCM_SEND(2), {0X56,0X01}}, 
{LCM_SEND(2), {0X57,0X23}}, 
{LCM_SEND(2), {0X58,0X45}}, 
{LCM_SEND(2), {0X59,0X67}}, 
{LCM_SEND(2), {0X5a,0X89}}, 
{LCM_SEND(2), {0X5b,0XAB}}, 
{LCM_SEND(2), {0X5c,0XCD}}, 
{LCM_SEND(2), {0X5d,0XEF}}, 
{LCM_SEND(2), {0X5e,0X00}}, 
{LCM_SEND(2), {0X5f,0X08}}, 
{LCM_SEND(2), {0X60,0X08}}, 
{LCM_SEND(2), {0X61,0X06}}, 
{LCM_SEND(2), {0X62,0X06}}, 
{LCM_SEND(2), {0X63,0X01}}, 
{LCM_SEND(2), {0X64,0X01}}, 
{LCM_SEND(2), {0X65,0X00}}, 
{LCM_SEND(2), {0X66,0X00}}, 
{LCM_SEND(2), {0X67,0X02}}, 
{LCM_SEND(2), {0X68,0X15}}, 
{LCM_SEND(2), {0X69,0X15}}, 
{LCM_SEND(2), {0X6a,0X14}}, 
{LCM_SEND(2), {0X6b,0X14}}, 
{LCM_SEND(2), {0X6c,0X0D}}, 
{LCM_SEND(2), {0X6d,0X0D}}, 
{LCM_SEND(2), {0X6e,0X0C}}, 
{LCM_SEND(2), {0X6f,0X0C}},
{LCM_SEND(2), {0X70,0X0F}}, 
{LCM_SEND(2), {0X71,0X0F}}, 
{LCM_SEND(2), {0X72,0X0E}}, 
{LCM_SEND(2), {0X73,0X0E}}, 
{LCM_SEND(2), {0X74,0X02}}, 
{LCM_SEND(2), {0X75,0X08}}, 
{LCM_SEND(2), {0X76,0X08}}, 
{LCM_SEND(2), {0X77,0X06}}, 
{LCM_SEND(2), {0X78,0X06}}, 
{LCM_SEND(2), {0X79,0X01}}, 
{LCM_SEND(2), {0X7a,0X01}}, 
{LCM_SEND(2), {0X7b,0X00}}, 
{LCM_SEND(2), {0X7c,0X00}}, 
{LCM_SEND(2), {0X7d,0X02}}, 
{LCM_SEND(2), {0X7e,0X15}}, 
{LCM_SEND(2), {0X7f,0X15}}, 
{LCM_SEND(2), {0X80,0X14}}, 
{LCM_SEND(2), {0X81,0X14}}, 
{LCM_SEND(2), {0X82,0X0D}}, 
{LCM_SEND(2), {0X83,0X0D}}, 
{LCM_SEND(2), {0X84,0X0C}}, 
{LCM_SEND(2), {0X85,0X0C}}, 
{LCM_SEND(2), {0X86,0X0F}}, 
{LCM_SEND(2), {0X87,0X0F}}, 
{LCM_SEND(2), {0X88,0X0E}}, 
{LCM_SEND(2), {0X89,0X0E}}, 
{LCM_SEND(2), {0X8A,0X02}},
 
{LCM_SEND(6), {4,0,0xFF,0x98,0x81,0x04}},
{LCM_SEND(2), {0X6E,0X2B}},
{LCM_SEND(2), {0X6F,0X37}},
{LCM_SEND(2), {0X3A,0XA4}},
{LCM_SEND(2), {0X8D,0X1A}},
{LCM_SEND(2), {0X87,0XBA}},
{LCM_SEND(2), {0XB2,0XD1}},
{LCM_SEND(2), {0X88,0X0B}}, 
{LCM_SEND(2), {0X38,0X01}}, 
{LCM_SEND(2), {0X39,0X00}}, 
{LCM_SEND(2), {0XB5,0X07}}, 
{LCM_SEND(2), {0X31,0X75}}, 
{LCM_SEND(2), {0X3B,0X98}}, 

{LCM_SEND(6), {4,0,0xFF,0x98,0x81,0x01}}, 
{LCM_SEND(2), {0X22,0X0A}},
{LCM_SEND(2), {0X31,0X00}},
{LCM_SEND(2), {0X53,0X40}},
{LCM_SEND(2), {0X55,0X40}}, 
{LCM_SEND(2), {0X50,0X99}}, 
{LCM_SEND(2), {0X51,0X94}},
{LCM_SEND(2), {0X60,0X10}}, 
{LCM_SEND(2), {0X62,0X20}},

{LCM_SEND(2), {0XA0,0X00}},
{LCM_SEND(2), {0XA1,0X00}},
{LCM_SEND(2), {0XA2,0X15}},
{LCM_SEND(2), {0XA3,0X14}},
{LCM_SEND(2), {0XA4,0X1B}},
{LCM_SEND(2), {0XA5,0X2F}},
{LCM_SEND(2), {0XA6,0X25}},
{LCM_SEND(2), {0XA7,0X24}},
{LCM_SEND(2), {0XA8,0X80}},
{LCM_SEND(2), {0XA9,0X1F}},
{LCM_SEND(2), {0XAA,0X2C}},
{LCM_SEND(2), {0XAB,0X6C}},
{LCM_SEND(2), {0XAC,0X16}},
{LCM_SEND(2), {0XAD,0X14}},
{LCM_SEND(2), {0XAE,0X4D}},
{LCM_SEND(2), {0XAF,0X20}},
{LCM_SEND(2), {0XB0,0X29}},
{LCM_SEND(2), {0XB1,0X4F}},
{LCM_SEND(2), {0XB2,0X5F}},
{LCM_SEND(2), {0XB3,0X23}},

{LCM_SEND(2), {0XC0,0X00}},
{LCM_SEND(2), {0XC1,0X2E}},
{LCM_SEND(2), {0XC2,0X3B}},
{LCM_SEND(2), {0XC3,0X15}},
{LCM_SEND(2), {0XC4,0X16}},
{LCM_SEND(2), {0XC5,0X28}},
{LCM_SEND(2), {0XC6,0X1A}},
{LCM_SEND(2), {0XC7,0X1C}},
{LCM_SEND(2), {0XC8,0XA7}},
{LCM_SEND(2), {0XC9,0X1B}},
{LCM_SEND(2), {0XCA,0X28}},
{LCM_SEND(2), {0XCB,0X92}},
{LCM_SEND(2), {0XCC,0X1F}},
{LCM_SEND(2), {0XCD,0X1C}},
{LCM_SEND(2), {0XCE,0X4B}},
{LCM_SEND(2), {0XCF,0X1F}},
{LCM_SEND(2), {0XD0,0X28}},
{LCM_SEND(2), {0XD1,0X4E}},
{LCM_SEND(2), {0XD2,0X5C}},
{LCM_SEND(2), {0XD3,0X23}},

{LCM_SEND(6), {4,0,0xFF,0x98,0x81,0x00}},
//{LCM_SEND(2), {0x35,0x00}},
{LCM_SEND(2),{0x11,0x00}},
{LCM_SLEEP(120),},
{LCM_SEND(2),{0x29,0x00}},
};

static LCM_Init_Code disp_on =  {LCM_SEND(1), {0x29}};

static LCM_Init_Code sleep_in =  {LCM_SEND(1), {0x10}};

static LCM_Init_Code sleep_out =  {LCM_SEND(1), {0x11}};

static LCM_Force_Cmd_Code rd_prep_code[]={
        {0x39, {LCM_SEND(6), {0x6, 0, 0xFF, 0x98, 0x81, 0x01}}},
        {0x37, {LCM_SEND(2), {0x3, 0}}},
};

static int32_t nt35521s_mipi_init(struct panel_spec *self)
{
	int32_t i;
	LCM_Init_Code *init = init_data;
	unsigned int tag;

	mipi_set_cmd_mode_t mipi_set_cmd_mode = self->info.mipi->ops->mipi_set_cmd_mode;
	mipi_dcs_write_t mipi_dcs_write = self->info.mipi->ops->mipi_dcs_write;
	mipi_eotp_set_t mipi_eotp_set = self->info.mipi->ops->mipi_eotp_set;
	mipi_set_lp_mode_t mipi_set_lp_mode = self->info.mipi->ops->mipi_set_lp_mode;
	mipi_set_hs_mode_t mipi_set_hs_mode = self->info.mipi->ops->mipi_set_hs_mode;
	LCD_PRINT("nt35521s_init\n");

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

static uint32_t nt35521s_readid(struct panel_spec *self)
{
#if 1
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
        mipi_set_lp_mode_t mipi_set_lp_mode = self->info.mipi->ops->mipi_set_lp_mode;
        mipi_set_hs_mode_t mipi_set_hs_mode = self->info.mipi->ops->mipi_set_hs_mode;
	mipi_eotp_set_t mipi_eotp_set = self->info.mipi->ops->mipi_eotp_set;


        printf("lcd_ili9881c_mipi read id!\n");


        mipi_set_cmd_mode();
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
                mipi_eotp_set(0,0);
                read_rtn = mipi_force_read(0x00, 1,(uint8_t *)&read_data[0]);
                read_rtn = mipi_force_read(0x01, 1,(uint8_t *)&read_data[1]);
			mipi_eotp_set(1,1);
                printf("lcd_ili9881c_mipi read id 0x00 value is 0x%x, 0x%x, 0x%x!\n", read_data[0], read_data[1], read_data[2]);

                if((0x98 == read_data[0])&&(0x81 == read_data[1])){
                        LCD_PRINT("lcd_ili9881c_mipi read id success!\n");
                        return 0x9881;
		}
	}
	mipi_eotp_set(1,1);
	return 0x0;
	#endif
}

static struct panel_operations lcd_nt35521s_mipi_operations = {
	.panel_init = nt35521s_mipi_init,
	.panel_readid = nt35521s_readid,
};

static struct timing_rgb lcd_nt35521s_mipi_timing = {
	.hfp = 100,  /* unit: pixel */
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
	.phy_feq = 560*1000,
	.h_sync_pol = SPRDFB_POLARITY_POS,
	.v_sync_pol = SPRDFB_POLARITY_POS,
	.de_pol = SPRDFB_POLARITY_POS,
	.te_pol = SPRDFB_POLARITY_POS,
	.color_mode_pol = SPRDFB_POLARITY_NEG,
	.shut_down_pol = SPRDFB_POLARITY_NEG,
	.timing = &lcd_nt35521s_mipi_timing,
	.ops = NULL,
};

struct panel_spec lcd_ili9881c_sq101ab4ii312_mipi_spec = {
	.width = 800,
	.height = 1280,
//	.fps = 60,
	.type = LCD_MODE_DSI,
	.direction = LCD_DIRECT_NORMAL,
	.info = {
		.mipi = &lcd_nt35521s_mipi_info
	},
	.ops = &lcd_nt35521s_mipi_operations,
};
