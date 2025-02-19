
#ifndef _SC9630_REG_DEF_H_
#define _SC9630_REG_DEF_H_

#ifndef BIT
#define BIT(x) (1<<x)
#endif

#include <config.h>
#include "hardware.h"
#if defined CONFIG_SPX15
#include "chip_x15/__regs_ap_apb.h"
#include "chip_x15/__regs_ap_ahb.h"
#include "chip_x15/__regs_ap_clk.h"
#include "chip_x15/__regs_mm_ahb_rf.h"
#include "chip_x15/__regs_mm_clk.h"
#include "chip_x15/__regs_gpu_apb.h"
#include "chip_x15/__regs_gpu_clk.h"
#include "chip_x15/__regs_aon_apb.h"
#include "chip_x15/__regs_aon_ckg.h"
#include "chip_x15/__regs_aon_clk.h"
#include "chip_x15/__regs_ana_apb_if.h"
#include "chip_x15/__regs_pmu_apb.h"
#include "chip_x15/__regs_pub_apb.h"
#elif defined(CONFIG_ARCH_SCX35LT8)
#include "chip_x35lt8/__regs_ap_apb.h"
#include "chip_x35lt8/__regs_ap_ahb_rf.h"
#include "chip_x35lt8/__regs_ap_clk.h"
#include "chip_x35lt8/__regs_mm_ahb_rf.h"
#include "chip_x35lt8/__regs_mm_clk.h"
#include "chip_x35lt8/__regs_gpu_apb_rf.h"
#include "chip_x35lt8/__regs_gpu_clk.h"
#include "chip_x35lt8/__regs_aon_apb_rf.h"
#include "chip_x35lt8/__regs_aon_ckg.h"
#include "chip_x35lt8/__regs_aon_clk.h"
#include "chip_x35lt8/__regs_sc2723_ana_glb.h"
#include "chip_x35lt8/__regs_pmu_apb_rf.h"
#include "chip_x35lt8/__regs_pub_apb.h"
#elif defined(CONFIG_ARCH_SCX35L64)
#include "chip_x35l64/__regs_ap_apb.h"
#include "chip_x35l64/__regs_ap_ahb_rf.h"
#include "chip_x35l64/__regs_ap_clk.h"
#include "chip_x35l64/__regs_mm_ahb_rf.h"
#include "chip_x35l64/__regs_mm_clk.h"
#include "chip_x35l64/__regs_gpu_apb_rf.h"
#include "chip_x35l64/__regs_gpu_clk.h"
#include "chip_x35l64/__regs_aon_apb_rf.h"
#include "chip_x35l64/__regs_aon_ckg.h"
#include "chip_x35l64/__regs_aon_clk.h"
#include "chip_x35l64/__regs_sc2723_ana_glb.h"
#include "chip_x35l64/__regs_pmu_apb_rf.h"
#include "chip_x35l64/__regs_pub_apb.h"
#elif defined(CONFIG_ARCH_SCX35L)
	#if defined(CONFIG_ARCH_SCX20L)
		#include "chip_x20l/__regs_ap_apb.h"
		#include "chip_x20l/__regs_ap_ahb_rf.h"
		#include "chip_x20l/__regs_ap_clk.h"
		#include "chip_x20l/__regs_mm_ahb_rf.h"
		#include "chip_x20l/__regs_mm_clk.h"
		#include "chip_x20l/__regs_gpu_apb_rf.h"
		#include "chip_x20l/__regs_gpu_clk.h"
		#include "chip_x20l/__regs_aon_apb_rf.h"
		#include "chip_x20l/__regs_aon_ckg.h"
		#include "chip_x20l/__regs_aon_clk.h"
		#include "chip_x20l/__regs_sc2723_ana_glb.h"
		#include "chip_x20l/__regs_pmu_apb_rf.h"
		#include "chip_x20l/__regs_pub_apb.h"
	#else
		#include "chip_x35l/__regs_ap_apb.h"
		#include "chip_x35l/__regs_ap_ahb_rf.h"
		#include "chip_x35l/__regs_ap_clk.h"
		#include "chip_x35l/__regs_mm_ahb_rf.h"
		#include "chip_x35l/__regs_mm_clk.h"
		#include "chip_x35l/__regs_gpu_apb_rf.h"
		#include "chip_x35l/__regs_gpu_clk.h"
		#include "chip_x35l/__regs_aon_apb_rf.h"
		#include "chip_x35l/__regs_aon_ckg.h"
		#include "chip_x35l/__regs_aon_clk.h"
		#include "chip_x35l/__regs_sc2723_ana_glb.h"
		#include "chip_x35l/__regs_pmu_apb_rf.h"
		#include "chip_x35l/__regs_pub_apb.h"
	#endif

#else
#include "chip_x35/sprd_reg_ap_apb.h"
#include "chip_x35/sprd_reg_ap_ahb.h"
#include "chip_x35/sprd_reg_ap_clk.h"
#include "chip_x35/sprd_reg_mm_ahb.h"
#include "chip_x35/sprd_reg_mm_clk.h"
#include "chip_x35/sprd_reg_gpu_apb.h"
#include "chip_x35/sprd_reg_gpu_clk.h"
#include "chip_x35/sprd_reg_aon_apb.h"
#include "chip_x35/sprd_reg_aon_ckg.h"
#include "chip_x35/sprd_reg_aon_clk.h"
#include "chip_x35/sprd_reg_ana_glb.h"
#include "chip_x35/sprd_reg_pmu_apb.h"
#include "chip_x35/sprd_reg_pub_apb.h"
#endif

#endif /*_SC9630_REG_DEF_H_*/

