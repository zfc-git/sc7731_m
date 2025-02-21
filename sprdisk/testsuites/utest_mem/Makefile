CURDIR = $(shell pwd)
MDIR    := $(CURDIR)/..

include $(MDIR)/Makefile.config

install:build

build:all

all:
	@bash $(CURDIR)/Install.sh $(CC)

clean:
	@bash $(CURDIR)/Install.sh -c
