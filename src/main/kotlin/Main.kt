import model.Settings
import model.Song

fun main(args: Array<String>) {

//    val folder = "/home/nsa/Documents/Караоке/Агата Кристи/1988 - Второй фронт"
//    val file = "(06) [Агата Кристи] Коммунальный блюз"
//    createKaraoke(getSong(getSettings("${folder}/${file}.settings")))

    val mapFiles = mutableListOf<Pair<String,String>>(
//        Pair("/home/nsa/Documents/Караоке/Разное/Infornal Fuckъ - Конунг Олаф Моржовый Хер","Конунг Олаф Моржовый Хер"),
//        Pair("/home/nsa/Documents/Караоке/Ундервуд/2002 - Все пройдет, Милая","(01) [Ундервуд] Все что надо"),
//        Pair("/home/nsa/Documents/Караоке/Ундервуд/2002 - Все пройдет, Милая","(02) [Ундервуд] Гагарин, я Вас любила"),
//        Pair("/home/nsa/Documents/Караоке/Ундервуд/2002 - Все пройдет, Милая","(03) [Ундервуд] Следи за ее левой рукой"),
//        Pair("/home/nsa/Documents/Караоке/Ундервуд/2002 - Все пройдет, Милая","(04) [Ундервуд] Истребитель № 0"),
//        Pair("/home/nsa/Documents/Караоке/Ундервуд/2002 - Все пройдет, Милая","(05) [Ундервуд] Парабеллум"),
//        Pair("/home/nsa/Documents/Караоке/Ундервуд/2002 - Все пройдет, Милая","(06) [Ундервуд] Федюнчик, посмотри 'Титаник'"),
//        Pair("/home/nsa/Documents/Караоке/Ундервуд/2002 - Все пройдет, Милая","(07) [Ундервуд] Пока я любил тебя"),
//        Pair("/home/nsa/Documents/Караоке/Ундервуд/2002 - Все пройдет, Милая","(08) [Ундервуд] Ауфвидерзейн"),
//        Pair("/home/nsa/Documents/Караоке/Ундервуд/2002 - Все пройдет, Милая","(09) [Ундервуд] Все пройдет, милая"),
//        Pair("/home/nsa/Documents/Караоке/Ундервуд/2002 - Все пройдет, Милая","(10) [Ундервуд] Ампутация"),
//        Pair("/home/nsa/Documents/Караоке/Ундервуд/2002 - Все пройдет, Милая","(11) [Ундервуд] Колыбельная джинна"),
//        Pair("/home/nsa/Documents/Караоке/Ундервуд/2003 - Красная кнопка","(07) [Ундервуд] Гулливер"),
//        Pair("",""),
//        Pair("",""),
//        Pair("",""),
//        Pair("",""),
//        Pair("",""),
//        Pair("",""),
//        Pair("",""),
//        Pair("/home/nsa/Documents/Караоке/Агата Кристи/1988 - Второй фронт","(01) [Агата Кристи] Инспектор ПО"),
//        Pair("/home/nsa/Documents/Караоке/Агата Кристи/1988 - Второй фронт","(02) [Агата Кристи] Гномы-каннибалы"),
//        Pair("/home/nsa/Documents/Караоке/Агата Кристи/1988 - Второй фронт","(03) [Агата Кристи] Пантера"),
//        Pair("/home/nsa/Documents/Караоке/Агата Кристи/1988 - Второй фронт","(04) [Агата Кристи] Неживая вода"),
//        Pair("/home/nsa/Documents/Караоке/Агата Кристи/1988 - Второй фронт","(05) [Агата Кристи] Ты уходишь"),
//        Pair("/home/nsa/Documents/Караоке/Агата Кристи/1988 - Второй фронт","(06) [Агата Кристи] Коммунальный блюз"),
//        Pair("/home/nsa/Documents/Караоке/Агата Кристи/1994 - Опиум","(01) [Агата Кристи] ХалиГалиКришна"),
//        Pair("/home/nsa/Documents/Караоке/Агата Кристи/1994 - Опиум","(05) [Агата Кристи] Черная луна"),
//        Pair("/home/nsa/Documents/Караоке/Агата Кристи/1994 - Опиум","(06) [Агата Кристи] Сказочная тайга"),
//        Pair("/home/nsa/Documents/Караоке/Агата Кристи/1994 - Опиум","(11) [Агата Кристи] Опиум для никого"),
        Pair("","")
    )
    mapFiles.forEach {
        if (it.first != "" && it.second != "") {
            createKaraoke(Song(Settings("${it.first}/${it.second}.settings")))
        }

    }

//    val folder = "/home/nsa/Documents/Караоке/Агата Кристи/1994 - Опиум"
//    val file = "(11) [Агата Кристи] Опиум для никого"
//    createKaraoke(getSong(getSettings("${folder}/${file}.settings")))


}

