/*
 * Copyright 2011, Marvell Semiconductor Inc.
 * Lei Wen <leiwen@marvell.com>
 *
 * SPDX-License-Identifier:	GPL-2.0+
 *
 * Back ported to the 8xx platform (from the 8260 platform) by
 * Murray.Jensen@cmst.csiro.au, 27-Jan-01.
 */

#include <common.h>
#include <malloc.h>
#include <mmc.h>
#include <sdhci_v40.h>

void *aligned_buffer;

#define CHIP_REG_GET(reg_addr)          (*(volatile u32 *)(reg_addr))

static void sdhci_reset(struct sdhci_host *host, u8 mask)
{
	unsigned long timeout;

	/* Wait max 100 ms */
	timeout = 100;
	sdhci_writeb(host, mask, SDHCI_SOFTWARE_RESET);
	while (sdhci_readb(host, SDHCI_SOFTWARE_RESET) & mask) {
		if (timeout == 0) {
			printf("%s: Reset 0x%x never completed.\n", __func__, (int)mask);
			return;
		}
		timeout--;
		udelay(1000);
	}
}

static void sdhci_cmd_done(struct sdhci_host *host, struct mmc_cmd *cmd)
{
	int i;
	if (cmd->resp_type & MMC_RSP_136) {
		/* CRC is stripped so we need to do some shifting. */
		for (i = 0; i < 4; i++) {
			cmd->response[i] = sdhci_readl(host, SDHCI_RESPONSE + (3 - i) * 4) << 8;
			if (i != 3)
				cmd->response[i] |= sdhci_readb(host, SDHCI_RESPONSE + (3 - i) * 4 - 1);
		}
	} else {
		cmd->response[0] = sdhci_readl(host, SDHCI_RESPONSE);
	}
}

static void sdhci_transfer_pio(struct sdhci_host *host, struct mmc_data *data)
{
	int i;
	char *offs;
	for (i = 0; i < data->blocksize; i += 4) {
		offs = data->dest + i;
		if (data->flags == MMC_DATA_READ)
			*(u32 *) offs = sdhci_readl(host, SDHCI_BUFFER);
		else
			sdhci_writel(host, *(u32 *) offs, SDHCI_BUFFER);
	}
}

static int sdhci_transfer_data(struct sdhci_host *host, struct mmc_data *data, unsigned int start_addr)
{
	unsigned int stat, rdy, mask, timeout, block = 0;

	timeout = 90000000;
	rdy = SDHCI_INT_SPACE_AVAIL | SDHCI_INT_DATA_AVAIL;
	mask = SDHCI_DATA_AVAILABLE | SDHCI_SPACE_AVAILABLE;

	do {
		stat = sdhci_readl(host, SDHCI_INT_STATUS);
		if (stat & SDHCI_INT_ERROR) {
			printf("%s: Error detected in status(0x%X)!\n", __func__, stat);
			return -1;
		}
		if (stat & rdy) {
			if (!(sdhci_readl(host, SDHCI_PRESENT_STATE) & mask))
				continue;
			sdhci_writel(host, rdy, SDHCI_INT_STATUS);
			sdhci_transfer_pio(host, data);
			data->dest += data->blocksize;
			if (++block >= data->blocks)
				break;
		}
#ifdef CONFIG_MMC_SDMA
		if (stat & SDHCI_INT_DMA_END) {
			sdhci_writel(host, SDHCI_INT_DMA_END, SDHCI_INT_STATUS);
			start_addr &= ~(SDHCI_DEFAULT_BOUNDARY_SIZE - 1);
			start_addr += SDHCI_DEFAULT_BOUNDARY_SIZE;
			sdhci_writel(host, (u32) (((u64) start_addr) & 0xFFFFFFFF), SDHCI_DMA_ADDRESS_LOW);
			sdhci_writel(host, (u32) (((u64) start_addr >> 32) & 0xFFFFFFFF), SDHCI_DMA_ADDRESS_HIGH);
		}
#endif
		if (timeout-- > 0)
			udelay(10);
		else {
			return -1;
		}
	} while (!(stat & SDHCI_INT_DATA_END));
	return 0;
}

/*
 * No command will be sent by driver if card is busy, so driver must wait
 * for card ready state.
 * Every time when card is busy after timeout then (last) timeout value will be
 * increased twice but only if it doesn't exceed global defined maximum.
 * Each function call will use last timeout value. Max timeout can be redefined
 * in board config file.
 */
#ifndef CONFIG_SDHCI_CMD_MAX_TIMEOUT
#define CONFIG_SDHCI_CMD_MAX_TIMEOUT		32000	//3200
#endif
#define CONFIG_SDHCI_CMD_DEFAULT_TIMEOUT	1000	//100

int sdhci_send_command(struct mmc *mmc, struct mmc_cmd *cmd, struct mmc_data *data)
{
	struct sdhci_host *host = (struct sdhci_host *)mmc->priv;
	unsigned int stat = 0;
	int ret = 0;
	int trans_bytes = 0, is_aligned = 1;
	u32 mask, flags, mode;
	unsigned int time = 0;
	unsigned long start_addr = 0;
	unsigned int retry = 8000000;
	int mmc_dev = mmc->block_dev.dev;

	unsigned int ultemp = 0;

	/* Timeout unit - ms */
	static unsigned int cmd_timeout = CONFIG_SDHCI_CMD_DEFAULT_TIMEOUT;

	sdhci_writel(host, SDHCI_INT_ALL_MASK, SDHCI_INT_STATUS);
	mask = SDHCI_CMD_INHIBIT | SDHCI_DATA_INHIBIT;

	udelay(1000);
	/* We shouldn't wait for data inihibit for stop commands, even
	   though they might use busy signaling */
	if (cmd->cmdidx == MMC_CMD_STOP_TRANSMISSION)
		mask &= ~SDHCI_DATA_INHIBIT;

	while (sdhci_readl(host, SDHCI_PRESENT_STATE) & mask) {
		if (time >= cmd_timeout) {
			printf("%s: MMC: %d busy ", __func__, mmc_dev);
			if (2 * cmd_timeout <= CONFIG_SDHCI_CMD_MAX_TIMEOUT) {
				cmd_timeout += cmd_timeout;
				printf("timeout increasing to: %u ms.\n", cmd_timeout);
				sdio_dump(host->ioaddr);
			} else {
				printf("timeout.\n");
				sdio_dump(host->ioaddr);
				return COMM_ERR;
			}
		}
		time++;
		udelay(10000);
	}

	mask = SDHCI_INT_RESPONSE;
	if (!(cmd->resp_type & MMC_RSP_PRESENT))
		flags = SDHCI_CMD_RESP_NONE;
	else if (cmd->resp_type & MMC_RSP_136)
		flags = SDHCI_CMD_RESP_LONG;
	else if (cmd->resp_type & MMC_RSP_BUSY) {
		flags = SDHCI_CMD_RESP_SHORT_BUSY;
		mask |= SDHCI_INT_DATA_END;
	} else
		flags = SDHCI_CMD_RESP_SHORT;

	if (cmd->resp_type & MMC_RSP_CRC)
		flags |= SDHCI_CMD_CRC;
	if (cmd->resp_type & MMC_RSP_OPCODE)
		flags |= SDHCI_CMD_INDEX;
	if (data)
		flags |= SDHCI_CMD_DATA;

	sdhci_writew(host, 0x080e, SDHCI_TIMEOUT_CONTROL);

	/* Set Transfer mode regarding to data flag */
	if (data != 0) {
		mode = SDHCI_TRNS_BLK_CNT_EN;
		trans_bytes = data->blocks * data->blocksize;
		if (data->blocks > 1)
			mode |= SDHCI_TRNS_MULTI;

		if (data->flags == MMC_DATA_READ)
			mode |= SDHCI_TRNS_READ;

#ifdef CONFIG_MMC_SDMA
		if (data->flags == MMC_DATA_READ) {
			start_addr = (unsigned long)data->dest;
		} else {
			start_addr = (unsigned long)data->src;
		}
		if ((host->quirks & SDHCI_QUIRK_32BIT_DMA_ADDR) && (start_addr & 0x7) != 0x0) {
			is_aligned = 0;
			start_addr = (unsigned long)aligned_buffer;
			if (data->flags != MMC_DATA_READ)
				memcpy(aligned_buffer, data->src, trans_bytes);
		}

		sdhci_writel(host, (u32) (((u64) start_addr) & 0xFFFFFFFF), SDHCI_DMA_ADDRESS_LOW);
		sdhci_writel(host, (u32) (((u64) start_addr >> 32) & 0xFFFFFFFF), SDHCI_DMA_ADDRESS_HIGH);

		mode |= SDHCI_TRNS_DMA;
#endif
		sdhci_writew(host, SDHCI_MAKE_BLKSZ(SDHCI_DEFAULT_BOUNDARY_ARG, data->blocksize), SDHCI_BLOCK_SIZE);
		sdhci_writew(host, data->blocks, SDHCI_BLOCK_COUNT);
		//sdhci_writew(host, mode, SDHCI_TRANSFER_MODE);
		ultemp = mode | (SDHCI_MAKE_CMD(cmd->cmdidx, flags) << 16);
	} else {
		/*
		   ultemp = sdhci_readl(host, SDHCI_TRANSFER_MODE);
		   ultemp = ultemp & 0xffff;
		 */
		ultemp = SDHCI_DEFAULT_TR_MODE;
		ultemp = ultemp | (SDHCI_MAKE_CMD(cmd->cmdidx, flags) << 16);
	}

	sdhci_writel(host, cmd->cmdarg, SDHCI_ARGUMENT);

#ifdef CONFIG_MMC_SDMA
	flush_cache(start_addr, trans_bytes);
#endif

	sdhci_writel(host, ultemp, SDHCI_TRANSFER_MODE);
	do {
		stat = sdhci_readl(host, SDHCI_INT_STATUS);
		//printf("stat: 0x%x \n ",stat);
		if (stat & SDHCI_INT_ERROR)
			break;
		if (--retry == 0)
			break;
	} while ((stat & mask) != mask);

	if (retry == 0) {
		if (host->quirks & SDHCI_QUIRK_BROKEN_R1B)
			return 0;
		else {
			printf("%s: Timeout for status update!\n", __func__);
			sdio_dump(host->ioaddr);
			return TIMEOUT;
		}
	}

	if ((stat & (SDHCI_INT_ERROR | mask)) == mask) {
		sdhci_cmd_done(host, cmd);
		//printf("cmd done. \n");
		sdhci_writel(host, mask, SDHCI_INT_STATUS);
	} else
		ret = -1;
	//printf("command send ok, wait data transfer.  \n");

	if (!ret && data)
		ret = sdhci_transfer_data(host, data, start_addr);

	if (host->quirks & SDHCI_QUIRK_WAIT_SEND_CMD)
		udelay(800000);

	stat = sdhci_readl(host, SDHCI_INT_STATUS);
	sdhci_writel(host, SDHCI_INT_ALL_MASK, SDHCI_INT_STATUS);
	if (!ret) {
		if ((host->quirks & SDHCI_QUIRK_32BIT_DMA_ADDR) && !is_aligned && (data->flags == MMC_DATA_READ))
			memcpy(data->dest, aligned_buffer, trans_bytes);
		return 0;
	}
	sdhci_reset(host, SDHCI_RESET_CMD);
	sdhci_reset(host, SDHCI_RESET_DATA);
	if (stat & SDHCI_INT_TIMEOUT)
		return TIMEOUT;
	else
		return COMM_ERR;
	}


int sdhci_send_command_backstage(struct mmc *mmc, struct mmc_cmd *cmd,
		       struct mmc_data *data)
{
	struct sdhci_host *host = (struct sdhci_host *)mmc->priv;
	unsigned int stat = 0;
	int ret = 0;
	int trans_bytes = 0, is_aligned = 1;
	u32 mask, flags, mode;
	unsigned int time = 0;
	unsigned long start_addr = 0;
	unsigned int retry = 8000000;
	int mmc_dev = mmc->block_dev.dev;

	unsigned int ultemp = 0;

	/* Timeout unit - ms */
	static unsigned int cmd_timeout = CONFIG_SDHCI_CMD_DEFAULT_TIMEOUT;

	sdhci_writel(host, SDHCI_INT_ALL_MASK, SDHCI_INT_STATUS);
	mask = SDHCI_CMD_INHIBIT | SDHCI_DATA_INHIBIT;

	udelay(1000);
	/* We shouldn't wait for data inihibit for stop commands, even
	   though they might use busy signaling */
	if (cmd->cmdidx == MMC_CMD_STOP_TRANSMISSION)
		mask &= ~SDHCI_DATA_INHIBIT;

	while (sdhci_readl(host, SDHCI_PRESENT_STATE) & mask) {
		if (time >= cmd_timeout) {
			debugf("%s: MMC: %d busy ", __func__, mmc_dev);
			if (2 * cmd_timeout <= CONFIG_SDHCI_CMD_MAX_TIMEOUT) {
				cmd_timeout += cmd_timeout;
				debugf("timeout increasing to: %u ms.\n",
				       cmd_timeout);
				sdio_dump(host->ioaddr);
			} else {
				errorf("timeout.\n");
				sdio_dump(host->ioaddr);
				return COMM_ERR;
			}
		}
		time++;
		udelay(10000);
	}

	mask = SDHCI_INT_RESPONSE;
	if (!(cmd->resp_type & MMC_RSP_PRESENT))
		flags = SDHCI_CMD_RESP_NONE;
	else if (cmd->resp_type & MMC_RSP_136)
		flags = SDHCI_CMD_RESP_LONG;
	else if (cmd->resp_type & MMC_RSP_BUSY) {
		flags = SDHCI_CMD_RESP_SHORT_BUSY;
		mask |= SDHCI_INT_DATA_END;
	} else
		flags = SDHCI_CMD_RESP_SHORT;

	if (cmd->resp_type & MMC_RSP_CRC)
		flags |= SDHCI_CMD_CRC;
	if (cmd->resp_type & MMC_RSP_OPCODE)
		flags |= SDHCI_CMD_INDEX;
	if (data)
		flags |= SDHCI_CMD_DATA;

	sdhci_writew(host, 0x080e, SDHCI_TIMEOUT_CONTROL);

	/* Set Transfer mode regarding to data flag */
	if (data != 0) {

		mode = SDHCI_TRNS_BLK_CNT_EN;
		trans_bytes = data->blocks * data->blocksize;
		if (data->blocks > 1)
			mode |= SDHCI_TRNS_MULTI;

		if (data->flags == MMC_DATA_READ)
			mode |= SDHCI_TRNS_READ;

#ifdef CONFIG_MMC_SDMA
		if (data->flags == MMC_DATA_READ) {
			start_addr = (unsigned long)data->dest;
		} else {
			start_addr = (unsigned long)data->src;
		}
		if ((host->quirks & SDHCI_QUIRK_32BIT_DMA_ADDR) && (start_addr & 0x7) != 0x0) {
			is_aligned = 0;
			start_addr = (unsigned long)aligned_buffer;
			if (data->flags != MMC_DATA_READ)
				memcpy(aligned_buffer, data->src, trans_bytes);
		}

		sdhci_writel(host, (u32) (((u64) start_addr) & 0xFFFFFFFF), SDHCI_DMA_ADDRESS_LOW);
		sdhci_writel(host, (u32) (((u64) start_addr >> 32) & 0xFFFFFFFF), SDHCI_DMA_ADDRESS_HIGH);

		mode |= SDHCI_TRNS_DMA;
#endif
		sdhci_writew(host, SDHCI_MAKE_BLKSZ(SDHCI_DEFAULT_BOUNDARY_ARG, data->blocksize), SDHCI_BLOCK_SIZE);
		sdhci_writew(host, data->blocks, SDHCI_BLOCK_COUNT);
		//sdhci_writew(host, mode, SDHCI_TRANSFER_MODE);
		ultemp = mode | (SDHCI_MAKE_CMD(cmd->cmdidx, flags) << 16);
	} else {
		/*
		   ultemp = sdhci_readl(host, SDHCI_TRANSFER_MODE);
		   ultemp = ultemp & 0xffff;
		 */
		ultemp = SDHCI_DEFAULT_TR_MODE;
		ultemp = ultemp | (SDHCI_MAKE_CMD(cmd->cmdidx, flags) << 16);
	}

	sdhci_writel(host, cmd->cmdarg, SDHCI_ARGUMENT);
#ifdef CONFIG_MMC_SDMA
	flush_cache(start_addr, trans_bytes);
#endif

	sdhci_writel(host, ultemp, SDHCI_TRANSFER_MODE);
	do {
		stat = sdhci_readl(host, SDHCI_INT_STATUS);
		if (stat & SDHCI_INT_ERROR)
			break;
		if (--retry == 0)
			break;
	} while ((stat & mask) != mask);

	if (retry == 0) {
		if (host->quirks & SDHCI_QUIRK_BROKEN_R1B)
			return 0;
		else {
			errorf("%s: Timeout for status update!\n", __func__);
			sdio_dump(host->ioaddr);
			return TIMEOUT;
		}
	}

	if ((stat & (SDHCI_INT_ERROR | mask)) == mask) {
		sdhci_cmd_done(host, cmd);
		sdhci_writel(host, mask, SDHCI_INT_STATUS);
		/*do not wait for transfer complete*/
		return 0;
	}

	if (host->quirks & SDHCI_QUIRK_WAIT_SEND_CMD)
		udelay(800000);

	stat = sdhci_readl(host, SDHCI_INT_STATUS);
	sdhci_writel(host, SDHCI_INT_ALL_MASK, SDHCI_INT_STATUS);

	if (stat & SDHCI_INT_TIMEOUT){
		//sdio_dump(host->ioaddr);
		sdhci_reset(host, SDHCI_RESET_CMD);
		sdhci_reset(host, SDHCI_RESET_DATA);
		return TIMEOUT;
	}
	else{
		//sdio_dump(host->ioaddr);
		sdhci_reset(host, SDHCI_RESET_CMD);
		sdhci_reset(host, SDHCI_RESET_DATA);
		return COMM_ERR;
	}
}


int sdhci_query_command_backstage(struct mmc *mmc, struct mmc_data *data)
{
	struct sdhci_host *host = (struct sdhci_host *)mmc->priv;
	unsigned int start_addr = 0;
	int ret = 0;
	unsigned int stat = 0;

	if (NULL != data)
		ret = sdhci_transfer_data(host, data, start_addr);

	start_addr = (unsigned int)data->src;

	stat = sdhci_readl(host, SDHCI_INT_STATUS);
	sdhci_writel(host, SDHCI_INT_ALL_MASK, SDHCI_INT_STATUS);
	if (!ret)
		return 0;

	if (stat & SDHCI_INT_TIMEOUT) {
		//sdio_dump(host->ioaddr);
		sdhci_reset(host, SDHCI_RESET_CMD);
		sdhci_reset(host, SDHCI_RESET_DATA);
		return TIMEOUT;
	} else {
		//sdio_dump(host->ioaddr);
		sdhci_reset(host, SDHCI_RESET_CMD);
		sdhci_reset(host, SDHCI_RESET_DATA);
		return COMM_ERR;
	}
}

static int sdhci_set_clock(struct mmc *mmc, unsigned int clock)
{
	struct sdhci_host *host = (struct sdhci_host *)mmc->priv;
	unsigned int div, clk, timeout;

	sdhci_writew(host, 0, SDHCI_CLOCK_CONTROL);

	if (clock == 0)
		return 0;

	if (SDHCI_GET_VERSION(host) >= SDHCI_SPEC_300) {
		/* Version 3.00 divisors must be a multiple of 2. */
		if (mmc->f_max <= clock)
			div = 1;
		else {
			for (div = 2; div < SDHCI_MAX_DIV_SPEC_300; div += 2) {
				if ((mmc->f_max / div) <= clock)
					break;
			}
		}
	} else {
		/* Version 2.00 divisors must be a power of 2. */
		for (div = 1; div < SDHCI_MAX_DIV_SPEC_200; div *= 2) {
			if ((mmc->f_max / div) <= clock)
				break;
		}
	}
	div >>= 1;

	if (host->set_clock)
		host->set_clock(host->index, div);

	clk = (div & SDHCI_DIV_MASK) << SDHCI_DIVIDER_SHIFT;
	clk |= ((div & SDHCI_DIV_HI_MASK) >> SDHCI_DIV_MASK_LEN)
	    << SDHCI_DIVIDER_HI_SHIFT;
	clk |= SDHCI_CLOCK_INT_EN;
	sdhci_writew(host, clk, SDHCI_CLOCK_CONTROL);

	/* Wait max 20 ms */
	timeout = 100;
	while (!((clk = sdhci_readw(host, SDHCI_CLOCK_CONTROL))
		 & SDHCI_CLOCK_INT_STABLE)) {
		if (timeout == 0) {
			printf("%s: Internal clock never stabilised.\n", __func__);
			return -1;
		}
		timeout--;
		udelay(1000);
	}

	clk |= SDHCI_CLOCK_CARD_EN;
	sdhci_writew(host, clk, SDHCI_CLOCK_CONTROL);
	return 0;
}

static void sdhci_set_power(struct sdhci_host *host, unsigned short power)
{
	u8 pwr = 0;
	if (power != (unsigned short)-1) {
		switch (1 << power) {
		case MMC_VDD_165_195:
			pwr = SDHCI_POWER_180;
			break;
		case MMC_VDD_29_30:
		case MMC_VDD_30_31:
			pwr = SDHCI_POWER_300;
			break;
		case MMC_VDD_32_33:
		case MMC_VDD_33_34:
			pwr = SDHCI_POWER_330;
			break;
		}
	}

	if (pwr == 0) {
		sdhci_writeb(host, 0, SDHCI_POWER_CONTROL);
		return;
	}

	if (host->quirks & SDHCI_QUIRK_NO_SIMULT_VDD_AND_POWER)
		sdhci_writeb(host, pwr, SDHCI_POWER_CONTROL);

	pwr |= SDHCI_POWER_ON;

	sdhci_writeb(host, pwr, SDHCI_POWER_CONTROL);
}

void sdhci_set_ios(struct mmc *mmc)
{
	u32 ctrl;
	struct sdhci_host *host = (struct sdhci_host *)mmc->priv;
/*
	if (host->set_control_reg)
		host->set_control_reg(host);
*/
	if (mmc->clock != host->clock)
		sdhci_set_clock(mmc, mmc->clock);

	/* Set bus width */
	ctrl = sdhci_readb(host, SDHCI_HOST_CONTROL_REG1);
	if (mmc->bus_width == 8) {
		ctrl &= ~SDHCI_CTRL_4BITBUS;
		if ((SDHCI_GET_VERSION(host) >= SDHCI_SPEC_300) || (host->quirks & SDHCI_QUIRK_USE_WIDE8))
			ctrl |= SDHCI_CTRL_8BITBUS;
	} else {
		if (SDHCI_GET_VERSION(host) >= SDHCI_SPEC_300)
			ctrl &= ~SDHCI_CTRL_8BITBUS;
		if (mmc->bus_width == 4)
			ctrl |= SDHCI_CTRL_4BITBUS;
		else
			ctrl &= ~SDHCI_CTRL_4BITBUS;
	}

#if 0
	if (mmc->clock > 26000000)
		ctrl |= SDHCI_CTRL_HISPD;
	else
#endif
		ctrl &= ~SDHCI_CTRL_HISPD;
	ctrl &= ~SDHCI_CTRL_DMA_MASK;
	if (host->quirks & SDHCI_QUIRK_NO_HISPD_BIT)
		ctrl &= ~SDHCI_CTRL_HISPD;

	sdhci_writeb(host, ctrl, SDHCI_HOST_CONTROL_REG1);
}

int sdhci_init(struct mmc *mmc)
{
	u32 temp = 0;
	struct sdhci_host *host = (struct sdhci_host *)mmc->priv;

	if ((host->quirks & SDHCI_QUIRK_32BIT_DMA_ADDR) && !aligned_buffer) {
		aligned_buffer = memalign(8, 512 * 1024);
		if (!aligned_buffer) {
			printf("%s: Aligned buffer alloc failed!!!\n", __func__);
			return -1;
		}
	}

	sdhci_set_power(host, fls(mmc->voltages) - 1);

	if (host->quirks & SDHCI_QUIRK_NO_CD) {
		unsigned int status;

		sdhci_writel(host, SDHCI_CTRL_CD_TEST_INS | SDHCI_CTRL_CD_TEST, SDHCI_HOST_CONTROL_REG1);

		status = sdhci_readl(host, SDHCI_PRESENT_STATE);
		while ((!(status & SDHCI_CARD_PRESENT)) || (!(status & SDHCI_CARD_STATE_STABLE)) || (!(status & SDHCI_CARD_DETECT_PIN_LEVEL)))
			status = sdhci_readl(host, SDHCI_PRESENT_STATE);
	}

	temp = sdhci_readl(host, SDHCI_HOST_CONTROL_REG2);
	temp |= SDHCI_64BIT_ADDR_EN;
	sdhci_writel(host, temp, SDHCI_HOST_CONTROL_REG2);

	/* Enable only interrupts served by the SD controller */
	sdhci_writel(host, SDHCI_INT_DATA_MASK | SDHCI_INT_CMD_MASK, SDHCI_INT_ENABLE);
	/* Mask all sdhci interrupt sources */
	//sdhci_writel(host, 0x0, SDHCI_SIGNAL_ENABLE);
	sdhci_writel(host, SDHCI_INT_DATA_MASK | SDHCI_INT_CMD_MASK | SDHCI_INT_ERROR, SDHCI_SIGNAL_ENABLE);
	return 0;
}

int add_sdhci(struct sdhci_host *host, u32 max_clk, u32 min_clk)
{
	struct mmc *mmc;
	unsigned int caps;

	mmc = malloc(sizeof(struct mmc));
	if (!mmc) {
		printf("%s: mmc malloc fail!\n", __func__);
		return -1;
	}
	memset(mmc, 0, sizeof(struct mmc));
	mmc->priv = host;
	host->mmc = mmc;

	sprintf(mmc->name, "%s", host->name);
	mmc->send_cmd = sdhci_send_command;
	mmc->set_ios = sdhci_set_ios;
	mmc->init = sdhci_init;
	mmc->getcd = NULL;
	mmc->getwp = NULL;

	caps = sdhci_readl(host, SDHCI_CAPABILITIES);
	/***************** enable 3.0V support *******************/
	caps |= 0x02000000;
#ifdef CONFIG_MMC_SDMA
	if (!(caps & SDHCI_CAN_DO_SDMA)) {
		printf("%s: Your controller doesn't support SDMA!!\n", __func__);
		return -1;
	}
#endif

	if (max_clk)
		mmc->f_max = max_clk;
	else {
		if (SDHCI_GET_VERSION(host) >= SDHCI_SPEC_300)
			mmc->f_max = (caps & SDHCI_CLOCK_V3_BASE_MASK)
			    >> SDHCI_CLOCK_BASE_SHIFT;
		else
			mmc->f_max = (caps & SDHCI_CLOCK_BASE_MASK)
			    >> SDHCI_CLOCK_BASE_SHIFT;
		mmc->f_max *= 1000000;
	}
	if (mmc->f_max == 0) {
		printf("%s: Hardware doesn't specify base clock frequency\n", __func__);
		return -1;
	}
	if (min_clk)
		mmc->f_min = min_clk;
	else {
		if (SDHCI_GET_VERSION(host) >= SDHCI_SPEC_300)
			mmc->f_min = mmc->f_max / SDHCI_MAX_DIV_SPEC_300;
		else
			mmc->f_min = mmc->f_max / SDHCI_MAX_DIV_SPEC_200;
	}

	mmc->voltages = 0;
	if (caps & SDHCI_CAN_VDD_330)
		mmc->voltages |= MMC_VDD_32_33 | MMC_VDD_33_34;
	if (caps & SDHCI_CAN_VDD_300)
		mmc->voltages |= MMC_VDD_29_30 | MMC_VDD_30_31;
	if (caps & SDHCI_CAN_VDD_180)
		mmc->voltages |= MMC_VDD_165_195;

	if (host->quirks & SDHCI_QUIRK_BROKEN_VOLTAGE)
		mmc->voltages |= host->voltages;

	mmc->host_caps = MMC_MODE_HS | MMC_MODE_HS_52MHz | MMC_MODE_4BIT;
	if (SDHCI_GET_VERSION(host) >= SDHCI_SPEC_300) {
		if (caps & SDHCI_CAN_DO_8BIT)
			mmc->host_caps |= MMC_MODE_8BIT;
	}
	if (host->host_caps)
		mmc->host_caps |= host->host_caps;

	sdhci_reset(host, SDHCI_RESET_ALL);
	mmc_register(mmc);

	return 0;
}

static char *SPRD_SDHCI_NAME = "sprd_sdhci";
int sprd_sdhci_init(u32 regbase, u32 max_clk, u32 min_clk, u32 quirks)
{
	struct sdhci_host *host = NULL;
	host = (struct sdhci_host *)malloc(sizeof(struct sdhci_host));
	if (!host) {
		printf("sdh_host malloc fail!\n");
		return 1;
	}

	host->name = SPRD_SDHCI_NAME;
	host->ioaddr = (void *)regbase;
	host->quirks = quirks;

	host->version = sdhci_readw(host, SDHCI_HOST_VERSION);
	printf("sprd_sdhci_init :version :%d\n", host->version);
	return add_sdhci(host, max_clk, min_clk);
}
