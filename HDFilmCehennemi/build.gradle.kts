version = 67

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
    status  = 1 // will be 3 if unspecified
    tvTypes = listOf("Movie", "TvSeries")
    iconUrl = "https://www.google.com/s2/favicons?domain=www.hdfilmcehennemi.nl&sz=%size%"
}

