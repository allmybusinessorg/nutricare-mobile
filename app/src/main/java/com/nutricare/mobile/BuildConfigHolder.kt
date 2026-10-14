package com.nutricare.mobile

/**
 * Public configuration values. The anon key is a public client key by design
 * (the same one embedded in the website bundle); Row Level Security on the
 * server is what actually protects data.
 */
object BuildConfigHolder {
    const val SUPABASE_URL: String = "https://lanbwwevtjnzvxfunupc.supabase.co"
    const val SUPABASE_ANON_KEY: String = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImxhbmJ3d2V2dGpuenZ4ZnVudXBjIiwicm9sZSI6ImFub24iLCJpYXQiOjE3Nzg5Mjc5MDYsImV4cCI6MjA5NDUwMzkwNn0.XBBeFcZKbEMsJhCGuK1OiRRdstWn8fWlW-62nwXTALI"
    const val API_BASE_URL: String = "https://nutricarepro.vercel.app"
    /** Google OAuth Web client ID (same Google Cloud project the website's Google provider uses). */
    const val GOOGLE_WEB_CLIENT_ID: String = "140208742814-lkp97jhronf339f4hign58lhb7ppgglr.apps.googleusercontent.com"
}
