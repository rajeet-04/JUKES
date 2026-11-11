package androidx.sqlite.db

import androidx.room.RoomRawQuery

/** Android's SimpleSQLiteQuery(sql, args) as a Room KMP raw query. */
fun SimpleSQLiteQuery(sql: String, args: Array<Any?> = emptyArray()): RoomRawQuery = RoomRawQuery(sql) { st ->
    args.forEachIndexed { i, a ->
        when (a) {
            null -> st.bindNull(i + 1)
            is String -> st.bindText(i + 1, a)
            is Boolean -> st.bindBoolean(i + 1, a)
            is Int -> st.bindLong(i + 1, a.toLong())
            is Long -> st.bindLong(i + 1, a)
            is Double -> st.bindDouble(i + 1, a)
            is Float -> st.bindDouble(i + 1, a.toDouble())
            else -> st.bindText(i + 1, a.toString())
        }
    }
}
