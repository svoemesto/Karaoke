package model

fun main() {

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