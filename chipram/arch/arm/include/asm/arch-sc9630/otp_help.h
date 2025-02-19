/*****************************************************************************
 **  File Name:    effuse_drv.h                                                 *
 **  Author:       Jenny Deng                                                *
 **  Date:         20/10/2009                                                *
 **  Copyright:    2009 Spreadtrum, Incorporated. All Rights Reserved.       *
 **  Description:  This file defines the basic operation interfaces of       *
 **                EFuse initilize and operation. It provides read and         *
 **                writer interfaces of 0~5 efuse. Efuse 0 for Sn block.     *
 **                Efuse 1 to 4 for Hash blocks. Efuse 5 for control block.  *
 *****************************************************************************
 *****************************************************************************
 **  Edit History                                                            *
 **--------------------------------------------------------------------------*
 **  DATE               Author              Operation                        *
 **  20/10/2009         Jenny.Deng          Create.                          *
 **  26/10/2009         Yong.Li             Update.                          *
 **  30/10/2009         Yong.Li             Update after review.             *
 *****************************************************************************/

#ifndef _EFuse_DRV_H
#define _EFuse_DRV_H

#include "sci_types.h"

extern void  sci_efuse_dhryst_binning_get(int *cal);

#endif



