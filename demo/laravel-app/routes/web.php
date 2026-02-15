<?php

use App\Http\Controllers\DashboardController;
use App\Http\Controllers\DemoQueryController;
use App\Http\Controllers\ProfilerApiController;
use Illuminate\Support\Facades\Route;

// Dashboard UI
Route::get('/', [DashboardController::class, 'index']);

// Profiler API
Route::post('/api/profiler/start', [ProfilerApiController::class, 'start']);
Route::post('/api/profiler/{key}/stop', [ProfilerApiController::class, 'stop']);
Route::get('/api/profiler/jobs', [ProfilerApiController::class, 'list']);

// Demo query runner
Route::post('/api/demo/queries', [DemoQueryController::class, 'run']);
