DDR_DIR =$(SRCTREE)/ddr
DDR_INIT_DIR = $(addprefix $(DDR_DIR)/,ddr_init)
DDR_SCAN_DIR = $(addprefix $(DDR_DIR)/,ddr_scan)
DDR_TEST_DIR = $(addprefix $(DDR_DIR)/,ddr_test)


### ============ part one: ddr init documents, need modification with different chipset ==========### 
ddr_init_dir = $(DDR_DIR)/ddr_common

ifdef CONFIG_WHALE
ddr_init_dir += $(DDR_INIT_DIR)/init/v0_0
ddr_init_dir += $(DDR_INIT_DIR)/qos/v0_0
ddr_init_dir += $(DDR_INIT_DIR)/timing/v0_0
endif
### ============================================================================================ ###

ddr_init_c_list = $(shell find $(ddr_common_dir) $(ddr_init_dir) $(ddr_qos_dir) $(ddr_timing_dir) -name "*.[c]")
ddr_init_h_list = $(shell find $(ddr_common_dir) $(ddr_init_dir) $(ddr_qos_dir) $(ddr_timing_dir) -name "*.[h]")
ddr_init_inc_dir = $(addprefix -I,$(dir $(ddr_init_h_list)) ) 

ddr_init_obj_list = $(ddr_init_c_list:%.c=%.o)
ddr_init_obj_dir  = $(DDR_DIR)/obj

DDR_COBJS = $(notdir $(ddr_init_c_list:%.c=%.o))

DDR_INIT: $(ddr_init_obj_list)
	@echo "ddr_init_obj_dir"=$(ddr_init_obj_dir)
	@echo "ddr_init_obj_list"=$(ddr_init_obj_list)
 
$(ddr_init_obj_list):%.o:%.c
	$(CC) $(CFLAGS) $(ddr_init_inc_dir)  -c -o $@ $<
	-mkdir -p  $(OBJTREE)/nand_spl/board/$(VENDOR)/$(SOC)
	-cp $@ $(OBJTREE)/nand_fdl/fdl-1/. 
	-mv $@ $(OBJTREE)/nand_spl/board/$(VENDOR)/$(SOC)/. 

ddr_init_clean:
	-rm $(DDR_INIT_DIR)/output/ -r
	#-find . -name *.o |xargs rm  -r

ddr_init_print:
	@echo "ddr_init_inc_dir"=$(ddr_init_inc_dir) 
	@echo "ddr_init_c_list"=$(ddr_init_c_list)
	@echo "ddr_init_h_list"=$(ddr_init_h_list)
	@echo "ddr_init_obj_list"=$(ddr_init_obj_list)
