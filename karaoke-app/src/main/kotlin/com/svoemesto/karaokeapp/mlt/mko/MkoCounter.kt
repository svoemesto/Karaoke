package com.svoemesto.karaokeapp.mlt.mko

import com.svoemesto.karaokeapp.Karaoke
import com.svoemesto.karaokeapp.mlt.MltGenerator
import com.svoemesto.karaokeapp.mlt.MltProp
import com.svoemesto.karaokeapp.mlt.MltText
import com.svoemesto.karaokeapp.mlt.mltNode
import com.svoemesto.karaokeapp.model.MltNode
import com.svoemesto.karaokeapp.model.MltNodeBuilder
import com.svoemesto.karaokeapp.model.ProducerType
import com.svoemesto.karaokeapp.xmldata
import java.awt.Color

/**
 * Класс Mko Counter.
 *
 * @see archive/docs/features/mlt-generator.md
 */
data class MkoCounter(
    val mltProp: MltProp,
    val type: ProducerType,
    val voiceId: Int = 0,
    val childId: Int = 0,
    val elementId: Int = 0,
) : MltKaraokeObject {
    val mltGenerator = MltGenerator(mltProp, type, voiceId, childId)

    /**
     * BR-243: предупреждения о некорректной конфигурации счётчиков (`voices`,
     * `countersColors`). Категория зарегистрирована в
     * `knowledge/domains/monitoring/components/log-categories.md` (`infra.render.counters`).
     */
    private val log: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger("infra.render.counters")

    private val frameWidthPx = mltProp.getFrameWidthPx()
    private val frameHeightPx = mltProp.getFrameHeightPx()
    private val songLengthFr = mltProp.getSongLengthFr()
    private val songEndTimecode = mltProp.getSongEndTimecode()
    private val mkoCounterId = mltProp.getId(listOf(ProducerType.COUNTERS, voiceId))
    private val mkoCounterFilePlaylistTransformProperties = mltProp.getRect(listOf(type, voiceId, childId))
    private val mkoCounterPositionXPx = mltProp.getPositionXPx(listOf(ProducerType.COUNTER, voiceId))
    private val mkoCounterPositionYPx = mltProp.getPositionYPx(listOf(ProducerType.COUNTER))
    private val fontSize = mltProp.getFontSize()

    override fun producer(): MltNode =
        mltGenerator
            .producer(
                props =
                    MltNodeBuilder(mltGenerator.defaultProducerPropertiesForMltService("kdenlivetitle"))
                        .propertyName("kdenlive:folderid", mkoCounterId)
                        .propertyName("length", songLengthFr)
                        .propertyName("kdenlive:duration", songEndTimecode)
                        .propertyName("xmldata", template().toString().xmldata())
                        .propertyName("meta.media.width", frameWidthPx)
                        .propertyName("meta.media.height", frameHeightPx)
                        .build(),
            )

    override fun filePlaylist(): MltNode {
        val result = mltGenerator.filePlaylist()
        result.body?.let {
            @Suppress("UNCHECKED_CAST")
            val body = it as MutableList<MltNode>
            body.add(
                mltGenerator.entry(
                    nodes =
                        MltNodeBuilder()
                            .propertyName("kdenlive:id", "filePlaylist${mltGenerator.id}")
                            .filterQtblend(mltGenerator.nameFilterQtblend, mainFilePlaylistTransformProperties())
                            .build(),
                ),
            )
        }
        return result
    }

    override fun mainFilePlaylistTransformProperties(): String = mkoCounterFilePlaylistTransformProperties

    override fun trackPlaylist(): MltNode = mltGenerator.trackPlaylist()

    override fun tractor(): MltNode = mltGenerator.tractor()

    override fun template(): MltNode {
        // BR-243: конфигурационные списки индексировались без проверки размера:
        // `Karaoke.voices[0].groups[0]` и `Karaoke.countersColors[childId]`. Значения
        // берутся из админских свойств (`POST /api/properties/setproperty` → строковое
        // свойство без валидации), поэтому короткий/битый список давал
        // IndexOutOfBoundsException ПРЯМО В `template()` — падала генерация `.mlt`.
        // Теперь: проверка размера + fallback (первый голос/первая группа, последний
        // цвет) + предупреждение в лог.
        val voices = Karaoke.voices
        val voice = voices.getOrNull(0)
        val group = voice?.groups?.getOrNull(0)
        if (voice == null || group == null) {
            log.warn(
                "MkoCounter: в свойстве 'voices' нет ни одного голоса с группами — " +
                    "используется запасной шрифт счётчика",
            )
        }
        val baseMltText = group?.mltText ?: Karaoke.melodyTabsFont
        val mltText: MltText = baseMltText.copy(childId.toString(), fontSize)
        val colors = Karaoke.countersColors
        val color =
            colors.getOrNull(childId)
                ?: colors.lastOrNull()
                ?: run {
                    log.warn("MkoCounter: свойство 'countersColors' пусто — используется цвет по умолчанию")
                    Color(255, 255, 255, 255)
                }
        if (colors.size <= childId) {
            log.warn(
                "MkoCounter: в 'countersColors' ${colors.size} цветов, а счётчик #$childId — " +
                    "используется последний цвет",
            )
        }
        mltText.shapeColor = color

        return MltNode(
            name = "kdenlivetitle",
            fields =
                mutableMapOf(
                    Pair("duration", "0"),
                    Pair("LC_NUMERIC", "C"),
                    Pair("width", "$frameWidthPx"),
                    Pair("height", "$frameHeightPx"),
                    Pair("out", "0"),
                ),
            body =
                mutableListOf(
                    MltNode(
                        name = "item",
                        fields =
                            mutableMapOf(
                                Pair("type", "QGraphicsTextItem"),
                                Pair("z-index", "0"),
                            ),
                        body =
                            mutableListOf(
                                MltNode(
                                    name = "position",
                                    fields = mutableMapOf(Pair("x", "$mkoCounterPositionXPx"), Pair("y", "$mkoCounterPositionYPx")),
                                    body = mutableListOf(MltNode(name = "transform", body = "1,0,0,0,1,0,0,0,1")),
                                ),
                                mltText.mltNode(childId.toString()),
                            ),
                    ),
                    MltNode(name = "startviewport", fields = mutableMapOf(Pair("rect", "0,0,$frameWidthPx,$frameHeightPx"))),
                    MltNode(name = "endviewport", fields = mutableMapOf(Pair("rect", "0,0,$frameWidthPx,$frameHeightPx"))),
                    MltNode(name = "background", fields = mutableMapOf(Pair("color", "0,0,0,0"))),
                ),
        )
    }
}
