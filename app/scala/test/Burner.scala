package test

// Set of possible types for a Burner event notification.
object EventType extends Enumeration {
  type EventType = Value
  val InboundText = Value("inboundText")
  val InboundMedia = Value("inboundMedia")
  val VoiceMail = Value("voiceMail")
}

// Structure of the event JSON sent to our registered webhook serivce URL.
case class Event(eventType: EventType.Value, payload: String, fromNumber: String, toNumber: String, userId: String, burnerId: String)
