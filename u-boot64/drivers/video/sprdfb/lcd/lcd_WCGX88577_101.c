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
{LCM_SEND(5),{3,0,0xE0,0xAB,0xBA}},
{LCM_SEND(5),{3,0,0xE1,0xBA,0xAB}},
{LCM_SEND(7),{5,0,0xB1,0x10,0x01,0x47,0xFF}},
{LCM_SEND(9),{7,0,0xB2,0x0C,0x14,0x04,0x50,0x50,0x14}},
{LCM_SEND(6),{4,0,0xB3,0x56,0xD3,0x00}},
{LCM_SEND(6),{4,0,0xB4,0x33,0x30,0x04}},//30 正扫  20反扫
{LCM_SEND(10),{8,0,0xB6,0xB0,0x00,0x00,0x10,0x00,0x10,0x00}},
{LCM_SEND(8),{6,0,0xB8,0x06,0x12,0x29,0x29,0x40}},//06 正扫  56 反扫
{LCM_SEND(41),{39,0,0xB9,0x7C,0x66,0x57,0x4C,0x49,0x3C,0x42,0x2E,0x48,0x47,0x48,0x66,0x54,0x5B,0x4D,0x4A,0x3D,0x27,0x06,0x7C,0x66,0x57,0x4C,0x49,0x3C,0x42,0x2E,0x48,0x47,0x48,0x66,0x54,0x5B,0x4D,0x4A,0x3D,0x27,0x06}},
{LCM_SEND(19),{17,0,0xC0,0x32,0x23,0x67,0x67,0x33,0x33,0x33,0x33,0x10,0x04,0x88,0x04,0x3F,0x00,0x00,0xC0}},
{LCM_SEND(13),{11,0,0xC1,0x13,0x14,0x02,0x08,0x10,0x04,0x7D,0x04,0x54,0x00}},
{LCM_SEND(15),{13,0,0xC2,0x37,0x09,0x08,0x89,0x88,0x21,0x22,0x21,0x44,0xBB,0x18,0x00}},
{LCM_SEND(25),{23,0,0xC3,0x89,0x64,0x24,0x07,0x1F,0x1E,0x02,0x16,0x14,0x02,0x12,0x10,0x02,0x0E,0x0C,0x04,0x02,0x02,0x02,0x02,0x02,0x02}},//正扫
{LCM_SEND(25),{23,0,0xC4,0x09,0x24,0x24,0x07,0x1F,0x1E,0x02,0x17,0x15,0x02,0x13,0x11,0x02,0x0F,0x0D,0x05,0x02,0x02,0x02,0x02,0x02,0x02}},//正扫
{LCM_SEND(5),{3,0,0xC6,0x22,0x22}},
{LCM_SEND(9),{7,0,0xC8,0x21,0x00,0x32,0x40,0x54,0x16}},
{LCM_SEND(5),{3,0,0xCA,0xCB,0x43}},
{LCM_SEND(11),{9,0,0xCD,0x0E,0x4F,0x4F,0x25,0x1E,0x6B,0x06,0xB3}},
{LCM_SEND(7),{5,0,0xD2,0xE3,0x2B,0x38,0x08}},
{LCM_SEND(14),{12,0,0xD4,0x00,0x01,0x00,0x0E,0x04,0x44,0x08,0x10,0x00,0x00,0x00}},
{LCM_SEND(11),{9,0,0xE6,0x80,0x09,0xFF,0xFF,0xFF,0xFF,0xFF,0xFF}},
{LCM_SEND(8),{6,0,0xF0,0x12,0x03,0x20,0x00,0xFF}},
{LCM_SEND(2),{0xF3,0x00}},
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
