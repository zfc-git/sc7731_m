/* drivers/video/sc8825/lcd_s6d7aa0x01_mipi.c
 *
 * Support for lcd_s6d7aa0x01 mipi LCD device
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

#define LCM_TAG_SEND  (1<< 0)
#define LCM_TAG_SLEEP (1 << 1)

static LCM_Init_Code init_data[] ={                                                                                                                                                                                                                                                                                                                                                                                                                            
{LCM_SEND(2),{0x00,0x00}}, 
{LCM_SEND(6),{4,0,0xFF,0x12,0x84,0x01}}, 

//#Command 2 Enable(step-2) 
{LCM_SEND(2),{0x00,0x80}}, 
{LCM_SEND(5),{3,0,0xFF,0x12,0x84}}, 

{LCM_SEND(2),{0x00,0x92}},                  
{LCM_SEND(5),{3,0,0xff,0x30,0x02}},  
 
               
//#800 RGB new no
//#{LCM_SEND(2),{0x00,0x90}},                                                              
//#{LCM_SEND(2),{0xb3,0x00}},                                  

//#800 RGB new no
//#{LCM_SEND(2),{0x00,0x00}},                
//#{LCM_SEND(2),{0x2a,0x00,0x00,0x03,0x1F}},          
                
//#enable mode1 new no
//#{LCM_SEND(2),{0x00,0x92}},                                                             
//#{LCM_SEND(2),{0xb3,0x40}},                                                  
                         
//#enable mode 2   new no                            
//#{LCM_SEND(2),{0x00,0x80}},                                                     
//#{LCM_SEND(2),{0xf6,0x01}},                  

//#OSC for 800 new no
//#{LCM_SEND(2),{0x00,0x80}}, 
//#{LCM_SEND(2),{0xC1,0x25}}, 

{LCM_SEND(2),{0x00,0xD2}}, 
{LCM_SEND(2),{0xB0,0x03}}, 

{LCM_SEND(2),{0x00,0x91}}, 
{LCM_SEND(2),{0xB0,0x9A}}, 

//#---------panel setting------------
#if 1
//#TCON Setting Parameter
{LCM_SEND(2),{0x00,0x80}}, 
{LCM_SEND(12),{10,0,0xc0,0x00,0x6e,0x00,0x15,0x15,0x00,0x6e,0x15,0x15}}, 
#endif
//#Panel Timing Setting Parameter
{LCM_SEND(2),{0x00,0x90}}, 
{LCM_SEND(9),{7,0,0xC0,0x00,0x5C,0x00,0x01,0x00,0x04}}, 

//#source pre.     p.104
//#{LCM_SEND(2),{0x00,0xA4}},        
//#{LCM_SEND(2),{0xC0,0x09}},        

//#Interval Scan Frame Setting (Column INVERSION)
//#{LCM_SEND(2),{0x00,0xB3}}, 
//#{LCM_SEND(2),{0xC0,0x00,0x55}},      

//#Oscillator Adjustment for ldle/Normal Mode
{LCM_SEND(2),{0x00,0x81}}, 
{LCM_SEND(2),{0xC1,0x66}}, 

//#source direction
//#{LCM_SEND(2),{0x00,0x00}}, 
//#{LCM_SEND(2),{0x0b,0x00}}, 

//#Zigzag inversion 
{LCM_SEND(2),{0x00,0xA6}}, 
{LCM_SEND(5),{3,0,0xB3,0x0F,0x01}}, 

//#clock delay 
//#{LCM_SEND(2),{0x00,0x90}}, 
//#{LCM_SEND(2),{0xC4,0x49}}, 

//#internal data latch timing
{LCM_SEND(2),{0x00,0x92}}, 
{LCM_SEND(2),{0xC4,0x00}}, 


//#---------power setting------------
//#DC2DC Setting
{LCM_SEND(2),{0x00,0xA0}}, 
{LCM_SEND(17),{15,0,0xC4,0x05,0x10,0x06,0x02,0x05,0x15,0x10,0x05,0x10,0x07,0x02,0x05,0x15,0x10}}, 

//#Power Control Setting
//# BOOSTCLP (C4B0~C4B1h): VSP/VSN voltage setting 
{LCM_SEND(2),{0x00,0xB0}},              
{LCM_SEND(5),{3,0,0xC4,0x22,0x00}},            

//# Power Control Setting
//# HVSET (C591~C593h): VGH/VGL voltage setting    p.130  
{LCM_SEND(2),{0x00,0x91}},                  
{LCM_SEND(5),{3,0,0xC5,0x46,0x42}},            

//#GVDDSET (D800h): GVDD/NGVDD Voltage setting   
{LCM_SEND(2),{0x00,0x00}},               
{LCM_SEND(5),{3,0,0xD8,0xC7,0xC7}},           

//# VCOMDC (D900h): Voltage setting   §ï0x65ªº­È§ä³Ì¨ÎVCOM 
{LCM_SEND(2),{0x00,0x00}}, 
{LCM_SEND(5),{3,0,0xD9,0x65,0x65}}, 

//#source bias 1uA   p.111
//#{LCM_SEND(2),{0x00,0x81}},       
//#{LCM_SEND(2),{0xC4,0x87}},            

{LCM_SEND(2),{0x00,0x87}}, 
{LCM_SEND(5),{3,0,0xC4,0x18,0x80}},

//#{LCM_SEND(2),{0x00,0x80}},     
//#{LCM_SEND(2),{0xC4,0x00}},      
             
//#{LCM_SEND(2),{0x00,0x93}},     
//#{LCM_SEND(2),{0xC0,0x10}}, 
 

//#VDD_18V/LVDSVDD Voltage setting
{LCM_SEND(2),{0x00,0xB3}}, 
{LCM_SEND(2),{0xC5,0x84}}, 

//# Power Control Setting
//# LVDSET (C5BBh): LVD Setting 
{LCM_SEND(2),{0x00,0xBB}}, 
{LCM_SEND(2),{0xC5,0x8a}}, 

//#Power Control Setting
{LCM_SEND(2),{0x00,0x82}}, 
{LCM_SEND(2),{0xC4,0x0a}}, 

//#Power Control Setting
{LCM_SEND(2),{0x00,0xC6}}, 
{LCM_SEND(2),{0xB0,0x03}}, 

//#precharge disable
{LCM_SEND(2),{0x00,0xC2}}, 
{LCM_SEND(2),{0xF5,0x40}}, 

//#sample hold gvdd
{LCM_SEND(2),{0x00,0xC3}}, 
{LCM_SEND(2),{0xF5,0x85}}, 

//#ID1
{LCM_SEND(2),{0x00,0x00}}, 
{LCM_SEND(2),{0xD0,0x40}}, 

//#ID2.ID3
{LCM_SEND(2),{0x00,0x00}}, 
{LCM_SEND(5),{3,0,0xD1,0x00,0x00}}, 


//#---------power IC------------
//#Power IC Setting1
{LCM_SEND(2),{0x00,0x90}}, 
{LCM_SEND(7),{5,0,0xF5,0x02,0x11,0x02,0x15}}, 

//#Power IC Setting2
{LCM_SEND(2),{0x00,0x90}}, 
{LCM_SEND(2),{0xC5,0x50}}, 

//#Power IC Setting3        
{LCM_SEND(2),{0x00,0x94}}, 
{LCM_SEND(6),{4,0,0xC5,0x66,0x66,0x63}},          

//#VGLO1 setting
{LCM_SEND(2),{0x00,0xB2}}, 
{LCM_SEND(5),{3,0,0xF5,0x00,0x00}}, 

//#VGLO1_s setting
{LCM_SEND(2),{0x00,0xB4}}, 
{LCM_SEND(5),{3,0,0xF5,0x00,0x00}}, 

//#VGLO2 setting
{LCM_SEND(2),{0x00,0xB6}}, 
{LCM_SEND(5),{3,0,0xF5,0x00,0x00}}, 

//#VGLO2_s setting
{LCM_SEND(2),{0x00,0xB8}}, 
{LCM_SEND(5),{3,0,0xF5,0x00,0x00}}, 

//#VCL on
{LCM_SEND(2),{0x00,0x94}}, 
{LCM_SEND(5),{3,0,0xF5,0x00,0x00}}, 

//#VCL reg.en
{LCM_SEND(2),{0x00,0xD2}}, 
{LCM_SEND(5),{3,0,0xF5,0x06,0x15}}, 

//#VGL 1/2 pull low
{LCM_SEND(2),{0x00,0xB4}}, 
{LCM_SEND(2),{0xC5,0xCC}}, 

//#VSP on
//#{LCM_SEND(2),{0x00,0xBA}}, 
//#{LCM_SEND(2),{0xF5,0x03}}, 

//#VGHO Option
//#{LCM_SEND(2),{0x00,0xB2}}, 
//#{LCM_SEND(2),{0xC5,0x40}}, 

//#VGLO Option
{LCM_SEND(2),{0x00,0xB4}}, 
{LCM_SEND(2),{0xC5,0xCC}}, 

//#---------power IC------------
//#TCON_GOA_WAVE(Panel timing state control)
//#{LCM_SEND(2),{0x00,0x80}}, 
//#{LCM_SEND(2),{0xCB,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00}}, 

//#TCON_GOA_WAVE(Panel timing state control)
//#{LCM_SEND(2),{0x00,0x90}}, 
//#{LCM_SEND(2),{0xCB,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00}}, 

//#TCON_GOA_WAVE(Panel timing state control)
//#{LCM_SEND(2),{0x00,0xA0}}, 
//#{LCM_SEND(2),{0xCB,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00}}, 

//#TCON_GOA_WAVE(Panel timing state control)
//#{LCM_SEND(2),{0x00,0xB0}}, 
//#{LCM_SEND(2),{0xCB,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00}}, 

//#TCON_GOA_WAVE(Panel timing state control)
{LCM_SEND(2),{0x00,0xC0}}, 
{LCM_SEND(18),{16,0,0xCB,0x15,0x15,0x15,0x15,0x15,0x15,0x15,0x15,0x15,0x00,0x15,0x15,0x00,0x00,0x00}}, 

//#TCON_GOA_WAVE(Panel timing state control)
{LCM_SEND(2),{0x00,0xD0}}, 
{LCM_SEND(18),{16,0,0xCB,0x15,0x15,0x00,0x15,0x15,0x15,0x00,0x15,0x15,0x15,0x15,0x15,0x15,0x15,0x15}}, 

//#TCON_GOA_WAVE(Panel timing state control)
{LCM_SEND(2),{0x00,0xE0}}, 
{LCM_SEND(17),{15,0,0xCB,0x15,0x00,0x15,0x15,0x00,0x00,0x00,0x15,0x15,0x00,0x15,0x15,0x15,0x00}}, 

//#TCON_GOA_WAVE(Panel timing state control)
{LCM_SEND(2),{0x00,0xF0}}, 
{LCM_SEND(14),{12,0,0xCB,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff}}, 

//#---------goa mapping------------
//#TCON_GOA_WAVE(Panel pad mapping control)
{LCM_SEND(2),{0x00,0x80}}, 
{LCM_SEND(18),{16,0,0xCC,0x14,0x0c,0x12,0x0a,0x0e,0x16,0x10,0x18,0x02,0x00,0x2f,0x2f,0x00,0x00,0x00}}, 
               
//#TCON_GOA_WAVE(Panel pad mapping control)
{LCM_SEND(2),{0x00,0x90}}, 
{LCM_SEND(18),{16,0,0xCC,0x29,0x2a,0x00,0x2d,0x2e,0x06,0x00,0x13,0x0b,0x11,0x09,0x0d,0x15,0x0f,0x17}},  


//#TCON_GOA_WAVE(Panel pad mapping control)
{LCM_SEND(2),{0x00,0xA0}}, 
{LCM_SEND(17),{15,0,0xCC,0x01,0x00,0x2f,0x2f,0x00,0x00,0x00,0x29,0x2a,0x00,0x2d,0x2e,0x05,0x00}}, 

                    
//#TCON_GOA_WAVE(Panel pad mapping control)
{LCM_SEND(2),{0x00,0xB0}}, 
{LCM_SEND(18),{16,0,0xCC,0x0d,0x15,0x0f,0x17,0x13,0x0b,0x11,0x09,0x05,0x00,0x2f,0x2f,0x00,0x00,0x00}}, 
                     
//#TCON_GOA_WAVE(Panel pad mapping control)
{LCM_SEND(2),{0x00,0xC0}}, 
{LCM_SEND(18),{16,0,0xCC,0x29,0x2a,0x00,0x2e,0x2d,0x01,0x00,0x0e,0x16,0x10,0x18,0x14,0x0c,0x12,0x0a}}, 

                    
//#TCON_GOA_WAVE(Panel pad mapping control)
{LCM_SEND(2),{0x00,0xD0}}, 
{LCM_SEND(17),{15,0,0xCC,0x06,0x00,0x2f,0x2f,0x00,0x00,0x00,0x29,0x2a,0x00,0x2e,0x2d,0x02,0x00}}, 

                   
//#---------goa timing------------
//#TCON_GOA_WAVE(VST setting)
{LCM_SEND(2),{0x00,0x80}}, 
{LCM_SEND(15),{13,0,0xCE,0x8F,0x0D,0x1A,0x8E,0x0D,0x1A,0x00,0x00,0x00,0x00,0x00,0x00}}, 
                     
//# TCON_GOA_WAVE(VEND stting)
{LCM_SEND(2),{0x00,0x90}}, 
{LCM_SEND(17),{15,0,0xCE,0x75,0x00,0x1A,0x75,0x01,0x1A,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00}}, 

                     
//# TCON_GOA_WAVE(TCON_GOA_WAVE)
{LCM_SEND(2),{0x00,0xA0}}, 
{LCM_SEND(17),{15,0,0xCE,0x78,0x07,0x84,0xF0,0x90,0x1A,0x00,0x78,0x06,0x84,0xF1,0x91,0x1A,0x00}}, 
                     
//# TCON_GOA_WAVE(TCON_GOA_WAVE)
{LCM_SEND(2),{0x00,0xB0}}, 
{LCM_SEND(17),{15,0,0xCE,0x78,0x05,0x84,0xF2,0x90,0x1A,0x00,0x78,0x04,0x84,0xF3,0x91,0x1A,0x00}}, 
                     
//# TCON_GOA_WAVE(TCON_GOA_WAVE)
{LCM_SEND(2),{0x00,0xC0}}, 
{LCM_SEND(17),{15,0,0xCE,0x78,0x03,0x84,0xF4,0x90,0x1A,0x00,0x78,0x02,0x84,0xF5,0x91,0x1A,0x00}}, 
                     
//# TCON_GOA_WAVE(TCON_GOA_WAVE)
{LCM_SEND(2),{0x00,0xD0}}, 
{LCM_SEND(17),{15,0,0xCE,0x78,0x01,0x84,0xF6,0x90,0x1A,0x00,0x78,0x00,0x84,0xF7,0x91,0x1A,0x00}}, 
                      
//# TCON_GOA_WAVE(TCON_GOA_WAVE)
{LCM_SEND(2),{0x00,0x80}}, 
{LCM_SEND(17),{15,0,0xCF,0x70,0x00,0x84,0xF8,0x90,0x1A,0x00,0x70,0x01,0x84,0xF9,0x91,0x1A,0x00}}, 
                     
//# TCON_GOA_WAVE(TCON_GOA_WAVE)
{LCM_SEND(2),{0x00,0x90}}, 
{LCM_SEND(17),{15,0,0xCF,0x70,0x02,0x84,0xFA,0x90,0x1A,0x00,0x70,0x03,0x84,0xFB,0x91,0x1A,0x00}}, 
                     
//# TCON_GOA_WAVE(TCON_GOA_WAVE)
{LCM_SEND(2),{0x00,0xA0}}, 
{LCM_SEND(17),{15,0,0xCF,0x70,0x04,0x84,0xFC,0x90,0x1A,0x00,0x70,0x05,0x84,0xFD,0x91,0x1A,0x00}}, 
                     
//# TCON_GOA_WAVE(TCON_GOA_WAVE)
{LCM_SEND(2),{0x00,0xB0}}, 
{LCM_SEND(17),{15,0,0xCF,0x70,0x06,0x84,0xFE,0x90,0x1A,0x00,0x70,0x07,0x84,0xFF,0x91,0x1A,0x00}}, 

//# TCON_GOA_WAVE(TCON_GOA_WAVE) 
{LCM_SEND(2),{0x00,0xC0}},                     
{LCM_SEND(14),{12,0,0xCF,0x01,0x01,0x64,0x64,0x00,0x00,0x01,0x00,0x00,0x00,0x00}}, 
                                                              
//# TCON_GOA_OUT Setting
{LCM_SEND(2),{0x00,0xB5}}, 
{LCM_SEND(9),{7,0,0xC5,0x3f,0xff,0xff,0x3f,0xff,0xff}}, 
                     
//# Gamma 2.2+
{LCM_SEND(2),{0x00,0x00}}, 
{LCM_SEND(23),{21,0,0xE1,0x00,0x19,0x29,0x3A,0x4C,0x5C,0x5D,0x8B,0x7D,0x98,0x67,0x53,0x63,0x40,0x3E,0x32,0X26,0X1A,0X0B,0X00}}, 


//# Gamma 2.2-
{LCM_SEND(2),{0x00,0x00}}, 
{LCM_SEND(23),{21,0,0xE2,0x00,0x19,0x29,0x3A,0x4C,0x5C,0x5D,0x8B,0x7D,0x98,0x67,0x53,0x63,0x40,0x3E,0x32,0X26,0X1A,0X0B,0X00}}, 

//#---------------------255--253--251--248--244--239-231--203--175--143--112---80---52---24---16---11---7----4----2----0                     
                     

{LCM_SEND(2),{0x00,0xA4}},
{LCM_SEND(2),{0xC1,0xF0}},


{LCM_SEND(2),{0x00,0x92}},
{LCM_SEND(2),{0xC4,0x00}},

{LCM_SEND(2),{0x00,0xB4}},
{LCM_SEND(2),{0xC5,0xCC}},



{LCM_SEND(2),{0x00,0x90}},
{LCM_SEND(2),{0xB6,0xB6}},

{LCM_SEND(2),{0x00,0x92}},
{LCM_SEND(2),{0xB3,0x06}},

{LCM_SEND(2),{0x00,0xC2}},
{LCM_SEND(2),{0xF5,0x40}},

{LCM_SEND(2),{0x00,0xb4}},
{LCM_SEND(2),{0xc0,0x50}},


{LCM_SEND(2),{0x00,0x80}},
{LCM_SEND(5),{3,0,0xFF,0xFF,0xFF}},

{LCM_SEND(1), {0x11}},//SLP OUT
{LCM_SLEEP(120)},
{LCM_SEND(1), {0x29}},//DISP ON 
{LCM_SLEEP(100)}, 

};


static LCM_Init_Code disp_on =  {LCM_SEND(1), {0x29}};

static LCM_Init_Code sleep_in[] =  {
	{LCM_SEND(1), {0x28}},
	{LCM_SLEEP(150)}, 	//>150ms
	{LCM_SEND(1), {0x10}},
	{LCM_SLEEP(150)},	//>150ms
};

static LCM_Init_Code sleep_out[] =  {
	{LCM_SEND(1), {0x11}},
	{LCM_SLEEP(120)},//>120ms
	{LCM_SEND(1), {0x29}},
	{LCM_SLEEP(20)}, //>20ms
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
static int32_t s6d7aa0x01_mipi_init(struct panel_spec *self)
{
	LCD_PRINT("[%s][%d] s6d7aa0x01_mipi_init Enter!\n",__FUNCTION__,__LINE__);
	//int32_t i;
	int32_t size = 0;
	LCM_Init_Code *init = init_data;
	unsigned int tag;
	

	mipi_set_cmd_mode_t mipi_set_cmd_mode = self->info.mipi->ops->mipi_set_cmd_mode;
	mipi_gen_write_t mipi_gen_write = self->info.mipi->ops->mipi_gen_write;
	mipi_eotp_set_t mipi_eotp_set = self->info.mipi->ops->mipi_eotp_set;
	mipi_set_lp_mode_t mipi_set_lp_mode = self->info.mipi->ops->mipi_set_lp_mode;
	mipi_set_hs_mode_t mipi_set_hs_mode = self->info.mipi->ops->mipi_set_hs_mode;
	
	LCD_PRINT("[%s] Lancewu lcd_s6d7aa0x01_init\n",__FUNCTION__);

	//s6d7aa0x01_mipi_set_lcmvdd();
	
	udelay(2*1000);
	mipi_set_cmd_mode();
	mipi_eotp_set(1,0);

	LCD_PRINT("lcd_s6d7aa0x01 data size: %d\n",ARRAY_SIZE(init_data));

	size=ARRAY_SIZE(init_data);

	lcd_code_init(self,init,size);

	mipi_eotp_set(1,1);

	return 0;
	
}


static uint32_t s6d7aa0x01_readid(struct panel_spec *self)
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

    	return 0xaa01;

	printk("uboot lcd_s6d7aa0x01_mipi read id!\n");
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
		//printk("lcd_s6d7aa0x01_mipi read id 0x04 value is 0x%x!\n", read_data[0]);
		printk("lcd_s6d7aa0x01_mipi read id 0x04 value is 0x%x, 0x%x, 0x%x!\n", read_data[0], read_data[1], read_data[2]);

		if((0x93 == read_data[0])){
			printk("lcd_s6d7aa0x01_mipi read id success!\n");
			return 0xaa01;
		}
	}

	mdelay(5);
	mipi_set_hs_mode();
	return 0;
	#endif
}

static struct panel_operations lcd_s6d7aa0x01_mipi_operations = {
	.panel_init = s6d7aa0x01_mipi_init,
	.panel_readid = s6d7aa0x01_readid,
};


static struct timing_rgb lcd_s6d7aa0x01_mipi_timing = {
	
/*	.hfp = 48,  
	.hbp = 48,
	.hsync = 10,
	.vfp = 23,
	.vbp = 15,
	.vsync = 20,
*/
	.hfp = 80,  
	.hbp = 80,
	.hsync = 4,
	.vfp = 16,
	.vbp = 16,
	.vsync = 2,
};								
		
static struct info_mipi lcd_s6d7aa0x01_mipi_info = {
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
	.timing = &lcd_s6d7aa0x01_mipi_timing,
	.ops = NULL,
};




struct panel_spec lcd_s6d7aa0x01_mipi_spec = {


	.width = 800,    
	.height = 1280,
	.fps					= 60,
	.type = LCD_MODE_DSI,
	.direction = LCD_DIRECT_NORMAL,
	.info = {
		.mipi = &lcd_s6d7aa0x01_mipi_info
	},
	.ops = &lcd_s6d7aa0x01_mipi_operations,
};

