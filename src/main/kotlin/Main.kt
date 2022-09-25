fun main(args: Array<String>) {

//    val folder = "/home/nsa/Documents/Караоке/Агата Кристи/1988 - Второй фронт"
//    val file = "(06) [Агата Кристи] Коммунальный блюз"
//    createKaraoke(getSong(getSettings("${folder}/${file}.settings")))

    val mapFiles = mutableListOf<Pair<String,String>>(
        Pair("/home/nsa/Documents/Караоке/Агата Кристи/1988 - Второй фронт","(01) [Агата Кристи] Инспектор ПО"),
        Pair("/home/nsa/Documents/Караоке/Агата Кристи/1988 - Второй фронт","(02) [Агата Кристи] Гномы-каннибалы"),
        Pair("/home/nsa/Documents/Караоке/Агата Кристи/1988 - Второй фронт","(03) [Агата Кристи] Пантера"),
        Pair("/home/nsa/Documents/Караоке/Агата Кристи/1988 - Второй фронт","(04) [Агата Кристи] Неживая вода"),
        Pair("/home/nsa/Documents/Караоке/Агата Кристи/1988 - Второй фронт","(05) [Агата Кристи] Ты уходишь"),
        Pair("/home/nsa/Documents/Караоке/Агата Кристи/1988 - Второй фронт","(06) [Агата Кристи] Коммунальный блюз"),
        Pair("/home/nsa/Documents/Караоке/Агата Кристи/1994 - Опиум","(01) [Агата Кристи] ХалиГалиКришна"),
        Pair("/home/nsa/Documents/Караоке/Агата Кристи/1994 - Опиум","(05) [Агата Кристи] Черная луна"),
        Pair("/home/nsa/Documents/Караоке/Агата Кристи/1994 - Опиум","(06) [Агата Кристи] Сказочная тайга"),
        Pair("/home/nsa/Documents/Караоке/Агата Кристи/1994 - Опиум","(11) [Агата Кристи] Опиум для никого"),
    )
    mapFiles.forEach {
        createKaraoke(getSong(getSettings("${it.first}/${it.second}.settings")))
    }

//    val folder = "/home/nsa/Documents/Караоке/Агата Кристи/1994 - Опиум"
//    val file = "(11) [Агата Кристи] Опиум для никого"
//    createKaraoke(getSong(getSettings("${folder}/${file}.settings")))


}

