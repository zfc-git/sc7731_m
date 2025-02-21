CDIR    := $(shell pwd)
MDIR    := $(CURDIR)/../..

include $(MDIR)/Makefile.config

TARGET := disk_test
SH_TARGET := *.sh
SOURCE := disk_test.c
CFLAGS += -static
LDFLAGS += -lrt -lpthread -static

install: build
	@install -m 0755 -D $(TARGET) $(VROOT)/usr/bin/$(TARGET)
	@install -m 0755 -D $(SH_TARGET) $(VROOT)/usr/bin/

build: $(TARGET)

$(TARGET): $(SOURCE)
	$(CC) $(CFLAGS) $(LDFLAGS) -o $@ $^

clean:
	-rm -rf *.o $(TARGET)