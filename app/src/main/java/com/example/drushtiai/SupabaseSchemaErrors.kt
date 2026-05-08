package com.example.drushtiai

/** PostgREST / GoTrue errors when tables were never created (run `supabase_schema.sql`). */
internal fun Throwable.indicatesMissingSupabaseTable(tableName: String): Boolean {
    val m = buildString {
        var t: Throwable? = this@indicatesMissingSupabaseTable
        while (t != null) {
            t.message?.let { append(it).append(' ') }
            t = t.cause
        }
    }.lowercase()
    val t = tableName.lowercase()
    return (m.contains("could not find the table") || m.contains("schema cache")) &&
        m.contains(t)
}
