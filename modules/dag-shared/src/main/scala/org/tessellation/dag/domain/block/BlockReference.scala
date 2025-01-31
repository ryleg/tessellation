package org.tessellation.dag.domain.block

import cats.effect.Async
import cats.syntax.functor._

import org.tessellation.ext.derevo.ordering
import org.tessellation.kryo.KryoSerializer
import org.tessellation.schema.height.Height
import org.tessellation.security.hash.ProofsHash
import org.tessellation.security.signature.Signed

import derevo.cats.{order, show}
import derevo.circe.magnolia.{decoder, encoder}
import derevo.derive
import derevo.scalacheck.arbitrary

@derive(arbitrary, encoder, decoder, order, ordering, show)
case class BlockReference(height: Height, hash: ProofsHash)

object BlockReference {

  def of[F[_]: Async: KryoSerializer](block: Signed[DAGBlock]): F[BlockReference] =
    block.proofsHash.map(BlockReference(block.height, _))

}
