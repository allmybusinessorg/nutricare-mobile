package com.nutricare.mobile

/**
 * Public configuration values. The anon key is a public client key by design
 * (the same one embedded in the website bundle); Row Level Security on the
 * server is what actually protects data.
 */
object BuildConfigHolder {
    const val SUPABASE_URL: String = "https://lanbwwevtjnzvxfunupc.supabase.co"
    const val SUPABASE_ANON_KEY: String = "eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImxhbmJ3d2V2dGpuenZ4ZnVudXBjIiwicm9sZSI6ImFub24iLCJpYXQiOjE3Nzg5Mjc5MDYsImV4cCI6MjA5NDUwMzkwNn0"
    const val API_BASE_URL: String = "https://nutricarepro.vercel.app"
}
