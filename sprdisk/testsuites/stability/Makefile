SUBDIRS = $(shell find * -maxdepth 0 -type d)
install:
	@for sub in $(SUBDIRS); do		\
		make -C $$sub install;		\
		[ $$? -eq 0 ] || exit 1;	\
	done

build:
	@for sub in $(SUBDIRS); do		\
		make -C $$sub build;		\
		[ $$? -eq 0 ] || exit 1;	\
	done

clean:
	@for sub in $(SUBDIRS); do		\
		make -C $$sub clean;		\
		[ $$? -eq 0 ] || exit 1;	\
	done
