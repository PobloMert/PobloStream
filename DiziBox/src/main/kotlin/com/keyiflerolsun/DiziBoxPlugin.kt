package com.keyiflerolsun

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class DiziBoxPlugin: Plugin() {
    override fun load(context: Context) {
        AlanAdi.kur(context)
        openSettings = { AlanAdi.ayarEkrani(it, "DiziBox") } // eklenti ayarı: site adresini elle değiştirme
        WebViewVekili.kur(context) // TarayiciGetir (Cloudflare) için
        registerMainAPI(DiziBox())
    }
}