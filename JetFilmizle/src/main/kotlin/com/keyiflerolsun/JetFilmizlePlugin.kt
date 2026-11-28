package com.keyiflerolsun

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class JetFilmizlePlugin: Plugin() {
    override fun load(context: Context) {
        AlanAdi.kur(context)
        openSettings = { AlanAdi.ayarEkrani(it, "JetFilmizle") } // eklenti ayarı: site adresini elle değiştirme
        WebViewVekili.kur(context) // TarayiciGetir (Cloudflare) için
        registerMainAPI(JetFilmizle())
        registerExtractorAPI(PixelDrain())
    }
}