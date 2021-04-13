package com.github.timgent.dataflare.api

import com.github.timgent.dataflare.api.qcresults.QcResultsRepo.QcResultsRepo
import zio.{Has, RIO, Task}

package object qcresults {

  type DTask[A] = RIO[QcResultsRepo, A]

  object Persistence {
    trait Service[A] {
      def get(id: Int): Task[A]
      def create(a: A): Task[A]
      def delete(id: Int): Task[Boolean]
    }
  }

  type UserPersistence = Has[Persistence.Service[User]]

  def getUser(id: Int): RIO[UserPersistence, User] = RIO.accessM(_.get.get(id))
  def createUser(a: User): RIO[UserPersistence, User] =
    RIO.accessM(_.get.create(a))
  def deleteUser(id: Int): RIO[UserPersistence, Boolean] =
    RIO.accessM(_.get.delete(id))
}
