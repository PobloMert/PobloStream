version = 2

cloudstream {
    authors     = listOf("keyiflerolsun", "muratcesmecioglu", "feroxx", "Ahmet")
    language    = "tr"
    description = "Dizipal Orijinal (dizipal1599.com): yabancı dizi ve filmler. Cloudflare doğrulaması uygulama tarafından geçilir."

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
    **/
    status  = 1 // will be 3 if unspecified
    tvTypes = listOf("TvSeries", "Movie")
    iconUrl = "https://dizipal1599.com/assets/favicon/favicon-32x32.png?v=3"
}
