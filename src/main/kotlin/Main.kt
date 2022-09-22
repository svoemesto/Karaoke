fun main(args: Array<String>) {

    val folder = "/home/nsa/Documents/Караоке/Агата Кристи/1994 - Опиум"
    val file = "(11) [Агата Кристи] Опиум для никого"
    getLyric(getSong(getSettings("${folder}/${file}.settings")))

}

