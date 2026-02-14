FROM php:8.3-fpm

# Build dependencies
RUN apt-get update && apt-get install -y \
    autoconf gcc make git unzip libzip-dev \
    && docker-php-ext-install pdo pdo_mysql zip \
    && rm -rf /var/lib/apt/lists/*

# Build mariadb_profiler extension
COPY ext/mariadb_profiler /tmp/ext/mariadb_profiler
WORKDIR /tmp/ext/mariadb_profiler
RUN phpize \
    && ./configure \
    && make -j$(nproc) \
    && make install \
    && rm -rf /tmp/ext

# PHP config for the extension
COPY demo/docker/app/php.ini /usr/local/etc/php/conf.d/99-mariadb-profiler.ini

# Install Composer
COPY --from=composer:2 /usr/bin/composer /usr/bin/composer

# Create Laravel project
WORKDIR /var/www/html
RUN composer create-project laravel/laravel . --prefer-dist --no-interaction

# Install profiler CLI tool
COPY cli /opt/profiler/cli
COPY composer.json /opt/profiler/composer.json
RUN cd /opt/profiler && composer install --no-dev --no-interaction

# Overlay custom Laravel files
COPY demo/laravel-app/app/Http/Controllers/ app/Http/Controllers/
COPY demo/laravel-app/database/migrations/ database/migrations/
COPY demo/laravel-app/database/seeders/ database/seeders/
COPY demo/laravel-app/resources/views/ resources/views/
COPY demo/laravel-app/routes/ routes/

# Create profiler log directory
RUN mkdir -p /var/profiler && chown www-data:www-data /var/profiler

# Entrypoint
COPY demo/docker/app/entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

EXPOSE 9000

ENTRYPOINT ["/entrypoint.sh"]
