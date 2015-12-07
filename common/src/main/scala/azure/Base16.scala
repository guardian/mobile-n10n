package azure

object Base16 {
  val base = 16

  def encode(source: String): String =
    source.getBytes("UTF-8").map("%02X".format(_)).mkString.toLowerCase

  def decode(encoded: String): String = {
    val bytes = encoded.grouped(2).map(group => java.lang.Byte.parseByte(group, base))
    new String(bytes.toArray, "UTF-8")
  }
}
