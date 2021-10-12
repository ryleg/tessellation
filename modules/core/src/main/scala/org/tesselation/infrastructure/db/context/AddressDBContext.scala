package org.tesselation.infrastructure.db.context

import org.tesselation.infrastructure.db.schema.StoredAddress
import org.tesselation.schema.address.{Address, Balance}

import io.getquill.NamingStrategy
import io.getquill.context.Context
import io.getquill.idiom.Idiom

trait AddressDBContext[I <: Idiom, N <: NamingStrategy] {
  this: Context[I, N] =>

  val getAddresses = quote {
    querySchema[StoredAddress]("address")
  }

  val getAddressBalance = quote { (address: Address) =>
    getAddresses.filter(_.address == address).map(_.balance).take(1)
  }

  val insertAddressBalance = quote { (address: Address, balance: Balance) =>
    getAddresses
      .insert(_.address -> address, _.balance -> balance)
  }

  val insertOrUpdateAddressBalance = quote { (address: Address, balance: Balance) =>
    insertAddressBalance(address, balance)
      .onConflictUpdate(_.address)((existing, conflicted) => existing.balance -> conflicted.balance)
  }

  val updateAddressBalance = quote { (address: Address, balance: Balance) =>
    getAddresses
      .filter(_.address == address)
      .update(_.balance -> balance)
  }

  val deleteAddressBalance = quote { (address: Address) =>
    getAddresses
      .filter(_.address == address)
      .delete
  }
}
