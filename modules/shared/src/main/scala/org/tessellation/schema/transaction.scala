package org.tessellation.schema

import cats.effect.Async
import cats.syntax.functor._
import cats.syntax.semigroup._

import org.tessellation.ext.crypto._
import org.tessellation.kryo.KryoSerializer
import org.tessellation.schema.address.Address
import org.tessellation.schema.balance.Amount
import org.tessellation.security.Encodable
import org.tessellation.security.hash.Hash
import org.tessellation.security.signature.Signed

import derevo.cats.{order, show}
import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import eu.timepit.refined.auto.{autoInfer, autoRefineV, autoUnwrap}
import eu.timepit.refined.cats._
import eu.timepit.refined.types.numeric.{NonNegLong, PosLong}
import io.estatico.newtype.macros.newtype
import io.estatico.newtype.ops._
import monocle.Lens
import monocle.macros.GenLens

object transaction {

  @derive(decoder, encoder, order, show)
  @newtype
  case class TransactionAmount(value: PosLong)

  object TransactionAmount {
    implicit def toAmount(amount: TransactionAmount): Amount = Amount(amount.value)
  }

  @derive(decoder, encoder, order, show)
  @newtype
  case class TransactionFee(value: NonNegLong)

  object TransactionFee {
    implicit def toAmount(fee: TransactionFee): Amount = Amount(fee.value)
  }

  @derive(decoder, encoder, order, show)
  @newtype
  case class TransactionOrdinal(value: NonNegLong) {
    def next: TransactionOrdinal = TransactionOrdinal(value |+| 1L)
  }

  object TransactionOrdinal {
    val first: TransactionOrdinal = TransactionOrdinal(1L)
  }

  @derive(decoder, encoder, order, show)
  case class TransactionReference(ordinal: TransactionOrdinal, hash: Hash)

  object TransactionReference {
    val empty: TransactionReference = TransactionReference(TransactionOrdinal(0L), Hash("".padTo(64, '0')))

    val _Hash: Lens[TransactionReference, Hash] = GenLens[TransactionReference](_.hash)
    val _Ordinal: Lens[TransactionReference, TransactionOrdinal] = GenLens[TransactionReference](_.ordinal)

    def of[F[_]: Async: KryoSerializer](signedTransaction: Signed[Transaction]): F[TransactionReference] =
      signedTransaction.value.hashF.map(TransactionReference(signedTransaction.ordinal, _))

  }

  @derive(decoder, encoder, order, show)
  @newtype
  case class TransactionSalt(value: Long)

  @derive(decoder, encoder, order, show)
  case class TransactionData(
    source: Address,
    destination: Address,
    amount: TransactionAmount,
    fee: TransactionFee
  )

  @derive(decoder, encoder, order, show)
  case class Transaction(
    source: Address,
    destination: Address,
    amount: TransactionAmount,
    fee: TransactionFee,
    parent: TransactionReference,
    salt: TransactionSalt
  ) extends Fiber[TransactionReference, TransactionData]
      with Encodable {
    import Transaction._

    def reference = parent
    def data = TransactionData(source, destination, amount, fee)

    // WARN: Transactions hash needs to be calculated with Kryo instance having setReferences=true, to be backward compatible
    override def toEncode: String =
      "2" +
        runLengthEncoding(
          Seq(
            source.coerce,
            destination.coerce,
            amount.coerce.value.toHexString,
            parent.hash.coerce,
            parent.ordinal.coerce.value.toString(),
            fee.coerce.value.toString(),
            salt.coerce.toHexString
          )
        )

    val ordinal: TransactionOrdinal = _ParentOrdinal.get(this).next
  }

  object Transaction {
    def runLengthEncoding(hashes: Seq[String]): String = hashes.fold("")((acc, hash) => s"$acc${hash.length}$hash")

    val _Source: Lens[Transaction, Address] = GenLens[Transaction](_.source)
    val _Destination: Lens[Transaction, Address] = GenLens[Transaction](_.destination)

    val _Amount: Lens[Transaction, TransactionAmount] = GenLens[Transaction](_.amount)
    val _Fee: Lens[Transaction, TransactionFee] = GenLens[Transaction](_.fee)
    val _Parent: Lens[Transaction, TransactionReference] = GenLens[Transaction](_.parent)

    val _ParentHash: Lens[Transaction, Hash] = _Parent.andThen(TransactionReference._Hash)
    val _ParentOrdinal: Lens[Transaction, TransactionOrdinal] = _Parent.andThen(TransactionReference._Ordinal)
  }

  @derive(decoder, encoder, order, show)
  case class RewardTransaction(
    destination: Address,
    amount: TransactionAmount
  )
}
