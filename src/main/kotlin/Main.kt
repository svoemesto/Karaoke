fun main(args: Array<String>) {

    val folder = "/home/nsa/Documents/Караоке/Агата Кристи/1988 - Второй фронт"
    val file = "(04) [Агата Кристи] Неживая вода"
    createKaraoke(getSong(getSettings("${folder}/${file}.settings")))

}

