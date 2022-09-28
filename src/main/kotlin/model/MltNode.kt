package model

fun main() {



    val prop1 = MltNode(
        name = "property",
        fields = mutableMapOf(Pair("name", "length")),
        body = "123432"
    )

    val prop2 = MltNode(
        name = "property",
        fields = mutableMapOf(Pair("name", "eof")),
        body = "pause"
    )

    val producer = MltNode(
        name = "producer",
        fields = mutableMapOf(Pair("id", "producer_song_text"), Pair("in", "00:00:00.000"), Pair("out", "00:05:05.500")),
        body = listOf(prop1, prop2)
    )

    println(producer)

}

data class MltNode (
    var name: String = "",
    var fields: MutableMap<String, String> = mutableMapOf(),
    var body: Any? = null,
    var type: ProducerType? = null
) {
    override fun toString(): String {
        return "<${name} ${fields.map { "${it.key}=\"${it.value}\"" }.joinToString(" ")}${if (body == null) "/>" else ">${if (body is List<*>) "\n  ${(body as List<*>).map {it.toString()}.joinToString("\n  ")}\n" else "${body}"}</${name}>"}"
    }
}