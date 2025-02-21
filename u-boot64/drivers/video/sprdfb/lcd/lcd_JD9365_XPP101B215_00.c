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

{LCM_SEND(2),{0xE0,0x00}},
//--- PASSWORD  ----//
{LCM_SEND(2),{0xE1,0x93}},
{LCM_SEND(2),{0xE2,0x65}},
{LCM_SEND(2),{0xE3,0xF8}},

//Lane select by internal reg  4 lanes
{LCM_SEND(2),{0xE0,0x04}},
{LCM_SEND(2),{0x2D,0x03}},//defult 0x01

{LCM_SEND(2),{0xE0,0x00}},
{LCM_SEND(2),{0x80,0x03}}, //0x03:4lanes;   0x02:3lanes

//--- Sequence Ctrl  ----// 
//{LCM_SEND(2),{0x70,0x02}},	//DC0,DC1 
//{LCM_SEND(2),{0x71,0x23}},	//DC2,DC3
//{LCM_SEND(2),{0x72,0x06}},	//DC7

//--- Page1  ----//
{LCM_SEND(2),{0xE0,0x01}},
//Set VCOM     ?y����
{LCM_SEND(2),{0x00,0x00}},
{LCM_SEND(2),{0x01,0x70}},//0x66

//Set VCOM_Reverse ���䨦������3y
//{LCM_SEND(2),{0x03,0x00}},
//{LCM_SEND(2),{0x04,0x6D}},

//Set Gamma Power, VGMP,VGMN,VGSP,VGSN
{LCM_SEND(2),{0x17,0x00}},
{LCM_SEND(2),{0x18,0xBF}},//4.5V
{LCM_SEND(2),{0x19,0x00}},
{LCM_SEND(2),{0x1A,0x00}},
{LCM_SEND(2),{0x1B,0xBF}},  //VGMN=-4.5V
{LCM_SEND(2),{0x1C,0x00}},
               
//Set Gate Power
{LCM_SEND(2),{0x1F,0x3E}},     //VGH_R  = 15V                       
{LCM_SEND(2),{0x20,0x28}},     //VGL_R  = -11V                      
{LCM_SEND(2),{0x21,0x28}},     //VGL_R2 = -11V                      
{LCM_SEND(2),{0x22,0x0E}},     //PA[6]=0, PA[5]=0, PA[4]=0, PA[0]=0 

//SETPANEL
{LCM_SEND(2),{0x37,0x09}},	//SS=1,BGR=1
//SET RGBCYC
{LCM_SEND(2),{0x38,0x04}},	//JDT=100 column inversion
{LCM_SEND(2),{0x39,0x08}},	//RGB_N_EQ1, modify 20140806
{LCM_SEND(2),{0x3A,0x12}},	//RGB_N_EQ2, modify 20140806
{LCM_SEND(2),{0x3C,0x78}},	//SET EQ3 for TE_H
{LCM_SEND(2),{0x3D,0xFF}},	//SET CHGEN_ON, modify 20140806 
{LCM_SEND(2),{0x3E,0xFF}},	//SET CHGEN_OFF, modify 20140806 
{LCM_SEND(2),{0x3F,0x7F}},	//SET CHGEN_OFF2, modify 20140806

//Set TCON
{LCM_SEND(2),{0x40,0x06}},	//RSO=800 RGB
{LCM_SEND(2),{0x41,0xA0}},	//LN=640->1280 line
//--- power voltage  ----//
{LCM_SEND(2),{0x55,0x01}},	//DCDCM=0001, JD PWR_IC
{LCM_SEND(2),{0x56,0x01}},
{LCM_SEND(2),{0x57,0x69}},
{LCM_SEND(2),{0x58,0x0A}},
{LCM_SEND(2),{0x59,0x0A}},	//VCL = -2.5V
{LCM_SEND(2),{0x5A,0x29}},	//VGH = 15.2V
{LCM_SEND(2),{0x5B,0x15}},	//VGL = -11.2V

//--- Gamma  ----//
{LCM_SEND(2),{0x5D,0x7C}},              
{LCM_SEND(2),{0x5E,0x65}},      
{LCM_SEND(2),{0x5F,0x55}},    
{LCM_SEND(2),{0x60,0x49}},    
{LCM_SEND(2),{0x61,0x44}},    
{LCM_SEND(2),{0x62,0x35}},    
{LCM_SEND(2),{0x63,0x3A}},    
{LCM_SEND(2),{0x64,0x23}},    
{LCM_SEND(2),{0x65,0x3D}},    
{LCM_SEND(2),{0x66,0x3C}},    
{LCM_SEND(2),{0x67,0x3D}},    
{LCM_SEND(2),{0x68,0x5D}},    
{LCM_SEND(2),{0x69,0x4D}},    
{LCM_SEND(2),{0x6A,0x56}},    
{LCM_SEND(2),{0x6B,0x48}},    
{LCM_SEND(2),{0x6C,0x45}},    
{LCM_SEND(2),{0x6D,0x38}},    
{LCM_SEND(2),{0x6E,0x25}},    
{LCM_SEND(2),{0x6F,0x00}},    
{LCM_SEND(2),{0x70,0x7C}},    
{LCM_SEND(2),{0x71,0x65}},    
{LCM_SEND(2),{0x72,0x55}},    
{LCM_SEND(2),{0x73,0x49}},    
{LCM_SEND(2),{0x74,0x44}},    
{LCM_SEND(2),{0x75,0x35}},    
{LCM_SEND(2),{0x76,0x3A}},    
{LCM_SEND(2),{0x77,0x23}},    
{LCM_SEND(2),{0x78,0x3D}},    
{LCM_SEND(2),{0x79,0x3C}},    
{LCM_SEND(2),{0x7A,0x3D}},    
{LCM_SEND(2),{0x7B,0x5D}},    
{LCM_SEND(2),{0x7C,0x4D}},    
{LCM_SEND(2),{0x7D,0x56}},    
{LCM_SEND(2),{0x7E,0x48}},    
{LCM_SEND(2),{0x7F,0x45}},    
{LCM_SEND(2),{0x80,0x38}},    
{LCM_SEND(2),{0x81,0x25}},    
{LCM_SEND(2),{0x82,0x00}},    
                                 
//Page2, for GIP                                      
{LCM_SEND(2),{0xE0,0x02}},                                
//GIP_L Pin mapping ?y���� 00-2B                                  
{LCM_SEND(2),{0x00,0x1E}},//1  VDS                        
{LCM_SEND(2),{0x01,0x1E}},//2  VDS                        
{LCM_SEND(2),{0x02,0x41}},//3  STV2                       
{LCM_SEND(2),{0x03,0x41}},//4  STV2                       
{LCM_SEND(2),{0x04,0x43}},//5  STV4                       
{LCM_SEND(2),{0x05,0x43}},//6  STV4                       
{LCM_SEND(2),{0x06,0x1F}},//7  VSD                        
{LCM_SEND(2),{0x07,0x1F}},//8  VSD                        
{LCM_SEND(2),{0x08,0x1F}},//9  GCL                        
{LCM_SEND(2),{0x09,0x1F}},//10                            
{LCM_SEND(2),{0x0A,0x1E}},//11 GCH                        
{LCM_SEND(2),{0x0B,0x1E}},//12 GCH                        
{LCM_SEND(2),{0x0C,0x1F}},//13                            
{LCM_SEND(2),{0x0D,0x47}},//14 CLK8                       
{LCM_SEND(2),{0x0E,0x47}},//15 CLK8                       
{LCM_SEND(2),{0x0F,0x45}},//16 CLK6                       
{LCM_SEND(2),{0x10,0x45}},//17 CLK6                       
{LCM_SEND(2),{0x11,0x4B}},//18 CLK4                       
{LCM_SEND(2),{0x12,0x4B}},//19 CLK4                       
{LCM_SEND(2),{0x13,0x49}},//20 CLK2                       
{LCM_SEND(2),{0x14,0x49}},//21 CLK2                       
{LCM_SEND(2),{0x15,0x1F}},//22 VGL                        
                                                      
                                                      
//GIP_R Pin mapping                                   
{LCM_SEND(2),{0x16,0x1E}},//1  VDS                 
{LCM_SEND(2),{0x17,0x1E}},//2  VDS                
{LCM_SEND(2),{0x18,0x40}},//3  STV1               
{LCM_SEND(2),{0x19,0x40}},//4  STV1               
{LCM_SEND(2),{0x1A,0x42}},//5  STV3               
{LCM_SEND(2),{0x1B,0x42}},//6  STV3               
{LCM_SEND(2),{0x1C,0x1F}},//7  VSD                
{LCM_SEND(2),{0x1D,0x1F}},//8  VSD                
{LCM_SEND(2),{0x1E,0x1F}},//9  GCL                
{LCM_SEND(2),{0x1F,0x1f}},//10                    
{LCM_SEND(2),{0x20,0x1E}},//11 GCH                
{LCM_SEND(2),{0x21,0x1E}},//12 GCH                
{LCM_SEND(2),{0x22,0x1f}},//13                    
{LCM_SEND(2),{0x23,0x46}},//14 CLK7               
{LCM_SEND(2),{0x24,0x46}},//15 CLK7               
{LCM_SEND(2),{0x25,0x44}},//16 CLK5               
{LCM_SEND(2),{0x26,0x44}},//17 CLK5               
{LCM_SEND(2),{0x27,0x4A}},//18 CLK3               
{LCM_SEND(2),{0x28,0x4A}},//19 CLK3               
{LCM_SEND(2),{0x29,0x48}},//20 CLK1               
{LCM_SEND(2),{0x2A,0x48}},//21 CLK1               
{LCM_SEND(2),{0x2B,0x1f}},//22 VGL                                 
     
//GIP_L_GS Pin mapping �����a 2c-57����3y
/*
{LCM_SEND(2),{0x2C,0x1F}},//1  VDS 		0x1E
{LCM_SEND(2),{0x2D,0x1F}},//2  VDS          0x1E
{LCM_SEND(2),{0x2E,0x42}},//3  STV2         0x41
{LCM_SEND(2),{0x2F,0x42}},//4  STV2         0x41
{LCM_SEND(2),{0x30,0x40}},//5  STV4         0x43
{LCM_SEND(2),{0x31,0x40}},//6  STV4         0x43
{LCM_SEND(2),{0x32,0x1E}},//7  VSD          0x1F
{LCM_SEND(2),{0x33,0x1E}},//8  VSD          0x1F
{LCM_SEND(2),{0x34,0x1F}},//9  GCL          0x1F
{LCM_SEND(2),{0x35,0x1F}},//10              0x1F
{LCM_SEND(2),{0x36,0x1E}},//11 GCH          0x1E
{LCM_SEND(2),{0x37,0x1E}},//12 GCH          0x1E
{LCM_SEND(2),{0x38,0x1F}},//13              0x1F
{LCM_SEND(2),{0x39,0x48}},//14 CLK8         0x47
{LCM_SEND(2),{0x3A,0x48}},//15 CLK8         0x47
{LCM_SEND(2),{0x3B,0x4A}},//16 CLK6         0x45
{LCM_SEND(2),{0x3C,0x4A}},//17 CLK6         0x45
{LCM_SEND(2),{0x3D,0x44}},//18 CLK4         0x4B
{LCM_SEND(2),{0x3E,0x44}},//19 CLK4         0x4B
{LCM_SEND(2),{0x3F,0x46}},//20 CLK2         0x49
{LCM_SEND(2),{0x40,0x46}},//21 CLK2         0x49
{LCM_SEND(2),{0x41,0x1F}},//22 VGL          0x1F
*/
//GIP_R_GS Pin mapping �����a
/*
{LCM_SEND(2),{0x42,0x1F}},//1  VDS 		0x1E
{LCM_SEND(2),{0x43,0x1F}},//2  VDS          0x1E
{LCM_SEND(2),{0x44,0x43}},//3  STV1         0x40
{LCM_SEND(2),{0x45,0x43}},//4  STV1         0x40
{LCM_SEND(2),{0x46,0x41}},//5  STV3         0x42
{LCM_SEND(2),{0x47,0x41}},//6  STV3         0x42
{LCM_SEND(2),{0x48,0x1E}},//7  VSD          0x1F
{LCM_SEND(2),{0x49,0x1E}},//8  VSD          0x1F
{LCM_SEND(2),{0x4A,0x1E}},//9  GCL          0x1F
{LCM_SEND(2),{0x4B,0x1F}},//10              0x1f
{LCM_SEND(2),{0x4C,0x1E}},//11 GCH          0x1E
{LCM_SEND(2),{0x4D,0x1E}},//12 GCH          0x1E
{LCM_SEND(2),{0x4E,0x1F}},//13              0x1f
{LCM_SEND(2),{0x4F,0x49}},//14 CLK7         0x46
{LCM_SEND(2),{0x50,0x49}},//15 CLK7         0x46
{LCM_SEND(2),{0x51,0x4B}},//16 CLK5         0x44
{LCM_SEND(2),{0x52,0x4B}},//17 CLK5         0x44
{LCM_SEND(2),{0x53,0x45}},//18 CLK3         0x4A
{LCM_SEND(2),{0x54,0x45}},//19 CLK3         0x4A
{LCM_SEND(2),{0x55,0x47}},//20 CLK1         0x48
{LCM_SEND(2),{0x56,0x47}},//21 CLK1         0x48
{LCM_SEND(2),{0x57,0x1F}},//22 VGL          0x1f
*/
//GIP Timing  
{LCM_SEND(2),{0x58,0x10}}, 
{LCM_SEND(2),{0x59,0x00}}, 
{LCM_SEND(2),{0x5A,0x00}}, 
{LCM_SEND(2),{0x5B,0x30}}, //STV_S0
{LCM_SEND(2),{0x5C,0x02}}, //STV_S0
{LCM_SEND(2),{0x5D,0x40}}, //STV_W / S1
{LCM_SEND(2),{0x5E,0x01}}, //STV_S2
{LCM_SEND(2),{0x5F,0x02}}, //STV_S3
{LCM_SEND(2),{0x60,0x30}}, //ETV_W / S1
{LCM_SEND(2),{0x61,0x01}}, //ETV_S2
{LCM_SEND(2),{0x62,0x02}}, //ETV_S3
{LCM_SEND(2),{0x63,0x6A}}, //SETV_ON  
{LCM_SEND(2),{0x64,0x6A}}, //SETV_OFF 
{LCM_SEND(2),{0x65,0x05}}, //ETV
{LCM_SEND(2),{0x66,0x12}}, 
{LCM_SEND(2),{0x67,0x74}}, 
{LCM_SEND(2),{0x68,0x04}}, 
{LCM_SEND(2),{0x69,0x6A}}, 
{LCM_SEND(2),{0x6A,0x6A}}, 
{LCM_SEND(2),{0x6B,0x08}}, 

{LCM_SEND(2),{0x6C,0x00}}, 
{LCM_SEND(2),{0x6D,0x04}}, 
{LCM_SEND(2),{0x6E,0x04}}, 
{LCM_SEND(2),{0x6F,0x88}}, 
{LCM_SEND(2),{0x70,0x00}}, 
{LCM_SEND(2),{0x71,0x00}}, 
{LCM_SEND(2),{0x72,0x06}}, 
{LCM_SEND(2),{0x73,0x7B}}, 
{LCM_SEND(2),{0x74,0x00}}, 
{LCM_SEND(2),{0x75,0x07}}, 
{LCM_SEND(2),{0x76,0x00}}, 
{LCM_SEND(2),{0x77,0x5D}}, 
{LCM_SEND(2),{0x78,0x17}}, 
{LCM_SEND(2),{0x79,0x1F}}, 
{LCM_SEND(2),{0x7A,0x00}}, 
{LCM_SEND(2),{0x7B,0x00}}, 
{LCM_SEND(2),{0x7C,0x00}}, 
{LCM_SEND(2),{0x7D,0x03}}, 
{LCM_SEND(2),{0x7E,0x7B}}, 

//Page4
{LCM_SEND(2),{0xE0,0x04}},
{LCM_SEND(2),{0x2B,0x2B}},
{LCM_SEND(2),{0x2E,0x44}},

//Page1
{LCM_SEND(2),{0xE0,0x01}},
{LCM_SEND(2),{0x0E,0x01}},	//LEDON output VCSW2

//Page3
{LCM_SEND(2),{0xE0,0x03}},
{LCM_SEND(2),{0x98,0x2F}},	//From 2E to 2F, LED_VOL

//Page0
{LCM_SEND(2),{0xE0,0x00}},
{LCM_SEND(2),{0xE6,0x02}},
{LCM_SEND(2),{0xE7,0x02}},
//SLP OUT
{LCM_SEND(2),{0x11,0x00}}, 	// SLPOUT
{LCM_SLEEP(120),},


//DISP ON
{LCM_SEND(2),{0x29,0x00}},	// DSPON
{LCM_SLEEP(5),},

//--- TE----//
//{LCM_SEND(2),{0x35,0x00}},
};

static LCM_Init_Code disp_on =  {LCM_SEND(1), {0x29}};

static LCM_Init_Code sleep_in =  {LCM_SEND(1), {0x10}};

static LCM_Init_Code sleep_out =  {LCM_SEND(1), {0x11}};

static LCM_Force_Cmd_Code rd_prep_code[]={
	{0x39, {LCM_SEND(8), {0x6, 0, 0xF0, 0x55, 0xAA, 0x52, 0x08, 0x01}}},
	{0x37, {LCM_SEND(2), {0x3, 0}}},
};

static int32_t jd9365_mipi_init(struct panel_spec *self)
{
	int32_t i;
	LCM_Init_Code *init = init_data;
	unsigned int tag;

	mipi_set_cmd_mode_t mipi_set_cmd_mode = self->info.mipi->ops->mipi_set_cmd_mode;
	mipi_gen_write_t mipi_gen_write = self->info.mipi->ops->mipi_gen_write;

	LCD_PRINT("nt35521s_init\n");

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

static uint32_t jd9365_readid(struct panel_spec *self)
{
	
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

    	//return 0x936600;

	printk("uboot lcd_jd9365_mipi read id!\n");
	#if 1
	mipi_set_cmd_mode();

	mipi_eotp_set(1,0);
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
		//printk("lcd_jd9365_mipi read id 0x04 value is 0x%x!\n", read_data[0]);
		printk("lcd_jd9365_mipi read id 0x04 value is 0x%x, 0x%x, 0x%x!\n", read_data[0], read_data[1], read_data[2]);

		if((0x93 == read_data[0])&&(0x65 == read_data[1])){
			printk("lcd_jd9365_mipi read id success!\n");
			mipi_eotp_set(1,1);
			return 0x936500;
		}
	}
	mipi_eotp_set(1,1);
	return 0x0;
	#endif
}
int32_t jd9365_reset(struct panel_spec *self)
{
	LCD_PRINT("==lxm==sprdfb:jd9365_reset \n");
			//VDD AVDD
			sprd_gpio_request(NULL, 233);
			sprd_gpio_direction_output(NULL, 233, 0);
			sprd_gpio_set(NULL, 233, 1);
			sprd_gpio_request(NULL, 232);
			sprd_gpio_direction_output(NULL, 232, 0);
			sprd_gpio_set(NULL, 232, 1);
			mdelay(50);
			// reset
			sprd_gpio_request(NULL, 103);
			sprd_gpio_direction_output(NULL, 103, 0);
			sprd_gpio_set(NULL, 103, 1);
			mdelay(20);
			sprd_gpio_set(NULL, 103, 0);
			mdelay(30);
			sprd_gpio_set(NULL, 103, 1);
			mdelay(100);

	return 0;
}
static struct panel_operations lcd_jd9365_mipi_operations = {
	.panel_init = jd9365_mipi_init,
	.panel_readid = jd9365_readid,
	.panel_reset = jd9365_reset,
};

static struct timing_rgb lcd_jd9365_mipi_timing = {
	.hfp = 18,  /* unit: pixel */
	.hbp = 18,
	.hsync = 18,
	.vfp = 24, /*unit: line*/
	.vbp = 8,
	.vsync = 4,
};

static struct info_mipi lcd_jd9365_mipi_info = {
	.work_mode  = SPRDFB_MIPI_MODE_VIDEO,
	.video_bus_width = 24, /*18,16*/
	.lan_number = 4,
	.phy_feq = 440*1000,
	.h_sync_pol = SPRDFB_POLARITY_POS,
	.v_sync_pol = SPRDFB_POLARITY_POS,
	.de_pol = SPRDFB_POLARITY_POS,
	.te_pol = SPRDFB_POLARITY_POS,
	.color_mode_pol = SPRDFB_POLARITY_NEG,
	.shut_down_pol = SPRDFB_POLARITY_NEG,
	.timing = &lcd_jd9365_mipi_timing,
	.ops = NULL,
};

struct panel_spec lcd_jd9365_mipi_spec = {
	.width = 800,
	.height = 1280,
	.type = LCD_MODE_DSI,
	.direction = LCD_DIRECT_NORMAL,
	.info = {
		.mipi = &lcd_jd9365_mipi_info
	},
	.ops = &lcd_jd9365_mipi_operations,
};
