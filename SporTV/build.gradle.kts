version = 3

cloudstream {
    authors     = listOf("Ahmet")
    language    = "tr"
    description = "Spor kanallarının kendi sitelerinden ücretsiz yayınladığı canlı yayınlar: TRT Spor, TRT Spor Yıldız, A Spor, HT Spor, TJK TV, TAY TV. Kanal listesi depodaki JSON'dan okunur."

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
    **/
    status  = 1
    tvTypes = listOf("Live")
    iconUrl = "https://www.google.com/s2/favicons?domain=trt.net.tr&sz=%size%"
}
