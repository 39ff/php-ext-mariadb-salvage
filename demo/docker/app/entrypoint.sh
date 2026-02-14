#!/bin/bash
set -e

cd /var/www/html

# Wait for DB to be ready and run migrations
echo "Running migrations..."
php artisan migrate --force --no-interaction 2>/dev/null || true

echo "Running seeders..."
php artisan db:seed --force --no-interaction 2>/dev/null || true

# Clear caches
php artisan config:clear
php artisan route:clear

echo "Starting PHP-FPM..."
exec php-fpm
