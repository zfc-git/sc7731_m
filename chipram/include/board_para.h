#if defined(CONFIG_SC8830) || defined(CONFIG_SC9630) || defined(CONFIG_SCX35L64)
#define EMMC_CFG_ADDR					EMMC_BASE_ADDR	/* 0x2060_0000 */
#define SD_CFG_ADDR					        SD_BASE_ADDR	/* 0x2030_0000 */

/********** ahb clock enable **********/
#define EMMC_AHB_EN						AHB_EB
#define EMMC_AHB_RST					AHB_SOFT_RST

#define EMMC_AHB_EN_SHIFT				11	/* BIT_11 */
#define EMMC_AHB_RST_SHIFT				14	/* BIT_14 */

#define SD_AHB_EN_SHIFT					8	/* BIT_8 */
#define SD_AHB_RST_SHIFT				        11	/* BIT_11 */

/********** base clock set ************/
#define EMMC_BASE_CLK					SDIO_BASE_CLK_26M
#define EMMC_BASE_AP_CKG				(CTL_BASE_AP_CKG + 0x54)

/********** master slot select *********/
#define EMMC_SLOT_SEL_REG				REG_AP_AHB_MISC_CFG
#if defined(CONFIG_ARCH_SCX35L)
#define EMMC_SLOT_SEL_BIT                               0
#define EMMC_SLOT_SEL_MSK                               (BIT(18)|BIT(19))
#else
#define EMMC_SLOT_SEL_BIT				BIT_EMMC_SLOT_SEL
#define EMMC_SLOT_SEL_MSK                               BIT_EMMC_SLOT_SEL
#endif
/******* emmc voltage controller *******/
#if defined (CONFIG_SPX15)

#define EMMC_VOLT_IO_CTRL				ANA_REG_GLB_LDO_V_CTRL2
#define EMMC_VOLT_CORE_CTRL				ANA_REG_GLB_LDO_V_CTRL7

#define EMMC_VOLT_IO_CTRL_SHIFT				0	/* [6:0] */
#define EMMC_VOLT_CORE_CTRL_SHIFT			8	/* [15:8] */

#define EMMC_VOLT_IO_MASK				0x7f
#define EMMC_VOLT_CORE_MASK                             0xff
#else
#if defined CONFIG_ADIE_SC2723S || defined CONFIG_ADIE_SC2723
#define EMMC_VOLT_IO_CTRL				ANA_REG_GLB_LDO_V_CTRL3
#define EMMC_VOLT_CORE_CTRL			ANA_REG_GLB_LDO_V_CTRL7

#define EMMC_VOLT_IO_CTRL_SHIFT		8	/* [13:12] */
#define EMMC_VOLT_CORE_CTRL_SHIFT		8	/* [15:14] */
#define EMMC_VOLT_IO_MASK				0x7f
#define EMMC_VOLT_CORE_MASK                             0xff
#else
#define EMMC_VOLT_IO_CTRL				ANA_REG_GLB_LDO_V_CTRL0
#define EMMC_VOLT_CORE_CTRL			ANA_REG_GLB_LDO_V_CTRL0

#define EMMC_VOLT_IO_CTRL_SHIFT			12	/* [13:12] */
#define EMMC_VOLT_CORE_CTRL_SHIFT		14	/* [15:14] */
#define EMMC_VOLT_MASK					0x3
#endif
#endif

#endif
