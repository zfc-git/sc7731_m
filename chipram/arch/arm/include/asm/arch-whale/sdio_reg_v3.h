/******************************************************************************
 ** File Name:    sdio_reg_v3.h                                           *
 ** Author:       Binggo.Zhou                                               *
 ** DATE:         02/03/2010                                                  *
 ** Copyright:    2005 Spreatrum, Incoporated. All Rights Reserved.           *
 ** Description:                                                              *
 ******************************************************************************/
/******************************************************************************
 **                   Edit    History                                         *
 **---------------------------------------------------------------------------*
 ** DATE          NAME            DESCRIPTION                                 *
 ** 02/03/2010    Binggo.Zhou     Create.                                     *
 ******************************************************************************/


#ifndef _SDIO_REG_V3_H_
#define _SDIO_REG_V3_H_


//-jason.wu confirm start

#define SDIO0_SYS_ADDR          (SDIO0_BASE_ADDR+0x0)
#define SDIO0_BLK_SIZE          (SDIO0_BASE_ADDR+0x4)
#define SDIO0_BLK_CNT           (SDIO0_BASE_ADDR+0x6)
#define SDIO0_ARGU_REG          (SDIO0_BASE_ADDR+0x8)
#define SDIO0_TRANS_MODE        (SDIO0_BASE_ADDR+0xc)
#define SDIO0_CMD_REG           (SDIO0_BASE_ADDR+0xE)
#define SDIO0_RESPONSE_REG      (SDIO0_BASE_ADDR+0x10)
#define SDIO0_BUF_DATA_PORT (SDIO0_BASE_ADDR+0x20)
#define SDIO0_PRESENT_STATE (SDIO0_BASE_ADDR+0x24)
#define SDIO0_HC_CTL            (SDIO0_BASE_ADDR+0x28)
#define SDIO0_PWR_CTL           (SDIO0_BASE_ADDR+0x29)
#define SDIO0_BLK_GAP           (SDIO0_BASE_ADDR+0x2A)
#define SDIO0_WAKEUP_CTL        (SDIO0_BASE_ADDR+0x2B)
#define SDIO0_CLK_CTL           (SDIO0_BASE_ADDR+0x2C)
#define SDIO0_TIMEOUT_CTL       (SDIO0_BASE_ADDR+0x2E)
#define SDIO0_SW_RESET          (SDIO0_BASE_ADDR+0x2F)
#define SDIO0_NML_INT_STS       (SDIO0_BASE_ADDR+0x30)
#define SDIO0_ERR_INT_STS       (SDIO0_BASE_ADDR+0x32)
#define SDIO0_NML_INT_STS_EN    (SDIO0_BASE_ADDR+0x34)
#define SDIO0_ERR_INT_STS_EN    (SDIO0_BASE_ADDR+0x36)
#define SDIO0_NML_INT_SIG_EN    (SDIO0_BASE_ADDR+0x38)
#define SDIO0_ERR_INT_SIG_EN    (SDIO0_BASE_ADDR+0x3A)
#define SDIO0_ACMD12_ERRSTS (SDIO0_BASE_ADDR+0x3C)
#define SDIO0_CAPBILITY_REG (SDIO0_BASE_ADDR+0x40)
#define SDIO0_MAX_CUR_CAP_REG   (SDIO0_BASE_ADDR+0x48)
#define SDIO0_SLOT_INT_STS      (SDIO0_BASE_ADDR+0xFC)
#define SDIO0_HC_VER_REG        (SDIO0_BASE_ADDR+0xFE)

#define SDIO1_SYS_ADDR          (SDIO1_BASE_ADDR+0x0)
#define SDIO1_BLK_SIZE          (SDIO1_BASE_ADDR+0x4)
#define SDIO1_BLK_CNT           (SDIO1_BASE_ADDR+0x6)
#define SDIO1_ARGU_REG          (SDIO1_BASE_ADDR+0x8)
#define SDIO1_TRANS_MODE        (SDIO1_BASE_ADDR+0xc)
#define SDIO1_CMD_REG           (SDIO1_BASE_ADDR+0xE)
#define SDIO1_RESPONSE_REG      (SDIO1_BASE_ADDR+0x10)
#define SDIO1_BUF_DATA_PORT (SDIO1_BASE_ADDR+0x20)
#define SDIO1_PRESENT_STATE (SDIO1_BASE_ADDR+0x24)
#define SDIO1_HC_CTL            (SDIO1_BASE_ADDR+0x28)
#define SDIO1_PWR_CTL           (SDIO1_BASE_ADDR+0x29)
#define SDIO1_BLK_GAP           (SDIO1_BASE_ADDR+0x2A)
#define SDIO1_WAKEUP_CTL        (SDIO1_BASE_ADDR+0x2B)
#define SDIO1_CLK_CTL           (SDIO1_BASE_ADDR+0x2C)
#define SDIO1_TIMEOUT_CTL       (SDIO1_BASE_ADDR+0x2E)
#define SDIO1_SW_RESET          (SDIO1_BASE_ADDR+0x2F)
#define SDIO1_NML_INT_STS       (SDIO1_BASE_ADDR+0x30)
#define SDIO1_ERR_INT_STS       (SDIO1_BASE_ADDR+0x32)
#define SDIO1_NML_INT_STS_EN    (SDIO1_BASE_ADDR+0x34)
#define SDIO1_ERR_INT_STS_EN    (SDIO1_BASE_ADDR+0x36)
#define SDIO1_NML_INT_SIG_EN    (SDIO1_BASE_ADDR+0x38)
#define SDIO1_ERR_INT_SIG_EN    (SDIO1_BASE_ADDR+0x3A)
#define SDIO1_ACMD12_ERRSTS (SDIO1_BASE_ADDR+0x3C)
#define SDIO1_CAPBILITY_REG (SDIO1_BASE_ADDR+0x40)
#define SDIO1_MAX_CUR_CAP_REG   (SDIO1_BASE_ADDR+0x48)
#define SDIO1_SLOT_INT_STS      (SDIO1_BASE_ADDR+0xFC)
#define SDIO1_HC_VER_REG        (SDIO1_BASE_ADDR+0xFE)

#define SDIO2_SYS_ADDR          (SDIO2_BASE_ADDR+0x0)
#define SDIO2_BLK_SIZE          (SDIO2_BASE_ADDR+0x4)
#define SDIO2_BLK_CNT           (SDIO2_BASE_ADDR+0x6)
#define SDIO2_ARGU_REG          (SDIO2_BASE_ADDR+0x8)
#define SDIO2_TRANS_MODE        (SDIO2_BASE_ADDR+0xc)
#define SDIO2_CMD_REG           (SDIO2_BASE_ADDR+0xE)
#define SDIO2_RESPONSE_REG      (SDIO2_BASE_ADDR+0x10)
#define SDIO2_BUF_DATA_PORT (SDIO2_BASE_ADDR+0x20)
#define SDIO2_PRESENT_STATE (SDIO2_BASE_ADDR+0x24)
#define SDIO2_HC_CTL            (SDIO2_BASE_ADDR+0x28)
#define SDIO2_PWR_CTL           (SDIO2_BASE_ADDR+0x29)
#define SDIO2_BLK_GAP           (SDIO2_BASE_ADDR+0x2A)
#define SDIO2_WAKEUP_CTL        (SDIO2_BASE_ADDR+0x2B)
#define SDIO2_CLK_CTL           (SDIO2_BASE_ADDR+0x2C)
#define SDIO2_TIMEOUT_CTL       (SDIO2_BASE_ADDR+0x2E)
#define SDIO2_SW_RESET          (SDIO2_BASE_ADDR+0x2F)
#define SDIO2_NML_INT_STS       (SDIO2_BASE_ADDR+0x30)
#define SDIO2_ERR_INT_STS       (SDIO2_BASE_ADDR+0x32)
#define SDIO2_NML_INT_STS_EN    (SDIO2_BASE_ADDR+0x34)
#define SDIO2_ERR_INT_STS_EN    (SDIO2_BASE_ADDR+0x36)
#define SDIO2_NML_INT_SIG_EN    (SDIO2_BASE_ADDR+0x38)
#define SDIO2_ERR_INT_SIG_EN    (SDIO2_BASE_ADDR+0x3A)
#define SDIO2_ACMD12_ERRSTS (SDIO2_BASE_ADDR+0x3C)
#define SDIO2_CAPBILITY_REG (SDIO2_BASE_ADDR+0x40)
#define SDIO2_MAX_CUR_CAP_REG   (SDIO2_BASE_ADDR+0x48)
#define SDIO2_SLOT_INT_STS      (SDIO2_BASE_ADDR+0xFC)
#define SDIO2_HC_VER_REG        (SDIO2_BASE_ADDR+0xFE)

#define EMMC_SYS_ADDR          (EMMC_BASE_ADDR+0x0)
#define EMMC_BLK_SIZE          (EMMC_BASE_ADDR+0x4)
#define EMMC_BLK_CNT           (EMMC_BASE_ADDR+0x6)
#define EMMC_ARGU_REG          (EMMC_BASE_ADDR+0x8)
#define EMMC_TRANS_MODE        (EMMC_BASE_ADDR+0xc)
#define EMMC_CMD_REG           (EMMC_BASE_ADDR+0xE)
#define EMMC_RESPONSE_REG      (EMMC_BASE_ADDR+0x10)
#define EMMC_BUF_DATA_PORT     (EMMC_BASE_ADDR+0x20)
#define EMMC_PRESENT_STATE     (EMMC_BASE_ADDR+0x24)
#define EMMC_HC_CTL            (EMMC_BASE_ADDR+0x28)
#define EMMC_PWR_CTL           (EMMC_BASE_ADDR+0x29)
#define EMMC_BLK_GAP           (EMMC_BASE_ADDR+0x2A)
#define EMMC_WAKEUP_CTL        (EMMC_BASE_ADDR+0x2B)
#define EMMC_CLK_CTL           (EMMC_BASE_ADDR+0x2C)
#define EMMC_TIMEOUT_CTL       (EMMC_BASE_ADDR+0x2E)
#define EMMC_SW_RESET          (EMMC_BASE_ADDR+0x2F)
#define EMMC_NML_INT_STS       (EMMC_BASE_ADDR+0x30)
#define EMMC_ERR_INT_STS       (EMMC_BASE_ADDR+0x32)
#define EMMC_NML_INT_STS_EN    (EMMC_BASE_ADDR+0x34)
#define EMMC_ERR_INT_STS_EN    (EMMC_BASE_ADDR+0x36)
#define EMMC_NML_INT_SIG_EN    (EMMC_BASE_ADDR+0x38)
#define EMMC_ERR_INT_SIG_EN    (EMMC_BASE_ADDR+0x3A)
#define EMMC_ACMD12_ERRSTS     (EMMC_BASE_ADDR+0x3C)
#define EMMC_CAPBILITY_REG     (EMMC_BASE_ADDR+0x40)
#define EMMC_MAX_CUR_CAP_REG   (EMMC_BASE_ADDR+0x48)
#define EMMC_FORCE_EVENT       (EMMC_BASE_ADDR+0x50)
#define EMMC_PRE_VAL_DEF       (EMMC_BASE_ADDR+0x60)
#define EMMC_PRE_VAL_HIGH      (EMMC_BASE_ADDR+0x64)
#define EMMC_PRE_VAL_SDR50     (EMMC_BASE_ADDR+0x68)
#define EMMC_PRE_VAL_DDR50     (EMMC_BASE_ADDR+0x6C)
#define EMMC_CLK_WR_DL         (EMMC_BASE_ADDR+0x80)
#define EMMC_CLK_RD_POS_DL     (EMMC_BASE_ADDR+0x84)
#define EMMC_CLK_RD_NEG_DL     (EMMC_BASE_ADDR+0x88)
#define EMMC_SLOT_INT_STS      (EMMC_BASE_ADDR+0xFC)
#define EMMC_HC_VER_REG        (EMMC_BASE_ADDR+0xFE)

typedef struct SDIO_REG_CFG_TAG
{

    volatile uint32 DMA_BLK_COUNT;
    volatile uint32 DMA_BLK_SIZE;
    volatile uint32 CMD_ARGUMENT;
    volatile uint32 CMD_TRANSMODE;
    volatile uint32 RSP0;
    volatile uint32 RSP1;
    volatile uint32 RSP2;
    volatile uint32 RSP3;
    volatile uint32 BUFFER_PORT;
    volatile uint32 PRESENT_STAT;
    volatile uint32 HOST_CTL1;
    volatile uint32 EMMC_CLK_CTRL;
    volatile uint32 INT_STA;
    volatile uint32 INT_STA_EN;
    volatile uint32 INT_SIG_EN;
    volatile uint32 HOST_CTL2;
    volatile uint32 CAPBILITY;
    volatile uint32 CAPBILITY_RES;
    volatile uint32 EMMC_SDHC_RED48;
    volatile uint32 EMMC_SDHC_RED4C;
    volatile uint32 EMMC_FORCE_EVT;
    volatile uint32 EMMC_ADMA_ERR;
    volatile uint32 EMMC_ADMA2_ADDR_L;
    volatile uint32 EMMC_ADMA2_ADDR_H;
}
SDIO_REG_CFG;


//---

//=====
//define transfer mode and command mode...
//command mode
#define SDIO_CMD_TYPE_ABORT                 (3<<22)
#define SDIO_CMD_TYPE_RESUME                    (2<<22)
#define SDIO_CMD_TYPE_SUSPEND                   (1<<22)
#define SDIO_CMD_TYPE_NML                       (0<<22)

#define SDIO_CMD_DATA_PRESENT                   BIT_21

#define SDIO_CMD_INDEX_CHK                  BIT_20
#define SDIO_CMD_CRC_CHK                        BIT_19
#define SDIO_CMD_NO_RSP                     (0x00<<16)
#define SDIO_CMD_RSP_136                        (0x01<<16)
#define SDIO_CMD_RSP_48                     (0x02<<16)
#define SDIO_CMD_RSP_48_BUSY                    (0x03<<16)

#define SDIO_NO_RSP     0x0;
#define SDIO_R1     ( SDIO_CMD_RSP_48 | SDIO_CMD_INDEX_CHK | SDIO_CMD_CRC_CHK )
#define SDIO_R2     ( SDIO_CMD_RSP_136 | SDIO_CMD_CRC_CHK )
#define SDIO_R3     SDIO_CMD_RSP_48
#define SDIO_R4     SDIO_CMD_RSP_48
#define SDIO_R5     ( SDIO_CMD_RSP_48 | SDIO_CMD_INDEX_CHK | SDIO_CMD_CRC_CHK )
#define SDIO_R6     ( SDIO_CMD_RSP_48 | SDIO_CMD_INDEX_CHK | SDIO_CMD_CRC_CHK )
#define SDIO_R7     ( SDIO_CMD_RSP_48 | SDIO_CMD_INDEX_CHK | SDIO_CMD_CRC_CHK )
#define SDIO_R1B    ( SDIO_CMD_RSP_48_BUSY | SDIO_CMD_INDEX_CHK | SDIO_CMD_CRC_CHK )
#define SDIO_R5B    ( SDIO_CMD_RSP_48_BUSY | SDIO_CMD_INDEX_CHK | SDIO_CMD_CRC_CHK )

//transfer mode
#define SDIO_TRANS_COMP_ATA         BIT_6
#define SDIO_TRANS_MULTIBLK         BIT_5
#define SDIO_TRANS_DIR_READ         BIT_4
#define SDIO_TRANS_AUTO_CMD12_EN        BIT_2
#define SDIO_TRANS_BLK_CNT_EN           BIT_1
#define SDIO_TRANS_DMA_EN               BIT_0

//define normal and error sts index...
#define SDIO_VENDOR_SPEC_ERR        (BIT_29|BIT_30|BIT_31)
#define SDIO_TARGET_RESP_ERR        (BIT_28)
#define SDIO_AUTO_CMD12_ERR         (BIT_24)
#define SDIO_CURRENT_LMT_ERR        (BIT_23)
#define SDIO_DATA_ENDBIT_ERR        (BIT_22)
#define SDIO_DATA_CRC_ERR           (BIT_21)
#define SDIO_DATA_TMOUT_ERR         (BIT_20)
#define SDIO_CMD_INDEX_ERR          (BIT_19)
#define SDIO_CMD_ENDBIT_ERR         (BIT_18)
#define SDIO_CMD_CRC_ERR            (BIT_17)
#define SDIO_CMD_TMOUT_ERR          (BIT_16)
#define SDIO_ERROR_INT              (BIT_15)
#define SDIO_CARD_INT               (BIT_8)
#define SDIO_CARD_REMOVAL           (BIT_7)
#define SDIO_CARD_INSERTION         (BIT_6)
#define SDIO_BUF_READ_RDY           (BIT_5)
#define SDIO_BUF_WRITE_RDY          (BIT_4)
#define SDIO_DMA_INT                (BIT_3)
#define SDIO_BLK_GAP_EVT            (BIT_2)
#define SDIO_TRANSFER_CMPLETE       (BIT_1)
#define SDIO_CMD_CMPLETE            (BIT_0)

//-jason.wu confirm end

#define SDIO_BASE_CLK_400K				400000
#define SDIO_BASE_CLK_26M				26000000			/* 26 MHz */
#define SDIO_BASE_CLK_48M				48000000			/* 48 MHz */
#define SDIO_BASE_CLK_153P6M			153600000			/* 153.6M */
#define SDIO_BASE_CLK_192M				192000000			/* 192 MHz */
#define SDIO_BASE_CLK_256M				256000000			/* 256 MHz */
#define SDIO_BASE_CLK_312M				312000000			/* 312 MHz */
#define SDIO_BASE_CLK_384M				384000000			/* 384 MHz */

#define NULL								0x0 

#define EMMC_AHB_EN_SHIFT				10 
#define EMMC_AHB_RST_SHIFT				11 
/********** dma mode **************/
#define EMMC_SDMA_MODE             0x00000018

/********** datawidth ***************/
#define EMMC_ENABLE_64BIT         0x20000000

/********** base clock set ************/
#define EMMC_BASE_CLK					SDIO_BASE_CLK_26M
#define EMMC_BASE_AP_CKG				(CTL_BASE_AP_CKG + 0x54)

/********** master slot select *********/
#define EMMC_SLOT_SEL_REG				REG_AP_AHB_MISC_CFG
#define EMMC_SLOT_SEL_BIT                               0
#define EMMC_SLOT_SEL_MSK                               (BIT(18)|BIT(19))

/******* emmc voltage controller *******/
#define EMMC_VOLT_IO_CTRL				ANA_REG_GLB_DCDC_GEN_VOL
#define EMMC_VOLT_CORE_CTRL			ANA_REG_GLB_LDO_EMMCCORE_REG1

#define EMMC_VOLT_IO_CTRL_SHIFT			0					/* [13:12] */
#define EMMC_VOLT_CORE_CTRL_SHIFT		0					/* [15:14] */
#define EMMC_VOLT_IO_MASK                               0xff
#define EMMC_VOLT_CORE_MASK                             0xff
/**************************   Card info   *********************************/
#define CARD_DATA_BLOCK_LEN				512

#define PARTITION_ENABLE_BOOT1_OFFSET	3
#define PARTITION_CFG_ENABLE_BOOT1		(1 << PARTITION_ENABLE_BOOT1_OFFSET)

#define PARTITION_CFG_ACCESS_USER		0
#define PARTITION_CFG_ACCESS_BOOT1		1
#define PARTITION_CFG_ACCESS_BOOT2		2
#define PARTITION_CFG_ACCESS_RPMB		3
#define PARTITION_CFG_ACCESS_GEN_P1		4
#define PARTITION_CFG_ACCESS_GEN_P2		5
#define PARTITION_CFG_ACCESS_GEN_P3		6
#define PARTITION_CFG_ACCESS_GEN_P4		7

#define EXT_CSD_CARD_WIDTH_1_BIT		0
#define EXT_CSD_CARD_WIDTH_4_BIT		1
#define EXT_CSD_CARD_WIDTH_8_BIT		2

#define CMD6_BIT_MODE_OFFSET_ACCESS	24
#define CMD6_BIT_MODE_MASK_ACCESS		0x03000000
#define CMD6_BIT_MODE_OFFSET_INDEX		16
#define CMD6_BIT_MODE_MASK_INDEX		0x00FF0000
#define CMD6_BIT_MODE_OFFSET_VALUE		8
#define CMD6_BIT_MODE_MASK_VALUE		0x0000FF00
#define CMD6_BIT_MODE_OFFSET_CMD_SET	0
#define CMD6_BIT_MODE_MASK_CMD_SET		0x00000003

#define CMD6_ACCESS_MODE_COMMAND_SET	(0 << CMD6_BIT_MODE_OFFSET_ACCESS)
#define CMD6_ACCESS_MODE_SET_BITS		(1 << CMD6_BIT_MODE_OFFSET_ACCESS)
#define CMD6_ACCESS_MODE_CLEAR_BITS	(2 << CMD6_BIT_MODE_OFFSET_ACCESS)
#define CMD6_ACCESS_MODE_WRITE_BYTE	(3 << CMD6_BIT_MODE_OFFSET_ACCESS)
#define CMD6_CMD_SET					(1 << CMD6_BIT_MODE_OFFSET_CMD_SET)

#define EXT_CSD_PARTITION_CFG_INDEX		179
#define EXT_CSD_BUS_WIDTH_INDEX			183			/* R/W */
#define EXT_CSD_HS_TIMING_INDEX			185			/* R/W */
#define EXT_CSD_CARD_TYPE_INDEX			196			/* RO */
#define EXT_CSD_SEC_CNT_INDEX			212			/* RO, 4 bytes */

#define SECTOR_MODE						0x40000000
#define BYTE_MODE						0x00000000 

/**************************   control info    *******************************/
typedef enum SDIO_VOL_E_TAG
{
	VOL_1_2								= 1200,
	VOL_1_3								= 1300,
	VOL_1_5								= 1500,
	VOL_1_8								= 1800,
	VOL_2_5								= 2500,
	VOL_2_8								= 2800,
	VOL_3_0								= 3000,
	VOL_RES
} SDIO_Vol_e;

typedef enum CARD_PARTITION_TAG
{
	PARTITION_USER,
	PARTITION_BOOT1,
	PARTITION_BOOT2,
	PARTITION_RPMB,
	PARTITION_GENERAL_P1,
	PARTITION_GENERAL_P2,
	PARTITION_GENERAL_P3,
	PARTITION_GENERAL_P4,
	PARTITION_MAX
} CARD_Partition_e;

typedef struct DATA_PARAM_TAG
{
	uint8								*data_buf;	/* the buffer address */
	uint32								blk_len;		/* block size */
	uint32								blk_num;		/* block number */
} DATA_Param_t;

typedef enum SDIO_ERROR_TAG
{
	SDIO_ERR_NONE						= 0,
	SDIO_ERR_RSP						= BIT_0,
	SDIO_ERR_CMD12						= BIT_1,
	SDIO_ERR_CUR_LIMIT					= BIT_2,
	SDIO_ERR_DATA_END					= BIT_3,
	SDIO_ERR_DATA_CRC					= BIT_4,
	SDIO_ERR_DATA_TIMEOUT				= BIT_5,
	SDIO_ERR_CMD_INDEX					= BIT_6,
	SDIO_ERR_CMD_END					= BIT_7,
	SDIO_ERR_CMD_CRC					= BIT_8,
	SDIO_ERR_CMD_TIMEOUT				= BIT_9
} SDIO_Error_e;

/**************************   card info    *********************************/
typedef enum SDIO_RST_TYPE_TAG
{
	RST_CMD_LINE,
	RST_DAT_LINE,
	RST_CMD_DAT_LINE,
	RST_ALL,
	RST_MODULE
} SDIO_Rst_e;

/**************************   emmc base handle    **************************/
typedef enum SDIO_BUSWIDTH_TAG
{
	SDIO_BUS_1_BIT,
	SDIO_BUS_4_BIT,
	SDIO_BUS_8_BIT
} SDIO_BusWidth_e;

typedef enum SDIO_ONOFF_TAG
{
	SDIO_OFF,
	SDIO_ON
} SDIO_OnOff_e;

typedef void (*SDIO_CALLBACK)(uint32 msg, uint32 errCode);

typedef struct SDIO_HANDLE_T_TAG
{
	volatile SDIO_REG_CFG					*host_cfg;	/* register configuration */
	BOOLEAN								open_flag;	/* open flag */
	uint32								base_clock;	/* base clock */
	uint32								sd_clock;
	uint32								err_filter;
	SDIO_CALLBACK						sig_callBack;
	volatile uint32							card_event;
	volatile uint32							card_errCode;

	uint16								rca;			/* card RCA */
	SDIO_BusWidth_e						bus_width;	/* card used bus width */
	uint32								block_len;	/* card block length */
	CARD_Partition_e						cur_partition;	/* card current partition */
} SDIO_Handle_t, *SDIO_Hd_Ptr;

/**************************   cmd info    *********************************/
#define TRANS_MODE_CMPLETE_SIG_EN		BIT_0
#define TRANS_MODE_MULTI_BLOCK			BIT_1
#define TRANS_MODE_READ					BIT_2
#define TRANS_MODE_CMD12_EN			BIT_3
#define TRANS_MODE_BLOCK_COUNT_EN		BIT_4
#define TRANS_MODE_DMA_EN				BIT_5
#define CMD_HAVE_DATA					BIT_6

typedef enum CMD_RSP_TYPE_E_TAG
{
	CMD_NO_RSP = 0,
	CMD_RSP_R1,
	CMD_RSP_R2,
	CMD_RSP_R3,
	CMD_RSP_R4,
	CMD_RSP_R5,
	CMD_RSP_R6,
	CMD_RSP_R7,
	CMD_RSP_R1B,
	CMD_RSP_R5B
} CARD_CmdRsp_e;

typedef enum SDIO_CMD_TYPE_E_TAG
{
	CMD_TYPE_NORMAL = 0,
	CMD_TYPE_SUSPEND,
	CMD_TYPE_RESUME,
	CMD_TYPE_ABORT
} SDIO_CmdType_e;

#define SIG_ERR							BIT_0
#define SIG_CARD_IN						BIT_1
#define SIG_CARD_INSERT					BIT_2
#define SIG_CARD_REMOVE					BIT_3
#define SIG_BUF_RD_RDY					BIT_4
#define SIG_BUF_WD_RDY					BIT_5
#define SIG_DMA_INT						BIT_6
#define SIG_BLK_CAP						BIT_7
#define SIG_TRANS_CMP					BIT_8
#define SIG_CMD_CMP						BIT_9
#define SIG_ALL							(SIG_ERR | SIG_CARD_IN | SIG_CARD_INSERT \
										| SIG_CARD_REMOVE | SIG_BUF_RD_RDY \
										| SIG_BUF_WD_RDY | SIG_DMA_INT \
										| SIG_BLK_CAP | SIG_TRANS_CMP \
										| SIG_CMD_CMP)

#define ERR_RSP							BIT_0
#define ERR_CMD12						BIT_1
#define ERR_CUR_LIMIT					BIT_2
#define ERR_DATA_END					BIT_3
#define ERR_DATA_CRC					BIT_4
#define ERR_DATA_TIMEOUT				BIT_5
#define ERR_CMD_INDEX					BIT_6
#define ERR_CMD_END						BIT_7
#define ERR_CMD_CRC						BIT_8
#define ERR_CMD_TIMEOUT					BIT_9
#define ERR_ALL							(ERR_RSP | ERR_CMD12 | ERR_CUR_LIMIT \
										| ERR_DATA_END | ERR_DATA_CRC \
										| ERR_DATA_TIMEOUT | ERR_CMD_INDEX \
										| ERR_CMD_END | ERR_CMD_CRC \
										| ERR_CMD_TIMEOUT)

//      response Name       cmd int filter                                                      ,rsp            ,cmd error filter
#define CARD_SDIO_NO_RSP				NULL/*|SIG_CARD_IN|SIG_CARD_INSERT|SIG_CARD_REMOVE*/|SIG_CMD_CMP    ,CMD_NO_RSP     ,NULL
#define CARD_SDIO_R1						NULL/*|SIG_CARD_IN|SIG_CARD_INSERT|SIG_CARD_REMOVE*/|SIG_CMD_CMP    ,CMD_RSP_R1     ,ERR_RSP|ERR_CMD_INDEX| ERR_CMD_END|    ERR_CMD_CRC|    ERR_CMD_TIMEOUT
#define CARD_SDIO_R2						NULL/*|SIG_CARD_IN|SIG_CARD_INSERT|SIG_CARD_REMOVE*/|SIG_CMD_CMP    ,CMD_RSP_R2     ,ERR_RSP|                           ERR_CMD_END|    ERR_CMD_CRC|    ERR_CMD_TIMEOUT
#define CARD_SDIO_R3						NULL/*|SIG_CARD_IN|SIG_CARD_INSERT|SIG_CARD_REMOVE*/|SIG_CMD_CMP    ,CMD_RSP_R3     ,ERR_RSP|                           ERR_CMD_END|                                ERR_CMD_TIMEOUT
#define CARD_SDIO_R4						NULL/*|SIG_CARD_IN|SIG_CARD_INSERT|SIG_CARD_REMOVE*/|SIG_CMD_CMP    ,CMD_RSP_R4     ,ERR_RSP|ERR_CMD_INDEX| ERR_CMD_END|    ERR_CMD_CRC|    ERR_CMD_TIMEOUT
#define CARD_SDIO_R5						NULL/*|SIG_CARD_IN|SIG_CARD_INSERT|SIG_CARD_REMOVE*/|SIG_CMD_CMP    ,CMD_RSP_R5     ,ERR_RSP|ERR_CMD_INDEX| ERR_CMD_END|    ERR_CMD_CRC|    ERR_CMD_TIMEOUT
#define CARD_SDIO_R6						NULL/*|SIG_CARD_IN|SIG_CARD_INSERT|SIG_CARD_REMOVE*/|SIG_CMD_CMP    ,CMD_RSP_R6     ,ERR_RSP|ERR_CMD_INDEX| ERR_CMD_END|    ERR_CMD_CRC|    ERR_CMD_TIMEOUT
#define CARD_SDIO_R7						NULL/*|SIG_CARD_IN|SIG_CARD_INSERT|SIG_CARD_REMOVE*/|SIG_CMD_CMP    ,CMD_RSP_R7     ,ERR_RSP|ERR_CMD_INDEX| ERR_CMD_END|    ERR_CMD_CRC|    ERR_CMD_TIMEOUT
#define CARD_SDIO_R1B					NULL/*|SIG_CARD_IN|SIG_CARD_INSERT|SIG_CARD_REMOVE*/|SIG_CMD_CMP    ,CMD_RSP_R1B   ,ERR_RSP|ERR_CMD_INDEX| ERR_CMD_END|    ERR_CMD_CRC|    ERR_CMD_TIMEOUT

typedef enum CARD_CMD_E_TAG
{
	// cmdindex,rsp,transmode
	CARD_CMD0_GO_IDLE_STATE,
	CARD_CMD1_SEND_OP_COND, 			/* MMC */
	CARD_CMD2_ALL_SEND_CID,
	CARD_CMD3_SET_RELATIVE_ADDR,		/* MMC */
	CARD_CMD7_SELECT_DESELECT_CARD,
	CARD_CMD12_STOP_TRANSMISSION,		/* It is auto performed by Host */
	CARD_CMD13_SEND_STATUS,
	CARD_CMD16_SET_BLOCKLEN,
	CARD_CMD18_READ_MULTIPLE_BLOCK,
	CARD_ACMD6_SET_EXT_CSD,
	CARD_CMDMAX
} CARD_Cmd_e;

typedef struct CARD_CMDCTLFLG_T_TAG
{
	CARD_Cmd_e							cmd;
	uint32								cmd_index;
	uint32								int_filter;
	CARD_CmdRsp_e						response;
	uint32								err_filter;
	uint32								transmode;
} CARD_CmdCtlFlg_t;



#endif //_SDIO_REG_V3_H_

