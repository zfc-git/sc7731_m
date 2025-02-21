
CDIR    := $(shell pwd)
MDIR    := $(CDIR)

include $(MDIR)/Makefile.config

SUBDIRS = $(shell find * -maxdepth 0 -type d)

include $(MDIR)/Makefile.build
