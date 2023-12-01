package com.svoemesto.karaokeapp.model

import java.io.Serializable


data class MltNode (
    var name: String = "",
    var fields: MutableMap<String, String> = mutableMapOf(),
    var body: Any? = null,
    var type: ProducerType? = null
) : Serializable {
    override fun toString(): String {
        return "<${name} ${fields.map { "${it.key}=\"${it.value}\"" }.joinToString(" ")}${if (body == null) "/>" else ">${if (body is List<*>) "\n  ${(body as List<*>).map {it.toString()}.joinToString("\n  ")}\n" else "${body}"}</${name}>"}"
    }
}

data class MltNodeBuilder(val nodes: MutableList<MltNode> = mutableListOf()) {

    fun node(node: MltNode) = apply {
        nodes.add(node)
    }

    fun nodes(nodesToAdd: List<MltNode>) = apply {
        nodes.addAll(nodesToAdd)
    }

    fun transitionsAndFilters(parentName: String, count: Int) = apply {
        for (i in 0  until  count) {
            nodes.add(
                MltNode(
                    name = "transition",
                    fields = mutableMapOf("id" to "${parentName}_transition_${i}"),
                    body = MltNodeBuilder()
                        .propertyName("a_track", 0)
                        .propertyName("b_track", i+1)
                        .propertyName("compositing", 0)
                        .propertyName("distort", 0)
                        .propertyName("rotate_center", 0)
                        .propertyName("mlt_service", "qtblend")
                        .propertyName("kdenlive_id", "qtblend")
                        .propertyName("internal_added", 237)
                        .propertyName("always_active", 1)
                        .build()
                )
            )
        }
        nodes.add(
            MltNode(
                name = "filter",
                fields = mutableMapOf("id" to "${parentName}_filter_volume"),
                body = MltNodeBuilder()
                    .propertyName("window", 75)
                    .propertyName("max_gain", "20dB")
                    .propertyName("mlt_service", "volume")
                    .propertyName("internal_added", 237)
                    .propertyName("disable", 1)
                    .build()
            )
        )
        nodes.add(
            MltNode(
                name = "filter",
                fields = mutableMapOf("id" to "${parentName}_filter_panner"),
                body = MltNodeBuilder()
                    .propertyName("channel", -1)
                    .propertyName("mlt_service", "panner")
                    .propertyName("internal_added", 237)
                    .propertyName("disable", 1)
                    .propertyName("start", "0.5")
                    .build()
            )
        )
    }


    fun propertyName(name: String, value: Any? = null) = apply {

        val result = MltNode(
            name = "property",
            fields = mutableMapOf("name" to name),
            body = value
        )
        val findedNode = nodes.firstOrNull { it.name == result.name && it.fields == result.fields }

        if (findedNode == null) {
            nodes.add(
                MltNode(
                    name = "property",
                    fields = mutableMapOf("name" to name),
                    body = value
                )
            )
        } else {
            findedNode.body = result.body
        }
    }

    fun property(fields: MutableMap<String, String> = mutableMapOf(), body: Any? = null, type: ProducerType? = null) = apply {
        nodes.add(
            MltNode(
                name = "property",
                fields = fields,
                body = body,
                type = type
            )
        )
    }

    fun track(fields: MutableMap<String, String> = mutableMapOf(), body: Any? = null, type: ProducerType? = null) = apply {
        nodes.add(
            MltNode(
                name = "track",
                fields = fields,
                body = body,
                type = type
            )
        )
    }

    fun blank(length: String) = apply {
        nodes.add(
            MltNode(
                name = "blank",
                fields = PropertiesMltNodeBuilder().length(length).build()
            )
        )
    }

    fun entry(fields: MutableMap<String, String> = mutableMapOf(), body: Any? = null, type: ProducerType? = null) = apply {
        nodes.add(
            MltNode(
                name = "entry",
                fields = fields,
                body = body,
                type = type
            )
        )
    }

    fun filter(fields: MutableMap<String, String> = mutableMapOf(), body: Any? = null, type: ProducerType? = null) = apply {
        nodes.add(
            MltNode(
                name = "filter",
                fields = fields,
                body = body,
                type = type
            )
        )
    }

    fun item(fields: MutableMap<String, String> = mutableMapOf(), body: Any? = null, type: ProducerType? = null) = apply {
        nodes.add(
            MltNode(
                name = "item",
                fields = fields,
                body = body,
                type = type
            )
        )
    }

    fun position(fields: MutableMap<String, String> = mutableMapOf(), body: Any? = null, type: ProducerType? = null) = apply {
        nodes.add(
            MltNode(
                name = "position",
                fields = fields,
                body = body,
                type = type
            )
        )
    }

    fun content(fields: MutableMap<String, String> = mutableMapOf(), body: Any? = null, type: ProducerType? = null) = apply {
        nodes.add(
            MltNode(
                name = "content",
                fields = fields,
                body = body,
                type = type
            )
        )
    }
    fun transform(fields: MutableMap<String, String> = mutableMapOf(), body: Any? = null, type: ProducerType? = null) = apply {
        nodes.add(
            MltNode(
                name = "transform",
                fields = fields,
                body = body,
                type = type
            )
        )
    }

    fun filterVolume(name: String, level: Any? = null) = apply {
        nodes.add(
            MltNode(
                name = "filter",
                fields = PropertiesMltNodeBuilder().id(name).build(),
                body = if (level == null) {
                    MltNodeBuilder()
                        .propertyName("window",75)
                        .propertyName("max_gain","20dB")
                        .propertyName("mlt_service","volume")
                        .propertyName("internal_added",237)
                        .propertyName("disable",1)
                        .build()
                } else {
                    MltNodeBuilder()
                        .propertyName("window",75)
                        .propertyName("max_gain","20dB")
                        .propertyName("level",level)
                        .propertyName("mlt_service","volume")
                        .propertyName("kdenlive_id","volume")
                        .propertyName("kdenlive:collapsed",0)
                        .build()
                }
            )
        )
    }

    fun filterPanner(name: String) = apply {
        nodes.add(
            MltNode(
                name = "filter",
                fields = PropertiesMltNodeBuilder().id(name).build(),
                body = MltNodeBuilder()
                    .propertyName("channel",-1)
                    .propertyName("mlt_service","panner")
                    .propertyName("internal_added",237)
                    .propertyName("start","0.5")
                    .propertyName("disable",1)
                    .build()
            )
        )
    }

    fun filterAudiolevel(name: String) = apply {
        nodes.add(
            MltNode(
                name = "filter",
                fields = PropertiesMltNodeBuilder().id(name).build(),
                body = MltNodeBuilder()
                    .propertyName("iec_scale",0)
                    .propertyName("mlt_service","audiolevel")
                    .propertyName("dbpeak",1)
                    .propertyName("disable",1)
                    .build()
            )
        )
    }

    fun filterQtblend(name: String, bodyRect: Any?, distort: Int = 0) = apply {
        nodes.add(
            MltNode(
                name = "filter",
                fields = PropertiesMltNodeBuilder().id(name).build(),
                body = MltNodeBuilder()
                    .propertyName("rotate_center",1)
                    .propertyName("mlt_service","qtblend")
                    .propertyName("kdenlive_id","qtblend")
                    .propertyName("rect", bodyRect)
                    .propertyName("compositing",0)
                    .propertyName("distort",distort)
                    .propertyName("kdenlive:collapsed",0)
                    .propertyName("rotation","00:00:00.000=0")
                    .build()
            )
        )
    }

    fun filterGamma(name: String, body: Any?) = apply {
        nodes.add(
            MltNode(
                name = "filter",
                fields = PropertiesMltNodeBuilder().id(name).build(),
                body = body
            )
        )
    }

    fun build(): MutableList<MltNode> {
        return nodes
    }
}

data class PropertiesMltNodeBuilder(val properties: MutableMap<String, String> = mutableMapOf()) {

    fun id(value: String) = apply { properties["id"] = value }
    fun name(value: String) = apply { properties["name"] = value }
    fun length(value: String) = apply { properties["length"] = value }
    fun `in`(value: String) = apply { properties["in"] = value }
    fun `out`(value: String) = apply { properties["out"] = value }
    fun hide(value: String) = apply { properties["hide"] = value }
    fun hideByType(type: ProducerType) = apply { properties["hide"] = if (!type.isAudio) "audio" else "video" }
    fun producer(value: String) = apply { properties["producer"] = value }
    fun type(value: String) = apply { properties["type"] = value }
    fun `z-index`(value: String) = apply { properties["z-index"] = value }
    fun x(value: String) = apply { properties["x"] = value }
    fun y(value: String) = apply { properties["y"] = value }
    fun base64(value: String) = apply { properties["base64"] = value }
    fun duration(value: String) = apply { properties["duration"] = value }
    fun LC_NUMERIC(value: String) = apply { properties["LC_NUMERIC"] = value }
    fun width(value: String) = apply { properties["width"] = value }
    fun height(value: String) = apply { properties["height"] = value }

    fun build(): MutableMap<String, String> {
        return properties
    }
}