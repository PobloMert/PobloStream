package com.keyiflerolsun

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class DiziPalOrijinalPlugin: Plugin() {
    override fun load(context: Context) {
        AlanAdi.kur(context)
        openSettings = { AlanAdi.ayarEkrani(it, "DiziPalOrijinal") } // eklenti ayarı: site adresini elle değiştirme
        WebViewVekili.kur(context)
        registerMainAPI(DiziPalOrijinal())
        registerExtractorAPI(DiziPalOrijinalPlayer())
    }
}