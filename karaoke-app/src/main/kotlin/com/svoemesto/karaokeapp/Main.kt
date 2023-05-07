package com.svoemesto.karaokeapp

fun main(args: Array<String>) {

//    val folder = "/home/nsa/Documents/Караоке/Агата Кристи/1988 - Второй фронт"
//    val file = "(06) [Агата Кристи] Коммунальный блюз"
//    createKaraoke(getSong(getSettings("${folder}/${file}.settings")))

    val mapFiles = mutableListOf<Pair<String,String>>(




//        Pair("/home/nsa/Documents/Караоке/Пикник/2012 - Певец декаданса","2012 (01) [Пикник] - Декаданс"),
//        Pair("/home/nsa/Documents/Караоке/Пикник/2012 - Певец декаданса","2012 (02) [Пикник] - Игла"),
//        Pair("/home/nsa/Documents/Караоке/Пикник/2012 - Певец декаданса","2012 (03) [Пикник] - За пижоном пижон"),
//        Pair("/home/nsa/Documents/Караоке/Пикник/2012 - Певец декаданса","2012 (04) [Пикник] - Вплети меня в своё кружево"),
//        Pair("/home/nsa/Documents/Караоке/Пикник/2012 - Певец декаданса","2012 (05) [Пикник] - Клоун беспощадный"),
//        Pair("/home/nsa/Documents/Караоке/Пикник/2012 - Певец декаданса","2012 (06) [Пикник] - Гильотины сечение, веревки петля"),
//        Pair("/home/nsa/Documents/Караоке/Пикник/2012 - Певец декаданса","2012 (07) [Пикник] - Трилогия"),
//        Pair("/home/nsa/Documents/Караоке/Пикник/2012 - Певец декаданса","2012 (08) [Пикник] - Инкогнито"),
//        Pair("/home/nsa/Documents/Караоке/Пикник/2012 - Певец декаданса","2012 (09) [Пикник] - Прикосновение"),
//        Pair("/home/nsa/Documents/Караоке/Пикник/2012 - Певец декаданса","2012 (10) [Пикник] - Звезда Декаданс"),
//        Pair("/home/nsa/Documents/Караоке/Пикник/2012 - Певец декаданса","2012 (11) [Пикник] - Быть может... (утешительная)"),

//        Pair("/home/nsa/Documents/Караоке/Пикник/2014 - Чужестранец","2014 (01) [Пикник] - Кем бы ты ни был"),
//        Pair("/home/nsa/Documents/Караоке/Пикник/2014 - Чужестранец","2014 (02) [Пикник] - Чужестранец"),
//        Pair("/home/nsa/Documents/Караоке/Пикник/2014 - Чужестранец","2014 (03) [Пикник] - Потерянный"),
//        Pair("/home/nsa/Documents/Караоке/Пикник/2014 - Чужестранец","2014 (04) [Пикник] - Азбука Морзе"),
//        Pair("/home/nsa/Documents/Караоке/Пикник/2014 - Чужестранец","2014 (05) [Пикник] - Мотылёк"),
//        Pair("/home/nsa/Documents/Караоке/Пикник/2014 - Чужестранец","2014 (06) [Пикник] - Песня эмигранта"),
//        Pair("/home/nsa/Documents/Караоке/Пикник/2014 - Чужестранец","2014 (07) [Пикник] - Танго «Чёрная каракатица»"),
//        Pair("/home/nsa/Documents/Караоке/Пикник/2014 - Чужестранец","2014 (08) [Пикник] - Бетховен"),
//        Pair("/home/nsa/Documents/Караоке/Пикник/2014 - Чужестранец","2014 (09) [Пикник] - Вот и я не иду до конца"),
//        Pair("/home/nsa/Documents/Караоке/Пикник/2014 - Чужестранец","2014 (10) [Пикник] - Письмо"),

//        Pair("/home/nsa/Documents/Караоке/Пикник/2017 - Искры и Канкан","2017 (01) [Пикник] - Лихие пришли времена"),
//        Pair("/home/nsa/Documents/Караоке/Пикник/2017 - Искры и Канкан","2017 (02) [Пикник] - Злая кровь"),
//        Pair("/home/nsa/Documents/Караоке/Пикник/2017 - Искры и Канкан","2017 (03) [Пикник] - Парню 90 лет"),
//        Pair("/home/nsa/Documents/Караоке/Пикник/2017 - Искры и Канкан","2017 (04) [Пикник] - Последний из Могикан"),
//        Pair("/home/nsa/Documents/Караоке/Пикник/2017 - Искры и Канкан","2017 (05) [Пикник] - Всё хорошо!"),
//        Pair("/home/nsa/Documents/Караоке/Пикник/2017 - Искры и Канкан","2017 (06) [Пикник] - Ты кукла из папье-маше"),
//        Pair("/home/nsa/Documents/Караоке/Пикник/2017 - Искры и Канкан","2017 (07) [Пикник] - Большая игра"),
//        Pair("/home/nsa/Documents/Караоке/Пикник/2017 - Искры и Канкан","2017 (08) [Пикник] - Принцесса"),
//        Pair("/home/nsa/Documents/Караоке/Пикник/2017 - Искры и Канкан","2017 (09) [Пикник] - Зачем"),
//        Pair("/home/nsa/Documents/Караоке/Пикник/2017 - Искры и Канкан","2017 (10) [Пикник] - Ничего..."),

//        Pair("/home/nsa/Documents/Караоке/Пикник/2019 - В руках великана","2019 (01) [Пикник] - Счастливчик"),
//        Pair("/home/nsa/Documents/Караоке/Пикник/2019 - В руках великана","2019 (02) [Пикник] - В руках великана"),
//        Pair("/home/nsa/Documents/Караоке/Пикник/2019 - В руках великана","2019 (03) [Пикник] - Лиловый корсет"),
//        Pair("/home/nsa/Documents/Караоке/Пикник/2019 - В руках великана","2019 (04) [Пикник] - Сияние"),
//        Pair("/home/nsa/Documents/Караоке/Пикник/2019 - В руках великана","2019 (05) [Пикник] - Такая их карма"),
//        Pair("/home/nsa/Documents/Караоке/Пикник/2019 - В руках великана","2019 (06) [Пикник] - Разноцветные ленты"),
//        Pair("/home/nsa/Documents/Караоке/Пикник/2019 - В руках великана","2019 (07) [Пикник] - Фильм окончен"),
//        Pair("/home/nsa/Documents/Караоке/Пикник/2019 - В руках великана","2019 (08) [Пикник] - Душа самурая - меч"),
//        Pair("/home/nsa/Documents/Караоке/Пикник/2019 - В руках великана","2019 (09) [Пикник] - Где душа летает…"),
//        Pair("/home/nsa/Documents/Караоке/Пикник/2019 - В руках великана","2019 (10) [Пикник] - Эпизод № 10"),
//        Pair("/home/nsa/Documents/Караоке/Пикник/2019 - В руках великана","2019 (11) [Пикник] - Grand finale"),

//        Pair("/home/nsa/Documents/Караоке/Пикник/2022 - Весёлый и злой","2022 (01) [Пикник] - Играй, струна, играй"),
//        Pair("/home/nsa/Documents/Караоке/Пикник/2022 - Весёлый и злой","2022 (02) [Пикник] - Дивись же, какими мы стали!"),
//        Pair("/home/nsa/Documents/Караоке/Пикник/2022 - Весёлый и злой","2022 (03) [Пикник] - Только не плачь, палач"),
//        Pair("/home/nsa/Documents/Караоке/Пикник/2022 - Весёлый и злой","2022 (04) [Пикник] - Сквозь одежды"),
//        Pair("/home/nsa/Documents/Караоке/Пикник/2022 - Весёлый и злой","2022 (05) [Пикник] - Женщина"),
//        Pair("/home/nsa/Documents/Караоке/Пикник/2022 - Весёлый и злой","2022 (06) [Пикник] - В любую минуту"),
//        Pair("/home/nsa/Documents/Караоке/Пикник/2022 - Весёлый и злой","2022 (07) [Пикник] - Одиночество"),
//        Pair("/home/nsa/Documents/Караоке/Пикник/2022 - Весёлый и злой","2022 (08) [Пикник] - Весёлый и злой"),
//        Pair("/home/nsa/Documents/Караоке/Пикник/2022 - Весёлый и злой","2022 (09) [Пикник] - Всё перевернётся"),
//        Pair("/home/nsa/Documents/Караоке/Пикник/2022 - Весёлый и злой","2022 (10) [Пикник] - Колдун"),
//        Pair("/home/nsa/Documents/Караоке/Пикник/2022 - Весёлый и злой","2022 (11) [Пикник] - Утро"),



//        Pair("/home/nsa/Documents/Караоке/Сплин/2004 - Черновики","2004 (01) [Александр Васильев] - Кто-то не успел"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2004 - Черновики","2004 (02) [Александр Васильев] - Мне 20 лет"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2004 - Черновики","2004 (03) [Александр Васильев] - Конец прекрасной эпохи"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2004 - Черновики","2004 (05) [Александр Васильев] - Загладь вину свою"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2004 - Черновики","2004 (07) [Александр Васильев] - Корень Мандрагоры"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2004 - Черновики","2004 (08) [Александр Васильев] - Небо в алмазах"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2004 - Черновики","2004 (10) [Александр Васильев] - Домовой"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2004 - Черновики","2004 (13) [Александр Васильев] - Двое не спят"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2004 - Черновики","2004 (14) [Александр Васильев] - Рождество"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2004 - Черновики","2004 (15) [Александр Васильев] - Пурга-кочерга"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2004 - Черновики","2004 (16) [Александр Васильев] - Двуречье (я ничего не скрыл)"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2007 - Раздвоение личности","2007 (01) [Сплин] - Мелькнула чья-то тень"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2007 - Раздвоение личности","2007 (02) [Сплин] - Скажи"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2007 - Раздвоение личности","2007 (03) [Сплин] - Матч"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2007 - Раздвоение личности","2007 (04) [Сплин] - На счастье!"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2007 - Раздвоение личности","2007 (05) [Сплин] - Волна"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2007 - Раздвоение личности","2007 (06) [Сплин] - Лепесток"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2007 - Раздвоение личности","2007 (07) [Сплин] - Император"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2007 - Раздвоение личности","2007 (08) [Сплин] - Бетховен"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2007 - Раздвоение личности","2007 (09) [Сплин] - Маяк"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2007 - Раздвоение личности","2007 (10) [Сплин] - Праздник"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2007 - Раздвоение личности","2007 (11) [Сплин] - Сухари и сушки"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2007 - Раздвоение личности","2007 (12) [Сплин] - Мобильный"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2007 - Раздвоение личности","2007 (13) [Сплин] - Колокол"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2007 - Раздвоение личности","2007 (14) [Сплин] - Пробки"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2007 - Раздвоение личности","2007 (15) [Сплин] - Мамма Миа"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2007 - Раздвоение личности","2007 (16) [Сплин] - Прочь из моей головы"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2007 - Раздвоение личности","2007 (17) [Сплин] - Сын"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2009 - Сигнал из космоса","2009 (01) [Сплин] - Настройка звука"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2009 - Сигнал из космоса","2009 (02) [Сплин] - Дыши легко"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2009 - Сигнал из космоса","2009 (03) [Сплин] - Добро пожаловать!"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2009 - Сигнал из космоса","2009 (04) [Сплин] - Больше никакого рок-н-ролла"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2009 - Сигнал из космоса","2009 (05) [Сплин] - Вниз головой"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2009 - Сигнал из космоса","2009 (06) [Сплин] - Чердак"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2009 - Сигнал из космоса","2009 (07) [Сплин] - Зеленая песня"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2009 - Сигнал из космоса","2009 (08) [Сплин] - Камень"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2009 - Сигнал из космоса","2009 (09) [Сплин] - 3007"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2009 - Сигнал из космоса","2009 (10) [Сплин] - Без тормозов"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2009 - Сигнал из космоса","2009 (11) [Сплин] - Корабль ждет!"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2009 - Сигнал из космоса","2009 (12) [Сплин] - Человек не спал"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2009 - Сигнал из космоса","2009 (13) [Сплин] - Ковчег"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2009 - Сигнал из космоса","2009 (14) [Сплин] - Выпусти меня отсюда"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2009 - Сигнал из космоса","2009 (15) [Сплин] - Письмо"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2009 - Сигнал из космоса","2009 (16) [Сплин] - Все так странно"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2009 - Сигнал из космоса","2009 (17) [Сплин] - Вальс"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2009 - Сигнал из космоса","2009 (18) [Сплин] - До встречи!"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2012 - Обман зрения","2012 (01) [Сплин] - Увертюра"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2012 - Обман зрения","2012 (02) [Сплин] - Летела жизнь"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2012 - Обман зрения","2012 (03) [Сплин] - Черная волга"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2012 - Обман зрения","2012 (04) [Сплин] - Лестница"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2012 - Обман зрения","2012 (05) [Сплин] - Страшная тайна"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2012 - Обман зрения","2012 (06) [Сплин] - Петербургская свадьба"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2012 - Обман зрения","2012 (07) [Сплин] - Дочь самурая"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2012 - Обман зрения","2012 (08) [Сплин] - Фибоначчи"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2012 - Обман зрения","2012 (09) [Сплин] - В мире иллюзий"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2012 - Обман зрения","2012 (11) [Сплин] - Ковш"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2012 - Обман зрения","2012 (12) [Сплин] - Солнце взойдет"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2012 - Обман зрения","2012 (13) [Сплин] - Чудак"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2012 - Обман зрения","2012 (14) [Сплин] - Волшебное слово"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2014 - Резонанс","2014 (01) [Сплин] - Всадник"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2014 - Резонанс","2014 (02) [Сплин] - Ай лов ю!"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2014 - Резонанс","2014 (03) [Сплин] - Старый дом"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2014 - Резонанс","2014 (04) [Сплин] - Мороз по коже"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2014 - Резонанс","2014 (05) [Сплин] - Мысль"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2014 - Резонанс","2014 (06) [Сплин] - Есть кто-нибудь живой"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2014 - Резонанс","2014 (07) [Сплин] - Рай в шалаше"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2014 - Резонанс","2014 (08) [Сплин] - Всё наоборот"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2014 - Резонанс","2014 (09) [Сплин] - Помолчим немного"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2014 - Резонанс","2014 (10) [Сплин] - Пусть играет музыка!"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2014 - Резонанс","2014 (11) [Сплин] - Горизонт событий"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2014 - Резонанс","2014 (12) [Сплин] - Среди зимы"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2014 - Резонанс","2014 (13) [Сплин] - Дверной глазок"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2014 - Резонанс","2014 (14) [Сплин] - Подводная песня"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2014 - Резонанс","2014 (15) [Сплин] - Красота"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2014 - Резонанс","2014 (16) [Сплин] - Оркестр"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2014 - Резонанс","2014 (17) [Сплин] - Песня на одном аккорде"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2014 - Резонанс","2014 (18) [Сплин] - Два плюс один"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2014 - Резонанс","2014 (19) [Сплин] - Полная Луна"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2014 - Резонанс","2014 (20) [Сплин] - Танцуй!"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2014 - Резонанс","2014 (21) [Сплин] - Симфония"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2014 - Резонанс","2014 (22) [Сплин] - Нефть"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2014 - Резонанс","2014 (23) [Сплин] - Пожар"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2014 - Резонанс","2014 (24) [Сплин] - Шахматы"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2014 - Резонанс","2014 (25) [Сплин] - Исчезаем в темноте"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2016 - Ключ к шифру","2016 (01) [Сплин] - Медный грош"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2016 - Ключ к шифру","2016 (02) [Сплин] - Пирамиды"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2016 - Ключ к шифру","2016 (03) [Сплин] - Нам, мудрецам"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2016 - Ключ к шифру","2016 (04) [Сплин] - Храм"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2016 - Ключ к шифру","2016 (05) [Сплин] - Окраины"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2016 - Ключ к шифру","2016 (06) [Сплин] - Кит"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2016 - Ключ к шифру","2016 (07) [Сплин] - Она была так прекрасна"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2016 - Ключ к шифру","2016 (08) [Сплин] - Джа играет джаз"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2016 - Ключ к шифру","2016 (09) [Сплин] - Реквием"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2016 - Ключ к шифру","2016 (10) [Сплин] - Земля уходит из-под ног"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2016 - Ключ к шифру","2016 (11) [Сплин] - Тревога"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2016 - Ключ к шифру","2016 (12) [Сплин] - Небесный хор"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2016 - Ключ к шифру","2016 (13) [Сплин] - Путь на восток"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2016 - Ключ к шифру","2016 (14) [Сплин] - День за днём"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2016 - Ключ к шифру","2016 (15) [Сплин] - Череп и кости"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2018 - Встречная полоса","2018 (01) [Сплин] - Встречная полоса"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2018 - Встречная полоса","2018 (02) [Сплин] - На утро"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2018 - Встречная полоса","2018 (03) [Сплин] - Чей-то ребёнок"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2018 - Встречная полоса","2018 (04) [Сплин] - Испанская инквизиция"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2018 - Встречная полоса","2018 (05) [Сплин] - Тепло родного дома"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2018 - Встречная полоса","2018 (06) [Сплин] - Булгаковский марш"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2018 - Встречная полоса","2018 (07) [Сплин] - Шпионы"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2018 - Встречная полоса","2018 (08) [Сплин] - Шаман"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2018 - Встречная полоса","2018 (09) [Сплин] - Когда пройдёт 100 лет"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2018 - Встречная полоса","2018 (10) [Сплин] - Волк"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2018 - Встречная полоса","2018 (11) [Сплин] - Яблоко"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2019 - Тайком","2019 (01) [Сплин] - Воздушный шар"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2019 - Тайком","2019 (02) [Сплин] - Тайком"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2019 - Тайком","2019 (03) [Сплин] - Атом"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2019 - Тайком","2019 (04) [Сплин] - Гимн"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2019 - Тайком","2019 (05) [Сплин] - Волшебная скрипка"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2019 - Тайком","2019 (06) [Сплин] - Важная вещь"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2020 - Вира и майна","2020 (01) [Сплин] - Призрак"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2020 - Вира и майна","2020 (02) [Сплин] - За семью печатями"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2020 - Вира и майна","2020 (03) [Сплин] - Беги, моя жизнь"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2020 - Вира и майна","2020 (04) [Сплин] - Джин"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2020 - Вира и майна","2020 (05) [Сплин] - Фаза"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2020 - Вира и майна","2020 (06) [Сплин] - Дежавю"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2020 - Вира и майна","2020 (07) [Сплин] - Кофейня"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2020 - Вира и майна","2020 (08) [Сплин] - Кесарь"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2020 - Вира и майна","2020 (09) [Сплин] - Фильм ужасов"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2020 - Вира и майна","2020 (10) [Сплин] - Кошмары"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2020 - Вира и майна","2020 (11) [Сплин] - Передайте это Гарри Поттеру, если вдруг его встретите"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2021-1 - Вирус","2021-1 (01) [Сплин] - Вирус"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2021-2 Я был влюблён в Вас","2021-2 (01) [Сплин] - Я был влюблён в Вас"),
//        Pair("/home/nsa/Documents/Караоке/Сплин/2021-3 Топай!","2021-3 (01) [Сплин] - Топай!"),

        Pair("","")
    )
    mapFiles.forEach {
        if (it.first != "" && it.second != "") {
            createKaraokeAll("${it.first}/${it.second}.settings")
        }

    }

//    val folder = "/home/nsa/Documents/Караоке/Агата Кристи/1994 - Опиум"
//    val file = "(11) [Агата Кристи] Опиум для никого"
//    createKaraoke(getSong(getSettings("${folder}/${file}.settings")))


}
