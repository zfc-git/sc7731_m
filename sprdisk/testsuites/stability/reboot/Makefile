CDIR    := $(shell pwd)
MDIR    := $(CURDIR)/../..

include $(MDIR)/Makefile.config

TARGET := S50test


install:
	@install -m 0755 -D $(TARGET) $(VROOT)/etc/init.d/$(TARGET)

build:


clean: