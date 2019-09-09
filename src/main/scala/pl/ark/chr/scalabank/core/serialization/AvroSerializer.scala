package pl.ark.chr.scalabank.core.serialization

import java.io.{ByteArrayInputStream, ByteArrayOutputStream}

import akka.serialization.Serializer
import com.sksamuel.avro4s._
import org.apache.avro.Schema
import pl.ark.chr.scalabank.account.BankAccount._
import pl.ark.chr.scalabank.account.UserAccount._
import pl.ark.chr.scalabank.common.TryWithResources._

private object AvroSerializer {
  //ENCODERS
  implicit val depositEncoder = Encoder[DepositEvent]
  implicit val withdrawEncoder = Encoder[WithdrawEvent]
  implicit val createBankAccountEncoder = Encoder[OpenBankAccountEvent]
  implicit val closeBankAccountEncoder = Encoder[CloseBankAccountEvent]

  //DECODERS
  val depositDecoder = Decoder[DepositEvent]
  val withdrawDecoder = Decoder[WithdrawEvent]
  val createBankAccountDecoder = Decoder[OpenBankAccountEvent]
  val closeBankAccountDecoder = Decoder[CloseBankAccountEvent]

  //SCHEMAS
  val avroSchemas: Map[Class[_], Schema] = Map(
    classOf[DepositEvent] -> AvroSchema[DepositEvent],
    classOf[WithdrawEvent] -> AvroSchema[WithdrawEvent],
    classOf[OpenBankAccountEvent] -> AvroSchema[OpenBankAccountEvent],
    classOf[CloseBankAccountEvent] -> AvroSchema[CloseBankAccountEvent]
  )
}

class AvroSerializer extends Serializer {

  override def identifier: Int = 151419834

  override def includeManifest: Boolean = true

  import AvroSerializer._

  override def toBinary(o: AnyRef): Array[Byte] = {
    o match {
      case a: DepositEvent => serialize(a)
      case a: WithdrawEvent => serialize(a)
      case a: OpenBankAccountEvent => serialize(a)
      case a: CloseBankAccountEvent => serialize(a)
      case _ => throw new RuntimeException("Wrong class to serialize")
    }
  }

  private def serialize[T <: AvroSerializable : Encoder](a: T): Array[Byte] = {
    avroSchemas.get(a.getClass) match {
      case Some(schema) => serialize(a, schema)
      case None => throw new RuntimeException(s"Avro serialization not supported for class: ${a.getClass}")
    }
  }

  private def serialize[T: Encoder](a: T, schema: Schema): Array[Byte] =
    withResources(new ByteArrayOutputStream()) { baos =>
      val avroOutputStream = AvroOutputStream.binary[T].to(baos).build(schema)
      avroOutputStream.write(a)
      avroOutputStream.flush()
      baos.toByteArray
    }

  override def fromBinary(bytes: Array[Byte], manifest: Option[Class[_]]): AnyRef =
    manifest match {
      case Some(clazz) =>
        val pair = for {
          schema <- avroSchemas.get(clazz)
          decoder <- findDecoder(clazz)
        } yield (schema, decoder)
        pair match {
          case Some((schema, decoder)) => deserialize(bytes, schema)(decoder)
          case None => throw new RuntimeException(s"No schema or decoder found for class $clazz")
        }
      case None => throw new RuntimeException("No manifest present")
    }

  private def findDecoder(clazz: Class[_]): Option[Decoder[_]] =
    if (clazz == classOf[DepositEvent])
      Some(depositDecoder)
    else if (clazz == classOf[WithdrawEvent])
      Some(withdrawDecoder)
    else if (clazz == classOf[OpenBankAccountEvent])
      Some(createBankAccountDecoder)
    else if (clazz == classOf[CloseBankAccountEvent])
      Some(closeBankAccountDecoder)
    else None

  private def deserialize[T: Decoder](bytes: Array[Byte], schema: Schema): T =
    withResources(new ByteArrayInputStream(bytes)) { bais =>
      val inputStream = AvroInputStream.binary[T].from(bais).build(schema)
      inputStream.iterator.next()
    }
}

