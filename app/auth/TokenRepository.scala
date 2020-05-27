package auth

import java.time.Instant
import java.util.Collections
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.repositories.AuthenticatorRepository
import com.mohiva.play.silhouette.impl.authenticators.BearerTokenAuthenticator
import com.mohiva.play.silhouette.impl.providers.CredentialsProvider
import org.apache.commons.collections4.map.LRUMap
import org.joda.time.DateTime
import play.api.db.slick.DatabaseConfigProvider
import slick.jdbc.H2Profile.api._
import slick.jdbc.JdbcProfile
import slick.lifted.Tag
import util.TemporalSqlMappings
import util.Time

/**
  * @param idleTimeout in seconds
  */
case class UserSession(id: String, username: String, lastUsed: Instant, expiry: Instant, idleTimeout: Option[Long])

object UserSession {
  def fromBearerTokenAuthenticator(a: BearerTokenAuthenticator) =
    UserSession(a.id, a.loginInfo.providerKey,
      Time.toJava(a.lastUsedDateTime.toInstant), Time.toJava(a.expirationDateTime.toInstant),
      a.idleTimeout.map(_.toSeconds))

  def toBearerTokenAuthenticator(session: UserSession, credentialsProvider: CredentialsProvider) =
    BearerTokenAuthenticator(session.id, LoginInfo(credentialsProvider.id, session.username),
      new DateTime(session.lastUsed.toEpochMilli), new DateTime(session.expiry.toEpochMilli),
      session.idleTimeout.map(FiniteDuration(_, TimeUnit.SECONDS)))

  val tupled = (this.apply _).tupled
}

class UserSessionTable(tag: Tag) extends Table[UserSession](tag, "user_session") with TemporalSqlMappings {
  def id = column[String]("id", O.PrimaryKey)
  def username = column[String]("username")
  def lastUsed = column[Instant]("last_used")
  def expiry = column[Instant]("expiry")
  def idleTimeout = column[Long]("idle_timeout") // seconds

  override def * = (id, username, lastUsed, expiry, idleTimeout.?) <> (UserSession.tupled, UserSession.unapply)
}

@Singleton
class TokenRepository @Inject()(dbConfigProvider: DatabaseConfigProvider, credentialsProvider: CredentialsProvider)
  extends AuthenticatorRepository[BearerTokenAuthenticator] {

  private val MaxCachedSessions = 1000
  private val authCache = Collections.synchronizedMap(new LRUMap[String, BearerTokenAuthenticator](MaxCachedSessions))

  def sessions = TableQuery[UserSessionTable]

  def dbConfig = dbConfigProvider.get[JdbcProfile]

  // ignore return wart as cache lookup is an intended side effect
  @SuppressWarnings(Array("org.wartremover.warts.Return"))
  override def find(id: String): Future[Option[BearerTokenAuthenticator]] = {
    val cached = Option(authCache.get(id))
    if (cached.isDefined) return Future.successful(cached) // scalastyle:ignore return

    val sessionById = sessions.filter(_.id === id).result.headOption
    dbConfig.db.run(sessionById).map(_.map(UserSession.toBearerTokenAuthenticator(_, credentialsProvider)))
  }

  override def add(authenticator: BearerTokenAuthenticator): Future[BearerTokenAuthenticator] = {
    // FIXME: Potential race condition (and not very efficient) - try to do everything in a single query/transaction
    this.find(authenticator.id) flatMap {
      case Some(_) => throw new IllegalArgumentException(s"BearerTokenAuthenticator with id ${authenticator.id} already exists")
      case None =>
        dbConfig.db.run(sessions += UserSession.fromBearerTokenAuthenticator(authenticator)) map {
          case 1 =>
            authCache.put(authenticator.id, authenticator)
            authenticator
          case 0 => throw new IllegalStateException("Insertion of authenticator to database failed")
          case _ => throw new IllegalStateException("Number of authenticator insertions was neither 0 nor 1")
        }
    }
  }

  override def update(authenticator: BearerTokenAuthenticator): Future[BearerTokenAuthenticator] = {
    authCache.put(authenticator.id, authenticator)
    dbConfig.db.run(sessions.filter(_.id === authenticator.id)
      .update(UserSession.fromBearerTokenAuthenticator(authenticator))).map(_ => authenticator)
  }

  override def remove(id: String): Future[Unit] = {
    authCache.remove(id)
    dbConfig.db.run(sessions.filter(_.id === id).delete).map(_ => Unit)
  }
}
