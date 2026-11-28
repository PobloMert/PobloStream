version = 28

cloudstream {
    authors     = listOf("keyiflerolsun", "feroxx", "Ahmet")
    language    = "tr"
    description = "Diziyou en kaliteli TÃ¼rkÃ§e dublaj ve altyazÄ±lÄ± yabancÄ± dizi izleme sitesidir. GÃ¼ncel ve efsanevi dizileri 1080p Full HD kalitede izlemek iÃ§in hemen tÄ±kla!"

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
    **/
    status  = 1 // will be 3 if unspecified
    tvTypes = listOf("TvSeries")
    iconUrl = "https://cdn.diziyou.love/wp-content/uploads/2020/04/diziyou-favicon.png"
}
