version = 68

cloudstream {
    authors     = listOf("hexated", "keyiflerolsun", "Ahmet")
    language    = "tr"
    description = "TÃ¼rkiye'nin en hÄ±zlÄ± hd film izleme sitesi"

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
    **/
    status  = 0 // 2026-11-28: site detay sayfalarında uygulama tarayıcısını engelliyor; haftalık test düzelince 1 yapar
    tvTypes = listOf("Movie", "TvSeries")
    iconUrl = "https://www.google.com/s2/favicons?domain=www.hdfilmcehennemi.nl&sz=%size%"
}

