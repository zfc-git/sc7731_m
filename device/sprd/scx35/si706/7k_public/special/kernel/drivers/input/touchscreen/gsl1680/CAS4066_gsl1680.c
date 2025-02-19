/* 
 * drivers/input/touchscreen/gslX680.c
 *
 * Sileadinc gslX680 TouchScreen driver. 
 *
 * Copyright (c) 2012  Sileadinc
 *
 * This software is licensed under the terms of the GNU General Public
 * License version 2, as published by the Free Software Foundation, and
 * may be copied, distributed, and modified under those terms.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * VERSION      	DATE			AUTHOR
 *   1.0		 2012-04-18		   leweihua
 *
 * note: only support mulititouch	Wenfs 2010-10-01
 */

   
/*
#include <linux/init.h>
#include <linux/ctype.h>
#include <linux/err.h>
#include <linux/input.h>
#include <linux/delay.h>
#include <linux/interrupt.h>
#include <linux/slab.h>
#include <linux/i2c.h>
#include <linux/byteorder/generic.h>
#include <linux/timer.h>
#include <linux/jiffies.h>
#include <linux/irq.h>
#include <linux/platform_device.h>
#include <linux/earlysuspend.h>
#include <linux/firmware.h>
#include <mach/ldo.h>
#include <mach/gpio.h>*/
#include <linux/i2c.h>
#include <linux/input.h>
#include <linux/gpio.h>
#include <linux/earlysuspend.h>
#include <linux/interrupt.h>
#include <linux/delay.h>

#include <linux/firmware.h>
#include <linux/platform_device.h>

#include <linux/slab.h>
//#include <mach/regulator.h>
#include <soc/sprd/regulator.h>

#include <linux/miscdevice.h>
#include <linux/wakelock.h>
#include <linux/ioctl.h>

#include <linux/module.h>
#include <linux/moduleparam.h>
#include <linux/kernel.h>
#include <linux/timer.h>
#include <linux/sysfs.h>
#include <linux/init.h>
#include <linux/mutex.h>
#include <mach/gpio.h>
#include <linux/spinlock.h>
#include <linux/syscalls.h>
#include <linux/file.h>
#include <linux/fs.h>
#include <linux/fcntl.h>
#include <linux/string.h>
#include <asm/unistd.h>
#include <linux/cdev.h>
#include <asm/uaccess.h>
#include <linux/earlysuspend.h>
#include <linux/input/mt.h>
#include "CAS4066_gsl1680.h"
#include <linux/regulator/consumer.h>

//#define GSL_GESTURE
#define USE_TP_PSENSOR
#ifdef USE_TP_PSENSOR
#include <asm/uaccess.h>
#include <linux/miscdevice.h>
#include "tp_psensor.h"
#define PS_DEBUG

#ifdef PS_DEBUG
#define PS_DBG(format, ...)	\
		printk(KERN_INFO "TP_PSENSOR " format "\n", ## __VA_ARGS__)
#else
#define PS_DBG(format, ...)
#endif  

static int tp_ps_opened = 0;
static atomic_t ps_flag;
static tp_ps_t *tp_ps = 0;
static int ps_en = 0;

static u8 gsl_psensor_data[8]={0};

#endif //USE_TP_PSENSOR
static int flag_ts_suspend=0;
static int suspend_entry_flag = 0;
#define GSL_DEBUG
#define I2C_BOARD_INFO_METHOD   1		/*Simon delete*/
//#define GSL_MONITOR		/*Simon delete*/

#define MAX_FINGERS		10
#define MAX_CONTACTS	10
#define DMA_TRANS_LEN	0x20
#define GSL_PAGE_REG	0xf0

#define PRESS_MAX    		255
#define GSLX680_TS_NAME	"gslX680"
#define GSLX680_TS_ADDR	0x40
#define FTS_GESTRUE_SWITCH

#if defined(FTS_GESTRUE_SWITCH)
#include <linux/fs.h>

#define GESTURE_FUNCTION_SWITCH		"/data/data/com.zyt.close_gesture_sttings/gesture_switch"
static int s_gesture_switch = 1;	// Defaultly, the macro is open
#endif
#define FILTER_POINT
#ifdef FILTER_POINT
#define FILTER_MAX	9
#endif

#define TPD_PROC_DEBUG	/*Simon delete*/
#ifdef TPD_PROC_DEBUG
#include <linux/proc_fs.h>
#include <asm/uaccess.h>
static struct proc_dir_entry *gsl_config_proc = NULL;
#define GSL_CONFIG_PROC_FILE "gsl_config"
#define CONFIG_LEN 31
static char gsl_read[CONFIG_LEN];
static u8 gsl_data_proc[8] = {0};
static u8 gsl_proc_flag = 0;
static struct i2c_client *gsl_client = NULL;
#endif

static int gsl_ts_write(struct i2c_client *client, u8 addr, u8 *pdata, int datalen);
static int gsl_ts_read(struct i2c_client *client, u8 addr, u8 *pdata, unsigned int datalen);
//static void gslX680_ts_reset(void);

#ifdef GSL_MONITOR
static struct delayed_work gsl_monitor_work;
static struct workqueue_struct *gsl_monitor_workqueue = NULL;
static u8 int_1st[4] = {0};
static u8 int_2nd[4] = {0};
static char dac_counter = 0;
static char b0_counter = 0;
static char bc_counter = 0;
static char i2c_lock_flag = 0;
#endif 



#ifdef GSL_DEBUG 
#define print_info(fmt, args...)   \
        do{                              \
                printk(fmt, ##args);     \
        }while(0)
#else
#define print_info(fmt, args...)   //
#endif

struct sprd_i2c_setup_data {
	unsigned i2c_bus;  //the same number as i2c->adap.nr in adapter probe function
	unsigned short i2c_address;
	int irq;
	char type[I2C_NAME_SIZE];
};

//static int sprd_3rdparty_gpio_tp_pwr;
//static int sprd_3rdparty_gpio_tp_rst;
//static int sprd_3rdparty_gpio_tp_irq;
static struct regulator *reg_vdd;
static struct regulator *reg_vddsim2;
#define sprd_3rdparty_gpio_tp_rst	81
#define sprd_3rdparty_gpio_tp_irq	82

static u32 id_sign[MAX_CONTACTS+1] = {0};
static u8 id_state_flag[MAX_CONTACTS+1] = {0};
static u8 id_state_old_flag[MAX_CONTACTS+1] = {0};
static u16 x_old[MAX_CONTACTS+1] = {0};
static u16 y_old[MAX_CONTACTS+1] = {0};
static u16 x_new = 0;
static u16 y_new = 0;

static struct i2c_client *this_client = NULL;
static struct sprd_i2c_setup_data gslX680_ts_setup={0, GSLX680_TS_ADDR, 0, GSLX680_TS_NAME};


struct gslX680_ts_data {
	struct input_dev	*input_dev;
	u8 touch_data[44];	
	struct work_struct 	pen_event_work;
	struct workqueue_struct *ts_workqueue;
	struct early_suspend	early_suspend;
};

//#define HAVE_TOUCH_KEY
#define TOUCH_VIRTUAL_KEYS

#ifdef HAVE_TOUCH_KEY
static u16 key = 0;
static int key_state_flag = 0;
struct key_data {
	u16 key;
	u16 x_min;
	u16 x_max;
	u16 y_min;
	u16 y_max;	
};

const u16 key_array[]={
                                      KEY_MENU,
                                      KEY_HOME,
                                      KEY_BACK,
                                     }; 
#define MAX_KEY_NUM     (sizeof(key_array)/sizeof(key_array[0]))

struct key_data gsl_key_data[MAX_KEY_NUM] = {
	{KEY_MENU, 80, 120, 830, 880},
	{KEY_HOME, 150, 260, 830, 880},	
	{KEY_BACK, 280, 320, 830, 880},
};
#endif
#ifdef GSL_GESTURE
#define REPORT_KEY_VALUE KEY_POWER//KEY_F1      //report key set
#define READ_DATA_LEN	8
static void gsl_irq_mode_change(struct i2c_client *client,u32 flag);
typedef enum{
	GE_DISABLE = 0,
	GE_ENABLE = 1,
	GE_WAKEUP = 2,
	GE_NOWORK =3,
}GE_T;

spinlock_t resume_lock;

static GE_T gsl_gesture_status = GE_DISABLE;
static unsigned int gsl_gesture_flag = 1;
static struct input_dev *gsl_power_idev;
static char gsl_gesture_c = 0;
static int power_key_status = 0;
extern void gsl_GestureExternInt(unsigned int *model,int len);
extern int irq_set_irq_type(unsigned int irq, unsigned int type);
static struct wake_lock gsl_wake_lock;
#endif
#ifdef TOUCH_VIRTUAL_KEYS
//#define PIXCIR_KEY_HOME	102
//#define PIXCIR_KEY_MENU	229
//#define PIXCIR_KEY_BACK		158
//#define PIXCIR_KEY_SEARCH  	158

static ssize_t virtual_keys_show(struct kobject *kobj, struct kobj_attribute *attr, char *buf)
{
	return sprintf(buf,
	 __stringify(EV_KEY) ":" __stringify(KEY_MENU) ":70:1058:80:65"
	 ":" __stringify(EV_KEY) ":" __stringify(KEY_HOMEPAGE) ":240:1058:80:65"
	 ":" __stringify(EV_KEY) ":" __stringify(KEY_BACK) ":420:1058:80:65" 
	);
     
}

static struct kobj_attribute virtual_keys_attr = {
    .attr = {
        .name = "virtualkeys.gslX680",
        .mode = S_IRUGO,
    },
    .show = &virtual_keys_show,
};

static struct attribute *properties_attrs[] = {
    &virtual_keys_attr.attr,
    NULL
};

static struct attribute_group properties_attr_group = {
    .attrs = properties_attrs,
};

static void pixcir_ts_virtual_keys_init(void)
{
    int ret;
    struct kobject *properties_kobj;	
	
    properties_kobj = kobject_create_and_add("board_properties", NULL);
    if (properties_kobj)
        ret = sysfs_create_group(properties_kobj,
                     &properties_attr_group);
    if (!properties_kobj || ret)
        pr_err("failed to create board_properties\n");    
}

#endif

#ifdef  USE_TP_PSENSOR
static int gsl_ts_read(struct i2c_client *client, u8 addr, u8 *pdata, unsigned int datalen);
static int tp_ps_ioctl(struct file *file, unsigned int cmd, unsigned long arg);

static void gsl_gain_psensor_data(struct i2c_client *client)
{
	int tmp = 0;
	u8 buf[4]={0};
	/**************************/
	buf[0]=0x3;
	gsl_ts_write(client,0xf0,buf,4);
	tmp = gsl_ts_read(client,0x0,&gsl_psensor_data[0],4);
	if(tmp <= 0)
	{
		 gsl_ts_read(client,0x0,&gsl_psensor_data[0],4);
	}
	/**************************/

	buf[0]=0x4;
	gsl_ts_write(client,0xf0,buf,4);
	tmp = gsl_ts_read(client,0x0,&gsl_psensor_data[4],4);
	if(tmp <= 0)
	{
		gsl_ts_read(client,0x0,&gsl_psensor_data[4],4);
	}

	
}

static int gsl_ts_read(struct i2c_client *client, u8 addr, u8 *pdata, unsigned int datalen);

/*customer implement: do something like read data from TP IC*/
static int tp_ps_getdata(char *data)
{
	unsigned char read_buf[4];
	gsl_ts_read(this_client, 0xac, read_buf, sizeof(read_buf));
	if(read_buf[0]==90)
		read_buf[0] = 0;
	*data = !(read_buf[0]);
	PS_DBG("read_buf[0]=%d\n\n",read_buf[0]);

	return 0;
}

static int tp_ps_enable()
{	
	u8 buf[4];

	PS_DBG("%s\n", __func__);

	buf[3] = 0x00;
	buf[2] = 0x00;
	buf[1] = 0x00;
	buf[0] = 0x3;
	gsl_ts_write(this_client, 0xf0, buf,4);
	buf[3] = 0x5a;
	buf[2] = 0x5a;
	buf[1] = 0x5a;
	buf[0] = 0x5a;
	gsl_ts_write(this_client, 0x00, buf,4);

	buf[3] = 0x00;
	buf[2] = 0x00;
	buf[1] = 0x00;
	buf[0] = 0x4;
	gsl_ts_write(this_client, 0xf0, buf,4);
	buf[3] = 0x0;
	buf[2] = 0x0;
	buf[1] = 0x0;
	buf[0] = 0x2;
	gsl_ts_write(this_client, 0x00, buf,4);  


	ps_en = 1;

	return 0;
}

static int tp_ps_disable()
{

	u8 buf[4];
	PS_DBG("%s\n", __func__);

	buf[3] = 0x00;
	buf[2] = 0x00;
	buf[1] = 0x00;
	buf[0] = 0x3;
	gsl_ts_write(this_client, 0xf0, buf,4);
	buf[3] = gsl_psensor_data[3];
	buf[2] = gsl_psensor_data[2];
	buf[1] = gsl_psensor_data[1];
	buf[0] = gsl_psensor_data[0];

	gsl_ts_write(this_client, 0x00, buf,4);

	buf[3] = 0x00;
	buf[2] = 0x00;
	buf[1] = 0x00;
	buf[0] = 0x4;

	gsl_ts_write(this_client, 0xf0,  buf,4);
	buf[3] = gsl_psensor_data[7];
	buf[2] = gsl_psensor_data[6];
	buf[1] = gsl_psensor_data[5];
	buf[0] = gsl_psensor_data[4];
	gsl_ts_write(this_client, 0x00, buf,4);	
	ps_en = 0;

	return 0;
}

static int tp_ps_open(struct inode *inode, struct file *file)
{
	PS_DBG("%s\n", __func__);
	if (tp_ps_opened)
		return -EBUSY;
	tp_ps_opened = 1;
	return 0;
}

static int tp_ps_release(struct inode *inode, struct file *file)
{
	PS_DBG("%s", __func__);
	tp_ps_opened = 0;
	//return tp_ps_disable();
	return 0;
}
//static long tp_ps_ioctl(struct inode *inode, struct file *file, unsigned int cmd, unsigned long arg)
static int tp_ps_ioctl(struct file *file, unsigned int cmd, unsigned long arg)

{
	void __user *argp = (void __user *)arg;
	int flag;
	unsigned char data;
	
	PS_DBG("%s: cmd %d", __func__, _IOC_NR(cmd));

	//get ioctl parameter
	switch (cmd) {
		case LTR_IOCTL_SET_PFLAG:
			if (copy_from_user(&flag, argp, sizeof(flag))) {
				return -EFAULT;
			}
			if (flag < 0 || flag > 1) {
				return -EINVAL;
			}
			PS_DBG("%s: set flag=%d", __func__, flag);
			break;
		default:
			break;
	} 

	//handle ioctl
	switch (cmd) {
		case LTR_IOCTL_GET_PFLAG:
			flag = atomic_read(&ps_flag);
			break;
		case LTR_IOCTL_GET_DATA:
			break;
		case LTR_IOCTL_SET_PFLAG:
			atomic_set(&ps_flag, flag);	
			if(flag==1){
				tp_ps_enable();
			}
			else if(flag==0) {
				tp_ps_disable();
			}
			break;
		default:
			pr_err("%s: invalid cmd %d\n", __func__, _IOC_NR(cmd));
			return -EINVAL;
	}

	//report ioctl
	switch (cmd) {
		case LTR_IOCTL_GET_PFLAG:
			if (copy_to_user(argp, &flag, sizeof(flag))) {
				return -EFAULT;
			}
			PS_DBG("%s: get flag=%d", __func__, flag);
			break;
		case LTR_IOCTL_GET_DATA:
			tp_ps_getdata(&data);
			if (copy_to_user(argp, &data, sizeof(data))) {
				return -EFAULT;
			}
			PS_DBG("%s: get data=%d", __func__, flag);
			break;
		default:
			break;
	}

	return 0;
}

static struct file_operations tp_ps_fops = {
	.owner			= THIS_MODULE,
	.open			= tp_ps_open,
	.release			= tp_ps_release,
	.unlocked_ioctl		= tp_ps_ioctl,
};

static struct miscdevice tp_ps_device = {
	.minor = MISC_DYNAMIC_MINOR,
	.name = TP_PS_DEVICE,
	.fops = &tp_ps_fops,
};
static int tp_ps_report_dps(unsigned int touches)
{
	unsigned char dps_data = 0;
	
	tp_ps_getdata(&dps_data);
	if(touches > 1)
	{
		dps_data = 0;
	}

	PS_DBG("%s: proximity=%d, touches = %d", __func__, dps_data, touches);
	
	input_report_abs(tp_ps->input, ABS_DISTANCE, dps_data);
	input_sync(tp_ps->input);
	return dps_data;
}
static int tp_ps_init(struct i2c_client *client)
{
	int err = 0;
	struct input_dev *input_dev;

	ps_en = 0;

		//SSDDDDD=0;
		
	tp_ps = kzalloc(sizeof(tp_ps_t), GFP_KERNEL);
	if (!tp_ps)
	{
		PS_DBG("cxiong %s: request memory failed\n", __func__);
		err= -ENOMEM;
		goto exit_mem_fail;
	}
		
	//register device
	err = misc_register(&tp_ps_device);
	if (err) {
		PS_DBG("%s: tp_ps_device register failed\n", __func__);
		goto exit_misc_reg_fail;
	}

	// register input device 
	input_dev = input_allocate_device();
	if (!input_dev) 
	{
		PS_DBG("cxiong %s: input allocate device failed\n", __func__);
		err = -ENOMEM;
		goto exit_input_dev_allocate_failed;
	}

	tp_ps->input = input_dev;

	input_dev->name = TP_PS_INPUT_DEV;
	input_dev->phys  = TP_PS_INPUT_DEV;
	input_dev->id.bustype = BUS_I2C;
	input_dev->dev.parent = &client->dev;
	input_dev->id.vendor = 0x0001;
	input_dev->id.product = 0x0001;
	input_dev->id.version = 0x0010;

	__set_bit(EV_ABS, input_dev->evbit);	
	//for proximity
	input_set_abs_params(input_dev, ABS_DISTANCE, 0, 1, 0, 0);

	err = input_register_device(input_dev);
	if (err < 0)
	{
	    PS_DBG("%s: input device regist failed\n", __func__);
	    goto exit_input_register_failed;
	}

	printk("cxiong %s: Probe Success!\n",__func__);
	return 0;

exit_input_register_failed:
	input_free_device(input_dev);
exit_input_dev_allocate_failed:
	misc_deregister(&tp_ps_device);
exit_misc_reg_fail:
	kfree(tp_ps);
exit_mem_fail:
	return err;
}

static int tp_ps_uninit()
{
	misc_deregister(&tp_ps_device);
	//free input
#ifndef CONFIG_ZYT_CTP_PS_CDC
	input_unregister_device(tp_ps->input);
	input_free_device(tp_ps->input);
#endif
	//free alloc
	kfree(tp_ps);
	tp_ps = 0;
}

#endif //USE_TP_PSENSOR

static inline u16 join_bytes(u8 a, u8 b)
{
	u16 ab = 0;
	ab = ab | a;
	ab = ab << 8 | b;
	return ab;
}

static u32 gsl_read_interface(struct i2c_client *client, u8 reg, u8 *buf, u32 num)
{
	struct i2c_msg xfer_msg[2];

	xfer_msg[0].addr = client->addr;
	xfer_msg[0].len = 1;
	xfer_msg[0].flags = client->flags & I2C_M_TEN;
	xfer_msg[0].buf = &reg;
	//xfer_msg[0].timing = 400;

	xfer_msg[1].addr = client->addr;
	xfer_msg[1].len = num;
	xfer_msg[1].flags |= I2C_M_RD;
	xfer_msg[1].buf = buf;
	//xfer_msg[1].timing = 400;

	if (reg < 0x80) {
		i2c_transfer(client->adapter, xfer_msg, ARRAY_SIZE(xfer_msg));
		msleep(5);
	}

	return i2c_transfer(client->adapter, xfer_msg, ARRAY_SIZE(xfer_msg)) == ARRAY_SIZE(xfer_msg) ? 0 : -EFAULT;
}

static u32 gsl_write_interface(struct i2c_client *client, const u8 reg, u8 *buf, u32 num)
{
	struct i2c_msg xfer_msg[1];

	buf[0] = reg;

	xfer_msg[0].addr = client->addr;
	xfer_msg[0].len = num + 1;
	xfer_msg[0].flags = client->flags & I2C_M_TEN;
	xfer_msg[0].buf = buf;
	//xfer_msg[0].timing = 400;

	return i2c_transfer(client->adapter, xfer_msg, 1) == 1 ? 0 : -EFAULT;
}

static int gsl_ts_write(struct i2c_client *client, u8 addr, u8 *pdata, int datalen)
{
	int ret = 0;
	u8 tmp_buf[128];
	unsigned int bytelen = 0;
	if (datalen > 125)
	{
		printk("cxiong %s too big datalen = %d!\n", __func__, datalen);
		return -1;
	}
	
	tmp_buf[0] = addr;
	bytelen++;
	
	if (datalen != 0 && pdata != NULL)
	{
		memcpy(&tmp_buf[bytelen], pdata, datalen);
		bytelen += datalen;
	}
	
	ret = i2c_master_send(client, tmp_buf, bytelen);
	return ret;
}

static int gsl_ts_read(struct i2c_client *client, u8 addr, u8 *pdata, unsigned int datalen)
{
	int ret = 0;

	if (datalen > 126)
	{
		printk("cxiong %s too big datalen = %d!\n", __func__, datalen);
		return -1;
	}

	ret = gsl_ts_write(client, addr, NULL, 0);
	if (ret < 0)
	{
		printk("%s set data address fail!\n", __func__);
		return ret;
	}
	
	return i2c_master_recv(client, pdata, datalen);
}
#ifdef GSL_GESTURE
static unsigned int gsl_read_oneframe_data(unsigned int *data,
				unsigned int addr,unsigned int len)
{
	u8 buf[4], read_len;
	int i=0, j;
	print_info("=======%s addr = %x, len = %d\n",__func__, addr, len);


			buf[0] = ((addr+i*4)/0x80)&0xff;
			buf[1] = (((addr+i*4)/0x80)>>8)&0xff;
			buf[2] = (((addr+i*4)/0x80)>>16)&0xff;
			buf[3] = (((addr+i*4)/0x80)>>24)&0xff;
			gsl_ts_write(this_client, 0xf0, buf, 4);
			gsl_ts_read(this_client, (((addr+i*4)%0x80+8)&0x5f), (char *)&data[i], 4);
			gsl_ts_read(this_client, (addr+i*4)%0x80, (char *)&data[i], 4);
	printk("==barry====read 0x80: %x %x %x %x ======\n",buf[3], buf[2], buf[1], buf[0]);
	for(i=0;i<len;i++)
	{
			gsl_ts_read(this_client, (addr+i*4)%0x80, (char *)&data[i], 4);
			print_info("data[%d] = 0x%08x\n", i, data[i]);
	}

	return len;
}
#endif


static __inline__ void fw2buf(u8 *buf, const u32 *fw)
{
	u32 *u32_buf = (int *)buf;
	*u32_buf = *fw;
}

static void gsl_load_fw(struct i2c_client *client)
{
	u8 buf[DMA_TRANS_LEN*4 + 1] = {0};
	u8 send_flag = 1;
	u8 *cur = buf + 1;
	u32 source_line = 0;
	u32 source_len;
	struct fw_data *ptr_fw;

	printk("cxiong =====gsl_load_fw start==============\n");

	ptr_fw = GSLX680_FW;
	source_len = ARRAY_SIZE(GSLX680_FW);
	for (source_line = 0; source_line < source_len; source_line++) 
	{
		/* init page trans, set the page val */
		if (GSL_PAGE_REG == ptr_fw[source_line].offset)
		{
			fw2buf(cur, &ptr_fw[source_line].val);
			gsl_write_interface(client, GSL_PAGE_REG, buf, 4);
			send_flag = 1;
		}
		else 
		{
			if (1 == send_flag % (DMA_TRANS_LEN < 0x20 ? DMA_TRANS_LEN : 0x20))
	    			buf[0] = (u8)ptr_fw[source_line].offset;

			fw2buf(cur, &ptr_fw[source_line].val);
			cur += 4;

			if (0 == send_flag % (DMA_TRANS_LEN < 0x20 ? DMA_TRANS_LEN : 0x20)) 
			{
	    			gsl_write_interface(client, buf[0], buf, cur - buf - 1);
	    			cur = buf + 1;
			}

			send_flag++;
		}
	}

	printk("cxiong ======gsl_load_fw end==============\n");

}

static int test_i2c(struct i2c_client *client)
{
	u8 read_buf = 0;
	u8 write_buf = 0x12;
	int ret, rc = 1;
	
	ret = gsl_ts_read( client, 0xf0, &read_buf, sizeof(read_buf) );
	if  (ret  < 0)  
    		rc --;
	else
		printk("I read reg 0xf0 is %x\n", read_buf);
	
	msleep(2);
	ret = gsl_ts_write(client, 0xf0, &write_buf, sizeof(write_buf));
	if(ret  >=  0 )
		printk("I write reg 0xf0 0x12\n");
	
	msleep(2);
	ret = gsl_ts_read( client, 0xf0, &read_buf, sizeof(read_buf) );
	if(ret <  0 )
		rc --;
	else
		printk("I read reg 0xf0 is 0x%x\n", read_buf);

	return rc;
}

static void startup_chip(struct i2c_client *client)
{
	u8 tmp = 0x00;
	
#ifdef GSL_NOID_VERSION
	gsl_DataInit(gsl_config_data_id);
#endif
	gsl_ts_write(client, 0xe0, &tmp, 1);
	msleep(10);	
}

static void reset_chip(struct i2c_client *client)
{
	u8 tmp = 0x88;
	u8 buf[4] = {0x00};
	
	gsl_ts_write(client, 0xe0, &tmp, sizeof(tmp));
	msleep(20);
	tmp = 0x04;
	gsl_ts_write(client, 0xe4, &tmp, sizeof(tmp));
	msleep(10);
	gsl_ts_write(client, 0xbc, buf, sizeof(buf));
	msleep(10);
}

static void clr_reg(struct i2c_client *client)
{
	u8 write_buf[4]	= {0};

	write_buf[0] = 0x88;
	gsl_ts_write(client, 0xe0, &write_buf[0], 1); 	
	msleep(20);
	write_buf[0] = 0x03;
	gsl_ts_write(client, 0x80, &write_buf[0], 1); 	
	msleep(5);
	write_buf[0] = 0x04;
	gsl_ts_write(client, 0xe4, &write_buf[0], 1); 	
	msleep(5);
	write_buf[0] = 0x00;
	gsl_ts_write(client, 0xe0, &write_buf[0], 1); 	
	msleep(20);
}

static void init_chip(struct i2c_client *client)
{
	int rc;
	
	gpio_set_value(sprd_3rdparty_gpio_tp_rst, 0);
	msleep(20); 	
	gpio_set_value(sprd_3rdparty_gpio_tp_rst, 1);
	msleep(20); 		
	rc = test_i2c(client);
	if(rc < 0)
	{
		printk("cxiong -gslX680 test_i2c error------\n");	
		return;
	}	
	clr_reg(client);
	reset_chip(client);
	gsl_load_fw(client);			
	startup_chip(client);
	reset_chip(client);
	startup_chip(client);		
}

static void check_mem_data(struct i2c_client *client)
{
	u8 read_buf[4]  = {0};
	
	msleep(30);
	gsl_ts_read(client,0xb0, read_buf, sizeof(read_buf));
	
	if (read_buf[3] != 0x5a || read_buf[2] != 0x5a || read_buf[1] != 0x5a || read_buf[0] != 0x5a)
	{
		printk("#########check mem read 0xb0 = %x %x %x %x #########\n", read_buf[3], read_buf[2], read_buf[1], read_buf[0]);
		init_chip(client);
	}
}

#ifdef TPD_PROC_DEBUG
static int char_to_int(char ch)
{
    if(ch>='0' && ch<='9')
        return (ch-'0');
    else
        return (ch-'a'+10);
}

static ssize_t gsl_config_read_proc(struct file *file, char __user *page, size_t size, loff_t *ppos)
//static int gsl_config_read_proc(char *page, char **start, off_t off, int count, int *eof, void *data)
{
	char temp_data[5] = {0};
	unsigned int tmp=0;
	char print_msg[128] = {0};
	char * ptr = &print_msg[0];
	
    s32 ret = 0;
	
    if (*ppos)      // ADB call again
    {
        return 0;
    }
	
	print_info("[tp-gsl][%s] gsl_read[0] = %c gsl_read[1] = %c \n",__func__, gsl_read[0], gsl_read[1]);
	if('v'==gsl_read[0]&&'s'==gsl_read[1])
	{
#ifdef GSL_NOID_VERSION
		tmp=gsl_version_id();
#else 
		tmp=0x20121215;
#endif
		sprintf(print_msg, "version:%x\n",tmp);
	}
	else if('r'==gsl_read[0]&&'e'==gsl_read[1])
	{
		if('i'==gsl_read[3])
		{
#ifdef GSL_NOID_VERSION 
			tmp=(gsl_data_proc[5]<<8) | gsl_data_proc[4];
			ptr +=sprintf(ptr,"gsl_config_data_id[%d] = ",tmp);
			if(tmp>=0&&tmp<ARRAY_SIZE(gsl_config_data_id))
			{
					ptr +=sprintf(ptr,"%d\n",gsl_config_data_id[tmp]); 
			}
#endif
		}
		else 
		{
			gsl_ts_write(this_client,0Xf0,&gsl_data_proc[4],4);
			if(gsl_data_proc[0] < 0x80)
				gsl_ts_read(this_client,gsl_data_proc[0],temp_data,4);
			gsl_ts_read(this_client,gsl_data_proc[0],temp_data,4);

			ptr +=sprintf(ptr,"offset : {0x%02x,0x",gsl_data_proc[0]);
			ptr +=sprintf(ptr,"%02x",temp_data[3]);
			ptr +=sprintf(ptr,"%02x",temp_data[2]);
			ptr +=sprintf(ptr,"%02x",temp_data[1]);
			ptr +=sprintf(ptr,"%02x};\n",temp_data[0]);
		}
	}

    ret = simple_read_from_buffer(page, size, ppos, print_msg, strlen(print_msg));
    return ret;	
}

static int gsl_config_write_proc(struct file *file, const char *buffer, unsigned long count, void *data)
{
	u8 buf[8] = {0};
	char temp_buf[CONFIG_LEN];
	char *path_buf;
	int tmp = 0;
	int tmp1 = 0;
	print_info("[tp-gsl][%s] \n",__func__);
	if(count > 512)
	{
		print_info("size not match [%d:%ld]\n", CONFIG_LEN, count);
        return -EFAULT;
	}
	path_buf=kzalloc(count,GFP_KERNEL);
	if(!path_buf)
	{
		printk("alloc path_buf memory error \n");
	}
	if(copy_from_user(path_buf, buffer, count))
	{
		print_info("copy from user fail\n");
		goto exit_write_proc_out;
	}
	memcpy(temp_buf,path_buf,(count<CONFIG_LEN?count:CONFIG_LEN));
	print_info("[tp-gsl][%s][%s]\n",__func__,temp_buf);
	
	buf[3]=char_to_int(temp_buf[14])<<4 | char_to_int(temp_buf[15]);	
	buf[2]=char_to_int(temp_buf[16])<<4 | char_to_int(temp_buf[17]);
	buf[1]=char_to_int(temp_buf[18])<<4 | char_to_int(temp_buf[19]);
	buf[0]=char_to_int(temp_buf[20])<<4 | char_to_int(temp_buf[21]);
	
	buf[7]=char_to_int(temp_buf[5])<<4 | char_to_int(temp_buf[6]);
	buf[6]=char_to_int(temp_buf[7])<<4 | char_to_int(temp_buf[8]);
	buf[5]=char_to_int(temp_buf[9])<<4 | char_to_int(temp_buf[10]);
	buf[4]=char_to_int(temp_buf[11])<<4 | char_to_int(temp_buf[12]);
	if('v'==temp_buf[0]&& 's'==temp_buf[1])//version //vs
	{
		memcpy(gsl_read,temp_buf,4);
		printk("gsl version\n");
	}
	else if('s'==temp_buf[0]&& 't'==temp_buf[1])//start //st
	{
		gsl_proc_flag = 1;
		reset_chip(this_client);
	}
	else if('e'==temp_buf[0]&&'n'==temp_buf[1])//end //en
	{
		msleep(20);
		reset_chip(this_client);
		startup_chip(this_client);
		gsl_proc_flag = 0;
	}
	else if('r'==temp_buf[0]&&'e'==temp_buf[1])//read buf //
	{
		memcpy(gsl_read,temp_buf,4);
		memcpy(gsl_data_proc,buf,8);
	}
	else if('w'==temp_buf[0]&&'r'==temp_buf[1])//write buf
	{
		gsl_ts_write(this_client,buf[4],buf,4);
	}
#ifdef GSL_NOID_VERSION
	else if('i'==temp_buf[0]&&'d'==temp_buf[1])//write id config //
	{
		tmp1=(buf[7]<<24)|(buf[6]<<16)|(buf[5]<<8)|buf[4];
		tmp=(buf[3]<<24)|(buf[2]<<16)|(buf[1]<<8)|buf[0];
		if(tmp1>=0 && tmp1<ARRAY_SIZE(gsl_config_data_id))
		{
			gsl_config_data_id[tmp1] = tmp;
		}
	}
#endif
exit_write_proc_out:
	kfree(path_buf);
	return count;
}
#endif


#ifdef FILTER_POINT
static void filter_point(u16 x, u16 y , u8 id)
{
	u16 x_err =0;
	u16 y_err =0;
	u16 filter_step_x = 0, filter_step_y = 0;
	
	id_sign[id] = id_sign[id] + 1;
	if(id_sign[id] == 1)
	{
		x_old[id] = x;
		y_old[id] = y;
	}
	
	x_err = x > x_old[id] ? (x -x_old[id]) : (x_old[id] - x);
	y_err = y > y_old[id] ? (y -y_old[id]) : (y_old[id] - y);

	if( (x_err > FILTER_MAX && y_err > FILTER_MAX/3) || (x_err > FILTER_MAX/3 && y_err > FILTER_MAX) )
	{
		filter_step_x = x_err;
		filter_step_y = y_err;
	}
	else
	{
		if(x_err > FILTER_MAX)
			filter_step_x = x_err; 
		if(y_err> FILTER_MAX)
			filter_step_y = y_err;
	}

	if(x_err <= 2*FILTER_MAX && y_err <= 2*FILTER_MAX)
	{
		filter_step_x >>= 2; 
		filter_step_y >>= 2;
	}
	else if(x_err <= 3*FILTER_MAX && y_err <= 3*FILTER_MAX)
	{
		filter_step_x >>= 1; 
		filter_step_y >>= 1;
	}	
	else if(x_err <= 4*FILTER_MAX && y_err <= 4*FILTER_MAX)
	{
		filter_step_x = filter_step_x*3/4; 
		filter_step_y = filter_step_y*3/4;
	}
	
	x_new = x > x_old[id] ? (x_old[id] + filter_step_x) : (x_old[id] - filter_step_x);
	y_new = y > y_old[id] ? (y_old[id] + filter_step_y) : (y_old[id] - filter_step_y);

	x_old[id] = x_new;
	y_old[id] = y_new;
}
#else

static void record_point(u16 x, u16 y , u8 id)
{
	u16 x_err =0;
	u16 y_err =0;

	id_sign[id]=id_sign[id]+1;
	
	if(id_sign[id]==1){
		x_old[id]=x;
		y_old[id]=y;
	}

	x = (x_old[id] + x)/2;
	y = (y_old[id] + y)/2;
		
	if(x>x_old[id]){
		x_err=x -x_old[id];
	}
	else{
		x_err=x_old[id]-x;
	}

	if(y>y_old[id]){
		y_err=y -y_old[id];
	}
	else{
		y_err=y_old[id]-y;
	}

	if( (x_err > 3 && y_err > 1) || (x_err > 1 && y_err > 3) ){
		x_new = x;     x_old[id] = x;
		y_new = y;     y_old[id] = y;
	}
	else{
		if(x_err > 3){
			x_new = x;     x_old[id] = x;
		}
		else
			x_new = x_old[id];
		if(y_err> 3){
			y_new = y;     y_old[id] = y;
		}
		else
			y_new = y_old[id];
	}

	if(id_sign[id]==1){
		x_new= x_old[id];
		y_new= y_old[id];
	}
	
}
#endif

#ifdef HAVE_TOUCH_KEY
static void report_key(struct gslX680_ts_data *ts, u16 x, u16 y)
{
	u16 i = 0;

	for(i = 0; i < MAX_KEY_NUM; i++) 
	{
		if((gsl_key_data[i].x_min < x) && (x < gsl_key_data[i].x_max)&&(gsl_key_data[i].y_min < y) && (y < gsl_key_data[i].y_max))
		{
			key = gsl_key_data[i].key;	
			input_report_key(ts->input_dev, key, 1);
			input_sync(ts->input_dev); 		
			key_state_flag = 1;
			break;
		}
	}
}
#endif

static void report_data(struct gslX680_ts_data *ts, u16 x, u16 y, u8 pressure, u8 id)
{
	printk("cxiong ---report_data: id %d, x %d, y %d \n",id, x, y);

	/*input_report_abs(ts->input_dev, ABS_MT_TOUCH_MAJOR, pressure);
	input_report_abs(ts->input_dev, ABS_MT_POSITION_X, x);
	input_report_abs(ts->input_dev, ABS_MT_POSITION_Y, y);	
	input_report_abs(ts->input_dev, ABS_MT_WIDTH_MAJOR, 1);
	input_report_abs(ts->input_dev, ABS_MT_TRACKING_ID, id);
	input_mt_sync(ts->input_dev);*/
#if  0//def HAVE_TOUCH_KEY
	if(x > SCREEN_MAX_X ||y > SCREEN_MAX_Y)
	{
		report_key(ts,x,y);
                return;
	}
#endif
	input_mt_slot(ts->input_dev, id);
	//input_mt_report_slot_state(ts->input_dev, MT_TOOL_FINGER, true);
        input_report_abs(ts->input_dev, ABS_MT_TOUCH_MAJOR, pressure);
        input_report_abs(ts->input_dev, ABS_MT_POSITION_X, x);
        input_report_abs(ts->input_dev, ABS_MT_POSITION_Y, y);
        input_report_abs(ts->input_dev, ABS_MT_WIDTH_MAJOR, 1);
        input_report_abs(ts->input_dev, ABS_MT_TRACKING_ID, id);
	//input_report_abs(ts->input_dev,BTN_TOUCH,1);
}

static void gslX680_ts_worker(struct work_struct *work)
{
	int rc;
	u8 read_buf[4] = {0};
	u8 id, touches;
	u16 x, y;
	int i = 0;
	
	struct gslX680_ts_data *ts = i2c_get_clientdata(this_client);

	print_info("gslX680_ts_pen_irq_work \n");

#ifdef GSL_MONITOR
	if(i2c_lock_flag != 0)
		goto i2c_lock_schedule;
	else
		i2c_lock_flag = 1;
#endif

#ifdef GSL_NOID_VERSION
	struct gsl_touch_info cinfo={0};
	u32 tmp1=0;
	u8 buf[4]={0};
#endif
	
#ifdef TPD_PROC_DEBUG
	if(gsl_proc_flag == 1)
		goto schedule;
#endif
#ifdef GSL_GESTURE
		if(gsl_gesture_flag==1 && GE_NOWORK == gsl_gesture_status){
			goto schedule;
		}
#endif

	rc = gsl_ts_read(this_client, 0x80, ts->touch_data, sizeof(ts->touch_data));
	if (rc < 0) 
	{
		dev_err(&this_client->dev, "read failed\n");
		goto schedule;
	}

	touches = ts->touch_data[0];
	

	
#ifdef GSL_NOID_VERSION
	cinfo.finger_num = touches;
	print_info("tp-gsl  finger_num = %d\n",cinfo.finger_num);
	for(i = 0; i < (touches < MAX_CONTACTS ? touches : MAX_CONTACTS); i ++)
	{
		cinfo.x[i] = join_bytes( ( ts->touch_data[4 * i + 7] & 0xf),ts->touch_data[4 * i + 6]);
		cinfo.y[i] = join_bytes(ts->touch_data[4 * i + 5],ts->touch_data[4 * i +4]);
		cinfo.id[i] = ((ts->touch_data[4 * i + 7]  & 0xf0)>>4);
		print_info("tp-gsl  before:  x[%d] = %d, y[%d] = %d, id[%d] = %d \n",i,cinfo.x[i],i,cinfo.y[i],i,cinfo.id[i]);
	}
	cinfo.finger_num=(ts->touch_data[3]<<24)|(ts->touch_data[2]<<16)
		|(ts->touch_data[1]<<8)|(ts->touch_data[0]);
	gsl_alg_id_main(&cinfo);
	tmp1=gsl_mask_tiaoping();
	print_info("[tp-gsl] tmp1=%x\n",tmp1);
	if(tmp1>0&&tmp1<0xffffffff)
	{
		buf[0]=0xa;buf[1]=0;buf[2]=0;buf[3]=0;
		gsl_ts_write(this_client,0xf0,buf,4);
		buf[0]=(u8)(tmp1 & 0xff);
		buf[1]=(u8)((tmp1>>8) & 0xff);
		buf[2]=(u8)((tmp1>>16) & 0xff);
		buf[3]=(u8)((tmp1>>24) & 0xff);
		print_info("tmp1=%08x,buf[0]=%02x,buf[1]=%02x,buf[2]=%02x,buf[3]=%02x\n",
			tmp1,buf[0],buf[1],buf[2],buf[3]);
		gsl_ts_write(this_client,0x8,buf,4);
	}
	touches = cinfo.finger_num;
#endif

#ifdef GSL_GESTURE
	printk("--zqiang--gsl_gesture_status=%d  gsl_gesture_flag=%d--\n",gsl_gesture_status,gsl_gesture_flag);
	if(GE_ENABLE == gsl_gesture_status && gsl_gesture_flag == 1){
	    unsigned int key_data=0;
		int tmp_c = 0;
		int flag = 0;	

		tmp_c = gsl_obtain_gesture();
		printk("Tom---gsl_gesture_[tmp_c] = %d, [Char] = %c\n",tmp_c,tmp_c);
		switch(tmp_c){
		//wxt
	    case 'c':
	    case 'C':
            key_data = KEY_C;
            break;
        case 'e':
        case 'E':
            key_data = KEY_E;
            break;
        case 'm':
        case 'M':
            key_data = KEY_M;
            break;
        case 'o':
        case 'O':
            key_data = KEY_O;
            break;
        case 'v':
        case 'V':
            key_data = KEY_V;
            break;
        case 'w':
        case 'W':
            key_data = KEY_W;
            break;
        case 'z':
        case 'Z':
            key_data = KEY_Z;
            break;
        case 0xa1fa:
            key_data = KEY_RIGHT;
            tmp_c = 'R';
            break;
        case 0xa1fb:
            key_data = KEY_LEFT;
            tmp_c = 'L';
            break;
        case 0xa1fc:
            key_data = KEY_UP;
            tmp_c = 'U';
            break;
        case 0xa1fd:
            key_data = KEY_DOWN;
            tmp_c = 'D';
            break;
        case '*':
            key_data = KEY_F1;
            break;
		default:
			//printk("zqiang---can't reconition gsl_gesture_[key_data] = %d, [Char] = %c\n",key_data,key_data);
			break;
		}

		print_info("--zqiang---tmp_c=%d-key_data=%d--\n",tmp_c,key_data);
		//if((key_data != 0)&& (power_key_status == 0)){	
		if(key_data != 0){	
			msleep(10);
			print_info("tp-gsl gesture %c;\n",(char)(tmp_c & 0xff));
			gsl_gesture_c = (char)(tmp_c & 0xff);
			input_report_key(ts->input_dev,KEY_POWER,1); //KEY_POWER
			input_sync(ts->input_dev);
			input_report_key(ts->input_dev,KEY_POWER,0);  //KEY_POWER
			input_sync(ts->input_dev);
			mdelay(200);
			power_key_status = 1;
		}
		goto  schedule;;
	}
#endif
#ifdef USE_TP_PSENSOR
		if (ps_en || suspend_entry_flag)
		{
			if(0 == tp_ps_report_dps(touches))
			{
				goto schedule;
			}
		}
#endif


	for(i = 1; i <= MAX_CONTACTS; i ++)
	{
		if(touches == 0)
			id_sign[i] = 0;	
		id_state_flag[i] = 0;
	}
	for(i = 0; i < (touches > MAX_FINGERS ? MAX_FINGERS : touches); i ++)
	{
	#ifdef GSL_NOID_VERSION
		id = cinfo.id[i];
		x =  cinfo.x[i];
		y =  cinfo.y[i];	
	#else	
		id = ts->touch_data[4 * i + 7] >> 4;
		x = join_bytes( ( ts->touch_data[4 * i + 7] & 0xf),ts->touch_data[4 * i + 6]);
		y = join_bytes(ts->touch_data[4 * i + 5],ts->touch_data[4 * i +4]);
	#endif
	
		if(1 <= id && id <= MAX_CONTACTS)
		{
		#ifdef FILTER_POINT
			filter_point(x, y ,id);
		#else
			record_point(x, y , id);
		#endif
			report_data(ts, x_new, y_new, 10, id);		
			id_state_flag[id] = 1;
		}
	}
	for(i = 1; i <= MAX_CONTACTS; i ++)
	{	
		if( (0 == touches) || ((0 != id_state_old_flag[i]) && (0 == id_state_flag[i])) )
		{
				input_mt_slot(ts->input_dev, i);
                        	input_report_abs(ts->input_dev, ABS_MT_TRACKING_ID, -1);
                        	input_mt_report_slot_state(ts->input_dev, MT_TOOL_FINGER, false);
			id_sign[i]=0;
		}
		id_state_old_flag[i] = id_state_flag[i];
	}
	if(0 == touches)
	{
	#ifdef HAVE_TOUCH_KEY
		if(key_state_flag)
		{
        		input_report_key(ts->input_dev, key, 0);
			input_sync(ts->input_dev);
			key_state_flag = 0;
		}
		else
	#endif
		{
			input_mt_slot(ts->input_dev, i);
                        input_report_abs(ts->input_dev, ABS_MT_TRACKING_ID, -1);
                        input_mt_report_slot_state(ts->input_dev, MT_TOOL_FINGER, false);
			//input_report_abs(ts->input_dev,BTN_TOUCH,0);
		}
	}	
	input_sync(ts->input_dev);

schedule:
#ifdef GSL_MONITOR
	i2c_lock_flag = 0;
i2c_lock_schedule:
#endif
	enable_irq(this_client->irq);
}

#ifdef GSL_MONITOR
static void gsl_monitor_worker(void)
{
	u8 write_buf[4] = {0};
	u8 read_buf[4]  = {0};
	char init_chip_flag = 0;
	
	print_info("----------------gsl_monitor_worker-----------------\n");	

	if(i2c_lock_flag != 0)
		goto queue_monitor_work;
	else
		i2c_lock_flag = 1;
	
	gsl_ts_read(this_client, 0xb0, read_buf, 4);
	if(read_buf[3] != 0x5a || read_buf[2] != 0x5a || read_buf[1] != 0x5a || read_buf[0] != 0x5a)
		b0_counter ++;
	else
		b0_counter = 0;

	if(b0_counter > 1)
	{
		printk("======read 0xb0: %x %x %x %x ======\n",read_buf[3], read_buf[2], read_buf[1], read_buf[0]);
		init_chip_flag = 1;
		b0_counter = 0;
		goto queue_monitor_init_chip;
	}
	
	gsl_ts_read(this_client, 0xb4, read_buf, 4);	
	int_2nd[3] = int_1st[3];
	int_2nd[2] = int_1st[2];
	int_2nd[1] = int_1st[1];
	int_2nd[0] = int_1st[0];
	int_1st[3] = read_buf[3];
	int_1st[2] = read_buf[2];
	int_1st[1] = read_buf[1];
	int_1st[0] = read_buf[0];

	if(int_1st[3] == int_2nd[3] && int_1st[2] == int_2nd[2] &&int_1st[1] == int_2nd[1] && int_1st[0] == int_2nd[0]) 
	{
		printk("======int_1st: %x %x %x %x , int_2nd: %x %x %x %x ======\n",int_1st[3], int_1st[2], int_1st[1], int_1st[0], int_2nd[3], int_2nd[2],int_2nd[1],int_2nd[0]);
		init_chip_flag = 1;
		goto queue_monitor_init_chip;
	}

#if 1 //1.4.0 or later than 1.4.0 read 0xbc for esd checking
	gsl_ts_read(this_client, 0xbc, read_buf, 4);
	if(read_buf[3] != 0 || read_buf[2] != 0 || read_buf[1] != 0 || read_buf[0] != 0)
		bc_counter++;
	else
		bc_counter = 0;
	if(bc_counter > 1)
	{
		printk("cxiong ==read 0xbc: %x %x %x %x======\n",read_buf[3], read_buf[2], read_buf[1], read_buf[0]);
		init_chip_flag = 1;
		bc_counter = 0;
	}
#else
	write_buf[3] = 0x01;
	write_buf[2] = 0xfe;
	write_buf[1] = 0x10;
	write_buf[0] = 0x00;
	gsl_ts_write(this_client, 0xf0, write_buf, 4);
	gsl_ts_read(this_client, 0x10, read_buf, 4);
	gsl_ts_read(this_client, 0x10, read_buf, 4);
	if(read_buf[3] < 10 && read_buf[2] < 10 && read_buf[1] < 10 && read_buf[0] < 10)
		dac_counter ++;
	else
		dac_counter = 0;

	if(dac_counter > 1) 
	{
		printk("======read DAC1_0: %x %x %x %x ======\n",read_buf[3], read_buf[2], read_buf[1], read_buf[0]);
		init_chip_flag = 1;
		dac_counter = 0;
	}
#endif
queue_monitor_init_chip:
	if(init_chip_flag)
		init_chip(this_client);
	
	i2c_lock_flag = 0;
	
queue_monitor_work:	
	queue_delayed_work(gsl_monitor_workqueue, &gsl_monitor_work, 100);
}
#endif

static irqreturn_t gslX680_ts_interrupt(int irq, void *dev_id)
{

	struct gslX680_ts_data *gslX680_ts = (struct gslX680_ts_data *)dev_id;

	print_info("gslX680_ts_interrupt");

    	disable_irq_nosync(this_client->irq);
	
#ifdef GSL_GESTURE
	if(gsl_gesture_status==GE_ENABLE&&gsl_gesture_flag==1){
		wake_lock_timeout(&gsl_wake_lock, msecs_to_jiffies(2000));
		print_info("gsl-jeft\n");
	}
#endif
	if (!work_pending(&gslX680_ts->pen_event_work)) {
		queue_work(gslX680_ts->ts_workqueue, &gslX680_ts->pen_event_work);
	}else
	{
     enable_irq(this_client->irq);
	}

	return IRQ_HANDLED;
}

#ifdef GSL_GESTURE
static void gsl_enter_doze(struct i2c_client *client)
{
	u8 buf[4] = {0};

       gpio_direction_output(sprd_3rdparty_gpio_tp_rst, 1);
	gpio_set_value(sprd_3rdparty_gpio_tp_rst, 1);
	msleep(20);
	
	buf[0] = 0xa;
	buf[1] = 0;
	buf[2] = 0;
	buf[3] = 0;
	gsl_ts_write(client,0xf0,buf,4);
	buf[0] = 0;
	buf[1] = 0;
	buf[2] = 0x1;
	buf[3] = 0x5a;
	gsl_ts_write(client,0x8,buf,4);
	gsl_gesture_status = GE_NOWORK;
	msleep(5);
	gsl_gesture_status = GE_ENABLE;

}
static void gsl_quit_doze(struct gsl_ts_data *ts)
{
	u8 buf[4] = {0};
	u32 tmp;

	gsl_gesture_status = GE_DISABLE;

	//gslX680_ts_reset();
	gpio_direction_output(sprd_3rdparty_gpio_tp_rst, 1);
	gpio_set_value(sprd_3rdparty_gpio_tp_rst, 0);
	msleep(20); 	
	gpio_set_value(sprd_3rdparty_gpio_tp_rst, 1);
	msleep(20); 
		
	buf[0] = 0xa;
	buf[1] = 0;
	buf[2] = 0;
	buf[3] = 0;
	gsl_ts_write(this_client,0xf0,buf,4);
	buf[0] = 0;
	buf[1] = 0;
	buf[2] = 0;
	buf[3] = 0x5a;
	gsl_ts_write(this_client,0x8,buf,4);
	msleep(10);

}


static void gsl_irq_mode_change(struct i2c_client *client,u32 flag)
{
	u8 buf[4]={0};
	buf[0] = 0x6;
	buf[1] = 0;
	buf[2] = 0;
	buf[3] = 0;
	gsl_ts_write(client,0xf0,buf,4);
	if(flag == 1){
		buf[0] = 0;
		buf[1] = 0;
		buf[2] = 0;
		buf[3] = 0;
	}else if(flag == 0){
		buf[0] = 1;
		buf[1] = 0;
		buf[2] = 0;
		buf[3] = 0;
	}else{
		return;
	}
	gsl_ts_write(client,0x1c,buf,4);
}

static ssize_t gsl_sysfs_tpgesture_show(struct device *dev,struct device_attribute *attr, char *buf)
{
	ssize_t len=0;
#if 0
	sprintf(&buf[len],"%s\n","tp gesture is on/off:");
	len += (strlen("tp gesture is on/off:")+1);
	if(gsl_gesture_flag == 1){
		sprintf(&buf[len],"%s\n","  on  ");
		len += (strlen("  on  ")+1);
	}else if(gsl_gesture_flag == 0){
		sprintf(&buf[len],"%s\n","  off  ");
		len += (strlen("  off  ")+1);
	}

	sprintf(&buf[len],"%s\n","tp gesture:");
	len += (strlen("tp gesture:")+1);
#endif
	sprintf(&buf[len],"%c\n",gsl_gesture_c);
	len += 2;	
    return len;
}
//wuhao start
static ssize_t gsl_sysfs_tpgesture_store(struct device *dev,struct device_attribute *attr, const char *buf, size_t count)
{
	char tmp_buf[16];
	
#if 0
	if(buf[0] == '0'){
		gsl_gesture_flag = 0;  
	}else if(buf[0] == '1'){
		gsl_gesture_flag = 1;
	}
#endif
    return count;
}

static void gsl_request_power_idev(void)
{
	int rc;
	struct input_dev *idev;
	idev = input_allocate_device();
	if(!idev){
		return;
	}
	gsl_power_idev = idev;
	idev->name = "gsl_gesture";
	idev->id.bustype = BUS_I2C;
	input_set_capability(idev,EV_KEY,REPORT_KEY_VALUE);
	input_set_capability(idev,EV_KEY,KEY_END);

	rc = input_register_device(idev);
	if(rc){
		input_free_device(idev);
		gsl_power_idev = NULL;
	}
}
static DEVICE_ATTR(tpgesture, 0666, gsl_sysfs_tpgesture_show, gsl_sysfs_tpgesture_store);

//add by lijin 2014.9.1
static ssize_t gsl_sysfs_tpgesture_func_show(struct device *dev,struct device_attribute *attr, char *buf)
{
#if 1 
	ssize_t len=0;
#if 1
	sprintf(&buf[len],"%s\n","tp gesture is on/off:");
	len += (strlen("tp gesture is on/off:")+1);
	if(gsl_gesture_flag == 1){
		sprintf(&buf[len],"%s\n","  on  ");
		len += (strlen("  on  ")+1);
	}else if(gsl_gesture_flag == 0){
		sprintf(&buf[len],"%s\n","  off  ");
		len += (strlen("  off  ")+1);
	}

	//sprintf(&buf[len],"%s\n","tp gesture:");
	//len += (strlen("tp gesture:")+1);
#endif
	//sprintf(&buf[len],"%c\n",gsl_gesture_c);
	//len += 2;	
    return len;


#else
	//ssize_t len=0;
	//return sprintf(&buf[len],"%c\n",gsl_gesture_flag);
	return sprintf(buf,"%c\n",gsl_gesture_flag);
	//len += 2;	
    //return len;
#endif	
	
}
//wuhao start
static ssize_t gsl_sysfs_tpgesture_func_store(struct device *dev,struct device_attribute *attr, const char *buf, size_t count)
{
	char tmp_buf[16];
	
#if 1
	if(buf[0] == '0'){
		gsl_gesture_flag = 0;  
	}else if(buf[0] == '1'){
		gsl_gesture_flag = 1;
	}
#endif
    return count;
}

static DEVICE_ATTR(tpgesture_func, 0666, gsl_sysfs_tpgesture_func_show, gsl_sysfs_tpgesture_func_store);

//end


static unsigned int gsl_gesture_init(void)
{
	int ret;
	struct kobject *gsl_debug_kobj;
	gsl_debug_kobj = kobject_create_and_add("sileadinc", NULL) ;
	if (gsl_debug_kobj == NULL)
	{
		printk("%s: subsystem_register failed\n", __func__);
		return -ENOMEM;
	}
#if 1
	ret = sysfs_create_file(gsl_debug_kobj, &dev_attr_tpgesture.attr);
	//ret = device_create_file(gsl_debug_kobj, &dev_attr_tpgesture);
    if (ret)
    {
        printk("%s: sysfs_create_version_file failed\n", __func__);
        return ret;
    }

	ret = sysfs_create_file(gsl_debug_kobj, &dev_attr_tpgesture_func.attr);
        //ret = device_create_file(gsl_debug_kobj, &dev_attr_tpgesture_fun);
    if (ret)
    {
        printk("%s: sysfs_create_version_file failed\n", __func__);
        return ret;
    }
#else
//add by lijin 2014.9.1
	ret = sysfs_create_group(gsl_debug_kobj, &gsl_tp_attribute_group);
	if (ret < 0) 
	{
		printk("%s: sysfs_create_version_file failed\n", __func__);
		return ret;
	}

//end
#endif
    //gsl_request_power_idev();
	return 1;
}

#endif
static void gslX680_ts_suspend(struct early_suspend *handler)
{
        int ret=-1;
	printk("==gslX680_ts_suspend=\n");
#ifdef USE_TP_PSENSOR
		PS_DBG("--luwl---%s---ps_en=%d	suspend_entry_flag=%d----\n",__func__,ps_en,suspend_entry_flag);
		if (ps_en == 1)
		{
			PS_DBG("==gslX680_ts_suspend=USE_TP_PSENSOR,  do nothing.\n");
			suspend_entry_flag = 1;
			return;
		}
	suspend_entry_flag = 0;
#endif

#ifdef GSL_MONITOR
	printk( "gsl_ts_suspend () : cancel gsl_monitor_work\n");
	cancel_delayed_work_sync(&gsl_monitor_work);
#endif
#ifdef GSL_GESTURE

				if(power_key_status == 0){
				gsl_gesture_c = '*';
				}
				power_key_status = 0;
				print_info("===gslX680_ts_suspend gsl_gesture_flag = %d===\n",gsl_gesture_flag);
				if(gsl_gesture_flag == 1){
					ret = enable_irq_wake(this_client->irq);
					print_info("set_irq_wake(1) result = %d\n",ret);
					
					gsl_irq_mode_change(this_client,1);
					irq_set_irq_type(this_client->irq,IRQF_TRIGGER_HIGH|IRQF_NO_SUSPEND); //IRQ_TYPE_EDGE_BOTH IRQ_TYPE_LEVEL_LOW
					gsl_enter_doze(this_client);
					return;
				}

#endif

    disable_irq_nosync(this_client->irq);
	gpio_set_value(sprd_3rdparty_gpio_tp_rst, 0);
}

static void gslX680_ts_resume(struct early_suspend *handler)
{	
	printk("==gslX680_ts_resume=\n");
#ifdef USE_TP_PSENSOR
		PS_DBG("-----%s---ps_en=%d	suspend_entry_flag=%d----\n",__func__,ps_en,suspend_entry_flag);
		if (1 == suspend_entry_flag)
		{
			PS_DBG("==gslX680_ts_resume=USE_TP_PSENSOR,  do nothing.\n");
			suspend_entry_flag = 0;
			if (0 == ps_en)
			{
				tp_ps_report_dps(0);
			}
			return;
		}
#endif
 #ifdef GSL_GESTURE
		int ret;
		spin_lock(&resume_lock); // add 20141111
		print_info("===gslX680_ts_resume gsl_gesture_flag = %d\n",gsl_gesture_flag);
			if(gsl_gesture_flag == 1){
			ret =disable_irq_wake(this_client->irq);
				print_info("set_irq_wake(1) result = %d\n",ret);
				irq_set_irq_type(this_client->irq,IRQF_TRIGGER_RISING);
				gsl_quit_doze(this_client);
				gsl_irq_mode_change(this_client,0);
			}
			msleep(2);
			power_key_status = 0;
		spin_unlock(&resume_lock);// add 20141111
#endif
	gpio_set_value(sprd_3rdparty_gpio_tp_rst, 1);
	msleep(20);
	reset_chip(this_client);
	startup_chip(this_client);	
	msleep(20);	
	check_mem_data(this_client);
	
#ifdef GSL_MONITOR
	printk( "gsl_ts_resume () : queue gsl_monitor_work\n");
	queue_delayed_work(gsl_monitor_workqueue, &gsl_monitor_work, 300);
#endif	
	
	enable_irq(this_client->irq);	
}

#ifdef TPD_PROC_DEBUG
static const struct file_operations tool_ops = {
    .owner = THIS_MODULE,
    .read = gsl_config_read_proc,
    .write = gsl_config_write_proc,
};
#endif

static int  gpio_power_config(void)
{
	printk("cxiong ctp %s\n", __func__);

	//reset
	gpio_direction_output(sprd_3rdparty_gpio_tp_rst, 1);
	gpio_set_value(sprd_3rdparty_gpio_tp_rst, 0);
	msleep(100);

	reg_vdd = regulator_get(NULL, "vdd28");
	regulator_set_voltage(reg_vdd, 2800000, 2800000);
	regulator_enable(reg_vdd);
        msleep(100);
	reg_vddsim2 = regulator_get(NULL, "vddsim2");
	regulator_set_voltage(reg_vddsim2, 1800000, 1800000);
	regulator_enable(reg_vddsim2);
	msleep(100);	
	
	gpio_set_value(sprd_3rdparty_gpio_tp_rst, 1);
	msleep(20);
	msleep(10);
}

static int gslX680_ts_probe(struct i2c_client *client, const struct i2c_device_id *id)
{
	struct gslX680_ts_data *gslX680_ts;
	struct input_dev *input_dev;
	int err = 0;
	unsigned char uc_reg_value=0; 
	u16 i = 0;
	printk("%s\n",__func__);

	gpio_direction_input(sprd_3rdparty_gpio_tp_irq);	/*Simon:add the irq*/
	client->irq = gpio_to_irq(sprd_3rdparty_gpio_tp_irq);
	if (!i2c_check_functionality(client->adapter, I2C_FUNC_I2C)) {
		err = -ENODEV;
		goto exit_check_functionality_failed;
	}

	gpio_power_config();
	err = test_i2c(client);
	if(err < 0)
	{
		printk("cxiong ctp ------gslX680 test_i2c error, no silead chip------\n");	
		goto exit_check_functionality_failed;
	}	



	printk("==kzalloc=\r\n");
	gslX680_ts = kzalloc(sizeof(*gslX680_ts), GFP_KERNEL);
	if (!gslX680_ts)	{
		err = -ENOMEM;
		goto exit_alloc_data_failed;
	}

	this_client = client;
	//gsl_client = client;	/*Simon delete*/
	i2c_set_clientdata(client, gslX680_ts);

	printk("cxiong ctp I2C addr=%x\r\n", client->addr);
#ifdef GSL_GESTURE
	wake_lock_init(&gsl_wake_lock, WAKE_LOCK_SUSPEND, "gsl_wake_lock");
#endif

	INIT_WORK(&gslX680_ts->pen_event_work, gslX680_ts_worker);

	gslX680_ts->ts_workqueue = create_singlethread_workqueue(dev_name(&client->dev));
	if (!gslX680_ts->ts_workqueue) {
		err = -ESRCH;
		goto exit_create_singlethread;
	}
	
	printk("cxiong ctp %s: ==request_irq=\r\n",__func__);
	printk("cxiong ctp %s IRQ number is %d\r\n", client->name, client->irq);	/*Simon:why client's irq is 0,where is client value ? */
	#ifdef GSL_GESTURE	
	err = request_irq(client->irq,  gslX680_ts_interrupt, IRQF_TRIGGER_RISING | IRQF_ONESHOT | IRQF_NO_SUSPEND, client->name, gslX680_ts);
	#else		
	err = request_irq(client->irq, gslX680_ts_interrupt, IRQF_TRIGGER_RISING, client->name, gslX680_ts);
	#endif
	
	if (err < 0) {
		dev_err(&client->dev, "gslX680_probe: request irq failed\n");
		goto exit_irq_request_failed;
	}

	disable_irq(client->irq);
#if 0    /*VIRTUAL_KEYS*/
	__set_bit(KEY_MENU,  input_dev->keybit);
	__set_bit(KEY_BACK,  input_dev->keybit);
	__set_bit(KEY_HOMEPAGE,  input_dev->keybit);
	__set_bit(BTN_TOUCH, input_dev->keybit);

#endif

	printk("cxiong ctp ==input_allocate_device=\n");
	input_dev = input_allocate_device();
	if (!input_dev) {
		err = -ENOMEM;
		dev_err(&client->dev, "failed to allocate input device\n");
		goto exit_input_dev_alloc_failed;
	}
	
	gslX680_ts->input_dev = input_dev;

	__set_bit(ABS_MT_TOUCH_MAJOR, input_dev->absbit);
	__set_bit(ABS_MT_POSITION_X, input_dev->absbit);
	__set_bit(ABS_MT_POSITION_Y, input_dev->absbit);
	__set_bit(ABS_MT_WIDTH_MAJOR, input_dev->absbit);
	//__set_bit(BTN_TOUCH, input_dev->keybit);


#if 0    /*VIRTUAL_KEYS*/
	__set_bit(KEY_MENU,  input_dev->keybit);
	__set_bit(KEY_BACK,  input_dev->keybit);
	__set_bit(KEY_HOMEPAGE,  input_dev->keybit);
	__set_bit(BTN_TOUCH, input_dev->keybit);

#endif
	input_set_abs_params(input_dev,
				ABS_MT_TRACKING_ID, 0, 255, 0, 0);
	input_set_abs_params(input_dev,
			     ABS_MT_POSITION_X, 0, SCREEN_MAX_X, 0, 0);
	input_set_abs_params(input_dev,
			     ABS_MT_POSITION_Y, 0, SCREEN_MAX_Y, 0, 0);
	input_set_abs_params(input_dev,
			     ABS_MT_TOUCH_MAJOR, 0, PRESS_MAX, 0, 0);
	input_set_abs_params(input_dev,
			     ABS_MT_WIDTH_MAJOR, 0, 200, 0, 0);

	set_bit(EV_ABS, input_dev->evbit);
	set_bit(EV_KEY, input_dev->evbit);
	set_bit(INPUT_PROP_DIRECT, input_dev->propbit);
	input_mt_init_slots(input_dev, 11,0);
#ifdef HAVE_TOUCH_KEY
    for(i = 0; i < MAX_KEY_NUM; i++)
    {
        input_set_capability(input_dev, EV_KEY, key_array[i]);
    }
#endif
#ifdef GSL_GESTURE
    input_set_capability(input_dev, EV_KEY, REPORT_KEY_VALUE);	
	//wxt
    input_set_capability(input_dev, EV_KEY, KEY_C);
    input_set_capability(input_dev, EV_KEY, KEY_E);
    input_set_capability(input_dev, EV_KEY, KEY_M);
    input_set_capability(input_dev, EV_KEY, KEY_O);
    input_set_capability(input_dev, EV_KEY, KEY_V);
    input_set_capability(input_dev, EV_KEY, KEY_W);
    input_set_capability(input_dev, EV_KEY, KEY_Z);
    input_set_capability(input_dev, EV_KEY, KEY_RIGHT);
    input_set_capability(input_dev, EV_KEY, KEY_LEFT);
    input_set_capability(input_dev, EV_KEY, KEY_UP);
    input_set_capability(input_dev, EV_KEY, KEY_DOWN);
    input_set_capability(input_dev, EV_KEY, KEY_F1);
	input_set_capability(input_dev, EV_KEY, KEY_POWER);
#endif
	input_dev->name = GSLX680_TS_NAME;		//dev_name(&client->dev)
	err = input_register_device(input_dev);
	if (err) {
		dev_err(&client->dev,
		"gslX680_ts_probe: failed to register input device: %s\n",
		dev_name(&client->dev));
		goto exit_input_register_device_failed;
	}
#ifdef USE_TP_PSENSOR
	err = tp_ps_init(client);
	if (err) {
		dev_err(&client->dev,
		"gslX680_ts_probe: failed to register input device: %s\n",
		dev_name(&client->dev));
		goto exit_tp_ps_init;
	}
#endif
    

#ifdef GSL_GESTURE
		gsl_FunIICRead(gsl_read_oneframe_data);
		gsl_GestureExternInt(gsl_model_extern,sizeof(gsl_model_extern)/sizeof(unsigned int)/18);
	
#endif
	init_chip(this_client);
	check_mem_data(this_client);

#ifdef TOUCH_VIRTUAL_KEYS
	pixcir_ts_virtual_keys_init();
#endif

	printk("cxiong ctp ==register_early_suspend =");
	gslX680_ts->early_suspend.level = EARLY_SUSPEND_LEVEL_BLANK_SCREEN + 1;
	//gslX680_ts->early_suspend.level = EARLY_SUSPEND_LEVEL_DISABLE_FB + 1;
	gslX680_ts->early_suspend.suspend = gslX680_ts_suspend;
	gslX680_ts->early_suspend.resume	= gslX680_ts_resume;
	register_early_suspend(&gslX680_ts->early_suspend);
	
   	enable_irq(client->irq);


#ifdef TPD_PROC_DEBUG
    //gsl_config_proc = create_proc_entry(GSL_CONFIG_PROC_FILE, 0666, NULL);
	gsl_config_proc = proc_create(GSL_CONFIG_PROC_FILE, 0666, NULL, &tool_ops);

    printk("[tp-gsl] [%s] gsl_config_proc = %x \n",__func__,gsl_config_proc);
    if (gsl_config_proc == NULL)
    {
        print_info("create_proc_entry %s failed\n", GSL_CONFIG_PROC_FILE);
    }
    //else
    //{
    //    gsl_config_proc->read_proc = gsl_config_read_proc;
    //    gsl_config_proc->write_proc = gsl_config_write_proc;
    //}
    gsl_proc_flag = 0;
#endif

#ifdef GSL_MONITOR
	printk( "gsl_ts_probe () : queue gsl_monitor_workqueue\n");

	INIT_DELAYED_WORK(&gsl_monitor_work, gsl_monitor_worker);
	gsl_monitor_workqueue = create_singlethread_workqueue("gsl_monitor_workqueue");
	queue_delayed_work(gsl_monitor_workqueue, &gsl_monitor_work, 1000);
#endif
#ifdef GSL_GESTURE
	#if 1
	gsl_gesture_init();
	spin_lock_init(&resume_lock);  // add 20141030
	#else
	ret= sysfs_create_group(&client->dev.kobj, &gslx680_gesture_group);
	if (ret < 0) {		
		//printk("--zqiang--sysfs_create_group fail--\n");
		return -ENOMEM;													
	}
	#endif
#endif

/************** start here *****************/

	disable_irq_nosync(this_client->irq);
	printk( "reset tp ...... add by Yale\n");
	gpio_set_value(sprd_3rdparty_gpio_tp_rst, 0);
	msleep(10);
	gpio_set_value(sprd_3rdparty_gpio_tp_rst, 1);
	msleep(20);
	reset_chip(this_client);
	startup_chip(this_client);	
	msleep(20);	
	check_mem_data(this_client);
	enable_irq(this_client->irq);

/**************** end here  ****************/
#ifdef USE_TP_PSENSOR
	gsl_gain_psensor_data(client);
#endif
	printk("cxiong ctp %s: ==probe over =\n",__func__);
	return 0;
#ifdef USE_TP_PSENSOR
exit_tp_ps_init:
	tp_ps_uninit();
	input_unregister_device(input_dev);
#endif

exit_input_register_device_failed:
	input_free_device(input_dev);
exit_input_dev_alloc_failed:
	free_irq(client->irq, gslX680_ts);
exit_irq_request_failed:
	cancel_work_sync(&gslX680_ts->pen_event_work);
	destroy_workqueue(gslX680_ts->ts_workqueue);
exit_create_singlethread:
	printk("==singlethread error =\n");
	i2c_set_clientdata(client, NULL);
	kfree(gslX680_ts);
exit_alloc_data_failed:
exit_check_functionality_failed:
	//sprd_free_gpio_irq(gslX680_ts_setup.irq);		/*Simon delete*/
	return err;
}

static int __exit gslX680_ts_remove(struct i2c_client *client)
{

	struct gslX680_ts_data *gslX680_ts = i2c_get_clientdata(client);

	printk("==gslX680_ts_remove=\n");



#ifdef GSL_MONITOR
	cancel_delayed_work_sync(&gsl_monitor_work);
	destroy_workqueue(gsl_monitor_workqueue);
#endif
#ifdef USE_TP_PSENSOR
	tp_ps_uninit();
#endif	
	unregister_early_suspend(&gslX680_ts->early_suspend);
	free_irq(client->irq, gslX680_ts);
	//sprd_free_gpio_irq(gslX680_ts_setup.irq);	/*Simon delete*/
	input_unregister_device(gslX680_ts->input_dev);
	kfree(gslX680_ts);
	cancel_work_sync(&gslX680_ts->pen_event_work);
	destroy_workqueue(gslX680_ts->ts_workqueue);
	i2c_set_clientdata(client, NULL);

	//LDO_TurnOffLDO(LDO_LDO_SIM2);

	return 0;
}

static const struct i2c_device_id gslX680_ts_id[] = {
	{ GSLX680_TS_NAME, 0 },{ }
};


MODULE_DEVICE_TABLE(i2c, gslX680_ts_id);

static const struct of_device_id gslX680_of_match[] = {
       { .compatible = "gslX680,gslX680_ts", },
       { }
};

MODULE_DEVICE_TABLE(of, gslX680_of_match);

static struct i2c_driver gslX680_ts_driver = {
	.probe		= gslX680_ts_probe,
	.remove		= __exit_p(gslX680_ts_remove),
	.id_table	= gslX680_ts_id,
	.driver	= {
		.name	= GSLX680_TS_NAME,
		.owner	= THIS_MODULE,
		.of_match_table = gslX680_of_match,
	},
};

#if I2C_BOARD_INFO_METHOD
static int __init gslX680_ts_init(void)
{
	int ret;
	printk("==gslX680_ts_init==\n");
	ret = i2c_add_driver(&gslX680_ts_driver);
	return ret;
}

static void __exit gslX680_ts_exit(void)
{
	printk("==gslX680_ts_exit==\n");
	i2c_del_driver(&gslX680_ts_driver);
}
#else //register i2c device&driver dynamicly

int sprd_add_i2c_device(struct sprd_i2c_setup_data *i2c_set_data, struct i2c_driver *driver)
{
	struct i2c_board_info info;
	struct i2c_adapter *adapter;
	struct i2c_client *client;
	int ret,err;


	printk("cxiong ctp %s : i2c_bus=%d; slave_address=0x%x; i2c_name=%s",__func__,i2c_set_data->i2c_bus, \
		    i2c_set_data->i2c_address, i2c_set_data->type);

	memset(&info, 0, sizeof(struct i2c_board_info));
	info.addr = i2c_set_data->i2c_address;
	strlcpy(info.type, i2c_set_data->type, I2C_NAME_SIZE);
	if(i2c_set_data->irq > 0)
		info.irq = i2c_set_data->irq;

	adapter = i2c_get_adapter( i2c_set_data->i2c_bus);
	if (!adapter) {
		printk("cxiong ctp %s: can't get i2c adapter %d\n",
			__func__,  i2c_set_data->i2c_bus);
		err = -ENODEV;
		goto err_driver;
	}

	client = i2c_new_device(adapter, &info);
	if (!client) {
		printk("cxiong ctp %s:  can't add i2c device at 0x%x\n",
			__func__, (unsigned int)info.addr);
		err = -ENODEV;
		goto err_driver;
	}

	i2c_put_adapter(adapter);

	ret = i2c_add_driver(driver);
	if (ret != 0) {
		printk("cxiong ctp %s: can't add i2c driver\n", __func__);
		err = -ENODEV;
		goto err_driver;
	}	

	return 0;

err_driver:
	return err;
}

void sprd_del_i2c_device(struct i2c_client *client, struct i2c_driver *driver)
{
	printk("cxiong ctp %s : slave_address=0x%x; i2c_name=%s",__func__, client->addr, client->name);
	i2c_unregister_device(client);
	i2c_del_driver(driver);
}

static int __init gslX680_ts_init(void)
{
	int gslX680_irq;

	printk("cxiong ctp %s\n", __func__);

	//reset
	gpio_direction_output(sprd_3rdparty_gpio_tp_rst, 1);
	gpio_set_value(sprd_3rdparty_gpio_tp_rst, 0);
	msleep(100);
	/*LDO_SetVoltLevel(LDO_LDO_SIM2, LDO_VOLT_LEVEL0);
	LDO_TurnOnLDO(LDO_LDO_SIM2);*/	/*Simon delete*/


	reg_vdd = regulator_get(NULL, "vdd28");
	regulator_set_voltage(reg_vdd, 2800000, 2800000);
	regulator_enable(reg_vdd);

       reg_vddsim2 = regulator_get(NULL, "vddsim2");
       regulator_set_voltage(reg_vddsim2, 1800000, 1800000);
       regulator_set_mode(reg_vddsim2, REGULATOR_MODE_STANDBY);
	regulator_enable(reg_vddsim2);
	msleep(100);	
	
	gpio_set_value(sprd_3rdparty_gpio_tp_rst, 1);
	msleep(20);

	//gpio_direction_input(sprd_3rdparty_gpio_tp_irq);	/*Simon delete*/
	//gslX680_irq=sprd_alloc_gpio_irq(sprd_3rdparty_gpio_tp_irq);
	msleep(10);
	
	/*gslX680_ts_setup.i2c_bus = 2;
	gslX680_ts_setup.i2c_address = GSLX680_TS_ADDR;
	strcpy (gslX680_ts_setup.type,GSLX680_TS_NAME);
	gslX680_ts_setup.irq = gslX680_irq;
	return sprd_add_i2c_device(&gslX680_ts_setup, &gslX680_ts_driver);*/	/*Simon delete*/
}

static void __exit gslX680_ts_exit(void)
{
	printk("%s\n", __func__);
	
	sprd_del_i2c_device(this_client, &gslX680_ts_driver);
}
#endif

late_initcall(gslX680_ts_init);
module_exit(gslX680_ts_exit);

MODULE_AUTHOR("leweihua");
MODULE_DESCRIPTION("GSLX680 TouchScreen Driver");
MODULE_LICENSE("GPL");

