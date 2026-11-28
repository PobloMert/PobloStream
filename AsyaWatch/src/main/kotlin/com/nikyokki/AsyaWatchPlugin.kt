package com.nikyokki

import com.keyiflerolsun.AlanAdi
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class AsyaWatchPlugin: Plugin() {
    override fun load(context: Context) {
        AlanAdi.kur(context)
        openSettings = { AlanAdi.ayarEkrani(it, "AsyaWatch") } // eklenti ayarı: site adresini elle değiştirme
        registerMainAPI(AsyaWatch())
    }
}
