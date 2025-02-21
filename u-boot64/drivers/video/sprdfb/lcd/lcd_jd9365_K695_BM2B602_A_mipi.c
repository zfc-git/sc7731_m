/* drivers/video/sc8825/lcd_jd9365_mipi.c
 *
 * Support for lcd_jd9365 mipi LCD device
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
//Page0                                                                                             
{LCM_SEND(2),{0xE0,0x00}},                                                                              

//--- PASSWORD  ----//                                                                              
{LCM_SEND(2),{0xE1,0x93}},                                                                              
{LCM_SEND(2),{0xE2,0x65}},                                                                              
{LCM_SEND(2),{0xE3,0xF8}},                                                                                                                                                                                  

{LCM_SEND(2),{0x80,0x03}},  
//Lane select by internal reg
{LCM_SEND(2),{0xE0,0x04}},
{LCM_SEND(2),{0x2D,0x03}},
{LCM_SEND(2),{0xE0,0x00}}, 
//Page0
{LCM_SEND(2),{0xE0,0x00}},                                                                                                 

//--- Sequence Ctrl  ----//
{LCM_SEND(2),{0x70,0x10}},	//DC0,DC1
{LCM_SEND(2),{0x71,0x13}},	//DC2,DC3
{LCM_SEND(2),{0x72,0x06}},	//DC7

{LCM_SEND(2),{0xE6,0x02}},	//Watch dog
{LCM_SEND(2),{0xE7,0x06}},	//Watch dog


//page4
//Generic_Short_Write_1P(0xE0,0x04}},  
//Generic_Short_Write_1P(0x2B,0x2B}},                                                                       
//Generic_Short_Write_1P(0x2D,0x03}},                                                                       
//Generic_Short_Write_1P(0x2E,0x44}},                                                                       

//--- Page1  ----//                                                                                 
{LCM_SEND(2),{0xE0,0x01}},                                                                              

//Set VCOM                                                                                          
{LCM_SEND(2),{0x00,0x00}},                                                                              
{LCM_SEND(2),{0x01,0x90}}, //55,5D,5B,6B                                                                            
//Set VCOM_Reverse                                                                                  
{LCM_SEND(2),{0x03,0x00}},                                                                              
{LCM_SEND(2),{0x04,0x90}}, //55,5D,5B,6B

//SSD_Single(0x0C,0x75}}, //PWRIC_CLK1[2:0]                                                                             

//Set Gamma Power, VGMP,VGMN,VGSP,VGSN                                                              
{LCM_SEND(2),{0x17,0x00}},                                                                              
{LCM_SEND(2),{0x18,0xB1}}, //VGMP=4.5V                                                                      
{LCM_SEND(2),{0x19,0x01}}, //                                                               
{LCM_SEND(2),{0x1A,0x00}},                                                                              
{LCM_SEND(2),{0x1B,0xB1}}, //VGMN=-4.5V                                                             
{LCM_SEND(2),{0x1C,0x01}}, //                                                               


//Set Gate Power
{LCM_SEND(2),{0x1F,0x3E}}, //VGH_REG=15.4V
{LCM_SEND(2),{0x20,0x2F}}, //VGL_REG=-13V
{LCM_SEND(2),{0x21,0x2F}}, //VGL_REG2=-13V
{LCM_SEND(2),{0x22,0x0E}},


//Generic_Short_Write_1P(0x35,0x26}},  //add  20150424

//SETPANEL                                                                                          
{LCM_SEND(2),{0x37,0x69}},	//SS=1,BGR=1                                                                

//SET RGBCYC                                                                                        
{LCM_SEND(2),{0x38,0x05}},	//JDT=101 zigzag inversion       0x05                                        
{LCM_SEND(2),{0x39,0x08}},	//RGB_N_EQ1,0x08                 0x08                             
{LCM_SEND(2),{0x3A,0x12}},	//RGB_N_EQ2,0x12                 0x1F
{LCM_SEND(2),{0x3B,0x18}},  //RGB_N_EQ2_Temp,0x18            0x1F                              
{LCM_SEND(2),{0x3C,0x78}},	//SET EQ3 for TE_H,0x78          0x88 
//Generic_Short_Write_1P(0x3D,0xFF}},                                            
{LCM_SEND(2),{0x3E,0x80}},	//SET CHGEN_OFF,0x80             0x8C                           
{LCM_SEND(2),{0x3F,0x80}},	//SET CHGEN_OFF2,0x80            0x8C                          


//Set TCON                                                                                          
{LCM_SEND(2),{0x40,0x02}},	//RSO=800 RGB                                                               
{LCM_SEND(2),{0x41,0x80}},	//LN=640->1280 line                                                                                                                                                             
//Generic_Short_Write_1P(0x4A,0x35}},  //BIST    888      


//--- power voltage  ----//
{LCM_SEND(2),{0x55,0x0F}},	//DCDCM=0001, JD PWR_IC
{LCM_SEND(2),{0x56,0x01}},
{LCM_SEND(2),{0x57,0x69}},  //
{LCM_SEND(2),{0x58,0x0A}},
{LCM_SEND(2),{0x59,0x0A}},	//VCL = -2.7V
{LCM_SEND(2),{0x5A,0x28}},	//VGH = 15.4V
{LCM_SEND(2),{0x5B,0x1B}},	//VGL = -13V                                                                                                   

//--- Gamma  ----//
{LCM_SEND(2),{0x5D,0x7C}},
{LCM_SEND(2),{0x5E,0x65}},
{LCM_SEND(2),{0x5F,0x55}},
{LCM_SEND(2),{0x60,0x48}},
{LCM_SEND(2),{0x61,0x43}},
{LCM_SEND(2),{0x62,0x34}},
{LCM_SEND(2),{0x63,0x38}},
{LCM_SEND(2),{0x64,0x21}},
{LCM_SEND(2),{0x65,0x38}},
{LCM_SEND(2),{0x66,0x36}},
{LCM_SEND(2),{0x67,0x36}},
{LCM_SEND(2),{0x68,0x53}},
{LCM_SEND(2),{0x69,0x41}},
{LCM_SEND(2),{0x6A,0x48}},
{LCM_SEND(2),{0x6B,0x39}},
{LCM_SEND(2),{0x6C,0x34}},
{LCM_SEND(2),{0x6D,0x27}},
{LCM_SEND(2),{0x6E,0x15}},
{LCM_SEND(2),{0x6F,0x00}},
{LCM_SEND(2),{0x70,0x7C}},
{LCM_SEND(2),{0x71,0x65}},
{LCM_SEND(2),{0x72,0x55}},
{LCM_SEND(2),{0x73,0x48}},
{LCM_SEND(2),{0x74,0x43}},
{LCM_SEND(2),{0x75,0x34}},
{LCM_SEND(2),{0x76,0x38}},
{LCM_SEND(2),{0x77,0x21}},
{LCM_SEND(2),{0x78,0x38}},
{LCM_SEND(2),{0x79,0x36}},
{LCM_SEND(2),{0x7A,0x36}},
{LCM_SEND(2),{0x7B,0x53}},
{LCM_SEND(2),{0x7C,0x41}},
{LCM_SEND(2),{0x7D,0x48}},
{LCM_SEND(2),{0x7E,0x39}},
{LCM_SEND(2),{0x7F,0x34}},
{LCM_SEND(2),{0x80,0x27}},
{LCM_SEND(2),{0x81,0x15}},
{LCM_SEND(2),{0x82,0x00}},                                                                        



//Page2, for GIP
{LCM_SEND(2),{0xE0,0x02}},

//GIP_L Pin mapping
{LCM_SEND(2),{0x00,0x45}}, //45
{LCM_SEND(2),{0x01,0x45}}, //45 
{LCM_SEND(2),{0x02,0x47}}, //47
{LCM_SEND(2),{0x03,0x47}}, //47
{LCM_SEND(2),{0x04,0x41}}, //41
{LCM_SEND(2),{0x05,0x41}}, //41
{LCM_SEND(2),{0x06,0x1F}},
{LCM_SEND(2),{0x07,0x1F}},
{LCM_SEND(2),{0x08,0x1F}},
{LCM_SEND(2),{0x09,0x1F}},
{LCM_SEND(2),{0x0A,0x1F}},
{LCM_SEND(2),{0x0B,0x1F}},
{LCM_SEND(2),{0x0C,0x1F}},
{LCM_SEND(2),{0x0D,0x1D}},//
{LCM_SEND(2),{0x0E,0x1D}},//
{LCM_SEND(2),{0x0F,0x1D}},//
{LCM_SEND(2),{0x10,0x1F}},
{LCM_SEND(2),{0x11,0x1F}},
{LCM_SEND(2),{0x12,0x1F}},
{LCM_SEND(2),{0x13,0x1F}},
{LCM_SEND(2),{0x14,0x1F}},
{LCM_SEND(2),{0x15,0x1F}},


//GIP_R Pin mapping
{LCM_SEND(2),{0x16,0x44}}, //44
{LCM_SEND(2),{0x17,0x44}}, //44
{LCM_SEND(2),{0x18,0x46}}, //46
{LCM_SEND(2),{0x19,0x46}}, //46
{LCM_SEND(2),{0x1A,0x40}}, //40
{LCM_SEND(2),{0x1B,0x40}}, //40
{LCM_SEND(2),{0x1C,0x1F}},
{LCM_SEND(2),{0x1D,0x1F}},
{LCM_SEND(2),{0x1E,0x1F}},
{LCM_SEND(2),{0x1F,0x1F}},
{LCM_SEND(2),{0x20,0x1F}},
{LCM_SEND(2),{0x21,0x1F}},
{LCM_SEND(2),{0x22,0x1F}},
{LCM_SEND(2),{0x23,0x1D}}, //
{LCM_SEND(2),{0x24,0x1D}}, //
{LCM_SEND(2),{0x25,0x1D}}, //
{LCM_SEND(2),{0x26,0x1F}},
{LCM_SEND(2),{0x27,0x1F}},
{LCM_SEND(2),{0x28,0x1F}},
{LCM_SEND(2),{0x29,0x1F}},
{LCM_SEND(2),{0x2A,0x1F}},
{LCM_SEND(2),{0x2B,0x1F}},


//GIP Timing          	                                                                            
{LCM_SEND(2),{0x58,0x40}},	                                                                            
{LCM_SEND(2),{0x59,0x00}},	                                                                            
{LCM_SEND(2),{0x5A,0x00}},	                                                                            
{LCM_SEND(2),{0x5B,0x10}},	//STV_Num                                                                   
{LCM_SEND(2),{0x5C,0x03}},	//STV_S0                                                                 
{LCM_SEND(2),{0x5D,0x10}},	//STV_W,STV_S1                                                              
{LCM_SEND(2),{0x5E,0x00}},	//STV_S2                                                                    
{LCM_SEND(2),{0x5F,0x00}},	//STV_S3                                                                    
{LCM_SEND(2),{0x60,0x00}},	//ETV_W,ETV_S1                                                              
{LCM_SEND(2),{0x61,0x00}},	//ETV_S2                                                                    
{LCM_SEND(2),{0x62,0x00}},	//ETV_S3                                                                    
{LCM_SEND(2),{0x63,0x03}},	//SETV_ON                                                                   
{LCM_SEND(2),{0x64,0x6B}},	//SETV_OFF  0x7B  ===                                                               
{LCM_SEND(2),{0x65,0x00}},	//ETV_EN,ETV_NUM                                                            
{LCM_SEND(2),{0x66,0x00}},	//ETV_S0                                                                    
{LCM_SEND(2),{0x67,0x31}},	//CKV0_NUM,CKV0_W                                                           
{LCM_SEND(2),{0x68,0x07}},	//CKV0_S0                                                                   
{LCM_SEND(2),{0x69,0x03}},	//CKV0_ON                                                                   
{LCM_SEND(2),{0x6A,0x7B}},	//CKV0_OFF  0x7B  ===                                                                  
{LCM_SEND(2),{0x6B,0x00}},	//CKV0_DUM                                                               
{LCM_SEND(2),{0x6C,0x00}},	//EOLR,GEQ_LINE,GEQ_W                                                       
{LCM_SEND(2),{0x6D,0x04}},	//GEQ_GGND1                                                                 
{LCM_SEND(2),{0x6E,0x04}},	//GEQ_GGND2                                                                 
{LCM_SEND(2),{0x6F,0x88}},	//GIPDR,VGHO_SEL,VGLO_SEL,VGLO_SEL2,CKV_GROUP,CKV1_CON,CKV0_CON             
{LCM_SEND(2),{0x70,0x00}},	//CKV1_NUM,CKV1_W                                                           
{LCM_SEND(2),{0x71,0x00}},	//CKV1_S0                                                                   
{LCM_SEND(2),{0x72,0x06}},	//CKV1_ON                                                                   
{LCM_SEND(2),{0x73,0x7B}},	//CKV1_OFF                                                                  
{LCM_SEND(2),{0x74,0x00}},	//CKV1_DUM                                                                  
{LCM_SEND(2),{0x75,0x07}}, 	//FLM_EN,FLM_W                                                           
{LCM_SEND(2),{0x76,0x00}}, 	//FLM_ON                                                                    
{LCM_SEND(2),{0x77,0x5D}}, 	//VEN_EN,VEN_W,FLM_NUM                                                      
{LCM_SEND(2),{0x78,0x17}}, 	//FLM_OFF                                                                   
{LCM_SEND(2),{0x79,0x1F}}, 	//VEN_W                                                                     
{LCM_SEND(2),{0x7A,0x00}}, 	//VEN_S0                                                                    
{LCM_SEND(2),{0x7B,0x00}}, 	//VEN_S1                                                                    
{LCM_SEND(2),{0x7C,0x00}}, 	//VEN_DUM                                                                   
{LCM_SEND(2),{0x7D,0x03}}, 	//VEN_ON                                                                    
{LCM_SEND(2),{0x7E,0x7B}}, 	//VEN_OFF                                                                   

//Page4
{LCM_SEND(2),{0xE0,0x04}},
{LCM_SEND(2),{0x09,0x10}},
{LCM_SEND(2),{0x2B,0x2B}},
{LCM_SEND(2),{0x2E,0x44}},

//Page0
{LCM_SEND(2),{0xE0,0x00}},
{LCM_SEND(2),{0xE6,0x02}},
{LCM_SEND(2),{0xE7,0x02}},
//Page 1
//Generic_Short_Write_1P(0xE0,0x01}},                                                                            
//Generic_Short_Write_1P(0x4A,0x35}},
{LCM_SLEEP(1)},
{LCM_SEND(1), {0x11}},//SLP OUT
{LCM_SLEEP(120)},
{LCM_SEND(1), {0x29}},//DISP ON
{LCM_SLEEP(50)},                                                                                       

//--- TE----//                                                                                      
{LCM_SEND(2),{0x35,0x00}}, 
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

static int32_t jd9365_mipi_init(struct panel_spec *self)
{
	LCD_PRINT("[%s][%d] jd9365_mipi_init Enter!\n",__FUNCTION__,__LINE__);
	int32_t i;
	LCM_Init_Code *init = init_data;
	unsigned int tag;
	

	mipi_set_cmd_mode_t mipi_set_cmd_mode = self->info.mipi->ops->mipi_set_cmd_mode;
	mipi_gen_write_t mipi_gen_write = self->info.mipi->ops->mipi_gen_write;
	mipi_eotp_set_t mipi_eotp_set = self->info.mipi->ops->mipi_eotp_set;
	mipi_set_lp_mode_t mipi_set_lp_mode = self->info.mipi->ops->mipi_set_lp_mode;
	mipi_set_hs_mode_t mipi_set_hs_mode = self->info.mipi->ops->mipi_set_hs_mode;
	
	LCD_PRINT("[%s] Lancewu lcd_jd9365_init\n",__FUNCTION__);

	//jd9365_mipi_set_lcmvdd();
	
	udelay(2*1000);
	mipi_set_cmd_mode();
	mipi_eotp_set(1,0);

	LCD_PRINT("lcd_jd9365 data size: %d\n",ARRAY_SIZE(init_data));

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


static uint32_t jd9365_readid(struct panel_spec *self)
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

    	return 0x936500;

	printk("uboot lcd_jd9365_mipi read id!\n");
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
		//printk("lcd_jd9365_mipi read id 0x04 value is,0x%x!\n", read_data[0]);
		printk("lcd_jd9365_mipi read id 0x04 value is,0x%x,,0x%x,,0x%x!\n", read_data[0], read_data[1], read_data[2]);

		if((0x93 == read_data[0])){
			printk("lcd_jd9365_mipi read id success!\n");
			return 0x936500;
		}
	}

	mdelay(5);
	mipi_set_hs_mode();
	return 0;
	#endif
}

static struct panel_operations lcd_jd9365_mipi_operations = {
	.panel_init = jd9365_mipi_init,
	.panel_readid = jd9365_readid,
};


static struct timing_rgb lcd_jd9365_mipi_timing = {
	.hfp = 32,  
	.hbp = 20,
	.hsync = 20,
	.vfp = 12,
	.vbp = 4,
	.vsync = 4,
};								
		
static struct info_mipi lcd_jd9365_mipi_info = {
	.work_mode  = SPRDFB_MIPI_MODE_VIDEO,
	.video_bus_width = 24, 
	.lan_number = 4,
	.phy_feq = 450*1000,
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


	.width = 600,    
	.height = 1024,
	.fps = 60,
	.type = LCD_MODE_DSI,
	.direction = LCD_DIRECT_NORMAL,
	.info = {
		.mipi = &lcd_jd9365_mipi_info
	},
	.ops = &lcd_jd9365_mipi_operations,
};

