package domain

sealed trait RaceRoute {
  def name: String
  def distanceKm: Int
}

case object Route25KM extends RaceRoute {
  val name = "25KM"
  val distanceKm = 25
}

case object Route10KM extends RaceRoute {
  val name = "10KM"
  val distanceKm = 10
}
