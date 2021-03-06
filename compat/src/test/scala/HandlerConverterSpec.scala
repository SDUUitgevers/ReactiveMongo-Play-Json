package reactivemongo

import _root_.play.api.libs.json.{
  Format,
  JsNumber,
  Json,
  JsResult,
  JsSuccess,
  OFormat,
  OWrites,
  Reads,
  Writes
}

import reactivemongo.api.bson.{
  BSONDocument,
  BSONDocumentHandler,
  BSONDocumentReader,
  BSONDocumentWriter,
  BSONDouble,
  BSONHandler,
  BSONInteger,
  BSONLong,
  BSONReader,
  BSONWriter
}

import org.specs2.specification.core.Fragment

final class HandlerConverterSpec extends org.specs2.mutable.Specification {
  "Handler converters" title

  import reactivemongo.play.json.compat._

  "Converters" should {
    "from JSON" >> {
      "convert reader" in {
        implicit val jr = Reads[Long] { _ => JsSuccess(1L) }
        def bvr: BSONReader[Long] = jr
        def bdr: BSONDocumentReader[Long] = jr

        toReader(jr).readTry(BSONLong(2L)) must beSuccessfulTry(1L) and {
          bvr.readTry(BSONLong(3L)) must beSuccessfulTry(1L)
        } and {
          bvr.readTry(dsl.long(4L)) must beSuccessfulTry(1L)
        } and {
          bdr.readTry(BSONDocument(
            f"$$numberLong" -> 1)) must beSuccessfulTry(1L)
        }
      }

      "convert handler" in {
        object Foo

        implicit val jh = Format[Foo.type](
          Reads { _ => JsSuccess(Foo) },
          Writes { _: Foo.type => JsNumber(1) })

        def bh: BSONHandler[Foo.type] = jh

        bh.writeTry(Foo) must beSuccessfulTry(BSONInteger(1)) and {
          bh.readTry(BSONInteger(2)) must beSuccessfulTry(Foo)
        }
      }
    }

    "to JSON" >> {
      "convert reader" in {
        implicit val br = BSONReader[Unit] { _ => () }
        def jr: Reads[Unit] = br

        fromReader(br).reads(JsNumber(1)) must beLike[JsResult[Unit]] {
          case JsSuccess((), _) =>
            jr.reads(JsNumber(2)) must beLike[JsResult[Unit]] {
              case JsSuccess((), _) => ok
            }
        }
      }

      "convert handler" in {
        object Bar

        implicit val bh = BSONHandler[Bar.type](
          _ => Bar,
          _ => BSONDouble(1.2D))

        def jh: Format[Bar.type] = bh

        jh.reads(dsl.double(3.4D)) must beLike[JsResult[Bar.type]] {
          case JsSuccess(Bar, _) =>
            jh.writes(Bar) must_=== JsNumber(1.2D)
        }
      }
    }

    Fragment.foreach(HandlerFixtures.fixtures) {
      case (js, bson) =>
        s"between $js & $bson" >> {
          "convert writer to BSON" in {
            implicit val jw = Writes[Unit] { _ => js }
            def bw: BSONWriter[Unit] = jw

            toWriter(jw).writeTry({}) must beSuccessfulTry(bson) and {
              bw.writeTry({}) must beSuccessfulTry(bson)
            }
          }

          "convert writer to JSON" in {
            implicit val bw = BSONWriter[Int] { _ => bson }
            def jw: Writes[Int] = bw

            fromWriter(bw).writes(1) must_=== js and {
              jw.writes(2) must_=== js
            }
          }
        }
    }

    "from JSON object" >> {
      "in writer" in {
        val doc = BSONDocument("ok" -> 1)
        implicit val jw = OWrites[Double] { _ => Json.obj("ok" -> 1) }
        def bw1: BSONDocumentWriter[Double] = jw
        def bw2 = implicitly[BSONDocumentWriter[Double]]

        toWriter(jw).writeTry(1.0D) must beSuccessfulTry(doc) and {
          bw1.writeTry(1.1D) must beSuccessfulTry(doc)
        } and {
          bw2.writeTry(1.2D) must beSuccessfulTry(doc)
        }
      }

      "in reader" in {
        implicit val jr: Reads[Float] = Reads[Float](_ => JsSuccess(1.2F))
        def br1: BSONDocumentReader[Float] = jr
        def br2 = implicitly[BSONDocumentReader[Float]]

        toDocumentReader(jr).readTry(
          BSONDocument("ok" -> 1)) must beSuccessfulTry(1.2F) and {
            br1.readTry(BSONDocument("ok" -> 2)) must beSuccessfulTry(1.2F)
          } and {
            br2.readTry(BSONDocument("ok" -> 3)) must beSuccessfulTry(1.2F)
          }
      }

      "in handler" in {
        implicit val jh = OFormat[None.type](
          Reads { _ => JsSuccess(None) },
          OWrites { _: None.type => Json.obj() })

        val bh: BSONDocumentHandler[None.type] = jh

        bh.readTry(BSONDocument("ok" -> 1)) must beSuccessfulTry(None) and {
          bh.writeTry(None) must beSuccessfulTry(BSONDocument.empty)
        }
      }
    }

    "to JSON object" >> {
      "in writer" in {
        val doc = Json.obj("ok" -> 2)
        implicit val bw = BSONDocumentWriter[Int](_ => BSONDocument("ok" -> 2))
        def jw: OWrites[Int] = bw

        fromWriter(bw).writes(1) must_=== doc and {
          jw.writes(2) must_=== doc
        }
      }

      "in reader" in {
        implicit val br = BSONDocumentReader[None.type](_ => None)
        def jr: Reads[None.type] = br

        fromDocumentReader(br).
          reads(Json.obj("ok" -> 1)) must beLike[JsResult[None.type]] {
            case JsSuccess(None, _) => jr.reads(Json.obj(
              "ok" -> 2)) must beLike[JsResult[None.type]] {
              case JsSuccess(None, _) => ok
            }
          }
      }

      "in handler" in {
        implicit val bh = BSONDocumentHandler[Unit](
          _ => (),
          _ => BSONDocument("foo" -> 1L))

        val jh: OFormat[Unit] = bh

        jh.reads(Json.obj("ok" -> 1)) must beLike[JsResult[Unit]] {
          case JsSuccess((), _) => jh.writes({}) must_=== Json.obj("foo" -> 1L)
        }
      }
    }
  }
}

object HandlerFixtures {
  import _root_.play.api.libs.json.{
    JsArray,
    JsNull,
    JsObject,
    JsString,
    JsValue
  }

  import reactivemongo.api.bson.{
    BSONArray,
    BSONBinary,
    BSONBoolean,
    BSONDecimal,
    BSONJavaScript,
    BSONJavaScriptWS,
    BSONMaxKey,
    BSONMinKey,
    BSONNull,
    BSONString,
    BSONSymbol,
    BSONUndefined,
    BSONValue
  }

  import _root_.reactivemongo.play.json.compat.{ dsl, JsTrue, ValueConverters }
  import ExtendedJsonFixtures.{ joid, jdt, jts, jsJavaScript, jre, boid, bdt, bre, bts, jsBinUuid, jsJavaScriptWS, uuid }

  val jarr = JsArray(Seq(joid, JsString("foo"), jdt, dsl.symbol("bar"), jts, jsJavaScript("lorem()"), jre, JsArray(Seq(JsNumber(1), JsNumber(2L))), JsNumber(3.4D)))

  val barr = BSONArray(boid, BSONString("foo"), bdt, BSONSymbol("bar"), bts, BSONJavaScript("lorem()"), bre, BSONArray(BSONInteger(1), BSONInteger(2 /* #note1 */ )), BSONDouble(3.4D))

  val jdoc = JsObject(Map[String, JsValue]("oid" -> joid, "str" -> JsString("foo"), "dt" -> jdt, "sym" -> dsl.symbol("bar"), "ts" -> jts, "nested" -> JsObject(Map[String, JsValue]("foo" -> JsString("bar"), "lorem" -> JsNumber(Long.MaxValue))), "js" -> jsJavaScript("lorem()"), "re" -> jre, "array" -> jarr, "double" -> JsNumber(3.4D)))

  val bdoc = BSONDocument("oid" -> boid, "str" -> BSONString("foo"), "dt" -> bdt, "sym" -> BSONSymbol("bar"), "ts" -> bts, "nested" -> BSONDocument("foo" -> "bar", "lorem" -> Long.MaxValue), "js" -> BSONJavaScript("lorem()"), "re" -> bre, "array" -> barr, "double" -> BSONDouble(3.4D))

  val fixtures = Seq[(JsValue, BSONValue)](
    jsBinUuid -> BSONBinary(uuid),
    JsTrue -> BSONBoolean(true),
    JsNumber(1.23D) -> BSONDouble(1.23D),
    JsString("Foo") -> BSONString("Foo"),
    JsNumber(1) -> BSONInteger(1),
    JsNumber(1L) -> BSONInteger(1), // #note1: no int/long discriminator in JSON
    JsNumber(Long.MaxValue) -> BSONLong(Long.MaxValue),
    joid -> boid,
    jdt -> bdt,
    jts -> bts,
    dsl.decimal(BigDecimal("0")) -> BSONDecimal.PositiveZero,
    jre -> bre,
    jsJavaScript("foo()") -> BSONJavaScript("foo()"),
    jsJavaScriptWS("bar()") -> BSONJavaScriptWS("bar()", BSONDocument.empty),
    dsl.symbol("sym") -> BSONSymbol("sym"),
    ValueConverters.JsUndefined -> BSONUndefined,
    JsNull -> BSONNull,
    ValueConverters.JsMaxKey -> BSONMaxKey,
    ValueConverters.JsMinKey -> BSONMinKey,
    jarr -> barr,
    jdoc -> bdoc)

}
