package com.nikyokki

import com.keyiflerolsun.AlanAdi
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class DMaxPlugin: Plugin() {
    override fun load(context: Context) {
        AlanAdi.kur(context)
        openSettings = { AlanAdi.ayarEkrani(it, "DMax") } // eklenti ayarı: site adresini elle değiştirme
        registerMainAPI(DMax())
    }
}