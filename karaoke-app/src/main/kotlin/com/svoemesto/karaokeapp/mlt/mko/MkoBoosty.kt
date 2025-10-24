package com.svoemesto.karaokeapp.mlt.mko

import com.svoemesto.karaokeapp.mlt.MltGenerator
import com.svoemesto.karaokeapp.mlt.MltProp
import com.svoemesto.karaokeapp.model.*
import com.svoemesto.karaokeapp.xmldata

data class MkoBoosty(val mltProp: MltProp, val type: ProducerType, val voiceId: Int = 0, val childId: Int = 0, val elementId: Int = 0): MltKaraokeObject {
    val mltGenerator = MltGenerator(mltProp, type)

    private val frameWidthPx = mltProp.getFrameWidthPx()
    private val frameHeightPx = mltProp.getFrameHeightPx()
    private val boostyLengthMs = mltProp.getBoostyLengthMs()
    private val boostyBlankTimecode = mltProp.getBoostyBlankTimecode()
    private val boostyStartTimecode = mltProp.getBoostyStartTimecode()
    private val boostyEndTimecode = mltProp.getBoostyEndTimecode()
    private val boostyFadeInTimecode = mltProp.getBoostyFadeInTimecode()
    private val boostyFadeOutTimecode = mltProp.getBoostyFadeOutTimecode()
    private val mkoBoostyBase64 = mltProp.getBase64(ProducerType.BOOSTY)

    override fun producer(): MltNode = mltGenerator
        .producer(
            props = MltNodeBuilder(mltGenerator.defaultProducerPropertiesForMltService("kdenlivetitle"))
                .propertyName("length", boostyLengthMs)
                .propertyName("kdenlive:duration", boostyEndTimecode)
                .propertyName("xmldata", template().toString().xmldata())
                .propertyName("meta.media.width", frameWidthPx)
                .propertyName("meta.media.height", frameHeightPx)
                .build()
        )

    override fun filePlaylist(): MltNode {
        val result = mltGenerator.filePlaylist()
        result.body?.let {
            @Suppress("UNCHECKED_CAST")
            val body = it as MutableList<MltNode>
            body.addAll(MltNodeBuilder().blank(boostyBlankTimecode).build())
            body.add(
                mltGenerator.entry(
                    timecodeIn = boostyStartTimecode,
                    timecodeOut = boostyEndTimecode,
                    nodes = MltNodeBuilder()
                        .propertyName("kdenlive:id", "filePlaylist${mltGenerator.id}")
                        .filterQtblend(mltGenerator.nameFilterQtblend, mainFilePlaylistTransformProperties())
                        .build()
                )
            )
        }
        return result
    }

    override fun mainFilePlaylistTransformProperties(): String {
        val tpStart = TransformProperty(time = boostyStartTimecode, x = 0, y = 0, w = frameWidthPx, h = frameHeightPx, opacity = 0.0)
        val tpFadeIn = TransformProperty(time = boostyFadeInTimecode, x = 0, y = 0, w = frameWidthPx, h = frameHeightPx, opacity = 1.0)
        val tpFadeOut = TransformProperty(time = boostyFadeOutTimecode, x = 0, y = 0, w = frameWidthPx, h = frameHeightPx, opacity = 1.0)
        val tpEnd = TransformProperty(time = boostyEndTimecode, x = 0, y = 0, w = frameWidthPx, h = frameHeightPx, opacity = 0.0)
        val resultListTp = listOf(tpStart, tpFadeIn, tpFadeOut, tpEnd)
        return resultListTp.joinToString(";")
    }

    override fun trackPlaylist(): MltNode = mltGenerator.trackPlaylist()

    override fun tractor(): MltNode = mltGenerator
        .tractor(
            timecodeIn = boostyStartTimecode,
            timecodeOut = boostyEndTimecode
        )

    override fun template(): MltNode = MltNode(
        name = "kdenlivetitle",
        fields = PropertiesMltNodeBuilder()
            .duration("0")
            .lcNumeric("C")
            .width("$frameWidthPx")
            .height("$frameHeightPx")
            .`out`("0")
            .build(),
        body = MltNodeBuilder()
            .item(
                fields = PropertiesMltNodeBuilder()
                    .type("QGraphicsPixmapItem")
                    .zIndex("6")
                    .build(),
                body = MltNodeBuilder()
                    .position(
                        fields = PropertiesMltNodeBuilder()
                            .x("0")
                            .y("0")
                            .build(),
                        body = MltNodeBuilder()
                            .transform(body = "1,0,0,0,1,0,0,0,1")
                            .build()
                    )
                    .content(
                        fields = PropertiesMltNodeBuilder()
                            .base64(mkoBoostyBase64)
                            .build()
                    )
                    .build()
            )
            .build()
    )

}
