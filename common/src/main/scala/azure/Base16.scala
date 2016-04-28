package azure

object Base16 {
  def encode(source: String): String =
    source.getBytes("UTF-8").map("%02X".format(_)).mkString.toLowerCase
}
