package com.nikyokki

import com.keyiflerolsun.AlanAdi
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class TvDizilerPlugin: Plugin() {
    override fun load(context: Context) {
        AlanAdi.kur(context)
        openSettings = { AlanAdi.ayarEkrani(it, "TvDiziler") } // eklenti ayarı: site adresini elle değiştirme
        registerMainAPI(TvDiziler())
        registerExtractorAPI(TvDizilerOynat())
    }
}