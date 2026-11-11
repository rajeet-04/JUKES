package androidx.room

import androidx.room.immediateTransaction
import androidx.room.useWriterConnection

/** Room KMP replacement for room-ktx's Android-only withTransaction. */
suspend fun <R> RoomDatabase.withTransaction(block: suspend () -> R): R =
    useWriterConnection { it.immediateTransaction { block() } }
