CDIR    := $(shell pwd)
MDIR    := $(CURDIR)/../..

include $(MDIR)/Makefile.config

TARGET := suspend.sh


install:
	@install -m 0755 -D $(TARGET) $(VROOT)/usr/bin/$(TARGET)

build:


clean: