
#ifndef _WHALE_REG_DEF_H_
#define _WHALE_REG_DEF_H_

#ifndef BIT
#define BIT(x) (1<<x)
#endif

#include <config.h>
#include "hardware.h"
#if defined CONFIG_WHALE
#include "chip_whale/__regs_ana_apb.h"
#include "chip_whale/__regs_aon_apb.h"
#include "chip_whale/__regs_aon_dbg_apb.h"
#include "chip_whale/__regs_aon_sec_apb.h"
#include "chip_whale/__regs_ap_ahb.h"
#include "chip_whale/__regs_ap_apb.h"
#include "chip_whale/__regs_pmu_apb.h"
#include "chip_whale/__regs_pub0_apb.h"
#include "chip_whale/__regs_pub1_apb.h"
#include "chip_whale/__regs_sc2731_ana_glb.h"
#include "chip_whale/__regs_ana_efuse.h"
#endif

#endif /*_WHALE_REG_DEF_H_*/

