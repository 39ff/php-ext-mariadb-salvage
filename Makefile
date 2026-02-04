EXTENSION_DIR = ext/mariadb_profiler
PHP_CONFIG ?= php-config

.PHONY: all build install clean test composer ext-configure ext-build ext-install cli-install

all: composer ext-build

## Composer dependencies
composer:
	composer install

## PHP Extension build
ext-configure:
	cd $(EXTENSION_DIR) && phpize && ./configure --enable-mariadb_profiler

ext-build: ext-configure
	cd $(EXTENSION_DIR) && make -j$$(nproc)

ext-install:
	cd $(EXTENSION_DIR) && make install
	@echo ""
	@echo "Add the following to your php.ini:"
	@echo "  extension=mariadb_profiler.so"
	@echo "  mariadb_profiler.enabled = 1"
	@echo "  mariadb_profiler.log_dir = /tmp/mariadb_profiler"
	@echo "  mariadb_profiler.raw_log = 1"
	@echo "  mariadb_profiler.job_check_interval = 1"

install: ext-install cli-install

cli-install:
	@echo "CLI tool available at: cli/mariadb_profiler.php"
	@echo ""
	@echo "Optional: create a symlink:"
	@echo "  ln -sf $$(pwd)/cli/mariadb_profiler.php /usr/local/bin/mariadb-profiler"

## Testing
test: composer
	php tests/test_sql_analyzer.php

test-extension:
	cd $(EXTENSION_DIR) && make test

## Clean
clean:
	cd $(EXTENSION_DIR) && [ -f Makefile ] && make clean || true
	cd $(EXTENSION_DIR) && [ -f configure ] && phpize --clean || true

distclean: clean
	rm -rf vendor composer.lock
