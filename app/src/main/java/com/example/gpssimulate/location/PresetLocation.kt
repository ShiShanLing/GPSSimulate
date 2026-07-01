package com.example.gpssimulate.location

data class PresetLocation(
    val name: String,
    val latitude: Double,
    val longitude: Double,
) {
    fun toStorageString(): String = "$name,$latitude,$longitude"

    companion object {
        fun fromStorageString(value: String): PresetLocation? {
            val parts = value.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.size != 3) return null
            val latitude = parts[1].toDoubleOrNull() ?: return null
            val longitude = parts[2].toDoubleOrNull() ?: return null
            if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
            return PresetLocation(parts[0], latitude, longitude)
        }
    }
}

object PresetLocationParser {
    fun parse(input: String): Result<PresetLocation> {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("输入不能为空"))
        }

        val parts = trimmed.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size != 3) {
            return Result.failure(
                IllegalArgumentException("格式应为：城市, 纬度, 经度（例如：苏州, 31.3167, 120.6167）")
            )
        }

        val name = parts[0]
        if (name.isEmpty()) {
            return Result.failure(IllegalArgumentException("城市名称不能为空"))
        }

        val latitude = parts[1].toDoubleOrNull()
            ?: return Result.failure(IllegalArgumentException("纬度格式不正确"))
        val longitude = parts[2].toDoubleOrNull()
            ?: return Result.failure(IllegalArgumentException("经度格式不正确"))

        if (latitude !in -90.0..90.0) {
            return Result.failure(IllegalArgumentException("纬度应在 -90 到 90 之间"))
        }
        if (longitude !in -180.0..180.0) {
            return Result.failure(IllegalArgumentException("经度应在 -180 到 180 之间"))
        }

        return Result.success(PresetLocation(name, latitude, longitude))
    }
}
