package com.fueltracker.ui

import com.fueltracker.data.CarDatabase

internal suspend fun ensureDbLoaded(db: CarDatabase) {
    check(db.ensureCatalogLoaded() != null) { "车型目录加载失败" }
}