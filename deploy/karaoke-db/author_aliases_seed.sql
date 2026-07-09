-- Разовое авто-заполнение алиасов авторов (солисты/участники групп) веб-поиском.
-- ЧЕРНОВИК: значения найдены агентами через веб-поиск, пользователь должен перепроверить
-- и поправить вручную через админку (webvue3, раздел Авторы → колонка "Алиасы").
-- Применяется только на LOCAL БД; сюда попали только авторы, для которых нашёлся хотя бы
-- один правдоподобный алиас (сольники/неопознанные коллективы оставлены без изменений).

UPDATE tbl_authors SET aliases = 'Иван Демьян' WHERE id = 1; -- 7Б
UPDATE tbl_authors SET aliases = 'Алексей Медведев' WHERE id = 2; -- Infornal FuckЪ
UPDATE tbl_authors SET aliases = 'Артём Леденев' WHERE id = 3; -- LEDENEV
UPDATE tbl_authors SET aliases = 'Рустем Булатов' WHERE id = 4; -- Lumen
UPDATE tbl_authors SET aliases = 'Вячеслав Бутусов' WHERE id = 5; -- Nautilus Pompilius
UPDATE tbl_authors SET aliases = 'Николай Клицов' WHERE id = 7; -- RYZE
UPDATE tbl_authors SET aliases = 'Юлия Соломонова' WHERE id = 8; -- SOLA MONOVA
UPDATE tbl_authors SET aliases = 'Вячеслав Погосов' WHERE id = 9; -- Slavik Pogosov
UPDATE tbl_authors SET aliases = 'Денис Коренев' WHERE id = 10; -- The Rigans
UPDATE tbl_authors SET aliases = 'Алексей Юзленко' WHERE id = 11; -- Znaki
UPDATE tbl_authors SET aliases = 'Андрей Горохов' WHERE id = 12; -- АДО
UPDATE tbl_authors SET aliases = 'Вадим Самойлов;Глеб Самойлов' WHERE id = 13; -- Агата Кристи
UPDATE tbl_authors SET aliases = 'Александр Уваров' WHERE id = 14; -- Александр Лаэртский
UPDATE tbl_authors SET aliases = 'Константин Кинчев' WHERE id = 15; -- АлисА
UPDATE tbl_authors SET aliases = 'Константин Кулясов' WHERE id = 17; -- АнимациЯ
UPDATE tbl_authors SET aliases = 'Валерий Кипелов' WHERE id = 19; -- Ария
UPDATE tbl_authors SET aliases = 'Артур Вячеславович Михеев' WHERE id = 20; -- Артур Беркут
UPDATE tbl_authors SET aliases = 'Леонид Фёдоров;Олег Гаркуша' WHERE id = 21; -- АукцЫон
UPDATE tbl_authors SET aliases = 'Игорь Сукачёв' WHERE id = 23; -- БИРТМАН, Гарик Сукачёв
UPDATE tbl_authors SET aliases = 'Вадим Степанцов' WHERE id = 24; -- Бахыт Компот
UPDATE tbl_authors SET aliases = 'Вадим Степанцов' WHERE id = 25; -- Бахыт Компот, Мамульки Bend, Оксана Архиреева
UPDATE tbl_authors SET aliases = 'Зоя Ященко' WHERE id = 26; -- Белая Гвардия
UPDATE tbl_authors SET aliases = 'Жанна Агузарова;Валерий Сюткин' WHERE id = 28; -- Браво
UPDATE tbl_authors SET aliases = 'Игорь Сукачёв' WHERE id = 29; -- Бригада С
UPDATE tbl_authors SET aliases = 'Илья Калинников' WHERE id = 33; -- Високосный год
UPDATE tbl_authors SET aliases = 'Алексей Горшенёв' WHERE id = 36; -- ГОРШЕНЕВ
UPDATE tbl_authors SET aliases = 'Игорь Сукачёв' WHERE id = 37; -- Гарик Сукачёв
UPDATE tbl_authors SET aliases = 'Александр Чвала' WHERE id = 38; -- ГештальТ
UPDATE tbl_authors SET aliases = 'Глеб Самойлов' WHERE id = 39; -- Глеб Самойлоff & The MatriXX
UPDATE tbl_authors SET aliases = 'Михаил Горшенёв' WHERE id = 40; -- Горшок
UPDATE tbl_authors SET aliases = 'Егор Летов' WHERE id = 41; -- Гражданская Оборона
UPDATE tbl_authors SET aliases = 'Юрий Шевчук' WHERE id = 42; -- ДДТ
UPDATE tbl_authors SET aliases = 'Алексей Поддубный' WHERE id = 44; -- Джанго
UPDATE tbl_authors SET aliases = 'Дмитрий Сычёв' WHERE id = 46; -- Дом Кукол
UPDATE tbl_authors SET aliases = 'Роман Билык' WHERE id = 48; -- Звери
UPDATE tbl_authors SET aliases = 'Константин Арбенин' WHERE id = 49; -- Зимовье зверей
UPDATE tbl_authors SET aliases = 'Станислав Шклярский' WHERE id = 51; -- Инкогнито
UPDATE tbl_authors SET aliases = 'Виктор Цой' WHERE id = 54; -- КИНО
UPDATE tbl_authors SET aliases = 'Дмитрий Ревякин' WHERE id = 55; -- Калинов Мост
UPDATE tbl_authors SET aliases = 'Валерий Кипелов' WHERE id = 56; -- Кипелов
UPDATE tbl_authors SET aliases = 'Андрей Князев' WHERE id = 57; -- КняZz
UPDATE tbl_authors SET aliases = 'Алёна Апина' WHERE id = 58; -- Комбинация
UPDATE tbl_authors SET aliases = 'Михаил Горшенёв;Андрей Князев' WHERE id = 60; -- Король и Шут
UPDATE tbl_authors SET aliases = 'Армен Григорян' WHERE id = 62; -- Крематорий
UPDATE tbl_authors SET aliases = 'Алексей Горшенёв' WHERE id = 63; -- Кукрыниксы
UPDATE tbl_authors SET aliases = 'Сергей Шнуров' WHERE id = 64; -- Ленинград
UPDATE tbl_authors SET aliases = 'Николай Расторгуев' WHERE id = 66; -- Любэ
UPDATE tbl_authors SET aliases = 'Хелависа;Наталья О''Шей' WHERE id = 67; -- Мельница
UPDATE tbl_authors SET aliases = 'Гарик Сукачёв' WHERE id = 71; -- Неприкасаемые
UPDATE tbl_authors SET aliases = 'Алексей Кортнев' WHERE id = 72; -- Несчастный Случай
UPDATE tbl_authors SET aliases = 'Фёдор Чистяков' WHERE id = 73; -- Ноль
UPDATE tbl_authors SET aliases = 'Вячеслав Бутусов' WHERE id = 76; -- Орден Славы (ТРЕБУЕТ ПРОВЕРКИ)
UPDATE tbl_authors SET aliases = 'Вахтанг Кикабидзе;Нани Брегвадзе' WHERE id = 77; -- Орэра
UPDATE tbl_authors SET aliases = 'Эдмунд Шклярский' WHERE id = 79; -- Пикник
UPDATE tbl_authors SET aliases = 'Илья Чёрт;Илья Кнабенгоф' WHERE id = 80; -- Пилот
UPDATE tbl_authors SET aliases = 'Максим Леонидов;Николай Фоменко' WHERE id = 84; -- Секрет
UPDATE tbl_authors SET aliases = 'Юрий Хой;Юрий Клинских' WHERE id = 85; -- Сектор Газа
UPDATE tbl_authors SET aliases = 'Сергей Галанин' WHERE id = 86; -- СерьГа
UPDATE tbl_authors SET aliases = 'Герман Загуменов' WHERE id = 87; -- Спектакль Джо (ТРЕБУЕТ ПРОВЕРКИ)
UPDATE tbl_authors SET aliases = 'Александр Васильев' WHERE id = 88; -- Сплин
UPDATE tbl_authors SET aliases = 'Дмитрий Спирин' WHERE id = 89; -- Тараканы!
UPDATE tbl_authors SET aliases = 'Максим Иванов' WHERE id = 91; -- Торба-на-Круче (ТРЕБУЕТ ПРОВЕРКИ)
UPDATE tbl_authors SET aliases = 'Владимир Ткаченко;Владимир Кучеренко' WHERE id = 92; -- Ундервуд
UPDATE tbl_authors SET aliases = 'Наталья О''Шей' WHERE id = 95; -- Хелависа
UPDATE tbl_authors SET aliases = 'Владимир Шахрин' WHERE id = 97; -- ЧайФ
UPDATE tbl_authors SET aliases = 'Сергей Чиграков' WHERE id = 98; -- Чиж & Co
UPDATE tbl_authors SET aliases = 'Анатолий Крупнов' WHERE id = 100; -- Чёрный Обелиск
UPDATE tbl_authors SET aliases = 'Дмитрий Варшавский' WHERE id = 101; -- Чёрный кофе
UPDATE tbl_authors SET aliases = 'Юрий Мелисов' WHERE id = 103; -- Эпидемия
UPDATE tbl_authors SET aliases = 'Вячеслав Бутусов' WHERE id = 104; -- Ю-Питер
UPDATE tbl_authors SET aliases = 'Анна Осипова' WHERE id = 109; -- Юта
UPDATE tbl_authors SET aliases = 'Ольга Вайнер' WHERE id = 111; -- Ясвена (ТРЕБУЕТ ПРОВЕРКИ)
UPDATE tbl_authors SET aliases = 'Антон Вартанов' WHERE id = 113; -- Магнитная Аномалия (ТРЕБУЕТ ПРОВЕРКИ)
UPDATE tbl_authors SET aliases = 'Владимир Ткаченко;Владимир Корниенко' WHERE id = 114; -- Проект «Игроки» (ТРЕБУЕТ ПРОВЕРКИ)
UPDATE tbl_authors SET aliases = 'Егор Летов' WHERE id = 116; -- Гражданская оборона
UPDATE tbl_authors SET aliases = 'Егор Летов' WHERE id = 118; -- Егор и Опизденевшие
UPDATE tbl_authors SET aliases = 'Ксения Островская' WHERE id = 122; -- Princesse Angine (ТРЕБУЕТ ПРОВЕРКИ)
UPDATE tbl_authors SET aliases = 'Роман Неумоев' WHERE id = 124; -- Инструкция по выживанию
