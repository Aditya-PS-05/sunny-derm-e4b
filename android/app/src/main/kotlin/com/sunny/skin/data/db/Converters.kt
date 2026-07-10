package com.sunny.skin.data.db

import androidx.room.TypeConverter
import com.sunny.skin.data.model.BodyPart

/** Enum <-> String converters for Room. */
class Converters {
    @TypeConverter fun bodyPartToString(p: BodyPart): String = p.name
    @TypeConverter fun stringToBodyPart(s: String): BodyPart = BodyPart.valueOf(s)

    @TypeConverter fun scanTypeToString(t: ScanType): String = t.name
    @TypeConverter fun stringToScanType(s: String): ScanType = ScanType.valueOf(s)
}
