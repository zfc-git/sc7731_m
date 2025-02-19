/*
 *  stk8baxx.h - Definitions for sensortek stk8baxx accelerometer
 *
 *  Copyright (C) 2012~2015 Lex Hsieh / sensortek <lex_hsieh@sensortek.com.tw>
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */   
 
#ifndef __STK8BAXX__
#define __STK8BAXX__

#include <linux/ioctl.h>
#include <linux/types.h>

#define IS_STK8BA50
#define LSB_1G	256	
/*	direction settings	*/
static const int coordinate_trans[8][3][3] = 
{
	/* x_after, y_after, z_after */
	{{0,-1,0}, {1,0,0}, {0,0,1}},
	{{1,0,0}, {0,1,0}, {0,0,1}},
	{{0,1,0}, {-1,0,0}, {0,0,1}},
	{{-1,0,0}, {0,-1,0}, {0,0,1}},
	{{0,1,0}, {1,0,0}, {0,0,-1}},
	{{-1,0,0}, {0,1,0}, {0,0,-1}},
	{{0,-1,0}, {-1,0,0}, {0,0,-1}},
	{{1,0,0}, {0,-1,0}, {0,0,-1}},

};

/* IOCTLs*/	
#define	STK8BAXX_ACC_IOCTL_BASE 77
/** The following define the IOCTL command values via the ioctl macros */
#define	STK8BAXX_ACC_IOCTL_SET_DELAY		_IOW(STK8BAXX_ACC_IOCTL_BASE, 0, int)
#define	STK8BAXX_ACC_IOCTL_GET_DELAY		_IOR(STK8BAXX_ACC_IOCTL_BASE, 1, int)
#define	STK8BAXX_ACC_IOCTL_SET_ENABLE		_IOW(STK8BAXX_ACC_IOCTL_BASE, 2, int)
#define	STK8BAXX_ACC_IOCTL_GET_ENABLE		_IOR(STK8BAXX_ACC_IOCTL_BASE, 3, int)
#define	STK8BAXX_ACC_IOCTL_READ_DATA		_IOR(STK8BAXX_ACC_IOCTL_BASE, 22, int)
#define	STK8BAXX_ACC_IOCTL_GET_CHIP_ID        _IOR(STK8BAXX_ACC_IOCTL_BASE, 255, char[32])

struct stk8baxx_platform_data
{
	unsigned char direction;
	int interrupt_pin;	
};



#endif	/* #ifndef __STK8BAXX__ */