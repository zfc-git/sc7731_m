/*
 * Copyright (C) 2012 Spreadtrum Communications Inc.
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
#include <common.h>
#include <asm/io.h>
#include <asm/arch/sprd_reg.h>
#include <asm/arch-sc9630/otp_help.h>

#define BLK_DHR_DETA		7
#define BLK_DHR_DETA_SHARKLS		12
#define BLK_DHR_DETA_SHARKLC           13
/*sharklc chip_id*/
extern int soc_is_sharklc(void);
extern int soc_is_sharkls(void);

/*sharkl Dhryst binning*/

void  sci_efuse_dhryst_binning_get(int *cal)
 {
    u32 data = 0;
	int Dhry_binning = 0;
	 /*sharklc chipid auto adapt*/
	 if(soc_is_sharklc()){
		data=__ddie_efuse_read(BLK_DHR_DETA_SHARKLC);
		Dhry_binning = (data >> 10) & 0x003F;
	}else if(soc_is_sharkls()){
		data=__ddie_efuse_read(BLK_DHR_DETA_SHARKLS);
		Dhry_binning = (data >> 4) & 0x003F;
	}else{
		data=__ddie_efuse_read(BLK_DHR_DETA);
		Dhry_binning = (data >> 4) & 0x003F;
	}
	*cal = Dhry_binning;
	return 0;
}


