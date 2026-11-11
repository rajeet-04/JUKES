package androidx.room.util

import androidx.collection.ArrayMap

/** Android-only helper the generated relation code calls: fetch in chunks of 999 keys. */
fun <K : Any, V> recursiveFetchArrayMap(map: ArrayMap<K, V>, isRelationCollection: Boolean, fetchBlock: (ArrayMap<K, V>) -> Unit) {
    var tmp = ArrayMap<K, V>(); var n = 0
    for ((k, v) in map.entries.toList()) {
        tmp[k] = v; n++
        if (n == 999) { fetchBlock(tmp); if (!isRelationCollection) tmp.forEach { (k2, v2) -> map.put(k2, v2) }; tmp = ArrayMap<K, V>(); n = 0 }
    }
    if (n > 0) { fetchBlock(tmp); if (!isRelationCollection) tmp.forEach { (k2, v2) -> map.put(k2, v2) } }
}
