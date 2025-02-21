/*
 * Copyright (C) 2012 Spreadtrum Communications Inc.
 *
 */

#ifndef _SPRDFB_H_
#define _SPRDFB_H_

#define printk printf
//#define  FB_DEBUG
#ifdef FB_DEBUG
#define FB_PRINT printk
#else
#define FB_PRINT(...)
#endif

#define mdelay(t)	({unsigned long msec=(t); while (msec--) { udelay(1000);}})

enum{
	SPRDFB_PANEL_IF_DBI = 0,
	SPRDFB_PANEL_IF_DPI,
	SPRDFB_PANEL_IF_EDPI,
	SPRDFB_PANEL_IF_LIMIT
};


enum{
	MCU_LCD_REGISTER_TIMING = 0,
	MCU_LCD_GRAM_TIMING,
	MCU_LCD_TIMING_KIND_MAX
};

enum{
	RGB_LCD_H_TIMING = 0,
	RGB_LCD_V_TIMING,
	RGB_LCD_TIMING_KIND_MAX
};

struct sprdfb_device;


struct panel_if_ctrl{
	const char *if_name;

	int32_t (*panel_if_check)(struct panel_spec *self);
	void (*panel_if_mount)(struct sprdfb_device *dev);
	void (*panel_if_init)(struct sprdfb_device *dev);
	void (*panel_if_ready)(struct sprdfb_device *dev);
	void (*panel_if_uninit)(struct sprdfb_device *dev);
	void (*panel_if_before_refresh)(struct sprdfb_device *dev);
	void (*panel_if_after_refresh)(struct sprdfb_device *dev);
	void (*panel_if_before_panel_reset)(struct sprdfb_device *dev);
	void (*panel_if_suspend)(struct sprdfb_device *dev);
	void (*panel_if_resume)(struct sprdfb_device *dev);
};


struct sprdfb_device {
	unsigned long smem_start;

	union{
		uint32_t	mcu_timing[MCU_LCD_TIMING_KIND_MAX];
		uint32_t	rgb_timing[RGB_LCD_TIMING_KIND_MAX];
	}panel_timing;

	uint16_t		panel_if_type; /*panel IF*/

#ifdef CONFIG_FB_LOW_RES_SIMU
	uint16_t display_width;
	uint16_t display_height;
#endif

	struct panel_spec	*panel;
	struct panel_if_ctrl *if_ctrl;
	struct display_ctrl	*ctrl;
	uint32_t dpi_clock;
};

struct display_ctrl {
	const char	*name;

	int32_t	(*early_init)	  (struct sprdfb_device *dev);
	int32_t	(*init)		  (struct sprdfb_device *dev);
	int32_t	(*uninit)		  (struct sprdfb_device *dev);

	int32_t 	(*refresh)	  (struct sprdfb_device *dev);
	void		(*update_clk)	  (struct sprdfb_device *dev);
};

void lcd_enable(void);
void lcd_disable(void);

#ifdef CONFIG_FPGA
int sprdchip_lvds_init(void);
#else
#define sprdchip_lvds_init NULL
#endif

#endif
