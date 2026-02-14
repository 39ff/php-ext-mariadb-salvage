#!/bin/bash
set -e

cd /var/www/html

# Ensure storage directories exist and are writable
mkdir -p storage/framework/{sessions,views,cache}
chown -R www-data:www-data storage bootstrap/cache

# Run migrations (show output for debugging)
echo "Running migrations..."
php artisan migrate --force --no-interaction || {
    echo "WARNING: Migration failed, retrying in 5s..."
    sleep 5
    php artisan migrate --force --no-interaction
}

echo "Running seeders..."
php artisan db:seed --force --no-interaction 2>&1 || echo "WARNING: Seeder failed (may already be seeded)"

# Clear caches
php artisan config:clear
php artisan route:clear
php artisan view:clear

echo "Starting PHP-FPM..."
exec php-fpm
